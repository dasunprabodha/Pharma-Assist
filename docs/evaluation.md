# Accuracy evaluation

Run 06/10/2026 with `scripts/evaluate.py` on 9 redacted Sri Lankan prescription photos (`samples/sri_lanka`), against `samples/ground_truth.json`.

> **Small, indicative sample (n = 9), not statistically conclusive.** PROVISIONAL - read by Claude Code from the redacted images on 06/10/2026; the student/pharmacist must verify and correct. Fields listed in 'uncertain' are not scored; null = not written on the prescription (not scored).
> Each run is a real Amazon Bedrock call; the model's output can vary between runs even at temperature 0.

## Summary

| Measure | v1 (prompt v1) | v2 (prompt v2) | v3 (prompt v3) | pro-v2 (prompt v2) |
|---|---|---|---|---|
| Items found (recall) | 70% (23/33) | 76% (25/33) | 73% (24/33) | 70% (23/33) |
| Extra items (not on the prescription) | 4 | 7 | 5 | 7 |
| Drug names correct | 43% (10/23) | 52% (12/23) | 52% (12/23) | 52% (12/23) |
| All scored fields correct | 40% (29/72) | 42% (30/72) | 43% (31/72) | 31% (22/72) |
| Items with an error that the model flagged (low/medium) | 100% (9/9) | 100% (12/12) | 73% (8/11) | 100% (16/16) |
| Fully correct items marked high confidence | 8% (1/12) | 27% (3/11) | 36% (4/11) | 0% (0/5) |
| Average read time | 2.2 s | 2.4 s | 2.5 s | 3.2 s |
| Average tokens per scan (in / out) | 1,558 / 163 | 1,888 / 187 | 2,046 / 175 | 2,542 / 190 |
| Model | `us.amazon.nova-2-lite-v1:0` | `us.amazon.nova-2-lite-v1:0` | `us.amazon.nova-2-lite-v1:0` | `us.amazon.nova-pro-v1:0` |
| Average cost per scan (est.) | USD 0.00096 | USD 0.00114 | USD 0.00116 | USD 0.00264 |
| Failed scans | 0 | 0 | 0 | 1 |

Scoring: a field is scored only when the ground truth has a value and it is not marked uncertain. Drug names match if they are similar enough (≥ 75%, ignoring Tab/Cap); frequencies are compared by doses per day and time of day (bd = twice daily, mane = morning); durations in days (3/12 = 3 months); strengths and doses by their numbers. A missed item counts all its scored fields as wrong.

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

## Per sample

| Sample | v1: items · names · fields · time | v2: items · names · fields · time | v3: items · names · fields · time | pro-v2: items · names · fields · time |
|---|---|---|---|---|
| SL02.jpg | 2/2 · 1/1 · 4/4 · 2.1 s | 2/2 · 1/1 · 4/4 · 2.0 s | 2/2 · 1/1 · 4/4 · 2.8 s | 2/2 · 1/1 · 4/4 · 3.4 s |
| SL04.jpg | 1/1 · 1/1 · 2/2 · 1.7 s | 1/1 · 1/1 · 2/2 · 1.5 s | 1/1 · 1/1 · 2/2 · 1.7 s | 1/1 · 1/1 · 2/2 · 2.3 s |
| SL05.jpg | 2/3 · 1/2 · 1/5 · 1.5 s | 2/3 · 1/2 · 1/5 · 1.5 s | 2/3 · 1/2 · 1/5 · 2.2 s | 3/3 · 1/2 · 1/5 · 3.0 s |
| SL06.jpg | 4/5 · 0/3 · 3/13 · 2.1 s | 3/5 · 0/3 · 2/13 · 3.0 s | 3/5 · 0/3 · 2/13 · 3.3 s | 2/5 · 0/3 · 1/13 · 3.4 s |
| SL07.jpg | 3/5 · 1/3 · 5/10 · 2.9 s | 3/5 · 1/3 · 5/10 · 2.6 s | 3/5 · 1/3 · 5/10 · 2.6 s | 3/5 · 1/3 · 1/10 · 3.3 s |
| SL09.jpg | 1/4 · 0/2 · 0/9 · 2.4 s | 2/4 · 0/2 · 0/9 · 2.5 s | 1/4 · 0/2 · 0/9 · 3.5 s | ERROR |
| SL10.jpg | 3/4 · 0/2 · 2/9 · 2.9 s | 3/4 · 0/2 · 2/9 · 3.3 s | 3/4 · 0/2 · 3/9 · 2.3 s | 3/4 · 0/2 · 3/9 · 3.0 s |
| SL11.jpg | 2/2 · 1/2 · 3/5 · 2.1 s | 2/2 · 1/2 · 3/5 · 2.1 s | 2/2 · 1/2 · 3/5 · 2.0 s | 2/2 · 1/2 · 3/5 · 2.7 s |
| SL12.jpg | 5/7 · 5/7 · 9/15 · 2.3 s | 7/7 · 7/7 · 11/15 · 2.7 s | 7/7 · 7/7 · 11/15 · 2.4 s | 7/7 · 7/7 · 7/15 · 4.4 s |

