# Feasibility study – draft

Prescription Reader for Dulnitha Pharmacy, Kottawa – IT6070 Assignment II (U.W.D.P. Amarasinghe, MS25903874)

> **Status: draft (Week 2, 05/10/2026).** Figures are labelled:
> **[Measured]** – from this proof of concept (`logs/usage.csv`, test runs) ·
> **[Pharmacist estimate]** – from the discussion with "Pharmacist A" on 30/09/2026 ·
> **[Assumption]** – a setting to be confirmed · **[Price]** – published AWS price, to verify before submission.
> Measured figures so far come from **one** synthetic test scan, so they are indicative only; they will be updated
> after the accuracy evaluation on 10–15 samples (Week 3).

## Summary

Reading handwritten prescriptions with Amazon Nova 2 Lite is technically feasible and cheap to run: about
**LKR 3,000–5,000 per month** for 150 handwritten prescriptions a day, plus about **LKR 25,000** setup.
It covers its running cost if it saves roughly **8–13 seconds of staff time per prescription**. Whether it does depends
on reading accuracy on real local handwriting, which has not been measured yet. The recommendation is a short pilot
with the pharmacist before any purchase decision.

## Technical requirements

| Need | Proof of concept | Production |
|------|------------------|------------|
| Device for the pharmacist | Any modern browser on a phone or PC (camera or file upload) | Same – the pharmacy's existing PC and a phone |
| Application server | Student laptop: Java 17 + Spring Boot backend, Angular frontend | One AWS Lightsail Linux instance (USD 7/month plan [Price]; the 2 GB USD 12 plan is safer for Java [Assumption]) |
| AI service | Amazon Bedrock, Amazon Nova 2 Lite, `us.amazon.nova-2-lite-v1:0` inference profile, Converse API | Same; model ID is a setting, so Nova Pro can be used if accuracy is too low |
| Internet | Required for every scan | Required; manual entry still works when the AI is unreachable |
| Speed | R1 target: list within 30 s. **3.1 s** for one scan [Measured, n = 1] | Same target; the dashboard tracks average and p95 read time |
| Stock data | 34-item simulated CSV | Export from the pharmacy's stock system (format to be agreed) |

The Feature 1 workflow (photo or PDF → AI extraction → editable table → confirm, with manual fallback) is already
built and covered by 46 automated tests with a mocked AI client [Measured].

## Data access

- **Prescriptions:** the pharmacy holds paper prescriptions. For this project only **synthetic or redacted** samples
  are used (10–15 images, written to imitate local handwriting and shorthand). No real patient data is collected.
- **Stock list and interaction rules:** **simulated** CSVs for the proof of concept (`data/`). The pharmacist will
  review them for realism. A real deployment needs a regular export from the stock system; the interaction rules are
  textbook examples, **not a clinical database**, and would need a licensed or professionally reviewed source.
- **Ground truth for evaluation:** the student writes down what each sample really says, so field-level accuracy can be
  measured (`scripts/evaluate.py`, Week 3).

## Skills

| Who | Skills needed | Available? |
|-----|---------------|------------|
| Student (build) | Java/Spring Boot, Angular/TypeScript, AWS IAM and Bedrock, prompt and schema design, testing | Yes – with Claude Code as a declared coding assistant |
| Pharmacy staff (use) | Take a clear photo, review a table, correct fields | Yes – about 1 hour of training [Assumption] |
| Pharmacy owner (run) | Pay an AWS bill, keep the stock export current | Needs a small support arrangement [Assumption] |

## Time

| Week | Milestone date | Work | Status (05/10/2026) |
|------|----------------|------|---------------------|
| 1 | 26/09/2026 | Professional and problem – presentation, progress form | Done |
| 2 | 03/10/2026 | Design and feasibility – scaffold, architecture, cost and feasibility draft, Feature 1 started | Done; Feature 1 built ahead of plan; TC1–TC3 pending samples |
| 3 | 10/10/2026 | Implementation and testing – Feature 1 finished (stable by 14/10), Feature 2, tests, accuracy evaluation | Planned |
| 4 | 17/10/2026 | Final readiness – README, ZIP and repo, 4-page report, AI declaration, demo video (≤ 3 min) | Planned |

