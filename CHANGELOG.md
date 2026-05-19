# Changelog

## 0.9.1.4-deltacalc

- Mega-stone trump rule for both sides: when the held item is a mega stone matching the species, the calc now forces the snapshot into its mega form (base stats, types, intrinsic ability) regardless of Cobblemon's live form. Previously this only applied to the opponent; the player side respected `partyPokemon` stats, which silently downgraded the calc when (a) the player hadn't pressed mega-evolve yet or (b) Cobblemon's post-switch-in visual glitch reverted the sprite to base form. Player `actualStats` are now re-derived heuristically from the new mega base stats whenever a mega stone is detected (precision trade-off vs. always-correct mega base; accepted per user direction). Only triggers on items in the mega-stone table — non-mega items never override live state.
- Data: scraped 7 new mons from `#custom-pokemon` (Trapinch-ATOM, Sevygarde, Hydrapple-Ultra, Applin/Dipplin/Flapple/Appletun-Ultra). Curated sets for Hydrapple-Ultra (AV Regenerator) and Sevygarde (defensive Toxic/Haze utility).
- Data: curated Trick Room Cresselia set replaces the OU defensive default.
- Removed Gigantamax handling — not in the Delta pack (Dynamax kept).

## 0.9.1.3-deltacalc

- Fix Parasol Prayer / Delta Stream: damage calc now clamps super-effective-vs-Flying moves to neutral on Flying-type defenders when either active Pokemon has the ability.
- Fix mega evolution stats lost after switch-out/switch-in: `FormTracker` preserves `isMega` across switches so the calc keeps using mega base stats even when Cobblemon's sprite reverts to base form (visual glitch). Player-side `actualStats` are re-derived from mega base stats in this state so `partyPokemon` reporting base values doesn't silently downgrade the calc.
- Fix Return / Frustration: now always calculated at 102 BP since the calc has no access to friendship; matches competitive max-friendship convention.
- Fix variant-alias overwrite in `BattleDatabase`: a two-pass indexer (primary identifiers first, aliases via `putIfAbsent`) prevents 303 variant entries from overwriting their base species' lookup key. Fixes Ursaluna defaulting to Bloodmoon, Zapdos/Moltres defaulting to Galar, Decidueye defaulting to Hisui, Camerupt defaulting to Mega, Basculegion defaulting to F, Aegislash defaulting to Blade, every typed Arceus, every regional/Gigantamax form, and similar mismatches.
- Fix Facade vs. burn: Facade now correctly bypasses burn's 0.5× physical Atk drop in addition to doubling its base power (previously netted to 1×, wiping out the BP boost).
- Harden Guts trigger: requires a recognized status name (burn/poison/paralysis/sleep/freeze + aliases) instead of any non-null status string.

## 0.9.1-deltacalc

- Rebrands the client mod as DeltaCalc for release packaging
- Adds the damage calculator panel and battle overlay improvements for Cobblemon Delta battles
- Includes battle-state export support for external analysis workflows
- Bundles a release icon and aligns packaged metadata with the DeltaCalc repository
