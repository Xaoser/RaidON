package ru.xaoser.raidon.runtime.raid;

import net.minecraft.resources.ResourceLocation;

public record RaidStartSettings(
        Trigger trigger,
        long cooldownTicks,
        ResourceLocation entity,
        ResourceLocation item,
        ResourceLocation structure,
        ResourceLocation biome,
        int value
) {
    public static final RaidStartSettings DEFAULT = new RaidStartSettings(Trigger.MANUAL, 0L, null, null, null, null, 0);

    public RaidStartSettings {
        trigger = trigger == null ? Trigger.MANUAL : trigger;
        cooldownTicks = Math.max(0L, cooldownTicks);
        value = Math.max(0, value);
    }

    public enum Trigger {
        MANUAL,
        PLAYER_JOIN_ANY,
        PLAYER_JOIN_SINGLEPLAYER,
        NIGHT_FALL,
        ON_KILL,
        ON_ITEM_PICKUP,
        ON_TRADE,
        ON_STRUCTURE_VISIT,
        ON_DIMENSION_CHANGE,
        ON_RESPAWN,
        ON_ENTER_BIOME,
        ON_DAY,
        ON_SUNSET,
        ON_MIDNIGHT
    }
}
