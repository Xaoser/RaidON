package ru.xaoser.raidon.runtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidBuilder;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.MobTraits;
import ru.xaoser.raidon.api.sup.RaidAction;
import ru.xaoser.raidon.api.sup.SpawnBehavior;
import ru.xaoser.raidon.runtime.nbt.RelaxedNbtParser;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
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
{
  "Name": "Example Raid",
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
    "size": "120, 40",
    "tone": "blue"
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
            """;
    private RaidConfigLoader() {}

    public static LoadReport load(MinecraftServer server, Logger logger) {

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
            boolean nbtSystemEnabled = parseBooleanFlag(model.nbt_system());

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
            RaidStartSettings startSettings = parseStartSettings(model.start(), model.start_nbt(), nbtSystemEnabled, logger, file);
            List<RaidFile.Action> raidStartActions = resolveActions(model.on_raid_start(), model.on_raid_start_nbt(),
                    nbtSystemEnabled, logger, file, "on_raid_start_nbt");
            List<RaidFile.Action> raidEndActions = resolveActions(model.on_raid_end(), model.on_raid_end_nbt(),
                    nbtSystemEnabled, logger, file, "on_raid_end_nbt");

            List<RaidFile.Wave> waves = model.waves() == null ? List.of() : model.waves();
            if (waves.isEmpty()) {
                logger.warn("[Raidon] Raid {} has no waves, skipping ({})", id, file.getFileName());
                return LoadIssue.failed(file, "No waves defined");
            }

            List<DropEntry> globalDrops = parseDrops(model.drops() == null ? null : model.drops().global(), logger, file);

            RaidBuilder builder = new RaidBuilder(id)
                    .name(model.name())
                    .difficulty((float) model.difficulty())
                    .startAction(buildActions(raidStartActions))
                    .endAction(buildActions(raidEndActions))
                    .globalDrops(globalDrops);

            boolean hasValidWave = false;

            for (int i = 0; i < waves.size(); i++) {
                RaidFile.Wave wave = waves.get(i);
                final int waveIndex = i;

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
                            parseMobTuning(mob.traits()),
                            parseMobNbt(mob.nbt(), nbtSystemEnabled, logger, file, waveIndex, mob.type())
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
                        wb.mob(mob.count(), mob.type(), mob.behavior(), mob.baseDamage(), mob.drops(),
                                mob.targeting(), mob.tuning(), mob.nbtData());
                    }

                    wb.completeWhenAllDead();

                    List<RaidFile.Action> waveStartActions = resolveActions(wave.on_start(), wave.on_start_nbt(),
                            nbtSystemEnabled, logger, file, "waves[" + waveIndex + "].on_start_nbt");
                    List<RaidFile.Action> waveEndActions = resolveActions(wave.on_end(), wave.on_end_nbt(),
                            nbtSystemEnabled, logger, file, "waves[" + waveIndex + "].on_end_nbt");
                    if (!waveStartActions.isEmpty()) {
                        wb.onWaveStart(buildActions(waveStartActions));
                    }
                    if (!waveEndActions.isEmpty()) {
                        wb.onWaveEnd(buildActions(waveEndActions));
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
                    case "broadcast", "chat", "message" -> {
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
                    case "sound", "playsound" -> playSound(ctx, action);
                    case "loop_sound", "raid_sound", "music" -> startLoopSound(ctx, action);
                    case "actionbar" -> {
                        if (action.text() != null) ctx.sendActionBar(action.text());
                    }
                    case "subtitle" -> {
                        if (action.text() != null) {
                            ctx.sendSubtitle(action.text(),
                                    action.fade_in() == null ? 0 : action.fade_in(),
                                    action.stay() == null ? 0 : action.stay(),
                                    action.fade_out() == null ? 0 : action.fade_out());
                        }
                    }
                    case "title" -> {
                        if (action.text() != null) {
                            ctx.sendTitle(action.text(),
                                    action.fade_in() == null ? 0 : action.fade_in(),
                                    action.stay() == null ? 0 : action.stay(),
                                    action.fade_out() == null ? 0 : action.fade_out());
                        }
                    }
                    default -> {
                    }
                }
            }
        };
    }

    private static void playSound(ru.xaoser.raidon.api.sup.RaidContext ctx, RaidFile.Action action) {
        if (action.sound() == null || action.sound().isBlank()) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(action.sound());
        if (soundId == null) {
            return;
        }
        SoundSource source = parseSoundSource(action.sound_source());
        float volume = clampFloat(action.volume() == null ? 1.0F : action.volume(), 0.0F, 64.0F);
        float pitch = clampFloat(action.pitch() == null ? 1.0F : action.pitch(), 0.0F, 4.0F);
        List<net.minecraft.server.level.ServerPlayer> players = ctx.playersInRaidZone();
        if (!players.isEmpty()) {
            for (var player : players) {
                RaidNetwork.sendSound(player, soundId, source, volume, pitch);
            }
            return;
        }
        if (BuiltInRegistries.SOUND_EVENT.containsKey(soundId)) {
            BlockPos pos = ctx.center();
            ctx.level().playSound(null, pos, BuiltInRegistries.SOUND_EVENT.get(soundId), source, volume, pitch);
        }
    }

    private static void startLoopSound(ru.xaoser.raidon.api.sup.RaidContext ctx, RaidFile.Action action) {
        if (action.sound() == null || action.sound().isBlank()) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(action.sound());
        if (soundId == null) {
            return;
        }
        SoundSource source = parseSoundSource(action.sound_source());
        float volume = clampFloat(action.volume() == null ? 1.0F : action.volume(), 0.0F, 64.0F);
        float pitch = clampFloat(action.pitch() == null ? 1.0F : action.pitch(), 0.0F, 4.0F);
        int repeatTicks = Math.max(20, action.repeat_ticks() == null ? 200 : action.repeat_ticks());
        ctx.setRaidLoopSound(soundId, source, volume, pitch, repeatTicks);
    }

    private static SoundSource parseSoundSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return SoundSource.MASTER;
        }
        String normalized = raw.trim().toLowerCase().replace('-', '_');
        return switch (normalized) {
            case "master" -> SoundSource.MASTER;
            case "music" -> SoundSource.MUSIC;
            case "record", "records" -> SoundSource.RECORDS;
            case "weather" -> SoundSource.WEATHER;
            case "block", "blocks" -> SoundSource.BLOCKS;
            case "hostile" -> SoundSource.HOSTILE;
            case "neutral" -> SoundSource.NEUTRAL;
            case "player", "players" -> SoundSource.PLAYERS;
            case "ambient" -> SoundSource.AMBIENT;
            case "voice" -> SoundSource.VOICE;
            default -> SoundSource.MASTER;
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




    private record ParsedMob(
            int count,
            EntityType<? extends Mob> type,
            SpawnBehavior behavior,
            Float baseDamage,
            List<DropEntry> drops,
            MobTargeting targeting,
            MobTraits tuning,
            String nbtData
    ) {}

    public record RaidFile(
            @SerializedName(value = "Name", alternate = {"name"}) String name,
            String id,
            @SerializedName(value = "nbt_system", alternate = {"nbtSystem"}) JsonElement nbt_system,
            double difficulty,
            Start start,
            @SerializedName(value = "start_nbt", alternate = {"startNbt"}) JsonElement start_nbt,
            Points points,
            Drops drops,
            Spawn spawn,
            @SerializedName(value = "GUI", alternate = {"gui"}) Gui gui,
            List<Wave> waves,
            @SerializedName(value = "on_raid_start", alternate = {"onRaidStart"}) List<Action> on_raid_start,
            @SerializedName(value = "on_raid_start_nbt", alternate = {"onRaidStartNbt"}) JsonElement on_raid_start_nbt,
            List<Action> on_raid_end,
            @SerializedName(value = "on_raid_end_nbt", alternate = {"onRaidEndNbt"}) JsonElement on_raid_end_nbt
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
                @SerializedName(value = "count", alternate = {"required_count", "requiredCount"}) int count,
                long cooldown_ticks,
                int radius,
                Center center,
                List<Condition> conditions
        ) {
            public record Center(
                    String type,
                    String structure,
                    @SerializedName(value = "search_radius", alternate = {"searchRadius"}) int search_radius,
                    @SerializedName(value = "prefer_nearest", alternate = {"preferNearest"}) boolean prefer_nearest
            ) {}
            public record Condition(String type, int min, int max, int value, String biome, String dimension) {}
        }

        public record Points(
                JsonElement mainpoint,
                JsonElement raidspawnpoint,
                JsonElement raidpoint,
                @SerializedName(value = "mob_wander_radius", alternate = {"gather_zone_radius", "collection_zone_radius", "collectionRadius", "gatherZoneRadius", "mobWanderRadius"}) Integer mob_wander_radius
        ) {}

        public record Spawn(int min_radius, int max_radius, int attempts_per_mob, boolean require_ground, boolean avoid_water) {}

        public record Gui(
                String main,
                String progress,
                @SerializedName(value = "progress_empty", alternate = {"progressEmpty", "empty", "bar_empty", "barEmpty"}) String progress_empty,
                @SerializedName(value = "progress_full", alternate = {"progressFull", "full", "bar_full", "barFull"}) String progress_full,
                String tone,
                JsonElement size
        ) {}

        public record Wave(List<Mob> mobs, Completion complete,
                           @SerializedName(value = "on_start", alternate = {"onWaveStart"}) List<Action> on_start,
                           @SerializedName(value = "on_start_nbt", alternate = {"onStartNbt"}) JsonElement on_start_nbt,
                           List<Action> on_end,
                           @SerializedName(value = "on_end_nbt", alternate = {"onEndNbt"}) JsonElement on_end_nbt,
                           int spawn_radius) {
            public Wave(List<Mob> mobs, Completion complete, List<Action> on_end) {
                this(mobs, complete, null, null, on_end, null, 0);
            }
        }

        public record Completion(String type) {}

        public record Mob(String type, int count, String ai, Double damage, JsonElement targets, List<Drop> drops, JsonElement traits, JsonElement nbt) {
            public Mob(String type, int count) {
                this(type, count, null, null, null, null, null, null);
            }
        }

        public record Action(String type, String text, String summon, Integer value, String command, Long time,
                             String effect, Integer duration, Integer amplifier, String sound,
                             @SerializedName(value = "sound_source", alternate = {"soundSource"}) String sound_source,
                             Float volume, Float pitch, String target, String entity, String block,
                             Integer x, Integer y, Integer z, Integer radius,
                             @SerializedName(value = "fade_in", alternate = {"fadeIn"}) Integer fade_in,
                             Integer stay,
                             @SerializedName(value = "fade_out", alternate = {"fadeOut"}) Integer fade_out,
                             @SerializedName(value = "repeat_ticks", alternate = {"repeatTicks"}) Integer repeat_ticks) {}

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
        ResourceLocation progressEmpty = parseTexture(gui.progress_empty(), logger, file, "gui.progress_empty");
        ResourceLocation progressFull = parseTexture(gui.progress_full(), logger, file, "gui.progress_full");
        int[] size = parseGuiSize(gui.size(), logger, file);

        return new RaidGuiSettings(main, progress, progressEmpty, progressFull, gui.tone(), size[0], size[1]);
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
                }
            }
        }

        logger.warn("[Raidon] Invalid gui.size in {}. Expected [w,h] or string 'w,h'", file.getFileName());
        return new int[]{width, height};
    }

    private static RaidStartSettings parseStartSettings(RaidFile.Start start, JsonElement startNbt, boolean nbtSystemEnabled,
                                                        Logger logger, Path file) {
        if (nbtSystemEnabled) {
            RaidStartSettings fromNbt = parseStartSettingsNbt(startNbt, logger, file);
            if (fromNbt != null) {
                return fromNbt;
            }
        }
        return parseStartSettings(start, logger, file);
    }

    private static RaidStartSettings parseStartSettings(RaidFile.Start start, Logger logger, Path file) {
        if (start == null) {
            return RaidStartSettings.DEFAULT;
        }
        String raw = firstNonBlank(start.type(), start.event());
        RaidStartSettings.Trigger trigger = parseTrigger(raw);
        warnIfUnknownTrigger(raw, trigger, logger, file, "start");
        ResourceLocation entity = parseOptionalResourceLocation(start.entity());
        ResourceLocation item = parseOptionalResourceLocation(start.item());
        ResourceLocation structure = parseOptionalResourceLocation(start.structure());
        ResourceLocation biome = parseOptionalResourceLocation(start.biome());
        ResourceLocation dimension = parseOptionalResourceLocation(start.dimension());
        RaidStartSettings.Center center = parseStartCenter(start.center(), structure, logger, file, "start.center");
        List<RaidStartSettings.Condition> conditions = parseStartConditions(start.conditions(), logger, file, "start.conditions");
        return new RaidStartSettings(trigger, start.cooldown_ticks(), entity, item, structure, biome, dimension,
                conditions, start.value(), start.count(), start.radius(), center);
    }

    private static RaidStartSettings parseStartSettingsNbt(JsonElement startNbt, Logger logger, Path file) {
        if (startNbt == null || startNbt.isJsonNull()) {
            return null;
        }
        try {
            JsonElement resolvedStartNbt = resolveNbtInput(startNbt, logger, file, "start_nbt");
            if (resolvedStartNbt == null || resolvedStartNbt.isJsonNull()) {
                return null;
            }
            CompoundTag tag = RelaxedNbtParser.parseCompound(resolvedStartNbt);
            String raw = firstNonBlank(getTagString(tag, "type"), getTagString(tag, "event"));
            RaidStartSettings.Trigger trigger = parseTrigger(raw);
            warnIfUnknownTrigger(raw, trigger, logger, file, "start_nbt");
            ResourceLocation entity = parseOptionalResourceLocation(getTagString(tag, "entity"));
            ResourceLocation item = parseOptionalResourceLocation(getTagString(tag, "item"));
            ResourceLocation structure = parseOptionalResourceLocation(getTagString(tag, "structure"));
            ResourceLocation biome = parseOptionalResourceLocation(getTagString(tag, "biome"));
            ResourceLocation dimension = parseOptionalResourceLocation(getTagString(tag, "dimension"));
            long cooldownTicks = getTagLong(tag, "cooldown_ticks");
            int value = getTagInt(tag, "value");
            int count = getTagInt(tag, "count");
            int radius = getTagInt(tag, "radius");
            RaidStartSettings.Center center = parseStartCenter(tag.contains("center", Tag.TAG_COMPOUND) ? tag.getCompound("center") : null,
                    structure, logger, file, "start_nbt.center");
            List<RaidStartSettings.Condition> conditions = parseStartConditions(tag.getList("conditions", Tag.TAG_COMPOUND),
                    logger, file, "start_nbt.conditions");
            return new RaidStartSettings(trigger, cooldownTicks, entity, item, structure, biome, dimension,
                    conditions, value, count, radius, center);
        } catch (CommandSyntaxException exception) {
            logger.warn("[Raidon] Invalid start_nbt in {}: {}", file.getFileName(), exception.getMessage());
            return null;
        }
    }

    private static ResourceLocation parseOptionalResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(value.trim());
    }

    private static RaidStartSettings.Center parseStartCenter(RaidFile.Start.Center center, ResourceLocation fallbackStructure,
                                                             Logger logger, Path file, String key) {
        if (center == null) {
            return RaidStartSettings.Center.DEFAULT;
        }
        RaidStartSettings.CenterType type = parseStartCenterType(center.type(), logger, file, key + ".type");
        ResourceLocation structure = parseOptionalResourceLocation(center.structure());
        if (type == RaidStartSettings.CenterType.STRUCTURE && structure == null && fallbackStructure == null) {
            logger.warn("[Raidon] {} in {} uses structure center but structure is missing", key, file.getFileName());
        }
        return new RaidStartSettings.Center(type, structure, center.search_radius(), center.prefer_nearest());
    }

    private static RaidStartSettings.Center parseStartCenter(CompoundTag center, ResourceLocation fallbackStructure,
                                                             Logger logger, Path file, String key) {
        if (center == null || center.isEmpty()) {
            return RaidStartSettings.Center.DEFAULT;
        }
        RaidStartSettings.CenterType type = parseStartCenterType(getTagString(center, "type"), logger, file, key + ".type");
        ResourceLocation structure = parseOptionalResourceLocation(getTagString(center, "structure"));
        if (type == RaidStartSettings.CenterType.STRUCTURE && structure == null && fallbackStructure == null) {
            logger.warn("[Raidon] {} in {} uses structure center but structure is missing", key, file.getFileName());
        }
        return new RaidStartSettings.Center(type, structure, getTagIntAny(center, "search_radius", "searchRadius"),
                getTagBooleanAny(center, "prefer_nearest", "preferNearest"));
    }

    private static RaidStartSettings.CenterType parseStartCenterType(String raw, Logger logger, Path file, String key) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        return switch (normalized) {
            case "", "event", "player", "current", "origin", "trigger" -> RaidStartSettings.CenterType.EVENT;
            case "spawn", "world_spawn", "worldspawn", "shared_spawn" -> RaidStartSettings.CenterType.WORLD_SPAWN;
            case "structure", "nearest_structure" -> RaidStartSettings.CenterType.STRUCTURE;
            default -> {
                logger.warn("[Raidon] Unknown {} '{}' in {}. Falling back to event center.", key, raw, file.getFileName());
                yield RaidStartSettings.CenterType.EVENT;
            }
        };
    }

    private static List<RaidStartSettings.Condition> parseStartConditions(List<RaidFile.Start.Condition> conditions,
                                                                          Logger logger, Path file, String key) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        List<RaidStartSettings.Condition> parsed = new ArrayList<>();
        for (RaidFile.Start.Condition condition : conditions) {
            if (condition == null || condition.type() == null || condition.type().isBlank()) {
                continue;
            }
            String type = normalizeStartConditionType(condition.type());
            if (type == null) {
                logger.warn("[Raidon] Unsupported {} '{}' in {}", key, condition.type(), file.getFileName());
                continue;
            }
            ResourceLocation biome = parseOptionalResourceLocation(condition.biome());
            ResourceLocation dimension = parseOptionalResourceLocation(condition.dimension());
            parsed.add(new RaidStartSettings.Condition(
                    type,
                    condition.min(),
                    condition.max(),
                    condition.value(),
                    biome,
                    dimension
            ));
        }
        return List.copyOf(parsed);
    }

    private static List<RaidStartSettings.Condition> parseStartConditions(ListTag conditions, Logger logger, Path file, String key) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        List<RaidStartSettings.Condition> parsed = new ArrayList<>();
        for (int i = 0; i < conditions.size(); i++) {
            if (!(conditions.get(i) instanceof CompoundTag tag)) {
                continue;
            }
            String type = getTagString(tag, "type");
            if (type == null || type.isBlank()) {
                continue;
            }
            String normalized = normalizeStartConditionType(type);
            if (normalized == null) {
                logger.warn("[Raidon] Unsupported {}[{}] '{}' in {}", key, i, type, file.getFileName());
                continue;
            }
            parsed.add(new RaidStartSettings.Condition(
                    normalized,
                    getTagInt(tag, "min"),
                    getTagInt(tag, "max"),
                    getTagInt(tag, "value"),
                    parseOptionalResourceLocation(getTagString(tag, "biome")),
                    parseOptionalResourceLocation(getTagString(tag, "dimension"))
            ));
        }
        return List.copyOf(parsed);
    }

    private static List<RaidFile.Action> resolveActions(List<RaidFile.Action> actions, JsonElement actionsNbt, boolean nbtSystemEnabled,
                                                        Logger logger, Path file, String key) {
        if (actions != null && !actions.isEmpty()) {
            return List.copyOf(actions);
        }
        if (!nbtSystemEnabled || actionsNbt == null || actionsNbt.isJsonNull()) {
            return List.of();
        }
        try {
            JsonElement resolvedActionsNbt = resolveNbtInput(actionsNbt, logger, file, key);
            if (resolvedActionsNbt == null || resolvedActionsNbt.isJsonNull()) {
                return List.of();
            }
            Tag root = RelaxedNbtParser.parseTag(resolvedActionsNbt);
            if (root instanceof CompoundTag compoundTag) {
                return parseActionsFromNbt(compoundTag);
            }
            if (root instanceof ListTag listTag) {
                return parseActionsFromNbtList(listTag);
            }
            logger.warn("[Raidon] Invalid {} in {}. Expected compound or list NBT.", key, file.getFileName());
            return List.of();
        } catch (CommandSyntaxException exception) {
            logger.warn("[Raidon] Invalid {} in {}: {}", key, file.getFileName(), exception.getMessage());
            return List.of();
        }
    }

    private static List<RaidFile.Action> parseActionsFromNbt(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return List.of();
        }
        List<RaidFile.Action> parsed = new ArrayList<>();
        if (root.contains("actions", Tag.TAG_LIST)) {
            ListTag actions = root.getList("actions", Tag.TAG_COMPOUND);
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i) instanceof CompoundTag actionTag) {
                    RaidFile.Action action = parseActionTag(actionTag);
                    if (action != null) {
                        parsed.add(action);
                    }
                }
            }
        } else {
            RaidFile.Action action = parseActionTag(root);
            if (action != null) {
                parsed.add(action);
            }
        }
        return List.copyOf(parsed);
    }

    private static List<RaidFile.Action> parseActionsFromNbtList(ListTag actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        List<RaidFile.Action> parsed = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i) instanceof CompoundTag actionTag) {
                RaidFile.Action action = parseActionTag(actionTag);
                if (action != null) {
                    parsed.add(action);
                }
            }
        }
        return List.copyOf(parsed);
    }

    private static RaidFile.Action parseActionTag(CompoundTag tag) {
        String type = getTagString(tag, "type");
        if (type == null || type.isBlank()) {
            return null;
        }
        return new RaidFile.Action(
                type,
                getTagString(tag, "text"),
                getTagString(tag, "summon"),
                getOptionalInt(tag, "value"),
                getTagString(tag, "command"),
                getOptionalLong(tag, "time"),
                getTagString(tag, "effect"),
                getOptionalInt(tag, "duration"),
                getOptionalInt(tag, "amplifier"),
                getTagString(tag, "sound"),
                getTagStringAny(tag, "sound_source", "soundSource"),
                getOptionalFloat(tag, "volume"),
                getOptionalFloat(tag, "pitch"),
                getTagString(tag, "target"),
                getTagString(tag, "entity"),
                getTagString(tag, "block"),
                getOptionalInt(tag, "x"),
                getOptionalInt(tag, "y"),
                getOptionalInt(tag, "z"),
                getOptionalInt(tag, "radius"),
                getOptionalIntAny(tag, "fade_in", "fadeIn"),
                getOptionalInt(tag, "stay"),
                getOptionalIntAny(tag, "fade_out", "fadeOut"),
                getOptionalIntAny(tag, "repeat_ticks", "repeatTicks")
        );
    }

    private static RaidStartSettings.Trigger parseTrigger(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        return switch (normalized) {
            case "enter_area", "on_enter_area", "area", "area_enter" -> RaidStartSettings.Trigger.ENTER_AREA;
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
    }

    private static void warnIfUnknownTrigger(String raw, RaidStartSettings.Trigger parsed, Logger logger, Path file, String key) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        if (parsed != RaidStartSettings.Trigger.MANUAL) {
            return;
        }
        if ("manual".equals(raw.trim().toLowerCase())) {
            return;
        }
        logger.warn("[Raidon] Unknown {} trigger '{}' in {}. Falling back to manual.", key, raw, file.getFileName());
    }

    private static String normalizeStartConditionType(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        return switch (normalized) {
            case "min_players", "minplayers" -> "min_players";
            case "max_players", "maxplayers" -> "max_players";
            case "y_between", "ybetween" -> "y_between";
            case "in_biome", "biome" -> "in_biome";
            case "in_dimension", "dimension" -> "in_dimension";
            case "time_of_day", "day_time", "timeofday" -> "time_of_day";
            case "moon_phase", "moonphase" -> "moon_phase";
            default -> null;
        };
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String getTagString(CompoundTag tag, String key) {
        if (tag == null || key == null || !tag.contains(key, Tag.TAG_STRING)) {
            return null;
        }
        String value = tag.getString(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static String getTagStringAny(CompoundTag tag, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = getTagString(tag, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int getTagInt(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : 0;
    }

    private static int getTagIntAny(CompoundTag tag, String... keys) {
        if (keys == null) {
            return 0;
        }
        for (String key : keys) {
            if (tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
                return tag.getInt(key);
            }
        }
        return 0;
    }

    private static long getTagLong(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : 0L;
    }

    private static boolean getTagBoolean(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_BYTE) && tag.getBoolean(key);
    }

    private static boolean getTagBooleanAny(CompoundTag tag, String... keys) {
        if (keys == null) {
            return false;
        }
        for (String key : keys) {
            if (tag != null && tag.contains(key, Tag.TAG_BYTE)) {
                return tag.getBoolean(key);
            }
        }
        return false;
    }

    private static Integer getOptionalInt(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : null;
    }

    private static Integer getOptionalIntAny(CompoundTag tag, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            if (tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
                return tag.getInt(key);
            }
        }
        return null;
    }

    private static Long getOptionalLong(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : null;
    }

    private static Float getOptionalFloat(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : null;
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
        Boolean raidAiEnabled = null;
        if (obj.has("raid_ai_enabled")) {
            raidAiEnabled = obj.get("raid_ai_enabled").getAsBoolean();
        } else if (obj.has("use_custom_ai")) {
            raidAiEnabled = obj.get("use_custom_ai").getAsBoolean();
        } else if (obj.has("ignore_custom_ai")) {
            raidAiEnabled = !obj.get("ignore_custom_ai").getAsBoolean();
        }
        return new MobTraits(burnInSun, canDrown, knockbackResistance, movementSpeedMultiplier, aiSpeedMultiplier, hardLeashMultiplier, raidAiEnabled);
    }

    private static String parseMobNbt(JsonElement nbt, boolean nbtSystemEnabled, Logger logger, Path file, int waveIndex, String mobType) {
        if (!nbtSystemEnabled || nbt == null || nbt.isJsonNull()) {
            return null;
        }
        JsonElement resolvedNbt = resolveNbtInput(nbt, logger, file,
                "waves[" + waveIndex + "].mobs[" + mobType + "].nbt");
        if (resolvedNbt == null || resolvedNbt.isJsonNull()) {
            return null;
        }
        if (resolvedNbt.isJsonPrimitive() && resolvedNbt.getAsJsonPrimitive().isString()) {
            String raw = resolvedNbt.getAsString();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return RelaxedNbtParser.normalizeStrictCompoundString(raw);
            } catch (CommandSyntaxException exception) {
                logger.warn("[Raidon] Invalid exact summon-style NBT for mob '{}' in wave {} ({}): {}",
                        mobType, waveIndex, file.getFileName(), exception.getMessage());
                return null;
            }
        }
        try {
            return RelaxedNbtParser.normalizeCompoundString(resolvedNbt);
        } catch (CommandSyntaxException exception) {
            logger.warn("[Raidon] Invalid NBT for mob '{}' in wave {} ({}): {}",
                    mobType, waveIndex, file.getFileName(), exception.getMessage());
            return null;
        }
    }

    private static JsonElement resolveNbtInput(JsonElement element, Logger logger, Path raidFile, String key) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return element;
        }
        String raw = element.getAsString();
        if (raw == null || raw.isBlank()) {
            return element;
        }
        Path nbtFile = resolveNbtFilePath(raw.trim(), raidFile);
        if (nbtFile == null) {
            return element;
        }
        try {
            return new JsonPrimitive(Files.readString(nbtFile));
        } catch (IOException exception) {
            logger.warn("[Raidon] Failed to read {} file '{}' in {}: {}",
                    key, nbtFile, raidFile.getFileName(), exception.getMessage());
            return null;
        }
    }

    private static Path resolveNbtFilePath(String raw, Path raidFile) {
        if (raw.isBlank()) {
            return null;
        }
        String reference;
        if (raw.startsWith("@file:")) {
            reference = raw.substring("@file:".length()).trim();
        } else if (raw.startsWith("file:")) {
            reference = raw.substring("file:".length()).trim();
        } else if (raw.endsWith(".snbt")) {
            reference = raw;
        } else {
            return null;
        }
        if (reference.isBlank()) {
            return null;
        }
        Path candidate = Path.of(reference);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        Path local = raidFile.getParent() == null ? candidate : raidFile.getParent().resolve(candidate).normalize();
        if (Files.exists(local)) {
            return local;
        }
        return FMLPaths.CONFIGDIR.get().resolve("raidon").resolve("nbt").resolve(candidate).normalize();
    }

    private static boolean parseBooleanFlag(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsInt() != 0;
            }
            if (primitive.isString()) {
                String normalized = primitive.getAsString().trim().toLowerCase();
                return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes") || normalized.equals("on");
            }
        }
        return false;
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
