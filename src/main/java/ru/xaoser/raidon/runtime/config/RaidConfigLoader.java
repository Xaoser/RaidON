package ru.xaoser.raidon.runtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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
import java.util.List;
import java.util.Map;

public final class RaidConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RaidConfigLoader() {}

    public static void load(MinecraftServer server, Logger logger) {
        Path baseDir = server.getFile("config/raidon/raids").toPath();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            logger.error("Failed to create raid config directory {}", baseDir, e);
            return;
        }

        RaidManager.clear();

        try (var stream = Files.list(baseDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> loadSingle(path, logger));
        } catch (IOException e) {
            logger.error("Failed to list raid configs in {}", baseDir, e);
        }
    }

    private static void loadSingle(Path file, Logger logger) {
        try (Reader reader = Files.newBufferedReader(file)) {
            RaidFile model = GSON.fromJson(reader, RaidFile.class);
            if (model == null) {
                logger.warn("Skipping empty raid file {}", file);
                return;
            }

            ResourceLocation id = ResourceLocation.tryParse(model.id());
            if (id == null) {
                logger.warn("Invalid raid id in {}: {}", file, model.id());
                return;
            }

            RaidSpawnSettings spawnSettings = model.spawn() == null
                    ? RaidSpawnSettings.defaults()
                    : new RaidSpawnSettings(
                    model.spawn().min_radius(),
                    model.spawn().max_radius(),
                    model.spawn().attempts_per_mob(),
                    model.spawn().require_ground(),
                    model.spawn().avoid_water()
            );

            List<RaidFile.Wave> waves = model.waves() == null ? List.of() : model.waves();
            if (waves.isEmpty()) {
                logger.warn("Raid {} has no waves, skipping", id);
                return;
            }

            RaidBuilder builder = new RaidBuilder(id)
                    .difficulty((float) model.difficulty())
                    .endAction(buildActions(model.on_raid_end()));

            for (int i = 0; i < waves.size(); i++) {
                RaidFile.Wave wave = waves.get(i);
                builder.addWave(wb -> {
                    if (wave.spawn_radius() > 0) {
                        wb.spawnRadius(wave.spawn_radius());
                    }
                    List<RaidFile.Mob> mobs = wave.mobs() == null ? List.of() : wave.mobs();
                    for (RaidFile.Mob mob : mobs) {
                        EntityType<? extends Mob> type = resolveEntity(mob.type());
                        if (type != null) {
                            wb.mob(mob.count(), type, SpawnBehavior.fromString(mob.ai()));
                        }
                    }
                    wb.completeWhenAllDead();
                    if (wave.on_end() != null) {
                        wb.onWaveEnd(buildActions(wave.on_end()));
                    }
                });
            }

            Raid raid = builder.build();
            RaidManager.registerRaid(id, raid, spawnSettings);
            logger.info("Loaded raid {} from {}", id, file.getFileName());
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            logger.error("Failed to parse raid file {}", file, e);
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
    private static EntityType<? extends Mob> resolveEntity(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return null;
        }
        return EntityType.byString(rl.toString())
                .filter(type -> Mob.class.isAssignableFrom(type.getBaseClass()))
                .map(type -> (EntityType<? extends Mob>) type)
                .orElse(null);
    }

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
            public record Center(String type, String structure, int search_radius, boolean prefer_nearest) { }
            public record Condition(String type, int min, int max, int value) { }
        }

        public record Spawn(int min_radius, int max_radius, int attempts_per_mob, boolean require_ground, boolean avoid_water) { }

        public record Wave(List<Mob> mobs, Completion complete, List<Action> on_end, int spawn_radius) {
            public Wave(List<Mob> mobs, Completion complete, List<Action> on_end) {
                this(mobs, complete, on_end, 0);
            }
        }

        public record Completion(String type) { }

        public record Mob(String type, int count, String ai) {
            public Mob(String type, int count) {
                this(type, count, null);
            }
        }

        public record Action(String type, String text, Map<String, Object> extra) { }
    }
}
