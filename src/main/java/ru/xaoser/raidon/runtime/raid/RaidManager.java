package ru.xaoser.raidon.runtime.raid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.raid.ai.MobAiHelper;

import java.util.*;

@Mod.EventBusSubscriber(modid = Raidon.MODID)
public final class RaidManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RAID_TICKS_PER_SERVER_TICK = 4;

    private static final Map<ResourceLocation, LoadedRaid> RAIDS = new HashMap<>();
    private static final Map<ResourceLocation, ActiveRaid> ACTIVE = new HashMap<>();
    private static final List<ResourceLocation> ACTIVE_TICK_ORDER = new ArrayList<>();
    private static int activeTickCursor = 0;
    private static final Map<ResourceLocation, Long> AUTO_LAST_START = new HashMap<>();
    private static final Map<UUID, ResourceLocation> LAST_PLAYER_BIOME = new HashMap<>();
    private static final Map<ResourceLocation, Map<UUID, Integer>> PLAYER_TRIGGER_PROGRESS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> GLOBAL_TRIGGER_PROGRESS = new HashMap<>();
    private static final Map<ResourceLocation, PendingTriggerStart> READY_TRIGGER_STARTS = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_TIMED_TRIGGER_OCCURRENCE = new HashMap<>();
    private static final Map<ResourceLocation, Set<UUID>> AREA_TRIGGER_PLAYERS_IN_RANGE = new HashMap<>();
    private static final Map<ResourceLocation, Set<UUID>> STRUCTURE_TRIGGER_PLAYERS_IN_RANGE = new HashMap<>();
    private static boolean activeStateRestored = false;

    private RaidManager() {}

    public static void clearDefinitions() {
        RAIDS.clear();
        PLAYER_TRIGGER_PROGRESS.clear();
        GLOBAL_TRIGGER_PROGRESS.clear();
        READY_TRIGGER_STARTS.clear();
        LAST_TIMED_TRIGGER_OCCURRENCE.clear();
        AREA_TRIGGER_PLAYERS_IN_RANGE.clear();
        STRUCTURE_TRIGGER_PLAYERS_IN_RANGE.clear();
    }

    public static void clearAll() {
        clearDefinitions();
        ACTIVE.clear();
        ACTIVE_TICK_ORDER.clear();
        activeTickCursor = 0;
        AUTO_LAST_START.clear();
        LAST_PLAYER_BIOME.clear();
        PLAYER_TRIGGER_PROGRESS.clear();
        GLOBAL_TRIGGER_PROGRESS.clear();
        READY_TRIGGER_STARTS.clear();
        LAST_TIMED_TRIGGER_OCCURRENCE.clear();
        AREA_TRIGGER_PLAYERS_IN_RANGE.clear();
        STRUCTURE_TRIGGER_PLAYERS_IN_RANGE.clear();
        activeStateRestored = false;
    }

    public static void registerRaid(ResourceLocation id, Raid raid, RaidSpawnSettings spawnSettings,
                                    RaidPointSettings pointSettings, RaidGuiSettings guiSettings, RaidStartSettings startSettings) {
        RAIDS.put(id, new LoadedRaid(raid, spawnSettings, pointSettings, guiSettings, startSettings));
        LOGGER.info("Registered raid definition {}", id);
    }

    public static Optional<Raid> getRaid(ResourceLocation id) { return Optional.ofNullable(RAIDS.get(id)).map(LoadedRaid::raid); }
    public static Optional<LoadedRaid> getLoaded(ResourceLocation id) { return Optional.ofNullable(RAIDS.get(id)); }

    public static StartResult startRaid(ResourceLocation id, ServerLevel level, BlockPos center) {
        LoadedRaid loaded = RAIDS.get(id);
        if (loaded == null) return StartResult.NOT_FOUND;
        if (ACTIVE.containsKey(id)) return StartResult.ALREADY_ACTIVE;

        RaidPointSettings.ResolvedPoints points = loaded.pointSettings().resolve(center);
        if (hasActiveRaidConflict(level, points.mainPoint(), loaded.spawnSettings().maxRadius() + 16.0D)) {
            return StartResult.AREA_BUSY;
        }
        ActiveRaid active = new ActiveRaid(loaded.raid(), level, points.mainPoint(), points.spawnPoint(), points.raidTargetPoint(),
                points.mobWanderRadius(), loaded.spawnSettings(), loaded.guiSettings());
        ACTIVE.put(id, active);
        ACTIVE_TICK_ORDER.add(id);
        active.triggerRaidStart();
        persistActiveState(level.getServer());
        LOGGER.info("Started raid {} center={} spawn={} target={} wanderRadius={}", id, points.mainPoint(), points.spawnPoint(), points.raidTargetPoint(), points.mobWanderRadius());
        sendProgress(active, false);
        return StartResult.STARTED;
    }

    public static boolean stopRaid(ResourceLocation id) {
        ActiveRaid raid = ACTIVE.remove(id);
        if (raid == null) return false;
        ACTIVE_TICK_ORDER.remove(id);
        if (activeTickCursor >= ACTIVE_TICK_ORDER.size()) {
            activeTickCursor = 0;
        }
        raid.forceComplete();
        persistActiveState(raid.level().getServer());
        sendProgress(raid, true);
        LOGGER.info("Raid {} was force-finished", id);
        return true;
    }

    public static void tickAll() {
        if (ACTIVE.isEmpty()) {
            ACTIVE_TICK_ORDER.clear();
            activeTickCursor = 0;
            return;
        }

        ACTIVE_TICK_ORDER.removeIf(id -> !ACTIVE.containsKey(id));
        for (ResourceLocation id : ACTIVE.keySet()) {
            if (!ACTIVE_TICK_ORDER.contains(id)) {
                ACTIVE_TICK_ORDER.add(id);
            }
        }
        if (ACTIVE_TICK_ORDER.isEmpty()) {
            return;
        }

        int budget = Math.min(MAX_RAID_TICKS_PER_SERVER_TICK, ACTIVE_TICK_ORDER.size());
        for (int i = 0; i < budget; i++) {
            if (ACTIVE_TICK_ORDER.isEmpty()) {
                activeTickCursor = 0;
                break;
            }
            if (activeTickCursor >= ACTIVE_TICK_ORDER.size()) {
                activeTickCursor = 0;
            }
            ResourceLocation id = ACTIVE_TICK_ORDER.get(activeTickCursor);
            ActiveRaid raid = ACTIVE.get(id);
            if (raid == null) {
                ACTIVE_TICK_ORDER.remove(activeTickCursor);
                continue;
            }

            raid.tick();
            if (raid.isCompleted()) {
                ACTIVE.remove(id);
                ACTIVE_TICK_ORDER.remove(activeTickCursor);
                LOGGER.info("Raid {} completed", id);
                sendProgress(raid, true);
                if (activeTickCursor >= ACTIVE_TICK_ORDER.size()) {
                    activeTickCursor = 0;
                }
            } else {
                activeTickCursor++;
                if (activeTickCursor >= ACTIVE_TICK_ORDER.size()) {
                    activeTickCursor = 0;
                }
            }
        }
    }

    public static void onServerStop() { clearAll(); }

    public static Map<ResourceLocation, LoadedRaid> raidsView() { return Map.copyOf(RAIDS); }
    public static Map<ResourceLocation, ActiveRaid> activeView() { return Map.copyOf(ACTIVE); }

    public static void restoreActiveRaids(MinecraftServer server) {
        ACTIVE.clear();
        ACTIVE_TICK_ORDER.clear();
        activeTickCursor = 0;

        for (CompoundTag tag : RaidSavedData.get(server).activeRaidTags()) {
            ActiveRaid raid = restoreActiveRaid(server, tag);
            if (raid == null) {
                continue;
            }
            ResourceLocation id = raid.raidId();
            ACTIVE.put(id, raid);
            ACTIVE_TICK_ORDER.add(id);
            LOGGER.info("Restored raid {} center={} wave={}", id, raid.center(), raid.currentWaveZeroBased());
        }

        activeStateRestored = true;
        rebindLoadedRaidMobs(server);
        persistActiveState(server);
    }

    public static List<ActiveRaidStatus> activeStatuses() {
        List<ActiveRaidStatus> list = new ArrayList<>();
        for (var entry : ACTIVE.entrySet()) {
            ActiveRaid raid = entry.getValue();
            list.add(new ActiveRaidStatus(entry.getKey(), raid.center(), raid.currentWaveZeroBased(), raid.totalWaves(),
                    raid.aliveMobsInCurrentWave(), raid.totalMobsInCurrentWave()));
        }
        return list;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickAll();
        persistActiveState(event.getServer());
        autoStartByTick(event.getServer());
        autoStartByPlayerTick(event.getServer());
        processReadyTriggerStarts(event.getServer());
        syncAllPlayers();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        autoStartOnLogin(player);
        syncPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        LAST_PLAYER_BIOME.remove(playerId);
        for (Set<UUID> playersInRange : AREA_TRIGGER_PLAYERS_IN_RANGE.values()) {
            playersInRange.remove(playerId);
        }
        for (Set<UUID> playersInRange : STRUCTURE_TRIGGER_PLAYERS_IN_RANGE.values()) {
            playersInRange.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!activeStateRestored) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) {
            return;
        }
        if (!mob.getTags().contains(MobAiHelper.RAID_MOB_TAG)) {
            return;
        }
        if (rebindRaidMob(level, mob)) {
            return;
        }
        mob.discard();
        LOGGER.info("Discarded stale raid mob {} because no active raid tracks it", mob.getUUID());
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) return;
        for (ActiveRaid raid : ACTIVE.values()) {
            if (raid.handleMobDeath(mob)) break;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            ResourceLocation killed = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            handlePlayerTriggerEvent(player, RaidStartSettings.Trigger.ON_KILL,
                    settings -> settings.entity() == null || settings.entity().equals(killed));
        }
    }


    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItem().getItem().getItem());
        handlePlayerTriggerEvent(player, RaidStartSettings.Trigger.ON_ITEM_PICKUP,
                settings -> settings.item() == null || settings.item().equals(itemId));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        handlePlayerTriggerEvent(player, RaidStartSettings.Trigger.ON_RESPAWN, settings -> true);
    }

    @SubscribeEvent
    public static void onPlayerDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation toDimension = event.getTo().location();
        handlePlayerTriggerEvent(player, RaidStartSettings.Trigger.ON_DIMENSION_CHANGE,
                settings -> settings.dimension() == null || settings.dimension().equals(toDimension));
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getMerchantOffer().getResult().getItem());
        handlePlayerTriggerEvent(player, RaidStartSettings.Trigger.ON_TRADE,
                settings -> settings.item() == null || settings.item().equals(itemId));
    }
    public record LoadedRaid(Raid raid, RaidSpawnSettings spawnSettings, RaidPointSettings pointSettings,
                             RaidGuiSettings guiSettings, RaidStartSettings startSettings) { }

    public enum StartResult { STARTED, NOT_FOUND, ALREADY_ACTIVE, AREA_BUSY; public boolean isStarted(){ return this == STARTED; } }

    public record ActiveRaidStatus(ResourceLocation id, BlockPos center, int waveIndex, int totalWaves, int aliveInWave, int totalInWave) {}

    private record PendingTriggerStart(ResourceKey<Level> dimension, BlockPos center) {}

    private record TriggerStartContext(ServerLevel level, BlockPos center) {}

    static void sendProgress(ActiveRaid raid, boolean finished) {
        if (RaidNetwork.channel() == null) return;
        RaidProgressS2CPacket packet = new RaidProgressS2CPacket(finished ? null : raid.payload());
        for (ServerPlayer player : raid.level().players()) {
            if (finished || isPlayerInRange(player, raid.center(), raid.hudRange())) {
                RaidNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    private static void syncPlayer(ServerPlayer player) {
        if (RaidNetwork.channel() == null) return;
        RaidProgressS2CPacket packet = new RaidProgressS2CPacket(null);
        for (ActiveRaid raid : ACTIVE.values()) {
            if (raid.level() == player.serverLevel() && isPlayerInRange(player, raid.center(), raid.hudRange())) {
                packet = new RaidProgressS2CPacket(raid.payload());
                break;
            }
        }
        RaidNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static void syncAllPlayers() {
        if (ACTIVE.isEmpty()) return;
        Set<ServerPlayer> players = new HashSet<>();
        for (ActiveRaid raid : ACTIVE.values()) {
            players.addAll(raid.level().players());
        }
        for (ServerPlayer player : players) {
            syncPlayer(player);
        }
    }


    private static void autoStartByPlayerTick(MinecraftServer server) {
        long tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceLocation biomeId = player.serverLevel().registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getKey(player.serverLevel().getBiome(player.blockPosition()).value());
            ResourceLocation prevBiome = LAST_PLAYER_BIOME.put(player.getUUID(), biomeId);

            for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
                LoadedRaid loaded = entry.getValue();
                RaidStartSettings settings = loaded.startSettings();
                RaidStartSettings.Trigger trigger = settings.trigger();

                if (trigger == RaidStartSettings.Trigger.ENTER_AREA) {
                    TriggerStartContext context = resolveTriggerContext(player.serverLevel(), player.blockPosition(), settings);
                    if (context == null) {
                        continue;
                    }
                    boolean inArea = isPlayerInRange(player, context.center(), settings.effectiveRadius(48));
                    Set<UUID> playersInRange = AREA_TRIGGER_PLAYERS_IN_RANGE.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>());
                    if (inArea) {
                        if (!playersInRange.add(player.getUUID())) {
                            continue;
                        }
                        handlePlayerTriggerOccurrence(entry.getKey(), loaded, player, tick, context);
                    } else {
                        playersInRange.remove(player.getUUID());
                    }
                } else if (trigger == RaidStartSettings.Trigger.ON_ENTER_BIOME) {
                    if (!Objects.equals(prevBiome, biomeId) && (settings.biome() == null || settings.biome().equals(biomeId))) {
                        handlePlayerTriggerOccurrence(entry.getKey(), loaded, player, tick, null);
                    }
                } else if (trigger == RaidStartSettings.Trigger.ON_STRUCTURE_VISIT) {
                    boolean nearStructure = isNearStructure(player, settings.structure(), settings.effectiveRadius(32));
                    Set<UUID> playersInRange = STRUCTURE_TRIGGER_PLAYERS_IN_RANGE.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>());
                    if (nearStructure) {
                        if (!playersInRange.add(player.getUUID())) {
                            continue;
                        }
                        handlePlayerTriggerOccurrence(entry.getKey(), loaded, player, tick, null);
                    } else {
                        playersInRange.remove(player.getUUID());
                    }
                }
            }
        }
    }

    private static boolean isNearStructure(ServerPlayer player, ResourceLocation structureId, int radius) {
        if (structureId == null) {
            return false;
        }
        var key = net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, structureId);
        if (player.serverLevel().structureManager().getStructureWithPieceAt(player.blockPosition(), key).isValid()) {
            return true;
        }
        int scanRadius = Math.max(16, radius);
        int step = Math.max(8, Math.min(32, scanRadius / 4));
        BlockPos origin = player.blockPosition();
        for (int dx = -scanRadius; dx <= scanRadius; dx += step) {
            for (int dz = -scanRadius; dz <= scanRadius; dz += step) {
                if ((dx * dx + dz * dz) > scanRadius * scanRadius) {
                    continue;
                }
                BlockPos probe = origin.offset(dx, 0, dz);
                if (player.serverLevel().structureManager().getStructureWithPieceAt(probe, key).isValid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void handlePlayerTriggerEvent(ServerPlayer player, RaidStartSettings.Trigger trigger, java.util.function.Predicate<RaidStartSettings> predicate) {
        long tick = player.server.getTickCount();
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            if (loaded.startSettings().trigger() != trigger) continue;
            RaidStartSettings settings = loaded.startSettings();
            if (!predicate.test(settings)) continue;
            handlePlayerTriggerOccurrence(entry.getKey(), loaded, player, tick, null);
        }
    }

    private static void handlePlayerTriggerOccurrence(ResourceLocation id, LoadedRaid loaded, ServerPlayer player, long tick,
                                                      TriggerStartContext preResolvedContext) {
        if (ACTIVE.containsKey(id)) {
            return;
        }
        RaidStartSettings settings = loaded.startSettings();
        if (!matchesAllConditions(player, settings)) {
            return;
        }
        int requiredCount = settings.requiredCount();
        int progress = incrementPlayerTriggerProgress(id, player.getUUID(), requiredCount);
        if (progress < requiredCount) {
            return;
        }
        TriggerStartContext context = preResolvedContext != null ? preResolvedContext
                : resolveTriggerContext(player.serverLevel(), player.blockPosition(), settings);
        if (context == null) {
            return;
        }
        markReadyTriggerStart(id, context.level(), context.center());
        tryAutoStart(id, loaded, context.level(), context.center(), tick);
    }

    private static void handleJoinTriggerOccurrence(ResourceLocation id, LoadedRaid loaded, ServerPlayer player, long tick) {
        if (ACTIVE.containsKey(id)) {
            return;
        }
        RaidStartSettings settings = loaded.startSettings();
        if (!matchesAllConditions(player, settings)) {
            return;
        }
        int requiredCount = settings.requiredCount();
        int progress = incrementGlobalTriggerProgress(id, requiredCount);
        if (progress < requiredCount) {
            return;
        }
        TriggerStartContext context = resolveTriggerContext(player.serverLevel(), player.blockPosition(), settings);
        if (context == null) {
            return;
        }
        markReadyTriggerStart(id, context.level(), context.center());
        tryAutoStart(id, loaded, context.level(), context.center(), tick);
    }

    private static void handleGlobalTriggerOccurrence(ResourceLocation id, LoadedRaid loaded, ServerLevel level, BlockPos center, long tick) {
        if (ACTIVE.containsKey(id)) {
            return;
        }
        int requiredCount = loaded.startSettings().requiredCount();
        int progress = incrementGlobalTriggerProgress(id, requiredCount);
        if (progress < requiredCount) {
            return;
        }
        TriggerStartContext context = resolveTriggerContext(level, center, loaded.startSettings());
        if (context == null) {
            return;
        }
        markReadyTriggerStart(id, context.level(), context.center());
        tryAutoStart(id, loaded, context.level(), context.center(), tick);
    }

    private static boolean matchesAllConditions(ServerPlayer player, RaidStartSettings settings) {
        if (settings.dimension() != null && !settings.dimension().equals(player.serverLevel().dimension().location())) {
            return false;
        }
        if (settings.biome() != null) {
            ResourceLocation biomeId = player.serverLevel().registryAccess().registryOrThrow(Registries.BIOME)
                    .getKey(player.serverLevel().getBiome(player.blockPosition()).value());
            if (!settings.biome().equals(biomeId)) {
                return false;
            }
        }
        if (settings.conditions().isEmpty()) {
            return true;
        }
        for (RaidStartSettings.Condition condition : settings.conditions()) {
            if (!matchesCondition(player, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesCondition(ServerPlayer player, RaidStartSettings.Condition condition) {
        return switch (condition.type()) {
            case "min_players" -> player.serverLevel().players().size() >= Math.max(1, condition.value());
            case "max_players" -> player.serverLevel().players().size() <= Math.max(0, condition.value());
            case "y_between" -> {
                int y = player.blockPosition().getY();
                yield y >= condition.min() && y <= condition.max();
            }
            case "in_biome" -> {
                if (condition.biome() == null) {
                    yield true;
                }
                ResourceLocation biomeId = player.serverLevel().registryAccess().registryOrThrow(Registries.BIOME)
                        .getKey(player.serverLevel().getBiome(player.blockPosition()).value());
                yield condition.biome().equals(biomeId);
            }
            case "in_dimension" -> {
                if (condition.dimension() == null) {
                    yield true;
                }
                yield condition.dimension().equals(player.serverLevel().dimension().location());
            }
            case "time_of_day" -> matchesTimeOfDay(player.serverLevel(), condition.min(), condition.max());
            case "moon_phase" -> player.serverLevel().getMoonPhase() == condition.value();
            default -> false;
        };
    }

    private static void autoStartOnLogin(ServerPlayer player) {
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            RaidStartSettings.Trigger trigger = loaded.startSettings().trigger();
            if (trigger == RaidStartSettings.Trigger.PLAYER_JOIN_ANY ||
                    (trigger == RaidStartSettings.Trigger.PLAYER_JOIN_SINGLEPLAYER && player.server.isSingleplayer())) {
                handleJoinTriggerOccurrence(entry.getKey(), loaded, player, player.server.getTickCount());
            }
        }
    }

    private static void autoStartByTick(MinecraftServer server) {
        long tick = server.getTickCount();
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            RaidStartSettings.Trigger trigger = loaded.startSettings().trigger();
            if (trigger != RaidStartSettings.Trigger.NIGHT_FALL
                    && trigger != RaidStartSettings.Trigger.ON_DAY
                    && trigger != RaidStartSettings.Trigger.ON_SUNSET
                    && trigger != RaidStartSettings.Trigger.ON_MIDNIGHT) {
                continue;
            }

            TriggerStartContext context = resolveTimedTriggerContext(server, loaded);
            if (context == null) {
                continue;
            }

            long occurrenceKey = resolveTimedTriggerOccurrence(context.level(), trigger);
            if (occurrenceKey == Long.MIN_VALUE) {
                continue;
            }
            Long previousOccurrence = LAST_TIMED_TRIGGER_OCCURRENCE.put(entry.getKey(), occurrenceKey);
            if (Objects.equals(previousOccurrence, occurrenceKey)) {
                continue;
            }
            handleGlobalTriggerOccurrence(entry.getKey(), loaded, context.level(), context.center(), tick);
        }
    }

    private static void tryAutoStart(ResourceLocation id, LoadedRaid loaded, ServerLevel level, BlockPos center, long tick) {
        if (ACTIVE.containsKey(id)) return;
        long cooldown = loaded.startSettings().cooldownTicks();
        long last = AUTO_LAST_START.getOrDefault(id, Long.MIN_VALUE / 2L);
        if (tick - last < cooldown) return;
        if (startRaid(id, level, center).isStarted()) {
            AUTO_LAST_START.put(id, tick);
            clearTriggerProgress(id);
        }
    }

    private static void processReadyTriggerStarts(MinecraftServer server) {
        if (READY_TRIGGER_STARTS.isEmpty()) {
            return;
        }
        long tick = server.getTickCount();
        for (Map.Entry<ResourceLocation, PendingTriggerStart> entry : List.copyOf(READY_TRIGGER_STARTS.entrySet())) {
            LoadedRaid loaded = RAIDS.get(entry.getKey());
            if (loaded == null) {
                clearTriggerProgress(entry.getKey());
                LAST_TIMED_TRIGGER_OCCURRENCE.remove(entry.getKey());
                continue;
            }
            ServerLevel level = server.getLevel(entry.getValue().dimension());
            if (level == null) {
                continue;
            }
            tryAutoStart(entry.getKey(), loaded, level, entry.getValue().center(), tick);
        }
    }

    private static int incrementPlayerTriggerProgress(ResourceLocation id, UUID playerId, int requiredCount) {
        Map<UUID, Integer> progressByPlayer = PLAYER_TRIGGER_PROGRESS.computeIfAbsent(id, ignored -> new HashMap<>());
        int next = Math.min(requiredCount, progressByPlayer.getOrDefault(playerId, 0) + 1);
        progressByPlayer.put(playerId, next);
        return next;
    }

    private static int incrementGlobalTriggerProgress(ResourceLocation id, int requiredCount) {
        int next = Math.min(requiredCount, GLOBAL_TRIGGER_PROGRESS.getOrDefault(id, 0) + 1);
        GLOBAL_TRIGGER_PROGRESS.put(id, next);
        return next;
    }

    private static void markReadyTriggerStart(ResourceLocation id, ServerLevel level, BlockPos center) {
        READY_TRIGGER_STARTS.put(id, new PendingTriggerStart(level.dimension(), center.immutable()));
    }

    private static void clearTriggerProgress(ResourceLocation id) {
        PLAYER_TRIGGER_PROGRESS.remove(id);
        GLOBAL_TRIGGER_PROGRESS.remove(id);
        READY_TRIGGER_STARTS.remove(id);
    }

    private static TriggerStartContext resolveTimedTriggerContext(MinecraftServer server, LoadedRaid loaded) {
        RaidStartSettings settings = loaded.startSettings();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (matchesAllConditions(player, settings)) {
                return new TriggerStartContext(player.serverLevel(), player.blockPosition());
            }
        }

        if (requiresPlayerContext(settings)) {
            return null;
        }

        ServerLevel level = resolveTriggerLevel(server, settings);
        if (level == null || !matchesGlobalConditions(level, settings)) {
            return null;
        }
        return new TriggerStartContext(level, level.getSharedSpawnPos());
    }

    private static ServerLevel resolveTriggerLevel(MinecraftServer server, RaidStartSettings settings) {
        if (settings.dimension() == null) {
            return server.overworld();
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, settings.dimension()));
    }

    private static boolean requiresPlayerContext(RaidStartSettings settings) {
        if (settings.biome() != null) {
            return true;
        }
        for (RaidStartSettings.Condition condition : settings.conditions()) {
            String type = condition.type();
            if ("y_between".equals(type) || "in_biome".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGlobalConditions(ServerLevel level, RaidStartSettings settings) {
        if (settings.conditions().isEmpty()) {
            return true;
        }
        for (RaidStartSettings.Condition condition : settings.conditions()) {
            if (!matchesGlobalCondition(level, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesGlobalCondition(ServerLevel level, RaidStartSettings.Condition condition) {
        return switch (condition.type()) {
            case "min_players" -> level.players().size() >= Math.max(1, condition.value());
            case "max_players" -> level.players().size() <= Math.max(0, condition.value());
            case "in_dimension" -> condition.dimension() == null || condition.dimension().equals(level.dimension().location());
            case "time_of_day" -> matchesTimeOfDay(level, condition.min(), condition.max());
            case "moon_phase" -> level.getMoonPhase() == condition.value();
            default -> false;
        };
    }

    private static boolean matchesTimeOfDay(ServerLevel level, int min, int max) {
        long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
        int normalizedMin = Math.floorMod(min, 24000);
        int normalizedMax = Math.floorMod(max, 24000);
        if (normalizedMin <= normalizedMax) {
            return timeOfDay >= normalizedMin && timeOfDay <= normalizedMax;
        }
        return timeOfDay >= normalizedMin || timeOfDay <= normalizedMax;
    }

    private static TriggerStartContext resolveTriggerContext(ServerLevel level, BlockPos origin, RaidStartSettings settings) {
        if (level == null || settings == null) {
            return null;
        }
        BlockPos fallbackCenter = origin == null ? level.getSharedSpawnPos() : origin.immutable();
        RaidStartSettings.Center center = settings.center();
        return switch (center.type()) {
            case EVENT -> new TriggerStartContext(level, fallbackCenter);
            case WORLD_SPAWN -> new TriggerStartContext(level, level.getSharedSpawnPos().immutable());
            case STRUCTURE -> {
                BlockPos structureCenter = resolveStructureCenter(level, fallbackCenter, settings);
                yield structureCenter == null ? null : new TriggerStartContext(level, structureCenter);
            }
        };
    }

    private static BlockPos resolveStructureCenter(ServerLevel level, BlockPos origin, RaidStartSettings settings) {
        RaidStartSettings.Center center = settings.center();
        ResourceLocation structureId = center.effectiveStructure(settings.structure());
        if (structureId == null) {
            return null;
        }
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<? extends Holder<Structure>> holderOptional = registry.getHolder(key);
        if (holderOptional.isEmpty()) {
            return null;
        }
        Holder<Structure> holder = holderOptional.get();
        int searchRadius = center.effectiveSearchRadius(Math.max(settings.effectiveRadius(64), 128));
        if (center.preferNearest()) {
            BlockPos nearest = findNearestStructureCenter(level, holder, origin, searchRadius);
            if (nearest != null) {
                return nearest;
            }
        }
        return findStructureCenterByScan(level, holder.value(), origin, searchRadius);
    }

    private static BlockPos findNearestStructureCenter(ServerLevel level, Holder<Structure> holder, BlockPos origin, int searchRadius) {
        int chunkRadius = Math.max(1, (searchRadius + 15) / 16);
        var result = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holder), origin, chunkRadius, false);
        if (result == null || result.getFirst() == null) {
            return null;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(result.getFirst(), holder.value());
        if (start != null && start.isValid()) {
            return start.getBoundingBox().getCenter().immutable();
        }
        return result.getFirst().immutable();
    }

    private static BlockPos findStructureCenterByScan(ServerLevel level, Structure structure, BlockPos origin, int searchRadius) {
        int chunkRadius = Math.max(1, (searchRadius + 15) / 16);
        double maxDistanceSq = (double) searchRadius * searchRadius;
        ChunkPos originChunk = new ChunkPos(origin);
        Set<Long> seen = new HashSet<>();

        for (int ring = 0; ring <= chunkRadius; ring++) {
            for (int chunkX = originChunk.x - ring; chunkX <= originChunk.x + ring; chunkX++) {
                for (int chunkZ = originChunk.z - ring; chunkZ <= originChunk.z + ring; chunkZ++) {
                    if (ring > 0 && Math.abs(chunkX - originChunk.x) != ring && Math.abs(chunkZ - originChunk.z) != ring) {
                        continue;
                    }
                    for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(chunkX, chunkZ),
                            candidate -> candidate == structure)) {
                        if (!start.isValid()) {
                            continue;
                        }
                        BlockPos center = start.getBoundingBox().getCenter();
                        if (center.distSqr(origin) > maxDistanceSq) {
                            continue;
                        }
                        if (!seen.add(center.asLong())) {
                            continue;
                        }
                        return center.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static long resolveTimedTriggerOccurrence(ServerLevel level, RaidStartSettings.Trigger trigger) {
        long dayIndex = Math.floorDiv(level.getDayTime(), 24000L);
        long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
        return switch (trigger) {
            case ON_DAY -> timeOfDay <= 200L ? dayIndex : Long.MIN_VALUE;
            case ON_SUNSET -> timeOfDay >= 12000L && timeOfDay <= 12200L ? dayIndex : Long.MIN_VALUE;
            case NIGHT_FALL -> timeOfDay >= 12500L && timeOfDay <= 12700L ? dayIndex : Long.MIN_VALUE;
            case ON_MIDNIGHT -> timeOfDay >= 18000L && timeOfDay <= 18200L ? dayIndex : Long.MIN_VALUE;
            default -> Long.MIN_VALUE;
        };
    }

    private static void persistActiveState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RaidSavedData.get(server).replaceFromActive(ACTIVE.values());
    }

    private static ActiveRaid restoreActiveRaid(MinecraftServer server, CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
        if (id == null || dimensionId == null) {
            LOGGER.warn("Skipping saved raid with invalid id or dimension");
            return null;
        }
        LoadedRaid loaded = RAIDS.get(id);
        if (loaded == null) {
            LOGGER.warn("Skipping saved raid {} because definition is missing", id);
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            LOGGER.warn("Skipping saved raid {} because level {} is missing", id, dimensionId);
            return null;
        }
        BlockPos center = readBlockPos(tag, "center");
        BlockPos spawnPoint = readBlockPos(tag, "spawnPoint");
        BlockPos raidTargetPoint = readBlockPos(tag, "raidTargetPoint");
        int mobWanderRadius = Math.max(4, tag.getInt("mobWanderRadius"));

        ActiveRaid active = new ActiveRaid(loaded.raid(), level, center, spawnPoint, raidTargetPoint,
                mobWanderRadius, loaded.spawnSettings(), loaded.guiSettings());
        if (!active.restoreState(tag)) {
            return null;
        }
        return active;
    }

    private static BlockPos readBlockPos(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        if (values.length >= 3) {
            return new BlockPos(values[0], values[1], values[2]);
        }
        return BlockPos.ZERO;
    }

    private static void rebindLoadedRaidMobs(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (!(entity instanceof net.minecraft.world.entity.Mob mob)) {
                    continue;
                }
                if (!mob.getTags().contains(MobAiHelper.RAID_MOB_TAG)) {
                    continue;
                }
                if (rebindRaidMob(level, mob)) {
                    continue;
                }
                mob.discard();
                LOGGER.info("Discarded stale loaded raid mob {} because no active raid tracks it", mob.getUUID());
            }
        }
    }

    private static boolean rebindRaidMob(ServerLevel level, net.minecraft.world.entity.Mob mob) {
        for (ActiveRaid raid : ACTIVE.values()) {
            if (raid.level() != level) {
                continue;
            }
            if (raid.rebindTrackedMob(mob)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveRaidConflict(ServerLevel level, BlockPos center, double requestedRadius) {
        double requestedRadiusSq = requestedRadius * requestedRadius;
        for (ActiveRaid active : ACTIVE.values()) {
            if (active.level() != level) {
                continue;
            }
            double distanceSq = active.center().distSqr(center);
            double minDistance = active.conflictRadius() + requestedRadius;
            if (distanceSq <= minDistance * minDistance || distanceSq <= requestedRadiusSq) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlayerInRange(ServerPlayer player, BlockPos center, double maxDist) {
        return player.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDist * maxDist;
    }
}
