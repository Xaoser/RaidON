package ru.xaoser.raidon.runtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidBuilder;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.MobTraits;
import ru.xaoser.raidon.api.sup.RaidAction;
import ru.xaoser.raidon.api.sup.SpawnBehavior;
import ru.xaoser.raidon.runtime.raid.RaidGuiSettings;
import ru.xaoser.raidon.runtime.raid.RaidManager;
import ru.xaoser.raidon.runtime.raid.RaidPointSettings;
import ru.xaoser.raidon.runtime.raid.RaidSpawnSettings;
import ru.xaoser.raidon.runtime.raid.RaidStartSettings;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RaidConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_RAID_FILE_NAME = "example_raid.json";
    private static final String DEFAULT_RAID_CONFIG = """
            {
                "id": "raidon:example_raid",
                "difficulty": 2,
                "start": { "event": "on_kill", "entity": "minecraft:chicken", "cooldown_ticks": 200 },
                "points": {
                  "mainpoint": {"x": "current", "y": "current", "z": "current"},
                  "raidspawnpoint": {"x": "current+64", "y": "current", "z": "current+64"},
                  "raidpoint": {"x": "current", "y": "current", "z": "current"}
                },
                "gui": {
                  "size": "120, 40"
                },
                "drops": { "global": [
                  { "item": "minecraft:emerald", "min": 0, "max": 1, "chance": 100 },
                  { "item": "minecraft:iron_nugget", "min": 1, "max": 3, "chance": 0.25 }
                ]
                },
                "spawn": { "min_radius": 18, "max_radius": 60, "attempts_per_mob": 12, "require_ground": true, "avoid_water": true  },
                "waves": [
                  {"mobs": [
                    {"type": "minecraft:chicken",
                      "count": 300,
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
                      { "type": "broadcast", "text": "Wave 1 cleared." }
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
                  { "type": "broadcast", "text": "Raid completed!" },
                  { "type": "summon", "summon": "minecraft:zombie", "value": 10 }
                ]
              }
            """;
    private RaidConfigLoader() {}

    public static LoadReport load(MinecraftServer server, Logger logger) {

        // Correct Forge config dir in 1.20.1
        Path baseDir = FMLPaths.CONFIGDIR.get().resolve("raidon").resolve("raids");
        logger.info("[Raidon] Loading raid configs from {}", baseDir.toAbsolutePath());

        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            logger.error("[Raidon] Failed to create raid config directory {}", baseDir, e);
            return LoadReport.failed("Failed to create config directory: " + baseDir, e);
        }

        ensureDefaultConfig(baseDir, logger);

        RaidManager.clearDefinitions();

        int found = 0;
        int loaded = 0;
        List<String> errors = new ArrayList<>();

        try (var stream = Files.list(baseDir)) {
            var files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList();

            found = files.size();
            logger.info("[Raidon] Found {} raid json file(s)", found);

            for (Path file : files) {
                LoadIssue issue = loadSingle(server, file, logger);
                if (issue.success()) {
                    loaded++;
                } else {
                    errors.add(issue.message());
                }
            }
        } catch (IOException e) {
            logger.error("[Raidon] Failed to list raid configs in {}", baseDir, e);
            errors.add("Error reading config directory " + baseDir + ": " + e.getMessage());
        }

        logger.info("[Raidon] Raid load done. loaded={}/{}. Registered now: {}",
                loaded, found, RaidManager.raidsView().size());
        return new LoadReport(found, loaded, errors);
    }

    private static void ensureDefaultConfig(Path baseDir, Logger logger) {
        try (var stream = Files.list(baseDir)) {
            boolean hasJson = stream.anyMatch(path -> path.getFileName().toString().endsWith(".json"));
            if (hasJson) {
                return;
            }
        } catch (IOException e) {
            logger.error("[Raidon] Failed to inspect raid configs in {}", baseDir, e);
            return;
        }

        Path defaultFile = baseDir.resolve(DEFAULT_RAID_FILE_NAME);
        try {
            Files.writeString(defaultFile, DEFAULT_RAID_CONFIG);
            logger.info("[Raidon] Generated default raid config at {}", defaultFile.toAbsolutePath());
        } catch (IOException e) {
            logger.error("[Raidon] Failed to write default raid config {}", defaultFile, e);
        }
    }


    /**
     * @return true if the raid file was successfully loaded and registered.
     */
    private static LoadIssue loadSingle(MinecraftServer server, Path file, Logger logger) {
        try (Reader reader = Files.newBufferedReader(file)) {
            RaidFile model = GSON.fromJson(reader, RaidFile.class);
            if (model == null) {
                logger.warn("[Raidon] Skipping empty raid file {}", file.getFileName());
                return LoadIssue.failed(file, "Empty or invalid JSON object");
            }

            ResourceLocation id = ResourceLocation.tryParse(model.id());
            if (id == null) {
                logger.warn("[Raidon] Invalid raid id in {}: {}", file.getFileName(), model.id());
                return LoadIssue.failed(file, "Invalid raid id: " + model.id());
            }

            // Spawn settings
            RaidSpawnSettings spawnSettings = model.spawn() == null
                    ? RaidSpawnSettings.defaults()
                    : new RaidSpawnSettings(
                    model.spawn().min_radius(),
                    model.spawn().max_radius(),
                    model.spawn().attempts_per_mob(),
                    model.spawn().require_ground(),
                    model.spawn().avoid_water()
            );
            spawnSettings = RaidSpawnSettings.sanitized(spawnSettings);

            RaidPointSettings pointSettings = parsePointSettings(model.points(), logger, file);
            RaidGuiSettings guiSettings = parseGuiSettings(model.gui(), logger, file);
            RaidStartSettings startSettings = parseStartSettings(model.start());

            List<RaidFile.Wave> waves = model.waves() == null ? List.of() : model.waves();
            if (waves.isEmpty()) {
                logger.warn("[Raidon] Raid {} has no waves, skipping ({})", id, file.getFileName());
                return LoadIssue.failed(file, "No waves defined");
            }

            List<DropEntry> globalDrops = parseDrops(model.drops() == null ? null : model.drops().global(), logger, file);

            // Build raid
            RaidBuilder builder = new RaidBuilder(id)
                    .difficulty((float) model.difficulty())
                    .endAction(buildActions(model.on_raid_end()))
                    .globalDrops(globalDrops);

            boolean hasValidWave = false;

            for (int i = 0; i < waves.size(); i++) {
                RaidFile.Wave wave = waves.get(i);
                final int waveIndex = i; // ← ВАЖНО

                List<RaidFile.Mob> mobs = wave.mobs() == null ? List.of() : wave.mobs();
                if (mobs.isEmpty()) {
                    logger.warn(
                            "[Raidon] Raid {} wave {} has no mobs (file {})",
                            id, waveIndex, file.getFileName()
                    );
                }

                List<ParsedMob> parsedMobs = new ArrayList<>();
                for (RaidFile.Mob mob : mobs) {
                    EntityType<? extends Mob> type = resolveEntity(server, mob.type());
                    if (type == null) {
                        logger.warn(
                                "[Raidon] Unknown mob type '{}' in raid {} wave {} (file {})",
                                mob.type(), id, waveIndex, file.getFileName()
                        );
                        continue;
                    }
                    List<DropEntry> mobDrops = parseDrops(mob.drops(), logger, file);
                    Float baseDamage = mob.damage() == null ? null : mob.damage().floatValue();
                    parsedMobs.add(new ParsedMob(
                            Math.max(1, mob.count()),
                            type,
                            SpawnBehavior.fromString(mob.ai()),
                            baseDamage,
                            mobDrops,
                            parseMobTargeting(mob.targets(), logger, file),
                            parseMobTuning(mob.traits())
                    ));
                }

                if (parsedMobs.isEmpty()) {
                    logger.warn("[Raidon] Raid {} wave {} has no valid mobs after parsing; skipping wave (file {})",
                            id, waveIndex, file.getFileName());
                    continue;
                }

                builder.addWave(wb -> {
                    if (wave.spawn_radius() > 0) {
                        wb.spawnRadius(wave.spawn_radius());
                    }

                    for (ParsedMob mob : parsedMobs) {
                        wb.mob(mob.count(), mob.type(), mob.behavior(), mob.baseDamage(), mob.drops(), mob.targeting(), mob.tuning());
                    }

                    wb.completeWhenAllDead();

                    if (wave.on_end() != null && !wave.on_end().isEmpty()) {
                        wb.onWaveEnd(buildActions(wave.on_end()));
                    }
                });

                hasValidWave = true;
            }

            if (!hasValidWave) {
                logger.warn("[Raidon] Raid {} has no valid waves, skipping ({})", id, file.getFileName());
                return LoadIssue.failed(file, "No valid waves after parsing");
            }

            Raid raid = builder.build();
            RaidManager.registerRaid(id, raid, spawnSettings, pointSettings, guiSettings, startSettings);

            logger.info("[Raidon] Loaded raid {} from {}", id, file.getFileName());
            return LoadIssue.ok(file);

        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            logger.error("[Raidon] Failed to parse raid file {}", file.getFileName(), e);
            return LoadIssue.failed(file, "Parsing error: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("[Raidon] Unexpected error while loading {}", file.getFileName(), e);
            return LoadIssue.failed(file, "Unexpected error: " + e.getMessage(), e);
        }
    }

    private static RaidAction buildActions(List<RaidFile.Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return ctx -> {};
        }
        List<RaidFile.Action> immutable = List.copyOf(actions);

        return ctx -> {
            for (RaidFile.Action action : immutable) {
                if (action.type() == null) {
                    continue;
                }
                switch (action.type().toLowerCase()) {
                    case "broadcast" -> {
                        if (action.text() != null) ctx.broadcast(action.text());
                    }
                    case "summon" -> {
                        ResourceLocation summonId = ResourceLocation.tryParse(action.summon());
                        if (summonId == null) break;
                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(summonId);
                        int count = Math.max(1, action.value() == null ? 1 : action.value());
                        for (int i = 0; i < count; i++) {
                            Entity entity = type.create(ctx.level());
                            if (entity == null) continue;
                            BlockPos pos = ctx.center().offset(ctx.level().random.nextInt(7) - 3, 1, ctx.level().random.nextInt(7) - 3);
                            entity.moveTo(pos, ctx.level().random.nextFloat() * 360.0F, 0.0F);
                            ctx.level().addFreshEntity(entity);
                        }
                    }
                    case "command" -> {
                        if (action.command() == null || action.command().isBlank()) break;
                        String command = action.command().trim();
                        if (command.startsWith("/")) {
                            command = command.substring(1).trim();
                        }
                        if (command.isBlank()) break;
                        CommandSourceStack source = ctx.level().getServer().createCommandSourceStack().withPermission(4);
                        ctx.level().getServer().getCommands().performPrefixedCommand(source, command);
                    }
                    case "set_time" -> {
                        if (action.time() != null) ctx.level().setDayTime(action.time());
                    }
                    case "lightning" -> triggerLightning(ctx, action);
                    case "effect" -> {
                        if (action.effect() == null) break;
                        ResourceLocation effectId = ResourceLocation.tryParse(action.effect());
                        if (effectId == null) break;
                        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
                        if (effect == null) break;
                        int duration = Math.max(20, action.duration() == null ? 200 : action.duration());
                        int amplifier = Math.max(0, action.amplifier() == null ? 0 : action.amplifier());
                        for (var player : ctx.playersInRaidZone()) {
                            player.addEffect(new MobEffectInstance(effect, duration, amplifier));
                        }
                    }
                    case "title" -> {
                        if (action.text() == null) break;
                        Component title = Component.literal(action.text());
                        for (var player : ctx.playersInRaidZone()) {
                            player.displayClientMessage(title, true);
                        }
                    }
                    default -> {
                    }
                }
            }
        };
    }

    private static void triggerLightning(ru.xaoser.raidon.api.sup.RaidContext ctx, RaidFile.Action action) {
        int strikes = Math.max(1, action.value() == null ? 1 : action.value());

        if (action.target() != null && action.target().equalsIgnoreCase("entity")) {
            List<Entity> targets = collectLightningEntities(ctx, action);
            for (Entity entity : targets) {
                for (int i = 0; i < strikes; i++) {
                    spawnLightning(ctx.level(), entity.blockPosition());
                }
            }
            if (!targets.isEmpty()) {
                return;
            }
        }

        BlockPos pos = resolveLightningPosition(ctx, action);
        for (int i = 0; i < strikes; i++) {
            spawnLightning(ctx.level(), pos);
        }
    }

    private static List<Entity> collectLightningEntities(ru.xaoser.raidon.api.sup.RaidContext ctx, RaidFile.Action action) {
        String selector = action.entity() == null ? "players_in_raid" : action.entity().toLowerCase();
        int radius = Math.max(1, action.radius() == null ? 64 : action.radius());

        return switch (selector) {
            case "players", "all_players", "players_in_world" -> new ArrayList<>(ctx.level().players());
            case "players_in_raid", "players_in_zone" -> new ArrayList<>(ctx.playersInRaidZone());
            case "nearest_player" -> {
                var nearest = ctx.level().getNearestPlayer(ctx.center().getX() + 0.5D, ctx.center().getY() + 0.5D, ctx.center().getZ() + 0.5D, radius, false);
                if (nearest == null) {
                    yield List.of();
                }
                yield List.of(nearest);
            }
            case "mobs", "all_mobs" -> ctx.level().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(ctx.center()).inflate(radius)).stream().map(e -> (Entity) e).toList();
            default -> List.of();
        };
    }

    private static BlockPos resolveLightningPosition(ru.xaoser.raidon.api.sup.RaidContext ctx, RaidFile.Action action) {
        if (action.x() != null && action.y() != null && action.z() != null) {
            return new BlockPos(action.x(), action.y(), action.z());
        }

        String blockTarget = action.block() == null ? "center" : action.block().toLowerCase();
        return switch (blockTarget) {
            case "raidpoint", "target", "raid_target" -> ctx.center();
            case "above_center" -> ctx.center().above();
            default -> ctx.center();
        };
    }

    private static void spawnLightning(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(bolt);
    }

    @SuppressWarnings("unchecked")
    private static EntityType<? extends Mob> resolveEntity(MinecraftServer server, String id) {
        if (id == null) return null;

        final ResourceLocation rl;
        try {
            rl = new ResourceLocation(id.trim());
        } catch (Exception e) {
            return null;
        }

        var reg = server.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE);

        EntityType<?> type = reg.get(rl);
        if (type == null) return null;

        Entity test = type.create(server.overworld());
        if (!(test instanceof Mob)) return null;

        return (EntityType<? extends Mob>) type;
    }




    // ===== JSON model =====
    private record ParsedMob(
            int count,
            EntityType<? extends Mob> type,
            SpawnBehavior behavior,
            Float baseDamage,
            List<DropEntry> drops,
            MobTargeting targeting,
            MobTraits tuning
    ) {}

    public record RaidFile(
            String id,
            double difficulty,
            Start start,
            Points points,
            Drops drops,
            Spawn spawn,
            @SerializedName(value = "GUI", alternate = {"gui"}) Gui gui,
            List<Wave> waves,
            List<Action> on_raid_end
    ) {
        public record Start(
                String type,
                String event,
                String entity,
                String item,
                String structure,
                String biome,
                String dimension,
                int value,
                long cooldown_ticks,
                int radius,
                Center center,
                List<Condition> conditions
        ) {
            public record Center(String type, String structure, int search_radius, boolean prefer_nearest) {}
            public record Condition(String type, int min, int max, int value, String biome, String dimension) {}
        }

        public record Points(JsonElement mainpoint, JsonElement raidspawnpoint, JsonElement raidpoint, Integer mob_wander_radius) {}

        public record Spawn(int min_radius, int max_radius, int attempts_per_mob, boolean require_ground, boolean avoid_water) {}

        public record Gui(String main, String progress, JsonElement size) {}

        public record Wave(List<Mob> mobs, Completion complete, List<Action> on_end, int spawn_radius) {
            public Wave(List<Mob> mobs, Completion complete, List<Action> on_end) {
                this(mobs, complete, on_end, 0);
            }
        }

        public record Completion(String type) {}

        public record Mob(String type, int count, String ai, Double damage, JsonElement targets, List<Drop> drops, JsonElement traits) {
            public Mob(String type, int count) {
                this(type, count, null, null, null, null, null);
            }
        }

        public record Action(String type, String text, String summon, Integer value, String command, Long time, String effect, Integer duration, Integer amplifier, String target, String entity, String block, Integer x, Integer y, Integer z, Integer radius) {}

        public record Drops(List<Drop> global) {}

        public record Drop(String item, int min, int max, double chance) {}
    }

    private static RaidPointSettings parsePointSettings(RaidFile.Points points, Logger logger, Path file) {
        if (points == null) {
            return RaidPointSettings.DEFAULT;
        }
        RaidPointSettings.PointTemplate main = parsePoint(points.mainpoint(), logger, file, "points.mainpoint");
        RaidPointSettings.PointTemplate spawn = parsePoint(points.raidspawnpoint(), logger, file, "points.raidspawnpoint");
        RaidPointSettings.PointTemplate target = parsePoint(points.raidpoint(), logger, file, "points.raidpoint");

        if (isLegacyStaticTemplate(main, 0, 70, 0)
                && isLegacyStaticTemplate(spawn, 64, 70, 64)
                && isLegacyStaticTemplate(target, 0, 70, 0)) {
            logger.info("[Raidon] {} uses legacy static example points (0/70/0, 64/70/64). Auto-migrating to current-based templates.", file.getFileName());
            main = new RaidPointSettings.PointTemplate(
                    RaidPointSettings.AxisValue.current(0),
                    RaidPointSettings.AxisValue.current(0),
                    RaidPointSettings.AxisValue.current(0)
            );
            spawn = new RaidPointSettings.PointTemplate(
                    RaidPointSettings.AxisValue.current(64),
                    RaidPointSettings.AxisValue.current(0),
                    RaidPointSettings.AxisValue.current(64)
            );
            target = new RaidPointSettings.PointTemplate(
                    RaidPointSettings.AxisValue.current(0),
                    RaidPointSettings.AxisValue.current(0),
                    RaidPointSettings.AxisValue.current(0)
            );
        }

        return new RaidPointSettings(main, spawn, target, points.mob_wander_radius());
    }

    private static boolean isLegacyStaticTemplate(RaidPointSettings.PointTemplate template, int x, int y, int z) {
        if (template == null) {
            return false;
        }
        return isAxisAbsolute(template.x(), x)
                && isAxisAbsolute(template.y(), y)
                && isAxisAbsolute(template.z(), z);
    }

    private static boolean isAxisAbsolute(RaidPointSettings.AxisValue axis, int expected) {
        return axis != null
                && axis.absolute() != null
                && axis.absolute() == expected
                && axis.currentOffset() == 0;
    }

    private static RaidPointSettings.PointTemplate parsePoint(JsonElement element, Logger logger, Path file, String key) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("x") && obj.has("y") && obj.has("z")) {
                try {
                    RaidPointSettings.AxisValue x = parseAxisValue(obj.get("x"), logger, file, key + ".x");
                    RaidPointSettings.AxisValue y = parseAxisValue(obj.get("y"), logger, file, key + ".y");
                    RaidPointSettings.AxisValue z = parseAxisValue(obj.get("z"), logger, file, key + ".z");
                    if (x != null && y != null && z != null) {
                        return new RaidPointSettings.PointTemplate(x, y, z);
                    }
                } catch (Exception ignored) {
                    // handled below
                }
            }
        }

        if (element.isJsonArray() && element.getAsJsonArray().size() == 3) {
            try {
                var a = element.getAsJsonArray();
                RaidPointSettings.AxisValue x = parseAxisValue(a.get(0), logger, file, key + "[0]");
                RaidPointSettings.AxisValue y = parseAxisValue(a.get(1), logger, file, key + "[1]");
                RaidPointSettings.AxisValue z = parseAxisValue(a.get(2), logger, file, key + "[2]");
                if (x != null && y != null && z != null) {
                    return new RaidPointSettings.PointTemplate(x, y, z);
                }
            } catch (Exception ignored) {
                // handled below
            }
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString().trim();
            String[] parts = raw.split("[,\\s]+");
            if (parts.length == 3) {
                try {
                    RaidPointSettings.AxisValue x = parseAxisValue(parts[0], logger, file, key + ".x");
                    RaidPointSettings.AxisValue y = parseAxisValue(parts[1], logger, file, key + ".y");
                    RaidPointSettings.AxisValue z = parseAxisValue(parts[2], logger, file, key + ".z");
                    if (x != null && y != null && z != null) {
                        return new RaidPointSettings.PointTemplate(x, y, z);
                    }
                } catch (Exception ignored) {
                    // handled below
                }
            }
        }

        logger.warn("[Raidon] Invalid {} in {}. Expected object {{x,y,z}}, array [x,y,z], or string 'x y z'. Axis values support integers or current/current+N/current-N", key, file.getFileName());
        return null;
    }

    private static RaidPointSettings.AxisValue parseAxisValue(JsonElement element, Logger logger, Path file, String key) {
        if (element == null || element.isJsonNull()) {
            logger.warn("[Raidon] Missing {} in {}", key, file.getFileName());
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return RaidPointSettings.AxisValue.absolute(element.getAsInt());
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseAxisValue(element.getAsString(), logger, file, key);
        }

        logger.warn("[Raidon] Invalid {} in {}. Expected integer or current/current+N/current-N", key, file.getFileName());
        return null;
    }

    private static RaidPointSettings.AxisValue parseAxisValue(String rawValue, Logger logger, Path file, String key) {
        if (rawValue == null) {
            logger.warn("[Raidon] Missing {} in {}", key, file.getFileName());
            return null;
        }

        String value = rawValue.trim().toLowerCase();
        if (value.isEmpty()) {
            logger.warn("[Raidon] Invalid {} in {}. Expected integer or current/current+N/current-N", key, file.getFileName());
            return null;
        }

        if (value.equals("current")) {
            return RaidPointSettings.AxisValue.current(0);
        }

        if (value.startsWith("current+")) {
            try {
                return RaidPointSettings.AxisValue.current(Integer.parseInt(value.substring("current+".length()).trim()));
            } catch (NumberFormatException ignored) {
                logger.warn("[Raidon] Invalid {} in {}: '{}'", key, file.getFileName(), rawValue);
                return null;
            }
        }

        if (value.startsWith("current-")) {
            try {
                return RaidPointSettings.AxisValue.current(-Integer.parseInt(value.substring("current-".length()).trim()));
            } catch (NumberFormatException ignored) {
                logger.warn("[Raidon] Invalid {} in {}: '{}'", key, file.getFileName(), rawValue);
                return null;
            }
        }

        try {
            return RaidPointSettings.AxisValue.absolute(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            logger.warn("[Raidon] Invalid {} in {}: '{}'", key, file.getFileName(), rawValue);
            return null;
        }
    }

    private static RaidGuiSettings parseGuiSettings(RaidFile.Gui gui, Logger logger, Path file) {
        if (gui == null) {
            return RaidGuiSettings.DEFAULT;
        }

        ResourceLocation main = parseTexture(gui.main(), logger, file, "gui.main");
        ResourceLocation progress = parseTexture(gui.progress(), logger, file, "gui.progress");
        int[] size = parseGuiSize(gui.size(), logger, file);

        return new RaidGuiSettings(main, progress, size[0], size[1]);
    }

    private static ResourceLocation parseTexture(String value, Logger logger, Path file, String key) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String raw = value.trim();
        if (!raw.contains(":")) {
            int slash = raw.indexOf('/');
            if (slash > 0 && slash < raw.length() - 1) {
                raw = raw.substring(0, slash) + ":" + raw.substring(slash + 1);
            }
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            logger.warn("[Raidon] Invalid {} in {}: '{}'", key, file.getFileName(), value);
            return null;
        }
        return parsed;
    }

    private static int[] parseGuiSize(JsonElement value, Logger logger, Path file) {
        int width = RaidGuiSettings.DEFAULT_WIDTH;
        int height = RaidGuiSettings.DEFAULT_HEIGHT;

        if (value == null || value.isJsonNull()) {
            return new int[]{width, height};
        }

        if (value.isJsonArray() && value.getAsJsonArray().size() >= 2) {
            try {
                width = value.getAsJsonArray().get(0).getAsInt();
                height = value.getAsJsonArray().get(1).getAsInt();
                return new int[]{width, height};
            } catch (Exception ignored) {
                // handled below
            }
        }

        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String[] parts = value.getAsString().trim().split("[,\\s]+");
            if (parts.length >= 2) {
                try {
                    width = Integer.parseInt(parts[0]);
                    height = Integer.parseInt(parts[1]);
                    return new int[]{width, height};
                } catch (NumberFormatException ignored) {
                    // handled below
                }
            }
        }

        logger.warn("[Raidon] Invalid gui.size in {}. Expected [w,h] or string 'w,h'", file.getFileName());
        return new int[]{width, height};
    }

    private static RaidStartSettings parseStartSettings(RaidFile.Start start) {
        if (start == null) {
            return RaidStartSettings.DEFAULT;
        }
        String raw = "";
        if (start.type() != null && !start.type().isBlank()) {
            raw = start.type();
        } else if (start.event() != null && !start.event().isBlank()) {
            raw = start.event();
        }
        String normalized = raw.trim().toLowerCase();
        RaidStartSettings.Trigger trigger = switch (normalized) {
            case "player_join", "player_join_any", "on_player_join", "player has join" -> RaidStartSettings.Trigger.PLAYER_JOIN_ANY;
            case "player has join in singleplay world", "player_join_singleplayer", "singleplayer_join" -> RaidStartSettings.Trigger.PLAYER_JOIN_SINGLEPLAYER;
            case "night", "night_fall", "on_night" -> RaidStartSettings.Trigger.NIGHT_FALL;
            case "on_kill" -> RaidStartSettings.Trigger.ON_KILL;
            case "on_item_pickup", "on_pickup", "item_pickup" -> RaidStartSettings.Trigger.ON_ITEM_PICKUP;
            case "on_trade", "trade" -> RaidStartSettings.Trigger.ON_TRADE;
            case "on_structure_visit", "visit_structure" -> RaidStartSettings.Trigger.ON_STRUCTURE_VISIT;
            case "on_dimension_change" -> RaidStartSettings.Trigger.ON_DIMENSION_CHANGE;
            case "on_respawn" -> RaidStartSettings.Trigger.ON_RESPAWN;
            case "on_enter_biome" -> RaidStartSettings.Trigger.ON_ENTER_BIOME;
            case "on_day" -> RaidStartSettings.Trigger.ON_DAY;
            case "on_sunset" -> RaidStartSettings.Trigger.ON_SUNSET;
            case "on_midnight" -> RaidStartSettings.Trigger.ON_MIDNIGHT;
            default -> RaidStartSettings.Trigger.MANUAL;
        };
        ResourceLocation entity = parseOptionalResourceLocation(start.entity());
        ResourceLocation item = parseOptionalResourceLocation(start.item());
        ResourceLocation structure = parseOptionalResourceLocation(start.structure());
        ResourceLocation biome = parseOptionalResourceLocation(start.biome());
        ResourceLocation dimension = parseOptionalResourceLocation(start.dimension());
        List<RaidStartSettings.Condition> conditions = parseStartConditions(start.conditions());
        return new RaidStartSettings(trigger, start.cooldown_ticks(), entity, item, structure, biome, dimension, conditions, start.value());
    }

    private static ResourceLocation parseOptionalResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(value.trim());
    }

    private static List<RaidStartSettings.Condition> parseStartConditions(List<RaidFile.Start.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        List<RaidStartSettings.Condition> parsed = new ArrayList<>();
        for (RaidFile.Start.Condition condition : conditions) {
            if (condition == null || condition.type() == null || condition.type().isBlank()) {
                continue;
            }
            ResourceLocation biome = parseOptionalResourceLocation(condition.biome());
            ResourceLocation dimension = parseOptionalResourceLocation(condition.dimension());
            parsed.add(new RaidStartSettings.Condition(
                    condition.type().trim().toLowerCase(),
                    condition.min(),
                    condition.max(),
                    condition.value(),
                    biome,
                    dimension
            ));
        }
        return List.copyOf(parsed);
    }

    private static MobTraits parseMobTuning(JsonElement traits) {
        if (traits == null || traits.isJsonNull() || !traits.isJsonObject()) {
            return MobTraits.defaults();
        }
        var obj = traits.getAsJsonObject();
        Boolean burnInSun = obj.has("burn_in_sun") ? obj.get("burn_in_sun").getAsBoolean() : null;
        Boolean canDrown = obj.has("can_drown") ? obj.get("can_drown").getAsBoolean() : null;
        Double knockbackResistance = obj.has("knockback_resistance") ? obj.get("knockback_resistance").getAsDouble() : null;
        Double movementSpeedMultiplier = obj.has("movement_speed_multiplier") ? obj.get("movement_speed_multiplier").getAsDouble() : null;
        Double aiSpeedMultiplier = obj.has("ai_speed_multiplier") ? obj.get("ai_speed_multiplier").getAsDouble() : null;
        Double hardLeashMultiplier = obj.has("hard_leash_multiplier") ? obj.get("hard_leash_multiplier").getAsDouble() : null;
        return new MobTraits(burnInSun, canDrown, knockbackResistance, movementSpeedMultiplier, aiSpeedMultiplier, hardLeashMultiplier);
    }

    private static MobTargeting parseMobTargeting(JsonElement targets, Logger logger, Path file) {
        if (targets == null || targets.isJsonNull() || !targets.isJsonObject()) {
            return MobTargeting.defaults();
        }

        var root = targets.getAsJsonObject();
        Double radius = root.has("radius") && root.get("radius").isJsonPrimitive()
                ? root.get("radius").getAsDouble()
                : null;

        java.util.Set<ResourceLocation> attackTypes = new java.util.HashSet<>();
        java.util.Set<ResourceLocation> ignoreTypes = new java.util.HashSet<>();
        boolean attackAll = false;

        if (root.has("attack")) {
            attackAll = collectTargets(root.get("attack"), attackTypes, logger, file, "targets.attack") || attackAll;
        }
        if (root.has("ignore")) {
            collectTargets(root.get("ignore"), ignoreTypes, logger, file, "targets.ignore");
        }

        if (root.has("whitelist") && root.get("whitelist").isJsonObject()) {
            var whitelist = root.getAsJsonObject("whitelist");
            if (whitelist.has("attack")) {
                attackAll = collectTargets(whitelist.get("attack"), attackTypes, logger, file, "targets.whitelist.attack") || attackAll;
            }
            if (whitelist.has("ignore")) {
                collectTargets(whitelist.get("ignore"), ignoreTypes, logger, file, "targets.whitelist.ignore");
            }
        }

        if (root.has("blacklist") && root.get("blacklist").isJsonObject()) {
            var blacklist = root.getAsJsonObject("blacklist");
            if (blacklist.has("attack")) {
                collectTargets(blacklist.get("attack"), ignoreTypes, logger, file, "targets.blacklist.attack");
            }
            if (blacklist.has("ignore")) {
                attackTypes.removeAll(collectTargetsAsSet(blacklist.get("ignore"), logger, file, "targets.blacklist.ignore"));
            }
        }

        return new MobTargeting(radius, attackAll, attackTypes, ignoreTypes);
    }

    private static boolean collectTargets(JsonElement element, java.util.Set<ResourceLocation> sink, Logger logger, Path file, String key) {
        boolean attackAll = false;
        for (String token : extractStrings(element)) {
            String normalized = token.trim().toLowerCase();
            if (normalized.equals("all")) {
                attackAll = true;
                continue;
            }
            if (normalized.equals("none")) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(token);
            if (rl == null) {
                logger.warn("[Raidon] Invalid entity id '{}' in {} ({})", token, key, file.getFileName());
                continue;
            }
            sink.add(rl);
        }
        return attackAll;
    }

    private static java.util.Set<ResourceLocation> collectTargetsAsSet(JsonElement element, Logger logger, Path file, String key) {
        java.util.Set<ResourceLocation> set = new java.util.HashSet<>();
        collectTargets(element, set, logger, file, key);
        return set;
    }

    private static java.util.List<String> extractStrings(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return java.util.List.of();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return java.util.List.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    out.add(entry.getAsString());
                }
            }
            return out;
        }
        return java.util.List.of();
    }

    private static List<DropEntry> parseDrops(List<RaidFile.Drop> drops, Logger logger, Path file) {
        if (drops == null || drops.isEmpty()) {
            return List.of();
        }
        List<DropEntry> parsed = new ArrayList<>();
        for (RaidFile.Drop drop : drops) {
            if (drop == null || drop.item() == null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(drop.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                logger.warn("[Raidon] Unknown item '{}' in drops list (file {})", drop.item(), file.getFileName());
                continue;
            }
            int min = Math.max(0, drop.min());
            int max = Math.max(min, drop.max());
            double chancePercent = Math.max(0.0, Math.min(100.0, drop.chance()));
            if (max == 0 || chancePercent <= 0.0) {
                continue;
            }
            parsed.add(new DropEntry(BuiltInRegistries.ITEM.get(id), min, max, chancePercent));
        }
        return parsed;
    }

    public record LoadReport(int found, int loaded, List<String> errors) {
        public static LoadReport failed(String message, Exception e) {
            return new LoadReport(0, 0, List.of(message + ": " + e.getClass().getSimpleName() + " - " + e.getMessage()));
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private record LoadIssue(boolean success, String message) {
        static LoadIssue ok(Path file) {
            return new LoadIssue(true, "OK: " + file.getFileName());
        }

        static LoadIssue failed(Path file, String message) {
            return new LoadIssue(false, file.getFileName() + " -> " + message);
        }

        static LoadIssue failed(Path file, String message, Exception e) {
            return new LoadIssue(false, file.getFileName() + " -> " + message + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

}
