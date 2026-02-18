package ru.xaoser.raidon.api.sup;

public record MobTraits(Boolean burnInSun, Boolean canDrown, Double knockbackResistance) {
    public static MobTraits defaults() {
        return new MobTraits(null, null, null);
    }

    public boolean isDefault() {
        return burnInSun == null && canDrown == null && knockbackResistance == null;
    }
}
