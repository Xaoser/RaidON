package ru.xaoser.raidon.runtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidBuilder;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.RaidAction;
import ru.xaoser.raidon.api.sup.SpawnBehavior;
import ru.xaoser.raidon.runtime.raid.RaidGuiSettings;
import ru.xaoser.raidon.runtime.raid.RaidManager;
import ru.xaoser.raidon.runtime.raid.RaidPointSettings;
import ru.xaoser.raidon.runtime.raid.RaidSpawnSettings;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RaidConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RaidConfigLoader() {}

    public static void load(MinecraftServer server, Logger logger) {

        // Correct Forge config dir in 1.20.1
        Path baseDir = FMLPaths.CONFIGDIR.get().resolve("raidon").resolve("raids");
        logger.info("[Raidon] Loading raid configs from {}", baseDir.toAbsolutePath());

        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            logger.error("[Raidon] Failed to create raid config directory {}", baseDir, e);
            return;
        }

        RaidManager.clearDefinitions();

        int found = 0;
        int loaded = 0;

        try (var stream = Files.list(baseDir)) {
            var files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList();

            found = files.size();
            logger.info("[Raidon] Found {} raid json file(s)", found);

            for (Path file : files) {
                if (loadSingle(server, file, logger)) {
                    loaded++;
                }
            }
        } catch (IOException e) {
            logger.error("[Raidon] Failed to list raid configs in {}", baseDir, e);
        }

        logger.info("[Raidon] Raid load done. loaded={}/{}. Registered now: {}",
                loaded, found, RaidManager.raidsView().size());
    }

    /**
     * @return true if the raid file was successfully loaded and registered.
     */
    private static boolean loadSingle(MinecraftServer server, Path file, Logger logger) {
        try (Reader reader = Files.newBufferedReader(file)) {
            RaidFile model = GSON.fromJson(reader, RaidFile.class);
            if (model == null) {
                logger.warn("[Raidon] Skipping empty raid file {}", file.getFileName());
                return false;
            }

            ResourceLocation id = ResourceLocation.tryParse(model.id());
            if (id == null) {
                logger.warn("[Raidon] Invalid raid id in {}: {}", file.getFileName(), model.id());
                return false;
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

            List<RaidFile.Wave> waves = model.waves() == null ? List.of() : model.waves();
            if (waves.isEmpty()) {
                logger.warn("[Raidon] Raid {} has no waves, skipping ({})", id, file.getFileName());
                return false;
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
                            parseMobTargeting(mob.targets(), logger, file)
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
                        wb.mob(mob.count(), mob.type(), mob.behavior(), mob.baseDamage(), mob.drops(), mob.targeting());
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
                return false;
            }

            Raid raid = builder.build();
            RaidManager.registerRaid(id, raid, spawnSettings, pointSettings, guiSettings);

            logger.info("[Raidon] Loaded raid {} from {}", id, file.getFileName());
            return true;

        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            logger.error("[Raidon] Failed to parse raid file {}", file.getFileName(), e);
            return false;
        } catch (Exception e) {
            logger.error("[Raidon] Unexpected error while loading {}", file.getFileName(), e);
            return false;
        }
    }

    private static RaidAction buildActions(List<RaidFile.Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return ctx -> {};
        }
        List<RaidFile.Action> immutable = List.copyOf(actions);

        return ctx -> {
            for (RaidFile.Action action : immutable) {
                if ("broadcast".equalsIgnoreCase(action.type()) && action.text() != null) {
                    ctx.broadcast(action.text());
                }
            }
        };
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
            MobTargeting targeting
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
                long cooldown_ticks,
                int radius,
                Center center,
                List<Condition> conditions
        ) {
            public record Center(String type, String structure, int search_radius, boolean prefer_nearest) {}
            public record Condition(String type, int min, int max, int value) {}
        }

        public record Points(JsonElement mainpoint, JsonElement raidspawnpoint, JsonElement raidpoint) {}

        public record Spawn(int min_radius, int max_radius, int attempts_per_mob, boolean require_ground, boolean avoid_water) {}

        public record Gui(String main, String progress, JsonElement size) {}

        public record Wave(List<Mob> mobs, Completion complete, List<Action> on_end, int spawn_radius) {
            public Wave(List<Mob> mobs, Completion complete, List<Action> on_end) {
                this(mobs, complete, on_end, 0);
            }
        }

        public record Completion(String type) {}

        public record Mob(String type, int count, String ai, Double damage, JsonElement targets, List<Drop> drops) {
            public Mob(String type, int count) {
                this(type, count, null, null, null, null);
            }
        }

        public record Action(String type, String text, Map<String, Object> extra) {}

        public record Drops(List<Drop> global) {}

        public record Drop(String item, int min, int max, double chance) {}
    }

    private static RaidPointSettings parsePointSettings(RaidFile.Points points, Logger logger, Path file) {
        if (points == null) {
            return RaidPointSettings.DEFAULT;
        }
        return new RaidPointSettings(
                parsePoint(points.mainpoint(), logger, file, "points.mainpoint"),
                parsePoint(points.raidspawnpoint(), logger, file, "points.raidspawnpoint"),
                parsePoint(points.raidpoint(), logger, file, "points.raidpoint")
        );
    }

    private static net.minecraft.core.BlockPos parsePoint(JsonElement element, Logger logger, Path file, String key) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("x") && obj.has("y") && obj.has("z")) {
                try {
                    return new net.minecraft.core.BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
                } catch (Exception ignored) {
                    // handled below
                }
            }
        }

        if (element.isJsonArray() && element.getAsJsonArray().size() == 3) {
            try {
                var a = element.getAsJsonArray();
                return new net.minecraft.core.BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
            } catch (Exception ignored) {
                // handled below
            }
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString().trim();
            String[] parts = raw.split("[,\\s]+");
            if (parts.length == 3) {
                try {
                    return new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                    // handled below
                }
            }
        }

        logger.warn("[Raidon] Invalid {} in {}. Expected object {{x,y,z}}, array [x,y,z], or string 'x y z'", key, file.getFileName());
        return null;
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
        ResourceLocation parsed = ResourceLocation.tryParse(value.trim());
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
            double normalizedChance = drop.chance() > 1.0 ? drop.chance() / 100.0 : drop.chance();
            double chance = Math.max(0.0, Math.min(1.0, normalizedChance));
            if (max == 0 || chance <= 0.0) {
                continue;
            }
            parsed.add(new DropEntry(BuiltInRegistries.ITEM.get(id), min, max, chance));
        }
        return parsed;
    }
}
