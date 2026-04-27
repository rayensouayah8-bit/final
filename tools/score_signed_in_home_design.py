#!/usr/bin/env python3
"""
Design quality auditor for signed-in home (FXML + CSS heuristics).
Outputs weighted score /100, per-category breakdown, issues with severity, suggestions.

Run: py tools/score_signed_in_home_design.py [--json]
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
FXML = ROOT / "src/main/resources/fxml/home/signed-in-home.fxml"
CSS = ROOT / "src/main/resources/css/styles.css"

WEIGHTS = {
    "layout_structure": 0.18,
    "visual_hierarchy": 0.14,
    "typography": 0.12,
    "color_system": 0.12,
    "component_consistency": 0.12,
    "interaction_states": 0.08,
    "depth_surface": 0.06,
    "usability": 0.08,
    "responsiveness": 0.04,
    "accessibility": 0.04,
    "brand_coherence": 0.02,
}

GRID = (4, 6, 8, 10, 12, 16, 20, 24, 32, 48)


@dataclass
class Issue:
    category: str
    severity: str  # low | medium | high
    message: str
    suggestion: str


@dataclass
class CategoryResult:
    key: str
    weight: float
    raw_score: float  # 0..1
    points: float
    notes: list[str] = field(default_factory=list)


def _rel_luminance(rgb: tuple[float, float, float]) -> float:
    def f(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = rgb
    R, G, B = f(r), f(g), f(b)
    return 0.2126 * R + 0.7152 * G + 0.0722 * B


def parse_rgba(text: str) -> tuple[float, float, float, float] | None:
    m = re.search(
        r"rgba?\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*(?:,\s*([0-9.]+))?\s*\)",
        text,
        re.I,
    )
    if not m:
        return None
    r, g, b = float(m.group(1)), float(m.group(2)), float(m.group(3))
    a = float(m.group(4)) if m.group(4) is not None else 1.0
    return (r, g, b, a)


def contrast_ratio(fg_rgb: tuple[float, float, float], bg_rgb: tuple[float, float, float]) -> float:
    l1 = _rel_luminance(fg_rgb)
    l2 = _rel_luminance(bg_rgb)
    L1, L2 = max(l1, l2), min(l1, l2)
    return (L1 + 0.05) / (L2 + 0.05)


def extract_sidebar_css(css: str) -> str:
    start = css.find("#signedInSidebarShell")
    if start == -1:
        return ""
    # include signed-in-home-root / placeholder used by same screen
    tail = css[start:]
    return tail


def extract_numbers_from_insets(fxml_text: str) -> list[int]:
    nums = []
    for m in re.finditer(r"<Insets[^>]*>", fxml_text):
        tag = m.group(0)
        for v in re.findall(r"(top|right|bottom|left)=\"([0-9.]+)", tag):
            nums.append(int(float(v[1])))
    return nums


def sidebar_css_block(css: str) -> str:
    m = re.search(
        r"/\* ═+[\s\S]*?Signed-in home — left rail[\s\S]*?═+ \*/([\s\S]*)",
        css,
    )
    if m:
        return m.group(1)
    return extract_sidebar_css(css)


def score_layout(fxml: str, issues: list[Issue]) -> CategoryResult:
    notes = []
    score = 1.0
    nums = extract_numbers_from_insets(fxml)
    off_grid = [n for n in nums if n not in GRID and n > 0]
    if off_grid:
        score -= 0.12
        issues.append(
            Issue(
                "layout_structure",
                "medium",
                f"FXML Insets use non-grid values: {sorted(set(off_grid))}",
                f"Prefer spacing from {GRID} (e.g. 16/20 instead of odd jumps).",
            )
        )
    if "VBox.vgrow=\"ALWAYS\"" not in fxml and "VBox.vgrow=\"ALWAYS\"" not in fxml.replace("'", '"'):
        if "ALWAYS" not in fxml:
            score -= 0.2
            issues.append(
                Issue(
                    "layout_structure",
                    "high",
                    "No flexible vertical spacer detected between primary nav and lower sections.",
                    "Keep a Region with VBox.vgrow=ALWAYS so empty space is intentional.",
                )
            )
    if "CENTER_LEFT" not in fxml:
        score -= 0.08
        issues.append(
            Issue(
                "layout_structure",
                "low",
                "Alignment CENTER_LEFT not found for nav rows.",
                "Keep icon+label rows left-aligned on one vertical axis.",
            )
        )
    if "sv-rail-gap-before-util" not in fxml:
        score -= 0.06
        notes.append("Optional: add sv-rail-gap-before-util Region above utilities for section rhythm.")
    else:
        notes.append("Utilities separated from primary nav by fixed gap (sv-rail-gap-before-util).")
    notes.append(f"Inset values sampled: {sorted(set(nums)) or 'none'}")
    return CategoryResult("layout_structure", WEIGHTS["layout_structure"], max(0, score), 0, notes)


def score_hierarchy(css_sidebar: str, issues: list[Issue]) -> CategoryResult:
    score = 1.0
    if "sv-rail-brand-title" in css_sidebar and "-fx-font-size: 18px" in css_sidebar:
        pass
    else:
        score -= 0.1
    if "sv-rail-brand-sub" in css_sidebar and "-fx-font-size:" in css_sidebar:
        pass
    if "sv-rail-nav-selectable-primary.active" in css_sidebar:
        pass
    else:
        score -= 0.25
        issues.append(
            Issue(
                "visual_hierarchy",
                "high",
                "Missing clear active-state rule for primary navigation.",
                "Style .sv-rail-nav-selectable-primary.active with stronger contrast than inactive rows.",
            )
        )
    if "sv-rail-nav-text-util" in css_sidebar and "sv-rail-nav-text-premium" in css_sidebar:
        pass
    else:
        score -= 0.08
    if "sv-rail-nav-premium-row" in css_sidebar:
        pass
    else:
        score -= 0.06
    return CategoryResult("visual_hierarchy", WEIGHTS["visual_hierarchy"], max(0, score), 0, [])


def score_typography(css_sidebar: str, css_full: str, issues: list[Issue]) -> CategoryResult:
    # Sidebar typography only (hero title 102px is intentional marketing scale, not rail type rhythm)
    block = css_sidebar
    sizes = [float(x) for x in re.findall(r"-fx-font-size:\s*([0-9.]+)px", block)]
    families = re.findall(r"-fx-font-family:\s*([^;]+);", block)
    score = 1.0
    if len(set(sizes)) > 8:
        score -= 0.15
        issues.append(
            Issue(
                "typography",
                "medium",
                f"Many distinct font sizes in scope ({len(set(sizes))}).",
                "Reduce to a small type scale (e.g. 11 / 12.5 / 14 / 18).",
            )
        )
    if len(families) > 4:
        score -= 0.1
    if not sizes:
        score -= 0.2
    return CategoryResult("typography", WEIGHTS["typography"], max(0, score), 0, [f"font-sizes: {sorted(set(sizes))}"])


def score_color(css_sidebar: str, issues: list[Issue]) -> CategoryResult:
    score = 1.0
    if "#06102a" in css_sidebar or "#06102A".lower() in css_sidebar.lower():
        pass
    elif "06102a" in css_sidebar:
        pass
    else:
        score -= 0.12
        issues.append(
            Issue(
                "color_system",
                "medium",
                "Rail base navy token #06102a not obvious in sidebar CSS.",
                "Anchor the rail with a clear dark base + one accent system.",
            )
        )
    if "linear-gradient" in css_sidebar:
        pass
    else:
        score -= 0.08
    return CategoryResult("color_system", WEIGHTS["color_system"], max(0, score), 0, [])


def score_components(css_sidebar: str, fxml: str, issues: list[Issue]) -> CategoryResult:
    radii = re.findall(r"-fx-background-radius:\s*([0-9]+)", css_sidebar)
    radii += re.findall(r"-fx-border-radius:\s*([0-9]+)", css_sidebar)
    score = 1.0
    if radii:
        uniq = set(radii)
        if len(uniq) > 3:
            score -= 0.12
            issues.append(
                Issue(
                    "component_consistency",
                    "medium",
                    f"Multiple border-radius tokens: {sorted(uniq)}.",
                    "Prefer one radius (e.g. 14px) for nav + cards in the rail.",
                )
            )
    icon_sizes = re.findall(r"fitWidth=\"([0-9.]+)\"", fxml)
    if icon_sizes and len(set(icon_sizes)) > 1:
        score -= 0.08
        issues.append(
            Issue(
                "component_consistency",
                "low",
                f"Mixed ImageView fit widths: {set(icon_sizes)}.",
                "Use one icon size (e.g. 22) for rhythm.",
            )
        )
    return CategoryResult("component_consistency", WEIGHTS["component_consistency"], max(0, score), 0, [])


def score_interaction(css_sidebar: str, issues: list[Issue]) -> CategoryResult:
    score = 1.0
    has_hover = ":hover" in css_sidebar
    has_pressed = ":pressed" in css_sidebar
    has_focused = ":focused" in css_sidebar
    if not has_hover:
        score -= 0.25
        issues.append(
            Issue(
                "interaction_states",
                "high",
                "No :hover rules detected in sidebar CSS.",
                "Add hover wash/glow for every clickable row.",
            )
        )
    if not has_pressed:
        score -= 0.15
        issues.append(
            Issue(
                "interaction_states",
                "medium",
                "No :pressed feedback on nav rows.",
                "Add subtle pressed scale or darken for tactile feedback.",
            )
        )
    if not has_focused:
        score -= 0.12
        issues.append(
            Issue(
                "interaction_states",
                "medium",
                "No :focused ring/glow for keyboard users.",
                "Set focusTraversable on rows and style :focused with a visible outline.",
            )
        )
    if ".active" not in css_sidebar:
        score -= 0.2
    return CategoryResult("interaction_states", WEIGHTS["interaction_states"], max(0, score), 0, [])


def score_depth(css_sidebar: str, issues: list[Issue]) -> CategoryResult:
    score = 1.0
    if "innershadow" in css_sidebar and "dropshadow" in css_sidebar:
        pass
    else:
        score -= 0.2
        issues.append(
            Issue(
                "depth_surface",
                "medium",
                "Limited layering (shadows) on rail or active item.",
                "Combine subtle inner shadow on rail + outer glow on active pill.",
            )
        )
    return CategoryResult("depth_surface", WEIGHTS["depth_surface"], max(0, score), 0, [])


def score_usability(fxml: str, css_full: str, issues: list[Issue]) -> CategoryResult:
    score = 1.0
    if "-fx-min-height: 48" in css_full or "-fx-min-height: 44" in css_full:
        pass
    else:
        score -= 0.1
    if "Logout" not in fxml:
        score -= 0.15
        issues.append(Issue("usability", "high", "Logout control not found.", "Keep logout isolated and clearly labeled."))
    return CategoryResult("usability", WEIGHTS["usability"], max(0, score), 0, [])


def score_responsive(fxml: str, issues: list[Issue]) -> CategoryResult:
    score = 1.0
    if 'maxWidth="Infinity"' in fxml or "Infinity" in fxml:
        pass
    else:
        score -= 0.15
    if "prefWidth=\"312" in fxml or "prefWidth=\"31" in fxml:
        pass
    else:
        score -= 0.08
    return CategoryResult("responsiveness", WEIGHTS["responsiveness"], max(0, score), 0, [])


def score_accessibility(css_sidebar: str, issues: list[Issue]) -> CategoryResult:
    """Rough contrast: nav body text rgba(236,242,255,0.96) on ~#06102a."""
    score = 1.0
    notes: list[str] = []
    bg = (6, 16, 42)  # #06102a
    fg_nav = (236, 242, 255)
    cr = contrast_ratio(fg_nav, bg)
    notes.append(f"Estimated primary nav label contrast ~{cr:.2f}:1 on #06102a")
    if cr < 4.5:
        score -= 0.35
        issues.append(
            Issue(
                "accessibility",
                "high",
                f"Estimated nav label contrast ~{cr:.2f}:1 vs rail base (target 4.5:1+ for UI text).",
                "Lighten body labels or darken rail slightly; verify with a contrast checker.",
            )
        )
    elif cr < 7:
        score -= 0.08
    sub_m = re.search(r"sv-rail-brand-sub\s*\{[^}]+\}", css_sidebar, re.S)
    if sub_m:
        sub_txt = sub_m.group(0)
        rgba = parse_rgba(sub_txt)
        if rgba:
            r, g, b, a = rgba
            eff = (r * a + bg[0] * (1 - a), g * a + bg[1] * (1 - a), b * a + bg[2] * (1 - a))
            cr2 = contrast_ratio(eff, bg)
            notes.append(f"Subtitle blended contrast ~{cr2:.2f}:1")
            if cr2 < 4.5:
                score -= 0.2
                issues.append(
                    Issue(
                        "accessibility",
                        "medium",
                        f"Subtitle contrast ~{cr2:.2f}:1 may be low.",
                        "Increase subtitle opacity or size slightly.",
                    )
                )
    return CategoryResult("accessibility", WEIGHTS["accessibility"], max(0, score), 0, notes)


