package ru.xaoser.raidon.runtime.config;

public record RaidSpawnRules(int defaultSpawnRadius, boolean enforceSurface) {
    public static final RaidSpawnRules DEFAULT = new RaidSpawnRules(16, true);

    public RaidSpawnRules(int defaultSpawnRadius, boolean enforceSurface) {
        this.defaultSpawnRadius = Math.max(1, defaultSpawnRadius);
        this.enforceSurface = enforceSurface;
    }
}
