#!/usr/bin/env bash
set -uo pipefail

# Download all Rider–Waite–Smith (Pam-A) card images into Android resources.
# Output: app/src/main/res/drawable-nodpi/tarot_rws_XX.png (00..77)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/app/src/main/res/drawable-nodpi"
TMP_DIR="$(mktemp -d)"

mkdir -p "$OUT_DIR"

echo "Fetching coordefs.js ..." >&2
if ! curl -fsSL "https://steve-p.org/cards/coordefs.js" -o "$TMP_DIR/coordefs.js"; then
  echo "Failed to fetch coordefs.js" >&2
  exit 1
fi

# Extract RWS arrays (order matters: Trumps, Pentacles, Wands, Cups, Swords)
extract_array() {
  local key="$1"
  local line
  line=$(grep -m1 "${key}:{" "$TMP_DIR/coordefs.js" || true)
  if [[ -z "$line" ]]; then
    echo "Error: key $key not found" >&2
    return 1
  fi
  echo "$line" \
    | awk -F'loi:\\[' '{print $2}' \
    | awk -F']' '{print $1}' \
    | tr -d ' \t"\r' \
    | tr ',' '\n' \
    | sed '/^$/d'
}

ALL_LIST=$( {
  extract_array "RWSaT"; 
  extract_array "RWSaP"; 
  extract_array "RWSaW"; 
  extract_array "RWSaC"; 
  extract_array "RWSaS"; 
} )

count=$(printf "%s\n" "$ALL_LIST" | sed '/^$/d' | wc -l | tr -d ' ')
echo "Downloading ${count} images (skips existing) ..." >&2

idx=0
while IFS= read -r stem; do
  printf -v num "%02d" "$idx"
  url="https://steve-p.org/cards/pix/${stem}.png"
  out="$OUT_DIR/tarot_rws_${num}.png"
  if [[ -s "$out" ]]; then
    echo "[$num] exists, skip -> ${out#$ROOT_DIR/}" >&2
  else
    echo "[$num] $url -> ${out#$ROOT_DIR/}" >&2
    if ! curl -fSL --retry 3 --retry-delay 1 --connect-timeout 10 --max-time 120 "$url" -o "$out"; then
      echo "[$num] download failed: $url" >&2
    fi
  fi
  idx=$((idx+1))
done <<< "$ALL_LIST"

missing=$(find "$OUT_DIR" -maxdepth 1 -name 'tarot_rws_*.png' | wc -l | tr -d ' ')
echo "Done. Saved to ${OUT_DIR#$ROOT_DIR/} (${missing}/${count})" >&2
