package ru.xaoser.raidon.runtime.config;

/**
 * Defaults that describe how mobs should be spawned for a raid unless a wave overrides them.
 */
public record RaidSpawnRules(int defaultSpawnRadius, boolean enforceSurface) {
    public static final RaidSpawnRules DEFAULT = new RaidSpawnRules(16, true);

    public RaidSpawnRules(int defaultSpawnRadius, boolean enforceSurface) {
        this.defaultSpawnRadius = Math.max(1, defaultSpawnRadius);
        this.enforceSurface = enforceSurface;
    }
}
