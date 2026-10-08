#!/usr/bin/env python3
"""Extract the 156 default fortunes from a Lifebase card dictionary (read-only).

Usage:
  python3 tools/extract_lifebase_catalog.py /path/to/lifebase           # rewrite the asset
  python3 tools/extract_lifebase_catalog.py /path/to/lifebase --check   # verify only

Only `Knowledge/Topics/Tarot/**/*.md` card notes are read. Journal notes and absolute paths
never enter the catalog. Sentences are copied character for character.
"""
import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ASSET = REPO / "app/src/main/assets/default_fortunes.ko.json"
DECK = REPO / "app/src/main/java/com/jagaldol/dailytarot/model/TarotDeck.kt"


def extract(vault: Path, version: str) -> dict:
    deck = {int(i): n for i, n in re.findall(r'Card\((\d+), "([^"]+)"', DECK.read_text())}
    entries = []
    for path in sorted((vault / "Knowledge/Topics/Tarot").rglob("*.md")):
        text = path.read_text(encoding="utf-8")
        match = re.search(r"^card_id: (\d+)$", text, re.M)
        if not match:
            continue
        card_id = int(match[1])
        if deck.get(card_id) != path.stem:
            raise ValueError(f"{path.name}: card_id {card_id} is {deck.get(card_id)} in the app deck")
        localized = re.search(r"^\*\*(.+?)\*\* \(", text, re.M)
        keywords = re.search(r"^키워드 — (.+)$", text, re.M)
        if not (localized and keywords):
            raise ValueError(f"{path.name}: missing Korean name or keywords")
        for reversed_, label in ((False, "정방향"), (True, "역방향")):
            direction = re.search(r"^- " + label + r"[^:]*: (.+)$", text, re.M)
            if not direction:
                raise ValueError(f"{path.name}: missing {label} sentence")
            entries.append({
                "cardId": card_id,
                "cardName": path.stem,
                "nameKo": localized[1],
                "reversed": reversed_,
                "keywordsText": keywords[1],
                "fortuneText": direction[1],
            })
    entries.sort(key=lambda e: (e["cardId"], e["reversed"]))
    keys = {(e["cardId"], e["reversed"]) for e in entries}
    if len(entries) != 156 or len(keys) != 156 or {k[0] for k in keys} != set(deck):
        raise ValueError(f"Expected 78 cards x 2 orientations, found {len(entries)} entries")
    return {"schemaVersion": 1, "catalogVersion": version, "locale": "ko", "entries": entries}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("vault", type=Path)
    parser.add_argument("--check", action="store_true", help="compare with the bundled asset")
    args = parser.parse_args()
    current = json.loads(ASSET.read_text(encoding="utf-8"))
    catalog = extract(args.vault, current["catalogVersion"])
    if args.check:
        if catalog["entries"] != current["entries"]:
            changed = [
                (e["cardName"], e["reversed"]) for e, c in zip(catalog["entries"], current["entries"]) if e != c
            ]
            print(f"Catalog differs from the vault: {changed[:5]}", file=sys.stderr)
            return 1
        print("Bundled catalog matches the vault: 156 entries")
        return 0
    ASSET.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {ASSET.relative_to(REPO)}; bump catalogVersion when sentences change")
    return 0


if __name__ == "__main__":
    sys.exit(main())
