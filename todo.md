# DeltaCalc TODO

## Phase 0
- [x] Scaffold Fabric Java client mod skeleton
- [x] Add a toggleable placeholder overlay rendered from a demo battle snapshot
- [x] Add debug logging for current screen changes and inferred battle-state entry
- [x] Document target-version placeholders and initial research notes
- [x] Replace screen heuristics with verified Cobblemon client hooks (CobblemonClient.battle != null)

## Phase 1
- [x] Define normalized ranked-usage JSON schema in runtime models
- [x] Add Python ranked HTML importer under `tools/usage-import`
- [x] Bundle a starter usage snapshot for local development
- [x] Generate a full ladder snapshot from the live ranked site (3 Elo tiers, 286/249/211 species)
- [x] Add stronger alias coverage for Delta-specific species and forms (reverse-form + delta: namespace fix)

## Phase 2
- [x] Add active battle snapshot models
- [x] Add revealed-info tracking models
- [ ] Read active species, HP, status, stat stages, and field state from real Cobblemon battle data
- [ ] Detect move/item/ability reveals from live client events

## Phase 3
- [x] Implement guessed-set builder from local JSON
- [x] Render guessed opponent header and move list
- [x] Mark guessed vs revealed values in the overlay
- [x] Add fallback behavior for missing species usage data

## Phase 4
- [x] Add a simplified damage estimator and KO labels
- [x] Compute player move estimates into the guessed opponent set
- [x] Compute guessed opponent move estimates into the player
- [x] Replace heuristic move power data with live Cobblemon + Showdown flag DB
- [x] Improve field, item, and ability accuracy (Phase 8 extensive coverage)

## Phase 5
- [x] Add move replacement logic for newly revealed moves
- [x] Add item/ability overwrite logic
- [x] Add a dirty-state recalculation path in the overlay pipeline
- [ ] Wire live reveal events into battle-state updates

## Phase 6
- [x] Add a small in-memory config with overlay/demo toggles
- [x] Persist user config to disk (PanelConfig.save/load via cobblemonextendedbattleui.json)
- [x] Add overlay positioning and scaling options (panelX/Y, width/height, fontScale)
- [x] Log DB-vs-mod type mismatches (CalcBattleSnapshotFactory.kt:70-78)
- [x] Add mismatch/debug dump tooling (DebugDumper writes latest-calc.json, gated on PanelConfig.debugDumpEnabled)
- [ ] Validate against real ladder battles and tighten species mappings (needs in-game testing)

## Phase 7 — DB coverage
- [x] DB types override live Cobblemon form types (fix for stale mod species data)
- [x] Heuristic base-stat spreads for species without ranked/Smogon sets
- [x] Curated sets JSON plumbing (`delta-curated-sets.json`, `--curated-sets` flag)
- [ ] Populate curated sets for common non-ranked Delta species

