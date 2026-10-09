#!/usr/bin/env python3
"""Extract the 156 default fortunes from a Lifebase card dictionary (read-only).

Usage:
  python3 tools/extract_lifebase_catalog.py /path/to/lifebase                # rewrite the Korean asset
  python3 tools/extract_lifebase_catalog.py /path/to/lifebase --check        # verify only
  python3 tools/extract_lifebase_catalog.py /path/to/lifebase --locale en    # English vault

Only `Knowledge/Topics/Tarot/**/*.md` card notes are read. Journal notes and absolute paths
never enter the catalog. Sentences are copied character for character.
"""
import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ASSETS = REPO / "app/src/main/assets"
DECK = REPO / "app/src/main/java/com/jagaldol/dailytarot/model/TarotDeck.kt"

# How each Lifebase language writes the card name, keywords and orientation lines.
LOCALES = {
    "ko": {"keywords": r"^키워드 — (.+)$", "upright": "정방향", "reversed": "역방향"},
    "en": {"keywords": r"^Keywords: (.+)$", "upright": "Upright", "reversed": "Reversed"},
}


def asset(locale: str) -> Path:
    return ASSETS / f"default_fortunes.{locale}.json"


def extract(vault: Path, version: str, locale: str) -> dict:
    rules = LOCALES[locale]
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
        if locale == "ko":
            localized = re.search(r"^\*\*(.+?)\*\* \(", text, re.M)
            name = localized[1] if localized else None
        else:
            name = path.stem
        keywords = re.search(rules["keywords"], text, re.M)
        if not (name and keywords):
            raise ValueError(f"{path.name}: missing {locale} name or keywords")
        for reversed_ in (False, True):
            label = rules["reversed" if reversed_ else "upright"]
            direction = re.search(r"^- " + label + r"[^:]*: (.+)$", text, re.M)
            if not direction:
                raise ValueError(f"{path.name}: missing {label} sentence")
            entries.append({
                "cardId": card_id,
                "cardName": path.stem,
                "name": name,
                "reversed": reversed_,
                "keywordsText": keywords[1],
                "fortuneText": direction[1],
            })
    entries.sort(key=lambda e: (e["cardId"], e["reversed"]))
    keys = {(e["cardId"], e["reversed"]) for e in entries}
    if len(entries) != 156 or len(keys) != 156 or {k[0] for k in keys} != set(deck):
        raise ValueError(f"Expected 78 cards x 2 orientations, found {len(entries)} entries")
    return {"schemaVersion": 2, "catalogVersion": version, "locale": locale, "entries": entries}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("vault", type=Path)
    parser.add_argument("--locale", choices=sorted(LOCALES), default="ko")
    parser.add_argument("--check", action="store_true", help="compare with the bundled asset")
    parser.add_argument("--version", help="catalogVersion for a new asset (default: keep the current one)")
    args = parser.parse_args()
    target = asset(args.locale)
    current = json.loads(target.read_text(encoding="utf-8")) if target.exists() else None
    version = args.version or (current and current["catalogVersion"])
    if not version:
        print("A new catalog needs --version", file=sys.stderr)
        return 2
    catalog = extract(args.vault, version, args.locale)
    if args.check:
        if current is None or catalog["entries"] != current["entries"]:
            changed = [
                (e["cardName"], e["reversed"]) for e, c in zip(catalog["entries"], (current or {}).get("entries", []))
                if e != c
            ]
            print(f"Catalog differs from the vault: {changed[:5]}", file=sys.stderr)
            return 1
        print(f"Bundled {args.locale} catalog matches the vault: 156 entries")
        return 0
    target.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {target.relative_to(REPO)}; bump catalogVersion when sentences change")
    return 0


if __name__ == "__main__":
    sys.exit(main())
