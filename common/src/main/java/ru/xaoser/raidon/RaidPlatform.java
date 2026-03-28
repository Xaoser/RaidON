package ru.xaoser.raidon;

import net.minecraft.server.level.ServerPlayer;
import ru.xaoser.raidon.runtime.network.RaidPacket;

import java.nio.file.Path;

public final class RaidPlatform {
    private static Access access;

    private RaidPlatform() {
    }

    public static void init(Access platformAccess) {
        if (platformAccess == null) {
            throw new IllegalArgumentException("platformAccess");
        }
        if (access == null) {
            access = platformAccess;
            return;
        }
        if (access.getClass() != platformAccess.getClass()) {
            throw new IllegalStateException("RaidPlatform is already initialized with " + access.getClass().getName());
        }
    }

    public static Path getConfigDir() {
        return requireAccess().getConfigDir();
    }

    public static Path getGameDir() {
        return requireAccess().getGameDir();
    }

    public static void sendToPlayer(ServerPlayer player, RaidPacket payload) {
        requireAccess().sendToPlayer(player, payload);
    }

    private static Access requireAccess() {
        if (access == null) {
            throw new IllegalStateException("RaidPlatform has not been initialized");
        }
        return access;
    }

    public interface Access {
        Path getConfigDir();

        Path getGameDir();

        void sendToPlayer(ServerPlayer player, RaidPacket payload);
    }
}
