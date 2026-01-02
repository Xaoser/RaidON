package ru.xaoser.raidon.runtime.config;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;

/**
 * Optional metadata used to validate whether a raid is allowed to start.
 */
public record RaidStartConditions(int minPlayers, boolean requireNight, List<ResourceLocation> allowedBiomes) {
    public static final RaidStartConditions DEFAULT = new RaidStartConditions(1, false, List.of());

    public RaidStartConditions(int minPlayers, boolean requireNight, List<ResourceLocation> allowedBiomes) {
        this.minPlayers = Math.max(1, minPlayers);
        this.requireNight = requireNight;
        this.allowedBiomes = List.copyOf(allowedBiomes == null ? Collections.emptyList() : allowedBiomes);
    }
}
