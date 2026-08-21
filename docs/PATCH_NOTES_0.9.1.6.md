# DeltaCalc 0.9.1.6 — Patch Notes

*Full rollup covering 0.9.1.3 → 0.9.1.6.*

---

## TL;DR

- **Megas now actually mega — base stats, types, AND ability.** Holding Charizardite Y makes the calc read Drought + 159 SpA. Mawilite makes Huge Power double Atk. Pixilate fires on Mega Gardevoir's Hyper Voice. All 47 Gen 6-7 megas covered on both sides.
- **Strong Winds works.** Flying defenders no longer take 2× from Ice / Rock / Electric while Parasol Prayer or Delta Stream is on the field.
- **Return / Frustration = 102 BP.** No more friendship-guessing.
- **Facade no longer cut by burn.** Bypasses the 0.5× physical Atk drop in addition to doubling base power.
- **Database overhaul.** 303 variants (Ursaluna, Zapdos, Moltres, Aegislash, every Mega, every regional, every typed Arceus) were silently mapping to the wrong form. Fixed.
- **7 new mons scraped from Discord** — Sevygarde, Hydrapple-Ultra, the Applin-Ultra family, Trapinch-ATOM — with sets baked in.
- **Curated TR Cresselia set** replaces the OU defensive default.
- **Gigantamax removed** — not in the Delta pack.

---

## Mega Evolution — finally works (0.9.1.3 → 0.9.1.6)

Three separate bugs collapsing into one bad experience: the calc would silently use base-form numbers when it should have used mega numbers. All three are fixed across four versions.

### Pre-mega: holding the stone, haven't pressed mega-evolve
Player or opponent is holding a mega stone the calc has identified. The calc now commits to the mega form immediately — base stats, types, intrinsic ability — without waiting for the mega evolution to actually fire. Applies symmetrically to both sides.

Previously: only the opponent got the swap. The player snapshot respected `partyPokemon` stats, which silently downgraded the calc when (a) the player hadn't pressed mega-evolve yet or (b) Cobblemon's post-switch-in visual glitch reverted the sprite to base form.

### Post-mega + switch-out + switch-in (the Cobblemon visual glitch)
Cobblemon has a known issue where a mega's sprite reverts to base form on switch-in even though the species is still mega. The form tracker now preserves the mega state across switches (`FormTracker.clearCurrentForm` gained a `keepPermanent` flag; switch/drag cleanup uses it), and player-side `actualStats` re-derive from mega base stats so the visual glitch can't downgrade the calc.

### Stats source — the DB, not Cobblemon's aspect lookup
The v0.9.1.4 trump rule was sourcing mega base stats via `species.getForm(setOf("mega-x"))` — Cobblemon's PokemonSpecies aspect API. For Charizard X/Y (and likely other megas), Cobblemon's actual aspect name doesn't match `"mega-x"` / `"megax"`, so the lookup silently returned the base form and the swap bailed.

The calc now reads mega base stats from the bundled battle database first. It has hand-verified entries for every Gen 6-7 mega:

| Form | HP / Atk / Def / SpA / SpD / Spe |
|---|---|
| Charizard-Mega-X | 78 / 130 / 111 / 130 / 85 / 100 |
| Charizard-Mega-Y | 78 / 104 / 78 / 159 / 115 / 100 |
| Gardevoir-Mega | 68 / 85 / 65 / 165 / 135 / 100 |
| Mawile-Mega | 50 / 105 / 125 / 55 / 95 / 50 |
| ...all 47 entries | |

DB hit short-circuits the Cobblemon aspect path entirely. The aspect path is kept as fallback for species missing from the DB, with a widened candidate list (`mega_x`, `x`, `mega-form`, `megaform`).

### Ability also swaps (0.9.1.6)
The big one. The DB has mega base stats and types, but doesn't carry abilities. A hand-verified `MEGA_ABILITY_BY_FORM_KEY` map fills that in for all 47 Gen 6-7 megas:

