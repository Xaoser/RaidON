package ru.xaoser.raidon.runtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidBuilder;
import ru.xaoser.raidon.api.sup.RaidAction;
import ru.xaoser.raidon.api.sup.SpawnBehavior;
import ru.xaoser.raidon.runtime.raid.RaidManager;
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
                if (loadSingle(file, logger)) {
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
    private static boolean loadSingle(Path file, Logger logger) {
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

            List<RaidFile.Wave> waves = model.waves() == null ? List.of() : model.waves();
            if (waves.isEmpty()) {
                logger.warn("[Raidon] Raid {} has no waves, skipping ({})", id, file.getFileName());
                return false;
            }

            // Build raid
            RaidBuilder builder = new RaidBuilder(id)
                    .difficulty((float) model.difficulty())
                    .endAction(buildActions(model.on_raid_end()));

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
                    EntityType<? extends Mob> type = resolveEntity(mob.type());
                    if (type == null) {
                        logger.warn(
                                "[Raidon] Unknown mob type '{}' in raid {} wave {} (file {})",
                                mob.type(), id, waveIndex, file.getFileName()
                        );
                        continue;
                    }
                    parsedMobs.add(new ParsedMob(Math.max(1, mob.count()), type, SpawnBehavior.fromString(mob.ai())));
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
                        wb.mob(mob.count(), mob.type(), mob.behavior());
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
            RaidManager.registerRaid(id, raid, spawnSettings);

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
                // You can extend here: give_loot, run_command, particles, etc.
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static EntityType<? extends Mob> resolveEntity(String id) {
        if (id == null || id.isBlank()) return null;

        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null) {
            type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        }
        if (type == null) {
            return null;
        }
        if (!Mob.class.isAssignableFrom(type.getBaseClass())) {
            return null;
        }
        return (EntityType<? extends Mob>) type;
    }

    // ===== JSON model =====
    private record ParsedMob(int count, EntityType<? extends Mob> type, SpawnBehavior behavior) {}

    public record RaidFile(
            String id,
            double difficulty,
            Start start,
            Spawn spawn,
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

        public record Spawn(int min_radius, int max_radius, int attempts_per_mob, boolean require_ground, boolean avoid_water) {}

        public record Wave(List<Mob> mobs, Completion complete, List<Action> on_end, int spawn_radius) {
            public Wave(List<Mob> mobs, Completion complete, List<Action> on_end) {
                this(mobs, complete, on_end, 0);
            }
        }

        public record Completion(String type) {}

        public record Mob(String type, int count, String ai) {
            public Mob(String type, int count) {
                this(type, count, null);
            }
        }

        public record Action(String type, String text, Map<String, Object> extra) {}
    }
}
