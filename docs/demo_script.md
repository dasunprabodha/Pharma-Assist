# Demo video script (target 3:00 – hard limit 4:00)

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

| Time | On screen | Say (simple, about 150 words per minute) |
|------|-----------|-------------------------------------------|
| 0:00–0:12 | Title slide or the app's Dashboard | "Hello, I'm U.W.D.P. Amarasinghe, student ID MS25903874. This is my IT6070 Assignment II: a Prescription Reader built with Amazon Nova on AWS." |
| 0:12–0:35 | Slide: Pharmacist A, Dulnitha Pharmacy, Kottawa; the four numbers | "My professional is a registered pharmacist at Dulnitha Pharmacy in Kottawa – I use the pseudonym Pharmacist A. They handle about 200 prescriptions a day, most of them handwritten, and spend two to three minutes on each one reading it and matching it to stock – five to eight staff-hours a day. Misreading a drug is also a safety risk." |
| 0:35–1:15 | **Feature 1 (TC1 success):** New prescription → upload `synthetic_tc1.png` → Read prescription → table beside the photo → point at a low-confidence row, the stock-match note and the **pack image** → Confirm list | "Feature one reads the prescription. I upload a photo; the server removes all hidden data from the image and sends it to Amazon Nova 2 Lite on Amazon Bedrock. In about three seconds Nova returns each item – drug, strength, dose, frequency and duration – with a confidence level. The pharmacist sees the photo and the table side by side. Uncertain rows are highlighted, each drug is matched to the stock list with a picture of the pack, and every cell can be corrected. Here everything is right, so I confirm the list." |
| 1:15–1:38 | **Feature 2:** the dispensing check appears → the R008 warning → Lipitor out of stock with the generic alternative and its image → tick Checked → Download CSV | "Feature two checks the confirmed list before dispensing. It finds that atorvastatin and clarithromycin interact, shows that Lipitor is out of stock and offers the in-stock generic, and shows the pack to pick. The pharmacist ticks each item and can download the checklist." |
| 1:38–2:00 | Architecture diagram (docs/architecture.md) | "How Nova is integrated: the Spring Boot backend calls the Bedrock Converse API with the image and a prompt, and forces a fixed answer format. The answer is validated, checked against stock, and fed into the dispensing check. The model is a setting, so it can be changed without code changes." |
| 2:00–2:20 | **Fallback (TC3):** switch to the wrong-model app → upload → "AI not configured – using manual entry" → type Lipitor and Klacid → Confirm → the check still runs | "If the AI fails – here I set a wrong model name – the app explains the problem and switches to manual entry. The pharmacist types the items, and the dispensing check still works, because both paths produce the same list." |
| 2:20–2:42 | Evaluation table (docs/evaluation.md) | "One key limitation: on clear prescriptions Nova was correct, but on dense real handwriting it read only about half of the drug names correctly, sometimes added a duration that wasn't written, and could miss an item. It did flag every wrong item, so this is decision support only – the pharmacist checks everything." |
| 2:42–3:00 | Cost table | "Cost: about 25,000 rupees to set up, and about 5,900 rupees a month to run – roughly one rupee thirty per prescription. It pays for itself if it saves just sixteen seconds per prescription, which a pilot should measure. Thank you." |

## Checklist after recording
- Length ≤ 3:00 (never over 4:00); voice clearly audible; name and ID visible at the start.
- No real patient data, AWS keys or `.env` contents on screen (Settings shows only "found", never the keys).
- Upload the video and check the link opens without a login.
