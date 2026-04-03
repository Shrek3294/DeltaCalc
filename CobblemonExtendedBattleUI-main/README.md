# Cobblemon Extended Battle UI

A client-side Fabric mod that adds helpful information displays to Cobblemon battles, as well as overhauling the default Battle Log.

## What does it do?

During Cobblemon battles, this mod shows you information that's normally hard to track:

![Full Showcase](https://github.com/user-attachments/assets/cb8c27df-966d-4ce2-9db4-5ba714921f64)

### Battle Info Panel

A panel on your screen that displays:

- **Weather & Terrain** - Shows active weather (rain, sun, sandstorm, etc.) and terrain effects with remaining turn counters
- **Field Effects** - Trick Room, Gravity, and other field-wide conditions
- **Your Side's Effects** - Screens (Reflect, Light Screen), hazards on your side, Tailwind, etc.
- **Enemy's Effects** - Same as above, but for the opponent
- **Stat Changes** - Shows every Pokemon's stat boosts and drops with easy-to-read arrows
- **Volatile Effects** - Taunt, Encore, Perish Song, Leech Seed, and other per-Pokemon effects

The panel shows turn ranges like "5-8" when we don't know if the opponent has items that extend duration (like Light Clay for screens or weather rocks).

![Battle Info Panel](https://github.com/user-attachments/assets/64772db2-5628-40c9-9614-acc1c691b274)

This panel is also fully resizable, moveable, and collapsible if you only want to see a quick glance of relevant information!

![Collapsed Panel](https://github.com/user-attachments/assets/5d34fb73-f0e7-405f-ab55-f2767e8e9baa)

### Battle Log

We have also implemented a custom battle log that replaces Cobblemon's default log:

- **Damage Percentages** - Shows how much damage each attack dealt (e.g., "→ 35% to Clefable")
- **Healing Tracking** - Healing shown in green (e.g., "→ +15% to Charizard")
- **Color-Coded Messages** - Different colors for moves, HP changes, effects, and field conditions
- **Turn Separators** - Clear visual separation between turns to improve readability
- **Auto-Scroll** - Follows new messages automatically, stops when you scroll up manually

The battle log uses Cobblemon's native textures and is fully resizable, moveable, and has adjustable font size.

![Battle Log](https://cdn.modrinth.com/data/cached_images/ae64946a56c0acbefa825fda45b51fe8f9e0cf02.png)

### Team Indicators

Pokemon model indicators below each team's health bars showing:

- **Visual Models** - Each Pokemon displayed as a mini 3D model
- **Hover Tooltips** - Hover over any Pokemon to see detailed information
- **Your Pokemon** - Shows all moves and held items
- **Opponent Pokemon** - Shows only revealed pokemon, moves, items, stat changes, and effects
- **Status Tracking** - Status conditions shown via color tinting on models
- **KO Tracking** - Fainted Pokemon appear grayed out

![Team Indicators](https://github.com/user-attachments/assets/904daa33-86bb-4225-b2b1-9c4f49e6ef2b)

The data in the tooltips will only display what you know with certainty for an opponent, and will update as more information is revealed. For example, in the following screenshot the opposing Porygon-Z has used Shadow Ball once, and we could see the recoil from Life Orb, but we have no idea what it's Ability, remaining moves, or Speed is exactly:

<img width="636" height="713" alt="Screenshot 2026-02-07 161138" src="https://github.com/user-attachments/assets/0de5ea49-6138-47c6-a316-76c6b774c188" />


### Move Tooltips

When selecting a move in battle, hover over any move to see:

- **Type Effectiveness** - Shows whether the move is super effective, not very effective, or has no effect against the opponent
- **Move Stats** - Power, accuracy, PP remaining
- **Move Category** - Physical, special, or status

![Move Tooltip](https://github.com/user-attachments/assets/848c6817-763e-4743-9035-0e576f9a3aaf)

This feature can be disabled if you prefer to use [Cobblemon Move Inspector](https://modrinth.com/mod/cobblemon-move-inspector) instead.

### What this mod does NOT do

This mod does not give you any information that you could not otherwise have obtained. Opposition team size, team members, stats, moves, etc... are not and never will be in scope for this mod. This mod is intended to only give any information that you could get via common knowledge, note taking, or reading the match log in a quick and easy way as a QOL upgrade.

## Controls

### Battle Info Panel & Battle Log
- **Drag the header** - Move the panel anywhere on screen
- **Ctrl + Scroll or [ and ]** - Adjust font size (while hovering over the panel)
- **Drag sides or corners** - Resize panel

### Battle Info
- **V or Click header** - Show/hide the detailed panel

### Battle Log
- **Click bottom-right arrow** - Expand/collapse the log

### Team Indicators
- **Hover** - Show detailed tooltip with moves, items, stats, and effects
- **Ctrl + Scroll or [ and ]** - Adjust tooltip font size (while hovering)

You can rebind keys in the Minecraft keybind settings menu.

Your preferences for panel/log size, position, and font sizes are saved automatically.

## Configuration

If you have [Mod Menu](https://modrinth.com/mod/modmenu) installed, you can configure features directly in-game via the mod settings screen.

Alternatively, you can edit the config file manually:

**Config file location:** `.minecraft/config/cobblemonextendedbattleui.json`

When a feature is disabled, its associated background tracking is also disabled to save resources. Note that stat change tracking is shared between the Battle Info Panel and Team Indicator tooltips - it remains active if either feature is enabled.

## Translations

The mod works in any language, but the UI elements are only configured to show in English by default.

Want to translate the mod's UI text? See the [Translation Guide](https://github.com/sveniik/CobblemonExtendedBattleUI/blob/main/TRANSLATIONS.md) for instructions on creating a translation resource pack.

**Community Translations:**

If you've created a translation, [open an issue](https://github.com/sveniik/CobblemonExtendedBattleUI/issues) to share it and I'll link it here!

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.0+
- Fabric API
- Fabric Language Kotlin
- Cobblemon 1.7.0+

## Known Issues
There is a known incompatability with the current version of [Cobblemon UI Tweaks](https://modrinth.com/mod/cobblemon-ui-tweaks).
This is not something that I am going to fix, as most of the features in that mod seem to be obsolete as of version 1.7 
of Cobblemon or something that this mod already handles, like improving on the user-friendliness of the battle log. See
more details [here](https://github.com/sveniik/CobblemonExtendedBattleUI/issues/13) and [here](https://github.com/sveniik/CobblemonExtendedBattleUI/issues/23).

## Notes

- This is a **client-side only** mod - it works in singleplayer and on servers without the server needing it
- The opponent's full team isn't shown until they send out each Pokemon. This is intended.
- Weather/terrain/screen durations show ranges because we can't know if the opponent has duration-extending items
- When spectating, some information - moves, items, stat changes - are only shown when revealed after you started
watching

## License

MIT