| Mega | Ability | | Mega | Ability |
|---|---|---|---|---|
| Charizard X | Tough Claws | | Charizard Y | Drought |
| Mewtwo X | Steadfast | | Mewtwo Y | Insomnia |
| Gardevoir | Pixilate | | Altaria | Pixilate |
| Lopunny | Scrappy | | Mawile | Huge Power |
| Medicham | Pure Power | | Kangaskhan | Parental Bond |
| Salamence | Aerilate | | Pinsir | Aerilate |
| Scizor | Technician | | Heracross | Skill Link |
| Lucario | Adaptability | | Beedrill | Adaptability |
| Aerodactyl | Tough Claws | | Metagross | Tough Claws |
| Sharpedo | Strong Jaw | | Manectric | Intimidate |
| Blastoise | Mega Launcher | | Sceptile | Lightning Rod |
| Tyranitar | Sand Stream | | Garchomp | Sand Force |
| Steelix | Sand Force | | Abomasnow | Snow Warning |
| Glalie | Refrigerate | | Houndoom | Solar Power |
| Camerupt | Sheer Force | | Pidgeot | No Guard |
| Banette | Prankster | | Absol | Magic Bounce |
| Sableye | Magic Bounce | | Diancie | Magic Bounce |
| Gengar | Shadow Tag | | Slowbro | Shell Armor |
| Latias / Latios | Levitate | | Aggron | Filter |
| Ampharos | Mold Breaker | | Gyarados | Mold Breaker |
| Audino | Healer | | Alakazam | Trace |
| Venusaur | Thick Fat | | Blaziken | Speed Boost |
| Swampert | Swift Swim | | Gallade | Inner Focus |

All the ability-conditional engine paths fire automatically — Tough Claws on contact, Pixilate's Normal→Fairy + 1.2×, Mega Launcher's pulse boost, Huge Power's 2× physical Atk, Solar Power's 1.5× under sun, Strong Jaw's 1.5× on bites, etc.

### Opponent item-cycle UI now surfaces mega stones
Charizard's item cycle now shows Charizardite X and Y as options. Same for Gardevoir → Gardevoirite, Mawile → Mawilite, all 45 mega-capable Gen 6-7 species. Previously the cycle only included usage-derived items plus a generic common-items list, so most megas required usage data to surface them. Cycle limit raised 8 → 10 to make room.

### Hard constraint
The trump rule only fires for items in the mega-stone table. No other item type overrides Cobblemon's live state.

### Asymmetry to know about
- **Opponent ability** still respects manual cycle overrides — if you cycled to a specific ability, trump won't undo it.
- **Player ability** is overridden unconditionally (no manual cycle exists on player side).

---

## Calc engine (0.9.1.3)

### Strong Winds (Parasol Prayer / Delta Stream)
When either active Pokemon has Parasol Prayer or Delta Stream, super-effective-vs-Flying multipliers (Ice / Rock / Electric, 2×) are clamped to neutral on Flying defenders. Per-type, so dual-type Flying defenders still take normal damage from their non-Flying type (Rock 2× × Flying 1× = 2× total on a Rock/Flying defender from an Ice move).

Move tooltips flag the active state with "Strong Winds (Flying SE → neutral)".

### Return / Frustration
Both calculate at 102 BP. The calc can't read friendship values, and competitive sets always max it, so this matches Showdown.

### Facade vs. burn
Facade now correctly bypasses burn's 0.5× physical Atk drop in addition to doubling its base power. Previously these cancelled to 1× — burned Facade hit like normal Facade. Fixed.

### Guts trigger hardened
Only fires on recognized status names (burn / poison / paralysis / sleep / freeze + aliases) instead of any non-null status string.

---

## Database (0.9.1.3 → scrape day)

### Variant alias overwrite (303 species)
Massive lookup bug: every variant entry listed the base species name in its `aliases`. The old indexer let those aliases overwrite the base species' lookup key, so `"ursaluna"` resolved to Ursaluna-Bloodmoon, `"zapdos"` to Zapdos-Galar, `"aegislash"` to Aegislash-Blade — silently wrong stats on hundreds of species.

A two-pass indexer (primary identifiers first, aliases via `putIfAbsent`) now guarantees a variant's alias can never override its base species. Resolves:

- Ursaluna defaulting to Bloodmoon
- Zapdos / Moltres defaulting to Galar
- Decidueye defaulting to Hisui
- Camerupt defaulting to Mega
- Basculegion defaulting to F
- Aegislash defaulting to Blade
- Every Mega, regional, Gigantamax-tagged, typed-Arceus variant

### 7 new mons scraped (custom-pokemon channel)
Previous snapshot was 2026-03-31. Re-scraped to catch everything released since:

