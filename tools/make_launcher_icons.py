#!/usr/bin/env python3
"""Render legacy (API 24-25) launcher icons from the adaptive icon geometry.

The shapes mirror res/drawable/ic_launcher_{background,foreground}.xml in the same 108-unit
space. Legacy icons show the central 72 units, as the adaptive mask would.
Requires Pillow and cwebp.
"""
import math
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

S = 20  # supersampling: pixels per unit
GOLD = (0xE6, 0xC8, 0x8F)
PALE = (0xF4, 0xE3, 0xBF)
RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def u(v):
    return round(v * S)


def background():
    size = u(108)
    img = Image.new("RGB", (size, size))
    inner, outer = (0x2E, 0x2A, 0x4A), (0x17, 0x15, 0x2A)
    cx, cy, radius = u(54), u(44), u(76)
    px = img.load()
    for y in range(0, size, 4):
        for x in range(0, size, 4):
            t = min(1.0, math.hypot(x - cx, y - cy) / radius)
            color = tuple(round(a + (b - a) * t) for a, b in zip(inner, outer))
            for dy in range(4):
                for dx in range(4):
                    if x + dx < size and y + dy < size:
                        px[x + dx, y + dy] = color
    return img


def sparkle(cx, cy, r, steps=24):
    points = []
    corners = [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]
    for i in range(4):
        a, b = corners[i], corners[(i + 1) % 4]
        for s in range(steps):
            t = s / steps
            # Quadratic Bezier with the centre as control point, as in the vector.
            x = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * cx + t ** 2 * b[0]
            y = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * cy + t ** 2 * b[1]
            points.append((u(x), u(y)))
    return points


def foreground():
    size = u(108)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    # Pillow strokes inside the box; vector strokes are centred, so grow by half the width.
    draw.rounded_rectangle([u(37.9), u(29.9), u(70.1), u(78.1)], radius=u(5.1), outline=GOLD, width=u(2.2))
    faint = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(faint).rounded_rectangle(
        [u(42.65), u(34.65), u(65.35), u(73.35)], radius=u(1.85), outline=GOLD + (140,), width=u(0.7),
    )
    layer = Image.alpha_composite(layer, faint)

    # Crescent: disc minus an offset disc, then rotated -24 degrees around its centre.
    moon = Image.new("L", (size, size), 0)
    ImageDraw.Draw(moon).ellipse([u(54 - 8.5), u(53 - 8.5), u(54 + 8.5), u(53 + 8.5)], fill=255)
    cut = Image.new("L", (size, size), 0)
    ImageDraw.Draw(cut).ellipse([u(63.43 - 9), u(53 - 9), u(63.43 + 9), u(53 + 9)], fill=255)
    moon = ImageChops.subtract(moon, cut).rotate(24, center=(u(54), u(53)), resample=Image.BICUBIC)
    layer.paste(Image.new("RGBA", (size, size), GOLD + (255,)), (0, 0), moon)

    draw = ImageDraw.Draw(layer)
    draw.polygon(sparkle(61.5, 40.5, 3.9), fill=PALE)
    draw.polygon(sparkle(47.5, 67, 2.6), fill=GOLD)
    dot = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(dot).ellipse([u(59.6), u(67.6), u(61.4), u(69.4)], fill=GOLD + (178,))
    return Image.alpha_composite(layer, dot)


def main():
    full = background().convert("RGBA")
    full = Image.alpha_composite(full, foreground())
    icon = full.crop((u(18), u(18), u(90), u(90)))
    side = icon.size[0]
    square_mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(square_mask).rounded_rectangle([0, 0, side - 1, side - 1], radius=round(side * 0.18), fill=255)
    round_mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(round_mask).ellipse([0, 0, side - 1, side - 1], fill=255)
    with tempfile.TemporaryDirectory() as tmp:
        for density, px in DENSITIES.items():
            for name, mask in (("ic_launcher", square_mask), ("ic_launcher_round", round_mask)):
                shaped = Image.new("RGBA", icon.size, (0, 0, 0, 0))
                shaped.paste(icon, (0, 0), mask)
                png = Path(tmp) / f"{name}-{density}.png"
                shaped.resize((px, px), Image.LANCZOS).save(png)
                out = RES / f"mipmap-{density}" / f"{name}.webp"
                subprocess.run(["cwebp", "-quiet", "-lossless", str(png), "-o", str(out)], check=True)
    print("Legacy launcher icons written")


if __name__ == "__main__":
    main()
