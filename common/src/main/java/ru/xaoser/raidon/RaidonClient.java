package ru.xaoser.raidon;

import ru.xaoser.raidon.runtime.client.RaidClientResources;
import ru.xaoser.raidon.runtime.client.RaidClientPacketHandlers;
import ru.xaoser.raidon.runtime.client.RaidHudState;

public final class RaidonClient {
    private static boolean initialized;

    private RaidonClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RaidClientResources.ensureLayout();
    }

    public static void onDisconnect() {
        RaidClientPacketHandlers.resetTransientState();
        RaidHudState.update(null);
    }
}
