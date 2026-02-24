package ru.xaoser.raidon.api.sup;

import net.minecraft.world.entity.PathfinderMob;

@FunctionalInterface
public interface MobAiAction {
    void execute(PathfinderMob mob);
}