A production pilot would need about 2–4 weeks more: hosting, HTTPS, login, the real stock export and staff
training [Assumption].

## Privacy and security

- **Sri Lanka Personal Data Protection Act, No. 9 of 2022 (PDPA).** Prescription images contain health data, which
  the Act treats as a *special category* of personal data needing stronger safeguards. The pharmacy would be the
  data controller and AWS a processor. Before real use, the pharmacy must establish a lawful basis (for example,
  patient consent or a health-care purpose), tell patients how their data is processed, and keep a record of
  processing. *To confirm against the official text and the current commencement status of the Act's provisions.*
- **Overseas AWS region.** There is no AWS Region in Sri Lanka. The PoC uses **us-east-1** via the `us.` inference
  profile, so images are processed in the USA. The PDPA restricts cross-border transfers to adequate jurisdictions or
  transfers with safeguards; this must be checked. Options: an Asia-Pacific (`apac.`) inference profile, if it serves
  Nova 2 Lite for the account (to verify), and contractual safeguards (AWS data processing terms). AWS states that
  Bedrock does not use prompts and images to train models or share them with model providers [verify in AWS docs].
- **Data minimisation and redaction.**
  - The upload is held in memory only, and the image is re-encoded, so no EXIF/GPS data or PDF metadata is sent.
  - The prompt and response schema exclude the patient's name, address and age and the doctor's details.
  - Only drug items, token counts and timings are logged.
  - **No patient data is stored, and by default no images are stored.** An optional setting (`KEEP_IMAGES`, off by
    default) keeps the processed image of a *confirmed* list for 7 days so it can be checked from History; it is then
    deleted automatically, and the pharmacist can delete it sooner. Because the photo may show the patient's name, this
    setting would need the pharmacy owner's agreement before real use.
  - During development only synthetic or redacted samples are used.
- **Least-privilege access.** A dedicated IAM user is allowed only `bedrock:InvokeModel` on Amazon Nova models.
  - Credentials live in the AWS CLI profile, never in code or git; `.env` is git-ignored.
  - An AWS Budget alert (USD 5/month) caps surprise costs.
  - The git history will be scanned for secrets before submission.
- **Gaps before production:**
  - User login and HTTPS (the PoC has no login).
  - Audit logging of who confirmed each list.
  - A retention policy for the confirmed lists (saved images already have one: `IMAGE_RETENTION_DAYS`).
  - Encryption at rest for saved images, if `KEEP_IMAGES` is used.
  - A written agreement with the pharmacy owner.

## Benefits

- **Staff time.** Deciphering, cross-checking and matching takes **2–3 min per prescription**, about
  **5–8 staff-hours/day** across ~200 prescriptions, 70–80% handwritten [Pharmacist estimate]. If the tool saves
  **1 min** per handwritten prescription [Assumption], that is about **75 staff-hours/month**, worth about
  **LKR 22,500/month** at LKR 300/hour [Assumption].
- **Speed.** One scan read in **3.1 s** [Measured, n = 1], well inside the 30 s target.
- **Safety.**
  - Low-confidence fields and drug names not on the stock list are flagged, so uncertain readings get a second look.
  - Feature 2 adds interaction, duplicate-class and duplicate-ingredient warnings.
  - It also shows in-stock generic alternatives.
- **Consistency and evidence.**
  - Confirmed lists are structured, so they can be audited and counted.
  - The dashboard shows read success, read time, cost and pharmacist corrections per list.
- **Fewer interruptions.** About **3%** of prescriptions currently need a colleague or the prescriber [Pharmacist estimate]. A clearer reading may reduce this [Assumption – to measure].

## Limitations

- **Handwriting accuracy is low on dense cursive prescriptions** [Measured, n = 9 redacted Sri Lankan samples,
  `docs/evaluation.md`]: with Nova 2 Lite and prompt v2, 76% of items were found, 52% of drug names and 42% of
  fields read correctly. Every item with an error was flagged, but the model often **adds a duration that is not
  written** (e.g. "5 days") and sometimes **misses an item** without a flag. Nova Pro did not do better. Every item
  must therefore be checked against the photo by the pharmacist.
