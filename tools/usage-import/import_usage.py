#!/usr/bin/env python3
import argparse
import datetime as dt
import json
import re
import sys
import urllib.error
import urllib.request
from html import unescape
from pathlib import Path


COMMON_SEED_SLUGS = [
    "ogerpon-wellspring",
    "corviknight",
    "greattusk",
    "kingambit",
    "landorus-therian",
    "volcarona",
]
BASE_STAT_LABELS = {
    "hp": "HP",
    "atk": "Attack",
    "def": "Defense",
    "spa": "Sp. Atk",
    "spd": "Sp. Def",
    "spe": "Speed",
}


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "DeltaCalcUsageImporter/1.0"})
    with urllib.request.urlopen(request) as response:
        return response.read().decode("utf-8")


def strip_tags(value: str) -> str:
    return unescape(re.sub(r"<[^>]+>", "", value)).strip()


def slugify(name: str) -> str:
    return name.lower().replace(" ", "-").replace("_", "-")


def parse_seed_url(seed_url: str) -> tuple[str, str, str, str, str]:
    match = re.match(r"^(https?://[^/]+)/season-(\d+)/([^/]+)/([^/]+)/([^/?#]+)", seed_url)
    if not match:
        raise ValueError(f"Unsupported seed URL format: {seed_url}")
    base_url, season, stage, elo, slug = match.groups()
    return base_url.rstrip("/"), season, stage, elo, slugify(slug)


def build_species_url(base_url: str, season: str, stage: str, elo: str, slug: str) -> str:
    return f"{base_url.rstrip('/')}/season-{season}/{stage}/{elo}/{slugify(slug)}"


def parse_sidebar_entries(html: str) -> list[dict]:
    entries = []
    pattern = (
        r'<a href="([a-z0-9\-]+)">\s*<div class="sidebar-pokemon(?: active)?">\s*'
        r"<p>([^<]+)</p>\s*<p>([\d.]+)%</p>\s*</div>\s*</a>"
    )
    for slug, name, percent_text in re.findall(pattern, html, flags=re.S | re.I):
        entries.append(
            {
                "slug": slugify(slug),
                "displayName": strip_tags(name),
                "usagePercent": float(percent_text),
            }
        )
    return entries


def resolve_seed_page(base_url: str, season: str, stage: str, elo: str, seed_slug: str | None) -> tuple[str, str]:
    candidates = []
    if seed_slug:
        candidates.append(slugify(seed_slug))
    candidates.extend(slug for slug in COMMON_SEED_SLUGS if slug not in candidates)

    last_error = None
    for candidate in candidates:
        url = build_species_url(base_url, season, stage, elo, candidate)
        try:
            html = fetch(url)
        except urllib.error.HTTPError as exc:
            last_error = exc
            continue
        if parse_sidebar_entries(html):
            return candidate, html

    raise ValueError(f"Could not resolve a valid seed page for season-{season}/{stage}/{elo}: {last_error}")


def parse_usage_stats(html: str) -> tuple[int, float, int]:
    rank_match = re.search(r"Usage Rank</(?:p|h2)>\s*<p[^>]*>#(\d+)</p>", html)
    usage_match = re.search(r"Usage Percent</(?:p|h2)>\s*<p[^>]*>([\d.]+)%</p>", html)
    samples_match = re.search(r"Samples</(?:p|h2)>\s*<p[^>]*>([\d,]+)</p>", html)
    return (
        int(rank_match.group(1)) if rank_match else 0,
        float(usage_match.group(1)) if usage_match else 0.0,
        int(samples_match.group(1).replace(",", "")) if samples_match else 0,
    )


def parse_types_from_block(block: str) -> list[str]:
    return re.findall(r'alt="([a-z\-]+)" class="type-icon"', block)


def build_aliases(display_name: str, slug: str) -> list[str]:
    alias_candidates = {
        display_name,
        display_name.replace("-", " "),
        display_name.replace("-", ""),
        slug.replace("-", " "),
        slug.replace("-", ""),
    }
    return [alias for alias in sorted(alias_candidates) if alias]


def parse_header(html: str) -> tuple[str, list[str]]:
    match = re.search(r'<div class="pokemon-name">\s*<h1>([^<]+)</h1>(.*?)</div>', html, flags=re.S)
    if not match:
        raise ValueError("Could not find pokemon name block")
    return strip_tags(match.group(1)), parse_types_from_block(match.group(2))


def parse_base_stats(html: str) -> dict:
    stats = {}
    for key, label in BASE_STAT_LABELS.items():
        match = re.search(rf">{re.escape(label)}</p>\s*<p[^>]*>(\d+)</p>", html)
        if match:
            stats[key] = int(match.group(1))
    return stats


def parse_options(html: str, class_name: str) -> list[dict]:
    results = []
    if class_name == "move":
        blocks = re.findall(
            r'<div class="move">\s*<div class="move-left">\s*<p>([^<]+)</p>(.*?)</div>\s*<p[^>]*>([\d.]+)%</p>\s*</div>',
            html,
            flags=re.S,
        )
        for name, inner, percent_text in blocks:
            clean_name = strip_tags(name)
            if not clean_name or clean_name == "Other":
                continue
            types = parse_types_from_block(inner)
            results.append(
                {
                    "id": slugify(clean_name),
                    "displayName": clean_name,
                    "type": types[0] if types else "",
                    "usagePercent": float(percent_text),
                    "sampleCount": None,
                }
            )
        return results

    if class_name == "item":
        blocks = re.findall(
            r'<div class="item">\s*<div>\s*<p>([^<]+)</p>\s*</div>\s*<p[^>]*>([\d.]+)%</p>\s*</div>',
            html,
            flags=re.S,
        )
        option_type = "item"
    elif class_name == "ability":
        blocks = re.findall(
            r'<div class="ability">\s*<div>\s*<p>([^<]+)</p>\s*</div>\s*<p[^>]*>([\d.]+)%</p>\s*</div>',
            html,
            flags=re.S,
        )
        option_type = "ability"
    else:
        return results

    for name, percent_text in blocks:
        clean_name = strip_tags(name)
        if not clean_name or clean_name == "Other":
            continue
        results.append(
            {
                "id": slugify(clean_name),
                "displayName": clean_name,
                "type": option_type,
                "usagePercent": float(percent_text),
                "sampleCount": None,
            }
        )
    return results


