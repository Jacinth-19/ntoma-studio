#!/usr/bin/env python3
"""Build values-*/strings.xml for tw / gaa / ee from the TSV translation packs.

- Key order follows the English values/strings.xml exactly.
- Every English key must have exactly one translation; nothing may be missing.
- Format specifiers (%1$s, %1$d, %2$s, %2$d, %%) must match the English value.
- XML escaping is applied; single quotes become \' (Android requirement).
- The 55 keys already shipped in values-tw keep their existing values
  (they predate the TSV pack and were reviewed at ship time).
"""

import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
I18N = Path(__file__).resolve().parent
VALUES = ROOT / "app/src/main/res"

PACKS = {
    "tw": ("Twi", "tw_a.tsv", "tw_b.tsv"),
    "gaa": ("Ga", "gaa_a.tsv", "gaa_b.tsv"),
    "ee": ("Ewe", "ee_a.tsv", "ee_b.tsv"),
}

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
  Ntoma Studio — {lang} ({tag}) translations.
  Covers every string in values/strings.xml.
  Community/starter quality: please have a native-speaker reviewer refine
  terminology before release. Specifiers and XML escaping are preserved.
-->
<resources>
"""

SPEC = re.compile(r"%(?:\d+\$)?[sdf%]")


def read_english():
    text = (VALUES / "values/strings.xml").read_text(encoding="utf-8")
    pairs = re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', text, re.S)
    return [(k, v) for k, v in pairs]


def android_value(v: str) -> str:
    """Convert stored text to an Android-escapable XML string body."""
    v = escape(v)                      # & < >
    v = v.replace('"', "&quot;")
    # escape every single quote that is not already escaped
    v = re.sub(r"(?<!\\)'", r"\\'", v)
    return v


def specifiers(v: str):
    return sorted(SPEC.findall(v))


def load_pack(files):
    out = {}
    for name in files:
        for i, line in enumerate((I18N / name).read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            if line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                print(f"BAD LINE {name}:{i}: {line[:60]}")
                sys.exit(2)
            key = parts[0].strip()
            val = "\t".join(parts[1:]).rstrip("\n")
            if key in out:
                print(f"DUPLICATE KEY {name}: {key}")
                sys.exit(2)
            out[key] = val
    return out


def main():
    english = read_english()
    en_map = dict(english)
    print(f"English strings: {len(english)}")

    failures = 0
    for tag, (lang, *files) in PACKS.items():
        pack = load_pack(files)
        missing = [k for k, _ in english if k not in pack]
        extra = [k for k in pack if k not in en_map]
        if missing:
            failures += 1
            print(f"[{tag}] MISSING {len(missing)}: {missing[:8]}")
        if extra:
            failures += 1
            print(f"[{tag}] EXTRA {len(extra)}: {extra[:8]}")
        bad_spec = [k for k, v in english
                    if k in pack and specifiers(v) != specifiers(pack[k])]
        if bad_spec:
            failures += 1
            print(f"[{tag}] SPECIFIER MISMATCH: {bad_spec[:8]}")
        empty = [k for k, v in english if k in pack and not v.strip()]
        if empty:
            failures += 1
            print(f"[{tag}] EMPTY: {empty[:8]}")
        print(f"[{tag}] pack={len(pack)} translated={len([k for k, _ in english if k in pack])}")

        target = VALUES / f"values-{tag}/strings.xml"
        target.parent.mkdir(parents=True, exist_ok=True)
        # preserve pre-existing reviewed values (values-tw shipped 55 strings)
        keep = {}
        if target.exists():
            old = dict(re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>',
                                  target.read_text(encoding="utf-8"), re.S))
            keep = old
        lines = [HEADER.format(lang=lang, tag=tag)]
        for k, en_val in english:
            if k in keep and keep[k].strip():
                body = keep[k].strip()   # already escaped at ship time
            else:
                body = android_value(pack.get(k, ""))
            lines.append(f'    <string name="{k}">{body}</string>\n')
        lines.append("</resources>\n")
        target.write_text("".join(lines), encoding="utf-8")
        print(f"[{tag}] wrote {target.relative_to(ROOT)} ({len(english)} entries, "
              f"{len(keep)} preserved)")

    if failures:
        print("VALIDATION FAILED")
        sys.exit(1)
    print("All packs validated and written.")


if __name__ == "__main__":
    main()
