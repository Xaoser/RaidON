package ru.xaoser.raidon.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.RaidonClient;
import ru.xaoser.raidon.runtime.client.RaidClientResources;
import ru.xaoser.raidon.runtime.client.RaidHudOverlay;

final class RaidonNeoForgeClient {
    private static long tickCounter;
    private static long lastSourceFingerprint = Long.MIN_VALUE;

    private RaidonNeoForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        RaidonClient.init();
        modEventBus.addListener(RaidonNeoForgeClient::onAddPackFinders);
        modEventBus.addListener(RaidonNeoForgeClient::onRegisterOverlays);
        NeoForge.EVENT_BUS.addListener(RaidonNeoForgeClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(RaidonNeoForgeClient::onClientTick);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES) {
            var pack = RaidClientResources.createConfigPack();
            if (pack != null) {
                event.addRepositorySource(consumer -> consumer.accept(pack));
                Raidon.LOGGER.info("[Raidon] Registered NeoForge config resource pack {}", RaidClientResources.PACK_ID);
            } else {
                Raidon.LOGGER.warn("[Raidon] Failed to create NeoForge config resource pack {}", RaidClientResources.PACK_ID);
            }
        }
    }

    private static void onRegisterOverlays(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Raidon.MODID, "raidon_progress"), RaidHudOverlay.INSTANCE);
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RaidonClient.onDisconnect();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
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
