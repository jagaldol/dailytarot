#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RES_DIR="$ROOT_DIR/app/src/main/res"

QUALITY=${QUALITY:-85}

if ! command -v cwebp >/dev/null 2>&1; then
  echo "Error: cwebp not found. Install via 'brew install webp' on macOS." >&2
  exit 1
fi

echo "Converting PNG -> WebP (q=$QUALITY) under $RES_DIR ..." >&2

files=$(find "$RES_DIR" -type f -name '*.png' | sort)

if [[ -z "$files" ]]; then
  echo "No PNG files found." >&2
  exit 0
fi

count=$(printf "%s\n" "$files" | wc -l | tr -d ' ')
idx=1
printf "%s\n" "$files" | while IFS= read -r f; do
  base="${f%.*}"
  out="${base}.webp"
  echo "[$idx/$count] ${f#$ROOT_DIR/} -> ${out#$ROOT_DIR/}" >&2
  # Convert with good quality-size balance; preserve alpha
  cwebp -quiet -q "$QUALITY" -m 6 -mt "$f" -o "$out"
  # Remove original PNG to avoid resource name duplication
  rm -f "$f"
  idx=$((idx+1))
done

echo "Done. Converted $count PNGs to WebP." >&2
