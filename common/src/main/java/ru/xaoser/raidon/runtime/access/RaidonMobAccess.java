package ru.xaoser.raidon.runtime.access;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public final class RaidonMobAccess {
    private RaidonMobAccess() {
    }

    public static Set<WrappedGoal> getGoalSelectorGoals(PathfinderMob mob) {
        return goalSelector(mob).getAvailableGoals();
    }

    public static Set<WrappedGoal> getTargetSelectorGoals(PathfinderMob mob) {
        return targetSelector(mob).getAvailableGoals();
    }

    public static void addGoal(PathfinderMob mob, int priority, Goal goal) {
        goalSelector(mob).addGoal(priority, goal);
    }

    public static void addTargetGoal(PathfinderMob mob, int priority, Goal goal) {
        targetSelector(mob).addGoal(priority, goal);
    }

    private static GoalSelector goalSelector(PathfinderMob mob) {
        return invokeSelector(mob, "raidon$getGoalSelector");
    }

    private static GoalSelector targetSelector(PathfinderMob mob) {
        return invokeSelector(mob, "raidon$getTargetSelector");
    }

    private static GoalSelector invokeSelector(PathfinderMob mob, String methodName) {
        try {
            Method method = mob.getClass().getMethod(methodName);
            return (GoalSelector) method.invoke(mob);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("RaidON mixin accessors are not available for " + mob.getClass().getName(), exception);
        }
    }
}
