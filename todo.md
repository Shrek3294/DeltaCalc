# DeltaCalc TODO

## Phase 0
- [x] Scaffold Fabric Java client mod skeleton
- [x] Add a toggleable placeholder overlay rendered from a demo battle snapshot
- [x] Add debug logging for current screen changes and inferred battle-state entry
- [x] Document target-version placeholders and initial research notes
- [ ] Replace screen heuristics with verified Cobblemon client hooks

## Phase 1
- [x] Define normalized ranked-usage JSON schema in runtime models
- [x] Add Python ranked HTML importer under `tools/usage-import`
- [x] Bundle a starter usage snapshot for local development
- [ ] Generate a full ladder snapshot from the live ranked site
- [ ] Add stronger alias coverage for Delta-specific species and forms

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
- [ ] Replace heuristic move power data with live Cobblemon move data
- [ ] Improve field, item, and ability accuracy

## Phase 5
- [x] Add move replacement logic for newly revealed moves
- [x] Add item/ability overwrite logic
- [x] Add a dirty-state recalculation path in the overlay pipeline
- [ ] Wire live reveal events into battle-state updates

## Phase 6
- [x] Add a small in-memory config with overlay/demo toggles
- [ ] Persist user config to disk
- [ ] Add overlay positioning and scaling options
- [ ] Add mismatch/debug dump tooling for live battles
- [ ] Validate against real ladder battles and tighten species mappings
