package ru.xaoser.raidon.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import ru.xaoser.raidon.RaidonClient;
import ru.xaoser.raidon.runtime.client.RaidHudOverlay;

public final class RaidonFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RaidonClient.init();
        RaidonFabricNetwork.initClient();
        RaidonFabricResources.prepare();

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) ->
                RaidHudOverlay.INSTANCE.render(guiGraphics));
        ClientTickEvents.END_CLIENT_TICK.register(client -> RaidonFabricResources.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RaidonClient.onDisconnect());
    }
}
