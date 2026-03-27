package ru.xaoser.raidon.api.sup;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;

/**
 * Optional per-mob targeting controls loaded from raid configuration.
 */
public record MobTargeting(
        Double radius,
        boolean attackAll,
        Set<ResourceLocation> attackTypes,
        Set<ResourceLocation> ignoreTypes
) {
    public static final MobTargeting DEFAULT = new MobTargeting(null, false, Set.of(), Set.of());

    public MobTargeting {
        radius = radius != null && radius > 0.0D ? radius : null;
        attackTypes = attackTypes == null ? Set.of() : Set.copyOf(attackTypes);
        ignoreTypes = ignoreTypes == null ? Set.of() : Set.copyOf(ignoreTypes);
    }

    public static MobTargeting defaults() {
        return DEFAULT;
    }

    public Set<ResourceLocation> attackTypesView() {
        return Collections.unmodifiableSet(attackTypes);
    }

    public Set<ResourceLocation> ignoreTypesView() {
        return Collections.unmodifiableSet(ignoreTypes);
    }
}
