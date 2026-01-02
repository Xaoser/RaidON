package ru.xaoser.raidon.runtime;

import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.runtime.config.RaidSpawnRules;
import ru.xaoser.raidon.runtime.config.RaidStartConditions;

/**
 * Full raid definition parsed from configuration. Contains the built {@link Raid}
 * instance plus auxiliary metadata describing how and where the raid should start.
 */
public record RaidDefinition(Raid raid, RaidStartConditions startConditions, RaidSpawnRules spawnRules) { }
