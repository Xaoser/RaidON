package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class RaidPlaySoundS2CPacket {
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

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(soundId);
        buf.writeEnum(source);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
    }

    public static RaidPlaySoundS2CPacket decode(FriendlyByteBuf buf) {
        return new RaidPlaySoundS2CPacket(
                buf.readResourceLocation(),
                buf.readEnum(SoundSource.class),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::playClientSound));
        ctx.setPacketHandled(true);
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
