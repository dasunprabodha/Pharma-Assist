# CLAUDE.md – Prescription Reader (IT6070 Assignment II)

This file gives Claude Code the context and the Week 1–2 tasks for this project. Read it fully before making changes.

## 1. Project context

- **Course:** SLIIT MSc/PGD in IT 2026 – IT6070 Emerging Topics in IT, Assignment II (individual, 30 marks)
- **Student:** U.W.D.P. Amarasinghe – MS25903874
- **Professional:** "Pharmacist A" (declared pseudonym), Registered Pharmacist, Dulnitha Pharmacy, Kottawa (independent community retail pharmacy, healthcare)
- **Problem:** ~200 prescriptions/day, 70–80% handwritten; 2–3 min each to decipher, cross-check and match to stock (≈5–8 staff-hours/day); ~3% need a colleague or the prescriber to confirm. Figures are the pharmacist's estimates from a discussion on 30/09/2026.
- **Platform rule: AWS services only** for the running application, billed to the student's AWS account. (Claude is used only as the coding assistant – Claude Code – not inside the app.)
- **Emerging technology:** a multimodal (vision) foundation model – **Amazon Nova on Amazon Bedrock** (Nova 2 Lite by default) – reads handwritten prescriptions into structured data.
- **Alternative considered:** a classic AWS AI-services pipeline – **Amazon Textract** (OCR with handwriting support) → **Amazon Comprehend Medical** (extracts medication, dosage, frequency and duration from text) → rules. Rejected as the main approach because it is two separate steps, OCR errors flow straight into the entity extraction, and Comprehend Medical is tuned to US clinical text and drug names, so local brands and shorthand may be missed. Optionally implement it later as a comparison or backup if time allows.

### Requirements

- **R1 – Read the prescription:** from a phone photo of a handwritten prescription, extract each item's drug name, strength, dose, frequency and duration into an editable list, expanding shorthand (bd, tds, nocte, 1×2, 5/7) and flagging low-confidence fields. The list must appear within 30 s; the pharmacist can correct any field.
- **R2 – Check before dispensing:** match each confirmed item against the stock list and a rule list (interactions, duplicate class, duplicate ingredient) and produce a dispensing checklist with warnings and in-stock generic alternatives. No patient name is sent or stored.

### The two connected features

1. **Feature 1 (Week 2):** photo → AI extraction → editable table → pharmacist confirms. **Fallback:** manual entry form.
2. **Feature 2 (Week 3, not in scope yet):** confirmed list → stock / generic / interaction check → dispensing checklist.

Feature 1's confirmed list (or the manual entry list) is Feature 2's input, so both paths must produce the **same data structure** (see §4).

### Assessment rules that affect the code

- The assignment requires the source as a ZIP plus a repository link, and a README containing software requirements, installation and run steps, API/config information, and known limitations.
- **Never commit API keys, passwords or credentials.** Keys live only in `.env`, which is git-ignored. Provide `.env.example`.
- **Never use or store real patient data.** Use synthetic or redacted samples only. Strip image metadata before sending images to the API.
- All AI assistance must be declared. Append an entry to `docs/ai_use_log.md` for every significant piece of work you (Claude Code) do – see §7.
- Any simulation or external component must be clearly identified (the stock and rule CSVs are simulated data).
- Testing needs at least three test cases, including an error or fallback case, each with input, expected result and actual result.

## 2. Tech stack

> **Stack decision (05/10/2026):** the student chose a **Java Spring Boot backend + Angular frontend** instead of
> Python/Streamlit. AWS-only rule unchanged. Read later sections with this mapping:
> boto3 → AWS SDK for Java v2 (`bedrockruntime`) · Streamlit `app.py` → Angular `frontend/` + Spring REST API ·
> `st.data_editor` → editable Angular table · `st.session_state["confirmed_items"]` → confirmed list held in the
> Angular app and posted to the backend · pytest → JUnit 5 + Mockito · rapidfuzz → a Java fuzzy-match library
> (e.g. `me.xdrop:fuzzywuzzy` WRatio) · Pillow → `javax.imageio` (+ a WEBP plugin) · python-dotenv → `AppConfig`.

