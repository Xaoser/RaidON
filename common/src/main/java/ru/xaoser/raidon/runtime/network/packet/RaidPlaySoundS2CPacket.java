package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.network.RaidPacket;

public final class RaidPlaySoundS2CPacket implements RaidPacket {
    public static final ResourceLocation ID = new ResourceLocation(Raidon.MODID, "raid_play_sound");

    private final ResourceLocation soundId;
    private final SoundSource source;
    private final float volume;
    private final float pitch;
    private final boolean zoneLimited;
    private final double zoneX;
    private final double zoneY;
    private final double zoneZ;
    private final float zoneRadius;

    public RaidPlaySoundS2CPacket(ResourceLocation soundId, SoundSource source, float volume, float pitch) {
        this(soundId, source, volume, pitch, false, 0.0D, 0.0D, 0.0D, 0.0F);
    }

    public RaidPlaySoundS2CPacket(ResourceLocation soundId, SoundSource source, float volume, float pitch,
                                  boolean zoneLimited, double zoneX, double zoneY, double zoneZ, float zoneRadius) {
        this.soundId = soundId;
        this.source = source == null ? SoundSource.MASTER : source;
        this.volume = volume;
        this.pitch = pitch;
        this.zoneLimited = zoneLimited;
        this.zoneX = zoneX;
        this.zoneY = zoneY;
        this.zoneZ = zoneZ;
        this.zoneRadius = Math.max(0.0F, zoneRadius);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(soundId);
        buf.writeEnum(source);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        buf.writeBoolean(zoneLimited);
        if (zoneLimited) {
            buf.writeDouble(zoneX);
            buf.writeDouble(zoneY);
            buf.writeDouble(zoneZ);
            buf.writeFloat(zoneRadius);
        }
    }

    public static RaidPlaySoundS2CPacket decode(FriendlyByteBuf buf) {
        ResourceLocation soundId = buf.readResourceLocation();
        SoundSource source = buf.readEnum(SoundSource.class);
        float volume = buf.readFloat();
        float pitch = buf.readFloat();
        boolean zoneLimited = buf.readBoolean();
        double zoneX = 0.0D;
        double zoneY = 0.0D;
        double zoneZ = 0.0D;
        float zoneRadius = 0.0F;
        if (zoneLimited) {
            zoneX = buf.readDouble();
            zoneY = buf.readDouble();
            zoneZ = buf.readDouble();
            zoneRadius = buf.readFloat();
        }
        return new RaidPlaySoundS2CPacket(
                soundId,
                source,
                volume,
                pitch,
                zoneLimited,
                zoneX,
                zoneY,
                zoneZ,
                zoneRadius
        );
    }

    public static void handle(RaidPlaySoundS2CPacket payload) {
        invokeClientHandler("handlePlaySound", payload);
    }

    public ResourceLocation soundId() {
        return soundId;
    }

    public SoundSource source() {
        return source;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    public boolean zoneLimited() {
        return zoneLimited;
    }

    public double zoneX() {
        return zoneX;
    }

    public double zoneY() {
        return zoneY;
    }

    public double zoneZ() {
        return zoneZ;
    }

    public float zoneRadius() {
        return zoneRadius;
    }

    private static void invokeClientHandler(String methodName, RaidPlaySoundS2CPacket payload) {
        try {
            Class<?> handlerClass = Class.forName("ru.xaoser.raidon.runtime.client.RaidClientPacketHandlers");
            handlerClass.getMethod(methodName, RaidPlaySoundS2CPacket.class).invoke(null, payload);
        } catch (ReflectiveOperationException ignored) {
            // Dedicated servers do not load client packet handlers.
        }
    }
}
