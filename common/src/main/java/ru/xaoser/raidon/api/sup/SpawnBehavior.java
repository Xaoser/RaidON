package ru.xaoser.raidon.api.sup;

public enum SpawnBehavior {
    NEUTRAL,
    HOSTILE;

    public static SpawnBehavior fromString(String value) {
        if (value == null) return NEUTRAL;
        return switch (value.toLowerCase()) {
            case "hostile", "aggressive", "aggresive" -> HOSTILE;
            default -> NEUTRAL;
        };
    }
}