- Java 17, Maven 3.6.3+, Node 20 + Angular CLI 19, and the AWS CLI
- **Backend:** Spring Boot 3.5 (`backend/`), package `lk.sliit.prescription`
- **Frontend:** Angular 19 standalone app (`frontend/`); `ng serve` proxies `/api` to `localhost:8080`
- **AWS SDK for Java v2** – Amazon Bedrock Runtime, **Converse API** (`BedrockRuntimeClient.converse()`), with an image content block.
  - **Check the current Bedrock docs for available Amazon Nova model IDs and inference profiles in the chosen region before coding** – IDs change, and some regions need a cross-region inference profile ID (e.g. prefixed `us.`/`apac.`).
  - Make the model configurable with `BEDROCK_MODEL_ID`; default to **Amazon Nova 2 Lite** (`us.amazon.nova-2-lite-v1:0`, verified in the Bedrock model card on 05/10/2026). Region comes from `AWS_REGION` (e.g. `us-east-1`). Because the model is a setting, it can be switched to another Bedrock vision model (e.g. Nova Pro) without code changes.
  - Credentials come from the standard AWS chain (`aws configure` profile or `AWS_PROFILE`). **Never put AWS access keys in code, `.env` committed to git, or the README.**
  - Settings are read by `config/AppConfig.java` (environment variables first, then `.env` in the repo root).
- Do **not** use any non-AWS AI service (no Gemini, OpenAI or Anthropic Console API) inside the app.
- commons-csv for the simulated data
- JUnit 5 + Mockito (tests use a mocked Bedrock client – no real API calls in the test suite)

## 3. Repository structure (repo root)

```
.
├── CLAUDE.md
├── README.md
├── .env.example            # AWS_REGION=, AWS_PROFILE=, BEDROCK_MODEL_ID=
├── .gitignore              # .env, logs/, samples/private/, target/, node_modules/, coursework docs
├── backend/                # Spring Boot (Maven)
│   ├── pom.xml
│   └── src/main/java/lk/sliit/prescription/
│       ├── PrescriptionReaderApplication.java
│       ├── config/         # AppConfig (.env/env vars), BedrockClients (client + credentials)
│       ├── api/            # REST controllers (GET /api/status; Week 2: extraction endpoint)
│       ├── data/           # DataLoader – loads the simulated CSVs
│       ├── model/          # Item, Confidence
│       ├── image/          # ImageUtils – file checks, resize, EXIF strip
│       ├── extractor/      # Extractor – Feature 1: Bedrock (Amazon Nova) call, prompt, JSON parsing, errors
│       ├── namecheck/      # NameCheck – fuzzy-match drug names against the stock list
│       ├── errors/         # custom exception classes
│       └── tools/          # CheckBedrock (model-access check), TrySample (early accuracy check)
├── frontend/               # Angular app
├── logs/                   # usage.csv (git-ignored)
├── data/
│   ├── stock_list.csv      # SIMULATED
│   └── interaction_rules.csv  # SIMULATED, not a clinical database
├── samples/                # synthetic/redacted prescription photos only
└── docs/
    ├── test_log.md
    └── ai_use_log.md
```

## 4. Shared data structure

Every prescription item, from the AI or from manual entry, uses this shape:

```python
@dataclass
class Item:
    drug_name: str | None       # as written, e.g. "Lipitor"
    strength: str | None        # e.g. "20 mg"
    dose: str | None            # e.g. "1 tablet"
    frequency: str | None       # expanded, e.g. "twice daily (bd)"
    duration: str | None        # e.g. "5 days"
    confidence: str             # "high" | "medium" | "low"
    note: str | None = None     # e.g. "drug name not found in stock list"
```

Rules:
- Any field the model cannot read is `None`, and the item's `confidence` becomes `"low"`.
- Invalid confidence values are normalised to `"low"`.
- An item with no `drug_name` is kept but flagged `"low"`, never silently dropped.
- The confirmed list is a list of `Item` (Java record / TypeScript interface with the same snake_case JSON field names).

---

## 5. WEEK 1 (ends 07/10/2026)

### Already done by the student (no code needed)
- [x] Professional identified; discussion held on 30/09/2026
- [x] Problem statement, evidence and requirements R1/R2 written
- [x] Technology choice and alternative decided
- [x] Week 1 progress form and progress presentation completed

