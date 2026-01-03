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

    public static RaidSpawnSettings sanitized(RaidSpawnSettings settings) {
        int min = Math.max(1, settings.minRadius());
        int max = Math.max(min, settings.maxRadius());
        int attempts = Math.max(1, settings.attemptsPerMob());
        return new RaidSpawnSettings(min, max, attempts, settings.requireGround(), settings.avoidWater());
    }
}
