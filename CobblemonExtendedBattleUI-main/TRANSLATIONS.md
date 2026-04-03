# Translations

## Creating Translation Resource Packs

You can translate the mod's UI into any language using a resource pack.

### Quick Start

1. Create a resource pack folder structure:
```
MyTranslationPack/
├── pack.mcmeta
└── assets/
    └── cobblemonextendedbattleui/
        └── lang/
            └── pt_br.json    (your language code)
```

2. Create `pack.mcmeta`:
```json
{
  "pack": {
    "pack_format": 34,
    "description": "Cobblemon Extended Battle UI - Portuguese Translation"
  }
}
```

3. Copy the English template below and translate the values (not the keys).

4. Place the folder in your `.minecraft/resourcepacks/` directory.

5. Enable the resource pack in Minecraft.

### Language Codes

Use Minecraft's language codes for the filename:
- `pt_br.json` - Portuguese (Brazil)
- `es_es.json` - Spanish (Spain)
- `fr_fr.json` - French
- `de_de.json` - German
- `it_it.json` - Italian
- `ja_jp.json` - Japanese
- etc...

### Translation Template

Copy this file and translate the values on the right side of each colon:

```json
{
  "category.cobblemonextendedbattleui": "Cobblemon Extended Battle UI",
  "key.cobblemonextendedbattleui.toggle_panel": "Toggle Battle Info Panel",
  "key.cobblemonextendedbattleui.increase_font": "Increase Panel Font Size",
  "key.cobblemonextendedbattleui.decrease_font": "Decrease Panel Font Size",

  "cobblemonextendedbattleui.config.title": "Cobblemon Extended Battle UI Settings",
  "cobblemonextendedbattleui.config.category.features": "Features",
  "cobblemonextendedbattleui.config.category.tooltipOptions": "Tooltip Options",

  "cobblemonextendedbattleui.config.enableTeamIndicators": "Team Indicators",
  "cobblemonextendedbattleui.config.enableTeamIndicators.tooltip": "Show Pokemon team indicators under health bars during battle",
  "cobblemonextendedbattleui.config.teamIndicatorRepositioning": "Team Indicator Repositioning",
  "cobblemonextendedbattleui.config.teamIndicatorRepositioning.tooltip": "Allow dragging team indicators to reposition them",
  "cobblemonextendedbattleui.config.enableBattleInfoPanel": "Battle Info Panel",
  "cobblemonextendedbattleui.config.enableBattleInfoPanel.tooltip": "Show panel with weather, terrain, field conditions, and stat stages",
  "cobblemonextendedbattleui.config.enableBattleLog": "Battle Log",
  "cobblemonextendedbattleui.config.enableBattleLog.tooltip": "Show custom battle log widget with damage percentages",
  "cobblemonextendedbattleui.config.enableMoveTooltips": "Move Tooltips",
  "cobblemonextendedbattleui.config.enableMoveTooltips.tooltip": "Show tooltips with type effectiveness when hovering over moves",
  "cobblemonextendedbattleui.config.showTeraType": "Show Tera Type",
  "cobblemonextendedbattleui.config.showTeraType.tooltip": "Display Tera Type in Pokemon tooltips when known",
  "cobblemonextendedbattleui.config.showStatRanges": "Show Stat Values",
  "cobblemonextendedbattleui.config.showStatRanges.tooltip": "Display Attack, Defense, Sp. Atk, and Sp. Def in Pokemon tooltips",
  "cobblemonextendedbattleui.config.showBaseCritRate": "Show Base Crit Rate",
  "cobblemonextendedbattleui.config.showBaseCritRate.tooltip": "Always show crit rate in move tooltips",

  "cobblemonextendedbattleui.stat.attack": "Attack",
  "cobblemonextendedbattleui.stat.attack.abbr": "Atk",
  "cobblemonextendedbattleui.stat.defense": "Defense",
  "cobblemonextendedbattleui.stat.defense.abbr": "Def",
  "cobblemonextendedbattleui.stat.special_attack": "Sp. Atk",
  "cobblemonextendedbattleui.stat.special_attack.abbr": "SpA",
  "cobblemonextendedbattleui.stat.special_defense": "Sp. Def",
  "cobblemonextendedbattleui.stat.special_defense.abbr": "SpD",
  "cobblemonextendedbattleui.stat.speed": "Speed",
  "cobblemonextendedbattleui.stat.speed.abbr": "Spe",
  "cobblemonextendedbattleui.stat.accuracy": "Accuracy",
  "cobblemonextendedbattleui.stat.accuracy.abbr": "Acc",
  "cobblemonextendedbattleui.stat.evasion": "Evasion",
  "cobblemonextendedbattleui.stat.evasion.abbr": "Eva",

  "cobblemonextendedbattleui.weather.rain": "Rain",
  "cobblemonextendedbattleui.weather.sun": "Harsh Sunlight",
  "cobblemonextendedbattleui.weather.sandstorm": "Sandstorm",
  "cobblemonextendedbattleui.weather.hail": "Hail",
  "cobblemonextendedbattleui.weather.snow": "Snow",

  "cobblemonextendedbattleui.terrain.electric": "Electric Terrain",
  "cobblemonextendedbattleui.terrain.grassy": "Grassy Terrain",
  "cobblemonextendedbattleui.terrain.misty": "Misty Terrain",
  "cobblemonextendedbattleui.terrain.psychic": "Psychic Terrain",

  "cobblemonextendedbattleui.field.trick_room": "Trick Room",
  "cobblemonextendedbattleui.field.gravity": "Gravity",
  "cobblemonextendedbattleui.field.magic_room": "Magic Room",
  "cobblemonextendedbattleui.field.wonder_room": "Wonder Room",

  "cobblemonextendedbattleui.side.reflect": "Reflect",
  "cobblemonextendedbattleui.side.light_screen": "Light Screen",
  "cobblemonextendedbattleui.side.aurora_veil": "Aurora Veil",
  "cobblemonextendedbattleui.side.tailwind": "Tailwind",
  "cobblemonextendedbattleui.side.safeguard": "Safeguard",
  "cobblemonextendedbattleui.side.lucky_chant": "Lucky Chant",
  "cobblemonextendedbattleui.side.mist": "Mist",
  "cobblemonextendedbattleui.side.stealth_rock": "Stealth Rock",
  "cobblemonextendedbattleui.side.spikes": "Spikes",
  "cobblemonextendedbattleui.side.toxic_spikes": "Toxic Spikes",
  "cobblemonextendedbattleui.side.sticky_web": "Sticky Web",

  "cobblemonextendedbattleui.volatile.leech_seed": "Leech Seed",
  "cobblemonextendedbattleui.volatile.confusion": "Confusion",
  "cobblemonextendedbattleui.volatile.taunt": "Taunt",
  "cobblemonextendedbattleui.volatile.encore": "Encore",
  "cobblemonextendedbattleui.volatile.disable": "Disable",
  "cobblemonextendedbattleui.volatile.torment": "Torment",
  "cobblemonextendedbattleui.volatile.infatuation": "Infatuation",
  "cobblemonextendedbattleui.volatile.perish_song": "Perish Song",
  "cobblemonextendedbattleui.volatile.drowsy": "Drowsy",
  "cobblemonextendedbattleui.volatile.curse": "Curse",
  "cobblemonextendedbattleui.volatile.nightmare": "Nightmare",
  "cobblemonextendedbattleui.volatile.bound": "Bound",
  "cobblemonextendedbattleui.volatile.trapped": "Trapped",
  "cobblemonextendedbattleui.volatile.substitute": "Substitute",
  "cobblemonextendedbattleui.volatile.aqua_ring": "Aqua Ring",
  "cobblemonextendedbattleui.volatile.ingrain": "Ingrain",
  "cobblemonextendedbattleui.volatile.focus_energy": "Focus Energy",
  "cobblemonextendedbattleui.volatile.magnet_rise": "Magnet Rise",
  "cobblemonextendedbattleui.volatile.embargo": "Embargo",
  "cobblemonextendedbattleui.volatile.heal_block": "Heal Block",
  "cobblemonextendedbattleui.volatile.destiny_bond": "Destiny Bond",
  "cobblemonextendedbattleui.volatile.flinch": "Flinch",

  "cobblemonextendedbattleui.ui.no_effects": "No effects",
  "cobblemonextendedbattleui.ui.none_active": "None active",
  "cobblemonextendedbattleui.ui.field": "FIELD",
  "cobblemonextendedbattleui.ui.side": "SIDE",
  "cobblemonextendedbattleui.ui.pokemon": "POKÉMON",
  "cobblemonextendedbattleui.ui.ally": "ALLY",
  "cobblemonextendedbattleui.ui.enemy": "ENEMY",
  "cobblemonextendedbattleui.ui.affected": "affected",
  "cobblemonextendedbattleui.ui.effect": "effect",
  "cobblemonextendedbattleui.ui.effects": "effects",
  "cobblemonextendedbattleui.ui.pokemon_count.singular": "Pokémon",
  "cobblemonextendedbattleui.ui.pokemon_count.plural": "Pokémon",

  "cobblemonextendedbattleui.popup.level": "Lv.%s",
  "cobblemonextendedbattleui.popup.hp": "HP: %s%%",
  "cobblemonextendedbattleui.popup.ko": "KO",
  "cobblemonextendedbattleui.popup.tera_prefix": "Tera ",
  "cobblemonextendedbattleui.popup.tera_label": "Tera: %s",

  "cobblemonextendedbattleui.item.knocked_off": "(off)",
  "cobblemonextendedbattleui.item.stolen": "(stolen)",
  "cobblemonextendedbattleui.item.swapped": "(swap)",
  "cobblemonextendedbattleui.item.consumed": "(used)",

  "cobblemonextendedbattleui.status.poisoned": "Poisoned",
  "cobblemonextendedbattleui.status.badly_poisoned": "Badly Poisoned",
  "cobblemonextendedbattleui.status.burned": "Burned",
  "cobblemonextendedbattleui.status.paralyzed": "Paralyzed",
  "cobblemonextendedbattleui.status.frozen": "Frozen",
  "cobblemonextendedbattleui.status.asleep": "Asleep",

  "cobblemonextendedbattleui.effectiveness.immune": "Immune (0x)",
  "cobblemonextendedbattleui.effectiveness.extremely_ineffective": "Extremely Ineffective (%sx)",
  "cobblemonextendedbattleui.effectiveness.not_effective": "Not effective (%sx)",
  "cobblemonextendedbattleui.effectiveness.not_very_effective": "Not very effective (%sx)",
  "cobblemonextendedbattleui.effectiveness.extremely_effective": "Extremely Effective! (%sx)",
  "cobblemonextendedbattleui.effectiveness.super_effective": "Super effective! (%sx)",
  "cobblemonextendedbattleui.effectiveness.neutral": "Normal damage (1x)",
  "cobblemonextendedbattleui.effectiveness.vs": "vs %1$s: %2$s"
}
```

### Tips

- You only need to include keys you want to change - missing keys fall back to English
- Keep abbreviations short (3-4 characters) so they fit in the UI
- Test your translation in-game to make sure text fits properly
- Share your translation with the community!
