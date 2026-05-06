"""Shared helpers for DeltaCalc database build scripts."""
import json
import re
from pathlib import Path


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
        # Reverse-form variants for Delta species: "Aegislash-Delta" -> "Delta Aegislash", "delta-aegislash", "deltaaegislash"
        stripped = value.strip()
        low = stripped.lower()
        if "delta" in low:
            tokens = [t for t in low.replace("_", "-").replace(" ", "-").split("-") if t]
            if "delta" in tokens:
                non_delta = [t for t in tokens if t != "delta"]
                if non_delta:
                    reversed_slug = "delta-" + "-".join(non_delta)
                    reversed_space = "Delta " + " ".join(t.capitalize() for t in non_delta)
                    for variant in [reversed_slug, reversed_space, compact(reversed_slug), reversed_space.lower()]:
                        if variant and variant not in seen:
                            seen.add(variant)
                            results.append(variant)
    return results


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def detect_showdown_data_dir() -> Path | None:
    path = Path.home() / "AppData" / "Roaming" / "ModrinthApp" / "profiles" / "Cobblemon Delta" / "showdown" / "data"
    return path if path.exists() else None
