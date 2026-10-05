#!/usr/bin/env python3
"""Monthly cost estimate for the Prescription Reader (IT6070 Assignment II).

Reads logs/usage.csv (written by the backend, one row per scan). If it contains successful
scans, the token counts per scan are MEASURED averages; otherwise the default ASSUMPTIONS
(2,000 input / 500 output tokens per scan) are used. Every other figure is a setting and is
labelled ASSUMPTION or PRICE (published price - verify against current AWS pricing).

Standard library only. Examples:
    python3 scripts/cost_estimate.py
    python3 scripts/cost_estimate.py --per-day 200 --wage-lkr-per-hour 400
    python3 scripts/cost_estimate.py --usage-csv logs/usage.csv --host-usd 12
"""
from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Default assumptions (CLAUDE.md section 8). Change with command-line options.
DEFAULT_INPUT_TOKENS = 2000
DEFAULT_OUTPUT_TOKENS = 500


@dataclass
class Usage:
    """Per-scan figures, either measured from logs/usage.csv or assumed."""

    input_tokens: float
    output_tokens: float
    measured: bool
    successful_scans: int = 0
    total_scans: int = 0
    avg_latency_s: float | None = None
    max_latency_s: float | None = None
    failed_tokens_share: float = 0.0  # tokens spent on failed scans / tokens on successful scans


def load_usage(path: Path) -> Usage:
    if not path.is_file():
        return Usage(DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS, measured=False)
    with path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    ok = [r for r in rows if r.get("outcome") == "success"]
    if not ok:
        return Usage(DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS, measured=False, total_scans=len(rows))

    def tokens(r: dict) -> int:
        return int(r["input_tokens"]) + int(r["output_tokens"])

    latencies = [int(r["latency_ms"]) / 1000 for r in ok]
    ok_tokens = sum(tokens(r) for r in ok)
    failed_tokens = sum(tokens(r) for r in rows if r.get("outcome") != "success")
    return Usage(
        input_tokens=sum(int(r["input_tokens"]) for r in ok) / len(ok),
        output_tokens=sum(int(r["output_tokens"]) for r in ok) / len(ok),
        measured=True,
        successful_scans=len(ok),
        total_scans=len(rows),
        avg_latency_s=sum(latencies) / len(latencies),
        max_latency_s=max(latencies),
        failed_tokens_share=failed_tokens / ok_tokens if ok_tokens else 0.0,
    )


