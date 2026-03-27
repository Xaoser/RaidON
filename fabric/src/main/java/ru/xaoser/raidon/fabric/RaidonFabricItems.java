package ru.xaoser.raidon.fabric;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import ru.xaoser.raidon.runtime.item.RaidSummonItem;
import ru.xaoser.raidon.runtime.item.RaidCreativeTabs;
import ru.xaoser.raidon.runtime.item.RaidSummonItemConfigs;
import ru.xaoser.raidon.runtime.item.RaidSummonItemDefinition;
import ru.xaoser.raidon.runtime.item.RaidSummonItemStacks;

import java.util.LinkedHashSet;

public final class RaidonFabricItems {
    private static boolean initialized;

    private RaidonFabricItems() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            Item item = Registry.register(BuiltInRegistries.ITEM, definition.id(), new RaidSummonItem(definition.id()));
        }

        LinkedHashSet<net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>> tabs =
                new LinkedHashSet<>(RaidCreativeTabs.knownTabs());
        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            tabs.addAll(definition.creativeTabs());
        }

        for (var creativeTab : tabs) {
            ItemGroupEvents.modifyEntriesEvent(creativeTab).register(entries -> {
                for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
                    if (!definition.creativeTabs().contains(creativeTab)) {
                        continue;
                    }
                    var stack = RaidSummonItemStacks.createStack(definition, 1);
                    if (!stack.isEmpty()) {
                        entries.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                    }
                }
            });
        }
    }
}
