## Findings (06/10/2026)

**Runs.** Run 1: prompts v1 and v2. Run 2 (the table above): v1, v2 and v3 together, then Nova Pro with prompt v2.
The same prompt scored differently between runs (v1: 49% of fields in run 1, 40% in run 2; v2: 47% and 42%), so
differences of a few points between prompts are **within run-to-run noise**.

1. **Accuracy is the limiting factor, not the prompt.** Across all versions, about 40–49% of scored fields and
   43–57% of drug names were read correctly, and 70–82% of items were found. Clear, well-spaced lines (SL02, SL04,
   SL12 supplies) were read almost perfectly; dense cursive lists (SL06, SL07, SL09, SL10) were not.
2. **Values were invented.** The model filled a duration on 12–19 of ~24 matched items where none is written
   (usually "5 days", the example in the v1 prompt), and often a dose. Prompt v3's explicit "never use a default"
   rules did not stop this (12 items). Every invented value sits in a field the pharmacist must check, and in the
   UI the field is editable – but this is a real dispensing risk and is listed in the limitations.
3. **Flagging is reliable with prompt v2.** In both runs, every item with an error was flagged low or medium
   (100%), and v2 never marked a wrong item high. v1 once replaced an illegible name with an invented
   "Paracetamol 500 mg" and a brand with its generic, marked **high**; v3 marked 3 wrong items high.
4. **Missed items still occur** (roughly 1 in 4–5) and are not flagged – only the side-by-side photo reveals them.
5. **A bigger model did not help.** Amazon Nova Pro (prompt v2) scored lower (31% of fields), was slower (3.2 s),
   cost 2.3× more (USD 0.0026 per scan) and failed once with `ModelErrorException` (malformed tool output). That
   error was being reported as a configuration error; it is now retried once and then reported as "AI returned an
   unexpected result".
6. **Decision:** the app now defaults to **Nova 2 Lite with prompt v2** (`PROMPT_VERSION=v2`) – the safest
   combination measured – and keeps v1/v3 selectable for further testing.

**Caveats.** The ground truth is provisional (read by Claude Code, to be verified by the student/pharmacist). The
prompts were refined on the same 9 images they were evaluated on, so the v2/v3 results may be slightly optimistic.
n = 9 photos (33 items) from two patients' prescriptions – indicative only.
