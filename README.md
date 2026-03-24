# RaidON library

THIS IS STILL ALPHA LIBRARY!!!
If you found bug, strange thing, cursed chicken invasion or just have idea for better stuff, write me in discord: `xaoser`

Do you want zombie apocalypse?
Do you want village event?
Do you want 200 angry chickens running to player?
Then yes, this library is for you.

## What this mod can do?
- Load raids from `config/raidon/raids/*.json`
- Support 2 config styles:
  `normal json system`
  `nbt_system: "true"`
- Start raids from commands
- Start raids from Java API
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
  "start_nbt": "{event:on_kill, entity:minecraft:zombie, cooldown_ticks:200}",
  "waves": [
    {
      "mobs": [
        {
          "type": "minecraft:zombie",
          "count": 1,
          "ai": "hostile",
          "nbt": "{Health:40.0f, CanPickUpLoot:1b, PersistenceRequired:1b}"
        }
      ],
      "on_end_nbt": "{actions:[{type:broadcast, text:wave finished now}]}"
    }
  ],
  "on_raid_end_nbt": "{actions:[{type:broadcast, text:raid finished now}]}"
}
```

You can use NBT mode for:
- mob NBT
- raid start trigger
- wave end actions
- raid end actions

Relaxed NBT is supported too:
- `text:raid finished now` works
- `entity:minecraft:zombie` works
- inner `\"...\"` is not mandatory for simple string values

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
  "event": "night_fall",
  "cooldown_ticks": 24000
}
```

Extra fields:
- `event` or `type` = trigger name
- `cooldown_ticks` = delay between auto starts
- `value` = extra numeric value for trigger

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

## End actions
- `broadcast`
- `summon`
- `command`
- `set_time`
- `lightning`
- `effect`
- `title`

Example:
```json
"on_raid_end": [
  { "type": "broadcast", "text": "Raid is over!" },
  { "type": "summon", "summon": "minecraft:zombie", "value": 10 },
  { "type": "effect", "effect": "minecraft:speed", "duration": 300, "amplifier": 0 }
]
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
