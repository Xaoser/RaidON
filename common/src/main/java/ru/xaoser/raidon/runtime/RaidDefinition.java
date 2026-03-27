package ru.xaoser.raidon.runtime;

import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.runtime.config.RaidSpawnRules;
import ru.xaoser.raidon.runtime.config.RaidStartConditions;

public record RaidDefinition(Raid raid, RaidStartConditions startConditions, RaidSpawnRules spawnRules) { }
