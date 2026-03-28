package ru.xaoser.raidon.runtime.item;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public enum RaidSummonRecipeType {
    CRAFTING_SHAPED("minecraft:crafting_shaped"),
    CRAFTING_SHAPELESS("minecraft:crafting_shapeless"),
    SMELTING("minecraft:smelting"),
    BLASTING("minecraft:blasting"),
    SMOKING("minecraft:smoking"),
    CAMPFIRE_COOKING("minecraft:campfire_cooking"),
    STONECUTTING("minecraft:stonecutting"),
    SMITHING_TRANSFORM("minecraft:smithing_transform");

    private final ResourceLocation serializerId;

    RaidSummonRecipeType(String serializerId) {
        this.serializerId = new ResourceLocation(serializerId);
    }

    public ResourceLocation serializerId() {
        return serializerId;
    }

    public static RaidSummonRecipeType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "crafting_shaped", "shaped", "workbench", "crafting_table" -> CRAFTING_SHAPED;
            case "crafting_shapeless", "shapeless" -> CRAFTING_SHAPELESS;
            case "smelting", "furnace" -> SMELTING;
            case "blasting", "blast_furnace" -> BLASTING;
            case "smoking", "smoker" -> SMOKING;
            case "campfire", "campfire_cooking" -> CAMPFIRE_COOKING;
            case "stonecutting", "stonecutter" -> STONECUTTING;
            case "smithing", "smithing_transform" -> SMITHING_TRANSFORM;
            default -> null;
        };
    }

    public int defaultCookingTime() {
        return switch (this) {
            case SMELTING -> 200;
            case BLASTING, SMOKING -> 100;
            case CAMPFIRE_COOKING -> 600;
            default -> 0;
        };
    }

    public boolean isCooking() {
        return this == SMELTING || this == BLASTING || this == SMOKING || this == CAMPFIRE_COOKING;
    }
}
