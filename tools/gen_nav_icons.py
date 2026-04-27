"""
Generate 32x32 PNG nav icons (light glyph on transparent) for signed-in sidebar.
Run: py tools/gen_nav_icons.py
"""
from __future__ import annotations

import math
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError as e:
    raise SystemExit("Install Pillow: py -m pip install pillow") from e

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/images/nav"
SIZE = 32


def save(name: str, draw_fn) -> None:
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    draw_fn(dr)
    OUT.mkdir(parents=True, exist_ok=True)
    im.save(OUT / name, "PNG")


def main() -> None:
    fill = (220, 226, 245, 255)
    gold = (250, 204, 21, 255)

    def home(d):
        d.polygon([(6, 18), (16, 8), (26, 18), (26, 28), (18, 28), (18, 20), (14, 20), (14, 28), (6, 28)], fill=fill)

    def tag(d):
        d.polygon([(8, 6), (22, 6), (26, 10), (14, 24), (6, 16)], outline=fill, width=2)
        d.ellipse((18, 8, 24, 14), outline=fill, width=2)

    def building(d):
        d.rectangle((8, 12, 24, 28), outline=fill, width=2)
        d.rectangle((11, 8, 15, 12), fill=fill)
        d.rectangle((17, 15, 21, 18), fill=fill)

    def envelope(d):
        d.polygon([(6, 10), (16, 20), (26, 10), (26, 24), (6, 24)], outline=fill, width=2)
        d.line([(6, 10), (16, 18), (26, 10)], fill=fill, width=2)

    def sparkle(d):
        d.line([(16, 6), (16, 26)], fill=fill, width=2)
        d.line([(6, 16), (26, 16)], fill=fill, width=2)
        d.line([(9, 9), (23, 23)], fill=fill, width=2)
        d.line([(23, 9), (9, 23)], fill=fill, width=2)

    def calendar(d):
        d.rectangle((7, 10, 25, 26), outline=fill, width=2)
        d.line([(7, 14), (25, 14)], fill=fill, width=2)
        d.rectangle((10, 6, 14, 10), fill=fill)
        d.rectangle((18, 6, 22, 10), fill=fill)

    def bell(d):
        d.polygon([(10, 12), (22, 12), (22, 20), (16, 26), (10, 20)], outline=fill, width=2)
        d.line([(8, 12), (24, 12)], fill=fill, width=2)

    def person(d):
        d.ellipse((11, 7, 21, 16), outline=fill, width=2)
        d.rounded_rectangle((9, 16, 23, 27), radius=5, outline=fill, width=2)

    def dashboard(d):
        d.rectangle((6, 6, 14, 14), outline=fill, width=2)
        d.rectangle((18, 6, 26, 14), outline=fill, width=2)
        d.rectangle((6, 18, 14, 26), outline=fill, width=2)
        d.rectangle((18, 18, 26, 26), outline=fill, width=2)

    def premium_badge(d):
        d.polygon([(16, 5), (23, 14), (16, 27), (9, 14)], fill=gold)

    def logout_ic(d):
        d.rectangle((6, 8, 14, 24), outline=fill, width=2)
        d.line([(14, 16), (24, 16)], fill=fill, width=2)
        d.polygon([(22, 12), (26, 16), (22, 20)], fill=fill)

    def star_dark(d):
        cx, cy, r = 16, 16, 10
        pts = []
        for i in range(10):
            ang = math.pi / 2 + i * math.pi / 5
            rad = r if i % 2 == 0 else r * 0.42
            pts.append((cx + rad * math.cos(ang), cy - rad * math.sin(ang)))
        d.polygon(pts, fill=(18, 24, 42, 255))

    save("nav-home.png", home)
    save("nav-offers.png", tag)
    save("nav-agencies.png", building)
    save("nav-messages.png", envelope)
    save("nav-recommendations.png", sparkle)
    save("nav-events.png", calendar)
    save("nav-notifications.png", bell)
    save("nav-profile.png", person)
    save("nav-dashboard.png", dashboard)
    save("nav-premium-badge.png", premium_badge)
    save("nav-logout.png", logout_ic)
    save("nav-premium-star.png", star_dark)
    print("Wrote icons to", OUT)


if __name__ == "__main__":
    main()