- **Decision support only:** it does not replace the pharmacist's judgement or a clinical drug-interaction database.
- **Simulated data:** 34 stock items and 21 textbook rules, with no live stock-system integration.
- **Internet required;** without it, only manual entry is available.
- **Overseas processing** (see Privacy).
- **Small evaluation set** (10–15 synthetic/redacted samples) – indicative, not statistically conclusive.
- PDFs are limited to 3 pages; every page adds image-token cost.

## Risks and mitigations

| Risk | Likelihood / impact | Mitigation |
|------|---------------------|------------|
| Misread drug name or dose leads to a dispensing error | Medium / High | Pharmacist confirms every item; low-confidence and unknown-name flags; model told not to guess; "decision support only" shown on every page |
| Poor accuracy on local handwriting | Medium / High | Measure in Week 3; switch to Nova Pro via `BEDROCK_MODEL_ID`; refine the prompt; manual entry always available |
| Patient data exposure or PDPA non-compliance | Low / High | No storage of images or patient details; metadata stripped; least-privilege IAM; legal check of cross-border transfer before real use |
| Leaked AWS credentials | Low / Medium | Keys only in the AWS CLI profile; `.env` git-ignored; history scan before submission; key rotation |
| Unexpected AWS bill | Low / Low | Budget alert at USD 5; per-scan cost about LKR 0.2–0.6 [Measured/Assumption]; token usage logged per scan |
| Bedrock outage, throttling or no internet | Low / Medium | Automatic fallback to manual entry; retries; the checker works on manual lists too |
| Model ID or price changes | Medium / Low | Model and prices are settings; cost script re-run from measured usage |
| Staff do not adopt it | Medium / Medium | Short training; the camera flow on a phone; a pilot to show the time saving first |

## Cost summary

From `python3 scripts/cost_estimate.py` (05/10/2026; 150 handwritten prescriptions/day × 30 days = 4,500 scans/month
[Assumption]; USD 1 = LKR 300 [Assumption]):

| Item | LKR/month | Basis |
|------|-----------|-------|
| Bedrock Nova 2 Lite | 970 – 2,747 | 1,536 in / 77 out tokens per scan [Measured, n = 1] to 2,000 / 500 [Assumption]; USD 0.33 / 2.75 per 1M tokens [Price] |
| AWS Lightsail host | 2,100 | USD 7/month [Price] |
| **Total recurring** | **3,070 – 4,847** | |
| One-off setup | 25,000 | [Assumption] |
| Lower-cost alternative: Nova Lite (v1) | ~2,250 | USD 0.06 / 0.24 per 1M tokens [Price – verify]; accuracy not tested |
| Lower-cost alternative: manual only | 0 | No time saving |
| Value of time saved | 22,500 | 1 min × 150/day × 30 days × LKR 300/h [Assumption] |

**Break-even:** recurring cost is LKR 0.7–1.1 per scan, and staff time costs LKR 5 per minute [Assumption]. So the
tool covers its running cost if it saves about **8–13 seconds per prescription** [derived]. The one-off setup is
repaid in about 1–2 months if the 1-minute assumption holds [derived].

## Adoption approach

- **Who pays:** the **pharmacy owner** pays the setup and the monthly AWS bill, about LKR 3,000–5,000 a month at
  the assumed volume. That is small compared with the staff time involved.
- **How:** a **two-week pilot** at the dispensing counter:
  - Staff photograph handwritten prescriptions, review them in the app, and continue to dispense as normal.
  - The app's dashboard records read time, success rate, low-confidence flags and pharmacist corrections.
  - Staff also time a sample of prescriptions with and without the tool.
- **Main condition to proceed:** **measured reading accuracy and time saving in the pilot.** For example: at least
  90% of drug names read correctly, every wrong field flagged or caught at review, and a measurable time saving per
  prescription (above the ~8–13 s break-even) [targets are assumptions to agree with the pharmacist].
- **Before going live:**
  - PDPA review of cross-border processing.
  - Login and HTTPS.
  - Real stock export.
  - A professionally reviewed interaction source.
- **If the condition is not met:** keep manual entry (no cost) or retry with Nova Pro.