## Item-level detail

✓ = scored correct, ✗ = scored wrong; confidence is the model's own (before the stock-list check).

### v1 (prompt v1, us.amazon.nova-2-lite-v1:0)

**SL02.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Minoxin · 1 ml · bd · 1 year | Mioxin · 2/ · 1 ml · twice daily · 1 year | medium | ✓dose ✓frequency ✓duration |
| Ionax | Iomax · 1 ug · 3 · once daily · 1 year | medium | ✓drug_name |

**SL04.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Depo-Medrol · 40 mg | Depo-medrol · 40 mg · 1 ml · intra-articular and soft tissue injection · 40 days | high | ✓drug_name ✓strength |

**SL05.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Dazor | Dazor Shanper · 2.0 · mg · daily · x6/52 | low | not scored |
| Terbinafine · 250 mg · daily · 6/52 | **missed** | – | ✗drug_name ✗strength ✗frequency ✗duration |
| Beprogel | Beprazol · Solus · for · YA · YA | low | ✓drug_name |

**SL06.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | Ghiclegder · 10g · 1 tablet · twice daily · 5 days | medium | ✗drug_name ✗strength ✓frequency |
| Glucophage SR · 500 mg · bd | Ghucophy · 50g · 1 tablet · twice daily · 5 days | medium | ✗drug_name ✗strength ✓frequency |
| Sitavic · 100 mg · mane | Sutavica · 1g · 1 tablet · once daily · 5 days | low | ✗strength ✓frequency |
| Atorvastatin · 10 mg · nocte | **missed** | – | ✗drug_name ✗strength ✗frequency |
| ? · 25 mg · bd | famm · 2g · 1 tablet · once daily · 5 days | low | ✗strength ✗frequency |
| *(extra items read)* | 1 item(s) not on the prescription | | |

**SL07.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Diamicron MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | Sibavic · 100 · 1 · mane · 5 days | low | ✓strength ✓frequency |
| Bimpa · 10 mg · nocte | Bmp · 10 · 1 · nocte · 5 days | low | ✓strength ✓frequency |
| Nitroguard | Nuroguard · 1 · 1 · bd · 5 days | low | ✓drug_name |
| *(extra items read)* | 2 item(s) not on the prescription | | |

**SL09.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | Whitepile · mg · 1 · bd · 5 days | low | ✗strength ✗frequency |
| Bimpa · 10 mg · nocte | **missed** | – | ✗strength ✗frequency |

**SL10.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Scanos lotion · 1 ml · bd · 3/12 | Scinos-5 Iodam · 1ml · 6P · three times daily · 3/7 | medium | ✗dose ✗frequency ✗duration |
| Finasteride · 5 mg · 1/3 tablet · nocte · 3/12 | finaattonide (5mg) · 5mg · 1/3 tab · three times daily · 3/7 | medium | ✗drug_name ✓strength ✓dose ✗frequency ✗duration |
| ? shampoo | Sdegme Jhamye · 1cdan · after lab · as needed · 2 days | low | not scored |
| Beprogel lotion | **missed** | – | ✗drug_name |

**SL11.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Finasteride · 1 mg · nocte · 3/12 | Feustrid · 1mg · 1 tablet · three times daily · 3/12 | low | ✗drug_name ✓strength ✗frequency ✓duration |
| Ketoplus shampoo | Ketoplus · shyym · 1 tablet · for 1/4 · 1/4 | low | ✓drug_name |

