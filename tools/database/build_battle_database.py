#!/usr/bin/env python3
import argparse
import concurrent.futures
import datetime as dt
import json
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

from _common import alias_variants, compact, detect_showdown_data_dir, load_json, slugify


RANKED_DEFAULTS = [
    "src/main/resources/data/deltacalc/usage/season-6-mid-1300.generated.json",
    "src/main/resources/data/deltacalc/usage/season-6-mid-1000.generated.json",
    "src/main/resources/data/deltacalc/usage/season-6-mid-1500.generated.json",
]


def detect_showdown_pokedex() -> Path | None:
    data_dir = detect_showdown_data_dir()
    if not data_dir:
        return None
    path = data_dir / "pokedex.js"
    return path if path.exists() else None


def load_ranked_entries(paths: list[Path]) -> tuple[dict[str, dict], dict[str, dict]]:
    species_map: dict[str, dict] = {}
    set_map: dict[str, dict] = {}

    for path in reversed(paths):
        source = source_label_for(path.name)
        payload = load_json(path)
        for entry in payload.get("species", []):
            species_key = slugify(entry.get("slug") or entry.get("speciesId") or entry.get("displayName", ""))
            if not species_key:
                continue
            display_name = entry.get("displayName") or species_key
            aliases = alias_variants(display_name, entry.get("slug"), entry.get("speciesId"), *entry.get("aliases", []))
            species_map[species_key] = {
                "speciesKey": species_key,
                "speciesId": entry.get("speciesId") or species_key,
                "displayName": display_name,
                "slug": entry.get("slug") or species_key,
                "formName": display_name.split("-", 1)[1] if "-" in display_name else None,
                "aliases": aliases,
                "typeNames": [str(t).strip().capitalize() for t in (entry.get("types") or []) if t],
                "baseStats": normalize_base_stats(entry.get("baseStats")),
            }
            set_map[species_key] = {
                "speciesKey": species_key,
                "speciesId": entry.get("speciesId") or species_key,
                "displayName": display_name,
                "slug": entry.get("slug") or species_key,
                "source": source,
                "usageRank": entry.get("usageRank", 0),
                "usagePercent": entry.get("usagePercent", 0.0),
                "sampleCount": entry.get("sampleCount", 0),
                "aliases": aliases,
                "moves": normalize_options(entry.get("moves", [])),
                "items": normalize_options(entry.get("items", [])),
                "abilities": normalize_options(entry.get("abilities", [])),
                "spreads": normalize_spreads(entry.get("spreads", [])),
            }
    return species_map, set_map


def source_label_for(filename: str) -> str:
    if "1300" in filename:
        return "delta-ranked-1300"
    if "1000" in filename:
        return "delta-ranked-1000"
    if "1500" in filename:
        return "delta-ranked-1500"
    return "delta-ranked"


def normalize_options(options: list[dict]) -> list[dict]:
    results = []
    for entry in options:
        display_name = entry.get("displayName")
        if not display_name:
            continue
        results.append(
            {
                "id": entry.get("id") or slugify(display_name),
                "displayName": display_name,
                "type": entry.get("type"),
                "usagePercent": entry.get("usagePercent", 0.0),
            }
        )
    return results


def normalize_spreads(spreads: list[dict]) -> list[dict]:
    results = []
    for entry in spreads:
        evs = entry.get("evs") or {}
        results.append(
            {
                "nature": entry.get("nature") or "Hardy",
                "evs": {
                    "hp": int(evs.get("hp", 0)),
                    "atk": int(evs.get("atk", 0)),
                    "def": int(evs.get("def", 0)),
                    "spa": int(evs.get("spa", 0)),
                    "spd": int(evs.get("spd", 0)),
                    "spe": int(evs.get("spe", 0)),
                },
                "usagePercent": entry.get("usagePercent", 0.0),
            }
        )
    return results


