#!/usr/bin/env python3
"""Accuracy evaluation of the prescription reader against a hand-written ground truth.

Sends every sample in --samples to one or more running backends (each can run a different prompt or model,
e.g. PROMPT_VERSION=v1 on port 8081 and v2 on 8082), compares the extracted items with
samples/ground_truth.json, and writes docs/evaluation.md (+ docs/evaluation_raw.json).

Scans use name_check=false so the model's own confidence is measured. Every scan is a real Amazon Bedrock call
(about USD 0.001 each) and is logged by the backend in logs/usage.csv.

Standard library only. Example:
    python3 scripts/evaluate.py --api v1=http://localhost:8081 --api v2=http://localhost:8082
"""
from __future__ import annotations

import argparse
import json
import re
import time
import urllib.error
import urllib.request
import uuid
from datetime import date
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIELDS = ["drug_name", "strength", "dose", "frequency", "duration"]

# ---------------------------------------------------------------- normalisation and field comparison


def _clean(s: str | None) -> str:
    s = (s or "").lower().replace("–", "-")
    s = re.sub(r"\b(tab|tabs|cap|caps|inj|syp|tablet|capsule)\b\.?", " ", s)
    return re.sub(r"[^a-z0-9/.% ]+", " ", s).strip()


def name_match(truth: str, pred: str | None) -> float:
    a, b = _clean(truth), _clean(pred)
    if not a or not b:
        return 0.0
    if len(a) >= 4 and (a in b or b in a):
        return 1.0
    first = SequenceMatcher(None, a.split()[0], b.split()[0]).ratio()
    return max(SequenceMatcher(None, a, b).ratio(), first)


def _numbers(s: str | None) -> list[float]:
    s = (s or "").replace("½", " 1/2").replace("⅓", " 1/3").replace("¼", " 1/4")
    out = []
    for m in re.finditer(r"(\d+(?:\.\d+)?)\s*/\s*(\d+)|(\d+(?:\.\d+)?)", s):
        out.append(float(m.group(1)) / float(m.group(2)) if m.group(1) else float(m.group(3)))
    return out


def strength_ok(truth: str, pred: str | None) -> bool:
    t, p = _numbers(truth), _numbers(pred)
    return bool(t) and t[0] in p


def dose_ok(truth: str, pred: str | None) -> bool:
    t, p = _numbers(truth), _numbers(pred)
    return bool(t) and bool(p) and abs(t[0] - p[0]) < 1e-6


def _freq(s: str | None) -> tuple[int | None, str | None]:
    s = (s or "").lower()
    tod = "am" if re.search(r"\bmane\b|morning", s) else "pm" if re.search(r"\bnocte\b|night|bedtime", s) else None
    if re.search(r"\bqds\b|four times|4 times", s):
        return 4, tod
    if re.search(r"\btds\b|three times|3 times|1\s*\+\s*1\s*\+\s*1", s):
        return 3, tod
    if re.search(r"\bbd\b|twice|two times|2 times|1\s*\+\s*0\s*\+\s*1|x\s*2\b", s):
        return 2, None
    if tod or re.search(r"\bdaily\b|\bod\b|once", s):
        return 1, tod
    return None, tod


def frequency_ok(truth: str, pred: str | None) -> bool:
    (tn, tt), (pn, pt) = _freq(truth), _freq(pred)
    return tn is not None and tn == pn and (tt is None or pt is None or tt == pt)


def _days(s: str | None) -> float | None:
    s = (s or "").lower()
    m = re.search(r"(\d+)\s*/\s*(7|52|12)\b", s)
    if m:
        return int(m.group(1)) * {"7": 1, "52": 7, "12": 30}[m.group(2)]
    if re.search(r"1\s*/\s*a\b", s):
        return 365
    m = re.search(r"(\d+)\s*(day|week|month|year|yr)", s)
    if m:
        return int(m.group(1)) * {"day": 1, "week": 7, "month": 30, "year": 365, "yr": 365}[m.group(2)]
    if re.search(r"\b(a|one) (year|yr)\b", s):
        return 365
    return None


