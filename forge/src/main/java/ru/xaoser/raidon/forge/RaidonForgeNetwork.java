package ru.xaoser.raidon.forge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidStopSoundS2CPacket;

public final class RaidonForgeNetwork {
    private static Channel<CustomPacketPayload> channel;

    private RaidonForgeNetwork() {
    }

    public static void init() {
        if (channel != null) {
            return;
        }

        int protocolVersion = Integer.parseInt(RaidNetwork.PROTOCOL_VERSION);
        channel = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(Raidon.MODID, "play"))
                .networkProtocolVersion(protocolVersion)
                .acceptedVersions(Channel.VersionTest.exact(protocolVersion))
                .payloadChannel()
                .play()
                .clientbound()
                .addMain(RaidProgressS2CPacket.TYPE, RaidProgressS2CPacket.STREAM_CODEC,
                        (payload, context) -> RaidProgressS2CPacket.handle(payload))
                .addMain(RaidPlaySoundS2CPacket.TYPE, RaidPlaySoundS2CPacket.STREAM_CODEC,
                        (payload, context) -> RaidPlaySoundS2CPacket.handle(payload))
                .addMain(RaidStopSoundS2CPacket.TYPE, RaidStopSoundS2CPacket.STREAM_CODEC,
                        (payload, context) -> RaidStopSoundS2CPacket.handle(payload))
                .build();
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        init();
        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