| Mon | Type | BST | Notable |
|---|---|---|---|
| **Sevygarde** | Poison/Ground | 600 | Seviper × Zygarde, Season 7 ranked reward, Shed Skin |
| **Hydrapple-Ultra** | Poison/Dragon | 540 | Spoiled Goods / Regenerator |
| Trapinch-ATOM | Water/Bug | 528 | Hyper Cutter / Strong Jaw |
| Dipplin-Ultra | Poison/Dragon | 485 | |
| Appletun-Ultra | Poison/Dragon | 485 | Thick Fat |
| Flapple-Ultra | Poison/Dragon | 485 | Hustle |
| Applin-Ultra | Poison/Dragon | 260 | Bulletproof |

### Curated competitive sets
- **Cresselia** — Trick Room replaces the OU defensive default. Levitate, Relaxed, 252 HP / 252 Def, Mental Herb > Leftovers, Trick Room / Moonblast / Psychic / Lunar Dance.
- **Hydrapple-Ultra** — AV Regenerator, Modest 244 HP / 252 SpA / 12 Spe, Fickle Beam / Sludge Bomb / Earth Power / Giga Drain. 25% Nasty Plot alt move, 20% Spoiled Goods alt ability.
- **Sevygarde** — Defensive utility, Careful 248 HP / 252 SpD / 8 Spe, Thousand Arrows / Toxic / Haze / Rest. 40% banded Adamant alt spread.

---

## Removed

- **Gigantamax handling** — not in the Delta pack. Message handler, signature-move bypass-ability entries, and translation key all removed. Dynamax handling kept intact.

---

## Internal / dev notes

- Strong Winds extracted as `isStrongWindsHolder` helper; `DamageContext.strongWindsActive` flag threaded into the type-chart loop.
- Mega-form lookup factored into a shared `computeMegaSwap` helper used by both `applyMegaFormSwap` (opponent) and the new `applyPlayerMegaSwap` (player).
- `CalcBattleSnapshotFactory.derivedHeuristicStats` exposed as `internal` so the mega swap can re-derive `actualStats` from new base stats.
- `FormTracker.clearCurrentForm` gained a `keepPermanent` flag; `StateUpdater` switch/drag cleanup now uses it so mega entries survive switch-outs.
- `MEGA_STONES_BY_SPECIES` (45 entries) drives the item-cycle prepend so every mega-capable species surfaces its stone(s) without depending on usage scrape coverage.
- `MEGA_ABILITY_BY_FORM_KEY` (47 entries) carries the intrinsic ability lookup since the DB doesn't store abilities.
- DB indexing changed from single-pass `put` to two-pass (primaries first via `putIfAbsent`, aliases second). Affects both species map and Delta-ranked set map.
- `BattleDatabase.findSpecies` lookup chain for mega forms keys on `<species>-mega(-x|-y)?` to match the data-pipeline's slug convention.

---

## Known limitations

- **Weather-setting on switch-in isn't automatic.** Pre-mega Charizard Y shows Drought as its ability but won't auto-set sun in the calc until the actual mega-evolve message fires. Same for Drizzle / Sand Stream / Snow Warning megas. Set the weather manually if you need Solar Power / sun-boosted Fire damage modeled.
- **Player mega stats lose precise IV/EV/nature investment.** The trump rule re-derives player `actualStats` heuristically from mega base stats. Within damage-roll variance for typical mega spreads; accepted trade-off vs. silently calc-ing base stats.
- **Two species still need a repro**: Cresselia base form and Pincurchin both have clean DB entries with no alias collisions — if you're still seeing wrong data, please drop specifics.
- **Gigantamax-tagged dead data** remains in the auto-generated database files. Harmless without the code paths reaching it; will be stripped on the next full data-pipeline run.

---

## Version timeline

- **0.9.1.3** — Strong Winds, mega sticky form (switch-out preserve), Return/Frustration, DB alias overwrite, Facade vs. burn, Guts hardening, curated TR Cresselia, Gigantamax removed.
- **0.9.1.4** — Mega-stone trump rule for both sides (player side previously missing); 7 new mons scraped + curated Hydrapple-Ultra / Sevygarde sets.
- **0.9.1.5** — Fix the v0.9.1.4 trump rule actually firing (DB is now primary source for mega base stats; broadened Cobblemon aspect fallback); opponent item cycle surfaces mega stones.
- **0.9.1.6** — Mega-stone trump also swaps ability to the mega's intrinsic on both sides (47-mega curated map).
