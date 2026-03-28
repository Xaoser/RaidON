package ru.xaoser.raidon.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.runtime.network.RaidPacket;

import java.nio.file.Path;

public enum RaidPlatformImpl implements RaidPlatform.Access {
    INSTANCE;

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, RaidPacket payload) {
        RaidonForgeNetwork.sendToPlayer(player, payload);
    }
}
