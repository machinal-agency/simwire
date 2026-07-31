#!/usr/bin/env python3
"""Build the site's pages from their templates.

Inlines the fonts, the brand wordmark and the QR code so every page ships as
a single self-contained file. Assets live in ./assets:
  - inter.b64, geistmono.b64  (base64 of the woff2 files)
  - wordmark.svg              (vector wordmark)
  - qr.svg                    (real QR, regenerate with the `qrcode` npm package)

The APK linked from download.html is copied next to the pages at deploy time
(see the deploy note in the repo README).
"""
from pathlib import Path

here = Path(__file__).parent
assets = {
    "__INTER_B64__": (here / "assets" / "inter.b64").read_text().strip(),
    "__GEISTMONO_B64__": (here / "assets" / "geistmono.b64").read_text().strip(),
    "__WORDMARK_SVG__": (here / "assets" / "wordmark.svg").read_text().strip(),
    "__QR_SVG__": (here / "assets" / "qr.svg").read_text().strip(),
}

for template in here.glob("*.template.html"):
    html = template.read_text()
    for placeholder, value in assets.items():
        html = html.replace(placeholder, value)
    out = here / template.name.replace(".template", "")
    out.write_text(html)
    print(f"{out.name} built ({len(html):,} chars)")
