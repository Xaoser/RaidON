package ru.xaoser.raidon.runtime.raid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import ru.xaoser.raidon.api.Raid;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public final class RaidManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, LoadedRaid> RAIDS = new HashMap<>();
    private static final Map<ResourceLocation, ActiveRaid> ACTIVE = new HashMap<>();

    private RaidManager() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(RaidManager.class);
    }

    public static void clear() {
        RAIDS.clear();
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
        return StartResult.STARTED;
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
            }
        }
    }

    public static void onServerStop() {
        ACTIVE.clear();
    }

    public static Map<ResourceLocation, LoadedRaid> raidsView() {
        return Map.copyOf(RAIDS);
    }

    public static Map<ResourceLocation, ActiveRaid> activeView() {
        return Map.copyOf(ACTIVE);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickAll();
        }
    }

    public record LoadedRaid(Raid raid, RaidSpawnSettings spawnSettings) { }

    public enum StartResult {
        STARTED,
        NOT_FOUND,
        ALREADY_ACTIVE
    }
}
