package ru.xaoser.raidon.api.sup;

public record MobTraits(
        Boolean burnInSun,
        Boolean canDrown,
        Double knockbackResistance,
        Double movementSpeedMultiplier,
        Double aiSpeedMultiplier,
        Double hardLeashMultiplier
) {
    public static MobTraits defaults() {
        return new MobTraits(null, null, null, null, null, null);
    }

    public boolean isDefault() {
        return burnInSun == null
                && canDrown == null
                && knockbackResistance == null
                && movementSpeedMultiplier == null
                && aiSpeedMultiplier == null
                && hardLeashMultiplier == null;
    }
}
