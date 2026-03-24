# RaidON library

THIS IS STILL ALPHA LIBRARY!!!
If you found bug, strange thing, cursed chicken invasion or just have idea for better stuff, write me in [discord](https://discord.gg/5NSxKrA8tN)

Do you want zombie apocalypse?
Do you want village event?
Do you want 200 angry chickens running to you or your friend?
Then yep, this library is for you.

## What this mod can do?
- Load raids from `config/raidon/raids/*.json`
- Support 2 config styles:
  `normal json system`
  `nbt_system: "true"`
- Start raids from commands
- Start raids from Java API
- Start raids from events
- Spawn mobs with custom AI and custom target logic
- Add custom mob NBT
- delete a обычный мусор mob 3d max ultra mega universe edition
- Give EPIC invasion for your soul
- Not baking a cookie(

## Example raid config
```json
{
  "id": "raidon:example_raid",
  "difficulty": 10,
  "start": {
    "event": "on_kill",
    "entity": "minecraft:zombie",
    "conditions": [
      { "type": "min_players", "value": 1 },
      { "type": "in_biome", "biome": "minecraft:plains" },
      { "type": "in_dimension", "dimension": "minecraft:overworld" },
      { "type": "y_between", "min": 60, "max": 90 }
    ]
  },
  "on_raid_start": [
    { "type": "title", "text": "Raid started!" },
    { "type": "effect", "effect": "minecraft:resistance", "duration": 200, "amplifier": 0 },
    { "type": "sound", "sound": "minecraft:entity.wither.spawn", "volume": 1.5, "pitch": 1.0 }
  ],
  "points": {
    "mainpoint": {"x": 0, "y": 70, "z": 0},
    "raidspawnpoint": {"x": 64, "y": 70, "z": 64},
    "raidpoint": {"x": 0, "y": 70, "z": 0}
  },
  "gui": {
    "size": "120, 40"
  },
  "drops": { "global": [
    { "item": "minecraft:emerald", "min": 1, "max": 100, "chance": 100 },
    { "item": "minecraft:iron_nugget", "min": 1, "max": 3, "chance": 0.25 }
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

GUI texture path can be:
- `raidon:gui/file.png`
- `raidon/gui/file.png`

## NBT system example
If you want use NBT style config, just write:
```json
{
  "id": "raidon:nbt_test",
  "nbt_system": "true",
  "start_nbt": "{event:on_kill, entity:minecraft:zombie, count:10, cooldown_ticks:200}",
  "on_raid_start_nbt": [
    { "type": "broadcast", "text": "raid started" },
    { "type": "sound", "sound": "minecraft:entity.ender_dragon.growl", "volume": 1.2, "pitch": 1.0 }
  ],
  "waves": [
    {
      "on_start_nbt": [
        { "type": "effect", "effect": "minecraft:speed", "duration": 200, "amplifier": 0 },
        { "type": "sound", "sound": "minecraft:block.bell.use", "sound_source": "master" }
      ],
      "mobs": [
        {
          "type": "minecraft:zombie",
          "count": 1,
          "ai": "hostile",
          "nbt": "{Health:40.0f,CanPickUpLoot:1b,CustomName:'{\"text\":\"Boss Zombie\",\"color\":\"red\"}',ActiveEffects:[{Id:1b,Amplifier:1b,Duration:1200}]}"
        }
      ],
      "on_end_nbt": "{actions:[{type:broadcast, text:wave finished now}]}"
    }
  ],
  "on_raid_end_nbt": "{actions:[{type:broadcast, text:raid finished now}]}"
}
```

If you want full summon-style NBT, just write normal SNBT string like in command:
```json
"nbt": "{Health:40.0f,CanPickUpLoot:1b,CustomName:'{\"text\":\"Boss Zombie\",\"color\":\"red\"}',ActiveEffects:[{Id:1b,Amplifier:1b,Duration:1200}]}"
```

If you do not want escape previous style, you can write same thing as JSON object:
```json
"nbt": {
  "Health": "40.0f",
  "CanPickUpLoot": "1b",
  "PersistenceRequired": true,
  "CustomName": { "text": "Boss Zombie", "color": "red" },
  "CustomNameVisible": true,
  "ActiveEffects": [
    { "Id": "1b", "Amplifier": "1b", "Duration": 1200 }
  ],
  "ArmorDropChances": ["0.0f", "0.0f", "0.25f", "0.5f"]
}
```

For start/end actions you can use object, SNBT string, or just list:
```json
"on_end_nbt": [
  { "type": "broadcast", "text": "wave finished now" },
  { "type": "command", "command": "say wave cleared" }
]
```

What is supported now:
- exact SNBT like in `/summon`
- JSON object to NBT convert
- JSON array to NBT list convert
- raid start / wave start / wave end / raid end actions in NBT mode
- relaxed simple strings in SNBT:
  `text:raid finished now` works
  `entity:minecraft:zombie` works
- `CustomName` can be plain string:
  `"CustomName": "Boss Zombie"`
- `CustomName` can be text component object:
  `"CustomName": { "text": "Boss Zombie", "bold": true }`
- `display.Lore` can be string list or text component list
- exact typed values inside JSON object can be written as strings:
  `"1b"`, `"20s"`, `"40.0f"`, `"[I;1,2,3,4]"`
- if you want force exact raw NBT in object mode, use:
  `{"$snbt":"[I;1,2,3,4]"}`

## Raid points
- `mainpoint` = center of raid
- `raidspawnpoint` = where mobs are spawning
- `raidpoint` = where mobs try to gather / return
- `mob_wander_radius` = soft gather zone around `raidpoint`
- `gather_zone_radius` = alias for same thing
- `collection_zone_radius` = alias for same thing
- hard border is automatic now:
  it uses `distance(spawnPoint -> raidpoint) + 30 blocks`

So yes, mobs can cheese.

## Commands
- `/raidon start <id> [x y z]`
- `/raidon stop <id>`
- `/raidon reload`
- `/raidon activeraids`

## Start triggers
- `manual`
- `enter_area`
- `player_join_any`
- `player_join_singleplayer`
- `night_fall`
- `on_kill`
- `on_item_pickup`
- `on_trade`
- `on_dimension_change`
- `on_respawn`
- `on_enter_biome`
- `on_day`
- `on_sunset`
- `on_midnight`
- `on_structure_visit`

Example:
```json
"start": {
  "event": "on_kill",
  "entity": "minecraft:zombie",
  "count": 10,
  "cooldown_ticks": 24000
}
```

Extra fields:
- `event` or `type` = trigger name
- `cooldown_ticks` = delay between auto starts
- `count` = how many matching events are needed before raid starts
- `radius` = search radius for triggers like `on_structure_visit`
- `center` = custom raid start center
- `value` = legacy numeric alias, still supported for old configs

Supported start conditions:
- `min_players`
- `max_players`
- `y_between`
- `in_biome`
- `in_dimension`
- `time_of_day`
- `moon_phase`

Examples:
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

```json
"start": {
  "event": "on_trade",
  "item": "minecraft:emerald",
  "count": 3
}
```

```json
"start": {
  "event": "on_item_pickup",
  "item": "minecraft:diamond",
  "count": 16
}
```

```json
"start": {
  "event": "on_structure_visit",
  "structure": "minecraft:village_plains",
  "radius": 96
}
```

Center types:
- `event` = start raid at trigger position/player position
- `spawn` = start raid at world spawn
- `structure` = start raid at located structure center

NBT center example:
```json
"start_nbt": {
  "event": "enter_area",
  "radius": 48,
  "center": {
    "type": "structure",
    "structure": "minecraft:village_plains",
    "search_radius": 1024,
    "prefer_nearest": true
  },
  "conditions": [
    { "type": "time_of_day", "min": 13000, "max": 23000 }
  ]
}
```

## Event actions
You can run actions on:
- `on_raid_start`
- `on_start`
- `on_end`
- `on_raid_end`

NBT twins:
- `on_raid_start_nbt`
- `on_start_nbt`
- `on_end_nbt`
- `on_raid_end_nbt`

Example:
```json
"on_raid_start": [
  { "type": "broadcast", "text": "Raid started!" },
  { "type": "effect", "effect": "minecraft:resistance", "duration": 200, "amplifier": 0 },
  { "type": "sound", "sound": "minecraft:entity.wither.spawn", "volume": 1.5, "pitch": 1.0 }
]
```

Wave start example:
```json
"on_start": [
  { "type": "title", "text": "Wave 2" },
  { "type": "sound", "sound": "minecraft:block.bell.use", "sound_source": "master", "volume": 1.0, "pitch": 1.1 }
]
```

## Mob traits
You can tune mobs with `traits`:
- `burn_in_sun`
- `can_drown`
- `knockback_resistance`
- `movement_speed_multiplier`
- `ai_speed_multiplier`
- `hard_leash_multiplier`
- `raid_ai_enabled`

If `raid_ai_enabled` = `false`, built-in raid AI will be ignored.
This is useful if you want control mob logic from your own mod code.

## Drops
- `100` = always
- `50` = 50%
- `0.25` = 0.25%

Works for global and local mob drops

## Actions
- `broadcast`
- `summon`
- `command`
- `set_time`
- `lightning`
- `effect`
- `sound`
- `title`

Example:
```json
"on_raid_end": [
  { "type": "broadcast", "text": "Raid is over!" },
  { "type": "summon", "summon": "minecraft:zombie", "value": 10 },
  { "type": "effect", "effect": "minecraft:speed", "duration": 300, "amplifier": 0 },
  { "type": "sound", "sound": "minecraft:ui.toast.challenge_complete", "sound_source": "master", "volume": 1.0, "pitch": 1.0 }
]
```

Sound example:
```json
{ "type": "sound", "sound": "minecraft:block.bell.use" }
{ "type": "sound", "sound": "minecraft:entity.wither.spawn", "sound_source": "hostile", "volume": 1.5, "pitch": 0.8 }
```

Lightning example:
```json
{ "type": "lightning" }
{ "type": "lightning", "value": 3 }
{ "type": "lightning", "target": "entity", "entity": "players_in_raid" }
{ "type": "lightning", "target": "entity", "entity": "mobs", "radius": 80, "value": 2 }
{ "type": "lightning", "x": 100, "y": 70, "z": -35, "value": 4 }
```

## API
Public API for other mods:
- `ru.xaoser.raidon.api.RaidonApi`
- `ru.xaoser.raidon.api.RaidRegistration`
- `ru.xaoser.raidon.api.RaidGuiBuilder`
- `ru.xaoser.raidon.api.RaidBuilder`
- `ru.xaoser.raidon.api.WaveBuilder`

### Example coding raid
```java
ResourceLocation raidId = new ResourceLocation("mymod", "library_raid");

Raid raid = new RaidBuilder(raidId)
        .difficulty(3.0F)
        .addWave(w -> w
                .mob(10, EntityType.ZOMBIE, SpawnBehavior.HOSTILE, null, List.of(), MobTargeting.defaults(),
                        new MobTraits(false, false, 0.6D, null, null, null, true))
                .completeWhenAllDead())
        .build();
```

### Example registration
```java
RaidGuiSettings gui = RaidGuiBuilder.create()
        .mainTexture(new ResourceLocation("mymod", "gui/raid_main.png"))
        .progressTexture(new ResourceLocation("mymod", "gui/raid_progress.png"))
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

### Start and stop from code
```java
RaidonApi.startRaid(raidId, serverLevel, centerPos);
RaidonApi.stopRaid(raidId);
```

## Small recommendation
- register raids in server lifecycle
- use unique id for every raid
- if you want default GUI, just do not set custom texture
- if you want fully own mob brain, disable built-in raid AI
- if something explodes, remember:
  this is alpha, brother
