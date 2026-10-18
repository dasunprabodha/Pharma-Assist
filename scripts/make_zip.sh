#!/usr/bin/env bash
# Builds submission/MS25903874_prescription_reader.zip from TRACKED, COMMITTED files only
# (never .env, logs/, node_modules/, target/, redacted samples or screenshots - those are untracked or git-ignored).
# Coursework documents at the repo root (assignment brief, progress forms) are left out: they are not source code.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! git diff --quiet HEAD -- backend frontend docs scripts data samples README.md CLAUDE.md .env.example .gitignore; then
  echo "Warning: there are uncommitted changes - the ZIP contains the last commit only." >&2
fi

OUT=submission/MS25903874_prescription_reader.zip
mkdir -p submission
rm -f "$OUT"
git archive --format=zip --prefix=prescription-reader/ -o "$OUT" HEAD -- . \
  ':(exclude)Assignment_02_*' ':(exclude)IT6070*' ':(exclude)Week 1 *' ':(exclude)CLAUDE(1).md' \
  ':(exclude)*.pptx' ':(exclude)*.docx'

# Safety net: refuse to finish if anything sensitive or bulky slipped in.
if unzip -Z1 "$OUT" | grep -E '(^|/)\.env$|logs/.+\.(csv|jsonl)$|node_modules/|/target/|/dist/|sri_lanka/|screenshots/|scan-images/'; then
  echo "ERROR: unexpected files in $OUT (listed above)" >&2
  rm -f "$OUT"
  exit 1
fi
echo "Wrote $OUT ($(du -h "$OUT" | cut -f1), $(unzip -Z1 "$OUT" | grep -vc '/$') files, commit $(git rev-parse --short HEAD))"
