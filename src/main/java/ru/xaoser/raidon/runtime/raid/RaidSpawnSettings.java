package ru.xaoser.raidon.runtime.raid;

public record RaidSpawnSettings(
        int minRadius,
        int maxRadius,
        int attemptsPerMob,
        boolean requireGround,
        boolean avoidWater
) {
    public static RaidSpawnSettings defaults() {
        return new RaidSpawnSettings(8, 16, 8, true, true);
    }
}