def duration_ok(truth: str, pred: str | None) -> bool:
    t, p = _days(truth), _days(pred)
    return t is not None and t == p


CHECK = {"strength": strength_ok, "dose": dose_ok, "frequency": frequency_ok, "duration": duration_ok}

# ---------------------------------------------------------------- matching and scoring


def match_items(truth: list[dict], pred: list[dict]) -> list[tuple[int, int | None]]:
    """Pairs each ground-truth item with at most one predicted item (best drug-name match, then order)."""
    pairs, used = {}, set()
    scores = []
    for ti, t in enumerate(truth):
        if "drug_name" in t.get("uncertain", []):
            continue
        for pi, p in enumerate(pred):
            scores.append((name_match(t["drug_name"], p.get("drug_name")), ti, pi))
    for score, ti, pi in sorted(scores, reverse=True):
        if score >= 0.6 and ti not in pairs and pi not in used:
            pairs[ti] = pi
            used.add(pi)
    # Items whose name is uncertain (or did not match by name): pair with the leftover prediction nearest in order.
    for ti in range(len(truth)):
        if ti in pairs:
            continue
        free = [pi for pi in range(len(pred)) if pi not in used]
        if free and "drug_name" in truth[ti].get("uncertain", []):
            pi = min(free, key=lambda x: abs(x - ti))
            pairs[ti] = pi
            used.add(pi)
    return [(ti, pairs.get(ti)) for ti in range(len(truth))]


def score_sample(truth: list[dict], pred: list[dict]) -> dict:
    rows, fields_total, fields_ok, names_total, names_ok = [], 0, 0, 0, 0
    flagged_wrong, wrong_items, high_correct, correct_items = 0, 0, 0, 0
    pairs = match_items(truth, pred)
    for ti, pi in pairs:
        t = truth[ti]
        p = pred[pi] if pi is not None else None
        results = {}
        for f in FIELDS:
            if t.get(f) is None or f in t.get("uncertain", []):
                continue
            if p is None:
                ok = False
            elif f == "drug_name":
                ok = name_match(t[f], p.get(f)) >= 0.75
            else:
                ok = CHECK[f](t[f], p.get(f))
            results[f] = ok
            fields_total += 1
            fields_ok += ok
            if f == "drug_name":
                names_total += 1
                names_ok += ok
        if p is not None and results:
            if all(results.values()):
                correct_items += 1
                high_correct += p.get("confidence") == "high"
            else:
                wrong_items += 1
                flagged_wrong += p.get("confidence") in ("low", "medium")
        rows.append({"truth": t, "pred": p, "results": results})
    found = sum(1 for _, pi in pairs if pi is not None)
    return {
        "items_truth": len(truth), "items_found": found, "items_pred": len(pred),
        "extra": len(pred) - found, "fields_total": fields_total, "fields_ok": fields_ok,
        "names_total": names_total, "names_ok": names_ok, "wrong_items": wrong_items,
        "flagged_wrong": flagged_wrong, "correct_items": correct_items, "high_correct": high_correct, "rows": rows,
    }

# ---------------------------------------------------------------- API call


