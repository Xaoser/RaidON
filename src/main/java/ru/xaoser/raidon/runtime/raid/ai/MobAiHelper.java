package ru.xaoser.raidon.runtime.raid.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.SpawnBehavior;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

public final class MobAiHelper {
    public static final String RAID_MOB_TAG = "raidon_raid_mob";
    private static final String RAID_BASE_DAMAGE_TAG = "raidon_base_damage";
    private static final double DEFAULT_BASE_DAMAGE = 2.0D;
    private static final double MIN_PLAYER_AGGRO_RANGE = 80.0D;
    private static final double RETURN_TO_ZONE_SPEED = 1.25D;

    private MobAiHelper() {}

    public static void applyBehavior(Mob mob, SpawnBehavior behavior, MobTargeting targeting, BlockPos raidTargetPoint, int mobWanderRadius) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            return;
        }

        MobTargeting cfg = targeting == null ? MobTargeting.defaults() : targeting;
        sanitizeGoalSelector(pathfinder);

        if (raidTargetPoint != null) {
            pathfinder.restrictTo(raidTargetPoint, Math.max(4, mobWanderRadius));
            addGoalIfAbsent(pathfinder, pathfinder.goalSelector.getAvailableGoals(), 0, RaidReturnToRestrictionGoal.class,
                    () -> new RaidReturnToRestrictionGoal(pathfinder));
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
            addGoalIfAbsent(mob, mob.goalSelector.getAvailableGoals(), 1, MeleeAttackGoal.class,
                    () -> new RaidMeleeAttackGoal(mob, 1.25D, true));
        }

        Predicate<LivingEntity> preferredFilter = createPreferredTargetFilter(targeting);
        Predicate<LivingEntity> fallbackFilter = createTargetFilter(targeting);

        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 0, RaidNearestPlayerTargetGoal.class,
                () -> new RaidNearestPlayerTargetGoal(mob));

        if (!targeting.attackTypes().isEmpty() || targeting.attackAll()) {
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, preferredFilter));
            mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, fallbackFilter));
        } else {
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, fallbackFilter));
        }
    }

    private static void setupNeutral(PathfinderMob mob, MobTargeting targeting) {
        applyFollowRange(mob, targeting.radius());
        ensureBaseDamage(mob);
        Predicate<LivingEntity> fallbackFilter = createTargetFilter(targeting);

        addGoalIfAbsent(mob, mob.goalSelector.getAvailableGoals(), 1, MeleeAttackGoal.class,
                () -> new RaidMeleeAttackGoal(mob, 1.25D, true));
        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 0, RaidNearestPlayerTargetGoal.class,
                () -> new RaidNearestPlayerTargetGoal(mob));
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

    private static void sanitizeGoalSelector(PathfinderMob mob) {
        Set<WrappedGoal> goals = mob.goalSelector.getAvailableGoals();
        goals.removeIf(goal -> {
            Goal inner = goal.getGoal();
            return inner instanceof TemptGoal
                    || inner instanceof PanicGoal
                    || inner instanceof BreedGoal
                    || inner instanceof FollowParentGoal
                    || inner instanceof RandomStrollGoal;
        });
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
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            double configured = radius == null || radius <= 0.0D ? MIN_PLAYER_AGGRO_RANGE : radius;
            followRange.setBaseValue(Math.max(MIN_PLAYER_AGGRO_RANGE, configured));
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
                                              Class<? extends Goal> goalClass, GoalSupplier supplier) {
        boolean exists = goals.stream().anyMatch(goal -> goalClass.isInstance(goal.getGoal()));
        if (!exists) {
            mob.targetSelector.addGoal(priority, supplier.create());
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

    private static final class RaidNearestPlayerTargetGoal extends NearestAttackableTargetGoal<Player> {
        private RaidNearestPlayerTargetGoal(PathfinderMob mob) {
            super(mob, Player.class, 10, true, false, MobAiHelper::isAggroEligiblePlayer);
        }
    }

    private static final class RaidReturnToRestrictionGoal extends Goal {
        private final PathfinderMob mob;

        private RaidReturnToRestrictionGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.hasRestriction() && !mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public boolean canContinueToUse() {
            return mob.hasRestriction() && !mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public void start() {
            mob.setTarget(null);
            BlockPos restrictCenter = mob.getRestrictCenter();
            mob.getNavigation().moveTo(restrictCenter.getX() + 0.5D, restrictCenter.getY(), restrictCenter.getZ() + 0.5D, RETURN_TO_ZONE_SPEED);
        }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone()) {
                BlockPos restrictCenter = mob.getRestrictCenter();
                mob.getNavigation().moveTo(restrictCenter.getX() + 0.5D, restrictCenter.getY(), restrictCenter.getZ() + 0.5D, RETURN_TO_ZONE_SPEED);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }
}
