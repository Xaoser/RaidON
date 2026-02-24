package ru.xaoser.raidon.api.sup;

import net.minecraft.world.entity.PathfinderMob;

@FunctionalInterface
public interface MobAiTrigger {
    boolean shouldActivate(PathfinderMob mob, MobAiState state);
}
