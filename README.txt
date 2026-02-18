RaidON — настройка, API и интеграция как библиотека
===================================================

Что умеет мод
-------------
- Загружает рейды из JSON из `config/raidon/raids/*.json`.
- Запускает рейды командами и через Java API.
- Отрисовывает HUD рейда (волны/мобы/прогресс), включая кастомные текстуры GUI.
- Синхронизирует HUD при перезаходе игрока, обновляет прогресс в реальном времени и скрывает HUD по `F1`.

Быстрый старт (JSON)
--------------------
1. Создайте файл в `config/raidon/raids/`, например `zombie_raid.json`.
2. Вставьте пример (как вы просили):

```json
{
  "id": "raidon:zombie_raid",
  "difficulty": 2,
  "start": { "event": "player has join in singleplay world" },
  "points": {
    "mainpoint": {"x": 0, "y": 70, "z": 0},
    "raidspawnpoint": {"x": 64, "y": 70, "z": 64},
    "raidpoint": {"x": 0, "y": 70, "z": 0}
  },
  "gui": {
    "main": "raidon/gui/frostis.png",
    "progress": "raidon/gui/frostis_progress.png",
    "size": "120, 40"
  },
  "drops": {
    "global": [
      { "item": "minecraft:emerald", "min": 0, "max": 1, "chance": 99.08 },
      { "item": "minecraft:iron_nugget", "min": 1, "max": 3, "chance": 0.25 }
    ]
  },
  "spawn": {
    "min_radius": 18,
    "max_radius": 60,
    "attempts_per_mob": 12,
    "require_ground": true,
    "avoid_water": true
  },
  "waves": [
    {
      "mobs": [
        {
          "type": "minecraft:zombie",
          "count": 50,
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
        {
          "type": "minecraft:zombie",
          "count": 40,
          "ai": "aggressive",
          "damage": 3.0,
          "drops": [
            { "item": "minecraft:leather", "min": 0, "max": 1, "chance": 100 }
          ]
        }
      ],
      "complete": {"type": "all_dead"},
      "on_end": [
        { "type": "broadcast", "text": "Волна 1 отбита." }
      ]
    },
    {
      "mobs": [
        { "type": "minecraft:zombie", "count": 6, "ai": "aggressive", "damage": 3.0 },
        { "type": "minecraft:zombie", "count": 2, "ai": "hostile", "damage": 3.0 }
      ],
      "complete": { "type": "all_dead" }
    }
  ],
  "on_raid_end": [
    { "type": "broadcast", "text": "congratulation!" }
  ]
}
```

> Примечание по GUI-путям: поддерживаются форматы `raidon:gui/file.png` и `raidon/gui/file.png`.

Команды
-------
- `/raidon start <id> [x y z]`
- `/raidon stop <id>`
- `/raidon reload`

Как работает HUD
----------------
- Если в `gui.main` и `gui.progress` указаны оба пути — используется кастомная текстура.
- Если хотя бы один путь не задан — рисуется стандартный progress bar.
- Прогресс заполняется динамически по убийству мобов и переходу волн.
- HUD скрывается при `F1`.
- После завершения рейда HUD закрывается.
- После перезахода игрока HUD восстанавливается по текущему состоянию активного рейда.

Drop шанс (важно)
-----------------
Теперь шанс в `drops` трактуется как **процент 0..100**:
- `100` = всегда
- `50` = 50%
- `0.25` = 0.25%

Это работает и для `drops.global`, и для `drops` у мобов в волнах.

Интеграция как библиотека (Java API)
------------------------------------
Новые публичные API для других модов:
- `ru.xaoser.raidon.api.RaidonApi`
- `ru.xaoser.raidon.api.RaidRegistration`
- `ru.xaoser.raidon.api.RaidGuiBuilder`
- `ru.xaoser.raidon.api.RaidBuilder` / `WaveBuilder` (создание рейдов кодом)

### 1) Создание рейда полностью через код
```java
ResourceLocation raidId = new ResourceLocation("mymod", "library_raid");

Raid raid = new RaidBuilder(raidId)
        .difficulty(3.0F)
        .addWave(w -> w
                .mob(10, EntityType.ZOMBIE, SpawnBehavior.AGGRESSIVE)
                .completeWhenAllDead())
        .addWave(w -> w
                .mob(4, EntityType.SKELETON, SpawnBehavior.HOSTILE)
                .completeWhenAllDead())
        .build();
```

### 2) GUI + настройки и регистрация
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
        gui
);

RaidonApi.registerRaid(registration);
```

### 3) Запуск/остановка из кода
```java
RaidonApi.startRaid(raidId, serverLevel, centerPos);
RaidonApi.stopRaid(raidId);
```

### 4) Получение статуса активных рейдов
```java
List<RaidManager.ActiveRaidStatus> statuses = RaidonApi.activeRaids();
```

Рекомендации для интеграторов
-----------------------------
- Регистрируйте рейды в server lifecycle (после поднятия registries).
- Используйте уникальные `ResourceLocation` id.
- Если хотите дефолтный HUD, просто не задавайте GUI-текстуры.
