package ru.xaoser.raidon;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import ru.xaoser.raidon.runtime.command.RaidonCommand;
import ru.xaoser.raidon.runtime.config.RaidConfigLoader;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.raid.RaidManager;
import ru.xaoser.raidon.runtime.client.RaidHudOverlay;

@Mod(Raidon.MODID)
public class Raidon {

    public static final String MODID = "raidon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Raidon() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Hello, if you read this, you having a great day! (from RaidON)");
        event.enqueueWork(RaidNetwork::register);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("RaidON is initialized, have a nice day!");
        RaidConfigLoader.load(event.getServer(), LOGGER);
        RaidonCommand.registerDispatcher(event.getServer().getCommands().getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RaidManager.onServerStop();
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("raidon_progress", RaidHudOverlay.INSTANCE);
        }
    }
}
