# Prescription Reader

IT6070 Emerging Topics in IT – Assignment II · U.W.D.P. Amarasinghe (MS25903874)

> **Decision support only – the pharmacist must confirm every item.**

## Overview

A proof-of-concept web app that reads a phone photo of a handwritten prescription into an editable,
structured list (drug name, strength, dose, frequency, duration), using a multimodal foundation model –
**Amazon Nova 2 Lite on Amazon Bedrock**. The confirmed list is then checked against the pharmacy's stock
and a simple interaction rule list (Week 3).

- **Backend:** Java 17, Spring Boot 3.5, AWS SDK for Java v2 (Bedrock Runtime Converse API) – `backend/`
- **Frontend:** Angular 19 – `frontend/`
- **Platform:** AWS services only. No non-AWS AI service is used by the app.

_Status: Feature 1 built (photo → AI extraction → editable table → pharmacist confirms, with manual-entry fallback)
plus a dashboard, history and reference-data views. Feature 2 (dispensing check) is Week 3._

### What the app does

| Page | Purpose |
|------|---------|
| **New prescription** | Upload a photo (JPG, PNG, WEBP) or a PDF (up to 3 pages), or take a photo → Amazon Nova reads it → editable table beside the photo, low-confidence rows flagged ("Row 2: low confidence – please verify"), drug names matched against the stock list, pharmacist edits tracked → **Confirm list**. Any AI error switches to manual entry automatically; manual entry can also be chosen directly. |
| **Dashboard** | Confirmed lists, AI read success rate, average/p95 read time vs the 30 s target, estimated cost per scan and per 1,000 scans, pharmacist corrections per AI list, scans per day, outcomes, confidence mix, most-prescribed drugs, recent scans. |
| **History** | Confirmed lists (search, filter AI/manual, expand items, export CSV). |
| **Stock & rules** | Read-only view of the simulated stock list and interaction rules. |
| **Settings** | Whether AWS credentials, region and model ID are configured; **Test connection** sends one tiny message to the model. |

### Design documents

- [Architecture](docs/architecture.md) – diagram, components, data flow and failure paths
- [Feasibility study (draft)](docs/feasibility.md) – technical, data, skills, time, privacy (PDPA), benefits, limitations, risks, costs, adoption
- Cost estimate: `python3 scripts/cost_estimate.py` (Python 3 standard library; reads `logs/usage.csv`; `--help` lists the settings)

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
git clone <repository-url>
cd <repository>

# Backend dependencies + tests
cd backend
mvn test
cd ..

# Frontend dependencies
cd frontend
npm install
cd ..
```

_TODO (Week 2): complete with screenshots/notes from a clean install._

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

| Method | Path | Description |
|--------|------|-------------|
All JSON uses snake_case field names. Base URL: `http://localhost:8080`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/status` | Configuration status: whether AWS credentials resolve (never their values), region, model ID, simulated data row counts |
| POST | `/api/status/check-bedrock` | Sends one tiny text message to the configured model; returns `ok`, reply or error |
| POST | `/api/preview` | Multipart `file` → `{pages: [data URL…]}` – the page images that would be sent to the model (renders PDFs for preview; no AWS call) |
| POST | `/api/scans` | Multipart `file` (JPG/PNG/WEBP, or PDF ≤ 3 pages; ≤ 10 MB) → `{scan_id, pages, items[], low_confidence_rows[], input_tokens, output_tokens, latency_ms, estimated_cost_usd}`. PDF pages are rendered to images at 200 DPI and sent as one image block per page |
| POST | `/api/name-check` | Body: `Item[]` → the same items with stock-list match notes / low-confidence flags |
| POST | `/api/prescriptions` | Body: `{source: "ai"\|"manual", scan_id, items[], corrections}` → saves the confirmed list (every row needs `drug_name`) |
| GET | `/api/prescriptions` | Confirmed lists, newest first |
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

Optional settings: `PRICE_INPUT_PER_1M` / `PRICE_OUTPUT_PER_1M` (cost estimate, default $0.33 / $2.75 for Nova 2 Lite on the US
inference profile), `LOGS_DIR` (default `logs/`).

**Logs** (git-ignored): `logs/usage.csv` – one row per scan (outcome, item counts, input/output tokens, latency; no images or
patient data); `logs/confirmed.jsonl` – confirmed drug lists.

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

46 JUnit tests; none call AWS (the Bedrock client is mocked with Mockito). They cover: tool-use and text responses,
JSON in code fences, invalid JSON twice → `InvalidResponseError`, unreadable image, timeout / throttling → `AIUnavailableError`,
missing credentials / AccessDenied / invalid model ID → `ConfigError`, forced tool choice rejected → retry with `auto`,
`.txt` / oversized / corrupt files → `InvalidImageError`, PDFs (1-page and scanned-image PDFs rendered, metadata dropped, more than 3 pages / password-protected / corrupt rejected), multi-page requests, resize + metadata strip, EXIF orientation, Item validation rules,
fuzzy name check ("Lipitr" → Lipitor, "Xyzzol" flagged low), CSV loading, and API error mapping / confirm validation.
Manual test cases are recorded in [docs/test_log.md](docs/test_log.md).

## Simulated data

`data/stock_list.csv` and `data/interaction_rules.csv` are **simulated** for this proof of concept.
Brand names are used for realism; quantities are invented. The interaction rules are textbook examples and
**not a clinical database** – they must not be used for real dispensing decisions.

Prescription images in `samples/` are synthetic or redacted only. No real patient data is used or stored, and
image metadata is stripped before any image is sent to Amazon Bedrock.

## Known limitations

- The stock / generic / interaction check and dispensing checklist (Feature 2) are not built yet.
- Handwriting accuracy of Amazon Nova on local prescriptions has not been measured on the full sample set yet.
- Any empty field (e.g. no duration written for a long-term medicine) makes the item low confidence, by design (CLAUDE.md §4).
- Name matching is fuzzy (WRatio ≥ 85) against a 34-item simulated list – a flagging aid, not a drug database.
- Cost figures are estimates from token counts × configured prices; the AWS bill is authoritative.
- Single-user proof of concept: no login; history is stored in local files.
- PDFs are limited to 3 pages (one prescription); each page costs image tokens, so a 3-page PDF costs about three times a photo.
- Simulated stock and rule data (see above).

_TODO: update after testing._

## AI use declaration

Claude (Anthropic), used through Claude Code, was used as a coding assistant to generate parts of this
repository. It is not part of the running application. Every use is recorded in
[docs/ai_use_log.md](docs/ai_use_log.md) with how the output was verified.
