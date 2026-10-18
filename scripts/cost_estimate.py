#!/usr/bin/env python3
"""Itemised cost estimate for the Prescription Reader (IT6070 Assignment II).

Prints one-off SETUP costs and monthly RECURRING costs in LKR (and USD), the cost of this proof of concept so far,
lower-cost alternatives, and the value of staff time saved. Every figure is labelled:

  MEASURED   - from logs/usage.csv (real Amazon Bedrock token usage written by the backend)
  PRICE      - published AWS price (verify on the AWS pricing page before submission)
  ASSUMPTION - a setting to confirm with the pharmacy (change it with a command-line option)
  SIMULATED  - simulated data used in the proof of concept
  derived    - calculated from the rows above

If logs/usage.csv has no successful scans for the chosen model, the default ASSUMPTION of 2,000 input and 500
output tokens per scan is used. Standard library only. Examples:
    python3 scripts/cost_estimate.py
    python3 scripts/cost_estimate.py --per-day 200 --wage-lkr-per-hour 400
    python3 scripts/cost_estimate.py --host-usd 12 --maintenance-lkr 3000
"""
from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_INPUT_TOKENS = 2000
DEFAULT_OUTPUT_TOKENS = 500
# USD per 1M input / output tokens (PRICE - verify). Used to price the PoC's own scans by model.
PRICES = {"nova-2-lite": (0.33, 2.75), "nova-pro": (0.80, 3.20), "nova-lite": (0.06, 0.24)}


def price_for(model_id: str) -> tuple[float, float]:
    return next((v for k, v in PRICES.items() if k in model_id), PRICES["nova-2-lite"])


@dataclass
class Usage:
    input_tokens: float
    output_tokens: float
    measured: bool
    successful: int = 0
    scans: int = 0
    avg_latency_s: float | None = None
    failed_share: float = 0.0   # tokens spent on failed scans / tokens on successful scans
    poc_spend_usd: float = 0.0  # every logged scan, all models
    poc_scans_all: int = 0