def per_scan_usd(input_tokens: float, output_tokens: float, in_price: float, out_price: float) -> float:
    return input_tokens / 1e6 * in_price + output_tokens / 1e6 * out_price


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--usage-csv", type=Path, default=ROOT / "logs" / "usage.csv")
    p.add_argument("--per-day", type=float, default=150, help="handwritten prescriptions per day [ASSUMPTION, from pharmacist estimate]")
    p.add_argument("--days", type=float, default=30, help="working days per month [ASSUMPTION]")
    p.add_argument("--in-price", type=float, default=0.33, help="Nova 2 Lite USD per 1M input tokens [PRICE, US inference profile]")
    p.add_argument("--out-price", type=float, default=2.75, help="Nova 2 Lite USD per 1M output tokens [PRICE]")
    p.add_argument("--alt-name", default="Amazon Nova Lite (v1)")
    p.add_argument("--alt-in-price", type=float, default=0.06, help="alternative model USD per 1M input tokens [PRICE - verify]")
    p.add_argument("--alt-out-price", type=float, default=0.24, help="alternative model USD per 1M output tokens [PRICE - verify]")
    p.add_argument("--host-usd", type=float, default=7.0, help="AWS Lightsail instance USD/month [PRICE]")
    p.add_argument("--fx", type=float, default=300.0, help="LKR per USD [ASSUMPTION]")
    p.add_argument("--setup-lkr", type=float, default=25_000, help="one-off setup LKR [ASSUMPTION]")
    p.add_argument("--minutes-saved", type=float, default=1.0, help="staff minutes saved per prescription [ASSUMPTION - measure in pilot]")
    p.add_argument("--wage-lkr-per-hour", type=float, default=300.0, help="staff cost LKR/hour [ASSUMPTION]")
    a = p.parse_args()

    u = load_usage(a.usage_csv)
    scans = a.per_day * a.days
    tag = "MEASURED" if u.measured else "ASSUMPTION"
    lkr = lambda usd: usd * a.fx  # noqa: E731

    scan_usd = per_scan_usd(u.input_tokens, u.output_tokens, a.in_price, a.out_price)
    # Failed scans (unreadable photo, retries) still cost tokens: add the measured share.
    ai_month_usd = scan_usd * scans * (1 + u.failed_tokens_share)
    assumed_scan_usd = per_scan_usd(DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS, a.in_price, a.out_price)
    alt_scan_usd = per_scan_usd(u.input_tokens, u.output_tokens, a.alt_in_price, a.alt_out_price)
    total_usd = ai_month_usd + a.host_usd

    hours_saved = a.per_day * a.minutes_saved / 60 * a.days
    value_lkr = hours_saved * a.wage_lkr_per_hour
    net_lkr = value_lkr - lkr(total_usd)

    line = "-" * 96
    row = "{:<46} {:>14} {:>14}   {}"
    print("Prescription Reader - monthly cost estimate (Amazon Nova 2 Lite on Amazon Bedrock)")
    print(line)
    print("Inputs")
    if u.measured:
        print(f"  Tokens per scan       {u.input_tokens:,.0f} in / {u.output_tokens:,.0f} out   [MEASURED: mean of {u.successful_scans} "
              f"successful scan(s) of {u.total_scans} in {a.usage_csv.name}]")
        print(f"  Read time             {u.avg_latency_s:.1f} s average, {u.max_latency_s:.1f} s max (target < 30 s)   [MEASURED]")
        if u.successful_scans < 10:
            print(f"  NOTE: n = {u.successful_scans} is too small to be reliable - indicative only. Re-run after testing on 10-15 samples.")
    else:
        print(f"  Tokens per scan       {u.input_tokens:,.0f} in / {u.output_tokens:,.0f} out   [ASSUMPTION: no successful scans in "
              f"{a.usage_csv}]")
    print(f"  Prescriptions         {a.per_day:,.0f}/day x {a.days:,.0f} days = {scans:,.0f} scans/month   [ASSUMPTION]")
    print(f"  Nova 2 Lite price     USD {a.in_price}/1M input, USD {a.out_price}/1M output   [PRICE - verify on AWS pricing page]")
    print(f"  Exchange rate         USD 1 = LKR {a.fx:,.0f}   [ASSUMPTION]")
    print(line)
    print(row.format("Item", "USD/month", "LKR/month", "Basis"))
    print(line)
    print(row.format(f"Bedrock Nova 2 Lite ({scans:,.0f} scans)", f"{ai_month_usd:,.2f}", f"{lkr(ai_month_usd):,.0f}",
                     f"{tag} tokens x PRICE" + (f" (+{u.failed_tokens_share:.0%} failed scans)" if u.failed_tokens_share else "")))
    print(row.format("AWS Lightsail host (app server)", f"{a.host_usd:,.2f}", f"{lkr(a.host_usd):,.0f}", "PRICE (setting)"))
    print(row.format("Data transfer, storage", "0.00", "0", "ASSUMPTION: within Lightsail allowance"))
    print(line)
    print(row.format("TOTAL recurring", f"{total_usd:,.2f}", f"{lkr(total_usd):,.0f}", ""))
    print(row.format("One-off setup (install, training)", "", f"{a.setup_lkr:,.0f}", "ASSUMPTION"))
    print(line)
    print("Per scan")
    print(row.format("  Nova 2 Lite, this estimate", f"{scan_usd:.5f}", f"{lkr(scan_usd):.3f}", f"{tag}"))
    print(row.format(f"  Nova 2 Lite at {DEFAULT_INPUT_TOKENS:,}/{DEFAULT_OUTPUT_TOKENS} tokens (sensitivity)",
                     f"{assumed_scan_usd:.5f}", f"{lkr(assumed_scan_usd):.3f}",
                     f"ASSUMPTION -> {lkr(assumed_scan_usd * scans + a.host_usd):,.0f} LKR/month total"))
    print(line)
    print("Lower-cost alternatives (monthly, including host where relevant)")
    alt_total = alt_scan_usd * scans * (1 + u.failed_tokens_share) + a.host_usd
    print(row.format(f"  {a.alt_name}", f"{alt_total:,.2f}", f"{lkr(alt_total):,.0f}",
                     "PRICE - verify; older model, accuracy not tested"))
    print(row.format("  Manual only (no AI)", "0.00", "0", "no time saving"))
    print(line)
    print("Expected value")
    print(row.format(f"  Staff time saved ({a.minutes_saved:g} min x {a.per_day:,.0f}/day)", "", f"{value_lkr:,.0f}",
                     f"ASSUMPTION: {hours_saved:,.0f} h/month x LKR {a.wage_lkr_per_hour:,.0f}/h"))
    print(row.format("  Net monthly value (time saved - recurring)", "", f"{net_lkr:,.0f}", "derived"))
    if net_lkr > 0:
        print(row.format("  Payback of setup cost", "", f"{a.setup_lkr / net_lkr:.1f} months", "derived"))
    else:
        print("  Payback: not reached at these assumptions.")
    print(line)
    print("Labels: MEASURED = from logs/usage.csv; PRICE = published AWS price (verify); ASSUMPTION = setting to confirm;")
    print("derived = calculated from the rows above. The time saving is the key unknown - measure it in the pilot.")


if __name__ == "__main__":
    main()