## Phase 8 — Calc accuracy (edge cases)
- [x] Move flag database (`tools/database/build_move_flags.py`, 952 moves from Showdown)
- [x] Offensive abilities: Tough Claws, Strong Jaw, Iron Fist, Mega Launcher, Sharpness, Punk Rock, Reckless, Sheer Force, Technician, Tinted Lens, Water Bubble (offensive), Steelworker, Dragon's Maw, Transistor, Rocky Payload
- [x] Pixelate family (Aerilate/Pixilate/Refrigerate/Galvanize: type change + 1.2x)
- [x] Defensive abilities: Water Bubble, Dry Skin Fire weakness, Fluffy, Ice Scales, Multiscale/Shadow Shield, Punk Rock (defensive sound), Purifying Salt
- [x] Mold Breaker / Turboblaze / Teravolt (bypass defender ability)
- [x] Wonder Guard (only SE hits)
- [x] Items: Expert Belt, Muscle Band, Wise Glasses
- [x] Foul Play (uses defender's Atk + stages + item)
- [x] Fixed-damage moves: Seismic Toss, Night Shade, Dragon Rage, Sonic Boom, Super Fang, Endeavor, Final Gambit, Psywave
- [x] Critical hit damage (1.5x, 2.25x Sniper, bypasses screens) — in DamageEstimate fields
- [x] Multi-hit moves: minHits/maxHits scale total damage (Bullet Seed, Rock Blast)
- [x] Weight-scaling BP (Low Kick, Grass Knot, Heavy Slam, Heat Crash) + Showdown weight data
- [x] Speed-scaling BP (Electro Ball, Gyro Ball)
- [x] Facade, Venoshock, Brine, Acrobatics, Eruption/Water Spout/Dragon Energy, Stored Power, Punishment
- [x] Full 16-roll damage distribution (damageRolls field)
- [x] Photon Geyser / Light That Burns the Sky / Tera Blast — split-category based on effective Atk vs SpA
- [x] Sunsteel Strike / Moongeist Beam / Photon Geyser / LTBTS bypass defender ability
- [x] Crit clamp: ignore attacker negative stages & defender positive stages
- [x] UI surfacing: multi-hit hit count + crit damage (toggles in PanelConfig: showMultiHitCount, showCritDamage)

## Bugs from damage-compare trace (2026-04-28)

Source: `logs/ebu-damage-compare.log` from two ranked battles. Skip Delta-species coverage — handled separately.

### Calc engine bugs

- [ ] **Aegislash form not tracked** — Stance Change post-attack flips Shield→Blade (Atk 50→150), calc keeps using Shield stats. Sacred Sword / Shadow Sneak underpredicted by 3-4× (e.g. 13.4-15.7% predicted vs 58% actual). Likely a `FormTracker` → snapshot wiring issue; the form change message arrives but `CalcPokemonSnapshot.actualStats` doesn't refresh.
- [ ] **Mega evolution stats not applied** — Mega Lopunny (Atk 73→136). Fake Out / U-turn underpredicted by ~1.5×. Same root cause as above; mega form swap post-evolution isn't propagating to the calc snapshot. Probably affects every Mega.
- [ ] **Ogerpon mask handling** — Ivy Cudgel from Ogerpon-Hearthflame consistently underpredicted 1.3-1.7× (e.g. 27-32% predicted vs 55% actual). Need to: (a) detect mask from item, (b) apply Embody Aspect's stat boost on switch-in, (c) change Ivy Cudgel's type per mask (Hearthflame=Fire, Wellspring=Water, Cornerstone=Rock).
- [ ] **Spectral Thief stat-steal not reflected in pre-move estimate** — `BattleStateTracker.stealPositiveStats` runs on the message, but the calc snapshot used for the prediction was already computed before the steal applied. Spectral Thief consistently under-predicts when target had positive stages. Fix: trigger calc invalidation on stat-steal events, or include the pending steal in the estimate.
- [ ] **Item inference: Draculedge holding Life Orb misclassified** — Trace shows clean 9.67% (= 26/269 ≈ 10%) self-damage every attacking turn, signature of Life Orb. The opponent set inference picked something else, which also depresses Draculedge's damage prediction. Audit `OpponentInferenceService` Life Orb signal: recurring 10%-of-max-HP self-damage right after a damaging move.
- [ ] **Sacred Sword from Draculedge over-hits even after item fix?** — Line 21 (turn 13 of battle 1): predicted 43-50%, actual 73.7%. May resolve once Life Orb is recognized; otherwise dig in. Recheck after item fix lands.

### Damage-compare logging (false-positive noise, not calc bugs)

- [ ] **Filter attacker self-damage from compare log** — Iron Barbs / Rocky Helmet / Life Orb recoil currently log as `predicted=NO_MATCH` on the attacker, polluting the trace. Gate `DebugDumper.recordActualDamage` so it only emits when `targetUuid == resolved(MessageParser.lastMoveTarget)`. Self-damage can still be logged separately as a `selfDamage=...` field if useful.
- [ ] **Filter end-of-turn passive damage** — Toxic / Leech Seed / sandstorm ticks pair against the most recent attacking move and always show as `BELOW`. Either (a) only log within a short window after the move resolves, or (b) detect the end-of-turn phase and tag entries `phase=endOfTurn predicted=N/A`.
- [ ] **Multi-hit aggregation** — Multi-hit moves currently emit one log line per hit, all comparing against the move's full-move predicted range, so every hit reads `BELOW`. Aggregate hits by (attacker, move, target) within a short window and emit one comparison line per move resolution.
- [ ] **Killing-blow overkill is not really BELOW** — When the predicted range exceeds remaining HP, `actual` is capped and reads `BELOW`. Add a `cap=KO` tag and treat predicted ≥ remainingHP% as `IN_RANGE` for diagnostic purposes.

### Move-data bugs (non-Delta)

- [ ] **Wretched Stab hit count uncertainty** — Predicted range 12-35% then 55-162% across battles suggests min/maxHits is unstable. *(User: probably a logging artifact from per-hit emit, not a real calc bug — confirm after multi-hit aggregation lands.)*

### Investigation queue (one-off, not yet a confirmed bug)

- [ ] Psycho Cut from "Human" on Tinkaton-G underpredicted 53-62% vs actual 72% (line 89 turn 17, battle 2). Could be Tinkaton's set or a Psycho Cut crit. Watch for repeats.
