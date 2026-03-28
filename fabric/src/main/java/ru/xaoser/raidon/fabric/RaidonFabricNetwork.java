package ru.xaoser.raidon.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import ru.xaoser.raidon.runtime.network.RaidPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidStopSoundS2CPacket;

public final class RaidonFabricNetwork {
    private RaidonFabricNetwork() {
    }

    public static void init() {
    }

    public static void sendToPlayer(ServerPlayer player, RaidPacket payload) {
        var buf = PacketByteBufs.create();
        payload.encode(buf);
        ServerPlayNetworking.send(player, payload.id(), buf);
    }

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(RaidProgressS2CPacket.ID, (client, handler, buf, responseSender) -> {
            RaidProgressS2CPacket payload = RaidProgressS2CPacket.decode(buf);
            RaidProgressS2CPacket.handle(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(RaidPlaySoundS2CPacket.ID, (client, handler, buf, responseSender) -> {
            RaidPlaySoundS2CPacket payload = RaidPlaySoundS2CPacket.decode(buf);
            RaidPlaySoundS2CPacket.handle(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(RaidStopSoundS2CPacket.ID, (client, handler, buf, responseSender) -> {
            RaidStopSoundS2CPacket payload = RaidStopSoundS2CPacket.decode(buf);
            RaidStopSoundS2CPacket.handle(payload);
        });
    }
}
