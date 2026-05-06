#!/usr/bin/env python3
"""Generate a heuristic default battle set (4 moves + ability + item + spread)
for every Delta final evo that isn't already covered by ranked usage or a
hand-curated entry.

Output: tools/database/generated/delta-auto-sets.generated.json
Schema: same as delta-curated-sets.json (consumed by build_battle_database.py
via --auto-curated-sets).
"""
import argparse
import datetime as dt
import json
import sys
from pathlib import Path

from _common import alias_variants, load_json, slugify


LEARNSETS_DEFAULT = "tools/database/generated/learnsets.generated.json"
CURATED_DEFAULT = "src/main/resources/data/deltacalc/usage/delta-curated-sets.json"
RANKED_DEFAULT_GLOBS = [
    "src/main/resources/data/deltacalc/usage/season-6-mid-1300.generated.json",
    "src/main/resources/data/deltacalc/usage/season-6-mid-1000.generated.json",
    "src/main/resources/data/deltacalc/usage/season-6-mid-1500.generated.json",
]
TEAM_BUILDER_DEFAULT = "cobblemon-delta-pokemon-team-builder.json"
OUTPUT_DEFAULT = "tools/database/generated/delta-auto-sets.generated.json"


# Utility-move priority lists, split by attacking category so a special
# attacker doesn't inherit Swords Dance.
UTILITY_PRIORITY_PHYSICAL = [
    "swordsdance", "dragondance", "bulkup", "shellsmash",
    "recover", "roost", "softboiled", "morningsun", "synthesis", "milkdrink", "rest",
    "willowisp", "thunderwave", "toxic", "leechseed", "spore",
    "stealthrock", "spikes", "toxicspikes", "stickyweb",
    "protect", "substitute", "taunt",
]
UTILITY_PRIORITY_SPECIAL = [
    "nastyplot", "calmmind", "quiverdance", "shellsmash", "tailglow",
    "recover", "roost", "softboiled", "morningsun", "synthesis", "milkdrink", "rest",
    "willowisp", "thunderwave", "toxic", "leechseed", "spore",
    "stealthrock", "spikes", "toxicspikes", "stickyweb",
    "protect", "substitute", "taunt",
]
UTILITY_PRIORITY_DEFENSIVE = [
    "recover", "roost", "softboiled", "morningsun", "synthesis", "milkdrink", "rest",
    "willowisp", "thunderwave", "toxic", "leechseed", "spore",
    "stealthrock", "spikes", "toxicspikes", "stickyweb",
    "protect", "substitute", "taunt", "irondefense", "calmmind",
]

# Moves we never want to pick automatically (recharge turn, low PP, situational).
MOVE_BLOCKLIST = {
    "hyperbeam", "gigaimpact", "frenzyplant", "blastburn", "hydrocannon",
    "explosion", "selfdestruct", "memento", "finalgambit",
    "skyattack", "solarbeam", "solarblade", "skullbash", "razorwind",
    "selfdestruct", "explosion",
}


def heuristic_spread(stats: dict) -> dict:
    """Mirrors build_battle_database.heuristic_spread."""
    if not stats:
        return {"nature": "Hardy", "evs": {"hp": 0, "atk": 0, "def": 0, "spa": 0, "spd": 0, "spe": 0}, "usagePercent": 100.0}
    hp = stats.get("hp", 0)
    atk = stats.get("atk", 0)
    defn = stats.get("def", 0)
    spa = stats.get("spa", 0)
    spd = stats.get("spd", 0)
    spe = stats.get("spe", 0)
    is_physical = atk >= spa
    offensive_score = max(atk, spa) + spe
    defensive_score = hp + max(defn, spd)
    if offensive_score >= defensive_score:
        if is_physical:
            return {"nature": "Adamant", "evs": {"hp": 4, "atk": 252, "def": 0, "spa": 0, "spd": 0, "spe": 252}, "usagePercent": 100.0}
        return {"nature": "Modest", "evs": {"hp": 4, "atk": 0, "def": 0, "spa": 252, "spd": 0, "spe": 252}, "usagePercent": 100.0}
    best_def_physical = defn >= spd
    evs = {"hp": 252, "atk": 0, "def": 0, "spa": 0, "spd": 0, "spe": 4}
    if best_def_physical:
        evs["def"] = 252
        nature = "Bold"
    else:
        evs["spd"] = 252
        nature = "Calm"
    return {"nature": nature, "evs": evs, "usagePercent": 100.0}


