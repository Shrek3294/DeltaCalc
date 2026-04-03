#!/usr/bin/env python3
import argparse
import concurrent.futures
import datetime as dt
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path


RANKED_DEFAULTS = [
    "src/main/resources/data/deltacalc/usage/season-6-mid-1300.generated.json",
    "src/main/resources/data/deltacalc/usage/season-6-mid-1000.generated.json",
    "src/main/resources/data/deltacalc/usage/season-6-mid-1500.generated.json",
]


def slugify(value: str) -> str:
    return value.strip().lower().replace(" ", "-").replace("_", "-")


def compact(value: str) -> str:
    return re.sub(r"[-_ '.]", "", slugify(value))


def alias_variants(*values: str | None) -> list[str]:
    results: list[str] = []
    seen = set()
    for value in values:
        if not value:
            continue
        for variant in [value.strip(), slugify(value), compact(value), value.strip().replace("-", " ")]:
            if variant and variant not in seen:
                seen.add(variant)
                results.append(variant)
    return results


def detect_showdown_pokedex() -> Path | None:
    path = Path.home() / "AppData" / "Roaming" / "ModrinthApp" / "profiles" / "Cobblemon Delta" / "showdown" / "data" / "pokedex.js"
    return path if path.exists() else None


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


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
                "typeNames": entry.get("types") or [],
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


def parse_smogon_page(species_key: str) -> dict | None:
    url = f"https://www.smogon.com/dex/sv/pokemon/{species_key}/national-dex/"
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            html = response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise

    settings = extract_dex_settings(html)
    dump = None
    if settings:
        for rpc_request, rpc_response in settings.get("injectRpcs", []):
            if '"dump-pokemon"' in rpc_request:
                dump = rpc_response
                break

    movesets = extract_json_array(html, "movesets")
    if not movesets:
        return None
    moveset = movesets[0]
    display_name = (dump or {}).get("pokemon", {}).get("name") or moveset.get("pokemon") or species_key
    if compact(display_name) != compact(species_key):
        return None

    moves = []
    for slot in moveset.get("moveslots", [])[:4]:
        if not slot:
            continue
        move_name = slot[0].get("move")
        if not move_name:
            continue
        moves.append(
            {
                "id": slugify(move_name),
                "displayName": move_name,
                "type": None,
                "usagePercent": 100.0,
            }
        )

    items = [
        {
            "id": slugify(item),
            "displayName": item,
            "type": "item",
            "usagePercent": 100.0,
        }
        for item in (moveset.get("items") or [])[:1]
    ]
    abilities = [
        {
            "id": slugify(ability),
            "displayName": ability,
            "type": "ability",
            "usagePercent": 100.0,
        }
        for ability in (moveset.get("abilities") or [])[:1]
    ]

    spreads = []
    evconfig = (moveset.get("evconfigs") or [None])[0]
    nature = (moveset.get("natures") or [None])[0]
    if evconfig and nature:
        spreads.append(
            {
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
            }
        )

    return {
        "speciesKey": species_key,
        "speciesId": species_key,
        "displayName": display_name,
        "slug": species_key,
        "source": "smogon-fallback",
        "usageRank": 0,
        "usagePercent": 0.0,
        "sampleCount": 0,
        "aliases": alias_variants(display_name, species_key),
        "moves": moves,
        "items": items,
        "abilities": abilities,
        "spreads": spreads,
    }


def fetch_smogon_fallback(species_keys: list[str], workers: int) -> tuple[dict[str, dict], list[str]]:
    fallback_map: dict[str, dict] = {}
    missing: list[str] = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        future_map = {pool.submit(parse_smogon_page, key): key for key in species_keys}
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


def merge_species_maps(*maps: dict[str, dict]) -> dict[str, dict]:
    merged: dict[str, dict] = {}
    for source_map in maps:
        for key, entry in source_map.items():
            existing = merged.get(key)
            if existing is None:
                merged[key] = entry
                continue
            aliases = sorted(set(existing.get("aliases", [])) | set(entry.get("aliases", [])))
            for field in ["speciesId", "displayName", "slug", "formName", "typeNames", "baseStats"]:
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
    parser.add_argument("--skip-smogon", action="store_true")
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
    species_map = merge_species_maps(showdown_species, ranked_species, external_species)

    smogon_map: dict[str, dict] = {}
    missing_smogon: list[str] = []
    if not args.skip_smogon:
        candidates = sorted(key for key in species_map.keys() if key not in ranked_sets)
        smogon_map, missing_smogon = fetch_smogon_fallback(candidates, args.workers)

    dataset = {
        "sourceMeta": {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "rankedSources": ",".join(path.name for path in ranked_paths),
            "showdownPokedex": str(showdown_pokedex) if showdown_pokedex else "",
            "externalSpeciesJson": args.species_json,
            "smogonEnabled": str(not args.skip_smogon).lower(),
        },
        "species": sorted(species_map.values(), key=lambda entry: entry["speciesKey"]),
        "deltaRanked": sorted(ranked_sets.values(), key=lambda entry: entry["speciesKey"]),
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
        "smogonFallbackCount": len(dataset["smogonFallback"]),
        "missingSmogonFallback": missing_smogon[:1000],
    }
    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(
        f"Wrote {len(dataset['species'])} species, "
        f"{len(dataset['deltaRanked'])} Delta ranked defaults, "
        f"and {len(dataset['smogonFallback'])} Smogon fallback defaults."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
