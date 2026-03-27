package ru.xaoser.raidon;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import ru.xaoser.raidon.runtime.config.RaidConfigLoader;
import ru.xaoser.raidon.runtime.item.RaidSummonItemConfigs;
import ru.xaoser.raidon.runtime.item.RaidSummonItemDataPack;
import ru.xaoser.raidon.runtime.raid.RaidManager;

public final class Raidon {
    public static final String MODID = "raidon";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized;
    private static int suppressedDataPackReloads;

    private Raidon() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RaidSummonItemConfigs.bootstrap(LOGGER);
        LOGGER.info("RaidON common bootstrap initialized");
    }

    public static void onServerStarted(MinecraftServer server) {
        if (server == null) {
            return;
        }
        LOGGER.info("RaidON server bootstrap started");
        RaidSummonItemConfigs.reload(LOGGER);
        suppressNextDataPackReload();
        RaidSummonItemDataPack.install(server, LOGGER);
        RaidConfigLoader.load(server, LOGGER);
        RaidManager.restoreActiveRaids(server);
    }

    public static void onDataPackReload(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        if (consumeSuppressedDataPackReload()) {
            return;
        }
        LOGGER.info("RaidON datapack reload detected, reloading raid configs");
        RaidConfigLoader.load(server, LOGGER);
    }

    public static synchronized void suppressNextDataPackReload() {
        suppressedDataPackReloads++;
    }

    public static void onServerStopping() {
        RaidManager.onServerStop();
    }

    private static synchronized boolean consumeSuppressedDataPackReload() {
        if (suppressedDataPackReloads <= 0) {
            return false;
        }
        suppressedDataPackReloads--;
        return true;
    }
}
