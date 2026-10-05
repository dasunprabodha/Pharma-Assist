# AI use log

Declaration of all AI assistance used in this project, in the assignment's AI declaration format.
Claude (via Claude Code) is used only as a coding assistant; it is not part of the running application.

| Date | AI tool | Purpose | Prompt or instruction (summary) | Output used | How the output was verified or corrected |
|------|---------|---------|---------------------------------|-------------|------------------------------------------|
| 05/10/2026 | Claude Code (Claude Opus 5.5) | Week 1 project scaffold (Spring Boot + Angular) | "Do the Week 1 tasks in CLAUDE.md §5 using a Java Spring Boot backend and Angular frontend; AWS (Bedrock/Nova) only; no real credentials; CSVs exactly as Appendix A" | Repository structure, pom.xml, Angular app, `.gitignore`, `.env.example`, README skeleton, log templates, `/api/status` + Configuration box, CheckBedrock and TrySample tools, the two CSVs (extracted verbatim from Appendix A), CLAUDE.md stack sections updated | `mvn test` (4 tests pass: both CSVs load with 34/21 rows and expected columns; Spring context starts); backend started and `/api/status` checked directly and through the Angular proxy; `ng build` succeeded; CheckBedrock/TrySample error paths checked without credentials; `git ls-files` checked so no `.env` or secret is tracked. Real Bedrock call not yet run (needs the student's AWS setup). |