def load_usage(path: Path, model_filter: str) -> Usage:
    rows = []
    if path.is_file():
        with path.open(newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
    spend = sum(int(r["input_tokens"]) / 1e6 * price_for(r["model_id"])[0]
                + int(r["output_tokens"]) / 1e6 * price_for(r["model_id"])[1] for r in rows)
    mine = [r for r in rows if model_filter in r.get("model_id", "")]
    ok = [r for r in mine if r.get("outcome") == "success"]
    if not ok:
        return Usage(DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS, False, scans=len(mine),
                     poc_spend_usd=spend, poc_scans_all=len(rows))
    tok = lambda r: int(r["input_tokens"]) + int(r["output_tokens"])  # noqa: E731
    ok_tokens = sum(tok(r) for r in ok)
    return Usage(
        input_tokens=sum(int(r["input_tokens"]) for r in ok) / len(ok),
        output_tokens=sum(int(r["output_tokens"]) for r in ok) / len(ok),
        measured=True, successful=len(ok), scans=len(mine),
        avg_latency_s=sum(int(r["latency_ms"]) for r in ok) / len(ok) / 1000,
        failed_share=sum(tok(r) for r in mine if r.get("outcome") != "success") / ok_tokens if ok_tokens else 0.0,
        poc_spend_usd=spend, poc_scans_all=len(rows))


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--usage-csv", type=Path, default=ROOT / "logs" / "usage.csv")
    p.add_argument("--model-filter", default="nova-2-lite", help="which logged model's token usage to use (default Nova 2 Lite)")
    p.add_argument("--per-day", type=float, default=150, help="handwritten prescriptions per day [ASSUMPTION, from the pharmacist's estimate]")
    p.add_argument("--days", type=float, default=30, help="working days per month [ASSUMPTION]")
    p.add_argument("--in-price", type=float, default=0.33, help="Nova 2 Lite USD per 1M input tokens [PRICE]")
    p.add_argument("--out-price", type=float, default=2.75, help="Nova 2 Lite USD per 1M output tokens [PRICE]")
    p.add_argument("--fx", type=float, default=300.0, help="LKR per USD [ASSUMPTION]")
    # Setup (one-off)
    p.add_argument("--devices-lkr", type=float, default=0, help="devices: the pharmacy's existing PC and phone [ASSUMPTION]")
    p.add_argument("--install-lkr", type=float, default=15000, help="install & configure: AWS account, server, HTTPS, login [ASSUMPTION]")
    p.add_argument("--integration-lkr", type=float, default=5000, help="stock-list export from the pharmacy system [ASSUMPTION]")
    p.add_argument("--training-lkr", type=float, default=5000, help="staff training, about 2 hours [ASSUMPTION]")
    # Recurring (monthly)
    p.add_argument("--host-usd", type=float, default=7.0, help="AWS Lightsail instance USD/month [PRICE]")
    p.add_argument("--domain-lkr", type=float, default=300, help="domain name for HTTPS, per month (certificate free) [ASSUMPTION]")
    p.add_argument("--maintenance-lkr", type=float, default=2000, help="maintenance & support: updates, backups [ASSUMPTION]")
    p.add_argument("--alt-name", default="Amazon Nova Lite (v1)")
    p.add_argument("--alt-in-price", type=float, default=0.06, help="[PRICE - verify]")
    p.add_argument("--alt-out-price", type=float, default=0.24, help="[PRICE - verify]")
    # Value
    p.add_argument("--minutes-saved", type=float, default=1.0, help="staff minutes saved per prescription [ASSUMPTION - measure in a pilot]")
    p.add_argument("--wage-lkr-per-hour", type=float, default=300.0, help="staff cost LKR/hour [ASSUMPTION]")
    a = p.parse_args()

    u = load_usage(a.usage_csv, a.model_filter)
    scans = a.per_day * a.days
    lkr = lambda usd: usd * a.fx  # noqa: E731
    tag = "MEASURED" if u.measured else "ASSUMPTION"
    per_scan_usd = u.input_tokens / 1e6 * a.in_price + u.output_tokens / 1e6 * a.out_price
    ai_lkr = lkr(per_scan_usd * scans * (1 + u.failed_share))
    host_lkr = lkr(a.host_usd)
    setup = [("Devices (existing PC + phone)", a.devices_lkr, "ASSUMPTION"),
             ("Installation & configuration (AWS, server, HTTPS, login)", a.install_lkr, "ASSUMPTION"),
             ("Stock-list export / integration (replaces SIMULATED CSV)", a.integration_lkr, "ASSUMPTION"),
             ("Staff training (~2 hours)", a.training_lkr, "ASSUMPTION"),
             ("Software licences (open source: Angular, Spring Boot, PDFBox)", 0, "FACT: free licences")]
    recurring = [(f"Amazon Bedrock – Nova 2 Lite ({scans:,.0f} scans)", ai_lkr,
                  f"{tag} tokens x PRICE" + (f" (+{u.failed_share:.0%} failed scans)" if u.failed_share else "")),
                 ("AWS Lightsail server (USD %.0f)" % a.host_usd, host_lkr, "PRICE"),
                 ("Domain name (HTTPS certificate free)", a.domain_lkr, "ASSUMPTION"),
                 ("Maintenance & support (updates, backups)", a.maintenance_lkr, "ASSUMPTION"),
                 ("Data transfer & storage", 0, "ASSUMPTION: within Lightsail allowance")]
    setup_total = sum(v for _, v, _ in setup)
    rec_total = sum(v for _, v, _ in recurring)
    value = a.per_day * a.minutes_saved / 60 * a.days * a.wage_lkr_per_hour
    net = value - rec_total
    breakeven_s = rec_total / scans / (a.wage_lkr_per_hour / 3600)

    line = "-" * 100
    row = "{:<60} {:>14}   {}"
    print("Prescription Reader – itemised cost estimate (LKR; USD 1 = LKR %.0f [ASSUMPTION])" % a.fx)
    print(line)
    if u.measured:
        print(f"Tokens per scan: {u.input_tokens:,.0f} in / {u.output_tokens:,.0f} out  [MEASURED: mean of {u.successful} successful "
              f"{a.model_filter} scans in {a.usage_csv.name}; average read time {u.avg_latency_s:.1f} s]")
    else:
        print(f"Tokens per scan: {u.input_tokens:,.0f} in / {u.output_tokens:,.0f} out  [ASSUMPTION: no successful {a.model_filter} scans logged]")
    print(f"Volume: {a.per_day:,.0f} handwritten prescriptions/day x {a.days:,.0f} days = {scans:,.0f} scans/month  [ASSUMPTION]")
    print(line)
    print(row.format("ONE-OFF SETUP", "LKR", "Basis"))
    for name, v, basis in setup:
        print(row.format("  " + name, f"{v:,.0f}", basis))
    print(row.format("  Total setup", f"{setup_total:,.0f}", "derived"))
    print(line)
    print(row.format("RECURRING – PER MONTH", "LKR", "Basis"))
    for name, v, basis in recurring:
        print(row.format("  " + name, f"{v:,.0f}", basis))
    print(row.format("  Total recurring", f"{rec_total:,.0f}", f"derived = LKR {rec_total / scans:.2f} per prescription"))
    print(row.format("  Per year (recurring x 12)", f"{rec_total * 12:,.0f}", "derived"))
    print(line)
    print("FREE-TIER / SIMULATED vs REALISTIC USE")
    print(f"  Proof of concept so far: {u.poc_scans_all} logged scans cost about USD {u.poc_spend_usd:.2f} "
          f"(LKR {lkr(u.poc_spend_usd):,.0f}) on pay-as-you-go Bedrock  [MEASURED]")
    print("  PoC hosting: the student's laptop (LKR 0) - realistic use needs the Lightsail server above")
    print("  Stock list and interaction rules are SIMULATED - realistic use needs the pharmacy's export (setup) and a")
    print("  professionally reviewed interaction source (not costed here)")
    print("  Free tiers: none assumed (check the AWS console for any Lightsail trial or credits)")
    print(line)
    print(row.format("LOWER-COST ALTERNATIVES (per month)", "LKR", "Basis"))
    alt_ai = lkr((u.input_tokens / 1e6 * a.alt_in_price + u.output_tokens / 1e6 * a.alt_out_price) * scans * (1 + u.failed_share))
    print(row.format(f"  {a.alt_name} instead of Nova 2 Lite", f"{rec_total - ai_lkr + alt_ai:,.0f}", "PRICE - verify; accuracy not tested"))
    print(row.format("  Manual only (no AI)", "0", "no time saving"))
    print(line)
    print(row.format("EXPECTED VALUE (per month)", "LKR", "Basis"))
    print(row.format(f"  Staff time saved ({a.minutes_saved:g} min x {a.per_day:,.0f}/day)", f"{value:,.0f}",
                     f"ASSUMPTION: LKR {a.wage_lkr_per_hour:,.0f}/h – NOT YET MEASURED"))
    print(row.format("  Net value (time saved - recurring)", f"{net:,.0f}", "derived"))
    print(row.format("  Payback of setup", f"{setup_total / net:.1f} months" if net > 0 else "not reached", "derived"))
    print(row.format("  Break-even time saving per prescription", f"{breakeven_s:.0f} seconds", "derived"))
    print(line)
    print("Prices: Nova 2 Lite USD %.2f / %.2f per 1M input / output tokens; check the AWS pricing page." % (a.in_price, a.out_price))


if __name__ == "__main__":
    main()
