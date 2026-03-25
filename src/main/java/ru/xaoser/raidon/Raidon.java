package ru.xaoser.raidon;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import ru.xaoser.raidon.runtime.command.RaidonCommand;
import ru.xaoser.raidon.runtime.config.RaidConfigLoader;
import ru.xaoser.raidon.runtime.client.RaidClientResources;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.raid.RaidManager;
import ru.xaoser.raidon.runtime.client.RaidHudOverlay;

@Mod(Raidon.MODID)
public class Raidon {

    public static final String MODID = "raidon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Raidon(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(RaidNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientModEvents::addPackFinders);
            modEventBus.addListener(ClientModEvents::registerOverlays);
        }
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Hello, if you read this, you having a great day! (from RaidON)");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("RaidON is initialized, have a nice day!");
        RaidConfigLoader.load(event.getServer(), LOGGER);
        RaidManager.restoreActiveRaids(event.getServer());
        RaidonCommand.registerDispatcher(event.getServer().getCommands().getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RaidManager.onServerStop();
    }

    public static class ClientModEvents {

        @SubscribeEvent
        public static void addPackFinders(AddPackFindersEvent event) {
            RaidClientResources.register(event);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MODID, "raidon_progress"), RaidHudOverlay.INSTANCE);
        }
    }
}
