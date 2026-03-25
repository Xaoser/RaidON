package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.xaoser.raidon.Raidon;

public final class RaidPlaySoundS2CPacket implements CustomPacketPayload {
    public static final Type<RaidPlaySoundS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Raidon.MODID, "raid_play_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidPlaySoundS2CPacket> STREAM_CODEC =
            StreamCodec.ofMember(RaidPlaySoundS2CPacket::encode, RaidPlaySoundS2CPacket::decode);

    private final ResourceLocation soundId;
    private final SoundSource source;
    private final float volume;
    private final float pitch;

    public RaidPlaySoundS2CPacket(ResourceLocation soundId, SoundSource source, float volume, float pitch) {
        this.soundId = soundId;
        this.source = source == null ? SoundSource.MASTER : source;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public Type<RaidPlaySoundS2CPacket> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(soundId);
        buf.writeEnum(source);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
    }

    public static RaidPlaySoundS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new RaidPlaySoundS2CPacket(
                buf.readResourceLocation(),
                buf.readEnum(SoundSource.class),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public static void handle(RaidPlaySoundS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(payload::playClientSound);
    }

    private void playClientSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(
                soundId,
                source,
                clamp(volume, 0.0F, 64.0F),
                clamp(pitch, 0.0F, 4.0F),
                RandomSource.create(),
                false,
                0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        ));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
