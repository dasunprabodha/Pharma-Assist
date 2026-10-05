# Test log

Manual and automated test cases for the Prescription Reader. Week 2 adds TC1–TC3 (success, low confidence, error/fallback).

| ID | Input | Expected result | Actual result | Pass/Fail | Evidence (screenshot file) |
|----|-------|-----------------|---------------|-----------|----------------------------|
| TC1 | Clear synthetic prescription photo `samples/tc1_clear.jpg`: "Tab Lipitor 20mg nocte; Tab Klacid 500mg bd × 5/7" | Both items extracted: Lipitor 20 mg, at night (nocte); Klacid 500 mg, twice daily (bd), 5 days; both matched to the stock list; list confirmed as "AI-extracted" within 30 s | *To run* | | `docs/screenshots/tc1_*.png` |
| TC2 | Messy handwriting sample `samples/tc2_messy.jpg` | Uncertain fields shown as low confidence / "unreadable" and listed ("Row n: low confidence – please verify"); pharmacist corrects them in the table; correction count shown; list confirmed | *To run* | | `docs/screenshots/tc2_*.png` |
| TC3 | `BEDROCK_MODEL_ID=bogus-model` in `.env` (backend restarted), then any sample | Message "AI not configured (AWS credentials or Bedrock model access) – using manual entry"; manual-entry form opens with the photo still visible; a manually entered list can be confirmed | *To run* | | `docs/screenshots/tc3_*.png` |
| AT1 | Automated suite: `cd backend && mvn test` (mocked Bedrock client) | All tests pass | 46 tests, 0 failures (05/10/2026) | Pass | Console output |
