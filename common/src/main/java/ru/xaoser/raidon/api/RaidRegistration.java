package ru.xaoser.raidon.api;

import ru.xaoser.raidon.runtime.raid.RaidGuiSettings;
import ru.xaoser.raidon.runtime.raid.RaidPointSettings;
import ru.xaoser.raidon.runtime.raid.RaidSpawnSettings;
import ru.xaoser.raidon.runtime.raid.RaidStartSettings;

/**
 * Programmatic raid registration payload for integrations.
 */
public record RaidRegistration(
        Raid raid,
        RaidSpawnSettings spawnSettings,
        RaidPointSettings pointSettings,
        RaidGuiSettings guiSettings,
        RaidStartSettings startSettings
) {
    public RaidRegistration {
        if (raid == null) {
            throw new IllegalArgumentException("raid must not be null");
        }
        spawnSettings = spawnSettings == null ? RaidSpawnSettings.defaults() : RaidSpawnSettings.sanitized(spawnSettings);
        pointSettings = pointSettings == null ? RaidPointSettings.DEFAULT : pointSettings;
        guiSettings = guiSettings == null ? RaidGuiSettings.DEFAULT : guiSettings;
        startSettings = startSettings == null ? RaidStartSettings.DEFAULT : startSettings;
    }

    public static RaidRegistration defaults(Raid raid) {
        return new RaidRegistration(raid, RaidSpawnSettings.defaults(), RaidPointSettings.DEFAULT, RaidGuiSettings.DEFAULT, RaidStartSettings.DEFAULT);
    }
}