**SL12.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| 3 cc syringe · 02 | Syringe · 3 cc · 1 · as needed · 40 | medium | ✓drug_name ✗dose |
| 23G needle · 01 | Needle · 23 g · 1 · as needed · 01 | medium | ✓drug_name ✓dose |
| Depo-Medrol · 40 mg · 1 vial | **missed** | – | ✗drug_name ✗strength ✗dose |
| Dettol plaster · 01 | Detol Plaster · 01 · 1 · as needed · 01 | medium | ✓drug_name ✓dose |
| Disposable gloves · 01 pair | Disposable gloves · 01 pair · 1 · as needed · 01 | medium | ✓drug_name ✓dose |
| Surgical spirit · 01 | Surgical spirit · 01 · 1 · as needed · 01 | medium | ✓drug_name ✓dose |
| Cotton wool · 01 | **missed** | – | ✗drug_name ✗dose |
| *(extra items read)* | 1 item(s) not on the prescription | | |

### v2 (prompt v2, us.amazon.nova-2-lite-v1:0)

**SL02.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Minoxin · 1 ml · bd · 1 year | Miwoxin · – · 1 ml · bd · 1 year | low | ✓dose ✓frequency ✓duration |
| Ionax | Ionax · 1 ug · 3 · – · – | low | ✓drug_name |

**SL04.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Depo-Medrol · 40 mg | Depo-medrol · 40 mg · 1 ml · intra-articular and soft tissue injection · 40 y | medium | ✓drug_name ✓strength |

**SL05.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Dazor | Dazor Shampoo · YA · for · YA · YA | low | not scored |
| Terbinafine · 250 mg · daily · 6/52 | **missed** | – | ✗drug_name ✗strength ✗frequency ✗duration |
| Beprogel | Beprazol · Lotris · for · YA · YA | low | ✓drug_name |
| *(extra items read)* | 1 item(s) not on the prescription | | |

**SL06.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | Ghiclegder · 60g · 1 · twice daily (bd) · 5 days | medium | ✗drug_name ✓strength ✓frequency |
| Glucophage SR · 500 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Sitavic · 100 mg · mane | Sutavica · 1g · 1 · three times daily (tds) · 5 days | low | ✗strength ✗frequency |
| Atorvastatin · 10 mg · nocte | **missed** | – | ✗drug_name ✗strength ✗frequency |
| ? · 25 mg · bd | famm · 2g · 1 · three times daily (tds) · 5 days | low | ✗strength ✗frequency |
| *(extra items read)* | 2 item(s) not on the prescription | | |

**SL07.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Diamicron MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | Sibavic · 100 mg · 1 tablet · once daily (od) · 30 days | high | ✓strength ✓frequency |
| Bimpa · 10 mg · nocte | Bmp · 10 mg · 1 tablet · once daily (od) · 30 days | high | ✓strength ✓frequency |
| Nitroguard | Nuroguard · 1 mg · 1 tablet · once daily (od) · 30 days | high | ✓drug_name |
| *(extra items read)* | 2 item(s) not on the prescription | | |

**SL09.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | Sulav · mg · 1 · bd · 5 days | low | ✗strength ✗frequency |
| Bimpa · 10 mg · nocte | Rmp · mg · 1 · bd · 5 days | low | ✗strength ✗frequency |
| *(extra items read)* | 2 item(s) not on the prescription | | |

**SL10.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Scanos lotion · 1 ml · bd · 3/12 | Scinos-5 · 1ml · 6 · x3/1/2 · 1/2 | low | ✗dose ✗frequency ✗duration |
| Finasteride · 5 mg · 1/3 tablet · nocte · 3/12 | finaxtomide · 5mg · 1/3 · table x 3/1/c · 1/2 | low | ✗drug_name ✓strength ✓dose ✗frequency ✗duration |
| ? shampoo | Sdegme · 1ml · after · Lah · 2 Scay | low | not scored |
| Beprogel lotion | **missed** | – | ✗drug_name |

**SL11.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Finasteride · 1 mg · nocte · 3/12 | Feuerteride · 1 mg · 1/2 tablet · three times daily (tds) · 3/12 | low | ✗drug_name ✓strength ✗frequency ✓duration |
| Ketoplus shampoo | Ketoplus · shyym · 1/4 · once daily (od) · 1/12 | low | ✓drug_name |

**SL12.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| 3 cc syringe · 02 | Syringe · 3 cc · 02 · – · – | low | ✓drug_name ✓dose |
| 23G needle · 01 | Needle · 23 g · 01 · – · – | low | ✓drug_name ✓dose |
| Depo-Medrol · 40 mg · 1 vial | Depo · 40 · 1 ml · – · – | low | ✓drug_name ✓strength ✓dose |
| Dettol plaster · 01 | Detol Plaster · 01 · – · – · – | low | ✓drug_name ✗dose |
| Disposable gloves · 01 pair | Disposable gloves · 01 pair · – · – · – | low | ✓drug_name ✗dose |
| Surgical spirit · 01 | Surgical spirit · 01 · – · – · – | low | ✓drug_name ✗dose |
| Cotton wool · 01 | Cotton wool · 01 · – · – · – | low | ✓drug_name ✗dose |