def scan(api: str, image: Path) -> tuple[dict | None, str | None, float]:
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{image.name}\"\r\n"
            f"Content-Type: image/jpeg\r\n\r\n").encode() + image.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(f"{api}/api/scans?name_check=false", data=body, method="POST",
                                 headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            return json.load(resp), None, time.time() - start
    except urllib.error.HTTPError as e:
        try:
            err = json.load(e)
            return None, f"{err.get('error_type')}: {err.get('detail') or err.get('message')}", time.time() - start
        except Exception:  # noqa: BLE001
            return None, f"HTTP {e.code}", time.time() - start

# ---------------------------------------------------------------- report


# USD per 1M input / output tokens by model (published prices - verify on the AWS pricing page).
PRICES = {"nova-2-lite": (0.33, 2.75), "nova-pro": (0.80, 3.20), "nova-lite": (0.06, 0.24)}


def price(model_id: str | None) -> tuple[float, float]:
    return next((v for k, v in PRICES.items() if k in (model_id or "")), PRICES["nova-2-lite"])


def pct(a: int, b: int) -> str:
    return f"{a / b:.0%} ({a}/{b})" if b else "–"


def fmt(v) -> str:
    return "–" if v in (None, "") else str(v).replace("|", "/")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--api", action="append", default=[], help="label=URL of a running backend (repeatable)")
    ap.add_argument("--reuse", action="append", default=[],
                    help="label=path/to/evaluation_raw.json: include an earlier run's results without new API calls")
    ap.add_argument("--samples", type=Path, default=ROOT / "samples" / "sri_lanka")
    ap.add_argument("--truth", type=Path, default=ROOT / "samples" / "ground_truth.json")
    ap.add_argument("--out", type=Path, default=ROOT / "docs" / "evaluation.md")
    ap.add_argument("--notes", type=Path, default=ROOT / "docs" / "evaluation_notes.md",
                    help="hand-written findings inserted after the summary (kept when the report is regenerated)")

    a = ap.parse_args()

    truth_all = json.loads(a.truth.read_text(encoding="utf-8"))
    truth = truth_all["samples"]
    apis = dict(x.split("=", 1) for x in a.api)
    results: dict[str, dict] = {}
    for spec in a.reuse:
        label, path = spec.split("=", 1)
        results[label] = json.loads(Path(path).read_text(encoding="utf-8"))[label]
    for label, url in apis.items():
        status = json.load(urllib.request.urlopen(f"{url}/api/status", timeout=10))
        results[label] = {"model_id": status.get("model_id"), "prompt_version": status.get("prompt_version"),
                          "samples": {}}
        for name in sorted(truth):
            data, error, secs = scan(url, a.samples / name)
            pred = (data or {}).get("items", [])
            s = score_sample(truth[name], pred)
            s.update(error=error, latency_s=(data or {}).get("latency_ms", secs * 1000) / 1000,
                     input_tokens=(data or {}).get("input_tokens", 0), output_tokens=(data or {}).get("output_tokens", 0))
            results[label]["samples"][name] = s
            print(f"{label} {name}: {s['items_found']}/{s['items_truth']} items, names {s['names_ok']}/{s['names_total']}, "
                  f"fields {s['fields_ok']}/{s['fields_total']}, {s['latency_s']:.1f}s" + (f"  ERROR {error}" if error else ""))

    (a.out.parent / "evaluation_raw.json").write_text(json.dumps(results, indent=1, ensure_ascii=False), encoding="utf-8")

    def total(label: str, key: str) -> int:
        return sum(s[key] for s in results[label]["samples"].values())

    L = []
    L.append("# Accuracy evaluation")
    L.append("")
    L.append(f"Run {date.today():%d/%m/%Y} with `scripts/evaluate.py` on {len(truth)} redacted Sri Lankan prescription photos "
             f"(`{a.samples.relative_to(ROOT)}`), against `{a.truth.relative_to(ROOT)}`.")
    L.append("")
    L.append("> **Small, indicative sample (n = %d), not statistically conclusive.** %s" % (len(truth), truth_all.get("_status", "")))
    L.append("> Each run is a real Amazon Bedrock call; the model's output can vary between runs even at temperature 0.")
    L.append("")
    L.append("## Summary")
    L.append("")
    labels = list(results)
    L.append("| Measure | " + " | ".join(f"{l} (prompt {results[l]['prompt_version']})" for l in labels) + " |")
    L.append("|---|" + "---|" * len(labels))

    def row(title, fn):
        L.append(f"| {title} | " + " | ".join(fn(l) for l in labels) + " |")

    row("Items found (recall)", lambda l: pct(total(l, "items_found"), total(l, "items_truth")))
    row("Extra items (not on the prescription)", lambda l: str(total(l, "extra")))
    row("Drug names correct", lambda l: pct(total(l, "names_ok"), total(l, "names_total")))
    row("All scored fields correct", lambda l: pct(total(l, "fields_ok"), total(l, "fields_total")))
    row("Items with an error that the model flagged (low/medium)",
        lambda l: pct(total(l, "flagged_wrong"), total(l, "wrong_items")))
    row("Fully correct items marked high confidence", lambda l: pct(total(l, "high_correct"), total(l, "correct_items")))

    def avg(l, k):
        v = [s[k] for s in results[l]["samples"].values() if not s["error"]]
        return sum(v) / len(v) if v else 0

    row("Average read time", lambda l: f"{avg(l, 'latency_s'):.1f} s")
    row("Average tokens per scan (in / out)", lambda l: f"{avg(l, 'input_tokens'):,.0f} / {avg(l, 'output_tokens'):,.0f}")
    row("Model", lambda l: f"`{results[l]['model_id']}`")
    row("Average cost per scan (est.)", lambda l: "USD %.5f" % (avg(l, 'input_tokens') / 1e6 * price(results[l]['model_id'])[0]
                                                            + avg(l, 'output_tokens') / 1e6 * price(results[l]['model_id'])[1]))
    row("Failed scans", lambda l: str(sum(1 for s in results[l]['samples'].values() if s['error'])))
    L.append("")
    L.append("Scoring: a field is scored only when the ground truth has a value and it is not marked uncertain. Drug names match "
             "if they are similar enough (≥ 75%, ignoring Tab/Cap); frequencies are compared by doses per day and time of day "
             "(bd = twice daily, mane = morning); durations in days (3/12 = 3 months); strengths and doses by their numbers. "
             "A missed item counts all its scored fields as wrong.")
    if a.notes.is_file():
        L.append("")
        L.append(a.notes.read_text(encoding="utf-8").strip())
    L.append("")
    L.append("## Per sample")
    L.append("")
    L.append("| Sample | " + " | ".join(f"{l}: items · names · fields · time" for l in labels) + " |")
    L.append("|---|" + "---|" * len(labels))
    for name in sorted(truth):
        cells = []
        for l in labels:
            s = results[l]["samples"][name]
            cells.append("ERROR" if s["error"] else
                         f"{s['items_found']}/{s['items_truth']} · {s['names_ok']}/{s['names_total']} · "
                         f"{s['fields_ok']}/{s['fields_total']} · {s['latency_s']:.1f} s")
        L.append(f"| {name} | " + " | ".join(cells) + " |")
    L.append("")
    L.append("## Item-level detail")
    L.append("")
    L.append("✓ = scored correct, ✗ = scored wrong; confidence is the model's own (before the stock-list check).")
    for l in labels:
        L.append("")
        L.append(f"### {l} (prompt {results[l]['prompt_version']}, {results[l]['model_id']})")
        for name in sorted(truth):
            s = results[l]["samples"][name]
            L.append("")
            L.append(f"**{name}**" + (f" – error: {s['error']}" if s["error"] else ""))
            L.append("")
            L.append("| Ground truth | Read by the model | Confidence | Result |")
            L.append("|---|---|---|---|")
            for r in s["rows"]:
                t, p = r["truth"], r["pred"]
                gt = " · ".join(fmt(t.get(f)) for f in FIELDS if t.get(f))
                pr = "**missed**" if p is None else " · ".join(fmt(p.get(f)) for f in FIELDS)
                res = " ".join(("✓" if ok else "✗") + f for f, ok in r["results"].items()) or "not scored"
                L.append(f"| {gt} | {pr} | {fmt(p.get('confidence')) if p else '–'} | {res} |")
            if s["extra"]:
                L.append(f"| *(extra items read)* | {s['extra']} item(s) not on the prescription | | |")
    a.out.write_text("\n".join(L) + "\n", encoding="utf-8")
    print(f"\nWrote {a.out.relative_to(ROOT)} and {(a.out.parent / 'evaluation_raw.json').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