### Still to do by the student (outside the code)
- [ ] Written confirmation of consent and the pseudonym from the pharmacist
- [ ] 10–15 redacted or synthetic prescription photos placed in `samples/`
- [ ] Pharmacist reviews `data/stock_list.csv` and `data/interaction_rules.csv`

### AWS setup (student, one-off – Claude Code should remind the student, not do it)
- [ ] In the AWS console, pick a region where Amazon Nova is available on Bedrock (e.g. `us-east-1`) and **enable model access** for the chosen Nova model if the console asks (Bedrock → Model access).
- [ ] Create a least-privilege IAM user or role for development with only the Bedrock invoke permissions needed for the Converse API (`bedrock:InvokeModel`); run `aws configure --profile prescription-reader`.
- [ ] Create an **AWS Budget** (e.g. USD 5/month) with an email alert so testing can never run up an unexpected bill.

### Billing safety for Claude Code itself
- Claude Code should keep using the student's Claude subscription login. **Do not set `ANTHROPIC_API_KEY` or `CLAUDE_CODE_USE_BEDROCK`** in this project or shell – either would switch Claude Code to pay-per-token billing.

### Week 1 tasks for Claude Code
1. Create the repository structure in §3 with empty modules and `__init__.py`.
2. Create `requirements.txt`, `.gitignore`, `.env.example`.
3. Create `data/stock_list.csv` and `data/interaction_rules.csv` exactly as in Appendix A.
4. Create a `README.md` skeleton with these sections: Overview, Professional and problem, Software requirements, Installation, Configuration (API key), Running the app, Running tests, Simulated data, Known limitations, AI use declaration.
5. Create `docs/test_log.md` and `docs/ai_use_log.md` from the templates in §7.
6. Create a minimal `app.py` that shows the title, the pseudonym and the problem in one sentence, and a "Configuration" status box saying whether AWS credentials and `BEDROCK_MODEL_ID` are available (use `boto3.Session().get_credentials()`; never print any credential).
6a. Write `scripts/check_bedrock.py`: sends one tiny text message to the configured model through the Converse API and prints OK or the exact error. The student runs this once to confirm model access is enabled.
6b. **Early accuracy check (do this before building the full UI in Week 2):** `scripts/try_sample.py samples/<file>` sends one sample photo to the configured model and prints the raw JSON, so the student can see whether Nova reads the handwriting well enough.
7. Verify that `streamlit run app.py` starts. Make the first commit: "Project scaffold and simulated data".

**Done when:** the app starts, the CSVs load with pandas without errors, and no secret is tracked by git.

---

## 6. WEEK 2 (ends 14/10/2026) – Build Feature 1

### 6.1 `errors.py`
Define:
- `ConfigError` – missing AWS credentials or region, `AccessDeniedException` (model access not enabled or IAM permission missing), `ValidationException` for an invalid model ID
- `InvalidImageError` – wrong file type, too large, corrupt
- `AIUnavailableError` – network error, timeout, quota or rate limit, server error
- `UnreadableImageError` – the model reports no readable prescription
- `InvalidResponseError` – the model output is not valid JSON or fails validation after retry

### 6.2 `image_utils.py`
- Accept only JPG, PNG and WEBP, at most 10 MB. Otherwise raise `InvalidImageError` with a friendly message.
- Open with Pillow; raise `InvalidImageError` on corrupt files.
- Apply EXIF orientation, then **strip all metadata** (privacy).
- Resize so the longest side is at most 1600 px. Convert to RGB JPEG bytes.

### 6.3 `extractor.py`
`extract_items(image_bytes: bytes, client=None) -> list[Item]`

