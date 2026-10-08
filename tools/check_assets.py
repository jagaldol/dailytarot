#!/usr/bin/env python3
"""Check the packaged deck without Pillow or an Android runtime."""
from pathlib import Path
import json
import re
import struct


def webp_size(path):
    data = path.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise ValueError(f"Not WebP: {path}")
    offset = 12
    while offset + 8 <= len(data):
        tag = data[offset:offset + 4]
        length = struct.unpack_from("<I", data, offset + 4)[0]
        chunk = data[offset + 8:offset + 8 + length]
        if tag == b"VP8 ":
            w, h = struct.unpack_from("<HH", chunk, 6)
            return w & 16383, h & 16383
        if tag == b"VP8L":
            bits = int.from_bytes(chunk[1:5], "little")
            return (bits & 16383) + 1, ((bits >> 14) & 16383) + 1
        if tag == b"VP8X":
            return int.from_bytes(chunk[4:7], "little") + 1, int.from_bytes(chunk[7:10], "little") + 1
        offset += 8 + length + length % 2
    raise ValueError(f"No dimensions: {path}")


def check_catalog(repo):
    deck = dict(
        (int(i), n) for i, n in re.findall(
            r'Card\((\d+), "([^"]+)"',
            (repo / "app/src/main/java/com/jagaldol/dailytarot/model/TarotDeck.kt").read_text(),
        )
    )
    catalog = json.loads((repo / "app/src/main/assets/default_fortunes.ko.json").read_text(encoding="utf-8"))
    entries = catalog["entries"]
    keys = {(e["cardId"], e["reversed"]) for e in entries}
    if catalog["schemaVersion"] != 1 or len(entries) != 156 or len(keys) != 156:
        raise ValueError("Catalog must hold 78 cards x 2 orientations")
    for e in entries:
        if deck.get(e["cardId"]) != e["cardName"]:
            raise ValueError(f"Catalog card {e['cardId']} does not match the deck")
        if not e["fortuneText"].strip() or not e["keywordsText"].strip() or not e["nameKo"].strip():
            raise ValueError(f"Empty text for {e['cardName']}")
    if "/Users/" in json.dumps(catalog) or "Journal/" in json.dumps(catalog):
        raise ValueError("Catalog must not contain local paths or journal references")
    print(f"Fortune catalog verified: {len(entries)} entries, {catalog['catalogVersion']}")


def main():
    repo = Path(__file__).resolve().parents[1]
    check_catalog(repo)
    resources = repo / "app/src/main/res/drawable-nodpi"
    expected = {
        f"tarot_rws_{prefix}{index:02}.webp"
        for prefix in ("", "thumb_") for index in range(78)
    }
    actual = {path.name for path in resources.glob("tarot_rws_*")}
    if actual != expected:
        raise ValueError(f"Missing: {expected - actual}; unexpected: {actual - expected}")
    for name in sorted(expected):
        expected_size = (360, 600) if "thumb_" in name else (720, 1200)
        actual_size = webp_size(resources / name)
        if actual_size != expected_size:
            raise ValueError(f"{name}: expected {expected_size}, found {actual_size}")
    total = sum((resources / name).stat().st_size for name in expected)
    print(f"156 card assets verified; {total / 2**20:.2f} MiB")


if __name__ == "__main__":
    main()
