package ru.xaoser.raidon.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.item.RaidSummonItem;
import ru.xaoser.raidon.runtime.item.RaidSummonItemConfigs;
import ru.xaoser.raidon.runtime.item.RaidSummonItemDefinition;
import ru.xaoser.raidon.runtime.item.RaidSummonItemStacks;

import java.util.function.Supplier;

public final class RaidonForgeItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Raidon.MODID);
    private static boolean registered;

    private RaidonForgeItems() {
    }

    public static void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;

        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            Supplier<Item> item = ITEMS.register(definition.id().getPath(), () -> new RaidSummonItem(definition.id()));
        }

        modEventBus.addListener(RaidonForgeItems::onBuildCreativeTabContents);
        ITEMS.register(modEventBus);
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            if (!definition.creativeTabs().contains(event.getTabKey())) {
                continue;
            }
            var stack = RaidSummonItemStacks.createStack(definition, 1);
            if (!stack.isEmpty()) {
                event.accept(stack);
            }
        }
    }
}