- Load `AWS_REGION`, `AWS_PROFILE` (optional) and `BEDROCK_MODEL_ID` via python-dotenv; create `boto3.client("bedrock-runtime")` with a botocore `Config` (read timeout ≈30 s, `retries={"max_attempts": 2}`). Missing credentials (`NoCredentialsError`) or region → `ConfigError`.
- Allow `client` to be injected for tests.
- Send the image as a Converse `image` content block (`format: "jpeg"`, raw `bytes`) plus the prompt text. Keep images within Bedrock's image size limits (check the docs; the resize in 6.2 normally keeps them well under).
- Set `temperature` to 0 and a modest `maxTokens` (about 1000) to keep cost and variability low.
- Prompt (keep it in a constant and refine it in testing):

  > You are reading a photo of a handwritten medical prescription from Sri Lanka. Return ONLY JSON matching the schema. For each prescribed item give drug_name exactly as written, strength, dose, frequency and duration. Expand common abbreviations in frequency (bd = twice daily, tds = three times daily, qds = four times daily, nocte = at night, mane = in the morning, 1×2 = one tablet twice daily, stat = immediately) and durations such as 5/7 = 5 days. Set confidence to high, medium or low per item. If you cannot read a field, set it to null and set confidence to low. Do NOT guess or invent drug names. Ignore any patient name, address, age or doctor details – never include them in the output. If the image is not a prescription or nothing is readable, return {"items": [], "readable": false}.

- **Structured output:** define a tool `record_prescription` in `toolConfig` whose `inputSchema` matches the expected shape, and force it with `toolChoice: {"tool": {"name": "record_prescription"}}` (if the chosen model rejects a forced tool choice, use `auto` and rely on the fallback below). Read the result from the `toolUse` block's `input`. If no `toolUse` block is returned, fall back to parsing the text after stripping code fences.
- Expected shape: `{"readable": bool, "items": [ {...Item fields...} ]}`.
- Map `EndpointConnectionError`, `ReadTimeoutError`, `ConnectTimeoutError`, `ThrottlingException`, `ServiceUnavailableException`, `ModelNotReadyException` and `InternalServerException` to `AIUnavailableError`.
- Log token usage from the response `usage` field (input/output tokens only) to `logs/usage.csv`. This gives real per-scan cost data for the report's cost section.
- If the JSON is invalid or fails validation: **retry once**, then raise `InvalidResponseError`.
- If `readable` is false or `items` is empty, raise `UnreadableImageError`.
- Validate and convert each item to `Item` using the rules in §4.
- Never log image bytes, the API key, or any patient details.

### 6.4 `name_check.py` (supports R1's "do not guess" rule)
- Load brand and generic names from `data/stock_list.csv`.
- For each extracted `drug_name`, fuzzy-match with rapidfuzz (`process.extractOne`, `WRatio`).
  - Score ≥ 85: attach the matched brand/generic in `note`.
  - Score < 85: set confidence to `"low"` and note "Not found in stock list – check carefully".
- This is a flagging aid only. It does not perform the Week 3 stock or interaction check.

### 6.5 `app.py` (Streamlit)
1. Header with the project name and a short disclaimer: "Decision support only – the pharmacist must confirm every item."
2. `st.file_uploader` for JPG/PNG/WEBP; show a preview.
3. A "Read prescription" button. Call `image_utils`, then `extractor`, then `name_check` inside `st.spinner`.
4. Show the results in `st.data_editor` with columns drug_name, strength, dose, frequency, duration, confidence and note. Allow adding and deleting rows. Show a legend and a clear warning listing the low-confidence rows (for example "Row 2: low confidence – please verify").
5. Error handling, each with a clear `st.error` or `st.warning` message, then an automatic switch to manual entry:
   - `ConfigError` → "AI not configured (AWS credentials or Bedrock model access) – using manual entry"
   - `InvalidImageError` → show the reason and ask for another file
   - `AIUnavailableError` → "AI service unavailable – using manual entry"
   - `UnreadableImageError` → "Could not read this photo – please retake it, or enter the items manually"
   - `InvalidResponseError` → "AI returned an unexpected result – using manual entry"
6. **Manual entry mode:** an empty `st.data_editor` with the same columns (confidence defaults to "high", because it is human-entered). A toggle also lets the user choose manual entry directly.
7. A "Confirm list" button. It validates that every row has a drug_name, saves to `st.session_state["confirmed_items"]`, and shows a success message with the item count.
8. Show which path produced the list ("AI-extracted" or "Manual entry") – useful for the demo video.

