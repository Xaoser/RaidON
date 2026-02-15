package ru.xaoser.raidon.runtime.raid.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import ru.xaoser.raidon.api.sup.SpawnBehavior;

public final class MobAiHelper {
    private MobAiHelper() {}

    public static void applyBehavior(Mob mob, SpawnBehavior behavior, BlockPos raidTargetPoint) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            return;
        }

        if (behavior == SpawnBehavior.HOSTILE) {
            setupHostile(pathfinder, raidTargetPoint);
        } else {
            setupNeutral(pathfinder, raidTargetPoint);
        }
    }

    private static void setupHostile(PathfinderMob mob, BlockPos raidTargetPoint) {
        mob.goalSelector.getAvailableGoals().clear();
        mob.targetSelector.getAvailableGoals().clear();

        if (raidTargetPoint != null) {
            mob.restrictTo(raidTargetPoint, 16);
        }

        mob.goalSelector.addGoal(0, new FloatGoal(mob));
        mob.goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(mob, 1.15D));

        // Some passive mobs do not have ATTACK_DAMAGE by default.
        // Without this check they can crash when trying to deal melee damage.
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.goalSelector.addGoal(2, new MeleeAttackGoal(mob, 1.2D, false));
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, true));
        }

        mob.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
        mob.goalSelector.addGoal(4, new LookAtPlayerGoal(mob, Player.class, 16.0F));
        mob.goalSelector.addGoal(5, new RandomLookAroundGoal(mob));
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
