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

_Status: Week 1 scaffold. Feature 1 (photo → AI extraction → editable table) is built in Week 2._

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
| GET | `/api/status` | Configuration status: whether AWS credentials resolve (never their values), region, model ID, simulated data row counts |

_TODO (Week 2): document the extraction endpoint._

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

Open http://localhost:4200. The **Configuration** box shows whether AWS credentials and `BEDROCK_MODEL_ID` are available.

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

Tests never call AWS (the Bedrock client is mocked from Week 2). Manual test cases are recorded in
[docs/test_log.md](docs/test_log.md).

## Simulated data

`data/stock_list.csv` and `data/interaction_rules.csv` are **simulated** for this proof of concept.
Brand names are used for realism; quantities are invented. The interaction rules are textbook examples and
**not a clinical database** – they must not be used for real dispensing decisions.

Prescription images in `samples/` are synthetic or redacted only. No real patient data is used or stored, and
image metadata is stripped before any image is sent to Amazon Bedrock.

## Known limitations

- Week 1 scaffold only: AI extraction, the editable table and the stock/interaction check are not built yet.
- `TrySample` accepts JPG/PNG only and does not yet apply EXIF orientation (added in Week 2 `ImageUtils`).
- Handwriting accuracy of Amazon Nova on local prescriptions has not been measured yet.
- Simulated stock and rule data (see above).

_TODO: update after testing._

## AI use declaration

Claude (Anthropic), used through Claude Code, was used as a coding assistant to generate parts of this
repository. It is not part of the running application. Every use is recorded in
[docs/ai_use_log.md](docs/ai_use_log.md) with how the output was verified.
