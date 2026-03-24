package ru.xaoser.raidon.runtime.raid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
    private static boolean activeStateRestored = false;

    private RaidManager() {}

    public static void clearDefinitions() { RAIDS.clear(); }

    public static void clearAll() {
        clearDefinitions();
        ACTIVE.clear();
        ACTIVE_TICK_ORDER.clear();
        activeTickCursor = 0;
        AUTO_LAST_START.clear();
        LAST_PLAYER_BIOME.clear();
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
        syncAllPlayers();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        autoStartOnLogin(player);
        syncPlayer(player);
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
            triggerEventRaids(player, RaidStartSettings.Trigger.ON_KILL, s -> s.entity() == null || s.entity().equals(killed));
        }
    }


    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItem().getItem().getItem());
        triggerEventRaids(player, RaidStartSettings.Trigger.ON_ITEM_PICKUP, s -> s.item() == null || s.item().equals(itemId));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        triggerEventRaids(player, RaidStartSettings.Trigger.ON_RESPAWN, s -> true);
    }

    @SubscribeEvent
    public static void onPlayerDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation toDimension = event.getTo().location();
        triggerEventRaids(player, RaidStartSettings.Trigger.ON_DIMENSION_CHANGE, settings -> settings.dimension() == null || settings.dimension().equals(toDimension));
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getMerchantOffer().getResult().getItem());
        triggerEventRaids(player, RaidStartSettings.Trigger.ON_TRADE, s -> s.item() == null || s.item().equals(itemId));
    }
    public record LoadedRaid(Raid raid, RaidSpawnSettings spawnSettings, RaidPointSettings pointSettings,
                             RaidGuiSettings guiSettings, RaidStartSettings startSettings) { }

    public enum StartResult { STARTED, NOT_FOUND, ALREADY_ACTIVE, AREA_BUSY; public boolean isStarted(){ return this == STARTED; } }

    public record ActiveRaidStatus(ResourceLocation id, BlockPos center, int waveIndex, int totalWaves, int aliveInWave, int totalInWave) {}

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

                if (trigger == RaidStartSettings.Trigger.ON_ENTER_BIOME) {
                    if (!Objects.equals(prevBiome, biomeId) && (settings.biome() == null || settings.biome().equals(biomeId)) && matchesAllConditions(player, settings)) {
                        tryAutoStart(entry.getKey(), loaded, player.serverLevel(), player.blockPosition(), tick);
                    }
                } else if (trigger == RaidStartSettings.Trigger.ON_STRUCTURE_VISIT) {
                    if (isNearStructure(player, settings.structure(), Math.max(32, settings.value())) && matchesAllConditions(player, settings)) {
                        tryAutoStart(entry.getKey(), loaded, player.serverLevel(), player.blockPosition(), tick);
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
        var structureCheck = player.serverLevel().structureManager().getStructureWithPieceAt(player.blockPosition(), key);
        if (structureCheck.isValid()) {
            return true;
        }
        return false;
    }

    private static void triggerEventRaids(ServerPlayer player, RaidStartSettings.Trigger trigger, java.util.function.Predicate<RaidStartSettings> predicate) {
        long tick = player.server.getTickCount();
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            if (loaded.startSettings().trigger() != trigger) continue;
            RaidStartSettings settings = loaded.startSettings();
            if (!predicate.test(settings)) continue;
            if (!matchesAllConditions(player, settings)) continue;
            tryAutoStart(entry.getKey(), loaded, player.serverLevel(), player.blockPosition(), tick);
        }
    }
    private static boolean matchesAllConditions(ServerPlayer player, RaidStartSettings settings) {
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
            default -> true;
        };
    }

    private static void autoStartOnLogin(ServerPlayer player) {
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            RaidStartSettings.Trigger trigger = loaded.startSettings().trigger();
            if (trigger == RaidStartSettings.Trigger.PLAYER_JOIN_ANY ||
                    (trigger == RaidStartSettings.Trigger.PLAYER_JOIN_SINGLEPLAYER && player.server.isSingleplayer())) {
                if (matchesAllConditions(player, loaded.startSettings())) {
                    tryAutoStart(entry.getKey(), loaded, player.serverLevel(), player.blockPosition(), player.server.getTickCount());
                }
            }
        }
    }

    private static void autoStartByTick(MinecraftServer server) {
        long tick = server.getTickCount();
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            ServerLevel level = server.overworld();
            RaidStartSettings.Trigger trigger = loaded.startSettings().trigger();
            if (trigger == RaidStartSettings.Trigger.NIGHT_FALL && !level.isDay()) {
                tryAutoStart(entry.getKey(), loaded, level, level.getSharedSpawnPos(), tick);
            } else if (trigger == RaidStartSettings.Trigger.ON_DAY && level.isDay()) {
                tryAutoStart(entry.getKey(), loaded, level, level.getSharedSpawnPos(), tick);
            } else if (trigger == RaidStartSettings.Trigger.ON_SUNSET && level.getDayTime() % 24000L >= 12000L && level.getDayTime() % 24000L <= 12200L) {
                tryAutoStart(entry.getKey(), loaded, level, level.getSharedSpawnPos(), tick);
            } else if (trigger == RaidStartSettings.Trigger.ON_MIDNIGHT && level.getDayTime() % 24000L >= 18000L && level.getDayTime() % 24000L <= 18200L) {
                tryAutoStart(entry.getKey(), loaded, level, level.getSharedSpawnPos(), tick);
            }
        }
    }

    private static void tryAutoStart(ResourceLocation id, LoadedRaid loaded, ServerLevel level, BlockPos center, long tick) {
        if (ACTIVE.containsKey(id)) return;
        long cooldown = loaded.startSettings().cooldownTicks();
        long last = AUTO_LAST_START.getOrDefault(id, Long.MIN_VALUE / 2L);
        if (tick - last < cooldown) return;
        if (startRaid(id, level, center).isStarted()) AUTO_LAST_START.put(id, tick);
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
