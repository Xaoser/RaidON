package ru.xaoser.raidon.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.xaoser.raidon.runtime.raid.RaidManager;

public final class RaidonFabricHooks {
    private RaidonFabricHooks() {
    }

    public static void onItemPickup(ServerPlayer player, ItemStack stack) {
        RaidManager.onItemPickup(player, stack);
    }

    public static void onTrade(ServerPlayer player, ItemStack stack) {
        RaidManager.onTrade(player, stack);
    }
}
