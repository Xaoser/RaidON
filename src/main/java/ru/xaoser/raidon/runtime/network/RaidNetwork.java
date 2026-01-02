package ru.xaoser.raidon.runtime.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.xaoser.raidon.Raidon;

/**
 * Central network registration point for raid-related packets.
 */
public final class RaidNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation(Raidon.MODID, "raid");
    private static int packetId = 0;
    private static SimpleChannel channel;

    private RaidNetwork() {
    }

    /**
     * Registers all packets the raid runtime relies on.
     */
    public static void register() {
        if (channel == null) {
            channel = NetworkRegistry.newSimpleChannel(
                    CHANNEL_ID,
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals,
                    PROTOCOL_VERSION::equals
            );
        }

        registerPackets();
    }

    private static void registerPackets() {
        // Raid lifecycle packets (start/stop) and wave updates will be registered here.
    }

    public static SimpleChannel channel() {
        return channel;
    }

    private static int nextId() {
        return packetId++;
    }
}
