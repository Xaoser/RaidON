package ru.xaoser.raidon.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidStopSoundS2CPacket;

public final class RaidonFabricNetwork {
    private RaidonFabricNetwork() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(RaidProgressS2CPacket.TYPE, RaidProgressS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RaidPlaySoundS2CPacket.TYPE, RaidPlaySoundS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RaidStopSoundS2CPacket.TYPE, RaidStopSoundS2CPacket.STREAM_CODEC);
    }

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(RaidProgressS2CPacket.TYPE, (payload, context) ->
                RaidProgressS2CPacket.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(RaidPlaySoundS2CPacket.TYPE, (payload, context) ->
                RaidPlaySoundS2CPacket.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(RaidStopSoundS2CPacket.TYPE, (payload, context) ->
                RaidStopSoundS2CPacket.handle(payload));
    }
}
