#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RES_DIR="$ROOT_DIR/app/src/main/res/drawable-nodpi"

if ! command -v cwebp >/dev/null 2>&1; then
  echo "Error: cwebp not found. Install via 'brew install webp' on macOS." >&2
  exit 1
fi

mkdir -p "$RES_DIR"

count=0
for src in "$RES_DIR"/tarot_rws_[0-9][0-9].webp; do
  [[ -e "$src" ]] || continue
  base=$(basename "$src")
  num=${base#tarot_rws_}
  num=${num%.webp}
  out="$RES_DIR/tarot_rws_thumb_${num}.webp"
  if [[ -s "$out" ]]; then
    echo "skip $out"
    continue
  fi
  # Resize to width ~360px (keep aspect), quality 80 for smooth scrolling
  echo "thumb $base -> $(basename "$out")"
  cwebp -quiet -q 80 -resize 360 0 "$src" -o "$out"
  count=$((count+1))
done

echo "Generated $count thumbnails in ${RES_DIR#$ROOT_DIR/}"

