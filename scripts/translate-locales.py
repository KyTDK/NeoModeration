#!/usr/bin/env python3
"""Validate bundled locale files have matching keys.

The Spanish locale is maintained in-repo because the plugin has a small fixed
message surface and CI only needs deterministic parity checks.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

def locale_keys(path: Path) -> set[str]:
    keys: set[str] = set()
    stack: list[tuple[int, str]] = []

    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        if "\t" in raw_line[:indent]:
            raise ValueError(f"{path}:{line_number}: tabs are not supported in locale keys")
        if ":" not in stripped:
            raise ValueError(f"{path}:{line_number}: expected key: value")

        key, value = stripped.split(":", 1)
        key = key.strip().strip("'\"")
        if not key:
            raise ValueError(f"{path}:{line_number}: empty key")

        while stack and stack[-1][0] >= indent:
            stack.pop()
        dotted = ".".join([parent for _, parent in stack] + [key])
        if value.strip():
            keys.add(dotted)
        else:
            stack.append((indent, key))
    return keys


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    root = Path("src/main/resources/locale")
    base = locale_keys(root / "en_US.yml")
    failures = []
    for locale in sorted(root.glob("*.yml")):
        keys = locale_keys(locale)
        missing = sorted(base - keys)
        extra = sorted(keys - base)
        if missing or extra:
            failures.append(f"{locale.name}: missing={missing} extra={extra}")
    if failures:
        print("\n".join(failures))
        return 1
    print("Locale check complete: all bundled locale keys match en_US.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
