package ru.xaoser.raidon.runtime.raid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;

import java.util.*;

@Mod.EventBusSubscriber(modid = Raidon.MODID)
public final class RaidManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, LoadedRaid> RAIDS = new HashMap<>();
    private static final Map<ResourceLocation, ActiveRaid> ACTIVE = new HashMap<>();
    private static final Map<ResourceLocation, Long> AUTO_LAST_START = new HashMap<>();

    private RaidManager() {}

    public static void clearDefinitions() { RAIDS.clear(); }

    public static void clearAll() {
        clearDefinitions();
        ACTIVE.clear();
        AUTO_LAST_START.clear();
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
        ActiveRaid active = new ActiveRaid(loaded.raid(), level, points.mainPoint(), points.spawnPoint(), points.raidTargetPoint(),
                points.mobWanderRadius(), loaded.spawnSettings(), loaded.guiSettings());
        ACTIVE.put(id, active);
        LOGGER.info("Started raid {} center={} spawn={} target={} wanderRadius={}", id, points.mainPoint(), points.spawnPoint(), points.raidTargetPoint(), points.mobWanderRadius());
        sendProgress(active, false);
        return StartResult.STARTED;
    }

    public static boolean stopRaid(ResourceLocation id) {
        ActiveRaid raid = ACTIVE.remove(id);
        if (raid == null) return false;
        raid.forceComplete();
        sendProgress(raid, true);
        LOGGER.info("Raid {} was force-finished", id);
        return true;
    }

    public static void tickAll() {
        Iterator<Map.Entry<ResourceLocation, ActiveRaid>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, ActiveRaid> entry = it.next();
            ActiveRaid raid = entry.getValue();
            raid.tick();
            if (raid.isCompleted()) {
                it.remove();
                LOGGER.info("Raid {} completed", entry.getKey());
                sendProgress(raid, true);
            }
        }
    }

    public static void onServerStop() { clearAll(); }

    public static Map<ResourceLocation, LoadedRaid> raidsView() { return Map.copyOf(RAIDS); }
    public static Map<ResourceLocation, ActiveRaid> activeView() { return Map.copyOf(ACTIVE); }

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
        autoStartByTick(event.getServer());
        syncAllPlayers();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        autoStartOnLogin(player);
        syncPlayer(player);
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) return;
        for (ActiveRaid raid : ACTIVE.values()) {
            if (raid.handleMobDeath(mob)) break;
        }
    }

    public record LoadedRaid(Raid raid, RaidSpawnSettings spawnSettings, RaidPointSettings pointSettings,
                             RaidGuiSettings guiSettings, RaidStartSettings startSettings) { }

    public enum StartResult { STARTED, NOT_FOUND, ALREADY_ACTIVE; public boolean isStarted(){ return this == STARTED; } }

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

    private static void autoStartOnLogin(ServerPlayer player) {
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            RaidStartSettings.Trigger trigger = loaded.startSettings().trigger();
            if (trigger == RaidStartSettings.Trigger.PLAYER_JOIN_ANY ||
                    (trigger == RaidStartSettings.Trigger.PLAYER_JOIN_SINGLEPLAYER && player.server.isSingleplayer())) {
                tryAutoStart(entry.getKey(), loaded, player.serverLevel(), player.blockPosition(), player.server.getTickCount());
            }
        }
    }

    private static void autoStartByTick(MinecraftServer server) {
        long tick = server.getTickCount();
        for (Map.Entry<ResourceLocation, LoadedRaid> entry : RAIDS.entrySet()) {
            LoadedRaid loaded = entry.getValue();
            if (loaded.startSettings().trigger() != RaidStartSettings.Trigger.NIGHT_FALL) continue;
            ServerLevel level = server.overworld();
            if (level.isDay()) continue;
            tryAutoStart(entry.getKey(), loaded, level, level.getSharedSpawnPos(), tick);
        }
    }

    private static void tryAutoStart(ResourceLocation id, LoadedRaid loaded, ServerLevel level, BlockPos center, long tick) {
        if (ACTIVE.containsKey(id)) return;
        long cooldown = loaded.startSettings().cooldownTicks();
        long last = AUTO_LAST_START.getOrDefault(id, Long.MIN_VALUE / 2L);
        if (tick - last < cooldown) return;
        if (startRaid(id, level, center).isStarted()) AUTO_LAST_START.put(id, tick);
    }

    private static boolean isPlayerInRange(ServerPlayer player, BlockPos center, double maxDist) {
        return player.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDist * maxDist;
    }
}
