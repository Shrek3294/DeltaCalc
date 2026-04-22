# DeltaCalc

DeltaCalc is a client-side utility mod for Cobblemon Delta on Fabric 1.21.1. It adds an in-battle damage calculator, expanded battle overlays, richer tooltips, team indicators, and optional battle-state export for external analysis tools.

## Features

- Live damage calculation panel for active battle states
- Battle info overlays for weather, terrain, side conditions, and boosts
- Team indicators and enriched Pokemon move/tooltips
- Battle log enhancements
- Optional structured battle export for external tooling

## Requirements

- Minecraft 1.21.1
- Fabric Loader
- Fabric API
- Fabric Language Kotlin
- Cobblemon 1.7.3+1.21.1
- Java 21

## Installation

1. Install Fabric Loader for Minecraft 1.21.1.
2. Add Fabric API, Fabric Language Kotlin, Cobblemon, and DeltaCalc to your `mods` folder.
3. Launch the game and configure DeltaCalc through Mod Menu if you have it installed.

## Notes For Release

- The internal mod id remains `cobblemonextendedbattleui` for compatibility with the current codebase.
- Set `MODRINTH_PROJECT_SLUG` in [`CobblemonExtendedBattleUI.kt`](/Z:/Cb%20delta/DeltaCalc/src/main/kotlin/com/cobblemonextendedbattleui/CobblemonExtendedBattleUI.kt:1) after the Modrinth project is created if you want in-game update checks.
