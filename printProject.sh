#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Dump directory tree and all file contents into a single output file
###############################################################################

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"

DATE_STR="$(date +%Y%m%d)"
OUT_FILE="${SCRIPT_DIR}/output_${DATE_STR}.txt"

# Create / overwrite output file
: > "$OUT_FILE"

# Separator line: "###" repeated 50 times (single line)
SEP_LINE="$(printf '###%.0s' {1..50})"

###############################################################################
# Directory tree
###############################################################################
{
  echo "########## DIRECTORY TREE: ${ROOT_DIR} ##########"
  if command -v tree >/dev/null 2>&1; then
    tree -a "$ROOT_DIR"
  else
    echo "(tree command not found — using find)"
    find "$ROOT_DIR"
  fi
  echo
  echo "########## FILE CONTENTS ##########"
  echo
} >> "$OUT_FILE"

###############################################################################
# Dump files
###############################################################################
while IFS= read -r -d '' f; do
  fname="$(basename "$f")"

  # Exclude any file starting with "output"
  [[ "$fname" == output* ]] && continue

  rel_path="${f#"$ROOT_DIR"/}"

  # Skip binary files if 'file' command exists
  if command -v file >/dev/null 2>&1; then
    if ! file -b --mime "$f" | grep -qiE 'charset=(us-ascii|utf-8|utf-16|iso-8859|windows-1252)'; then
      {
        echo "# ${rel_path}"
        echo "[SKIPPED: non-text or binary file]"
        echo "$SEP_LINE"
        echo "#### end of file ${fname} ####"
        echo "#### end of file ${fname} ####"
        echo
      } >> "$OUT_FILE"
      continue
    fi
  fi

  {
    echo "# ${rel_path}"
    cat "$f"
    echo
    echo "$SEP_LINE"
    echo "#### end of file ${fname} ####"
    echo "#### end of file ${fname} ####"
    echo
  } >> "$OUT_FILE"

done < <(
  find "$ROOT_DIR" \
    -type d \( -name .git -o -name node_modules -o -name .venv -o -name dist -o -name build \) -prune -false \
    -o -type f -print0
)

echo "Done."
echo "Root directory : $ROOT_DIR"
echo "Output file    : $OUT_FILE"
