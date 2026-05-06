#!/usr/bin/env python3
"""Report curated-set coverage vs. the final-evo target list.

Reads delta-curated-sets.json and learnsets.generated.json and prints:
  - X / Y final evos curated
  - Breakdown by tier (custom Delta vs. standard, stub vs. movepool)
  - List of un-curated finals so the user knows what to pick next
"""
import argparse
import sys
from pathlib import Path

from _common import load_json, slugify


CURATED_DEFAULT = "src/main/resources/data/deltacalc/usage/delta-curated-sets.json"
LEARNSETS_DEFAULT = "tools/database/generated/learnsets.generated.json"


def main() -> int:
    parser = argparse.ArgumentParser(description="Show curated-set coverage vs. final-evo targets.")
    parser.add_argument("--curated", default=CURATED_DEFAULT)
    parser.add_argument("--learnsets", default=LEARNSETS_DEFAULT)
    parser.add_argument("--list-missing", action="store_true",
                        help="Print every uncurated final evo, not just the count.")
    parser.add_argument("--limit", type=int, default=40,
                        help="Max items to show in any preview list. Use --list-missing to see all.")
    args = parser.parse_args()

    learnsets_path = Path(args.learnsets)
    if not learnsets_path.exists():
        print(f"Learnsets file missing: {learnsets_path}. Run build_learnsets.py first.", file=sys.stderr)
        return 1
    learnsets = load_json(learnsets_path)

    finals_with_movepool: set[str] = set()
    finals_stub: set[str] = set()
    display_by_key: dict[str, str] = {}
    for entry in learnsets.get("species", []):
        if not entry.get("isFinalEvo"):
            continue
        key = entry["speciesKey"]
        display_by_key[key] = entry.get("displayName") or key
        if entry.get("_stub"):
            finals_stub.add(key)
        else:
            finals_with_movepool.add(key)

    finals_total = finals_with_movepool | finals_stub

    curated_path = Path(args.curated)
    curated_keys: set[str] = set()
    if curated_path.exists():
        payload = load_json(curated_path)
        for raw in payload.get("deltaRanked", []) if isinstance(payload, dict) else []:
            key_source = raw.get("speciesKey") or raw.get("slug") or raw.get("speciesId") or raw.get("displayName")
            if key_source:
                curated_keys.add(slugify(key_source))

    curated_in_target = curated_keys & finals_total
    curated_outside_target = curated_keys - finals_total
    missing = finals_total - curated_keys

    missing_movepool = sorted(missing & finals_with_movepool, key=lambda k: display_by_key[k])
    missing_stub = sorted(missing & finals_stub, key=lambda k: display_by_key[k])

    print(f"Final evos total:               {len(finals_total)}")
    print(f"  with Showdown movepool:       {len(finals_with_movepool)}")
    print(f"  stubs (custom Delta only):    {len(finals_stub)}")
    print(f"Curated entries:                {len(curated_keys)}")
    print(f"  matching a final evo target:  {len(curated_in_target)}")
    print(f"  outside target list:          {len(curated_outside_target)}")
    print(f"Coverage:                       {len(curated_in_target)} / {len(finals_total)} "
          f"({(len(curated_in_target) / len(finals_total) * 100) if finals_total else 0:.1f}%)")
    print()
    print(f"Missing — with movepool ({len(missing_movepool)}):")
    preview = missing_movepool if args.list_missing else missing_movepool[: args.limit]
    for key in preview:
        print(f"  {display_by_key[key]:30s} ({key})")
    if not args.list_missing and len(missing_movepool) > args.limit:
        print(f"  ... and {len(missing_movepool) - args.limit} more (use --list-missing)")
    print()
    print(f"Missing — stub (no movepool source) ({len(missing_stub)}):")
    preview = missing_stub if args.list_missing else missing_stub[: args.limit]
    for key in preview:
        print(f"  {display_by_key[key]:30s} ({key})")
    if not args.list_missing and len(missing_stub) > args.limit:
        print(f"  ... and {len(missing_stub) - args.limit} more (use --list-missing)")

    if curated_outside_target:
        print()
        print(f"Curated but not in final-evo target list ({len(curated_outside_target)}):")
        for key in sorted(curated_outside_target):
            print(f"  {key}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
