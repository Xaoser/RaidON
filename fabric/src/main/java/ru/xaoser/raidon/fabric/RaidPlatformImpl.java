package ru.xaoser.raidon.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.runtime.network.RaidPacket;

import java.nio.file.Path;

public enum RaidPlatformImpl implements RaidPlatform.Access {
    INSTANCE;

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, RaidPacket payload) {
        RaidonFabricNetwork.sendToPlayer(player, payload);
    }
}