def classify_role(stats: dict) -> tuple[str, str]:
    """Returns (role, attack_category). role in {'offensive', 'defensive'}."""
    if not stats:
        return "offensive", "physical"
    atk = stats.get("atk", 0)
    spa = stats.get("spa", 0)
    spe = stats.get("spe", 0)
    hp = stats.get("hp", 0)
    defn = stats.get("def", 0)
    spd = stats.get("spd", 0)
    is_physical = atk >= spa
    offensive_score = max(atk, spa) + spe
    defensive_score = hp + max(defn, spd)
    role = "offensive" if offensive_score >= defensive_score else "defensive"
    return role, "physical" if is_physical else "special"


def pick_moves(species: dict, role: str, attack_cat: str) -> list[dict]:
    """Return up to 4 move dicts in delta-curated-sets.json shape."""
    types_lower = {t.lower() for t in (species.get("types") or [])}
    movepool = [m for m in (species.get("moves") or []) if m.get("id") not in MOVE_BLOCKLIST]

    def shape(move: dict) -> dict:
        return {
            "id": move["id"],
            "displayName": move.get("displayName") or move["id"],
            "type": move.get("type"),
            "usagePercent": 100.0,
        }

    chosen: list[dict] = []
    chosen_ids: set[str] = set()
    chosen_move_types: set[str] = set()

    def add(move: dict) -> None:
        if move["id"] in chosen_ids:
            return
        chosen.append(shape(move))
        chosen_ids.add(move["id"])
        if move.get("type"):
            chosen_move_types.add(move["type"].lower())

    # 1. Up to 2 STAB attacks in dominant category, sorted by BP desc.
    stab_attacks = sorted(
        [m for m in movepool
         if (m.get("category") or "").lower() == attack_cat
         and (m.get("type") or "").lower() in types_lower
         and (m.get("basePower") or 0) >= 60],
        key=lambda m: -(m.get("basePower") or 0),
    )
    for m in stab_attacks[:2]:
        add(m)

    # 2. Best coverage move in dominant category, different type.
    coverage = sorted(
        [m for m in movepool
         if (m.get("category") or "").lower() == attack_cat
         and (m.get("type") or "").lower() not in chosen_move_types
         and (m.get("basePower") or 0) >= 70],
        key=lambda m: -(m.get("basePower") or 0),
    )
    if coverage and len(chosen) < 4:
        add(coverage[0])

    # 3. One status / utility move from priority list (category-aware).
    if len(chosen) < 4:
        statuses = [m for m in movepool if (m.get("category") or "").lower() == "status"]
        status_by_id = {m["id"]: m for m in statuses}
        if role == "defensive":
            priority = UTILITY_PRIORITY_DEFENSIVE
        elif attack_cat == "physical":
            priority = UTILITY_PRIORITY_PHYSICAL
        else:
            priority = UTILITY_PRIORITY_SPECIAL
        for util_id in priority:
            move = status_by_id.get(util_id)
            if move:
                add(move)
                break

    # 4. Backfill with next-best attacks (any category) until 4 moves.
    fillers = sorted(
        [m for m in movepool
         if m["id"] not in chosen_ids
         and (m.get("basePower") or 0) >= 60],
        key=lambda m: -(m.get("basePower") or 0),
    )
    for m in fillers:
        if len(chosen) >= 4:
            break
        add(m)

    return chosen[:4]


def pick_ability(species: dict) -> dict | None:
    abilities = species.get("abilities") or []
    if not abilities:
        return None
    chosen = abilities[0]
    return {
        "id": slugify(chosen),
        "displayName": chosen,
        "type": "ability",
        "usagePercent": 100.0,
    }


def pick_item(role: str, attack_cat: str, has_status_move: bool) -> dict:
    if role == "offensive":
        if not has_status_move:
            name = "Choice Band" if attack_cat == "physical" else "Choice Specs"
        else:
            name = "Life Orb"
    else:
        name = "Leftovers"
    return {
        "id": slugify(name),
        "displayName": name,
        "type": "item",
        "usagePercent": 100.0,
    }


