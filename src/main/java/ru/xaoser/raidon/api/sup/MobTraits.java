package ru.xaoser.raidon.api.sup;

public record MobTraits(
        Boolean burnInSun,
        Boolean canDrown,
        Double knockbackResistance,
        Double movementSpeedMultiplier,
        Double aiSpeedMultiplier,
        Double hardLeashMultiplier,
        Boolean raidAiEnabled
) {
    public MobTraits(Boolean burnInSun, Boolean canDrown, Double knockbackResistance) {
        this(burnInSun, canDrown, knockbackResistance, null, null, null, null);
    }

    public MobTraits(Boolean burnInSun, Boolean canDrown, Double knockbackResistance,
                     Double movementSpeedMultiplier, Double aiSpeedMultiplier, Double hardLeashMultiplier) {
        this(burnInSun, canDrown, knockbackResistance, movementSpeedMultiplier, aiSpeedMultiplier, hardLeashMultiplier, null);
    }

    public static MobTraits defaults() {
        return new MobTraits(null, null, null, null, null, null, null);
    }

    public boolean usesRaidAi() {
        return !Boolean.FALSE.equals(raidAiEnabled);
    }

    public boolean isDefault() {
        return burnInSun == null
                && canDrown == null
                && knockbackResistance == null
                && movementSpeedMultiplier == null
                && aiSpeedMultiplier == null
                && hardLeashMultiplier == null
                && raidAiEnabled == null;
    }
}
