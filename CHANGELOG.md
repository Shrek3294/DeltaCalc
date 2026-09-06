# Changelog

## 3.2.0

- Responsive and reachable panels: dynamic geometry reconciliation keeps all panels, tooltips, popups, and battle log elements within bounds across window resizes and GUI scale changes without invalid clamp ranges or lost panels.
- Guaranteed vs. likely outcomes and confidence warnings: structured damage calculations separate guaranteed KOs from likely KOs with distinct visual styles, left-edge confidence rails for lower confidence tiers, and visible de-duplicated warning chips for uncertain states.
- Explicit calculator controls: override rows provide separate click targets for previous, next, and reset actions; section bands feature clear collapse/expand chevrons; and panel resize handles provide active hover feedback.
- HUD layout reset and default lanes: adds a discoverable one-shot layout reset toggle under a dedicated Layout category in Mod Menu / Cloth Config settings that restores default geometry on Done; places the Battle Info Panel on the left edge and Damage Calculator on the right edge by default to avoid fresh-install overlap.
- Stronger invalidation and form safety: battle snapshots use deterministic length-prefixed fingerprinting across all calculation-relevant fields (stats, weight, items, moves, tera types), and form resolution catches unexpected form exceptions with bounded, rate-limited diagnostics to avoid crashes or log spam.
- Pure regression test suite: adds comprehensive JUnit tests for viewport bounds reconciliation, layout calculations, snapshot fingerprint collision resistance, coordinator invalidation rules, safe form diagnostic gates, and hardened version comparison.
- Cross-namespace overlay mixin safety: dual-targets BattleOverlay render across development (named) and production (intermediary) runtimes without refmap dependency, resolving a startup crash during mixin application.

## 0.9.1.6-deltacalc

- Mega-stone trump now swaps the holder's ability to the mega's intrinsic, on both sides. Charizardite Y → Drought, Charizardite X → Tough Claws, Gardevoirite → Pixilate, Mawilite → Huge Power, Mewtwonite X → Steadfast, Mewtwonite Y → Insomnia, etc. Hand-verified for all 47 Gen 6-7 megas. The DB doesn't carry abilities so the trump rule reads from a curated `MEGA_ABILITY_BY_FORM_KEY` map keyed by `<species>-mega(-x|-y)?`. Ability-conditional effects (Tough Claws on contact, Pixilate's Normal→Fairy + 1.2×, etc.) fire automatically as soon as the mega stone is detected. Opponent-side ability override still respects manual user-cycled overrides; player side always overrides since there's no manual-cycle mechanism for the player.

## 0.9.1.5-deltacalc

- Fix mega-stone cycle not changing damage numbers (the v0.9.1.4 trump rule actually firing). Root cause: `computeMegaSwap` only consulted Cobblemon's `species.getForm(setOf("mega-x"))` to source the mega's base stats, and Cobblemon's actual aspect-name convention for Mega Charizard X / Y (and others) doesn't match `"mega-x"` / `"megax"`, so the lookup silently returned the base form and the swap bailed out. The calc now reads mega base stats directly from the bundled battle database (`charizard-mega-x` → 78/130/111/130/85/100, `charizard-mega-y` → 78/104/78/159/115/100, etc.) — hand-verified for every Gen 6-7 mega — and only falls back to the Cobblemon aspect lookup when the DB doesn't have the entry. Aspect-fallback candidate list also widened (`mega_x`, `x`, `mega-form`) for mods that register megas under non-standard aspects.
- Surfaced mega stones in the opponent item-cycle UI. Charizard now shows Charizardite X / Charizardite Y as cycle options, Gardevoir gets Gardevoirite, etc., for every Gen 6-7 mega-capable species — previously the cycle only included usage-derived items plus a generic common-items list, so reaching a mega stone via cycle required the species' competitive usage data to include it.

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
