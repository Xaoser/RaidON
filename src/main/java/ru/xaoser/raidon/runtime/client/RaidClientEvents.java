package ru.xaoser.raidon.runtime.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.xaoser.raidon.Raidon;

@Mod.EventBusSubscriber(modid = Raidon.MODID, value = Dist.CLIENT)
public final class RaidClientEvents {
    private RaidClientEvents() {}

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RaidHudState.update(null);
    }
}
