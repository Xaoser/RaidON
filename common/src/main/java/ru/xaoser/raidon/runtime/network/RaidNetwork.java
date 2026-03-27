package ru.xaoser.raidon.runtime.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidStopSoundS2CPacket;
import net.minecraft.sounds.SoundSource;

public final class RaidNetwork {
    public static final String PROTOCOL_VERSION = "2";

    private RaidNetwork() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        RaidPlatform.sendToPlayer(player, payload);
    }

    public static void sendSound(ServerPlayer player, ResourceLocation soundId, SoundSource source, float volume, float pitch) {
        if (player == null || soundId == null) {
            return;
        }
        sendToPlayer(player, new RaidPlaySoundS2CPacket(soundId, source, volume, pitch));
    }

    public static void sendSound(ServerPlayer player, ResourceLocation soundId, SoundSource source, float volume, float pitch,
                                 double zoneX, double zoneY, double zoneZ, float zoneRadius) {
        if (player == null || soundId == null) {
            return;
        }
        sendToPlayer(player, new RaidPlaySoundS2CPacket(
                soundId,
                source,
                volume,
                pitch,
                true,
                zoneX,
                zoneY,
                zoneZ,
                zoneRadius
        ));
    }

    public static void sendStopSound(ServerPlayer player, double zoneX, double zoneY, double zoneZ, float zoneRadius) {
        if (player == null) {
            return;
        }
        sendToPlayer(player, new RaidStopSoundS2CPacket(zoneX, zoneY, zoneZ, zoneRadius));
    }
}
