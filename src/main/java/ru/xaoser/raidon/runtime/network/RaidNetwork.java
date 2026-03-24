package ru.xaoser.raidon.runtime.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import net.minecraft.sounds.SoundSource;

public final class RaidNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation(Raidon.MODID, "raid");
    private static int packetId = 0;
    private static SimpleChannel channel;

    private RaidNetwork() {
    }

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
        channel.registerMessage(
                nextId(),
                RaidProgressS2CPacket.class,
                RaidProgressS2CPacket::encode,
                RaidProgressS2CPacket::decode,
                RaidProgressS2CPacket::handle
        );
        channel.registerMessage(
                nextId(),
                RaidPlaySoundS2CPacket.class,
                RaidPlaySoundS2CPacket::encode,
                RaidPlaySoundS2CPacket::decode,
                RaidPlaySoundS2CPacket::handle
        );
    }

    public static SimpleChannel channel() {
        return channel;
    }

    public static void sendSound(ServerPlayer player, ResourceLocation soundId, SoundSource source, float volume, float pitch) {
        if (channel == null || player == null || soundId == null) {
            return;
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), new RaidPlaySoundS2CPacket(soundId, source, volume, pitch));
    }

    private static int nextId() {
        return packetId++;
    }
}
