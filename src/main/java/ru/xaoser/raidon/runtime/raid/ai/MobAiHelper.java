package ru.xaoser.raidon.runtime.raid.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.levelgen.Heightmap;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.MobTraits;
import ru.xaoser.raidon.api.sup.SpawnBehavior;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

public final class MobAiHelper {
    public static final String RAID_MOB_TAG = "raidon_raid_mob";
    private static final String RAID_BASE_DAMAGE_TAG = "raidon_base_damage";
    private static final String RAID_BASE_SPEED_TAG = "raidon_base_speed";
    private static final String RAID_STATE_TAG = "raidon_ai_state";
    private static final String RAID_PENDING_RETURN_TAG = "raidon_pending_return";
    private static final double DEFAULT_BASE_DAMAGE = 2.0D;
    private static final double MIN_PLAYER_AGGRO_RANGE = 80.0D;
    private static final double MIN_PURSUE_RANGE = 160.0D;
    private static final double DEFAULT_AI_SPEED_MULTIPLIER = 1.0D;
    private static final double DEFAULT_HARD_LEASH_MULTIPLIER = 1.75D;

    private MobAiHelper() {}

    public static void applyBehavior(Mob mob, SpawnBehavior behavior, MobTargeting targeting, MobTraits tuning,
                                     BlockPos raidTargetPoint, int mobWanderRadius) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            return;
        }

        MobTargeting cfg = targeting == null ? MobTargeting.defaults() : targeting;
        BehaviorSettings settings = BehaviorSettings.from(tuning);
        sanitizeGoalSelector(pathfinder);

        if (raidTargetPoint != null) {
            pathfinder.restrictTo(raidTargetPoint, Math.max(4, mobWanderRadius));
            addGoalIfAbsent(pathfinder, pathfinder.goalSelector.getAvailableGoals(), 0, RaidReturnToRestrictionGoal.class,
                    () -> new RaidReturnToRestrictionGoal(pathfinder, settings));
            addGoalIfAbsent(pathfinder, pathfinder.goalSelector.getAvailableGoals(), 6, RaidPatrolWithinRestrictionGoal.class,
                    () -> new RaidPatrolWithinRestrictionGoal(pathfinder, settings));
        }

        addGoalIfAbsent(pathfinder, pathfinder.goalSelector.getAvailableGoals(), 0, RaidStateGoal.class,
                () -> new RaidStateGoal(pathfinder));
        addGoalIfAbsent(pathfinder, pathfinder.goalSelector.getAvailableGoals(), 0, RaidTargetSanitizerGoal.class,
                () -> new RaidTargetSanitizerGoal(pathfinder));

        if (behavior == SpawnBehavior.HOSTILE) {
            setupHostile(pathfinder, cfg, settings);
        } else {
            setupNeutral(pathfinder, cfg, settings);
        }
    }

    private static void setupHostile(PathfinderMob mob, MobTargeting targeting, BehaviorSettings settings) {
        applyFollowRange(mob, targeting.radius());
        applyMovementSpeed(mob, settings.movementSpeedMultiplier());
        ensureBaseDamage(mob);

        if (!hasAttackDamageAttribute(mob)) {
            addGoalIfAbsent(mob, mob.goalSelector.getAvailableGoals(), 1, MeleeAttackGoal.class,
                    () -> new RaidMeleeAttackGoal(mob, 1.0D * settings.aiSpeedMultiplier(), true));
        }

        Predicate<LivingEntity> preferredFilter = createPreferredTargetFilter(targeting);
        Predicate<LivingEntity> fallbackFilter = createTargetFilter(targeting);

        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 0, RaidNearestPlayerTargetGoal.class,
                () -> new RaidNearestPlayerTargetGoal(mob));

        if (!targeting.attackTypes().isEmpty() || targeting.attackAll()) {
            addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 1, RaidNearestPreferredTargetGoal.class,
                    () -> new RaidNearestPreferredTargetGoal(mob, preferredFilter));
            addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 2, RaidNearestFallbackTargetGoal.class,
                    () -> new RaidNearestFallbackTargetGoal(mob, fallbackFilter));
        } else {
            addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 1, RaidNearestFallbackTargetGoal.class,
                    () -> new RaidNearestFallbackTargetGoal(mob, fallbackFilter));
        }
    }

    private static void setupNeutral(PathfinderMob mob, MobTargeting targeting, BehaviorSettings settings) {
        applyFollowRange(mob, targeting.radius());
        applyMovementSpeed(mob, settings.movementSpeedMultiplier());
        ensureBaseDamage(mob);
        Predicate<LivingEntity> fallbackFilter = createTargetFilter(targeting);

        addGoalIfAbsent(mob, mob.goalSelector.getAvailableGoals(), 1, MeleeAttackGoal.class,
                () -> new RaidMeleeAttackGoal(mob, 1.0D * settings.aiSpeedMultiplier(), true));
        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 0, RaidNearestPlayerTargetGoal.class,
                () -> new RaidNearestPlayerTargetGoal(mob));
        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 1, RaidNearestFallbackTargetGoal.class,
                () -> new RaidNearestFallbackTargetGoal(mob, fallbackFilter));
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
            // Keep a high minimum so chase behavior does not drop target just due to short follow distance.
            followRange.setBaseValue(Math.max(MIN_PURSUE_RANGE, configured));
        }
    }

    private static void applyMovementSpeed(PathfinderMob mob, Double movementSpeedMultiplier) {
        if (movementSpeedMultiplier == null) {
            return;
        }
        AttributeInstance movementSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            double multiplier = Math.max(0.1D, movementSpeedMultiplier);
            // Persist original base speed once to avoid compounding multiplication when applyBehavior runs again.
            double storedBase = mob.getPersistentData().contains(RAID_BASE_SPEED_TAG)
                    ? mob.getPersistentData().getDouble(RAID_BASE_SPEED_TAG)
                    : movementSpeed.getBaseValue();
            if (storedBase <= 0.0D) {
                storedBase = movementSpeed.getBaseValue();
            }
            mob.getPersistentData().putDouble(RAID_BASE_SPEED_TAG, storedBase);
            movementSpeed.setBaseValue(Math.max(0.01D, storedBase * multiplier));
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

    private static void setState(PathfinderMob mob, RaidState state) {
        mob.getPersistentData().putString(RAID_STATE_TAG, state.name());
    }

    private static boolean isPendingReturn(PathfinderMob mob) {
        return mob.getPersistentData().getBoolean(RAID_PENDING_RETURN_TAG);
    }

    private static void setPendingReturn(PathfinderMob mob, boolean pending) {
        mob.getPersistentData().putBoolean(RAID_PENDING_RETURN_TAG, pending);
    }

    private enum RaidState {
        GOING_AGGRESIVE,
        IDLE_AGGRESIVE,
        RETURNING,
        ATTACKING
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

    private abstract static class PersistentRaidTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        private final Predicate<LivingEntity> retentionFilter;

        private PersistentRaidTargetGoal(PathfinderMob mob, Class<T> targetType, int randomInterval, boolean mustSee,
                                         boolean mustReach, Predicate<LivingEntity> targetPredicate,
                                         Predicate<LivingEntity> retentionFilter) {
            super(mob, targetType, randomInterval, mustSee, mustReach, targetPredicate);
            this.retentionFilter = retentionFilter;
        }

        @Override
        public boolean canUse() {
            LivingEntity current = mob.getTarget();
            if (isRetainable(current)) {
                return false;
            }
            return super.canUse();
        }

        @Override
        public void stop() {
            LivingEntity previous = mob.getTarget();
            super.stop();
            if (isRetainable(previous)) {
                mob.setTarget(previous);
            }
        }

        private boolean isRetainable(LivingEntity entity) {
            return entity != null && entity.isAlive() && retentionFilter.test(entity);
        }
    }

    private static final class RaidNearestPlayerTargetGoal extends PersistentRaidTargetGoal<Player> {
        private RaidNearestPlayerTargetGoal(PathfinderMob mob) {
            super(mob, Player.class, 10, true, false, MobAiHelper::isAggroEligiblePlayer,
                    MobAiHelper::isAggroEligiblePlayer);
        }
    }

    private static final class RaidNearestPreferredTargetGoal extends PersistentRaidTargetGoal<LivingEntity> {
        private RaidNearestPreferredTargetGoal(PathfinderMob mob, Predicate<LivingEntity> preferredFilter) {
            super(mob, LivingEntity.class, 10, true, false, preferredFilter, preferredFilter);
        }
    }

    private static final class RaidNearestFallbackTargetGoal extends PersistentRaidTargetGoal<LivingEntity> {
        private RaidNearestFallbackTargetGoal(PathfinderMob mob, Predicate<LivingEntity> fallbackFilter) {
            super(mob, LivingEntity.class, 10, true, false, fallbackFilter, fallbackFilter);
        }
    }

    private static final class RaidStateGoal extends Goal {
        private final PathfinderMob mob;
        private int tickCooldown;

        private RaidStateGoal(PathfinderMob mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }

        @Override
        public void tick() {
            if (--tickCooldown > 0) {
                return;
            }
            tickCooldown = 5;

            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                setState(mob, RaidState.ATTACKING);
                return;
            }
            if (mob.hasRestriction() && !mob.isWithinRestriction(mob.blockPosition())) {
                // If there was recent combat outside restriction, state is set by return goal to RETURNING.
                if (!isPendingReturn(mob)) {
                    setState(mob, RaidState.GOING_AGGRESIVE);
                }
                return;
            }
            setState(mob, RaidState.IDLE_AGGRESIVE);
        }
    }

    private static final class RaidTargetSanitizerGoal extends Goal {
        private final PathfinderMob mob;
        private int checkCooldown;

        private RaidTargetSanitizerGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }

        @Override
        public void tick() {
            // Small interval avoids doing player-mode checks every tick while still clearing invalid targets quickly.
            if (--checkCooldown > 0) {
                return;
            }
            checkCooldown = 5;

            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            if (!target.isAlive()) {
                mob.setTarget(null);
                return;
            }
            if (target instanceof Player && !isAggroEligiblePlayer(target)) {
                mob.setTarget(null);
            }
        }
    }

    private static final class RaidReturnToRestrictionGoal extends Goal {
        private final PathfinderMob mob;
        private final BehaviorSettings settings;
        private int moveCooldown;
        private boolean hadActiveTarget;

        private RaidReturnToRestrictionGoal(PathfinderMob mob, BehaviorSettings settings) {
            this.mob = mob;
            this.settings = settings;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mob.getTarget();
            boolean hasActiveTarget = target != null && target.isAlive();
            if (hasActiveTarget) {
                hadActiveTarget = true;
                return false;
            }

            if (hadActiveTarget && mob.hasRestriction() && !mob.isWithinRestriction(mob.blockPosition())) {
                // Target was lost/killed outside restriction: mark pending return state.
                setPendingReturn(mob, true);
            }
            hadActiveTarget = false;

            if (!mob.hasRestriction()) {
                return false;
            }
            return !mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public boolean canContinueToUse() {
            if (hasActiveTarget()) {
                return false;
            }
            if (!mob.hasRestriction()) {
                return false;
            }
            return !mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public void start() {
            moveCooldown = 0;
            issueMoveToRestriction();
        }

        @Override
        public void tick() {
            // Repath only when needed to avoid constant moveTo resets that create stop-start movement.
            if (--moveCooldown <= 0 || mob.getNavigation().isDone()) {
                issueMoveToRestriction();
            }
        }

        @Override
        public void stop() {
            if (mob.hasRestriction() && mob.isWithinRestriction(mob.blockPosition())) {
                setPendingReturn(mob, false);
            }
        }

        private void issueMoveToRestriction() {
            BlockPos restrictCenter = mob.getRestrictCenter();
            double centerY = resolveNavigationY(mob, restrictCenter);
            mob.getNavigation().moveTo(restrictCenter.getX() + 0.5D, centerY, restrictCenter.getZ() + 0.5D,
                    1.0D * settings.aiSpeedMultiplier());
            moveCooldown = 20;

            if (isPendingReturn(mob)) {
                setState(mob, RaidState.RETURNING);
            } else {
                setState(mob, RaidState.GOING_AGGRESIVE);
            }
        }

        private boolean hasActiveTarget() {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive();
        }
    }

    private static final class RaidPatrolWithinRestrictionGoal extends Goal {
        private static final double ARRIVAL_DISTANCE_SQR = 2.25D;

        private final PathfinderMob mob;
        private final BehaviorSettings settings;
        private int recalcTicks;
        private int reselectionCooldown;
        private BlockPos currentPatrolTarget;

        private RaidPatrolWithinRestrictionGoal(PathfinderMob mob, BehaviorSettings settings) {
            this.mob = mob;
            this.settings = settings;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.hasRestriction() && mob.getTarget() == null && mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public boolean canContinueToUse() {
            return mob.hasRestriction() && mob.getTarget() == null && mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public void start() {
            recalcTicks = 0;
            reselectionCooldown = 0;
            currentPatrolTarget = null;
            moveToNextPoint();
        }

        @Override
        public void tick() {
            if (recalcTicks > 0) {
                recalcTicks--;
            }
            if (reselectionCooldown > 0) {
                reselectionCooldown--;
            }

            boolean hasDestination = currentPatrolTarget != null;
            boolean reachedDestination = hasDestination && mob.distanceToSqr(
                    currentPatrolTarget.getX() + 0.5D,
                    currentPatrolTarget.getY(),
                    currentPatrolTarget.getZ() + 0.5D
            ) <= ARRIVAL_DISTANCE_SQR;

            // Keep patrol fluid by selecting a new point quickly after arrival, but with a short throttle to avoid spam.
            if (reselectionCooldown <= 0 && (recalcTicks <= 0 || mob.getNavigation().isDone() || reachedDestination)) {
                moveToNextPoint();
            }
        }

        private void moveToNextPoint() {
            BlockPos center = mob.getRestrictCenter();
            int radius = Math.max(4, Mth.floor(mob.getRestrictRadius()));
            for (int i = 0; i < 24; i++) {
                // sqrt radius sampling gives uniform area coverage instead of over-biasing the restriction edge.
                double angle = mob.getRandom().nextDouble() * (Math.PI * 2.0D);
                double randomRadius = Math.sqrt(mob.getRandom().nextDouble()) * radius;
                int candidateX = center.getX() + Mth.floor(Mth.cos((float) angle) * (float) randomRadius);
                int candidateZ = center.getZ() + Mth.floor(Mth.sin((float) angle) * (float) randomRadius);

                int candidateSurfaceY = mob.level().getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(candidateX, mob.blockPosition().getY(), candidateZ)
                ).getY();
                BlockPos candidate = new BlockPos(candidateX, candidateSurfaceY, candidateZ);
                if (!mob.isWithinRestriction(candidate)) {
                    continue;
                }

                // Cheap rejection of poor nodes (water/liquid or large vertical jump) to reduce stalled paths.
                if (!mob.level().getBlockState(candidate).getFluidState().isEmpty()
                        || !mob.level().getBlockState(candidate.above()).getFluidState().isEmpty()) {
                    continue;
                }
                if (Math.abs(candidateSurfaceY - mob.blockPosition().getY()) > 12) {
                    continue;
                }

                double candidateY = resolveNavigationY(mob, candidate);
                if (mob.getNavigation().moveTo(candidate.getX() + 0.5D, candidateY, candidate.getZ() + 0.5D,
                        0.9D * settings.aiSpeedMultiplier())) {
                    currentPatrolTarget = candidate;
                    recalcTicks = 18 + mob.getRandom().nextInt(18);
                    reselectionCooldown = 6;
                    return;
                }
            }

            currentPatrolTarget = null;
            recalcTicks = 10;
            reselectionCooldown = 8;
        }
    }

    private static double resolveNavigationY(PathfinderMob mob, BlockPos target) {
        BlockPos top = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
        return top.getY() + 1;
    }

    private record BehaviorSettings(double aiSpeedMultiplier, double hardLeashMultiplier, double movementSpeedMultiplier) {
        private static BehaviorSettings from(MobTraits tuning) {
            if (tuning == null) {
                return new BehaviorSettings(DEFAULT_AI_SPEED_MULTIPLIER, DEFAULT_HARD_LEASH_MULTIPLIER, 1.0D);
            }
            double aiSpeed = tuning.aiSpeedMultiplier() == null
                    ? DEFAULT_AI_SPEED_MULTIPLIER
                    : Math.max(0.2D, tuning.aiSpeedMultiplier());
            double hardLeash = tuning.hardLeashMultiplier() == null
                    ? DEFAULT_HARD_LEASH_MULTIPLIER
                    : Math.max(1.1D, tuning.hardLeashMultiplier());
            double movementSpeed = tuning.movementSpeedMultiplier() == null
                    ? 1.0D
                    : Math.max(0.1D, tuning.movementSpeedMultiplier());
            return new BehaviorSettings(aiSpeed, hardLeash, movementSpeed);
        }
    }
}
