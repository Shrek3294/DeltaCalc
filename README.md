# DeltaCalc

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.7.3+-red.svg)](https://cobblemon.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-Delta--Calc-00AF5C.svg)](https://modrinth.com/mod/delta-calc)

**DeltaCalc** is an advanced client-side battle assistant and live damage calculator mod built for **Cobblemon** (specifically tuned for the **Cobblemon Delta** competitive ladder) on Fabric 1.21.1.

It provides real-time damage calculations, intelligent opponent set inference, comprehensive Gen 6–7 & custom Delta Mega Evolution handling, dynamic field overlays, move tooltips, and in-battle manual set overrides.

---

## Key Features

### 1. Live In-Battle Damage Calculator
- **Real-time damage projections**: Calculates damage rolls, min/max damage percentages, and KO projections (OHKO, 2HKO, etc.) for all player moves against the opponent and all opponent moves against the active Pokémon.
- **Smart Set Inference**: Automatically guesses opponent EV spreads, abilities, and items based on competitive ladder usage statistics (1000, 1300, and 1500+ Elo tiers) and Smogon fallback data.
- **Draggable & Persistent HUD**: Left-click and drag the calculation panel header anywhere on your screen. The position and dimensions are automatically saved to `config/cobblemonextendedbattleui.json`.
- **Matchup HP & Forecast Bars**: Visual forecast bars showing remaining HP post-attack for quick tactical decisions.

### 2. Full Mega Evolution & Delta Form Handling
- **Mega Evolution Stat & Ability Swapping**: Holding a Mega Stone immediately activates the Mega form's base stats, typing, and intrinsic abilities (e.g. Drought on Mega Charizard Y, Huge Power on Mega Mawile, Pixilate on Mega Gardevoir) across all 47 Gen 6–7 Megas as well as custom Delta Megas.
- **Cobblemon Aspect & Switch Glitch Resilience**: Preserves Mega state across switch-outs and switch-ins, avoiding visual sprite desyncs that would otherwise corrupt stat calculations.
- **Special Mechanics**: Custom Delta weather (e.g. *Strong Winds / Parasol Prayer / Delta Stream* negation of Flying weaknesses), *Thousand Arrows*, *Facade* burn bypass, and *White Herb* stat-drop clearing.

### 3. In-Battle Manual Overrides
- **Live Cycle Controls**: Click directly on the opponent's inferred Item, Ability, or Spread rows to cycle through likely alternatives during a live battle.
- **Visual Status Badges**: Clear visual badges (`MANUAL`, `REVEALED`, `GUESSED`) and usage percentage indicators so you always know what data the calculation is using.

### 4. Battle Information & Field Overlays
- **Field & Weather Tracking**: Active weather, terrain, room effects (Trick Room, Wonder Room), and turn counters.
- **Side Conditions**: Reflect, Light Screen, Aurora Veil, Tailwind, Hazards (Stealth Rock, Spikes, Toxic Spikes, Sticky Web), and screens.
- **Stat Boost Tracker**: Clear display of active stat stages (Atk, Def, SpA, SpD, Spe, Acc, Eva) for both sides.

### 5. Enriched Tooltips & Team Indicators
- **Move Tooltips**: In-depth move info during battle, including base power, accuracy, damage category (Physical/Special/Status), priority, contact flag, and type matchup multipliers.
- **Team Preview Counters**: Displays team compositions, remaining active/fainted Pokémon, and known status conditions.

### 6. Mod Menu & In-Game Configuration
- Fully configurable through **Mod Menu** and **Cloth Config** with options to toggle individual HUD components, adjust font scaling, enable compact mode, or enable debug snapshot logging.

---

## Requirements

| Requirement | Supported Version |
|---|---|
| **Minecraft** | `1.21.1` |
| **Fabric Loader** | `>= 0.16.0` (Recommended: `0.17.2+`) |
| **Fabric API** | `0.116.6+1.21.1` |
| **Fabric Language Kotlin** | `1.13.4+kotlin.2.2.0` |
| **Cobblemon** | `1.7.3+1.21.1` |
| **Java** | `Java 21` |
| *(Optional)* **Mod Menu** | `11.0.3+` |
| *(Optional)* **Cloth Config** | `15.0.140+` |

---

## Installation

1. Download and install **Fabric Loader** for Minecraft 1.21.1.
2. Download the required dependencies:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
   - [Cobblemon](https://modrinth.com/mod/cobblemon)
   - *(Optional)* [Mod Menu](https://modrinth.com/mod/modmenu)
   - *(Optional)* [Cloth Config](https://modrinth.com/mod/cloth-config)
3. Download the latest `deltacalc-x.x.x.jar` from [Releases](https://github.com/nickmatteo/DeltaCalc/releases) or [Modrinth](https://modrinth.com/mod/delta-calc).
4. Place all `.jar` files into your `.minecraft/mods` folder.
5. Launch Minecraft and join a Cobblemon world or server!

---

## Controls & Usage

- **Moving the Calc Panel**: Left-click and drag the panel header. Release to dock and save the new position.
- **Cycling Overrides**: During battle, left-click or right-click the opponent's **Item**, **Ability**, or **Spread** line to cycle through alternative options.
- **Configuring Settings**: Press `Escape` -> `Mods` -> `DeltaCalc` (via Mod Menu) or edit `.minecraft/config/cobblemonextendedbattleui.json`.

---

## Developer Guide

### Prerequisites
- JDK 21 (Temurin, Oracle, or OpenJDK)
- Git

### Building from Source

```bash
# Clone repository
git clone https://github.com/nickmatteo/DeltaCalc.git
cd DeltaCalc

# Build mod jar (output located in build/libs/deltacalc.jar)
./gradlew build

# Run Fabric test client in development environment
./gradlew runClient
```

### Architecture Overview

```
src/main/
├── kotlin/com/cobblemonextendedbattleui/
│   ├── CobblemonExtendedBattleUIClient.kt   # Client entrypoint & event listeners
│   ├── BattleStateTracker.kt                # Active battle tracking & event hooks
│   ├── PanelConfig.kt                       # Configuration persistence & defaults
│   ├── ModMenuIntegration.kt                # Cloth Config / Mod Menu GUI integration
│   ├── calc/
│   │   ├── DamageEngine.kt                  # Core damage formula & modifier pipeline
│   │   ├── CalcBattleSnapshot.kt            # Normalized immutable battle snapshot
│   │   └── BattleDatabase.kt                # Species base stats, typing, and spreads
│   ├── battle/                              # Field condition, weather, and boost tracking
│   ├── pokemon/                             # Form, aspect, ability, and item parsers
│   └── ui/                                  # Calc HUD, overlays, and tooltip renderers
└── resources/
    ├── fabric.mod.json                      # Mod metadata
    └── data/deltacalc/                      # Bundled databases (moves, stats, ladder usage)
```

### Updating Data & Compiling Databases

DeltaCalc bundles data for Pokémon species, Showdown move flags, and ranked usage tiers. Maintenance scripts are available in `tools/`:

```bash
# Rebuild move flags from Pokémon Showdown database
python tools/database/build_move_flags.py

# Rebuild battle database & Smogon fallback sets
python tools/database/build_battle_database.py

# Import new ranked ladder usage stats
python tools/usage-import/import_usage.py --season 6 --stage mid
```

---

## Credits & Attribution

- **Cobblemon**: Created and maintained by the [Cobblemon Team](https://cobblemon.com/).
- **Original Mod**: DeltaCalc builds upon and expands the foundational UI work of [CobblemonExtendedBattleUI](https://github.com/sveniik/CobblemonExtendedBattleUI) by **sveniik** (licensed under MIT).
- **Damage Calculations & Move Data**: Adapted from the mechanics and datasets of Pokémon Showdown and competitive community research.

---

## License

DeltaCalc is open-source software licensed under the [MIT License](LICENSE).
