package ru.xaoser.raidon.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.command.RaidonCommand;
import ru.xaoser.raidon.runtime.reload.RaidDataPackReloadListener;
import ru.xaoser.raidon.runtime.raid.RaidManager;

@Mod(Raidon.MODID)
public final class RaidonNeoForge {
    public RaidonNeoForge(IEventBus modEventBus) {
        RaidPlatform.init(RaidPlatformImpl.INSTANCE);
        Raidon.init();
        RaidonNeoForgeItems.register(modEventBus);

        modEventBus.addListener(RaidonNeoForgeNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RaidonNeoForgeClient.init(modEventBus);
        }

        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(this::onMobDeath);
        NeoForge.EVENT_BUS.addListener(this::onItemPickup);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerDimensionChange);
        NeoForge.EVENT_BUS.addListener(this::onTrade);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListener);
    }

    private void onServerStarted(ServerStartedEvent event) {
        Raidon.onServerStarted(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        Raidon.onServerStopping();
    }

    private void onServerTick(ServerTickEvent.Post event) {
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

    private void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
            RaidManager.onItemPickup(player, event.getOriginalStack());
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
