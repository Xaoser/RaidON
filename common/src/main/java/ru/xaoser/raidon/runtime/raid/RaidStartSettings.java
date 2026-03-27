package ru.xaoser.raidon.runtime.raid;

import net.minecraft.resources.ResourceLocation;

public record RaidStartSettings(
        Trigger trigger,
        long cooldownTicks,
        ResourceLocation entity,
        ResourceLocation item,
        ResourceLocation structure,
        ResourceLocation biome,
        ResourceLocation dimension,
        java.util.List<Condition> conditions,
        int value,
        int count,
        int radius,
        Center center
) {
    public static final RaidStartSettings DEFAULT = new RaidStartSettings(Trigger.MANUAL, 0L, null, null, null, null, null, java.util.List.of(), 0, 0, 0, Center.DEFAULT);

    public RaidStartSettings {
        trigger = trigger == null ? Trigger.MANUAL : trigger;
        cooldownTicks = Math.max(0L, cooldownTicks);
        conditions = conditions == null ? java.util.List.of() : java.util.List.copyOf(conditions);
        value = Math.max(0, value);
        count = Math.max(0, count);
        radius = Math.max(0, radius);
        center = center == null ? Center.DEFAULT : center;
    }

    public int requiredCount() {
        return Math.max(1, count > 0 ? count : value > 0 ? value : 1);
    }

    public int effectiveRadius(int fallback) {
        int legacyRadius = trigger == Trigger.ON_STRUCTURE_VISIT && count <= 0 && value > 0 ? value : 0;
        int configured = radius > 0 ? radius : legacyRadius;
        return Math.max(1, configured > 0 ? configured : fallback);
    }

    public record Center(CenterType type, ResourceLocation structure, int searchRadius, boolean preferNearest) {
        public static final Center DEFAULT = new Center(CenterType.EVENT, null, 0, true);

        public Center {
            type = type == null ? CenterType.EVENT : type;
            searchRadius = Math.max(0, searchRadius);
        }

        public int effectiveSearchRadius(int fallback) {
            return Math.max(16, searchRadius > 0 ? searchRadius : fallback);
        }

        public ResourceLocation effectiveStructure(ResourceLocation fallback) {
            return structure != null ? structure : fallback;
        }
    }

    public record Condition(String type, int min, int max, int value, ResourceLocation biome, ResourceLocation dimension) {
    }

    public enum CenterType {
        EVENT,
        WORLD_SPAWN,
        STRUCTURE
    }

    public enum Trigger {
        MANUAL,
        ENTER_AREA,
        PLAYER_JOIN_ANY,
        PLAYER_JOIN_SINGLEPLAYER,
        NIGHT_FALL,
        ON_KILL,
        ON_ITEM_PICKUP,
        ON_TRADE,
        ON_STRUCTURE_VISIT,
        ON_DIMENSION_CHANGE,
        ON_RESPAWN,
        ON_ENTER_BIOME,
        ON_DAY,
        ON_SUNSET,
        ON_MIDNIGHT
    }
}
