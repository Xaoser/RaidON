package ru.xaoser.raidon.runtime.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import net.minecraft.sounds.SoundSource;

public final class RaidNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private RaidNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(RaidProgressS2CPacket.TYPE, RaidProgressS2CPacket.STREAM_CODEC, RaidProgressS2CPacket::handle);
        registrar.playToClient(RaidPlaySoundS2CPacket.TYPE, RaidPlaySoundS2CPacket.STREAM_CODEC, RaidPlaySoundS2CPacket::handle);
    }

    public static void sendSound(ServerPlayer player, ResourceLocation soundId, SoundSource source, float volume, float pitch) {
        if (player == null || soundId == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new RaidPlaySoundS2CPacket(soundId, source, volume, pitch));
    }
}
