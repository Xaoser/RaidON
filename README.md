# RaidON library :thinking:

### LIBRARY IN BETA!
If you found a bug, error or suggestion for improvement, plz write me about it in discord:
https://discord.gg/5NSxKrA8tN

Do you want zombie apocalypse?
Do you want village invasion?
Do you want 200 angry chickens with boss music?
Then yep, this library is for you.

## What RaidON can do
- load raids from `config/raidon/raids/*.json`
- support 2 config styles:
  `normal json system`
  `nbt_system`
- start raids from commands
- start raids from Java API
- support mob NBT
- support custom raid start actions, wave actions, and raid end actions
- show custom or default HUD
- load custom sounds from config folder
- register summon-items from config
- auto-generate recipes for summon-items
- provide an EPIC invasion for your ass!
- delete a обычный мусор 3D MAX ultra mega universe edition mob
- not bake a cookie(

## Read this first
NOTE:
You do not need to read the whole README in one painful session.

If you want to start fast, read Quick start.

If you are an advanced modpack creator, read NBT System.

If you are looking for visual or sound raid config, read Visual and Sound.

If you want to create your own mod with this library, read API Section.

## Quick start
You want a quick start using this mod, okay. Below is an example raid config.
Copy this json and paste it into:

`your_minecraft/config/raidon/raids/`

If you already started the game at least once, this path should already contain an example raid config.
You can edit that file or create a new one.

TIP:
When you change something in a json file, you do not have to restart the game.
Instead, use the command `/raidon reload`.

```json
{
  "name": "Example",
  "id": "raidon:example_raid",
  "difficulty": 2,
  "start": {
    "event": "on_kill",
    "entity": "minecraft:zombie",
    "count": "4",
    "conditions": [
      { "type": "min_players", "value": 1 }
    ]
  },

  "on_raid_start": [
    { "type": "title", "text": "&1Raid started!" },
    { "type": "effect", "effect": "minecraft:resistance", "duration": 200, "amplifier": 0 },
    { "type": "sound", "sound": "minecraft:entity.wither.spawn", "volume": 1.5, "pitch": 1.0 }
  ],
  "points": {
    "mainpoint": {"x": 0, "y": 70, "z": 0},
    "raidspawnpoint": {"x": 64, "y": 70, "z": 64},
    "raidpoint": {"x": 0, "y": 70, "z": 0}
  },
  "gui": {
    "tone": "green"
  },
  "drops": { "global": [
    { "item": "minecraft:emerald", "min": 1, "max": 100, "chance": 100 },
    { "item": "minecraft:iron_nugget", "min": 1, "max": 3, "chance": 25 }
  ]
  },
  "spawn": { "min_radius": 18, "max_radius": 60, "attempts_per_mob": 12, "require_ground": true, "avoid_water": true  },
  "waves": [
    {"mobs": [
      {"type": "minecraft:zombie",
        "count": 40,
        "ai": "aggressive",
        "damage": 3.0,
        "targets": {
          "whitelist": {"attack": ["all", "minecraft:zombie", "minecraft:player"], "ignore": ["minecraft:cow"]},
          "blacklist": {"attack": ["none"], "ignore": "none"}
        },
        "drops": [
          { "item": "minecraft:leather", "min": 0, "max": 1, "chance": 100 }
        ]
      },
      {"type": "minecraft:zombie",
        "count": 0,
        "ai": "aggressive",
        "damage": 3.0,
        "drops": [
          { "item": "minecraft:leather", "min": 0, "max": 1, "chance": 100 }
        ]
      }
    ],
      "complete": {"type": "all_dead" },
      "on_end": [
        { "type": "broadcast", "text": "Волна 1 отбита." }
      ]
    },
    {"mobs": [
      { "type": "minecraft:zombie",
        "count": 6,
        "ai": "aggressive",
        "damage": 3.0
      },
      {"type": "minecraft:zombie",
        "count": 2,
        "ai": "hostile",
        "damage": 3.0
      }
    ],
      "complete": { "type": "all_dead" }
    }
  ],
  "on_raid_end": [
    { "type": "broadcast", "text": "congratulation!" }
  ]
}
```

This raid config is simple and does not show most available features.
If you want more advanced options, continue reading.

## How RaidON is structured
Every raid necessarily has:
`name`, `id`, `difficulty`, `start trigger`, `mainpoint`, `raidspawnpoint`, `raidpoint`, one or more waves, and `global_drops`.

A raid can also have optional things like:
`gui`, start or end actions, and `nbt system`.

Every wave necessarily has:
`mobs`

Optional wave fields:
`on_start`, `on_end`

Every mob entry has:
`mob type`, `count`, `ai`, `damage`

Optional mob fields:
`drops`, `blacklists`, `whitelists`, `traits`, `nbt`

NOTE:
Everything about drops is described in Drops.

## NBT System
Use this if you want NBT system.
Enable it with:

```json
"nbt_system": true
```

Writing `"true"` as string also works.

In NBT mode you can use:
`start_nbt`, `on_raid_start_nbt`, `on_start_nbt`, `on_end_nbt`, `on_raid_end_nbt`, and mob `"nbt"` like this:

```json
"nbt": "{Health:40.0f,CanPickUpLoot:1b,CustomName:'{\"text\":\"KFC BOSS\",\"color\":\"red\"}',CustomNameVisible:1b,ActiveEffects:[{Id:1b,Amplifier:1b,Duration:1200}],Attributes:[{Name:\"minecraft:generic.armor\",Base:0.0d},{Name:\"minecraft:generic.attack_damage\",Base:999.0d}]}"
```

This mode supports:
- exact SNBT like `/summon`
- `.snbt` files
- JSON object to NBT conversion
- JSON array to NBT list conversion
- NBT actions for raid start, wave start, wave end, and raid end
- relaxed simple strings in SNBT

Raw string example:

```json
"nbt": "{Health:40.0f,CanPickUpLoot:1b,CustomName:'{\"text\":\"Boss Zombie\",\"color\":\"red\"}',CustomNameVisible:1b}"
```

Big file example:

```json
"nbt": "@file:bosses/raid_archer.snbt"
```

The search order for `@file` is simple.
RaidON first checks near the current raid json, then `config/raidon/nbt/`, and absolute path also works.

JSON object mode example:

```json
"nbt": {
  "Health": "40.0f",
  "CanPickUpLoot": "1b",
  "PersistenceRequired": true,
  "CustomName": { "text": "Boss Zombie", "color": "red" },
  "CustomNameVisible": true,
  "ActiveEffects": [
    { "Id": "1b", "Amplifier": "1b", "Duration": 1200 }
  ]
}
```

## Points and raid area
`points` is one of the most important parts of the raid config.
If these points are wrong, the raid can still work, but mob behavior may become strange and unpredictable.

```json
"points": {
  "mainpoint": {"x": 0, "y": 70, "z": 0},
  "raidspawnpoint": {"x": 64, "y": 70, "z": 64},
  "raidpoint": {"x": 0, "y": 70, "z": 0},
  "mob_wander_radius": 24
}
```

`mainpoint` is the main center of the raid.
`raidspawnpoint` is the point from which the wave spawn circle is calculated.
`raidpoint` is the point where mobs try to gather and return.
`mob_wander_radius` defines the soft gathering zone around `raidpoint`.

For `mob_wander_radius` you can also use aliases:
`gather_zone_radius` or `collection_zone_radius`.

IMPORTANT:
Raid mobs have hard return radius (`gather_zone_radius + 30`).
When mobs cross this zone, they hard-return into `collection_zone_radius`.

## Spawn settings
Example:

```json
"spawn": {
  "min_radius": 18,
  "max_radius": 60,
  "attempts_per_mob": 12,
  "require_ground": true,
  "avoid_water": true
}
```

`min_radius` defines how close mobs are allowed to spawn to the center.
`max_radius` defines the maximum spawn radius.
`attempts_per_mob` controls how many spawn attempts are made for each mob before RaidON gives up for that tick.
`require_ground` tells the system to try spawning mobs on ground.
`avoid_water` tells the system to avoid water whenever possible.

Wave mobs do not spawn in a perfect ring anymore.
They are placed at random positions inside the spawn circle, so the raid looks more natural and less like a school line.

## Start triggers
Available triggers:
`manual`, `enter_area`, `player_join_any`, `player_join_singleplayer`, `night_fall`, `on_kill`, `on_item_pickup`, `on_trade`, `on_dimension_change`, `on_respawn`, `on_enter_biome`, `on_day`, `on_sunset`, `on_midnight`, `on_structure_visit`.

Example:

```json
"start": {
  "event": "on_kill",
  "entity": "minecraft:zombie",
  "count": 10,
  "cooldown_ticks": 24000
}
```

The `event` field, or its alias `type`, defines the trigger name.
`entity` is used as a filter for entity-based triggers.
`item` is used for pickup or trade triggers.
`structure` is used for structure-based triggers.
`count` defines how many matching events are required.
`cooldown_ticks` defines the delay between automatic starts.
`radius` is used by area and structure triggers.
`center` tells RaidON how the raid center should be resolved.
`value` is kept as a legacy numeric alias.

Supported start conditions:
`min_players`, `max_players`, `y_between`, `in_biome`, `in_dimension`, `time_of_day`, `moon_phase`.

Structure-based example:

```json
"start": {
  "event": "enter_area",
  "radius": 48,
  "center": {
    "type": "structure",
    "structure": "minecraft:village_plains",
    "search_radius": 1024,
    "prefer_nearest": true
  },
  "conditions": [
    { "type": "time_of_day", "min": 13000, "max": 23000 },
    { "type": "min_players", "value": 1 }
  ]
}
```

RaidON supports three center types:
- `event` uses the trigger position or player position
- `spawn` uses the world spawn
- `structure` uses the center of the located structure

## Waves and mobs
Wave example:

```json
"waves": [
  {
    "spawn_radius": 20,
    "mobs": [
      {
        "type": "minecraft:zombie",
        "count": 30,
        "ai": "hostile",
        "damage": 4.0
      },
      {
        "type": "minecraft:skeleton",
        "count": 8,
        "ai": "hostile"
      }
    ],
    "on_start": [
      { "type": "subtitle", "text": "&6Wave 1" }
    ],
    "on_end": [
      { "type": "broadcast", "text": "&aWave finished" }
    ]
  }
]
```

Every mob entry can use fields such as:
`type`, `count`, `ai`, `damage`, `targets`, `drops`, `traits`, and `nbt`.

The `ai` field supports:
`hostile`, `aggressive`, and `neutral`.

In practice, raid hostile mobs use RaidON combat logic first.
Vanilla behavior only gets a chance when the custom logic has nothing to do.
Raid mobs also ignore other raid mobs as valid targets.

## Target logic
Example:

```json
"targets": {
  "whitelist": {
    "attack": ["all", "minecraft:player"],
    "ignore": ["minecraft:cow"]
  },
  "blacklist": {
    "attack": ["none"],
    "ignore": "none"
  }
}
```

The idea is simple.
`attack` defines what the mob is allowed to attack, while `ignore` defines what the mob must ignore.
Players still have the highest priority if they are visible.

## Mob traits
You can tune mob behavior with `traits`.

```json
"traits": {
  "burn_in_sun": false,
  "can_drown": false,
  "knockback_resistance": 0.8,
  "movement_speed_multiplier": 1.0,
  "ai_speed_multiplier": 1.0,
  "hard_leash_multiplier": 1.75,
  "raid_ai_enabled": true
}
```

Supported trait fields include:
`burn_in_sun`, `can_drown`, `knockback_resistance`, `movement_speed_multiplier`, `ai_speed_multiplier`, `hard_leash_multiplier`, and `raid_ai_enabled`.

If `raid_ai_enabled` is set to `false`, the built-in raid AI is fully ignored.
This is useful when you want full control from your own mod code.

## Drops
Drop example:

```json
"drops": {
  "global": [
    { "item": "minecraft:emerald", "min": 1, "max": 3, "chance": 25 }
  ]
}
```

Chance logic works like this:
`100` means always, `50` means 50%, and `0.25` means 0.25%.

This system works for both global raid drops and local mob drops.

## Events
You can run events on:
`on_raid_start`, `on_start`, `on_end`, and `on_raid_end`.

NBT versions of these are:
`on_raid_start_nbt`, `on_start_nbt`, `on_end_nbt`, and `on_raid_end_nbt`.

Available events:
`broadcast`, `chat`, `actionbar`, `subtitle`, `title`, `summon`, `command`, `set_time`, `lightning`, `effect`, `sound`, `loop_sound`, `raid_sound`, and `music`.

Example:

```json
"on_raid_start": [
  { "type": "chat", "text": "&cRaid started!" },
  { "type": "title", "text": "&6&lRaid started!", "fade_in": 10, "stay": 70, "fade_out": 20 },
  { "type": "sound", "sound": "minecraft:entity.wither.spawn", "volume": 1.5, "pitch": 1.0 },
  { "type": "loop_sound", "sound": "minecraft:music_disc.13", "sound_source": "music", "repeat_ticks": 240 }
]
```

## Visual and Sound
Normal sound example:

```json
{ "type": "sound", "sound": "minecraft:block.bell.use" }
{ "type": "sound", "sound": "minecraft:entity.wither.spawn", "sound_source": "hostile", "volume": 1.5, "pitch": 0.8 }
{ "type": "loop_sound", "sound": "minecraft:music_disc.13", "sound_source": "music", "repeat_ticks": 240 }
```

The `sound` action plays once.
`loop_sound`, `raid_sound`, and `music` keep replaying while the raid is active.
If you want ending sounds, they should still go inside `on_raid_end`.

## Custom sounds and item textures from config
RaidON automatically loads client resources from:

`config/raidon/resources/assets/raidon_cfg/`

This folder is created automatically on client start.

Minimal example:

```
config/raidon/resources/
└── assets/
    └── raidon_cfg/
        ├── sounds/
        │   ├── raid_start.ogg
        │   └── music/
        │       └── raid_loop.ogg
        └── textures/
            └── item/
                └── raid_horn.png
```

### Raid sounds
Place `.ogg` files here: `config/raidon/resources/assets/raidon_cfg/sounds/`

Example:

```
config/raidon/resources/assets/raidon_cfg/sounds/raid_start.ogg
config/raidon/resources/assets/raidon_cfg/sounds/music/raid_loop.ogg
```

Then in raid config you can use short id:

```json
{ "type": "sound", "sound": "raid_start", "sound_source": "master" }
{ "type": "loop_sound", "sound": "music/raid_loop", "sound_source": "music", "repeat_ticks": 1200 }
```

Or full resource location:

```json
{ "type": "sound", "sound": "raidon_cfg:raid_start" }
{ "type": "loop_sound", "sound": "raidon_cfg:music/raid_loop" }
```

RaidON auto-generates `assets/raidon_cfg/sounds.json` from `.ogg` files inside this folder.

If you need advanced options for one sound file, place a sidecar file near it:

```text
raid_loop.ogg
raid_loop.sound.json
```

Example `raid_loop.sound.json`:

```json
{
  "stream": true,
  "subtitle": "Raid loop",
  "replace": false
}
```

Supported sidecar fields:
`id`, `stream`, `preload`, `volume`, `pitch`, `weight`, `attenuation_distance`, `subtitle`, `replace`.

>[!TIP]\
>After changing `.ogg` or `sounds.json`, do `F3+T` or restart client.

### Item textures for json-items
Json-items use the same resource folder.

If `texture` uses full path like:

```json
"texture": "minecraft:item/goat_horn"
```

then RaidON uses the existing texture directly.

If `texture` uses short path like:

```json
"texture": "my_custom_horn"
```

then RaidON resolves it as: `raidon:item/my_custom_horn`

So PNG must be placed here: `config/raidon/resources/assets/raidon_cfg/textures/item/my_custom_horn.png`

## Json Items
RaidON can load special summon-items from: `config/raidon/items/*.json`

Each json defines one real in-game item.

Example:

```json
{
  "id": "example_raid_horn",
  "raid": "raidon:example_raid",
  "display_name": "Example Raid Horn",
  "description": [
    "Starts example raid at clicked position or near player.",
    "This description will be shown in the item tooltip."
  ],
  "texture": "minecraft:item/goat_horn",
  "creative_tabs": ["tools_and_utilities", "combat"],
  "consume": false,
  "use_duration_ticks": 32,
  "use_animation": "toot_horn",
  "cooldown_ticks": 200,
  "max_stack_size": 1,
  "rarity": "rare",
  "glint": true,
  "recipes": [
    {
      "name": "crafting",
      "type": "crafting_shaped",
      "category": "equipment",
      "pattern": [
        " EI",
        " HE",
        "I  "
      ],
      "key": {
        "E": { "item": "minecraft:emerald" },
        "H": { "item": "minecraft:goat_horn" },
        "I": { "tag": "c:ingots/iron" }
      }
    }
  ]
}
```

## Supported summon-item fields
- `id`
  Item id. If namespace is missing, RaidON uses `raidon`.

- `raid`
  Raid id that this item should start.

- `display_name`
  Visible item name.

- `description`
  Human-readable description.
  Supports string or array of strings.
  These lines go into tooltip.

- `tooltip`
  Extra tooltip lines.
  Also supports string or array of strings.

- `texture`
  Item texture path.

- `creative_tabs`
  Array of creative inventory tabs where this item should appear.
  You can use `none` if you do not want the item in creative tabs.

- `consume`
  If `true`, the item is consumed after successful raid start.

- `use_duration_ticks`
  Hold-to-use duration in ticks.
  `0` keeps instant-use behavior.

- `use_animation`
  Arm animation during long use.
  Supported values include:
  `none`, `eat`, `drink`, `block`, `bow`, `spear`, `crossbow`, `spyglass`, `toot_horn`, `brush`.

- `cooldown_ticks`
  Cooldown after use.

- `max_stack_size`
  Max stack size.

- `rarity`
  `common`, `uncommon`, `rare`, `epic`.

- `glint`
  If `true`, item has enchant glow.

- `recipes`
  Array of recipe definitions for this item.

## Description and tooltip
If you just want a clean readable hover text, use `description`.

Example with single line:

```json
"description": "Starts night raid at clicked position."
```

Example with multiple lines:

```json
"description": [
  "Starts night raid.",
  "Works only if target zone is free."
]
```

If needed, `description` and `tooltip` can be used together.
Their lines will be merged into one tooltip.

## Creative tabs
Supported vanilla aliases:`tools_and_utilities`, `combat`, `ingredients`, `food_and_drinks`, `spawn_eggs`, `building_blocks`, `colored_blocks`, `natural_blocks`, `functional_blocks`, `redstone_blocks`.

You can also use full `ResourceLocation` for modded tabs.

## Recipes
### General idea
The `type` field defines which recipe station or recipe kind is used.

Supported values:`crafting_shaped`, `crafting_shapeless`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing_transform`

Convenient aliases are also supported:
- `workbench`, `crafting_table` -> `crafting_shaped`
- `furnace` -> `smelting`
- `blast_furnace` -> `blasting`
- `smoker` -> `smoking`
- `campfire` -> `campfire_cooking`
- `stonecutter` -> `stonecutting`
- `smithing` -> `smithing_transform`

> [!TIP]\
> If you want to create your own recipe using craftweaker or any of these mods, you don't have to write the recipe in a json-item.
### Ingredients
Ingredient can be written as short item id:

```json
"minecraft:emerald"
```

As tag:

```json
"#c:ingots/iron"
```

Or in explicit form:

```json
{ "item": "minecraft:emerald" }
{ "tag": "c:ingots/iron" }
```

### Shaped recipe
```json
{
  "type": "crafting_shaped",
  "pattern": [
    " EI",
    " HE",
    "I  "
  ],
  "key": {
    "E": { "item": "minecraft:emerald" },
    "H": { "item": "minecraft:goat_horn" },
    "I": { "tag": "c:ingots/iron" }
  }
}
```

### Shapeless recipe
```json
{
  "type": "crafting_shapeless",
  "ingredients": [
    "minecraft:goat_horn",
    "minecraft:emerald",
    "#c:ingots/iron"
  ]
}
```

### Furnace / blast furnace / smoker / campfire
```json
{
  "type": "smelting",
  "ingredient": "minecraft:goat_horn",
  "experience": 0.5,
  "cooking_time": 200
}
```

### Stonecutter
```json
{
  "type": "stonecutting",
  "ingredient": "minecraft:goat_horn"
}
```

### Smithing table
```json
{
  "type": "smithing_transform",
  "template": "minecraft:netherite_upgrade_smithing_template",
  "base": "minecraft:goat_horn",
  "addition": "minecraft:emerald"
}
```

## HUD and GUI
RaidON has 2 HUD modes.

### 1. Built-in HUD
If you do not load custom textures, RaidON uses the built-in HUD.

Important thing now:
- built-in HUD size is fixed in JSON configs
- `gui.size` is not used anymore
- this was done on purpose, so default HUD does not become random monster on screen

Built-in HUD config:

```json
"gui": {
  "tone": "blue"
}
```

Supported `tone` values are:
`blue`, `green`, `purple`, `gold`, and `gray`.

If `tone` is missing, the default red/brown style is used.

### 2. Custom bar textures
If you set custom textures, RaidON hides only the default black background.
Text and progress still stay visible.

Texture keys are:
`progress_empty` and `progress_full`

Legacy aliases:
`main` and `progress`

Path examples:
`raidon:gui/file.png` and `raidon/gui/file.png`

Config example:

```json
"gui": {
  "progress_empty": "mymod:gui/raid_bar_empty.png",
  "progress_full": "mymod:gui/raid_bar_full.png"
}
```

If custom textures are loaded, `tone` does nothing.
`tone` works only for the built-in HUD.

JSON config no longer controls HUD size.
For config-based custom textures, use fixed bar size `120x12`.
If you register the raid from Java API, you can still set custom bar size there.

## Commands
Available commands:
- `/raidon start <id> [x y z]`
- `/raidon stop <id>`
- `/raidon reload`
- `/raidon activeraids`

## API Section
Public API includes:
- `ru.xaoser.raidon.api.RaidonApi`
- `ru.xaoser.raidon.api.RaidRegistration`
- `ru.xaoser.raidon.api.RaidBuilder`
- `ru.xaoser.raidon.api.WaveBuilder`
- `ru.xaoser.raidon.api.RaidGuiBuilder`

### Build raid from code
```java
ResourceLocation raidId = new ResourceLocation("mymod", "library_raid");

Raid raid = new RaidBuilder(raidId)
        .name("Library Raid")
        .difficulty(3.0F)
        .addWave(w -> w
                .mob(10, EntityType.ZOMBIE, SpawnBehavior.HOSTILE, null, List.of(), MobTargeting.defaults(),
                        new MobTraits(false, false, 0.6D, null, null, null, true))
                .completeWhenAllDead())
        .build();
```

### Register raid from code
```java
RaidGuiSettings gui = RaidGuiBuilder.create()
        .progressTextures(
                new ResourceLocation("mymod", "gui/raid_bar_empty.png"),
                new ResourceLocation("mymod", "gui/raid_bar_full.png")
        )
        .size(180, 18)
        .build();

RaidRegistration registration = new RaidRegistration(
        raid,
        new RaidSpawnSettings(18, 60, 12, true, true),
        RaidPointSettings.DEFAULT,
        gui,
        RaidStartSettings.DEFAULT
);

RaidonApi.registerRaid(registration);
```

Important thing:
- config JSON no longer controls HUD size
- Java API still can control custom bar size with `RaidGuiBuilder.size(...)`

### Start and stop from code
```java
RaidonApi.startRaid(raidId, serverLevel, centerPos);
RaidonApi.stopRaid(raidId);
```

TIP:
- use `name` for raid name
- register raids in server lifecycle
- use unique id for every raid
- if you want default GUI, just do not set custom texture
- if you want fully own mob brain, disable built-in raid AI
- if something explodes, remember:
  this is beta, brother :feelsgood:
