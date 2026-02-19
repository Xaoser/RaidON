# RaidON library

THIS IS A ALPHA VERSION OF LIBRARY!!!
If you found a bug, error or suggestion for improvement, plz write me about it in discord: xaoser

Do you want to make a zombie apocalypse or just make some kind of event?, then this library is just for you!, here you can even make an invasion of CHICKENS!

## What can this mod?
- Loads JSON raids from `config/raidon/raids/*.json'.
- Launches raids with commands and via the Java API.
- Render the raid HUD (waves/mobs/progress), including custom GUI textures, put textures to `config/raidon/gui/*.png`.
- Provide EPIC invasion for your soul!
- Change mob's AI for aggressive or else
- Help to create Raid into other mods with API
- Create example raid config in config folder
- Not baking a cookie(


## Example raid config:
```json
{
  "id": "raidon:example_raid",
  "difficulty": 2,
  "start": { "event": "player has join in singleplay world" },
  "points": {
    "mainpoint": {"x": 0, "y": 70, "z": 0},
    "raidspawnpoint": {"x": 64, "y": 70, "z": 64},
    "raidpoint": {"x": 0, "y": 70, "z": 0}
  },
  "gui": {
    "size": "120, 40"
  },
  "drops": { "global": [
    { "item": "minecraft:iron_ingot", "min": 0, "max": 1, "chance": 100 },
    { "item": "minecraft:iron_nugget", "min": 1, "max": 3, "chance": 0.25 }
  ]
  },
  "spawn": { "min_radius": 18, "max_radius": 60, "attempts_per_mob": 12, "require_ground": true, "avoid_water": true  },
  "waves": [
    {"mobs": [
      {"type": "minecraft:chicken",
        "count": 60,
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
        "count": 40,
        "ai": "aggressive",
        "damage": 3.0,
        "drops": [
          { "item": "minecraft:leather", "min": 0, "max": 1, "chance": 100 }
        ]
      }
    ],
      "complete": {"type": "all_dead" },
      "on_end": [
        { "type": "broadcast", "text": "Wave 1 end" }
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
> GUI endpoints: `raidon:gui/file.png` and `raidon/gui/file.png`.


## Information
Raidpoints
-------------------------------
- `mainpoint` — Center of raid
- `raidspawnpoint` — Spawnpoint of raid mobs
- `raidpoint` — Point where goes raid mobs
- `mob_wander_radius` — The radius of the collection area around the `raidpoint` (default is `50`).
---------
### Commands
---------
- `/raidon start <id> [x y z]`
- `/raidon stop <id>`
- `/raidon reload`
- `/raidon activeraids`
-------------------------------
### Start:
- `manual` (default, if start trigger is empty) - 
- `player_join_any` / `player_join` — autorun if any player join
  ```"start": { "event": "player_join_any" }```
- `player_join_singleplayer` / `player has join in singleplay world` — autorun only in singleplayer world.
  ```"start": { "event": "player_join_singleplayer" }```
- `night_fall` / `night` — autorun at nightfall (overworld).
  ```"start": { "event": "night_fall", "cooldown_ticks": 24000 }```
- `on_kill` - run after killing a mob.
  ```"start": { "event": "on_kill", "entity": "minecraft:chicken", "cooldown_ticks": 200 }```
- `on_item_pickup` - start after pickup item
  ```"start": { "event": "on_item_pickup", "item": "minecraft:diamond", "cooldown_ticks": 100 }```
- `on_trade` - start after trading on item
  ```"start": { "event": "on_structure_visit", "structure": "minecraft:village_plains", "value": 64 }```
- `on_dimension_change` - start after dimmension changed
  ```"start": { "event": "on_dimension_change", "dimension": "minecraft:nether" }```
- `on_respawn` - start when player is respawning
  ```"start": { "event": "on_respawn" }```
- `on_enter_biome` - start when player enter biome
  ```"start": { "event": "on_enter_biome", "biome": "minecraft:desert" }```
- `on_day` - start at day
  ```"start": { "event": "on_day", "cooldown_ticks": 24000 }```
- `on_sunset` - start at sunset
  ```"start": { "event": "on_sunset", "cooldown_ticks": 24000 }```
- `on_midnight` - start at midnight
  ```"start": { "event": "on_midnight", "cooldown_ticks": 24000 }```
  
`addition start`:
- `event` or `type` — name of trigger
- `cooldown_ticks` — Delay between automatic raid launches

Example:
```json
"start": {
  "event": "night_fall",
  "cooldown_ticks": 24000
}
```

### Fine-tuning mobs (`traits`)
---------------------------------
For each mob in the wave, you can set:
- `burn_in_sun` (bool) — Can burn on sun.
- `can_drown` (bool)
- `knockback_resistance` (0..1)

Example
```json
{
  "type": "minecraft:zombie",
  "count": 20,
  "ai": "aggressive",
  "traits": {
    "burn_in_sun": false,
    "can_drown": false,
    "knockback_resistance": 0.75
  }
}
```

### Drop chance
-----------------
- `100` = always drops
- `50` = 50%
- `0.25` = 0.25%

It works for global and local drops

### End trigger
----------------------------------------------------
End triggers:
- `broadcast` — send broadcast message to players
- `summon` - summon mob on end
- `command` - execute any server command (`"/time set day"` and `"time set day"` both supported)
- `lightning` - strike lightning into entities, block point, or explicit coordinates
- `on_kill` - `"start": { "event": "on_kill", "entity": "minecraft:chicken", "cooldown_ticks": 200 }`

Example
```json
"on_raid_end": [
  { "type": "broadcast", "text": "Raid is over!" },
  { "type": "summon", "summon": "minecraft:zombie", "value": "10"}
]
```

Lightning examples
```json
{ "type": "lightning" }
{ "type": "lightning", "value": 3 }
{ "type": "lightning", "target": "entity", "entity": "players_in_raid" }
{ "type": "lightning", "target": "entity", "entity": "mobs", "radius": 80, "value": 2 }
{ "type": "lightning", "x": 100, "y": 70, "z": -35, "value": 4 }
```

- `value` for lightning = strikes count (for entity target it is strikes per entity).
- Multiplayer safety: raid configs are loaded and executed on server side only, rewards/actions are server-authoritative.

## API
------------------------------------
Новые публичные API для других модов:
- `ru.xaoser.raidon.api.RaidonApi`
- `ru.xaoser.raidon.api.RaidRegistration`
- `ru.xaoser.raidon.api.RaidGuiBuilder`
- `ru.xaoser.raidon.api.RaidBuilder` / `WaveBuilder` (создание рейдов кодом)
- `ru.xaoser.raidon.runtime.raid.RaidPointSettings` (настройка `mainpoint/raidspawnpoint/raidpoint/mob_wander_radius`)

### 1) Example coding raid
```java
ResourceLocation raidId = new ResourceLocation("mymod", "library_raid");

Raid raid = new RaidBuilder(raidId)
        .difficulty(3.0F)
        .addWave(w -> w
                .mob(10, EntityType.ZOMBIE, SpawnBehavior.AGGRESSIVE, null, List.of(), MobTargeting.defaults(), new MobTraits(false, false, 0.6D))
                .completeWhenAllDead())
        .addWave(w -> w
                .mob(4, EntityType.SKELETON, SpawnBehavior.HOSTILE)
                .completeWhenAllDead())
        .build();
```

### 2) GUI + registration
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
        new RaidStartSettings(RaidStartSettings.Trigger.PLAYER_JOIN_ANY, 1200)
);

RaidonApi.registerRaid(registration);
```

### 3) Start/Stop from code
```java
RaidonApi.startRaid(raidId, serverLevel, centerPos);
RaidonApi.stopRaid(raidId);
```

Recomindation
-----------------------------
- Register Raids in server lifecycle (after registries).
- Use unique ID for each Raid
- If you want deffault GUI, don't use custom paths
