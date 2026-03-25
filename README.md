# RaidON library :thinking:

### LIBRARY IN BETA!
If you found a bug, error or suggestion for improvement, plz write me about it in [discord](https://discord.gg/5NSxKrA8tN)

Do you want zombie apocalypse?\
Do you want village invasion?\
Do you want 200 angry chickens with **boss** music?\
Then yep, this library for you.

## What RaidON can do
- load raids from `config/raidon/raids/*.json`
- support 2 config styles:
  `normal json system`
  `nbt_system`
- start raids from commands
- start raids from Java API
- support mob NBT
- support custom raid start actions, wave actions, raid end actions
- show custom or default HUD
- load custom sounds from config folder
- provide a **EPIC** invasion for your ass!
- delete a обычный мусор 3D MAX ultra mega universe edition mob
- not bake a cookie(

## Read this first
> [!NOTE]\
> You do not need read whole README in one pain session.
>
### If you want start fast, read [Quick start](#Quick-start).

### If you advanced modpack creator or some of this peoples, you can read [NBT System](#NBT-System)

### Seeing for visual or sound raid config? read this page: [Visual and Sound](#Visual-and-Sound)

### If you want create your own mod with this library, then read a [API Section](#API-Section).

## Quick start
You want a quick start using this mod, okay, bellow is example config of raid, you must copy this **_json_** and past in path `your_minecraft/config/raidon/raids/`.\
if you already run your game, in this path you already have example raid config, you can configure this file or create new.\
> [!TIP]\
> When you changed something in json file, you don't have to restart the game, instead you can write the command `/raidon restart`


```json
{
  "Name": "Example Raid",
  "id": "raidon:example_raid",
  "difficulty": 5,
  "start": {
    "event": "on_kill",
    "entity": "minecraft:zombie",
    "count": 10,
    "cooldown_ticks": 200
  },
  "points": {
    "mainpoint": {"x": 0, "y": 70, "z": 0},
    "raidspawnpoint": {"x": 64, "y": 70, "z": 64},
    "raidpoint": {"x": 0, "y": 70, "z": 0},
    "mob_wander_radius": 24
  },
  "gui": {
    "tone": "blue"
  },
  "waves": [
    {
      "mobs": [
        {
          "type": "minecraft:zombie",
          "count": 20,
          "ai": "hostile",
          "damage": 3.0
        }
      ],
      "on_start": [
        { "type": "title", "text": "&6&lZombie Raid" },
        { "type": "sound", "sound": "minecraft:block.bell.use" }
      ],
      "on_end": [
        { "type": "broadcast", "text": "&aWave finished" }
      ]
    }
  ],
  "on_raid_end": [
    { "type": "broadcast", "text": "&6Raid is over" }
  ]
}
```
This raid config is simple and don't show you a most things, if you want more advanced options, continue reading.

## How RaidON is structured
Every raid necessarily has: `Name`, `id`, `difficulty`, `start trigger`, `mainpoint, spawnpoint, raidpoint`, `one or more waves`, `global_drops`.\
Also Raid has optional things like: `gui`, `start or end actions`, `nbt system`.

Every wave necessarily has: `mobs` and optional `on_start`, `on_end` more about it you can read in [Events](Events)

Every mob entry has: `mob type`, `count`, `ai`, `damage` also mobs has a optional things like: `drops`, `black and white lists`, `traits`.

> [!NOTE]\
> Everything about drops you can found in [Drops](#Drops)

## NBT System
Use this if you want NBT system, you can enable this system with:
```json
"nbt_system": true
```
Writing`"true"` also works.

In NBT mode you can use:`start_nbt`, `on_raid_start_nbt`, `on_start_nbt`, `on_end_nbt`, `on_raid_end_nbt`, mob `"nbt"` like:
>"nbt": "{Health:40.0f,CanPickUpLoot:1b,CustomName:'{\"text\":\"KFC BOSS\",\"color\":\"red\"}',CustomNameVisible:1b,ActiveEffects:[{Id:1b,Amplifier:1b,Duration:1200}],Attributes:[{Name:\"minecraft:generic.armor\",Base:0.0d},{Name:\"minecraft:generic.attack_damage\",Base:999.0d}]}"
This mode supports exact SNBT like `/summon`, `.snbt` files, JSON object to NBT conversion, JSON array to NBT list conversion, NBT actions for raid start, wave start, wave end, and raid end, and also relaxed simple strings in SNBT.

Raw string example:

```json
"nbt": "{Health:40.0f,CanPickUpLoot:1b,CustomName:'{\"text\":\"Boss Zombie\",\"color\":\"red\"}',CustomNameVisible:1b}"
```

Big file example:**

```json
"nbt": "@file:bosses/raid_archer.snbt"
```

The search order for `@file` is simple. RaidON first checks near the current raid json, then `config/raidon/nbt/`, and absolute path also works.

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

`mainpoint` is the main center of the raid.\
`raidspawnpoint` is the point from which the wave spawn circle is calculated.\
`raidpoint` is the point where mobs try to gather and return.\
`mob_wander_radius` defines the soft gathering zone around `raidpoint`.

For `mob_wander_radius` you can also use aliases `gather_zone_radius` or `collection_zone_radius`.

> [!IMPORTANT]\
> Raid mobs has hard return radius (gather_zone_radius+30), when mobs cross this zone, they are hard returning into collection_zone_radius

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

`min_radius` defines how close mobs are allowed to spawn to the center. `max_radius` defines the maximum spawn radius. `attempts_per_mob` controls how many spawn attempts are made for each mob before RaidON gives up for that tick. `require_ground` tells the system to try spawning mobs on ground. `avoid_water` tells the system to avoid water whenever possible.

Wave mobs do not spawn in a perfect ring anymore.
They are placed at random positions inside the spawn circle, so the raid looks more natural and less like a school line.

## Start triggers
Available triggers are `manual`, `enter_area`, `player_join_any`, `player_join_singleplayer`, `night_fall`, `on_kill`, `on_item_pickup`, `on_trade`, `on_dimension_change`, `on_respawn`, `on_enter_biome`, `on_day`, `on_sunset`, `on_midnight`, `on_structure_visit`.

Example:

```json
"start": {
  "event": "on_kill",
  "entity": "minecraft:zombie",
  "count": 10,
  "cooldown_ticks": 24000
}
```

The `event` field, or its alias `type`, defines the trigger name. `entity` is used as a filter for entity-based triggers. `item` is used for pickup or trade triggers. `structure` is used for structure-based triggers. `count` defines how many matching events are required. `cooldown_ticks` defines the delay between automatic starts. `radius` is used by area and structure triggers. `center` tells RaidON how the raid center should be resolved. `value` is kept as a legacy numeric alias.

Supported start conditions are `min_players`, `max_players`, `y_between`, `in_biome`, `in_dimension`, `time_of_day`, and `moon_phase`.

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

RaidON supports three center types. `event` uses the trigger position or player position. `spawn` uses the world spawn. `structure` uses the center of the located structure.

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

Every mob entry can use fields such as `type`, `count`, `ai`, `damage`, `targets`, `drops`, `traits`, and `nbt`.

The `ai` field supports `hostile`, `aggressive`, and `neutral`.

In practice, raid hostile mobs use RaidON combat logic first. Vanilla behavior only gets a chance when the custom logic has nothing to do. Raid mobs also ignore other raid mobs as valid targets.

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

The idea is simple. `attack` defines what the mob is allowed to attack, while `ignore` defines what the mob must ignore. Players still have the highest priority if they are visible.

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

Supported trait fields include `burn_in_sun`, `can_drown`, `knockback_resistance`, `movement_speed_multiplier`, `ai_speed_multiplier`, `hard_leash_multiplier`, and `raid_ai_enabled`.

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

Chance logic works like this: `100` means always, `50` means 50%, and `0.25` means 0.25%.

This system works for both global raid drops and local mob drops.

## Events
You can run events on `on_raid_start`, `on_start`, `on_end`, and `on_raid_end`.

NBT versions of these are `on_raid_start_nbt`, `on_start_nbt`, `on_end_nbt`, and `on_raid_end_nbt`.

Available events are `broadcast`, `chat`, `actionbar`, `subtitle`, `title`, `summon`, `command`, `set_time`, `lightning`, `effect`, `sound`, `loop_sound`, `raid_sound`, and `music`.

Example:

```json
"on_raid_start": [
  { "type": "chat", "text": "&cRaid started!" },
  { "type": "title", "text": "&6&lRaid started!", "fade_in": 10, "stay": 70, "fade_out": 20 },
  { "type": "sound", "sound": "minecraft:entity.wither.spawn", "volume": 1.5, "pitch": 1.0 },
  { "type": "loop_sound", "sound": "minecraft:music_disc.13", "sound_source": "music", "repeat_ticks": 240 }
]
```

## :exclamation: Visual and Sound
Normal sound example:

```json
{ "type": "sound", "sound": "minecraft:block.bell.use" }
{ "type": "sound", "sound": "minecraft:entity.wither.spawn", "sound_source": "hostile", "volume": 1.5, "pitch": 0.8 }
{ "type": "loop_sound", "sound": "minecraft:music_disc.13", "sound_source": "music", "repeat_ticks": 240 }
```

The `sound` action plays once. `loop_sound`, `raid_sound`, and `music` keep replaying while the raid is active.
If you want ending sounds, they should still go inside `on_raid_end`.

## Custom sounds from config
RaidON automatically loads client resources from `config/raidon/resources/`.

This folder is created automatically on client start.

Minimal example:

```text
config/raidon/resources/
└── assets/
    └── raidon_sound/
        ├── sounds.json
        └── sounds/
            ├── raid_start.ogg
            └── raid_loop.ogg
```

Example `sounds.json`:

```json
{
  "raid_start": {
    "sounds": [
      "raidon_sound:raid_start"
    ]
  },
  "raid_loop": {
    "sounds": [
      {
        "name": "raidon_sound:raid_loop",
        "stream": true
      }
    ]
  }
}
```

Then in raid config:

```json
{ "type": "sound", "sound": "raidon_sound:raid_start", "sound_source": "master" }
{ "type": "loop_sound", "sound": "raidon_sound:raid_loop", "sound_source": "music", "repeat_ticks": 1200 }
```

> [!TIP]\
> after changing `.ogg` or `sounds.json`, do `F3+T` or restart client

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

Supported `tone` values are `blue`, `green`, `purple`, `gold`, and `gray`.

If `tone` is missing, the default red/brown style is used.

### 2. Custom bar textures
If you set custom textures, RaidON hides only the default black background.
Text and progress still stay visible.

Texture keys are `progress_empty` and `progress_full`. Legacy aliases are `main` and `progress`.

Path examples are `raidon:gui/file.png` and `raidon/gui/file.png`.

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
Available commands are `/raidon start <id> [x y z]`, `/raidon stop <id>`, `/raidon reload`, and `/raidon activeraids`.

## API Section
Public API includes `ru.xaoser.raidon.api.RaidonApi`, `ru.xaoser.raidon.api.RaidRegistration`, `ru.xaoser.raidon.api.RaidBuilder`, `ru.xaoser.raidon.api.WaveBuilder`, and `ru.xaoser.raidon.api.RaidGuiBuilder`.

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


> [!TIP]
> - use `Name` for raid name
> - register raids in server lifecycle
> - use unique id for every raid
> - if you want default GUI, just do not set custom texture
> - if you want fully own mob brain, disable built-in raid AI
> - if something explodes, remember:
    this is beta, brother :feelsgood:
