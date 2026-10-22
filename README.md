# Prescription Reader

IT6070 Emerging Topics in IT – Assignment II · U.W.D.P. Amarasinghe (MS25903874)

> **Decision support only – the pharmacist must confirm every item.**

## Overview

A proof-of-concept web app that reads a phone photo of a handwritten prescription into an editable,
structured list (drug name, strength, dose, frequency, duration), using a multimodal foundation model –
**Amazon Nova 2 Lite on Amazon Bedrock** (Feature 1). The confirmed list is then checked against the pharmacy's
stock list and an interaction rule list to produce a dispensing checklist (Feature 2).

- **Backend:** Java 17, Spring Boot 3.5, AWS SDK for Java v2 (Bedrock Runtime Converse API) – `backend/`
- **Frontend:** Angular 19 – `frontend/`
- **Platform:** AWS services only. No non-AWS AI service is used by the app.

_Status (18/10/2026): both features built and tested – Feature 1 (photo/PDF → AI extraction → editable table →
pharmacist confirms, with manual-entry fallback) and Feature 2 (dispensing check with stock, in-stock alternatives,
interaction warnings and pack images) – plus a dashboard, history and reference-data views._

**Login:** not applicable – **no login is needed**. This is a single-user proof of concept that runs on the
pharmacist's or student's computer (a login would be added before real use; see Known limitations).

### What the app does

| Page | Purpose |
|------|---------|
| **New prescription** | Upload a photo (JPG, PNG, WEBP) or a PDF (up to 3 pages), or take a photo → Amazon Nova reads it → editable table beside the photo, low-confidence rows flagged ("Row 2: low confidence – please verify"), drug names matched against the stock list with the **pack image** of the matched product, pharmacist edits tracked → **Confirm list** → the **dispensing check** runs automatically. Any AI error switches to manual entry automatically; manual entry can also be chosen directly. |
| **Dispensing check** (after Confirm, and in History) | Per item: the pack to dispense (image), stock status, in-stock alternatives with the same active ingredient, notes (e.g. strength differs); warnings for interactions, duplicate classes and duplicate active ingredients (major → moderate → minor); items not in the stock list are flagged "not checked"; a **Checked** box per item and **Download checklist (CSV)**. |
| **Dashboard** | Confirmed lists, AI read success rate, average/p95 read time vs the 30 s target, estimated cost per scan and per 1,000 scans, pharmacist corrections per AI list, scans per day, outcomes, confidence mix, most-prescribed drugs, recent scans. |
| **History** | Confirmed lists (search, filter AI/manual, expand items, export CSV, run the dispensing check again). |
| **Stock & rules** | Read-only view of the simulated stock list and interaction rules. |
| **Settings** | Whether AWS credentials, region and model ID are configured; **Test connection** sends one tiny message to the model. |

### Design documents

- [Architecture](docs/architecture.md) – diagram, components, data flow and failure paths
- [Feasibility study](docs/feasibility.md) – technical, data, skills, time, privacy (PDPA), benefits, limitations, risks, costs, adoption
- [Accuracy evaluation](docs/evaluation.md) – 9 redacted Sri Lankan samples, prompts v1–v3 and Nova Pro: `python3 scripts/evaluate.py --api v2=http://localhost:8080` (real Bedrock calls; ground truth in `samples/ground_truth.json`)
- Cost estimate: `python3 scripts/cost_estimate.py` (Python 3 standard library; reads `logs/usage.csv`; itemised setup and recurring costs in LKR; `--help` lists the settings)
- [Test log](docs/test_log.md) · [Report draft](docs/report_draft.md) · [Demo script](docs/demo_script.md) · [AI use declaration](docs/ai_use_table.md)

## Professional and problem

"Pharmacist A" (declared pseudonym), Registered Pharmacist at Dulnitha Pharmacy, Kottawa – an independent
community retail pharmacy. The pharmacy handles about 200 prescriptions a day, 70–80% handwritten. Each takes
2–3 minutes to decipher, cross-check and match to stock (≈5–8 staff-hours a day), and about 3% need a colleague
or the prescriber to confirm. (Figures are the pharmacist's estimates from a discussion on 30/09/2026.)

- **R1 – Read the prescription:** extract each item into an editable list within 30 s, expanding shorthand
  (bd, tds, nocte, 1×2, 5/7) and flagging low-confidence fields.
- **R2 – Check before dispensing:** match confirmed items against stock and rules; produce a dispensing
  checklist with warnings and in-stock generic alternatives. No patient name is sent or stored.

## Software requirements

| Software | Version used | Notes |
|----------|--------------|-------|
| Java (JDK) | 17 | Spring Boot 3 requires 17+ |
| Maven | 3.6.3+ | |
| Node.js / npm | 20 / 10 | |
| Angular CLI | 19 | `npm install -g @angular/cli@19` (optional – `npx ng` also works) |
| AWS CLI | v2 | for `aws configure` |
| AWS account | – | Amazon Bedrock with access to Amazon Nova 2 Lite |

## Installation

```bash
git clone https://github.com/dasunprabodha/Pharma-Assist.git
cd Pharma-Assist

# Backend dependencies + tests
cd backend
mvn test
cd ..

# Frontend dependencies (exact versions from package-lock.json) and a production build
cd frontend
npm ci
npx ng build
cd ..
```

These steps were verified on a fresh clone (see *Running tests*).

## Configuration (API key)

The app uses **no API key in the code or repository**. It authenticates to Amazon Bedrock with the
standard AWS credentials chain.

1. Create a least-privilege IAM user/role with `bedrock:InvokeModel` and run
   `aws configure --profile prescription-reader` (credentials are stored by the AWS CLI in `~/.aws/`, outside the repository).
2. Copy `.env.example` to `.env` (git-ignored) and adjust:

| Variable | Example | Purpose |
|----------|---------|---------|
| `AWS_REGION` | `us-east-1` | Region where Amazon Nova is available on Bedrock |
| `AWS_PROFILE` | `prescription-reader` | Named AWS CLI profile (optional; otherwise the default chain) |
| `BEDROCK_MODEL_ID` | `us.amazon.nova-2-lite-v1:0` | Bedrock model / inference profile ID; switch to e.g. `us.amazon.nova-pro-v1:0` without code changes |

Environment variables take precedence over `.env`.

3. Check model access once:

```bash
cd backend
mvn -q compile exec:java -Dexec.mainClass=lk.sliit.prescription.tools.CheckBedrock
```

### API

All JSON uses snake_case field names. Base URL: `http://localhost:8080`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/status` | Configuration status: whether AWS credentials resolve (never their values), region, model ID, simulated data row counts |
| POST | `/api/status/check-bedrock` | Sends one tiny text message to the configured model; returns `ok`, reply or error |
| POST | `/api/preview` | Multipart `file` → `{pages: [data URL…]}` – the page images that would be sent to the model (renders PDFs for preview; no AWS call) |
| POST | `/api/scans` | Multipart `file` (JPG/PNG/WEBP, or PDF ≤ 3 pages; ≤ 10 MB) → `{scan_id, pages, items[], low_confidence_rows[], input_tokens, output_tokens, latency_ms, estimated_cost_usd}`. PDF pages are rendered to images at 200 DPI and sent as one image block per page |
| POST | `/api/name-check` | Body: `Item[]` → the same items with stock-list match notes / low-confidence flags |
| POST | `/api/name-suggestions` | Body: drug names (`string[]`) → up to 3 stock-list names per name (`item_id`, `name`, `generic_name`, `strength`, `in_stock`, `score`), best first, for the "Did you mean…?" chips |
| POST | `/api/stock-matches` | Body: `[{drug_name, strength}]` → the stock item each name resolves to (same matcher as the check) or `null`, with `image_url` |
| POST | `/api/check` | **Feature 2.** Body: `{items: Item[]}` (AI-extracted or manual) → `{checked_at, rows[], warnings[]}`: per row the matched product, generic, stock status, quantity, alternatives, notes and `image_url`; warnings with `severity`, `rule_id`, `rule_type`, `rows`, `message`, sorted major → moderate → minor |
| GET | `/api/stock/{item_id}/image` | Pack image: the pharmacy's own photo `data/drug_images/<item_id>.jpg/.png/.webp` if present, otherwise a generated SVG illustration labelled "not a product photo" (`X-Image-Kind: photo` or `illustration`) |
| POST | `/api/prescriptions` | Body: `{source: "ai"\|"manual", scan_id, items[], corrections}` → saves the confirmed list (every row needs `drug_name`) |
| GET | `/api/prescriptions` | Confirmed lists, newest first, each with `image_pages` / `image_expires_at` (saved image, if any) |
| GET | `/api/prescriptions/{id}/images/{page}` | Saved prescription image page (JPEG, `Cache-Control: no-store`); 404 if none, deleted or expired |
| DELETE | `/api/prescriptions/{id}/images` | Delete a confirmed list's saved image now (the drug list is kept) |
| GET | `/api/dashboard` | Aggregated figures for the dashboard |
| GET | `/api/stock`, `/api/rules` | Simulated reference data |

`Item` = `{drug_name, strength, dose, frequency, duration, confidence: "high"|"medium"|"low", note}`.

**Errors** return `{error_type, message, detail, fallback_to_manual}`:

| error_type | HTTP | UI behaviour |
|------------|------|--------------|
| `invalid_image` | 400 | Shows the reason, asks for another file |
| `unreadable_image` | 422 | "Could not read this photo – please retake it, or enter the items manually" → manual entry |
| `config_error` | 503 | "AI not configured (AWS credentials or Bedrock model access) – using manual entry" |
| `ai_unavailable` | 503 | "AI service unavailable – using manual entry" |
| `invalid_response` | 502 | "AI returned an unexpected result – using manual entry" (after one retry) |
| `data_error` | 503 | "Stock/rule data unavailable – the dispensing check cannot run" (the confirmed list is still shown) |

**Pack images:** to show real products, put the pharmacy's own photos in `data/drug_images/` named by `item_id`
(e.g. `S024.jpg`). No internet images are used; without a photo the app draws a labelled illustration.

Optional settings: `PROMPT_VERSION` (`v2` default; `v1`, `v3` for comparison – see the evaluation), `PRICE_INPUT_PER_1M` / `PRICE_OUTPUT_PER_1M` (cost estimate, default $0.33 / $2.75 for Nova 2 Lite on the US
inference profile), `LOGS_DIR` (default `logs/`).

**History images (optional, off by default):** `KEEP_IMAGES=true` keeps the processed image of each *confirmed* AI list so
History can show it next to the drug list; `IMAGE_RETENTION_DAYS` (default 7) sets when it is deleted automatically.
- Only the processed image is kept (metadata stripped, resized to ≤ 1600 px JPEG); the original upload is never stored.
- After a scan the image is held in memory only and written to disk only when the pharmacist confirms the list; unconfirmed
  scans are forgotten after 30 minutes. Manual-entry lists have no image.
- Images are deleted hourly once older than the retention period (also if `KEEP_IMAGES` is later turned off), or at once
  with **Delete image** in History.
- A prescription photo usually shows the patient's name, so leave this off unless the pharmacist needs it, and use only
  synthetic or redacted samples during testing.

**Logs** (git-ignored): `logs/usage.csv` – one row per scan (outcome, item counts, input/output tokens, latency; no images or
patient data); `logs/confirmed.jsonl` – confirmed drug lists; `logs/scan-images/` – saved History images (only with
`KEEP_IMAGES=true`).

## Running the app

Two terminals:

```bash
# Terminal 1 – backend on http://localhost:8080
cd backend
mvn spring-boot:run

# Terminal 2 – frontend on http://localhost:4200 (proxies /api to the backend)
cd frontend
npx ng serve
```

Open http://localhost:4200. The top-right pill shows **AI ready** when AWS credentials are found; Settings → **Test connection**
confirms model access. Restart the backend after changing `.env` or the AWS profile.

**Early accuracy check** (sends one synthetic/redacted sample to Nova and prints the raw JSON):

```bash
cd backend
mvn -q compile exec:java -Dexec.mainClass=lk.sliit.prescription.tools.TrySample -Dexec.args="../samples/<file>.jpg"
```

## Running tests

```bash
cd backend
mvn test
```

82 JUnit tests; none call AWS (the Bedrock client is mocked with Mockito). **Feature 2:** Lipitor → out of stock →
"Atorvastatin (generic)"; Warfarin + Disprin → major R001; Brufen + Voltaren → duplicate NSAID R017; Panadol + Paracetamol
(generic) → duplicate generic R021; Atorvastatin + Klacid → R008 in either order; unknown "Xyzzol" flagged without a crash;
empty list → empty checklist; missing or malformed CSV → `DataError`; no in-stock alternative; strength mismatch; pack
images (illustration, pharmacy photo, path tricks rejected, SVG escaping). **Feature 1:** tool-use and text responses,
JSON in code fences, invalid JSON twice → `InvalidResponseError`, unreadable image, timeout / throttling → `AIUnavailableError`,
missing credentials / AccessDenied / invalid model ID → `ConfigError`, forced tool choice rejected → retry with `auto`,
`.txt` / oversized / corrupt files → `InvalidImageError`, PDFs (1-page and scanned-image PDFs rendered, metadata dropped, more than 3 pages / password-protected / corrupt rejected), multi-page requests, resize + metadata strip, EXIF orientation, Item validation rules,
fuzzy name check ("Lipitr" → Lipitor, "Xyzzol" flagged low), CSV loading, and API error mapping / confirm validation.
Frontend: `cd frontend && npx ng build` must finish without errors.

Manual test cases TC1–TC4 (success, messy handwriting, error/fallback, major interaction) are recorded in
[docs/test_log.md](docs/test_log.md); the accuracy evaluation is in [docs/evaluation.md](docs/evaluation.md).

## Simulated data

`data/stock_list.csv` and `data/interaction_rules.csv` are **simulated** for this proof of concept.
Brand names are used for realism; quantities are invented. The interaction rules are textbook examples and
**not a clinical database** – they must not be used for real dispensing decisions.

Prescription images in `samples/` are synthetic or redacted only. No real patient data is used or stored, and
image metadata is stripped before any image is sent to Amazon Bedrock.

## Known limitations

- **Handwriting accuracy:** clear prescriptions are read correctly (TC1), but on dense cursive Sri Lankan prescriptions only
  about half of the drug names and ~42% of fields were correct; the model sometimes adds a duration that is not written
  and can miss an item without a flag ([evaluation](docs/evaluation.md)). The pharmacist must check every item against the photo.
- **Small test set:** 9 redacted Sri Lankan prescriptions plus synthetic samples – indicative, not statistically conclusive.
- **Not a clinical database:** the interaction rules are 21 simulated textbook examples; the stock list has 34 simulated items.
- **No stock-system integration:** the stock list is a CSV, not linked to the pharmacy's live stock.
- **Pack images are illustrations** unless the pharmacy adds its own photos to `data/drug_images/`.
- **Internet required** for the AI reading (manual entry and the dispensing check work offline from the AI).
- **Overseas processing:** images are processed in AWS us-east-1 (USA); Sri Lanka's PDPA must be checked before real use.
- Any empty field (e.g. no duration written for a long-term medicine) makes the item low confidence, by design (CLAUDE.md §4).
- Name matching is fuzzy (WRatio ≥ 85) against a 34-item simulated list – a flagging aid, not a drug database.
- Cost figures are estimates from token counts × configured prices; the AWS bill is authoritative.
- Single-user proof of concept: **no login**, no HTTPS; history is stored in local files.
- With `KEEP_IMAGES=true`, saved images (which may show patient details) are plain files in `logs/scan-images/` – not
  encrypted and not access-controlled beyond the local machine; they are protected only by short retention and deletion.
- PDFs are limited to 3 pages (one prescription); each page costs image tokens, so a 3-page PDF costs about three times a photo.
- Simulated stock and rule data (see above).

## Submission

`scripts/make_zip.sh` builds `submission/MS25903874_prescription_reader.zip` from **tracked files only** (no `.env`,
`logs/`, `node_modules/`, build output, redacted samples or screenshots).

## AI use declaration

AI tools were used and are declared. Claude (Anthropic), through Claude Code, was used as a coding assistant to generate
much of this repository under the student's direction; it is **not** part of the running application (the app uses only
Amazon Nova on Amazon Bedrock). The declaration table is in [docs/ai_use_table.md](docs/ai_use_table.md); the detailed
log of every Claude Code task, with how each output was verified, is in [docs/ai_use_log.md](docs/ai_use_log.md).
