#!/usr/bin/env python3
"""Parse the per-mon Delta moveset spreadsheets (xlsx) into a JSON learnset index.

Each spreadsheet has four side-by-side tables:
  Cols A-C : Level | Move | Type      (level-up)
  Cols E-G : TM/HM | Move | Type      (machine)
  Cols I-J : Move | Type               (tutor)
  Cols L-M : Move | Type               (breeding/egg)

Output: tools/database/generated/delta-movesets.generated.json
Schema:
  {
    "sourceMeta": {...},
    "species": [
      {
        "speciesKey": "aegislash-delta",
        "displayName": "Aegislash-Delta",
        "types": ["Steel", "Fire"],
        "moves": [
          {"id": "infernalshield", "displayName": "Infernal Shield", "type": "fire", "methods": ["L1"]},
          ...
        ]
      }
    ]
  }
"""
import argparse
import datetime as dt
import json
import sys
from pathlib import Path

from _common import compact, load_json, slugify

try:
    from openpyxl import load_workbook
except ImportError:
    print("openpyxl is required. Install with: py -m pip install openpyxl", file=sys.stderr)
    raise

DEFAULT_MOVESETS_DIR = "Z:/Cb delta/Delta team building/movesets"
DEFAULT_OUTPUT = "tools/database/generated/delta-movesets.generated.json"


def parse_type_cell(value) -> str | None:
    """'(Fire)' -> 'fire'. Returns None for empty/sentinel."""
    if not value:
        return None
    text = str(value).strip().strip("()").strip().lower()
    return text or None


def parse_move_cell(value):
    """Returns the move display name or None for empty cells."""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def parse_workbook(path: Path) -> dict | None:
    wb = load_workbook(path, data_only=True, read_only=True)
    ws = wb.active

    rows = list(ws.iter_rows(values_only=True))
    if len(rows) < 5:
        return None

    name_cell = rows[0][0] if rows[0] else None
    type_cell = rows[1][0] if len(rows) > 1 and rows[1] else None
    if not name_cell or not str(name_cell).lower().startswith("pok"):
        return None

    display_name = str(name_cell).split(":", 1)[1].strip()
    types_raw = str(type_cell).split(":", 1)[1].strip() if type_cell and ":" in str(type_cell) else ""
    types = [t.strip() for t in types_raw.replace("/", ",").split(",") if t.strip()]

    moves: dict[str, dict] = {}  # move_id -> entry (deduped, methods accumulated)

    def add(move_name: str | None, type_text: str | None, method: str):
        if not move_name:
            return
        # Strip leading/trailing punctuation, ignore obvious non-moves.
        if move_name.lower() in {"move", "tm/hm", "level", "tutor", "breeding"}:
            return
        move_id = compact(move_name)
        if not move_id:
            return
        existing = moves.get(move_id)
        if existing is None:
            moves[move_id] = {
                "id": move_id,
                "displayName": move_name,
                "type": type_text,
                "methods": [method],
            }
        else:
            if method not in existing["methods"]:
                existing["methods"].append(method)
            if not existing.get("type") and type_text:
                existing["type"] = type_text

    # Skip header rows 0,1,2,3 (names, types, section labels, column headers).
    for row in rows[4:]:
        # Row width may vary; pad so direct indexing is safe.
        cells = list(row) + [None] * (16 - len(row))

        # Level-up: A (level), B (move), C (type)
        level_val = cells[0]
        level_move = parse_move_cell(cells[1])
        level_type = parse_type_cell(cells[2])
        if level_move:
            level_label = ""
            if isinstance(level_val, (int, float)):
                level_label = f"L{int(level_val)}"
            elif level_val:
                level_label = f"L{str(level_val).strip()}"
            else:
                level_label = "L"
            add(level_move, level_type, level_label)

        # TM/HM: E (tm code), F (move), G (type)
        tm_code = cells[4]
        tm_move = parse_move_cell(cells[5])
        tm_type = parse_type_cell(cells[6])
        if tm_move:
            method = f"M:{str(tm_code).strip()}" if tm_code else "M"
            add(tm_move, tm_type, method)

        # Tutor: I (move), J (type)
        tutor_move = parse_move_cell(cells[8])
        tutor_type = parse_type_cell(cells[9])
        if tutor_move:
            add(tutor_move, tutor_type, "T")

        # Breeding/egg: L (move), M (type)
        egg_move = parse_move_cell(cells[11])
        egg_type = parse_type_cell(cells[12])
        if egg_move:
            add(egg_move, egg_type, "E")

    species_key = slugify(display_name)
    return {
        "speciesKey": species_key,
        "displayName": display_name,
        "types": types,
        "moves": sorted(moves.values(), key=lambda m: m["displayName"]),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Parse Delta moveset xlsx files into a JSON learnset index.")
    parser.add_argument("--movesets-dir", default=DEFAULT_MOVESETS_DIR)
    parser.add_argument("--output", default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    movesets_dir = Path(args.movesets_dir)
    if not movesets_dir.exists():
        print(f"Movesets directory not found: {movesets_dir}", file=sys.stderr)
        return 1

    files = sorted(movesets_dir.glob("*.xlsx"))
    if not files:
        print(f"No xlsx files in {movesets_dir}", file=sys.stderr)
        return 1

    species_records = []
    failed: list[tuple[str, str]] = []
    for path in files:
        try:
            record = parse_workbook(path)
        except Exception as exc:
            failed.append((path.name, repr(exc)))
            continue
        if not record:
            failed.append((path.name, "no header / empty"))
            continue
        species_records.append(record)

    species_records.sort(key=lambda r: r["speciesKey"])

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "sourceMeta": {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "movesetsDir": str(movesets_dir),
            "fileCount": len(files),
            "parsedCount": len(species_records),
            "failedCount": len(failed),
        },
        "species": species_records,
    }
    output_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    print(f"Parsed {len(species_records)} / {len(files)} Delta moveset spreadsheets -> {output_path}")
    move_counts = [len(r["moves"]) for r in species_records]
    if move_counts:
        print(f"  moves per mon: min={min(move_counts)}, max={max(move_counts)}, avg={sum(move_counts)/len(move_counts):.1f}")
    if failed:
        print(f"  Failed ({len(failed)}):")
        for name, reason in failed[:10]:
            print(f"    {name}: {reason}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
