#!/usr/bin/env python3
"""Extract Showdown learnsets + evolution chain for Cobblemon Delta and emit
per-mon curation worksheets (one JSON per final evo).

Outputs:
  - tools/database/generated/learnsets.generated.json  (merged, one file)
  - tools/database/generated/mons/<species-key>.json   (one worksheet per final evo)
"""
import argparse
import datetime as dt
import json
import subprocess
import sys
from pathlib import Path

from _common import alias_variants, compact, detect_showdown_data_dir, load_json, slugify


MOVE_FLAGS_DEFAULT = "src/main/resources/data/deltacalc/database/move-flags.generated.json"
TEAM_BUILDER_DEFAULT = "cobblemon-delta-pokemon-team-builder.json"
DELTA_MOVESETS_DEFAULT = "tools/database/generated/delta-movesets.generated.json"
OUTPUT_DEFAULT = "tools/database/generated/learnsets.generated.json"
WORKSHEETS_DEFAULT = "tools/database/generated/mons"


def load_showdown_via_node(data_dir: Path) -> dict:
    """Load both pokedex.js and learnsets.js in one Node subprocess."""
    pokedex_path = data_dir / "pokedex.js"
    learnsets_path = data_dir / "learnsets.js"
    if not pokedex_path.exists():
        raise RuntimeError(f"Showdown pokedex.js not found at {pokedex_path}")
    if not learnsets_path.exists():
        raise RuntimeError(f"Showdown learnsets.js not found at {learnsets_path}")

    node_script = (
        f"const pokedex = require({json.dumps(str(pokedex_path))}).Pokedex;\n"
        f"const learnsets = require({json.dumps(str(learnsets_path))}).Learnsets;\n"
        "console.log(JSON.stringify({pokedex, learnsets}));\n"
    )
    result = subprocess.run(
        ["node", "-e", node_script],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    if not result.stdout:
        raise RuntimeError("Empty output from node subprocess loading Showdown data")
    return json.loads(result.stdout)


def is_final_evo(entry: dict) -> bool:
    """Final = no further evolutions. Megas/regionals always count as final."""
    forme = (entry.get("forme") or "").lower()
    if forme in {"mega", "mega-x", "mega-y", "primal", "gmax"}:
        return True
    evos = entry.get("evos")
    return not evos  # None or []


def normalize_methods(method_codes: list[str]) -> list[str]:
    """Strip generation prefix from learnset codes for compactness.
    "9L1" -> "L1", "8M" -> "M", "9T" -> "T", "9E" -> "E".
    """
    out = []
    seen = set()
    for code in method_codes or []:
        if not isinstance(code, str) or len(code) < 2:
            continue
        # First char is generation digit; strip leading digit(s).
        i = 0
        while i < len(code) and code[i].isdigit():
            i += 1
        stripped = code[i:] or code
        if stripped not in seen:
            seen.add(stripped)
            out.append(stripped)
    return out


def method_priority(methods: list[str]) -> int:
    """Lower number = more "natural" learn method (level-up first, machine, etc.)."""
    bag = {m[0] for m in methods if m}
    if "L" in bag:
        return 0
    if "M" in bag:
        return 1
    if "T" in bag:
        return 2
    if "E" in bag:
        return 3
    return 4


def sort_moves(moves: list[dict]) -> list[dict]:
    """Sort: status last, then by category, then by base power desc, then name."""
    cat_rank = {"physical": 0, "special": 1, "status": 2}
    return sorted(
        moves,
        key=lambda m: (
            cat_rank.get((m.get("category") or "").lower(), 9),
            -(m.get("basePower") or 0),
            m.get("displayName") or "",
        ),
    )


def load_team_builder(path: Path) -> dict[str, dict]:
    """Index Cobblemon Delta team-builder entries by slugified name."""
    if not path.exists():
        return {}
    payload = load_json(path)
    indexed: dict[str, dict] = {}
    for entry in payload.get("entries", []):
        name = entry.get("name")
        if not name:
            continue
        indexed[slugify(name)] = entry
    return indexed


def load_delta_movesets(path: Path, move_flags_index: dict[str, dict]) -> dict[str, list[dict]]:
    """Index parsed xlsx Delta movesets by speciesKey, enriching each move with
    BP/category/accuracy from move-flags when available."""
    if not path.exists():
        return {}
    payload = load_json(path)
    indexed: dict[str, list[dict]] = {}
    for entry in payload.get("species", []):
        key = entry.get("speciesKey")
        if not key:
            continue
        enriched = []
        for move in entry.get("moves", []):
            flags = move_flags_index.get(move.get("id")) or {}
            enriched.append({
                "id": move["id"],
                "displayName": flags.get("displayName") or move.get("displayName") or move["id"],
                "type": flags.get("type") or move.get("type"),
                "category": flags.get("category"),
                "basePower": flags.get("basePower") or 0,
                "accuracy": flags.get("accuracy"),
                "priority": flags.get("priority", 0),
                "methods": move.get("methods", []),
                "_methodRank": method_priority(move.get("methods", [])),
            })
        indexed[key] = sort_moves(enriched)
    return indexed


def build_species_record(
    showdown_id: str,
    pokedex_entry: dict,
    learnset_block: dict | None,
    move_flags_index: dict[str, dict],
    team_builder_index: dict[str, dict],
    pokedex: dict,
) -> dict:
    display_name = pokedex_entry.get("name") or showdown_id
    species_key = slugify(display_name)
    forme = pokedex_entry.get("forme")
    base_species = pokedex_entry.get("baseSpecies")

    # Resolve learnset; many alt formes inherit from the base species.
    raw_learnset = (learnset_block or {}).get("learnset") or {}
    if not raw_learnset and base_species:
        base_id = compact(base_species).lower()
        # Showdown internal IDs are alphanumeric, all lowercase, no spaces/dashes.
        base_block = pokedex.get("learnsets_index", {}).get(base_id)
        if base_block:
            raw_learnset = base_block.get("learnset") or {}

    moves: list[dict] = []
    for move_id, method_codes in raw_learnset.items():
        flags = move_flags_index.get(move_id) or {}
        methods = normalize_methods(method_codes)
        moves.append(
            {
                "id": move_id,
                "displayName": flags.get("displayName") or move_id,
                "type": flags.get("type") or None,
                "category": flags.get("category") or None,
                "basePower": flags.get("basePower") or 0,
                "accuracy": flags.get("accuracy"),
                "priority": flags.get("priority", 0),
                "methods": methods,
                "_methodRank": method_priority(methods),
            }
        )
    moves = sort_moves(moves)

    abilities_dict = pokedex_entry.get("abilities") or {}
    ability_list: list[str] = []
    for slot in ("0", "1", "H", "S"):
        val = abilities_dict.get(slot)
        if val and val not in ability_list:
            ability_list.append(val)

    base_stats = pokedex_entry.get("baseStats") or {}

    # Team-builder enrichment for custom Delta entries.
    tb = team_builder_index.get(species_key)
    signature_text = []
    obtain_methods = []
    classification = None
    if tb:
        classification = tb.get("classification")
        for sig in tb.get("signatureAbilities", []) or []:
            text = sig.get("text")
            if text:
                signature_text.append(f"[ability] {text}")
        for sig in tb.get("signatureMoves", []) or []:
            text = sig.get("text")
            if text:
                signature_text.append(f"[move] {text}")
        obtain_methods = tb.get("obtainMethods") or []

    return {
        "speciesKey": species_key,
        "speciesId": showdown_id,
        "displayName": display_name,
        "forme": forme,
        "baseSpecies": base_species,
        "isFinalEvo": is_final_evo(pokedex_entry),
        "prevo": pokedex_entry.get("prevo"),
        "evos": pokedex_entry.get("evos") or [],
        "types": pokedex_entry.get("types") or [],
        "abilities": ability_list,
        "baseStats": base_stats,
        "weightKg": pokedex_entry.get("weightkg"),
        "classification": classification,
        "obtainMethods": obtain_methods,
        "signatureNotes": signature_text,
        "moves": moves,
    }


def build_curation_skeleton(record: dict) -> dict:
    """Pre-filled template that the curator copies into delta-curated-sets.json."""
    display_name = record["displayName"]
    species_key = record["speciesKey"]
    aliases = alias_variants(display_name, species_key)
    return {
        "speciesKey": species_key,
        "displayName": display_name,
        "speciesId": record["speciesId"],
        "source": "delta-curated",
        "aliases": aliases,
        "abilities": [{"id": "", "displayName": "", "type": "ability", "usagePercent": 100.0}],
        "items":     [{"id": "", "displayName": "", "type": "item",    "usagePercent": 100.0}],
        "moves":     [],
        "spreads":   [{"nature": "", "evs": {"hp": 0, "atk": 0, "def": 0, "spa": 0, "spd": 0, "spe": 0}, "usagePercent": 100.0}],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract Showdown learnsets and emit per-mon curation worksheets.")
    parser.add_argument("--showdown-dir", default="",
                        help="Path to Showdown data dir (containing pokedex.js, learnsets.js). Auto-detected if omitted.")
    parser.add_argument("--move-flags", default=MOVE_FLAGS_DEFAULT)
    parser.add_argument("--team-builder", default=TEAM_BUILDER_DEFAULT)
    parser.add_argument("--delta-movesets", default=DELTA_MOVESETS_DEFAULT,
                        help="Optional JSON of parsed xlsx Delta movesets; fills custom-mon stubs.")
    parser.add_argument("--output", default=OUTPUT_DEFAULT)
    parser.add_argument("--worksheets-dir", default=WORKSHEETS_DEFAULT)
    parser.add_argument("--include-non-final", action="store_true",
                        help="Emit worksheets for every species, not just final evos.")
    args = parser.parse_args()

    showdown_dir = Path(args.showdown_dir) if args.showdown_dir else detect_showdown_data_dir()
    if not showdown_dir or not showdown_dir.exists():
        print("Could not locate Showdown data dir. Pass --showdown-dir.", file=sys.stderr)
        return 1

    payload = load_showdown_via_node(showdown_dir)
    pokedex = payload["pokedex"]
    learnsets = payload["learnsets"]

    move_flags_path = Path(args.move_flags)
    move_flags_index: dict[str, dict] = {}
    if move_flags_path.exists():
        for entry in load_json(move_flags_path).get("moves", []):
            mid = entry.get("id")
            if mid:
                move_flags_index[mid] = entry
    else:
        print(f"WARN: move-flags file missing at {move_flags_path}; moves will lack type/BP.", file=sys.stderr)

    team_builder_index = load_team_builder(Path(args.team_builder))
    delta_movesets_index = load_delta_movesets(Path(args.delta_movesets), move_flags_index)

    # Build a side index for learnset inheritance lookups.
    pokedex_meta = {"learnsets_index": learnsets}

    records: list[dict] = []
    for showdown_id, entry in pokedex.items():
        learnset_block = learnsets.get(showdown_id)
        record = build_species_record(
            showdown_id, entry, learnset_block, move_flags_index, team_builder_index, pokedex_meta,
        )
        records.append(record)

    # Add stub records for custom Delta mons present in team-builder but missing from Showdown.
    # These have no legal-move list (Showdown lacks them) but still get a worksheet so the curator
    # can fill in moves manually from another source.
    showdown_keys = {r["speciesKey"] for r in records}
    stub_count = 0
    for tb_key, tb_entry in team_builder_index.items():
        if tb_key in showdown_keys:
            continue
        forms = tb_entry.get("forms") or []
        base_form = next((f for f in forms if f.get("name") in ("Base", None)), forms[0] if forms else {})
        types = base_form.get("types") or []
        abilities_block = base_form.get("abilities") or {}
        ability_list = [v for v in (
            abilities_block.get("primary"),
            abilities_block.get("secondary"),
            abilities_block.get("hidden"),
            *(abilities_block.get("extra") or []),
        ) if v]
        stats_block = base_form.get("stats") or {}
        base_stats = {k: int(stats_block[k]) for k in ("hp", "atk", "def", "spa", "spd", "spe") if k in stats_block}
        size_block = base_form.get("size") or {}
        signature_text = []
        for sig in tb_entry.get("signatureAbilities", []) or []:
            text = sig.get("text")
            if text:
                signature_text.append(f"[ability] {text}")
        for sig in tb_entry.get("signatureMoves", []) or []:
            text = sig.get("text")
            if text:
                signature_text.append(f"[move] {text}")
        delta_moves = delta_movesets_index.get(tb_key) or []
        is_stub = not delta_moves
        records.append({
            "speciesKey": tb_key,
            "speciesId": tb_key,
            "displayName": tb_entry.get("name") or tb_key,
            "forme": None,
            "baseSpecies": None,
            "isFinalEvo": True,
            "prevo": None,
            "evos": [],
            "types": types,
            "abilities": ability_list,
            "baseStats": base_stats,
            "weightKg": size_block.get("weightKg"),
            "classification": tb_entry.get("classification"),
            "obtainMethods": tb_entry.get("obtainMethods") or [],
            "signatureNotes": signature_text,
            "moves": delta_moves,
            "_stub": is_stub,
        })
        if is_stub:
            stub_count += 1

    # xlsx-only mons (no team-builder entry, no Showdown entry) — still emit them.
    seen_keys = {r["speciesKey"] for r in records}
    for xlsx_key, xlsx_moves in delta_movesets_index.items():
        if xlsx_key in seen_keys:
            continue
        # Recover a display name from the parsed moveset payload.
        display_name = next(
            (m["displayName"] for m in []),
            xlsx_key.replace("-", " ").title(),
        )
        records.append({
            "speciesKey": xlsx_key,
            "speciesId": xlsx_key,
            "displayName": display_name,
            "forme": None,
            "baseSpecies": None,
            "isFinalEvo": True,
            "prevo": None,
            "evos": [],
            "types": [],
            "abilities": [],
            "baseStats": {},
            "weightKg": None,
            "classification": None,
            "obtainMethods": [],
            "signatureNotes": [],
            "moves": xlsx_moves,
            "_stub": False,
            "_orphan": True,
        })

    records.sort(key=lambda r: r["speciesKey"])

    final_count = sum(1 for r in records if r["isFinalEvo"])
    moveless_finals = [r["speciesKey"] for r in records if r["isFinalEvo"] and not r["moves"]]

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    dataset = {
        "sourceMeta": {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "showdownDir": str(showdown_dir),
            "speciesCount": len(records),
            "finalEvoCount": final_count,
        },
        "species": records,
    }
    output_path.write_text(json.dumps(dataset, indent=2), encoding="utf-8")

    worksheets_dir = Path(args.worksheets_dir)
    worksheets_dir.mkdir(parents=True, exist_ok=True)
    for path in worksheets_dir.glob("*.json"):
        path.unlink()

    written = 0
    for record in records:
        if not args.include_non_final and not record["isFinalEvo"]:
            continue
        worksheet = {
            "_curated": build_curation_skeleton(record),
            "species": {
                "speciesKey": record["speciesKey"],
                "speciesId": record["speciesId"],
                "displayName": record["displayName"],
                "forme": record["forme"],
                "baseSpecies": record["baseSpecies"],
                "types": record["types"],
                "abilities": record["abilities"],
                "baseStats": record["baseStats"],
                "weightKg": record["weightKg"],
                "classification": record["classification"],
                "obtainMethods": record["obtainMethods"],
                "signatureNotes": record["signatureNotes"],
                "prevo": record["prevo"],
                "isStub": record.get("_stub", False),
            },
            "legalMoves": [
                {k: v for k, v in m.items() if k != "_methodRank"} for m in record["moves"]
            ],
        }
        if record.get("_stub"):
            worksheet["_note"] = (
                "STUB worksheet — Showdown pokedex.js does not contain this custom Delta mon. "
                "legalMoves is empty; fill _curated.moves from the Discord thread / wiki / in-game data."
            )
        target = worksheets_dir / f"{record['speciesKey']}.json"
        target.write_text(json.dumps(worksheet, indent=2), encoding="utf-8")
        written += 1

    delta_filled = sum(1 for r in records if r.get("_stub") is False and r["speciesKey"] in delta_movesets_index)
    orphan_count = sum(1 for r in records if r.get("_orphan"))
    print(
        f"Wrote {len(records)} species ({final_count} final evos), "
        f"{written} worksheets to {worksheets_dir}/."
    )
    print(
        f"  Delta xlsx coverage: {delta_filled} mons filled from xlsx, "
        f"{stub_count} still stubs (no xlsx), {orphan_count} xlsx-only orphans."
    )
    print(
        f"  Other moveless finals (Pokestar etc.): {len(moveless_finals) - stub_count}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
