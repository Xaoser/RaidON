package ru.xaoser.raidon.runtime.raid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = Raidon.MODID)
public final class RaidManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, LoadedRaid> RAIDS = new HashMap<>();
    private static final Map<ResourceLocation, ActiveRaid> ACTIVE = new HashMap<>();

    private RaidManager() {}

    public static void clearDefinitions() {
        RAIDS.clear();
    }

    public static void clearAll() {
        clearDefinitions();
        ACTIVE.clear();
    }

    public static void registerRaid(ResourceLocation id, Raid raid, RaidSpawnSettings spawnSettings) {
        RAIDS.put(id, new LoadedRaid(raid, spawnSettings));
        LOGGER.info("Registered raid definition {}", id);
    }

    public static Optional<Raid> getRaid(ResourceLocation id) {
        return Optional.ofNullable(RAIDS.get(id)).map(LoadedRaid::raid);
    }

    public static Optional<LoadedRaid> getLoaded(ResourceLocation id) {
        return Optional.ofNullable(RAIDS.get(id));
    }

    public static StartResult startRaid(ResourceLocation id, ServerLevel level, BlockPos center) {
        LoadedRaid loaded = RAIDS.get(id);
        if (loaded == null) {
            return StartResult.NOT_FOUND;
        }
        if (ACTIVE.containsKey(id)) {
            return StartResult.ALREADY_ACTIVE;
        }
        ActiveRaid active = new ActiveRaid(loaded.raid(), level, center, loaded.spawnSettings());
        ACTIVE.put(id, active);
        LOGGER.info("Started raid {} at {}", id, center);
        sendProgress(active, false);
        return StartResult.STARTED;
    }

    public static boolean stopRaid(ResourceLocation id) {
        ActiveRaid raid = ACTIVE.remove(id);
        if (raid == null) {
            return false;
        }
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

    public static void onServerStop() {
        clearAll();
    }

    public static Map<ResourceLocation, LoadedRaid> raidsView() {
        return Map.copyOf(RAIDS);
    }

    public static Map<ResourceLocation, ActiveRaid> activeView() {
        return Map.copyOf(ACTIVE);
    }

    public static List<ActiveRaidStatus> activeStatuses() {
        List<ActiveRaidStatus> list = new ArrayList<>();
        for (var entry : ACTIVE.entrySet()) {
            ResourceLocation id = entry.getKey();
            ActiveRaid raid = entry.getValue();
            list.add(new ActiveRaidStatus(
                    id,
                    raid.center(),
                    raid.currentWaveZeroBased(),
                    raid.totalWaves(),
                    raid.aliveMobsInCurrentWave(),
                    raid.totalMobsInCurrentWave()
            ));
        }
        return list;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) tickAll();
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) {
            return;
        }
        for (ActiveRaid raid : ACTIVE.values()) {
            if (raid.handleMobDeath(mob)) {
                break;
            }
        }
    }

    public record LoadedRaid(Raid raid, RaidSpawnSettings spawnSettings) { }

    public enum StartResult {
        STARTED,
        NOT_FOUND,
        ALREADY_ACTIVE;

        public boolean isStarted() {
            return this == STARTED;
        }
    }

    public record ActiveRaidStatus(
            ResourceLocation id,
            BlockPos center,
            int waveIndex,
            int totalWaves,
            int aliveInWave,
            int totalInWave
    ) {}

    static void sendProgress(ActiveRaid raid, boolean finished) {
        if (RaidNetwork.channel() == null) {
            return;
        }

        RaidProgressS2CPacket packet = new RaidProgressS2CPacket(
                finished ? null : raid.payload()
        );

        for (ServerPlayer player : raid.level().players()) {
            if (isPlayerInRange(player, raid.center())) {
                RaidNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    private static boolean isPlayerInRange(ServerPlayer player, BlockPos center) {
        double maxDist = 128.0D;
        return player.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDist * maxDist;
    }
}