### v3 (prompt v3, us.amazon.nova-2-lite-v1:0)

**SL02.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Minoxin · 1 ml · bd · 1 year | Miwoxin · – · 1 ml · twice daily (bd) · 1 year | low | ✓dose ✓frequency ✓duration |
| Ionax | Ionax · 1 ug · 3 · – · – | low | ✓drug_name |

**SL04.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Depo-Medrol · 40 mg | Depo-medrol · 40 mg · 1 ml · intra-articular and soft tissue injection · 40 y | high | ✓drug_name ✓strength |

**SL05.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Dazor | Dazor Shampoo · YA · for · YA · YA | low | not scored |
| Terbinafine · 250 mg · daily · 6/52 | **missed** | – | ✗drug_name ✗strength ✗frequency ✗duration |
| Beprogel | Benragel · Lotris · for · YA · YA | low | ✓drug_name |
| *(extra items read)* | 1 item(s) not on the prescription | | |

**SL06.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | Ghiclegder · 60g · 1 · twice daily (bd) · 5 days | high | ✗drug_name ✓strength ✓frequency |
| Glucophage SR · 500 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Sitavic · 100 mg · mane | Sitavic · 1mg · 1 · three times daily (tds) · 5 days | high | ✗strength ✗frequency |
| Atorvastatin · 10 mg · nocte | **missed** | – | ✗drug_name ✗strength ✗frequency |
| ? · 25 mg · bd | famm · 2g · 1 · three times daily (tds) · 5 days | high | ✗strength ✗frequency |
| *(extra items read)* | 2 item(s) not on the prescription | | |

**SL07.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Diamicron MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | Sitavic · 100 mg · 1 tablet · once daily (od) · 30 days | high | ✓strength ✓frequency |
| Bimpa · 10 mg · nocte | Bmp · 10 mg · 1 tablet · once daily (od) · 7 days | high | ✓strength ✓frequency |
| Nitroguard | Neuroguard · 1 mg · 1 tablet · once daily (od) · 30 days | high | ✓drug_name |
| *(extra items read)* | 2 item(s) not on the prescription | | |

**SL09.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | Whitpile · mg · 1 · bd · 5 days | low | ✗strength ✗frequency |
| Bimpa · 10 mg · nocte | **missed** | – | ✗strength ✗frequency |

**SL10.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Scanos lotion · 1 ml · bd · 3/12 | Scinos-5 · 1ml · 6 p · x3/1/2 · 1/2 | low | ✗dose ✗frequency ✗duration |
| Finasteride · 5 mg · 1/3 tablet · nocte · 3/12 | finaattonide · 5mg · 1/3 tablet · once x 3/1/2 · 3/1/2 | low | ✗drug_name ✓strength ✓dose ✓frequency ✗duration |
| ? shampoo | Sdegme · 1ml · after bath · 2 scag · 1/2 | low | not scored |
| Beprogel lotion | **missed** | – | ✗drug_name |

**SL11.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Finasteride · 1 mg · nocte · 3/12 | Feuerteride · 1 mg · 1/2 tablet · three times daily (tds) · 3/12 | low | ✗drug_name ✓strength ✗frequency ✓duration |
| Ketoplus shampoo | Ketoplus · shyym · 1/4 · once daily (od) · 1/A | low | ✓drug_name |

**SL12.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| 3 cc syringe · 02 | Syringe · 3 cc · 02 · – · – | low | ✓drug_name ✓dose |
| 23G needle · 01 | Needle · 23 g · 01 · – · – | low | ✓drug_name ✓dose |
| Depo-Medrol · 40 mg · 1 vial | Depo · 40 · 1 ml · – · – | low | ✓drug_name ✓strength ✓dose |
| Dettol plaster · 01 | Detol Plaster · 01 · – · – · – | low | ✓drug_name ✗dose |
| Disposable gloves · 01 pair | Disposable gloves · 01 pair · – · – · – | low | ✓drug_name ✗dose |
| Surgical spirit · 01 | Surgical spirit · 01 · – · – · – | low | ✓drug_name ✗dose |
| Cotton wool · 01 | Cotton wool · 01 · – · – · – | low | ✓drug_name ✗dose |

