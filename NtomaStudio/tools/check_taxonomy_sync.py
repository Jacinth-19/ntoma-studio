#!/usr/bin/env python3
"""Verify that the fabric taxonomy is consistent across every place it is written down.

The taxonomy lives in five files that cannot see each other:

  1. app/.../domain/model/Fabric.kt        — the `FabricCategory` enum
  2. app/.../media/ColorPatternAnalyzer.kt — `usesFor()`, an exhaustive `when`
  3. app/.../ui/screens/discover/DiscoverScreen.kt — palette + pattern `when`s
  4. app/src/main/res/values*/strings.xml  — labels and descriptions
  5. tools/dataset/label_schema.json       — the v2 training schema (Ghana only)

Adding an enum member without updating (2) and (3) is a **compile error** in Kotlin:
a `when` used as an expression over an enum must be exhaustive or carry an `else`.
Renaming a member is worse than a compile error — `category` is persisted as a
String, so old rows silently degrade to UNKNOWN (see DbMappers.enumOrDefault).

This tool is read-only and needs no Android SDK, JDK or network, so it can be run
in CI and on a machine that cannot build the app. Run it after every taxonomy edit:

    python3 tools/check_taxonomy_sync.py

Exit codes: 0 clean, 1 errors, 2 warnings only.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"
EN_STRINGS = RES / "values/strings.xml"
LOCALES = ["ee", "gaa", "tw"]
SCHEMA = ROOT / "tools/dataset/label_schema.json"

# ---------------------------------------------------------------------------
# The one hand-maintained piece: how the training schema names map onto the
# enum the app ships. The schema separates classes that need different training
# data; the app only needs different display names. Five names differ.
#
#   KENTE_ASHANTI   -> KENTE          (Ashanti is the default sense of "kente")
#   FUGU_BATAKARI   -> FUGU           (aka: batakari, fugu)
#   BROCADE_BAZIN   -> BROCADE        (bazin is the Malian trade name)
#   SATIN_SILK      -> SILK           (sold as one bolt in Ghanaian markets)
#   CHIFFON_GEORGETTE -> CHIFFON
#   WAX_REAL        -> WAX            (double-sided Vlisco-class print)
#   FANCY_PRINT     -> ANKARA         (one-sided hitarget/imiwax print)
#
# WAX/FANCY_PRINT -> WAX/ANKARA is a deliberate many-to-one: v1 shipped both
# names and market vocabulary uses them interchangeably, so the app keeps the
# familiar pair and the *schema* carries the printing-side distinction that the
# model needs. Nothing is lost — the training label is still exact.
SCHEMA_TO_ENUM = {
    "KENTE_ASHANTI": "KENTE",
    "KETE_EWE": "KETE_EWE",
    "FUGU_BATAKARI": "FUGU",
    "GONJA": "GONJA",
    "ADINKRA": "ADINKRA",
    "BATIK": "BATIK",
    "TIEDYE": "TIEDYE",
    "NWOMU": "NWOMU",
    "OBAMA_EMBROIDERY": "OBAMA_EMBROIDERY",
    "KENTE_PRINT": "KENTE_PRINT",
    "WAX_REAL": "WAX",
    "FANCY_PRINT": "ANKARA",
    "JAVA_PRINT": "JAVA_PRINT",
    "LACE": "LACE",
    "BROCADE_BAZIN": "BROCADE",
    "SATIN_SILK": "SILK",
    "CHIFFON_GEORGETTE": "CHIFFON",
    "CREPE": "CREPE",
    "ORGANZA_TULLE": "ORGANZA_TULLE",
    "VELVET": "VELVET",
    "LINEN": "LINEN",
    "COTTON_PLAIN": "COTTON_PLAIN",
    "DENIM": "DENIM",
    "SEERSUCKER": "SEERSUCKER",
    "TAPESTRY_JACQUARD": "TAPESTRY_JACQUARD",
    "UNKNOWN": "UNKNOWN",
}

# Enum members allowed to exist without a schema class. Empty by design: the
# taxonomy is the single source of truth for what the app may name.
ALLOWED_ENUM_ONLY: set[str] = set()

# Non-constant members that legitimately follow `FabricCategory.`
NON_CONSTANT_MEMBERS = {"values", "entries", "name", "ordinal", "Companion", "valueOf"}


# ---------------------------------------------------------------------------
# Kotlin source handling
# ---------------------------------------------------------------------------
def strip_kotlin(src: str) -> str:
    """Blank out comments and string/char literals, preserving offsets and newlines.

    Structural scanning must not see `FabricCategory.KENTE` inside a comment or a
    `->` inside a string, but line numbers have to survive so messages are useful.
    """
    out = list(src)
    i, n = 0, len(src)

    def blank(a: int, b: int) -> None:
        for k in range(a, min(b, n)):
            if out[k] != "\n":
                out[k] = " "

    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            blank(i, n if j == -1 else j)
            i = n if j == -1 else j
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            j = n if j == -1 else j + 2
            blank(i, j)
            i = j
        elif c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                j = n if j == -1 else j + 3
            else:
                j = i + 1
                while j < n:
                    if src[j] == "\\":
                        j += 2
                        continue
                    if src[j] == '"' or src[j] == "\n":
                        j += 1
                        break
                    j += 1
            blank(i, j)
            i = j
        elif c == "'":
            j = i + 2 if (i + 1 < n and src[i + 1] == "\\") else i + 1
            if j < n and src[j] == "'":
                j += 1
            blank(i, j)
            i = j
        else:
            i += 1
    return "".join(out)


def match_brace(src: str, open_idx: int) -> int:
    """Index just past the `}` matching the `{` at open_idx."""
    depth = 0
    for i in range(open_idx, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return i + 1
    raise ValueError(f"unbalanced braces from offset {open_idx}")


def split_top_level(body: str, sep: str = ",") -> list[str]:
    """Split on `sep` at brace/paren/bracket depth 0."""
    parts, cur, depth = [], [], 0
    for ch in body:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    parts.append("".join(cur))
    return parts


def line_of(src: str, idx: int) -> int:
    return src.count("\n", 0, idx) + 1


# ---------------------------------------------------------------------------
# 1. The enum
# ---------------------------------------------------------------------------
def parse_any_enum(src: str, class_name: str, masked: str | None = None) -> list[dict]:
    """Return [{name, label, desc, line}] for every member of `enum class <class_name>`."""
    masked = masked if masked is not None else strip_kotlin(src)
    m = re.search(rf"enum\s+class\s+{re.escape(class_name)}\s*[\(\{{]", masked)
    if not m:
        return []
    if masked[m.end() - 1] == "{":
        brace = m.end() - 1
    else:
        # The match consumed `(`; skip the primary-constructor params.
        depth = 0
        i = m.end() - 1
        while i < len(masked):
            if masked[i] == "(":
                depth += 1
            elif masked[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        brace = masked.index("{", i)
    body_start = brace + 1
    body_end = match_brace(masked, brace) - 1
    body = masked[body_start:body_end]

    members = []
    for chunk in split_top_level(body, ","):
        s = chunk.strip()
        if not s:
            continue
        name_m = re.match(r"([A-Z][A-Z0-9_]*)\s*[(\n]", s)
        if not name_m:
            continue
        name = name_m.group(1)
        refs = re.findall(r"R\.string\.(\w+)", s)
        members.append(
            {
                "name": name,
                "label": refs[0] if refs else None,
                "desc": refs[1] if len(refs) > 1 else None,
                "line": line_of(src, body_start + body.index(chunk[:20])),
            }
        )
    return members


def parse_enum(src: str) -> list[dict]:
    """FabricCategory members, with the label/description pair pulled out."""
    members = parse_any_enum(src, "FabricCategory")
    if not members:
        raise SystemExit("ERROR: could not find `enum class FabricCategory` in Fabric.kt")
    return members


# ---------------------------------------------------------------------------
# 2/3. `when` blocks over FabricCategory
# ---------------------------------------------------------------------------
def find_when_blocks(masked: str) -> list[tuple[int, int]]:
    """(body_start, body_end) offsets for the `{ ... }` of every `when`."""
    blocks = []
    for m in re.finditer(r"\bwhen\b", masked):
        i = m.end()
        while i < len(masked) and masked[i] in " \t\n":
            i += 1
        if i < len(masked) and masked[i] == "(":
            depth = 0
            while i < len(masked):
                if masked[i] == "(":
                    depth += 1
                elif masked[i] == ")":
                    depth -= 1
                    if depth == 0:
                        i += 1
                        break
                i += 1
            while i < len(masked) and masked[i] in " \t\n":
                i += 1
        if i < len(masked) and masked[i] == "{":
            blocks.append((i + 1, match_brace(masked, i) - 1))
    return blocks


def parse_when_arms(masked: str, start: int, end: int) -> tuple[set[str], bool, list[str]]:
    """Return (members covered, has_else, duplicate members) for one when block."""
    covered: set[str] = set()
    dups: list[str] = []
    has_else = False
    depth = 0
    pending: list[str] = []

    pos = start
    while pos < end:
        nl = masked.find("\n", pos)
        if nl == -1 or nl > end:
            nl = end
        line = masked[pos:nl]
        stripped = line.strip()

        # Track depth while scanning the line; an arm arrow only counts at depth 0.
        arrow_at = None
        d = depth
        for k, ch in enumerate(line):
            if ch in "([{":
                d += 1
            elif ch in ")]}":
                d -= 1
            elif ch == "-" and k + 1 < len(line) and line[k + 1] == ">" and d == 0:
                arrow_at = k
                break

        if arrow_at is not None:
            cond = "".join(pending) + line[:arrow_at]
            if re.search(r"\belse\b", cond):
                has_else = True
            else:
                for name in re.findall(r"FabricCategory\.([A-Z][A-Z0-9_]*)", cond):
                    if name in covered:
                        dups.append(name)
                    covered.add(name)
            pending = []
        elif stripped and not re.fullmatch(r"[\}\{\)\s,]*", stripped):
            # Accumulate multi-line arm conditions, but never a bare closing brace.
            pending.append(line + "\n")

        depth += line.count("{") - line.count("}")
        if depth < 0:
            depth = 0
        pos = nl + 1

    return covered, has_else, dups


# ---------------------------------------------------------------------------
# 4. Strings
# ---------------------------------------------------------------------------
def parse_string_keys(path: Path) -> list[str]:
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8")
    return re.findall(r'<string name="([^"]+)"', text)


# ---------------------------------------------------------------------------
def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []

    fabric_kt = KOTLIN_ROOT / "com/ntoma/studio/domain/model/Fabric.kt"
    if not fabric_kt.exists():
        raise SystemExit(f"ERROR: {fabric_kt} not found")
    src = fabric_kt.read_text(encoding="utf-8")
    members = parse_enum(src)
    names = {m["name"] for m in members}

    print(f"FabricCategory: {len(members)} members")
    print("  " + ", ".join(m["name"] for m in members))
    print()

    # -- 1. every member carries a label and a description ------------------
    for m in members:
        if not m["label"]:
            errors.append(f"Fabric.kt:{m['line']} {m['name']} has no labelRes")
        if not m["desc"]:
            errors.append(f"Fabric.kt:{m['line']} {m['name']} has no descriptionRes")

    # -- 2. every FabricCategory.X reference resolves ------------------------
    kt_files = sorted(KOTLIN_ROOT.rglob("*.kt"))
    refs: dict[str, list[str]] = {}
    for p in kt_files:
        masked = strip_kotlin(p.read_text(encoding="utf-8"))
        for name in re.findall(r"FabricCategory\.([A-Za-z][A-Za-z0-9_]*)", masked):
            refs.setdefault(name, []).append(str(p.relative_to(ROOT)))
    unknown = {k: v for k, v in refs.items() if k not in names and k not in NON_CONSTANT_MEMBERS}
    for k, where in sorted(unknown.items()):
        errors.append(
            f"unknown enum member FabricCategory.{k} referenced in {', '.join(sorted(set(where)))}"
        )
    print(f"References checked: {len(refs)} distinct members across {len(kt_files)} Kotlin files")

    # -- 2b. the same sweep for EVERY enum in the app -----------------------
    # A typo like `Occasion.CHURCHH` or `PatternType.FLORALL` is a compile error,
    # and this sandbox has no JDK, so catch it here instead. Only references to
    # enums declared in this source tree are checked, which keeps false
    # positives at zero (R.*, objects and library types are simply not enums
    # we parsed).
    enum_map: dict[str, set[str]] = {}
    for p in kt_files:
        masked = strip_kotlin(p.read_text(encoding="utf-8"))
        for em in re.finditer(r"\benum\s+class\s+(\w+)", masked):
            cls = em.group(1)
            got = parse_any_enum(p.read_text(encoding="utf-8"), cls, masked)
            if got:
                enum_map.setdefault(cls, set()).update(g["name"] for g in got)

    bad_enum_refs: dict[tuple[str, str], list[str]] = {}
    for p in kt_files:
        masked = strip_kotlin(p.read_text(encoding="utf-8"))
        rel = str(p.relative_to(ROOT))
        for cls, member in re.findall(r"\b(\w+)\.([A-Z][A-Z0-9_]*)\b", masked):
            if cls in enum_map and member not in enum_map[cls] and cls != "FabricCategory":
                bad_enum_refs.setdefault((cls, member), []).append(rel)
    for (cls, member), where in sorted(bad_enum_refs.items()):
        errors.append(
            f"unknown enum member {cls}.{member} in {', '.join(sorted(set(where)))} "
            f"(valid: {', '.join(sorted(enum_map[cls]))})"
        )
    print(
        f"Enums discovered: {len(enum_map)} — "
        + ", ".join(f"{k}({len(v)})" for k, v in sorted(enum_map.items()))
    )
    unused = sorted(names - set(refs))
    if unused:
        warnings.append(f"enum members never referenced in Kotlin: {', '.join(unused)}")
    print()

    # -- 3. exhaustive `when` blocks ----------------------------------------
    print("`when` blocks mentioning FabricCategory:")
    for p in kt_files:
        text = p.read_text(encoding="utf-8")
        masked = strip_kotlin(text)
        rel = p.relative_to(ROOT)
        for start, end in find_when_blocks(masked):
            covered, has_else, dups = parse_when_arms(masked, start, end)
            if not covered:
                continue
            missing = sorted(names - covered)
            line = line_of(masked, start)
            state = "exhaustive" if (not missing or has_else) else "INCOMPLETE"
            note = " + else" if has_else else ""
            print(f"  {rel}:{line}  {len(covered)} arms{note}  [{state}]")
            if missing and not has_else:
                errors.append(
                    f"{rel}:{line} `when` is not exhaustive and has no `else` — "
                    f"missing {', '.join('FabricCategory.' + x for x in missing)}"
                )
            for d in sorted(set(dups)):
                errors.append(f"{rel}:{line} FabricCategory.{d} appears in more than one arm")
    print()

    # -- 4. strings exist, in English and in every locale -------------------
    en_keys = parse_string_keys(EN_STRINGS)
    en_set = set(en_keys)
    dupe_keys = {k for k in en_keys if en_keys.count(k) > 1}
    for k in sorted(dupe_keys):
        errors.append(f"duplicate <string name=\"{k}\"> in values/strings.xml")

    missing_str = []
    for m in members:
        for kind in ("label", "desc"):
            key = m[kind]
            if key and key not in en_set:
                missing_str.append((m["name"], kind, key))
    for name, kind, key in missing_str:
        errors.append(f"FabricCategory.{name} {kind} -> R.string.{key} is not defined in values/strings.xml")
    print(f"English strings: {len(en_set)} total, all {2 * len(members)} fabric label/desc ids present"
          if not missing_str else f"English strings: {len(en_set)} total, {len(missing_str)} MISSING")

    referenced = set()
    for p in kt_files:
        referenced |= set(re.findall(r"R\.string\.(\w+)", strip_kotlin(p.read_text(encoding="utf-8"))))
    declared = re.findall(
        r'<string name="(fabric_[a-z0-9_]+)"', EN_STRINGS.read_text(encoding="utf-8")
    )
    orphan = sorted(set(declared) - referenced)
    if orphan:
        warnings.append(f"fabric_* strings not referenced from Kotlin: {', '.join(orphan)}")

    # Keys declared in tools/i18n/untranslated.txt are omitted from the locale
    # files on purpose and fall back to English per key. Any *other* absence is
    # locale drift, and a name in that file that is not a real string is a typo
    # that would silently disable the check for that key.
    untranslated_path = ROOT / "tools/i18n/untranslated.txt"
    untranslated: set[str] = set()
    if untranslated_path.exists():
        for line in untranslated_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                untranslated.add(line)
        for k in sorted(untranslated - en_set):
            errors.append(f"untranslated.txt names {k}, which is not defined in values/strings.xml")
    print(f"untranslated.txt: {len(untranslated)} keys on English fallback")

    for tag in LOCALES:
        loc = RES / f"values-{tag}/strings.xml"
        loc_keys = parse_string_keys(loc)
        if not loc_keys:
            warnings.append(f"values-{tag}/strings.xml missing or unreadable")
            continue
        loc_set = set(loc_keys)
        miss = sorted((en_set - loc_set) - untranslated)
        extra = sorted(loc_set - en_set)
        if miss:
            errors.append(f"values-{tag} missing {len(miss)} keys: {', '.join(miss[:6])}")
        if extra:
            errors.append(f"values-{tag} has {len(extra)} keys absent from English: {', '.join(extra[:6])}")
        print(f"values-{tag}: {len(loc_keys)} keys, {len(miss)} missing, {len(extra)} extra")
    print()

    # -- 5. schema <-> enum -------------------------------------------------
    if SCHEMA.exists():
        schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        classes = schema["classes"]
        mapped_enums = set(SCHEMA_TO_ENUM.values())
        print(f"label_schema.json v{schema.get('version')}: {len(classes)} classes")
        for c in classes:
            if c not in SCHEMA_TO_ENUM:
                errors.append(f"schema class {c} has no entry in SCHEMA_TO_ENUM")
        for c in SCHEMA_TO_ENUM:
            if c not in classes:
                errors.append(f"SCHEMA_TO_ENUM maps {c}, which is not a schema class")
        for e in sorted(mapped_enums - names):
            errors.append(f"SCHEMA_TO_ENUM targets FabricCategory.{e}, which does not exist")
        for e in sorted(names - mapped_enums - ALLOWED_ENUM_ONLY):
            errors.append(f"FabricCategory.{e} has no schema class (add it, or map it)")
        if not (set(classes) - set(SCHEMA_TO_ENUM)):
            print(f"  all {len(classes)} classes map to enum members; no unmapped members")
    else:
        warnings.append("tools/dataset/label_schema.json not found; schema check skipped")
    print()

    # -- report -------------------------------------------------------------
    for w in warnings:
        print(f"WARNING: {w}")
    for e in errors:
        print(f"ERROR: {e}")
    if errors:
        print(f"\nFAILED: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    if warnings:
        print(f"\nOK with {len(warnings)} warning(s)")
        return 2
    print("OK: taxonomy is consistent across enum, when-blocks, strings, locales and schema")
    return 0


if __name__ == "__main__":
    sys.exit(main())
