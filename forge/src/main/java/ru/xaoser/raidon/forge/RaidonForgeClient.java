package ru.xaoser.raidon.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.RaidonClient;
import ru.xaoser.raidon.runtime.client.RaidClientResources;
import ru.xaoser.raidon.runtime.client.RaidHudOverlay;

final class RaidonForgeClient {
    private static long tickCounter;
    private static long lastSourceFingerprint = Long.MIN_VALUE;

    private RaidonForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        RaidonClient.init();
        modEventBus.addListener(RaidonForgeClient::onAddPackFinders);
        modEventBus.addListener(RaidonForgeClient::onRegisterOverlays);
        MinecraftForge.EVENT_BUS.addListener(RaidonForgeClient::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(RaidonForgeClient::onClientTick);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES) {
            var pack = RaidClientResources.createConfigPack();
            if (pack != null) {
                event.addRepositorySource(consumer -> consumer.accept(pack));
                Raidon.LOGGER.info("[Raidon] Registered Forge config resource pack {}", RaidClientResources.PACK_ID);
            } else {
                Raidon.LOGGER.warn("[Raidon] Failed to create Forge config resource pack {}", RaidClientResources.PACK_ID);
            }
        }
    }

    private static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        IGuiOverlay overlay = (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                RaidHudOverlay.INSTANCE.render(guiGraphics);
        event.registerAboveAll("raidon_progress", overlay);
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RaidonClient.onDisconnect();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if ((tickCounter++ % 20L) != 0L) {
            return;
        }

        long fingerprint = RaidClientResources.computeSourceFingerprint(RaidClientResources.sourceRoot());
        if (fingerprint == lastSourceFingerprint) {
            return;
        }

        lastSourceFingerprint = fingerprint;
        RaidClientResources.ensureLayout();
    }
}
