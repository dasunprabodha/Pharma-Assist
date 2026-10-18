# Demo video script (target 3:00 – maximum 4:00)

Covers every element the brief requires: name and ID · professional and problem · running software in the workflow ·
both features · how the emerging technology is integrated · one success and one error/fallback case · one key
limitation · setup and recurring cost.

## Before recording (5 minutes)

1. Terminal 1: `cd backend && mvn spring-boot:run` (the real model). Terminal 2: `cd frontend && npx ng serve`.
2. Terminal 3 – a second backend with a **wrong model ID** for the fallback scene:
   `cd backend && BEDROCK_MODEL_ID=bogus-model mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`
   and a frontend for it: `cd frontend && npx ng serve --port 4202 --proxy-config proxy-8082.json` with a file
   `proxy-8082.json` containing `{"/api":{"target":"http://localhost:8082","secure":false}}`.
   *(Simpler alternative: set `BEDROCK_MODEL_ID=bogus-model` in `.env` and restart the backend for that scene.)*
3. Open http://localhost:4200, check the pill says **AI ready**, and open Settings → **Test connection** once.
4. Have `samples/synthetic_tc1.png` ready. Use only synthetic or redacted images on screen.
5. Browser zoom 110–125% so text is readable; close other tabs; turn notifications off.

## Script

Every scene shows its **requirement label** on screen (a caption added while editing, or simply the slide title), so the
marker can tick off the seven required elements while watching.

| Time | Requirement shown on screen | On screen and actions | Say |
|------|-----------------------------|-----------------------|-----|
| 0:00–0:12 | **Student name and ID** | Slide 1 (title) – caption "U.W.D.P. Amarasinghe · MS25903874" | "Hello, my name is U.W.D.P. Amarasinghe and my student ID is MS25903874. This is my IT6070 Assignment II: a Prescription Reader built with Amazon Nova on AWS." |
| 0:12–0:39 | **Professional, role and specific problem** | Slide 2 (the four numbers); point at each | "The professional is a Registered Pharmacist in healthcare, at Dulnitha Pharmacy, an independent community pharmacy in Kottawa. I use the declared pseudonym Pharmacist A. The specific problem: about 200 prescriptions a day, most of them handwritten, each taking two to three minutes to read, check and match to stock – five to eight staff-hours a day – and a misread drug is a patient-safety risk." |
| 0:39–1:19 | **Running software in the workflow · Feature 1 · Successful test (TC1)** | App → New prescription → upload `samples/synthetic_tc1.png` → Read prescription (keep the real ~3 s wait) → point at the table, a low-confidence row and a pack image → mark rows 1 and 3 High → Confirm list | "Here is the pharmacist's workflow. A patient hands over a handwritten prescription, and the pharmacist photographs it. This is my successful test case. Feature one, Read the prescription: I upload the photo; the server removes hidden data and sends it to Amazon Nova. In about three seconds every item appears – drug, strength, dose, frequency and duration – with a confidence level, beside the photo. Uncertain rows are highlighted, and each drug shows its stock match and pack image. All correct, so I confirm." |
| 1:19–1:43 | **Feature 2** | The dispensing check appears → point at the R008 warning → Lipitor "Out of stock" and the generic alternative → tick the Checked boxes → Download checklist (CSV) | "Feature two, Check before dispensing, runs on the confirmed list. It warns that atorvastatin and clarithromycin interact, shows that Lipitor is out of stock and offers the in-stock generic, and shows each pack to pick. The pharmacist ticks each item, downloads the checklist, and dispenses." |
| 1:43–2:06 | **How the emerging technology is integrated** | Slide 5 (architecture); trace browser → API → Bedrock → table → checker | "How the emerging technology is integrated: Amazon Nova 2 Lite is a multimodal foundation model on Amazon Bedrock. My Spring Boot backend sends it the prepared image and a prompt through the Converse API and forces a fixed answer format; the answer is validated, matched to stock and passed to the check." |
| 2:06–2:29 | **Error / fallback situation (TC3)** | Wrong-model app (port 4202) → upload → Read prescription → point at "AI not configured – using manual entry" → type Lipitor 20 mg → Confirm → the checklist appears | "Now the error case. I start the app with a wrong model name. Instead of failing, it explains that the AI is not configured and switches to manual entry. I type the item and confirm – and the dispensing check still runs, because both paths produce the same list." |
| 2:29–2:45 | **Key limitation** | Slide 10 (accuracy box); point at "52% of drug names" and "100% of wrong items flagged" | "One key limitation: on dense real handwriting Nova read only about half of the drug names correctly and sometimes added an unwritten duration – although it flagged every wrong item. So it is decision support: the pharmacist checks everything." |
| 2:45–3:00 | **Setup and recurring costs** | Slide 11 (costs); point at the two totals | "The estimated setup cost is about 25,000 rupees, one-off, and the recurring cost about 5,900 rupees a month – roughly one rupee thirty per prescription. It pays for itself if it saves sixteen seconds per prescription. Thank you." |

**Length:** 3:00 (the rubric's "Excellent" level). The brief's hard limit is 4:00 – anything after 4:00 may not be marked. If a
rehearsal runs long, shorten scene 5 first; never cut scenes 1, 2, 6, 7 or 8, which carry required elements.

## Checklist after recording
- Length ≤ 3:00 (never over 4:00); voice clearly audible; name and ID visible at the start.
- No real patient data, AWS keys or `.env` contents on screen (Settings shows only "found", never the keys).
- Upload the video and check the link opens without a login.
