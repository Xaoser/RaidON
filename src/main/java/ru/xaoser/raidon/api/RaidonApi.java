package ru.xaoser.raidon.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import ru.xaoser.raidon.runtime.raid.RaidGuiSettings;
import ru.xaoser.raidon.runtime.raid.RaidManager;
import ru.xaoser.raidon.runtime.raid.RaidPointSettings;
import ru.xaoser.raidon.runtime.raid.RaidSpawnSettings;
import ru.xaoser.raidon.runtime.raid.RaidStartSettings;

import java.util.List;
import java.util.Optional;

/**
 * Public integration API for using RaidON as a library from other mods.
 */
public final class RaidonApi {
    private RaidonApi() {}

    public static void registerRaid(RaidRegistration registration) {
        Raid raid = registration.raid();
        RaidManager.registerRaid(
                raid.id(),
                raid,
                registration.spawnSettings(),
                registration.pointSettings(),
                registration.guiSettings(),
                registration.startSettings()
        );
    }

    public static void registerRaid(Raid raid) {
        registerRaid(RaidRegistration.defaults(raid));
    }

    public static void registerRaid(
            Raid raid,
            RaidSpawnSettings spawnSettings,
            RaidPointSettings pointSettings,
            RaidGuiSettings guiSettings
    ) {
        registerRaid(new RaidRegistration(raid, spawnSettings, pointSettings, guiSettings, RaidStartSettings.DEFAULT));
    }

    public static void registerRaid(
            Raid raid,
            RaidSpawnSettings spawnSettings,
            RaidPointSettings pointSettings,
            RaidGuiSettings guiSettings,
            RaidStartSettings startSettings
    ) {
        registerRaid(new RaidRegistration(raid, spawnSettings, pointSettings, guiSettings, startSettings));
    }

    public static RaidManager.StartResult startRaid(ResourceLocation id, ServerLevel level, BlockPos center) {
        return RaidManager.startRaid(id, level, center);
    }

    public static boolean stopRaid(ResourceLocation id) {
        return RaidManager.stopRaid(id);
    }

    public static Optional<Raid> getRaid(ResourceLocation id) {
        return RaidManager.getRaid(id);
    }

    public static List<RaidManager.ActiveRaidStatus> activeRaids() {
        return RaidManager.activeStatuses();
    }
}
