package ru.xaoser.raidon.runtime.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RaidSummonItemStacks {
    private RaidSummonItemStacks() {
    }

    public static ItemStack createStack(RaidSummonItemDefinition definition, int count) {
        if (definition == null) {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.get(definition.id());
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, Math.max(1, count));
        applyDefinition(stack, definition);
        return stack;
    }

    public static void applyDefinition(ItemStack stack, RaidSummonItemDefinition definition) {
        if (stack == null || stack.isEmpty() || definition == null) {
            return;
        }
    }
}