def normalize_base_stats(base_stats: dict | None) -> dict | None:
    if not base_stats:
        return None
    keys = ["hp", "atk", "def", "spa", "spd", "spe"]
    if not all(key in base_stats and base_stats[key] is not None for key in keys):
        return None
    return {key: int(base_stats[key]) for key in keys}


def load_showdown_species(pokedex_path: Path) -> dict[str, dict]:
    node_script = f"""
const pokedex = require({json.dumps(str(pokedex_path))}).Pokedex;
console.log(JSON.stringify(pokedex));
"""
    result = subprocess.run(
        ["node", "-e", node_script],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    if not result.stdout:
        raise RuntimeError("Failed to read showdown pokedex from node subprocess")
    payload = json.loads(result.stdout)
    species_map: dict[str, dict] = {}
    for internal_id, entry in payload.items():
        display_name = entry.get("name") or internal_id
        species_key = slugify(display_name)
        aliases = alias_variants(display_name, internal_id, entry.get("baseSpecies"))
        species_map[species_key] = {
            "speciesKey": species_key,
            "speciesId": internal_id,
            "displayName": display_name,
            "slug": species_key,
            "formName": entry.get("forme"),
            "aliases": aliases,
            "typeNames": entry.get("types") or [],
            "baseStats": normalize_base_stats(entry.get("baseStats")),
            "weightKg": entry.get("weightkg") if isinstance(entry.get("weightkg"), (int, float)) else None,
        }
    return species_map


def heuristic_spread(base_stats: dict) -> dict:
    hp = base_stats["hp"]
    atk = base_stats["atk"]
    defn = base_stats["def"]
    spa = base_stats["spa"]
    spd = base_stats["spd"]
    spe = base_stats["spe"]
    is_physical = atk >= spa
    offensive_score = max(atk, spa) + spe
    defensive_score = hp + max(defn, spd)
    if offensive_score >= defensive_score:
        if is_physical:
            return {"nature": "Adamant", "evs": {"hp": 4, "atk": 252, "def": 0, "spa": 0, "spd": 0, "spe": 252}, "usagePercent": 0.0}
        return {"nature": "Modest", "evs": {"hp": 4, "atk": 0, "def": 0, "spa": 252, "spd": 0, "spe": 252}, "usagePercent": 0.0}
    best_def_physical = defn >= spd
    evs = {"hp": 252, "atk": 0, "def": 0, "spa": 0, "spd": 0, "spe": 4}
    if best_def_physical:
        evs["def"] = 252
        nature = "Bold"
    else:
        evs["spd"] = 252
        nature = "Calm"
    return {"nature": nature, "evs": evs, "usagePercent": 0.0}


def build_heuristic_set(entry: dict) -> dict:
    base_stats = entry.get("baseStats")
    if not base_stats:
        return {}
    return {
        "speciesKey": entry["speciesKey"],
        "speciesId": entry.get("speciesId") or entry["speciesKey"],
        "displayName": entry.get("displayName") or entry["speciesKey"],
        "slug": entry.get("slug") or entry["speciesKey"],
        "source": "heuristic-baseStats",
        "usageRank": 0,
        "usagePercent": 0.0,
        "sampleCount": 0,
        "aliases": entry.get("aliases", []),
        "moves": [],
        "items": [],
        "abilities": [],
        "spreads": [heuristic_spread(base_stats)],
    }


def load_curated_sets(path: Path | None) -> dict[str, dict]:
    if not path or not path.exists():
        return {}
    payload = load_json(path)
    items = payload.get("deltaRanked") if isinstance(payload, dict) else payload
    if not isinstance(items, list):
        items = []
    curated: dict[str, dict] = {}
    for raw in items:
        key_source = raw.get("speciesKey") or raw.get("slug") or raw.get("speciesId") or raw.get("displayName")
        if not key_source:
            continue
        species_key = slugify(key_source)
        display_name = raw.get("displayName") or species_key
        curated[species_key] = {
            "speciesKey": species_key,
            "speciesId": raw.get("speciesId") or species_key,
            "displayName": display_name,
            "slug": raw.get("slug") or species_key,
            "source": raw.get("source") or "delta-curated",
            "usageRank": raw.get("usageRank", 0),
            "usagePercent": raw.get("usagePercent", 0.0),
            "sampleCount": raw.get("sampleCount", 0),
            "aliases": alias_variants(display_name, species_key, *(raw.get("aliases") or [])),
            "moves": normalize_options(raw.get("moves", [])),
            "items": normalize_options(raw.get("items", [])),
            "abilities": normalize_options(raw.get("abilities", [])),
            "spreads": normalize_spreads(raw.get("spreads", [])),
        }
    return curated


def load_learnset_index(path: Path | None) -> dict[str, dict]:
    """Load learnsets.generated.json keyed by speciesKey for legality checks.
    Returns empty dict if the file isn't present (validation becomes a no-op)."""
    if not path or not path.exists():
        return {}
    payload = load_json(path)
    index: dict[str, dict] = {}
    for entry in payload.get("species", []):
        key = entry.get("speciesKey")
        if not key:
            continue
        # Normalize both move and ability ids to compact form so kebab-case curator
        # input ('salt-cure') matches Showdown's compact ids ('saltcure').
        legal_moves = {compact(m.get("id") or m.get("displayName") or "") for m in entry.get("moves", [])}
        legal_moves.discard("")
        legal_abilities = {compact(a) for a in entry.get("abilities", []) if a}
        index[key] = {
            "displayName": entry.get("displayName"),
            "isStub": entry.get("_stub", False),
            "isFinalEvo": entry.get("isFinalEvo", False),
            "moves": legal_moves,
            "abilities": legal_abilities,
        }
    return index


def warn_curated_legality(curated: dict[str, dict], learnset_index: dict[str, dict]) -> int:
    """Warn (non-fatal) when a curated entry picks a move/ability not in the legal list.
    Stub species (no Showdown learnset) are skipped. Returns warning count."""
    if not learnset_index:
        return 0
    warnings = 0
    for species_key, entry in curated.items():
        legal = learnset_index.get(species_key)
        if not legal or legal.get("isStub"):
            continue
        for move in entry.get("moves", []):
            mid = move.get("id") or ""
            normalized = compact(mid) if mid else compact(move.get("displayName") or "")
            if normalized and normalized not in legal["moves"]:
                print(
                    f"WARN curated[{species_key}]: move '{move.get('displayName') or mid}' "
                    f"not in Showdown learnset",
                    file=sys.stderr,
                )
                warnings += 1
        for ability in entry.get("abilities", []):
            aid = ability.get("id") or ""
            normalized = compact(aid) if aid else compact(ability.get("displayName") or "")
            if normalized and normalized not in legal["abilities"]:
                print(
                    f"WARN curated[{species_key}]: ability '{ability.get('displayName') or aid}' "
                    f"not in legal ability list",
                    file=sys.stderr,
                )
                warnings += 1
    return warnings


def load_learnsets_species(path: Path | None) -> dict[str, dict]:
    """Pull species metadata (types, abilities, baseStats, weight) from
    learnsets.generated.json. For custom Delta mons this beats the ranked-scrape
    fallback because team-builder is the authoritative type source."""
    if not path or not path.exists():
        return {}
    payload = load_json(path)
    species_map: dict[str, dict] = {}
    for entry in payload.get("species", []):
        key = entry.get("speciesKey")
        if not key:
            continue
        display_name = entry.get("displayName") or key
        species_map[key] = {
            "speciesKey": key,
            "speciesId": entry.get("speciesId") or key,
            "displayName": display_name,
            "slug": key,
            "formName": entry.get("forme"),
            "aliases": alias_variants(display_name, key, entry.get("speciesId"), entry.get("baseSpecies")),
            "typeNames": list(entry.get("types") or []),
            "baseStats": normalize_base_stats(entry.get("baseStats")),
            "weightKg": entry.get("weightKg") if isinstance(entry.get("weightKg"), (int, float)) else None,
        }
    return species_map


def load_external_species(path: Path | None) -> dict[str, dict]:
    if not path or not path.exists():
        return {}
    payload = load_json(path)
    items = payload.get("species", []) if isinstance(payload, dict) else payload
    species_map = {}
    for raw in items:
        key = raw.get("speciesKey") or raw.get("canonicalKey") or raw.get("slug") or raw.get("id") or raw.get("speciesId") or raw.get("name")
        display_name = raw.get("displayName") or raw.get("name") or key
        if not key or not display_name:
            continue
        species_key = slugify(key)
        species_map[species_key] = {
            "speciesKey": species_key,
            "speciesId": raw.get("speciesId") or species_key,
            "displayName": display_name,
            "slug": raw.get("slug") or species_key,
            "formName": raw.get("formName") or raw.get("forme"),
            "aliases": alias_variants(display_name, key, *(raw.get("aliases") or [])),
            "typeNames": [value for value in (raw.get("types") or [raw.get("primaryType"), raw.get("secondaryType")]) if value],
            "baseStats": normalize_base_stats(raw.get("baseStats") or raw),
        }
    return species_map


def extract_dex_settings(html: str) -> dict | None:
    start = html.find("dexSettings = ")
    if start == -1:
        return None
    end = html.find("</script>", start)
    if end == -1:
        return None
    raw = html[start + len("dexSettings = "):end].strip().rstrip(";")
    return json.loads(raw)


def extract_json_array(html: str, key: str) -> list:
    marker = f'"{key}":['
    start = html.find(marker)
    if start == -1:
        return []
    start = html.find("[", start)
    depth = 0
    in_string = False
    escape = False
    for index in range(start, len(html)):
        char = html[index]
        if in_string:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
            if depth == 0:
                return json.loads(html[start:index + 1])
    return []


SMOGON_GEN_CASCADE = ["sv", "ss", "sm", "xy", "bw", "dp", "rs", "gs", "rb"]


def extract_strategies(html: str) -> list[dict]:
    """Pull the strategies array from the Smogon dex page's dump-pokemon RPC response."""
    settings = extract_dex_settings(html)
    if not settings:
        return []
    for rpc_request, rpc_response in settings.get("injectRpcs", []):
        if '"dump-pokemon"' in rpc_request:
            return rpc_response.get("strategies") or []
    return []


def shape_smogon_set(species_key: str, display_name: str, moveset: dict, source_label: str) -> dict:
    moves = []
    for slot in moveset.get("moveslots", [])[:4]:
        if not slot:
            continue
        move_name = slot[0].get("move")
        if not move_name:
            continue
        moves.append({
            "id": slugify(move_name),
            "displayName": move_name,
            "type": None,
            "usagePercent": 100.0,
        })

    items = [
        {"id": slugify(item), "displayName": item, "type": "item", "usagePercent": 100.0}
        for item in (moveset.get("items") or [])[:1]
    ]
    abilities = [
        {"id": slugify(ability), "displayName": ability, "type": "ability", "usagePercent": 100.0}
        for ability in (moveset.get("abilities") or [])[:1]
    ]
    spreads = []
    evconfig = (moveset.get("evconfigs") or [None])[0]
    nature = (moveset.get("natures") or [None])[0]
    if evconfig and nature:
        spreads.append({
            "nature": nature,
            "evs": {
                "hp": int(evconfig.get("hp", 0)),
                "atk": int(evconfig.get("atk", 0)),
                "def": int(evconfig.get("def", 0)),
                "spa": int(evconfig.get("spa", 0)),
                "spd": int(evconfig.get("spd", 0)),
                "spe": int(evconfig.get("spe", 0)),
            },
            "usagePercent": 100.0,
        })

    return {
        "speciesKey": species_key,
        "speciesId": species_key,
        "displayName": display_name,
        "slug": species_key,
        "source": source_label,
        "usageRank": 0,
        "usagePercent": 0.0,
        "sampleCount": 0,
        "aliases": alias_variants(display_name, species_key),
        "moves": moves,
        "items": items,
        "abilities": abilities,
        "spreads": spreads,
    }


def fetch_smogon_gen(species_key: str, gen: str, target_format: str = "OU") -> dict | None:
    """Fetch the {target_format} moveset for a species at the given gen.
    Returns None if the page 404s or the format isn't present."""
    url = f"https://www.smogon.com/dex/{gen}/pokemon/{species_key}/"
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            html = response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise

    strategies = extract_strategies(html)
    target = next((s for s in strategies if s.get("format") == target_format), None)
    if not target or not target.get("movesets"):
        return None
    moveset = target["movesets"][0]
    settings = extract_dex_settings(html)
    dump = None
    if settings:
        for rpc_request, rpc_response in settings.get("injectRpcs", []):
            if '"dump-pokemon"' in rpc_request:
                dump = rpc_response
                break
    display_name = (dump or {}).get("pokemon", {}).get("name") or moveset.get("pokemon") or species_key
    if compact(display_name) != compact(species_key):
        return None
    return shape_smogon_set(species_key, display_name, moveset, f"smogon-{target_format.lower()}-{gen}")


def fetch_smogon_ou_cascade(species_key: str, gens: list[str]) -> dict | None:
    """Try each gen in order, return the first OU set found."""
    for gen in gens:
        try:
            entry = fetch_smogon_gen(species_key, gen, "OU")
        except Exception:
            continue
        if entry is not None:
            return entry
    return None


def fetch_smogon_fallback(
    species_keys: list[str],
    workers: int,
    gens: list[str],
    final_evo_keys: set[str] | None = None,
) -> tuple[dict[str, dict], list[str]]:
    fallback_map: dict[str, dict] = {}
    missing: list[str] = []
    if final_evo_keys is not None:
        candidates = [k for k in species_keys if k in final_evo_keys]
    else:
        candidates = species_keys

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        future_map = {pool.submit(fetch_smogon_ou_cascade, key, gens): key for key in candidates}
        for future in concurrent.futures.as_completed(future_map):
            key = future_map[future]
            try:
                entry = future.result()
            except Exception:
                missing.append(key)
                continue
            if entry is None:
                missing.append(key)
            else:
                fallback_map[key] = entry
    return fallback_map, sorted(missing)


def realign_keys(input_map: dict[str, dict], canonical_by_compact: dict[str, str]) -> dict[str, dict]:
    """Rewrite each entry's speciesKey to the canonical kebab-case form when the
    compact-form keys match. Eliminates ranked-vs-team-builder slug collisions
    like 'ironblaster' (ranked) vs 'iron-blaster' (team-builder)."""
    if not canonical_by_compact:
        return input_map
    out: dict[str, dict] = {}
    for key, entry in input_map.items():
        canonical = canonical_by_compact.get(compact(key), key)
        rewritten = dict(entry)
        rewritten["speciesKey"] = canonical
        if "slug" in rewritten:
            rewritten["slug"] = rewritten.get("slug") or canonical
        existing = out.get(canonical)
        if existing is None:
            out[canonical] = rewritten
            continue
        # When two pre-realign keys collide, prefer the richer display name
        # (one containing a space or hyphen — the team-builder formatting)
        # and union aliases.
        existing_name = existing.get("displayName") or ""
        new_name = rewritten.get("displayName") or ""
        if (" " in new_name or "-" in new_name) and not (" " in existing_name or "-" in existing_name):
            existing["displayName"] = new_name
        existing_aliases = set(existing.get("aliases") or [])
        existing_aliases.update(rewritten.get("aliases") or [])
        existing["aliases"] = sorted(existing_aliases)
        # Fill any null fields from the incoming entry.
        for field in ("speciesId", "formName", "typeNames", "baseStats", "weightKg",
                      "moves", "items", "abilities", "spreads",
                      "source", "usageRank", "usagePercent", "sampleCount"):
            if not existing.get(field) and rewritten.get(field):
                existing[field] = rewritten[field]
    return out


def merge_species_maps(*maps: dict[str, dict]) -> dict[str, dict]:
    merged: dict[str, dict] = {}
    for source_map in maps:
        for key, entry in source_map.items():
            existing = merged.get(key)
            if existing is None:
                merged[key] = entry
                continue
            aliases = sorted(set(existing.get("aliases", [])) | set(entry.get("aliases", [])))
            for field in ["speciesId", "displayName", "slug", "formName", "typeNames", "baseStats", "weightKg"]:
                if not existing.get(field) and entry.get(field):
                    existing[field] = entry[field]
            existing["aliases"] = aliases
    return merged


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the DeltaCalc runtime battle database with Smogon fallback.")
    parser.add_argument("--ranked", nargs="*", default=RANKED_DEFAULTS)
    parser.add_argument("--output", default="src/main/resources/data/deltacalc/database/battle-database.generated.json")
    parser.add_argument("--smogon-output", default="src/main/resources/data/deltacalc/database/smogon-fallback.generated.json")
    parser.add_argument("--report", default="build/reports/deltacalc/battle-database-report.json")
    parser.add_argument("--species-json", default="")
    parser.add_argument("--showdown-pokedex", default="")
    parser.add_argument("--curated-sets", default="src/main/resources/data/deltacalc/usage/delta-curated-sets.json",
                        help="Optional JSON of hand-authored Delta sets; highest priority, overrides ranked for the same species.")
    parser.add_argument("--auto-curated-sets", default="tools/database/generated/delta-auto-sets.generated.json",
                        help="Optional JSON of auto-generated Delta defaults (build_default_delta_sets.py). "
                             "Priority: manual curated > auto > ranked > Smogon > heuristic.")
    parser.add_argument("--learnsets", default="tools/database/generated/learnsets.generated.json",
                        help="Optional learnset index for curated-set legality checks; produced by build_learnsets.py.")
    parser.add_argument("--skip-smogon", action="store_true")
    parser.add_argument("--skip-heuristics", action="store_true",
                        help="Skip base-stat-derived spreads for species without a ranked or Smogon set.")
    parser.add_argument("--smogon-gens", default=",".join(SMOGON_GEN_CASCADE),
                        help="Comma-separated gen cascade for Smogon OU lookup (first hit wins).")
    parser.add_argument("--smogon-include-non-final", action="store_true",
                        help="Scrape Smogon OU even for non-final-evo species (default: final evos only).")
    parser.add_argument("--workers", type=int, default=8)
    args = parser.parse_args()

    ranked_paths = [Path(path) for path in args.ranked if Path(path).exists()]
    if not ranked_paths:
        print("No ranked datasets found.", file=sys.stderr)
        return 1

    ranked_species, ranked_sets = load_ranked_entries(ranked_paths)
    showdown_pokedex = Path(args.showdown_pokedex) if args.showdown_pokedex else detect_showdown_pokedex()
    showdown_species = load_showdown_species(showdown_pokedex) if showdown_pokedex else {}
    external_species = load_external_species(Path(args.species_json)) if args.species_json else {}
    learnsets_species = load_learnsets_species(Path(args.learnsets)) if args.learnsets else {}
    # Realign ranked entries' species keys to the canonical kebab-case form so
    # 'ironblaster' (ranked compact slug) merges with 'iron-blaster' (team-builder).
    canonical_by_compact = {compact(k): k for k in learnsets_species.keys()}
    ranked_species = realign_keys(ranked_species, canonical_by_compact)
    ranked_sets = realign_keys(ranked_sets, canonical_by_compact)
    # Priority: learnsets (Delta team-builder + Showdown merged, authoritative for Delta types)
    # > Showdown direct > ranked-scrape > external override.
    species_map = merge_species_maps(learnsets_species, showdown_species, ranked_species, external_species)

    curated_sets = load_curated_sets(Path(args.curated_sets)) if args.curated_sets else {}
    auto_curated_sets = load_curated_sets(Path(args.auto_curated_sets)) if args.auto_curated_sets else {}
    learnset_index = load_learnset_index(Path(args.learnsets)) if args.learnsets else {}
    legality_warnings = warn_curated_legality(curated_sets, learnset_index)
    # Priority: manual curated > auto-curated > ranked. Apply lowest first, highest last.
    merged_delta_sets = dict(ranked_sets)
    for key, entry in auto_curated_sets.items():
        merged_delta_sets[key] = entry
    for key, entry in curated_sets.items():
        merged_delta_sets[key] = entry

    smogon_map: dict[str, dict] = {}
    missing_smogon: list[str] = []
    if not args.skip_smogon:
        candidates = sorted(key for key in species_map.keys() if key not in merged_delta_sets)
        gens = [g.strip() for g in args.smogon_gens.split(",") if g.strip()]
        final_evo_filter = None
        if learnset_index and not args.smogon_include_non_final:
            final_evo_filter = {
                key for key, info in learnset_index.items()
                if info.get("isFinalEvo") and not info.get("isStub")
            }
        smogon_map, missing_smogon = fetch_smogon_fallback(
            candidates, args.workers, gens, final_evo_filter,
        )

    heuristic_count = 0
    if not args.skip_heuristics:
        covered = set(merged_delta_sets.keys()) | set(smogon_map.keys())
        for key, entry in species_map.items():
            if key in covered:
                continue
            heuristic = build_heuristic_set(entry)
            if not heuristic:
                continue
            smogon_map[key] = heuristic
            heuristic_count += 1

    dataset = {
        "sourceMeta": {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "rankedSources": ",".join(path.name for path in ranked_paths),
            "showdownPokedex": str(showdown_pokedex) if showdown_pokedex else "",
            "externalSpeciesJson": args.species_json,
            "curatedSetsJson": args.curated_sets if curated_sets else "",
            "curatedSetCount": str(len(curated_sets)),
            "heuristicSetCount": str(heuristic_count),
            "smogonEnabled": str(not args.skip_smogon).lower(),
        },
        "species": sorted(species_map.values(), key=lambda entry: entry["speciesKey"]),
        "deltaRanked": sorted(merged_delta_sets.values(), key=lambda entry: entry["speciesKey"]),
        "smogonFallback": sorted(smogon_map.values(), key=lambda entry: entry["speciesKey"]),
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(dataset, indent=2), encoding="utf-8")

    smogon_output = Path(args.smogon_output)
    smogon_output.parent.mkdir(parents=True, exist_ok=True)
    smogon_output.write_text(json.dumps({"source": "smogon-fallback", "species": dataset["smogonFallback"]}, indent=2), encoding="utf-8")

    report = {
        "speciesCount": len(dataset["species"]),
        "deltaRankedCount": len(dataset["deltaRanked"]),
        "curatedSetCount": len(curated_sets),
        "heuristicSetCount": heuristic_count,
        "smogonFallbackCount": len(dataset["smogonFallback"]),
        "missingSmogonFallback": missing_smogon[:1000],
    }
    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(
        f"Wrote {len(dataset['species'])} species, "
        f"{len(dataset['deltaRanked'])} Delta ranked/curated defaults "
        f"({len(curated_sets)} manual curated, {len(auto_curated_sets)} auto-curated), "
        f"and {len(dataset['smogonFallback'])} fallback defaults "
        f"({heuristic_count} heuristic). "
        f"Legality warnings: {legality_warnings}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
