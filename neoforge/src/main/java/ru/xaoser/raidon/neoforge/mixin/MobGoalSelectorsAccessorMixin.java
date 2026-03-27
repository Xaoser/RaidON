package ru.xaoser.raidon.neoforge.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobGoalSelectorsAccessorMixin {
    @Accessor("goalSelector")
    GoalSelector raidon$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector raidon$getTargetSelector();
}