def parse_spreads(html: str) -> list[dict]:
    pattern = r'<div class="ev-spread">\s*<p[^>]*>([^<]+)</p>\s*<p[^>]*>([\d.]+)%</p>'
    spreads = []
    for spread_text, usage in re.findall(pattern, html):
        clean = strip_tags(spread_text)
        nature, values = clean.split(" ", 1)
        hp, atk, defe, spa, spd, spe = [int(value) for value in values.split("/")]
        spreads.append(
            {
                "nature": nature,
                "evs": {"hp": hp, "atk": atk, "def": defe, "spa": spa, "spd": spd, "spe": spe},
                "usagePercent": float(usage),
            }
        )
    return spreads


def parse_teammates(html: str) -> list[dict]:
    teammates = []
    pattern = (
        r'<div class="teammate">\s*<div class="teammate-left">\s*<a href="([a-z0-9\-]+)">\s*<p>([^<]+)</p>\s*</a>\s*'
        r'<div class="types">(.*?)</div>\s*</div>\s*<p[^>]*>([\d.]+)%</p>\s*</div>'
    )
    for slug, name, types_block, usage_percent in re.findall(pattern, html, flags=re.S | re.I):
        clean_name = strip_tags(name)
        if not clean_name:
            continue
        teammates.append(
            {
                "speciesId": slugify(slug),
                "displayName": clean_name,
                "types": parse_types_from_block(types_block),
                "usagePercent": float(usage_percent),
            }
        )
    return teammates


def parse_species_page(base_url: str, season: str, stage: str, elo: str, slug: str, sidebar_entry: dict | None = None) -> dict:
    url = build_species_url(base_url, season, stage, elo, slug)
    html = fetch(url)
    display_name, types = parse_header(html)
    usage_rank, usage_percent, sample_count = parse_usage_stats(html)
    if sidebar_entry and not usage_percent:
        usage_percent = sidebar_entry["usagePercent"]

    return {
        "speciesId": slugify(slug),
        "displayName": display_name,
        "slug": slugify(slug),
        "types": types,
        "baseStats": parse_base_stats(html),
        "usageRank": usage_rank,
        "usagePercent": usage_percent,
        "sampleCount": sample_count,
        "aliases": build_aliases(display_name, slugify(slug)),
        "moves": parse_options(html, "move"),
        "items": parse_options(html, "item"),
        "abilities": parse_options(html, "ability"),
        "spreads": parse_spreads(html),
        "teammates": parse_teammates(html),
    }


def build_dataset(base_url: str, season: str, stage: str, elo: str, seed_slug: str | None, limit: int | None) -> dict:
    resolved_seed_slug, seed_html = resolve_seed_page(base_url, season, stage, elo, seed_slug)
    sidebar_entries = parse_sidebar_entries(seed_html)
    if limit is not None:
        sidebar_entries = sidebar_entries[:limit]

    total = len(sidebar_entries)
    species = []
    for index, entry in enumerate(sidebar_entries, start=1):
        print(f"[{index}/{total}] Fetching {entry['slug']}", file=sys.stderr)
        species.append(parse_species_page(base_url, season, stage, elo, entry["slug"], entry))

    return {
        "sourceMeta": {
            "site": re.sub(r"^https?://", "", base_url).rstrip("/"),
            "season": season,
            "stage": stage,
            "elo": elo,
            "seedSlug": resolved_seed_slug,
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        },
        "species": species,
    }


def default_output_path(season: str, stage: str, elo: str) -> str:
    return f"src/main/resources/data/deltacalc/usage/season-{season}-{stage}-{elo}.generated.json"


def main() -> int:
    parser = argparse.ArgumentParser(description="Scrape Cobblemon Delta ranked usage into normalized JSON.")
    parser.add_argument("--base-url", default="http://ranked.cobblemondelta.com")
    parser.add_argument("--season", default="6")
    parser.add_argument("--stage", default="mid")
    parser.add_argument("--elo", default="1000")
    parser.add_argument("--seed-slug", default=None, help="Species slug used to enter a ladder page and read the sidebar.")
    parser.add_argument("--seed-url", default=None, help="Full ranked species URL; overrides base/season/stage/elo/seed-slug.")
    parser.add_argument("--limit", type=int, default=None, help="Maximum species pages to fetch. Omit for full ladder.")
    parser.add_argument("--output", default=None)
    args = parser.parse_args()

    if args.seed_url:
        args.base_url, args.season, args.stage, args.elo, args.seed_slug = parse_seed_url(args.seed_url)

    output_path = Path(args.output or default_output_path(args.season, args.stage, args.elo))

    try:
        dataset = build_dataset(args.base_url, args.season, args.stage, args.elo, args.seed_slug, args.limit)
    except Exception as exc:
        print(f"Import failed: {exc}", file=sys.stderr)
        return 1

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(dataset, indent=2), encoding="utf-8")
    print(f"Wrote {len(dataset['species'])} species to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
