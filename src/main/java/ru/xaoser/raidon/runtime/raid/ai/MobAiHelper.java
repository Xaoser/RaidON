package ru.xaoser.raidon.runtime.raid.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.SpawnBehavior;

import java.util.Set;
import java.util.function.Predicate;

public final class MobAiHelper {
    public static final String RAID_MOB_TAG = "raidon_raid_mob";
    private static final String RAID_BASE_DAMAGE_TAG = "raidon_base_damage";
    private static final double DEFAULT_BASE_DAMAGE = 2.0D;

    private MobAiHelper() {}

    public static void applyBehavior(Mob mob, SpawnBehavior behavior, MobTargeting targeting, BlockPos raidTargetPoint, int mobWanderRadius) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            return;
        }

        MobTargeting cfg = targeting == null ? MobTargeting.defaults() : targeting;

        if (raidTargetPoint != null) {
            pathfinder.restrictTo(raidTargetPoint, Math.max(4, mobWanderRadius));
        }

        if (behavior == SpawnBehavior.HOSTILE) {
            setupHostile(pathfinder, cfg);
        } else {
            setupNeutral(pathfinder, cfg);
        }
    }

    private static void setupHostile(PathfinderMob mob, MobTargeting targeting) {
        applyFollowRange(mob, targeting.radius());
        ensureBaseDamage(mob);

        if (!hasAttackDamageAttribute(mob)) {
            addGoalIfAbsent(mob, mob.goalSelector.getAvailableGoals(), 6, MeleeAttackGoal.class,
                    () -> new RaidMeleeAttackGoal(mob, 1.2D, false));
        }

        Predicate<LivingEntity> preferredFilter = createPreferredTargetFilter(targeting);
        Predicate<LivingEntity> fallbackFilter = createTargetFilter(targeting);

        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 0, NearestAttackableTargetGoal.class,
                () -> new NearestAttackableTargetGoal<>(mob, Player.class, 10, true, false, MobAiHelper::isAggroEligiblePlayer));

        if (!targeting.attackTypes().isEmpty() || targeting.attackAll()) {
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, preferredFilter));
            mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, fallbackFilter));
        } else {
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, fallbackFilter));
        }
    }

    private static void setupNeutral(PathfinderMob mob, MobTargeting targeting) {
        ensureBaseDamage(mob);
        Predicate<LivingEntity> fallbackFilter = createTargetFilter(targeting);

        addGoalIfAbsent(mob, mob.goalSelector.getAvailableGoals(), 3, MeleeAttackGoal.class,
                () -> new RaidMeleeAttackGoal(mob, 1.15D, false));
        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 0, NearestAttackableTargetGoal.class,
                () -> new NearestAttackableTargetGoal<>(mob, Player.class, 10, true, false, MobAiHelper::isAggroEligiblePlayer));
        mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, fallbackFilter));
    }

    private static Predicate<LivingEntity> createPreferredTargetFilter(MobTargeting targeting) {
        Set<ResourceLocation> attackTypes = targeting.attackTypes();
        Set<ResourceLocation> ignoreTypes = targeting.ignoreTypes();
        boolean attackAll = targeting.attackAll();

        return entity -> {
            if (entity == null || !entity.isAlive() || isRaidMob(entity)) {
                return false;
            }
            if (entity instanceof Player player && !isAggroEligiblePlayer(player)) {
                return false;
            }
            ResourceLocation typeId = EntityType.getKey(entity.getType());
            if (ignoreTypes.contains(typeId)) {
                return false;
            }
            if (attackAll) {
                return true;
            }
            return !attackTypes.isEmpty() && attackTypes.contains(typeId);
        };
    }

    private static Predicate<LivingEntity> createTargetFilter(MobTargeting targeting) {
        boolean attackAll = targeting.attackAll();
        Set<ResourceLocation> attackTypes = targeting.attackTypes();
        Set<ResourceLocation> ignoreTypes = targeting.ignoreTypes();

        return entity -> {
            if (entity == null || !entity.isAlive() || isRaidMob(entity)) {
                return false;
            }
            if (entity instanceof Player player && !isAggroEligiblePlayer(player)) {
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
                return attackTypes.contains(typeId) || entity instanceof Player;
            }
            return entity instanceof Player;
        };
    }

    private static boolean isRaidMob(LivingEntity entity) {
        return entity != null && entity.getTags().contains(RAID_MOB_TAG);
    }

    private static boolean isAggroEligiblePlayer(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !isRaidMob(player);
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

    private static boolean hasAttackDamageAttribute(PathfinderMob mob) {
        return mob.getAttribute(Attributes.ATTACK_DAMAGE) != null;
    }

    private static void ensureBaseDamage(PathfinderMob mob) {
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getBaseValue() <= 0.0D) {
            damage.setBaseValue(DEFAULT_BASE_DAMAGE);
            return;
        }
        if (!mob.getPersistentData().contains(RAID_BASE_DAMAGE_TAG)) {
            mob.getPersistentData().putDouble(RAID_BASE_DAMAGE_TAG, DEFAULT_BASE_DAMAGE);
        }
    }

    private static double getBaseDamage(PathfinderMob mob) {
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            return Math.max(0.0D, damage.getValue());
        }
        if (mob.getPersistentData().contains(RAID_BASE_DAMAGE_TAG)) {
            return Math.max(0.0D, mob.getPersistentData().getDouble(RAID_BASE_DAMAGE_TAG));
        }
        return DEFAULT_BASE_DAMAGE;
    }

    private interface GoalSupplier {
        Goal create();
    }

    private static void addGoalIfAbsent(PathfinderMob mob, Set<WrappedGoal> goals, int priority,
                                        Class<? extends Goal> goalClass, GoalSupplier supplier) {
        boolean exists = goals.stream().anyMatch(goal -> goalClass.isInstance(goal.getGoal()));
        if (!exists) {
            mob.goalSelector.addGoal(priority, supplier.create());
        }
    }

    private static void addTargetGoalIfAbsent(PathfinderMob mob, Set<WrappedGoal> goals, int priority,
                                              Class<? extends Goal> goalClass,
                                              java.util.function.Supplier<NearestAttackableTargetGoal<?>> supplier) {
        boolean exists = goals.stream().anyMatch(goal -> goalClass.isInstance(goal.getGoal()));
        if (!exists) {
            mob.targetSelector.addGoal(priority, supplier.get());
        }
    }

    private static final class RaidMeleeAttackGoal extends MeleeAttackGoal {
        private final PathfinderMob mob;

        private RaidMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(mob, speedModifier, followingTargetEvenIfNotSeen);
            this.mob = mob;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
            if (distToEnemySqr <= this.getAttackReachSqr(enemy) && this.isTimeToAttack()) {
                this.resetAttackCooldown();
                if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    mob.doHurtTarget(enemy);
                    return;
                }
                DamageSource source = mob.damageSources().mobAttack(mob);
                enemy.hurt(source, (float) getBaseDamage(mob));
            }
        }
    }
}
