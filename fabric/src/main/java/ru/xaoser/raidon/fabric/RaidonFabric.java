package ru.xaoser.raidon.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.command.RaidonCommand;
import ru.xaoser.raidon.runtime.raid.RaidManager;

public final class RaidonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RaidPlatform.init(RaidPlatformImpl.INSTANCE);
        Raidon.init();
        RaidonFabricItems.init();
        RaidonFabricNetwork.init();

        ServerLifecycleEvents.SERVER_STARTED.register(Raidon::onServerStarted);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                Raidon.onDataPackReload(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Raidon.onServerStopping());
        ServerTickEvents.END_SERVER_TICK.register(RaidManager::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RaidonCommand.registerDispatcher(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RaidManager.onPlayerLogin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RaidManager.onPlayerLogout(handler.player));

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob) {
                RaidManager.onEntityJoin(world, mob);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob) {
                RaidManager.onMobDeath(mob, source.getEntity());
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                RaidManager.onPlayerRespawn(newPlayer));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                RaidManager.onPlayerDimension(player, destination.dimension().location()));
    }
}
