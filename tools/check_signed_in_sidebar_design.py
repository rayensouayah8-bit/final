"""
Static design gate for signed-in home: Pi-java2 sidebar (structure + theme CSS).
Run: py tools/check_signed_in_sidebar_design.py
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FXML = ROOT / "src/main/resources/fxml/home/signed-in-home.fxml"
CSS = ROOT / "src/main/resources/css/signed-in-unified.css"


def fail(msg: str) -> None:
    print("DESIGN_CHECK_FAIL:", msg, file=sys.stderr)


def main() -> int:
    if not FXML.exists():
        fail(f"missing {FXML}")
        return 1
    fx = FXML.read_text(encoding="utf-8")
    if "sidebar-immersive" not in fx:
        fail("FXML: missing Pi-java2 sidebar-immersive VBox")
        return 1
    if "sidebar-nav-item" not in fx:
        fail("FXML: missing Pi-java2 sidebar-nav-item")
        return 1
    if 'onAction="#onHome"' not in fx or 'onAction="#onLogout"' not in fx:
        fail("FXML: missing Pi-java2 Home / Logout actions")
        return 1
    if "navDashboardButton" not in fx:
        fail("FXML: missing fx:id navDashboardButton (signed-in shell)")
        return 1

    if not CSS.exists():
        fail(f"missing {CSS}")
        return 1
    css = CSS.read_text(encoding="utf-8")
    if ".sidebar-immersive" not in css or ".sidebar-nav-item" not in css:
        fail("CSS: signed-in-unified missing sidebar rules")
        return 1
    if ".sidebar-nav-item-active" not in css:
        fail("CSS: signed-in-unified missing active nav rule")
        return 1

    print("DESIGN_CHECK_OK: signed-in home uses Pi-java2 sidebar + signed-in-unified.css.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