### pro-v2 (prompt v2, us.amazon.nova-pro-v1:0)

**SL02.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Minoxin · 1 ml · bd · 1 year | Minoxin · – · 1 ml · twice daily (bd) · 1 year | low | ✓dose ✓frequency ✓duration |
| Ionax | Ionax · 1 mg · 1 tablet · twice daily (bd) · 3 days | medium | ✓drug_name |

**SL04.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Depo-Medrol · 40 mg | Depo-Medrol · 40 mg · 1 ml · once daily (od) · 5 days (5/7) | medium | ✓drug_name ✓strength |
| *(extra items read)* | 1 item(s) not on the prescription | | |

**SL05.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Dazor | Dazol Shampoo · – · – · – · – | low | not scored |
| Terbinafine · 250 mg · daily · 6/52 | Terbinafin · 200 mg · 1 tablet · twice daily (bd) · 10 days | medium | ✓drug_name ✗strength ✗frequency ✗duration |
| Beprogel | Benagel · – · – · – · – | low | ✗drug_name |

**SL06.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 500 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Sitavic · 100 mg · mane | Sative · 50g · mence · twice daily (bd) · 5 days | low | ✗strength ✗frequency |
| Atorvastatin · 10 mg · nocte | **missed** | – | ✗drug_name ✗strength ✗frequency |
| ? · 25 mg · bd | famm · 2g · BP · twice daily (bd) · 5 days | low | ✗strength ✓frequency |
| *(extra items read)* | 3 item(s) not on the prescription | | |

**SL07.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Diamicron MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | Subvic · – · – · – · – | low | ✗strength ✗frequency |
| Bimpa · 10 mg · nocte | Bmp · – · – · – · – | low | ✗strength ✗frequency |
| Nitroguard | Niroguard · – · – · – · – | low | ✓drug_name |
| *(extra items read)* | 2 item(s) not on the prescription | | |

**SL09.jpg** – error: config_error: ModelErrorException: Model produced invalid sequence as part of ToolUse. Please refer to the model tool use troubleshooting guide.

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Gliclazide MR · 60 mg · bd | **missed** | – | ✗drug_name ✗strength ✗frequency |
| Glucophage SR · 2 tablets · bd | **missed** | – | ✗drug_name ✗frequency |
| Sitavic · 100 mg · mane | **missed** | – | ✗strength ✗frequency |
| Bimpa · 10 mg · nocte | **missed** | – | ✗strength ✗frequency |

**SL10.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Scanos lotion · 1 ml · bd · 3/12 | Scino-5 · 100ml · 1ml · bd · 3/12 | medium | ✓dose ✓frequency ✓duration |
| Finasteride · 5 mg · 1/3 tablet · nocte · 3/12 | **missed** | – | ✗drug_name ✗strength ✗dose ✗frequency ✗duration |
| ? shampoo | Stagmore · Shampoo · 100ml · bd · 3/12 | medium | not scored |
| Beprogel lotion | Bemoge · 100ml · 100ml · bd · 3/12 | medium | ✗drug_name |
| *(extra items read)* | 1 item(s) not on the prescription | | |

**SL11.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| Finasteride · 1 mg · nocte · 3/12 | Fenastrid · 1mg · 1 · twice daily (bd) · 3/12 | medium | ✓drug_name ✓strength ✗frequency ✓duration |
| Ketoplus shampoo | Keto plus · Shym · to · once daily (od) · 1/A | low | ✗drug_name |

**SL12.jpg**

| Ground truth | Read by the model | Confidence | Result |
|---|---|---|---|
| 3 cc syringe · 02 | 3 cc syringes · 02 · – · – · – | low | ✓drug_name ✗dose |
| 23G needle · 01 | 23 g needle · 01 · – · – · – | low | ✓drug_name ✗dose |
| Depo-Medrol · 40 mg · 1 vial | Depo-medrol 40 · 1ml · – · – · – | low | ✓drug_name ✗strength ✗dose |
| Dettol plaster · 01 | Detol Plaster · 01 · – · – · – | low | ✓drug_name ✗dose |
| Disposable gloves · 01 pair | Disposable gloves · 01 pair · – · – · – | low | ✓drug_name ✗dose |
| Surgical spirit · 01 | Surgical spirit · 01 · – · – · – | low | ✓drug_name ✗dose |
| Cotton wool · 01 | Cotton wool · 01 · – · – · – | low | ✓drug_name ✗dose |
