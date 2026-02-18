package ru.xaoser.raidon.runtime.raid.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.SpawnBehavior;

import java.util.Set;
import java.util.function.Predicate;

public final class MobAiHelper {
    private MobAiHelper() {}

    public static void applyBehavior(Mob mob, SpawnBehavior behavior, MobTargeting targeting, BlockPos raidTargetPoint) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            return;
        }

        MobTargeting cfg = targeting == null ? MobTargeting.defaults() : targeting;

        if (behavior == SpawnBehavior.HOSTILE) {
            setupHostile(pathfinder, cfg, raidTargetPoint);
        } else {
            setupNeutral(pathfinder, raidTargetPoint);
        }
    }

    private static void setupHostile(PathfinderMob mob, MobTargeting targeting, BlockPos raidTargetPoint) {
        mob.goalSelector.getAvailableGoals().clear();
        mob.targetSelector.getAvailableGoals().clear();

        if (raidTargetPoint != null) {
            mob.restrictTo(raidTargetPoint, 16);
        }

        applyFollowRange(mob, targeting.radius());

        mob.goalSelector.addGoal(0, new FloatGoal(mob));
        mob.goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(mob, 1.15D));

        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.goalSelector.addGoal(2, new MeleeAttackGoal(mob, 1.2D, false));
            Predicate<LivingEntity> filter = createTargetFilter(targeting);
            mob.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(mob, Player.class, true));
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, filter));
        }

        mob.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
        mob.goalSelector.addGoal(4, new LookAtPlayerGoal(mob, Player.class, 16.0F));
        mob.goalSelector.addGoal(5, new RandomLookAroundGoal(mob));
    }

    private static Predicate<LivingEntity> createTargetFilter(MobTargeting targeting) {
        boolean attackAll = targeting.attackAll();
        Set<ResourceLocation> attackTypes = targeting.attackTypes();
        Set<ResourceLocation> ignoreTypes = targeting.ignoreTypes();

        return entity -> {
            if (entity == null || !entity.isAlive()) {
                return false;
            }
            ResourceLocation typeId = EntityType.getKey(entity.getType());
            if (ignoreTypes.contains(typeId)) {
                return false;
            }
            if (attackAll) {
                return true;
            }
            if (!attackTypes.isEmpty()) {
                return attackTypes.contains(typeId);
            }
            return entity instanceof Player;
        };
    }

    private static void applyFollowRange(PathfinderMob mob, Double radius) {
        if (radius == null || radius <= 0.0D) {
            return;
        }
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(Math.max(2.0D, radius));
        }
    }

    private static void setupNeutral(PathfinderMob mob, BlockPos raidTargetPoint) {
        if (mob.getType() == EntityType.COW || mob.getType() == EntityType.SHEEP || mob.getType() == EntityType.PIG) {
            mob.goalSelector.getAvailableGoals().clear();
            mob.targetSelector.getAvailableGoals().clear();

            if (raidTargetPoint != null) {
                mob.restrictTo(raidTargetPoint, 16);
            }

            mob.goalSelector.addGoal(0, new FloatGoal(mob));
            mob.goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(mob, 1.1D));
            mob.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
            mob.goalSelector.addGoal(3, new LookAtPlayerGoal(mob, Player.class, 8.0F));
            mob.goalSelector.addGoal(4, new RandomLookAroundGoal(mob));
        }
    }
}
