# DeltaCalc Research Notes

## Validated External Facts
- Ranked source root: [http://ranked.cobblemondelta.com/](http://ranked.cobblemondelta.com/)
- Example species page: [http://ranked.cobblemondelta.com/season-6/mid/1000/volcarona](http://ranked.cobblemondelta.com/season-6/mid/1000/volcarona)
- As verified on March 31, 2026:
  - `http://ranked.cobblemondelta.com/` returns `302` to a species page.
  - `http://ranked.cobblemondelta.com/season-6/mid/1000/` returns `404`.
  - Species pages are server-rendered HTML and include:
    - usage rank
    - usage percent
    - sample count
    - base stats
    - moves
    - items
    - abilities
    - EV spreads
    - teammates

## Current Scaffold Choices
- Language: Java
- Runtime data source: bundled JSON snapshot in `src/main/resources/data/deltacalc/usage`
- Import tool: Python script in `tools/usage-import`
- Battle integration: no Cobblemon dependency yet; initial client hook uses screen detection, debug logging, and demo battle snapshots

## Known Gaps To Close
- Pin exact Minecraft, Fabric Loader, Fabric API, Cobblemon, and Delta modpack versions against the local install.
- Replace screen-name heuristics with real Cobblemon battle-state integration.
- Decide whether Delta custom mechanics can be read from client registries or need override files.
- Verify server policy allows this class of client overlay.