def score_brand(css_sidebar: str, issues: list[Issue]) -> CategoryResult:
    score = 1.0
    if "ec4899" in css_sidebar or "#ec4899" in css_sidebar.lower():
        pass
    else:
        score -= 0.15
    if "a855f7" in css_sidebar or "124, 58, 237" in css_sidebar:
        pass
    else:
        score -= 0.1
    return CategoryResult("brand_coherence", WEIGHTS["brand_coherence"], max(0, score), 0, [])


def verdict(total: float) -> str:
    if total >= 90:
        return "premium / advanced"
    if total >= 80:
        return "strong product design"
    if total >= 70:
        return "decent but not polished"
    if total >= 55:
        return "functional but weak"
    return "poor / inconsistent"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    ap.add_argument(
        "--min-score",
        type=float,
        default=0.0,
        help="Exit 2 if final score is below this threshold (for CI gates).",
    )
    args = ap.parse_args()

    if not FXML.exists() or not CSS.exists():
        print("Missing FXML or CSS", file=sys.stderr)
        return 1

    fxml = FXML.read_text(encoding="utf-8")
    css = CSS.read_text(encoding="utf-8")
    css_sb = sidebar_css_block(css)

    issues: list[Issue] = []
    categories: list[CategoryResult] = [
        score_layout(fxml, issues),
        score_hierarchy(css_sb, issues),
        score_typography(css_sb, css, issues),
        score_color(css_sb, issues),
        score_components(css_sb, fxml, issues),
        score_interaction(css_sb, issues),
        score_depth(css_sb, issues),
        score_usability(fxml, css, issues),
        score_responsive(fxml, issues),
        score_accessibility(css_sb, issues),
        score_brand(css_sb, issues),
    ]

    total = 0.0
    out_categories = {}
    for c in categories:
        c.points = round(100 * c.weight * c.raw_score, 2)
        total += c.points
        out_categories[c.key] = {
            "weight": c.weight,
            "raw_0_1": round(c.raw_score, 3),
            "weighted_points": c.points,
            "max_weighted": round(100 * c.weight, 2),
            "notes": c.notes,
        }

    total = round(total, 2)
    v = verdict(total)

    # Top issues by severity
    sev_rank = {"high": 0, "medium": 1, "low": 2}
    top = sorted(issues, key=lambda i: (sev_rank.get(i.severity, 3), i.message))[:5]

    report = {
        "final_score": total,
        "verdict": v,
        "categories": out_categories,
        "issues": [i.__dict__ for i in issues],
        "top_5_issues": [i.__dict__ for i in top],
        "suggestions_summary": list({i.suggestion for i in issues})[:8],
    }

    if args.json:
        print(json.dumps(report, indent=2))
        rc = 0 if total >= args.min_score else 2
        return rc

    print("=== Signed-in home design audit (token/CSS + FXML heuristics) ===\n")
    print(f"final score: {total}/100")
    print(f"verdict: {v}\n")
    print("per-category (weighted points / max for that weight):")
    for k, d in out_categories.items():
        mx = d["max_weighted"]
        print(f"  {k}: {d['weighted_points']:.2f}/{mx:.2f}  (raw {d['raw_0_1']:.2f})")
        for n in d["notes"][:2]:
            print(f"      note: {n}")
    print("\ndetected issues (%d):" % len(issues))
    for i in issues:
        print(f"  [{i.severity.upper()}] [{i.category}] {i.message}")
        print(f"          -> {i.suggestion}")
    print("\ntop 5 issues (severity-prioritized):")
    for i in top:
        print(f"  - ({i.severity}) {i.message}")
    print("\naggregate suggestions:")
    for s in report["suggestions_summary"]:
        print(f"  - {s}")
    print(
        "\nLimitations: static analysis cannot judge pixel-perfect alignment, animation quality, or "
        "real WCAG compliance without runtime screenshots. Use this as a regression gate + checklist."
    )
    if total < args.min_score:
        print(f"\nFAIL: score {total} < --min-score {args.min_score}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
