package ru.xaoser.raidon.runtime.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;

import java.util.List;
import java.util.Locale;

public record RaidSummonItemDefinition(
        ResourceLocation id,
        ResourceLocation raidId,
        String displayName,
        List<String> description,
        List<String> usage,
        List<String> tooltip,
        ResourceLocation texture,
        boolean consume,
        int useDurationTicks,
        String useAnimation,
        int cooldownTicks,
        int maxStackSize,
        String rarity,
        boolean glint,
        List<ResourceKey<CreativeModeTab>> creativeTabs,
        List<RaidSummonRecipeDefinition> recipes
) {
    public RaidSummonItemDefinition {
        description = description == null ? List.of() : List.copyOf(description);
        usage = usage == null ? List.of() : List.copyOf(usage);
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        useDurationTicks = Math.max(0, useDurationTicks);
        useAnimation = useAnimation == null ? "none" : useAnimation.trim().toLowerCase(Locale.ROOT);
        cooldownTicks = Math.max(0, cooldownTicks);
        maxStackSize = Math.max(1, Math.min(64, maxStackSize));
        rarity = rarity == null ? "common" : rarity.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? "" : displayName;
        creativeTabs = creativeTabs == null ? List.of() : List.copyOf(creativeTabs);
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
    }

    public Rarity resolvedRarity() {
        return switch (rarity) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    public boolean isChargedUse() {
        return useDurationTicks > 0;
    }

    public UseAnim resolvedUseAnimation() {
        return switch (useAnimation) {
            case "eat" -> UseAnim.EAT;
            case "drink" -> UseAnim.DRINK;
            case "block" -> UseAnim.BLOCK;
            case "bow" -> UseAnim.BOW;
            case "spear", "trident" -> UseAnim.SPEAR;
            case "crossbow" -> UseAnim.CROSSBOW;
            case "spyglass" -> UseAnim.SPYGLASS;
            case "toot_horn", "toot", "horn", "goat_horn" -> UseAnim.TOOT_HORN;
            case "brush" -> UseAnim.BRUSH;
            default -> UseAnim.NONE;
        };
    }
}
