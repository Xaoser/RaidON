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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.InteractionHand;
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
    private static final String RAID_PENDING_RETURN_TAG = "raidon_pending_return";
    private static final String RAID_HARD_RADIUS_TAG = "raidon_hard_radius";
    private static final String RAID_COMBAT_ENGAGED_TAG = "raidon_combat_engaged";
    private static final double DEFAULT_BASE_DAMAGE = 2.0D;
    private static final double DEFAULT_FOLLOW_RANGE = 32.0D;
    private static final double MIN_FOLLOW_RANGE = 24.0D;
    private static final double MAX_FOLLOW_RANGE = 40.0D;
    private static final double EXTRA_HARD_BOUNDARY_RADIUS = 30.0D;
    private static final double DEFAULT_AI_SPEED_MULTIPLIER = 1.0D;
    private static final double DEFAULT_HARD_LEASH_MULTIPLIER = 1.75D;

    private MobAiHelper() {}

    public static void applyBehavior(Mob mob, SpawnBehavior behavior, MobTargeting targeting, MobTraits tuning,
                                     BlockPos raidTargetPoint, int mobWanderRadius, BlockPos spawnPoint) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            return;
        }

        MobTargeting cfg = targeting == null ? MobTargeting.defaults() : targeting;
        BehaviorSettings settings = BehaviorSettings.from(tuning);
        sanitizeGoalSelector(pathfinder, behavior);

        if (raidTargetPoint != null) {
            pathfinder.restrictTo(raidTargetPoint, Math.max(4, mobWanderRadius));
            configureHardBoundary(pathfinder, raidTargetPoint, Math.max(4, mobWanderRadius), spawnPoint, settings);
            addGoalIfAbsent(pathfinder, pathfinder.goalSelector.getAvailableGoals(), 0, RaidReturnToRestrictionGoal.class,
                    () -> new RaidReturnToRestrictionGoal(pathfinder, behavior, settings));
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
        sanitizeTargetSelector(mob);
        replaceCombatGoal(mob, settings);
        addGoalIfAbsent(mob, mob.goalSelector.getAvailableGoals(), 6, RaidPatrolWithinRestrictionGoal.class,
                () -> new RaidPatrolWithinRestrictionGoal(mob, settings));

        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 0, RaidHurtByTargetGoal.class,
                () -> new RaidHurtByTargetGoal(mob));

        Predicate<LivingEntity> preferredFilter = restrictTargets(mob, createPreferredTargetFilter(targeting));
        Predicate<LivingEntity> fallbackFilter = restrictTargets(mob, createTargetFilter(mob, targeting));

        addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 1, RaidNearestPlayerTargetGoal.class,
                () -> new RaidNearestPlayerTargetGoal(mob));

        if (!targeting.attackTypes().isEmpty() || targeting.attackAll()) {
            addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 2, RaidNearestPreferredTargetGoal.class,
                    () -> new RaidNearestPreferredTargetGoal(mob, preferredFilter));
            addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 3, RaidNearestFallbackTargetGoal.class,
                    () -> new RaidNearestFallbackTargetGoal(mob, fallbackFilter));
        } else {
            addTargetGoalIfAbsent(mob, mob.targetSelector.getAvailableGoals(), 2, RaidNearestFallbackTargetGoal.class,
                    () -> new RaidNearestFallbackTargetGoal(mob, fallbackFilter));
        }
    }

    private static void setupNeutral(PathfinderMob mob, MobTargeting targeting, BehaviorSettings settings) {
        applyFollowRange(mob, targeting.radius());
        applyMovementSpeed(mob, settings.movementSpeedMultiplier());
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
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

    private static boolean shouldForceReturn(PathfinderMob mob, BehaviorSettings settings) {
        if (!mob.hasRestriction()) {
            return false;
        }
        return isOutsideHardBoundary(mob, settings);
    }

    private static ReturnDecision resolveReturnDecision(PathfinderMob mob, BehaviorSettings settings) {
        if (shouldForceReturn(mob, settings)) {
            setCombatEngaged(mob, false);
            return ReturnDecision.allow();
        }

        LivingEntity target = refreshCombatTarget(mob);
        if (target != null) {
            return ReturnDecision.deny();
        }
        return ReturnDecision.allow();
    }

    private static Predicate<LivingEntity> createTargetFilter(PathfinderMob mob, MobTargeting targeting) {
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
            return entity instanceof Player || isApproachingRaidCenter(mob);
        };
    }

    private static Predicate<LivingEntity> restrictTargets(PathfinderMob mob, Predicate<LivingEntity> filter) {
        return entity -> filter.test(entity) && isTargetWithinCombatBounds(mob, entity);
    }

    private static void configureHardBoundary(PathfinderMob mob, BlockPos center, int softRadius, BlockPos spawnPoint, BehaviorSettings settings) {
        double spawnDistance = spawnPoint == null
                ? softRadius
                : horizontalDistance(center, spawnPoint);
        double hardRadius = Math.max(spawnDistance + EXTRA_HARD_BOUNDARY_RADIUS, softRadius * settings.hardLeashMultiplier());
        mob.getPersistentData().putDouble(RAID_HARD_RADIUS_TAG, Math.max(softRadius + 1.0D, hardRadius));
    }

    private static double getHardBoundaryRadius(PathfinderMob mob, BehaviorSettings settings) {
        if (mob.getPersistentData().contains(RAID_HARD_RADIUS_TAG)) {
            return Math.max(2.0D, mob.getPersistentData().getDouble(RAID_HARD_RADIUS_TAG));
        }
        if (mob.hasRestriction()) {
            double fallback = settings == null
                    ? mob.getRestrictRadius() * DEFAULT_HARD_LEASH_MULTIPLIER
                    : mob.getRestrictRadius() * settings.hardLeashMultiplier();
            return Math.max(2.0D, fallback);
        }
        return 0.0D;
    }

    private static boolean isWithinHardBoundary(PathfinderMob mob, double hardRadius, double x, double z) {
        if (hardRadius <= 0.0D || !mob.hasRestriction()) {
            return true;
        }
        BlockPos center = mob.getRestrictCenter();
        double dx = x - (center.getX() + 0.5D);
        double dz = z - (center.getZ() + 0.5D);
        return (dx * dx + dz * dz) <= hardRadius * hardRadius;
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        double dx = (first.getX() + 0.5D) - (second.getX() + 0.5D);
        double dz = (first.getZ() + 0.5D) - (second.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean isApproachingRaidCenter(PathfinderMob mob) {
        return mob.hasRestriction() && !mob.isWithinRestriction(mob.blockPosition());
    }

    private static boolean isOutsideHardBoundary(PathfinderMob mob, BehaviorSettings settings) {
        return !isWithinHardBoundary(mob, getHardBoundaryRadius(mob, settings), mob.getX(), mob.getZ());
    }

    private static boolean isTargetWithinCombatBounds(PathfinderMob mob, LivingEntity target) {
        double hardRadius = getHardBoundaryRadius(mob, null);
        return target != null
                && isWithinHardBoundary(mob, hardRadius, mob.getX(), mob.getZ())
                && isWithinHardBoundary(mob, hardRadius, target.getX(), target.getZ());
    }

    private static boolean isRetaliationTargetAllowed(PathfinderMob mob, LivingEntity target) {
        if (target == null || !target.isAlive() || isRaidMob(target)) {
            return false;
        }
        if (target instanceof Player player && !isAggroEligiblePlayer(player)) {
            return false;
        }
        return isTargetWithinCombatBounds(mob, target);
    }

    private static boolean hasCombatPriorityTarget(PathfinderMob mob, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player) {
            return isAggroEligiblePlayer(player) && isTargetWithinCombatBounds(mob, target);
        }
        return !isRaidMob(target) && isTargetWithinCombatBounds(mob, target);
    }

    private static LivingEntity refreshCombatTarget(PathfinderMob mob) {
        LivingEntity current = mob.getTarget();
        if (hasCombatPriorityTarget(mob, current)) {
            setCombatEngaged(mob, true);
            return current;
        }

        if (current != null) {
            mob.setTarget(null);
        }

        LivingEntity replacement = findVisibleCombatTarget(mob);
        if (replacement != null) {
            mob.setTarget(replacement);
            setCombatEngaged(mob, true);
            return replacement;
        }

        setCombatEngaged(mob, false);
        return null;
    }

    private static LivingEntity findVisibleCombatTarget(PathfinderMob mob) {
        double searchRadius = getCombatSearchRadius(mob);
        double verticalRadius = Math.max(8.0D, searchRadius * 0.5D);
        Player bestPlayer = null;
        double bestPlayerDistance = Double.MAX_VALUE;
        LivingEntity bestOther = null;
        double bestOtherDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(searchRadius, verticalRadius, searchRadius),
                entity -> isCombatSearchCandidate(mob, entity) && mob.hasLineOfSight(entity)
        )) {
            double distance = mob.distanceToSqr(candidate);
            if (candidate instanceof Player player) {
                if (distance < bestPlayerDistance) {
                    bestPlayer = player;
                    bestPlayerDistance = distance;
                }
                continue;
            }
            if (distance < bestOtherDistance) {
                bestOther = candidate;
                bestOtherDistance = distance;
            }
        }

        return bestPlayer != null ? bestPlayer : bestOther;
    }

    private static boolean isCombatSearchCandidate(PathfinderMob mob, LivingEntity candidate) {
        if (candidate == null || candidate == mob || !candidate.isAlive() || isRaidMob(candidate)) {
            return false;
        }
        if (candidate instanceof Player player) {
            return isAggroEligiblePlayer(player) && isTargetWithinCombatBounds(mob, candidate);
        }
        return isTargetWithinCombatBounds(mob, candidate);
    }

    private static double getCombatSearchRadius(PathfinderMob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            return DEFAULT_FOLLOW_RANGE;
        }
        return Math.max(MIN_FOLLOW_RANGE, followRange.getValue());
    }

    private static void sanitizeGoalSelector(PathfinderMob mob, SpawnBehavior behavior) {
        if (behavior != SpawnBehavior.HOSTILE) {
            return;
        }
        Set<WrappedGoal> goals = mob.goalSelector.getAvailableGoals();
        goals.removeIf(goal -> shouldRemoveHostileGoal(goal.getGoal()));
    }

    private static void sanitizeTargetSelector(PathfinderMob mob) {
        Set<WrappedGoal> goals = mob.targetSelector.getAvailableGoals();
        goals.removeIf(goal -> {
            Goal inner = goal.getGoal();
            return inner instanceof HurtByTargetGoal && !(inner instanceof RaidHurtByTargetGoal)
                    || inner instanceof NearestAttackableTargetGoal && !(inner instanceof PersistentRaidTargetGoal);
        });
    }

    private static boolean shouldRemoveHostileGoal(Goal goal) {
        if (goal instanceof FloatGoal) {
            return false;
        }
        if (goal instanceof RaidReturnToRestrictionGoal
                || goal instanceof RaidStateGoal
                || goal instanceof RaidTargetSanitizerGoal
                || goal instanceof RaidCombatGoal
                || goal instanceof RaidPatrolWithinRestrictionGoal) {
            return false;
        }
        if (goal instanceof PanicGoal) {
            return true;
        }
        return goal.getFlags().contains(Goal.Flag.MOVE);
    }

    private static void replaceCombatGoal(PathfinderMob mob, BehaviorSettings settings) {
        Set<WrappedGoal> goals = mob.goalSelector.getAvailableGoals();
        goals.removeIf(goal -> goal.getGoal() instanceof RaidCombatGoal);
        addGoalIfAbsent(mob, goals, 1, RaidCombatGoal.class,
                () -> new RaidCombatGoal(mob, 1.0D * settings.aiSpeedMultiplier(), settings));
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
        if (followRange == null) {
            return;
        }
        double configured = radius == null || radius <= 0.0D ? DEFAULT_FOLLOW_RANGE : radius;
        followRange.setBaseValue(Mth.clamp(configured, MIN_FOLLOW_RANGE, MAX_FOLLOW_RANGE));
    }

    private static void applyMovementSpeed(PathfinderMob mob, Double movementSpeedMultiplier) {
        if (movementSpeedMultiplier == null) {
            return;
        }
        AttributeInstance movementSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            double multiplier = Math.max(0.1D, movementSpeedMultiplier);
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
        boolean exists = goals.stream().anyMatch(goal -> goal.getGoal().getClass() == goalClass);
        if (!exists) {
            mob.goalSelector.addGoal(priority, supplier.create());
        }
    }

    private static void addTargetGoalIfAbsent(PathfinderMob mob, Set<WrappedGoal> goals, int priority,
                                              Class<? extends Goal> goalClass, GoalSupplier supplier) {
        boolean exists = goals.stream().anyMatch(goal -> goal.getGoal().getClass() == goalClass);
        if (!exists) {
            mob.targetSelector.addGoal(priority, supplier.create());
        }
    }

    private static boolean isPendingReturn(PathfinderMob mob) {
        return mob.getPersistentData().getBoolean(RAID_PENDING_RETURN_TAG);
    }

    private static void setPendingReturn(PathfinderMob mob, boolean pending) {
        mob.getPersistentData().putBoolean(RAID_PENDING_RETURN_TAG, pending);
    }

    private static boolean isCombatEngaged(PathfinderMob mob) {
        return mob.getPersistentData().getBoolean(RAID_COMBAT_ENGAGED_TAG);
    }

    private static void setCombatEngaged(PathfinderMob mob, boolean engaged) {
        if (mob.getPersistentData().getBoolean(RAID_COMBAT_ENGAGED_TAG) == engaged) {
            return;
        }
        mob.getPersistentData().putBoolean(RAID_COMBAT_ENGAGED_TAG, engaged);
        if (engaged) {
            setPendingReturn(mob, false);
        }
    }

    private static final class RaidCombatGoal extends Goal {
        private final PathfinderMob mob;
        private final BehaviorSettings settings;
        private final double speedModifier;
        private int attackCooldown;
        private int moveCooldown;

        private RaidCombatGoal(PathfinderMob mob, double speedModifier, BehaviorSettings settings) {
            this.mob = mob;
            this.settings = settings;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return hasCombatPriorityTarget(mob, mob.getTarget()) && !shouldForceReturn(mob, settings);
        }

        @Override
        public boolean canContinueToUse() {
            return hasCombatPriorityTarget(mob, mob.getTarget()) && !shouldForceReturn(mob, settings);
        }

        @Override
        public void start() {
            attackCooldown = 0;
            moveCooldown = 0;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (!hasCombatPriorityTarget(mob, target)) {
                return;
            }

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (attackCooldown > 0) {
                attackCooldown--;
            }
            if (--moveCooldown <= 0 || mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(target, speedModifier);
                moveCooldown = 4 + mob.getRandom().nextInt(4);
            }

            double distToEnemySqr = mob.distanceToSqr(target);
            if (distToEnemySqr <= getAttackReachSqr(target)) {
                mob.getNavigation().stop();
            }
            checkAndPerformAttack(target, distToEnemySqr);
        }

        private void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
            if (distToEnemySqr <= getAttackReachSqr(enemy) && attackCooldown <= 0) {
                attackCooldown = adjustedTickDelay(20);
                mob.swing(InteractionHand.MAIN_HAND);
                if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    mob.doHurtTarget(enemy);
                    return;
                }
                DamageSource source = mob.damageSources().mobAttack(mob);
                enemy.hurt(source, (float) getBaseDamage(mob));
            }
        }

        private double getAttackReachSqr(LivingEntity enemy) {
            return (double) (mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + enemy.getBbWidth());
        }
    }

    private static final class RaidHurtByTargetGoal extends HurtByTargetGoal {
        private final PathfinderMob mob;

        private RaidHurtByTargetGoal(PathfinderMob mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return isRetaliationTargetAllowed(mob, mob.getLastHurtByMob()) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isRetaliationTargetAllowed(mob, mob.getTarget()) && super.canContinueToUse();
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
        private final PathfinderMob mob;

        private RaidNearestPlayerTargetGoal(PathfinderMob mob) {
            super(mob, Player.class, 10, true, false,
                    entity -> isAggroEligiblePlayer(entity) && isTargetWithinCombatBounds(mob, entity),
                    entity -> isAggroEligiblePlayer(entity) && isTargetWithinCombatBounds(mob, entity));
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            LivingEntity current = mob.getTarget();
            if (current instanceof Player player && isAggroEligiblePlayer(player) && isTargetWithinCombatBounds(mob, player)) {
                return false;
            }
            return super.canUse();
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
            if (--tickCooldown > 0) return;
            tickCooldown = 5;

            LivingEntity target = shouldForceReturn(mob, null) ? null : refreshCombatTarget(mob);
            if (target != null) {
                return;
            }

            if (mob.hasRestriction() && !mob.isWithinRestriction(mob.blockPosition())) {
                return;
            }
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
            if (--checkCooldown > 0) {
                return;
            }
            checkCooldown = 5;

            LivingEntity target = mob.getTarget();
            if (target == null) {
                setCombatEngaged(mob, false);
                return;
            }
            if (hasCombatPriorityTarget(mob, target)) {
                setCombatEngaged(mob, true);
                return;
            }

            LivingEntity replacement = findVisibleCombatTarget(mob);
            if (replacement != null) {
                mob.setTarget(replacement);
                setCombatEngaged(mob, true);
                return;
            }

            mob.setTarget(null);
            setCombatEngaged(mob, false);
        }
    }

    private static final class RaidReturnToRestrictionGoal extends Goal {
        private final PathfinderMob mob;
        private final SpawnBehavior behavior;
        private final BehaviorSettings settings;
        private int moveCooldown;
        private boolean hadActiveTarget;

        private RaidReturnToRestrictionGoal(PathfinderMob mob, SpawnBehavior behavior, BehaviorSettings settings) {
            this.mob = mob;
            this.behavior = behavior;
            this.settings = settings;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ReturnDecision decision = resolveReturnDecision(mob, settings);
            if (decision.blocked()) {
                hadActiveTarget = true;
                return false;
            }

            if (hadActiveTarget && mob.hasRestriction() && !mob.isWithinRestriction(mob.blockPosition())) {
                setPendingReturn(mob, true);
            }
            hadActiveTarget = false;

            if (!mob.hasRestriction()) return false;
            return !mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public boolean canContinueToUse() {
            ReturnDecision decision = resolveReturnDecision(mob, settings);
            if (decision.blocked()) return false;
            if (!mob.hasRestriction()) return false;
            return !mob.isWithinRestriction(mob.blockPosition());
        }

        @Override
        public void start() {
            moveCooldown = 0;

            if (shouldForceReturn(mob, settings)) {
                LivingEntity target = mob.getTarget();
                if (target != null) {
                    mob.setTarget(null);
                }
            }
            issueMoveToRestriction();
        }

        @Override
        public void tick() {
            if (--moveCooldown <= 0 || mob.getNavigation().isDone()) {
                issueMoveToRestriction();
            }
        }

        @Override
        public void stop() {
            if (isCombatEngaged(mob) || hasCombatPriorityTarget(mob, mob.getTarget())) {
                mob.getNavigation().stop();
            }
            if (mob.hasRestriction() && mob.isWithinRestriction(mob.blockPosition())) {
                setPendingReturn(mob, false);
            }
        }

        private void issueMoveToRestriction() {
            BlockPos restrictCenter = mob.getRestrictCenter();
            double centerY = resolveNavigationY(mob, restrictCenter);
            double speed = returnSpeedModifier(behavior, settings);
            mob.getNavigation().moveTo(restrictCenter.getX() + 0.5D, centerY, restrictCenter.getZ() + 0.5D, speed);
            moveCooldown = 20;
        }

    }

    private record ReturnDecision(boolean blocked) {
        private static ReturnDecision deny() {
            return new ReturnDecision(true);
        }

        private static ReturnDecision allow() {
            return new ReturnDecision(false);
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

            if (reselectionCooldown <= 0 && (recalcTicks <= 0 || mob.getNavigation().isDone() || reachedDestination)) {
                moveToNextPoint();
            }
        }


        private void moveToNextPoint() {
            BlockPos center = mob.getRestrictCenter();
            int radius = Math.max(4, Mth.floor(mob.getRestrictRadius()));
            for (int i = 0; i < 24; i++) {
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

    private static double returnSpeedModifier(SpawnBehavior behavior, BehaviorSettings settings) {
        double base = behavior == SpawnBehavior.HOSTILE ? 1.0D : 0.6D;
        return Math.max(0.2D, base * settings.aiSpeedMultiplier());
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
