package ru.xaoser.raidon.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.command.RaidonCommand;
import ru.xaoser.raidon.runtime.reload.RaidDataPackReloadListener;
import ru.xaoser.raidon.runtime.raid.RaidManager;

@Mod(Raidon.MODID)
public final class RaidonForge {
    public RaidonForge(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        RaidPlatform.init(RaidPlatformImpl.INSTANCE);
        Raidon.init();
        RaidonForgeItems.register(modEventBus);
        RaidonForgeNetwork.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            RaidonForgeClient.init(modEventBus);
        }

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(this::onMobDeath);
        MinecraftForge.EVENT_BUS.addListener(this::onItemPickup);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerDimensionChange);
        MinecraftForge.EVENT_BUS.addListener(this::onTrade);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);
    }

    private void onServerStarted(ServerStartedEvent event) {
        Raidon.onServerStarted(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        Raidon.onServerStopping();
    }

    private void onServerTick(TickEvent.ServerTickEvent.Post event) {
        RaidManager.onServerTick(event.getServer());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        RaidonCommand.registerDispatcher(event.getDispatcher());
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new RaidDataPackReloadListener(ServerLifecycleHooks::getCurrentServer));
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RaidManager.onPlayerLogin(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RaidManager.onPlayerLogout(player);
        }
    }

    private void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && event.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
            RaidManager.onEntityJoin(level, mob);
        }
    }

    private void onMobDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
            RaidManager.onMobDeath(mob, event.getSource().getEntity());
        }
    }

    private void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RaidManager.onItemPickup(player, event.getStack());
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RaidManager.onPlayerRespawn(player);
        }
    }

    private void onPlayerDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RaidManager.onPlayerDimension(player, event.getTo().location());
        }
    }

    private void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RaidManager.onTrade(player, event.getMerchantOffer().getResult());
        }
    }
}
