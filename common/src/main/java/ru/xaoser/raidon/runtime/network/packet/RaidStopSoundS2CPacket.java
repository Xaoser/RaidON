package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.Raidon;

public final class RaidStopSoundS2CPacket implements CustomPacketPayload {
    public static final Type<RaidStopSoundS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Raidon.MODID, "raid_stop_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidStopSoundS2CPacket> STREAM_CODEC =
            StreamCodec.ofMember(RaidStopSoundS2CPacket::encode, RaidStopSoundS2CPacket::decode);

    private final double zoneX;
    private final double zoneY;
    private final double zoneZ;
    private final float zoneRadius;

    public RaidStopSoundS2CPacket(double zoneX, double zoneY, double zoneZ, float zoneRadius) {
        this.zoneX = zoneX;
        this.zoneY = zoneY;
        this.zoneZ = zoneZ;
        this.zoneRadius = Math.max(0.0F, zoneRadius);
    }

    @Override
    public Type<RaidStopSoundS2CPacket> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(zoneX);
        buf.writeDouble(zoneY);
        buf.writeDouble(zoneZ);
        buf.writeFloat(zoneRadius);
    }

    public static RaidStopSoundS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new RaidStopSoundS2CPacket(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat()
        );
    }

    public static void handle(RaidStopSoundS2CPacket payload) {
        invokeClientHandler("handleStopSound", payload);
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

    private static void invokeClientHandler(String methodName, RaidStopSoundS2CPacket payload) {
        try {
            Class<?> handlerClass = Class.forName("ru.xaoser.raidon.runtime.client.RaidClientPacketHandlers");
            handlerClass.getMethod(methodName, RaidStopSoundS2CPacket.class).invoke(null, payload);
        } catch (ReflectiveOperationException ignored) {
            // Dedicated servers do not load client packet handlers.
        }
    }
}