### 6.6 Tests (pytest, mocked client)
At minimum:
- valid JSON response → correct `Item` list
- response wrapped in ```json fences → parsed correctly
- invalid JSON twice → `InvalidResponseError`
- `{"readable": false, "items": []}` → `UnreadableImageError`
- client raises timeout → `AIUnavailableError`
- missing AWS credentials (`NoCredentialsError`) or `AccessDeniedException` → `ConfigError`
- `ThrottlingException` → `AIUnavailableError`
- tool-use response parsed correctly; text-only response falls back to JSON parsing
- `.txt` or oversized file → `InvalidImageError`
- a null field → confidence becomes "low"
- name check: "Lipitr" matches Lipitor; "Xyzzol" is flagged low

### 6.7 Manual test run (with the student)
Run the app on the samples and record the results in `docs/test_log.md`. Required cases for the report:
- **TC1 (success):** a clear synthetic prescription, e.g. "Tab Lipitor 20mg nocte; Tab Klacid 500mg bd × 5/7" → both items extracted correctly
- **TC2 (low confidence):** a messy handwriting sample → uncertain fields flagged, then corrected in the table
- **TC3 (error/fallback):** set an invalid `BEDROCK_MODEL_ID` or disconnect the network → error message and manual entry still works

**Week 2 done when:** all pytest tests pass; TC1–TC3 are recorded with screenshots; the README "Installation", "Configuration" and "Running" sections are complete; the work is committed and pushed.

### Out of scope until Week 3
The stock / generic / interaction check, the dispensing checklist, cost tables and the report. Don't build these yet; keep the `Item` structure stable so Feature 2 can plug in.

---

## 7. Logs to maintain

### `docs/test_log.md`
| ID | Input | Expected result | Actual result | Pass/Fail | Evidence (screenshot file) |
|----|-------|-----------------|---------------|-----------|----------------------------|

### `docs/ai_use_log.md` (the assignment's AI declaration format)
| Date | AI tool | Purpose | Prompt or instruction (summary) | Output used | How the output was verified or corrected |
|------|---------|---------|---------------------------------|-------------|------------------------------------------|

Add a row for every significant task you complete in this repository, for example "Claude Code – generated extractor.py – verified by pytest and a manual test on samples 1–5".

---

## Appendix A – Simulated data files

These are simulated for the proof of concept. Brand names are used for realism; quantities are invented. The rules are textbook examples, **not a clinical database**. Say this in the README.

### `data/stock_list.csv`
```csv
item_id,brand_name,generic_name,strength,dosage_form,therapeutic_class,in_stock,quantity_on_hand
S001,Panadol,paracetamol,500 mg,tablet,analgesic,yes,1200
S002,Paracetamol (generic),paracetamol,500 mg,tablet,analgesic,yes,3000
S003,Brufen,ibuprofen,400 mg,tablet,NSAID,no,0
S004,Ibuprofen (generic),ibuprofen,400 mg,tablet,NSAID,yes,600
S005,Voltaren,diclofenac,50 mg,tablet,NSAID,yes,300
S006,Disprin,aspirin,300 mg,dispersible tablet,antiplatelet / analgesic,yes,200
S007,Aspirin (generic),aspirin,75 mg,tablet,antiplatelet / analgesic,yes,900
S008,Plavix,clopidogrel,75 mg,tablet,antiplatelet,no,0
S009,Clopidogrel (generic),clopidogrel,75 mg,tablet,antiplatelet,yes,450
S010,Warfarin (generic),warfarin,5 mg,tablet,anticoagulant,yes,150
S011,Amoxil,amoxicillin,500 mg,capsule,antibiotic - penicillin,yes,400
S012,Augmentin,co-amoxiclav,625 mg,tablet,antibiotic - penicillin,yes,120
S013,Klacid,clarithromycin,500 mg,tablet,antibiotic - macrolide,yes,80
S014,Zithromax,azithromycin,500 mg,tablet,antibiotic - macrolide,no,0
S015,Azithromycin (generic),azithromycin,500 mg,tablet,antibiotic - macrolide,yes,90
S016,Ciprobay,ciprofloxacin,500 mg,tablet,antibiotic - fluoroquinolone,yes,100
S017,Flagyl,metronidazole,400 mg,tablet,antibiotic - nitroimidazole,yes,250
S018,Norvasc,amlodipine,5 mg,tablet,antihypertensive - calcium channel blocker,yes,500
S019,Cozaar,losartan,50 mg,tablet,antihypertensive - ARB,no,0
S020,Losartan (generic),losartan,50 mg,tablet,antihypertensive - ARB,yes,700
S021,Renitec,enalapril,5 mg,tablet,antihypertensive - ACE inhibitor,yes,300
S022,Tenormin,atenolol,50 mg,tablet,antihypertensive - beta blocker,yes,350
S023,Glucophage,metformin,500 mg,tablet,antidiabetic - biguanide,yes,1500
S024,Lipitor,atorvastatin,20 mg,tablet,statin,no,0
S025,Atorvastatin (generic),atorvastatin,20 mg,tablet,statin,yes,800
S026,Zocor,simvastatin,20 mg,tablet,statin,yes,200
S027,Losec,omeprazole,20 mg,capsule,proton pump inhibitor,yes,600
S028,Nexium,esomeprazole,20 mg,tablet,proton pump inhibitor,yes,150
S029,Motilium,domperidone,10 mg,tablet,antiemetic,yes,400
S030,Clarityne,loratadine,10 mg,tablet,antihistamine,yes,250
S031,Zyrtec,cetirizine,10 mg,tablet,antihistamine,yes,300
S032,Piriton,chlorphenamine,4 mg,tablet,antihistamine,yes,500
S033,Prednisolone (generic),prednisolone,5 mg,tablet,corticosteroid,yes,400
S034,Ventolin,salbutamol,100 mcg/dose,inhaler,bronchodilator,yes,40
```

### `data/interaction_rules.csv`
```csv
rule_id,rule_type,item_a,item_b,severity,warning_message
R001,interaction,warfarin,aspirin,major,Increased bleeding risk. Confirm with prescriber.
R002,interaction,warfarin,ibuprofen,major,NSAID with warfarin increases bleeding risk.
R003,interaction,warfarin,diclofenac,major,NSAID with warfarin increases bleeding risk.
R004,interaction,warfarin,metronidazole,major,Metronidazole can markedly raise warfarin effect (INR).
R005,interaction,warfarin,clarithromycin,major,Clarithromycin can raise warfarin effect (INR).
R006,interaction,warfarin,ciprofloxacin,moderate,Ciprofloxacin may raise warfarin effect (INR).
R007,interaction,simvastatin,clarithromycin,major,Raised simvastatin levels; risk of muscle damage. Combination normally avoided.
R008,interaction,atorvastatin,clarithromycin,moderate,Raised atorvastatin levels; monitor for muscle pain.
R009,interaction,clopidogrel,omeprazole,moderate,Omeprazole may reduce clopidogrel's antiplatelet effect.
R010,interaction,clopidogrel,esomeprazole,moderate,Esomeprazole may reduce clopidogrel's antiplatelet effect.
R011,interaction,clopidogrel,aspirin,moderate,Increased bleeding risk; often intentional (dual antiplatelet). Confirm intent.
R012,interaction,aspirin,ibuprofen,moderate,Ibuprofen may reduce aspirin's antiplatelet effect; added stomach bleeding risk.
R013,interaction,enalapril,ibuprofen,moderate,NSAID may reduce blood-pressure control and affect kidney function.
R014,interaction,losartan,ibuprofen,moderate,NSAID may reduce blood-pressure control and affect kidney function.
R015,interaction,enalapril,losartan,major,Dual ACE inhibitor + ARB blockade; raised risk of high potassium and kidney problems.
R016,interaction,ciprofloxacin,prednisolone,moderate,Fluoroquinolone with corticosteroid raises tendon rupture risk.
R017,duplicate_class,NSAID,NSAID,moderate,Two NSAIDs prescribed together.
R018,duplicate_class,statin,statin,moderate,Two statins prescribed together.
R019,duplicate_class,proton pump inhibitor,proton pump inhibitor,minor,Two PPIs prescribed together.
R020,duplicate_class,antihistamine,antihistamine,minor,Two antihistamines prescribed together; check for drowsiness.
R021,duplicate_generic,any,any,moderate,Same active ingredient appears twice (e.g. two paracetamol products).
```
