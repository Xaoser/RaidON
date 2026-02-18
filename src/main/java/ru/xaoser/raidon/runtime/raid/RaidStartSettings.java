package ru.xaoser.raidon.runtime.raid;

public record RaidStartSettings(
        Trigger trigger,
        long cooldownTicks
) {
    public static final RaidStartSettings DEFAULT = new RaidStartSettings(Trigger.MANUAL, 0L);

    public RaidStartSettings {
        trigger = trigger == null ? Trigger.MANUAL : trigger;
        cooldownTicks = Math.max(0L, cooldownTicks);
    }

    public enum Trigger {
        MANUAL,
        PLAYER_JOIN_ANY,
        PLAYER_JOIN_SINGLEPLAYER,
        NIGHT_FALL
    }
}
