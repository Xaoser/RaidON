package ru.xaoser.raidon.api.sup;

public record MobTraits(
        Boolean burnInSun,
        Boolean canDrown,
        Boolean disableTransformations,
        Double knockbackResistance,
        Double movementSpeedMultiplier,
        Double aiSpeedMultiplier,
        Double hardLeashMultiplier,
        Boolean forceMeleeAttackGoal
) {
    public MobTraits(Boolean burnInSun, Boolean canDrown, Double knockbackResistance) {
        this(burnInSun, canDrown, null, knockbackResistance, null, null, null, null);
    }

    public MobTraits(Boolean burnInSun, Boolean canDrown, Double knockbackResistance,
                     Double movementSpeedMultiplier, Double aiSpeedMultiplier, Double hardLeashMultiplier) {
        this(burnInSun, canDrown, null, knockbackResistance, movementSpeedMultiplier, aiSpeedMultiplier, hardLeashMultiplier, null);
    }

    public static MobTraits defaults() {
        return new MobTraits(null, null, null, null, null, null, null, null);
    }

    public boolean disablesTransformations() {
        return !Boolean.FALSE.equals(disableTransformations);
    }

    public boolean forcesMeleeAttackGoal() {
        return !Boolean.FALSE.equals(forceMeleeAttackGoal);
    }

    public boolean isDefault() {
        return burnInSun == null
                && canDrown == null
                && disableTransformations == null
                && knockbackResistance == null
                && movementSpeedMultiplier == null
                && aiSpeedMultiplier == null
                && hardLeashMultiplier == null
                && forceMeleeAttackGoal == null;
    }
}
