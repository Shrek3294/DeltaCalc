#!/usr/bin/env python3
import argparse
import datetime as dt
import json
import re
import subprocess
import sys
from pathlib import Path


def detect_showdown_moves() -> Path | None:
    path = Path.home() / "AppData" / "Roaming" / "ModrinthApp" / "profiles" / "Cobblemon Delta" / "showdown" / "data" / "moves.js"
    return path if path.exists() else None


def slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.lower())


def load_showdown_moves(moves_path: Path) -> list[dict]:
    node_script = f"const moves = require({json.dumps(str(moves_path))}).Moves; console.log(JSON.stringify(moves));"
    result = subprocess.run(
        ["node", "-e", node_script],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    payload = json.loads(result.stdout)
    entries: list[dict] = []
    for internal_id, entry in payload.items():
        name = entry.get("name") or internal_id
        flags = entry.get("flags") or {}
        secondary = entry.get("secondary")
        has_secondary = bool(secondary) or bool(entry.get("secondaries"))
        recoil = entry.get("recoil")
        self_effect = entry.get("self") or {}
        category = entry.get("category") or ""
        entries.append({
            "id": slugify(internal_id),
            "displayName": name,
            "type": (entry.get("type") or "").lower(),
            "category": category.lower(),
            "basePower": int(entry.get("basePower") or 0),
            "accuracy": entry.get("accuracy") if isinstance(entry.get("accuracy"), int) else 0,
            "priority": int(entry.get("priority") or 0),
            "flags": sorted(k for k, v in flags.items() if v),
            "hasSecondary": has_secondary,
            "isRecoil": bool(recoil),
            "isSelfStatDrop": bool(self_effect.get("boosts") and any((v or 0) < 0 for v in self_effect["boosts"].values())),
            "multihitMin": extract_multihit(entry)[0],
            "multihitMax": extract_multihit(entry)[1],
        })
    entries.sort(key=lambda e: e["id"])
    return entries


def extract_multihit(entry: dict) -> tuple[int, int]:
    mh = entry.get("multihit")
    if mh is None:
        return 1, 1
    if isinstance(mh, int):
        return mh, mh
    if isinstance(mh, list) and len(mh) >= 2:
        return int(mh[0]), int(mh[1])
    return 1, 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate move flag database from Showdown moves.js")
    parser.add_argument("--moves-js", default="")
    parser.add_argument("--output", default="src/main/resources/data/deltacalc/database/move-flags.generated.json")
    args = parser.parse_args()

    moves_path = Path(args.moves_js) if args.moves_js else detect_showdown_moves()
    if not moves_path or not moves_path.exists():
        print(f"Showdown moves.js not found at {moves_path}", file=sys.stderr)
        return 1

    moves = load_showdown_moves(moves_path)

    dataset = {
        "sourceMeta": {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "showdownMoves": str(moves_path),
            "moveCount": str(len(moves)),
        },
        "moves": moves,
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(dataset, indent=2), encoding="utf-8")
    print(f"Wrote {len(moves)} move entries to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
