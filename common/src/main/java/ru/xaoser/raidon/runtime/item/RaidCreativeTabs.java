package ru.xaoser.raidon.runtime.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RaidCreativeTabs {
    public static final ResourceKey<CreativeModeTab> DEFAULT_TAB = key("tools_and_utilities");

    private static final Map<String, ResourceKey<CreativeModeTab>> VANILLA_ALIASES = Map.ofEntries(
            Map.entry("blocks", key("building_blocks")),
            Map.entry("building", key("building_blocks")),
            Map.entry("building_blocks", key("building_blocks")),
            Map.entry("colored_blocks", key("colored_blocks")),
            Map.entry("natural", key("natural_blocks")),
            Map.entry("natural_blocks", key("natural_blocks")),
            Map.entry("functional", key("functional_blocks")),
            Map.entry("functional_blocks", key("functional_blocks")),
            Map.entry("redstone", key("redstone_blocks")),
            Map.entry("redstone_blocks", key("redstone_blocks")),
            Map.entry("hotbar", key("hotbar")),
            Map.entry("search", key("search")),
            Map.entry("tools", key("tools_and_utilities")),
            Map.entry("utilities", key("tools_and_utilities")),
            Map.entry("tools_utilities", key("tools_and_utilities")),
            Map.entry("utilities_and_tools", key("tools_and_utilities")),
            Map.entry("tools_and_utilities", key("tools_and_utilities")),
            Map.entry("combat", key("combat")),
            Map.entry("foods", key("food_and_drinks")),
            Map.entry("drinks", key("food_and_drinks")),
            Map.entry("food", key("food_and_drinks")),
            Map.entry("food_and_drinks", key("food_and_drinks")),
            Map.entry("ingredients", key("ingredients")),
            Map.entry("spawn", key("spawn_eggs")),
            Map.entry("spawn_eggs", key("spawn_eggs")),
            Map.entry("op", key("op_blocks")),
            Map.entry("operator", key("op_blocks")),
            Map.entry("op_blocks", key("op_blocks")),
            Map.entry("inventory", key("inventory"))
    );

    private RaidCreativeTabs() {
    }

    public static ResourceKey<CreativeModeTab> resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TAB;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        ResourceKey<CreativeModeTab> alias = VANILLA_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }

        ResourceLocation id = normalized.contains(":")
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(ResourceLocation.DEFAULT_NAMESPACE, normalized);
        if (id == null) {
            return null;
        }
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, id);
    }

    public static Set<ResourceKey<CreativeModeTab>> knownTabs() {
        return Set.copyOf(VANILLA_ALIASES.values());
    }

    private static ResourceKey<CreativeModeTab> key(String path) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(ResourceLocation.DEFAULT_NAMESPACE, path));
    }
}
