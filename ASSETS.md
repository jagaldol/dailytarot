# Assets

## Card illustrations

The 78 cards are the Rider–Waite–Smith tarot deck, illustrated by Pamela Colman Smith
and first published in 1909. The illustrations are in the public domain.

The scans are the "Pam-A" set from [Steve's playing-card site](https://steve-p.org/cards/).
`tools/fetch_rws_images.sh` reads `https://steve-p.org/cards/coordefs.js` and downloads the
referenced PNG files from `https://steve-p.org/cards/pix/`, mapping them to the app's order:
Major Arcana (0–21), Pentacles (22–35), Wands (36–49), Cups (50–63), Swords (64–77).

Local transformations:

- Full card: WebP, 720 × 1200, quality 85 (`tools/convert_pngs_to_webp.sh`).
- Grid thumbnail: WebP, 360 × 600, quality 80 (`tools/make_thumbs.sh`).
- Widget images are scaled to at most 1000 px high and rotated at runtime.

## Fortunes

`app/src/main/assets/default_fortunes.ko.json` and `default_fortunes.en.json` each hold one keyword
line and one fortune sentence for every card and orientation (156 entries), in Korean and English.
They are copied verbatim from the author's Lifebase card dictionary in that language with
`tools/extract_lifebase_catalog.py` (`--locale en` for the English vault).

## App icon and card back

The launcher icon, monochrome icon and card back are original vector drawables in
`app/src/main/res/drawable/`. `tools/make_launcher_icons.py` renders the bitmap icons used
on Android 7.x from the same shapes.
