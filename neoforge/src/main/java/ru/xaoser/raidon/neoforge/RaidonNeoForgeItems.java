package ru.xaoser.raidon.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.item.RaidSummonItem;
import ru.xaoser.raidon.runtime.item.RaidSummonItemConfigs;
import ru.xaoser.raidon.runtime.item.RaidSummonItemDefinition;
import ru.xaoser.raidon.runtime.item.RaidSummonItemStacks;

import java.util.function.Supplier;

public final class RaidonNeoForgeItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Raidon.MODID);
    private static boolean registered;

    private RaidonNeoForgeItems() {
    }

    public static void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;

        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            Supplier<net.minecraft.world.item.Item> item = ITEMS.register(definition.id().getPath(), id -> new RaidSummonItem(definition.id()));
        }

        modEventBus.addListener(RaidonNeoForgeItems::onBuildCreativeTabContents);
        ITEMS.register(modEventBus);
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            if (!definition.creativeTabs().contains(event.getTabKey())) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = RaidSummonItemStacks.createStack(definition, 1);
            if (!stack.isEmpty()) {
                event.accept(stack,
                        net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }
    }
}
