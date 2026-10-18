# Technical evidence – Prescription Reader (draft for pages 1–2)

IT6070 Assignment II · U.W.D.P. Amarasinghe (MS25903874) · AI-assisted prescription reading and dispensing check

## 1. Professional and problem
**"Pharmacist A"** (declared pseudonym; real name withheld), Registered Pharmacist, **Dulnitha Pharmacy, Kottawa** – an
independent community retail pharmacy (healthcare; pharmacy named with permission). **Evidence:** a face-to-face
discussion on 30/09/2026 (pharmacist's estimates): ~200 prescriptions a day, 70–80% handwritten; 2–3 minutes each to
decipher, cross-check and match to stock (≈5–8 staff-hours a day); ~3% need a colleague or the prescriber. Misreading a
drug or dose is a patient-safety risk.
- **R1 – Read the prescription:** from a phone photo, list each item's drug, strength, dose, frequency and duration
  in an editable list within 30 s, expanding shorthand (bd, nocte, 5/7) and flagging uncertain fields.
- **R2 – Check before dispensing:** match the confirmed list with stock and interaction rules; produce a checklist with
  warnings and in-stock generic alternatives. No patient name is sent or stored.

## 2. Emerging technology and alternative
A **multimodal foundation model – Amazon Nova 2 Lite on Amazon Bedrock** – reads the photo and returns structured items
in one step (Converse API, schema-forced tool output, temperature 0). It understands context and shorthand, reports its
own confidence, needs no training data, and costs ≈ LKR 0.33 per scan. **Alternative:** Amazon Textract (OCR) →
Amazon Comprehend Medical → rules. Rejected for R1: two separate services (OCR errors flow straight into the extraction), and
Comprehend Medical is tuned to US clinical text and drug names, so Sri Lankan brands and shorthand may be missed.
Both are AWS-only; Nova was preferred because it meets R1 in a single, configurable step (the model is a setting).

## 3. Architecture *(diagram: docs/architecture.md)*
Browser (Angular) → Spring Boot API → image checks, PDF → images, **metadata removed**, resize → **Amazon Bedrock (Nova)**
→ validation + fuzzy name check → editable table (pharmacist confirms) → **checker** (simulated stock list + rules) →
dispensing checklist. Any AI failure switches to **manual entry**, which produces the same confirmed list.

## 4. Two connected features
1. **Read the prescription (R1).** Upload/photograph a prescription (JPG/PNG/WEBP or PDF) → Nova extracts the items →
   editable table beside the zoomable photo; low-confidence rows listed; stock match and **pack image** under each name;
   "Did you mean…?" suggestions; corrections counted → **Confirm list**. Fallback: automatic manual entry with a clear
   reason for each error type.
2. **Check before dispensing (R2).** The confirmed list (AI or manual) → stock status, in-stock alternatives with the
   same active ingredient, interaction / duplicate-class / duplicate-ingredient warnings (major → moderate → minor),
   unmatched items flagged "not checked", a **Checked** box per item and a CSV checklist.

## 5. Test cases (full log: docs/test_log.md)
| Test | Input | Expected | Actual | Result |
|------|-------|----------|--------|--------|
| TC1 success | Synthetic clear prescription: Lipitor 20 mg nocte; Klacid 500 mg bd × 5/7; Panadol 500 mg | Items correct within 30 s; Lipitor alternative; atorvastatin + clarithromycin warning | 3.0 s; all fields correct; 0 corrections; Lipitor out of stock → Atorvastatin (generic) 20 mg; R008 moderate warning | Pass |
| TC2 messy | Redacted Sri Lankan prescription (4 items) | Uncertain fields flagged → corrected → check runs | All rows flagged low; 1 item missed in one run; 20 corrections; check ran, all 4 marked "not in stock list – check manually" | Pass |
| TC3 error | Invalid model ID | Message → manual entry → check runs | "AI not configured … – using manual entry"; manual list confirmed; same checklist as TC1 | Pass |
| TC4 | Manual: Warfarin + Disprin | Major warning | R001 major: increased bleeding risk | Pass |
82 automated tests pass (mocked Bedrock). **Accuracy evaluation** (9 redacted Sri Lankan prescriptions, 33 items):
76% of items found, 52% of drug names and 42% of fields correct; **100% of wrong items flagged**; 2.4 s per scan.
Clear handwriting is read well; dense cursive is not, and the model sometimes adds a duration that is not written.

## 6. Feasibility
**Technical:** browser + one small AWS Lightsail server + Bedrock; internet needed (manual entry works without the AI).
**Data:** synthetic/redacted samples only; stock list and rules are **simulated** – real use needs the pharmacy's stock
export and a reviewed interaction source. **Skills:** staff need ~2 h training; maintenance needs basic AWS skills.
**Time:** built in 4 weeks; a pilot needs 2–4 more. **Privacy:** metadata stripped, patient details ignored and never
stored, least-privilege IAM, no keys in code; images are processed in the USA (no AWS region in Sri Lanka) – the
**PDPA (No. 9 of 2022)** cross-border rules must be checked, and login + HTTPS added, before real use.

## 7. Cost (LKR; USD 1 = LKR 300; `scripts/cost_estimate.py`)
| Setup (one-off) | LKR | | Recurring (per month) | LKR |
|---|---|---|---|---|
| Devices (existing PC + phone) | 0 | | Bedrock Nova 2 Lite, 4,500 scans [measured tokens] | 1,492 |
| Install & configure (AWS, HTTPS, login) | 15,000 | | Lightsail server (USD 7) | 2,100 |
| Stock-list export / integration | 5,000 | | Domain name | 300 |
| Training (~2 h) | 5,000 | | Maintenance & support | 2,000 |
| Licences (open source) | 0 | | **Total** (LKR 1.31 per prescription) | **5,892** |
| **Total setup** | **25,000** | | | |
Assumptions: 150 handwritten prescriptions/day × 30 days; labour LKR 300/h; prices to verify. The PoC itself cost
≈ USD 0.09 in Bedrock calls (laptop hosting, simulated data). **Affordability:** saving 1 minute per prescription
[assumption] is worth ≈ LKR 22,500/month (payback ≈ 1.5 months); the running cost is covered by saving just **16 s per
prescription**. Lower-cost options: Nova Lite v1 (≈ LKR 4,600/month, accuracy untested) or manual work (LKR 0, no saving).

## 8. Benefits, limitations, risks, adoption
**Benefits:** fast (≈2.5 s), cheap, flags its own errors, structured and auditable lists, interaction and stock checks,
pack images to pick the right product. **Limitations:** weak on dense cursive handwriting; invents durations; can miss an
item; small test set; simulated data; no stock-system link. **Risks → mitigations:** misread drug → pharmacist confirms
every item, flags, photo side by side; data exposure → no storage, redaction, PDPA review; outage → manual entry.
**Adoption:** the **pharmacy owner** pays (≈ LKR 25,000 setup + LKR 5,900/month). A two-week **pilot** at the counter,
with the dashboard recording read success and corrections and staff timing prescriptions with and without the tool.
**Main condition to proceed:** measured accuracy and a time saving above the 16-second break-even.
