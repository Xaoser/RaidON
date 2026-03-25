package ru.xaoser.raidon.runtime.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import ru.xaoser.raidon.Raidon;

@EventBusSubscriber(modid = Raidon.MODID, value = Dist.CLIENT)
public final class RaidClientEvents {
    private RaidClientEvents() {}

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RaidHudState.update(null);
    }
}
