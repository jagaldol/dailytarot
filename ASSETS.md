# Card assets

## Provenance

The existing download script identifies the images as the Rider–Waite–Smith
Pam-A deck from [Steve's playing-card site](https://steve-p.org/cards/).
It reads `https://steve-p.org/cards/coordefs.js` and downloads the referenced
PNG files from `https://steve-p.org/cards/pix/`.

The resource order is Major Arcana (0–21), Pentacles (22–35), Wands (36–49),
Cups (50–63), and Swords (64–77). The source filenames are mapped by the downloader.

## Local transformations

- Full card: WebP, 720 × 1200, quality 85.
- Grid thumbnail: WebP, 360 × 600, quality 80.
- The 2026-10-07 optimization resized the previously committed WebP cards.
  It did not replace the illustrations or fetch a different scan.
- Runtime widget images are further bounded to 1000px high and rotated off the UI thread.

## Publication status

No blanket license is asserted for these scans. The download script's source URL
alone does not establish redistribution terms. The source page returned HTTP 403
to the automated reader on 2026-10-07, so its current terms were not verified.
Before public redistribution, verify and record the terms for the actual scans,
or replace them with assets whose redistribution terms are documented.
The code license, when chosen, must distinguish code from third-party images.