def build_default_set(species: dict) -> dict | None:
    if not species.get("moves"):
        return None
    stats = species.get("baseStats") or {}
    role, attack_cat = classify_role(stats)
    moves = pick_moves(species, role, attack_cat)
    if not moves:
        return None
    has_status = any(((m.get("type") or "").lower() == "" or
                      next((sm for sm in species["moves"] if sm["id"] == m["id"] and (sm.get("category") or "").lower() == "status"), None))
                     for m in moves)
    ability = pick_ability(species)
    abilities_list = [ability] if ability else []
    spread = heuristic_spread(stats)
    item = pick_item(role, attack_cat, has_status)
    display_name = species["displayName"]
    species_key = species["speciesKey"]
    return {
        "speciesKey": species_key,
        "displayName": display_name,
        "speciesId": species.get("speciesId") or species_key,
        "source": "delta-auto",
        "aliases": alias_variants(display_name, species_key),
        "abilities": abilities_list,
        "items": [item],
        "moves": moves,
        "spreads": [spread],
    }


def collect_ranked_keys(paths: list[Path]) -> set[str]:
    keys = set()
    for path in paths:
        if not path.exists():
            continue
        payload = load_json(path)
        for entry in payload.get("species", []):
            slug = entry.get("slug") or entry.get("speciesId") or entry.get("displayName")
            if slug:
                keys.add(slugify(slug))
    return keys


def collect_curated_keys(path: Path) -> set[str]:
    if not path.exists():
        return set()
    payload = load_json(path)
    items = payload.get("deltaRanked", []) if isinstance(payload, dict) else []
    keys = set()
    for raw in items:
        key_source = raw.get("speciesKey") or raw.get("slug") or raw.get("speciesId") or raw.get("displayName")
        if key_source:
            keys.add(slugify(key_source))
    return keys


def is_delta_mon(record: dict, team_builder_keys: set[str]) -> bool:
    """A 'Delta mon' is a custom Cobblemon Delta species — present in team-builder
    or named with a known custom suffix. Filters out vanilla finals."""
    if record["speciesKey"] in team_builder_keys:
        return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate heuristic default sets for uncovered Delta mons.")
    parser.add_argument("--learnsets", default=LEARNSETS_DEFAULT)
    parser.add_argument("--curated", default=CURATED_DEFAULT)
    parser.add_argument("--ranked", nargs="*", default=RANKED_DEFAULT_GLOBS)
    parser.add_argument("--team-builder", default=TEAM_BUILDER_DEFAULT)
    parser.add_argument("--output", default=OUTPUT_DEFAULT)
    parser.add_argument("--include-non-delta", action="store_true",
                        help="Generate defaults for ALL uncovered final evos, not just Delta mons.")
    args = parser.parse_args()

    learnsets_path = Path(args.learnsets)
    if not learnsets_path.exists():
        print(f"Learnsets file missing: {learnsets_path}. Run build_learnsets.py first.", file=sys.stderr)
        return 1
    learnsets = load_json(learnsets_path)

    team_builder_keys: set[str] = set()
    tb_path = Path(args.team_builder)
    if tb_path.exists():
        for entry in load_json(tb_path).get("entries", []):
            name = entry.get("name")
            if name:
                team_builder_keys.add(slugify(name))

    ranked_keys = collect_ranked_keys([Path(p) for p in args.ranked])
    curated_keys = collect_curated_keys(Path(args.curated))
    skip_keys = ranked_keys | curated_keys

    generated: list[dict] = []
    skipped_no_movepool: list[str] = []
    skipped_already_covered = 0
    for species in learnsets.get("species", []):
        if not species.get("isFinalEvo"):
            continue
        if not args.include_non_delta and not is_delta_mon(species, team_builder_keys):
            continue
        key = species["speciesKey"]
        if key in skip_keys:
            skipped_already_covered += 1
            continue
        if not species.get("moves"):
            skipped_no_movepool.append(species["displayName"])
            continue
        default = build_default_set(species)
        if default:
            generated.append(default)

    generated.sort(key=lambda r: r["speciesKey"])

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "_schema": {
            "description": "Auto-generated heuristic Delta sets. Lower priority than delta-curated-sets.json. Regenerated by build_default_delta_sets.py.",
            "howToApply": "Pass --auto-curated-sets to build_battle_database.py.",
        },
        "sourceMeta": {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "generatedCount": len(generated),
            "skippedAlreadyCovered": skipped_already_covered,
            "skippedNoMovepool": len(skipped_no_movepool),
        },
        "deltaRanked": generated,
    }
    output_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    print(f"Generated {len(generated)} default Delta sets -> {output_path}")
    print(f"  skipped already covered (ranked or curated): {skipped_already_covered}")
    print(f"  skipped no movepool (stubs): {len(skipped_no_movepool)}")
    if skipped_no_movepool:
        print(f"  no-movepool samples: {skipped_no_movepool[:6]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
