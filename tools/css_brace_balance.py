"""Rough brace balance for JavaFX CSS (handles //, /* */, single/double quotes)."""
from __future__ import annotations

import sys
from pathlib import Path


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("src/main/resources/css/signed-in-unified.css")
    text = path.read_text(encoding="utf-8")
    depth = 0
    stack: list[int] = []
    line = 1
    col = 0
    in_s = in_d = False
    esc = False
    in_line = False
    in_block = False
    i = 0
    last_open_line = 0
    while i < len(text):
        ch = text[i]
        col += 1
        if ch == "\n":
            line += 1
            col = 0
            in_line = False
            i += 1
            continue
        if in_block:
            if ch == "*" and i + 1 < len(text) and text[i + 1] == "/":
                in_block = False
                i += 2
                col += 1
                continue
            i += 1
            continue
        if in_line:
            i += 1
            continue
        if not in_s and not in_d:
            if ch == "/" and i + 1 < len(text):
                n = text[i + 1]
                if n == "/":
                    in_line = True
                    i += 2
                    col += 1
                    continue
                if n == "*":
                    in_block = True
                    i += 2
                    col += 1
                    continue
        if in_s:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_s = False
            i += 1
            continue
        if in_d:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == "'":
                in_d = False
            i += 1
            continue
        if ch == '"':
            in_s = True
            i += 1
            continue
        if ch == "'":
            in_d = True
            i += 1
            continue
        if ch == "{":
            depth += 1
            stack.append(line)
            last_open_line = line
        elif ch == "}":
            depth -= 1
            if stack:
                stack.pop()
            if depth < 0:
                print(f"extra }} at line {line} col {col}")
                return 2
        i += 1
    print(f"{path}: final brace depth = {depth} (last open-brace line ~{last_open_line})")
    if stack:
        print("Unclosed { at lines:", stack[-10:])
    return 0 if depth == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
