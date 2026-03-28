package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.network.RaidPacket;

public final class RaidStopSoundS2CPacket implements RaidPacket {
    public static final ResourceLocation ID = new ResourceLocation(Raidon.MODID, "raid_stop_sound");

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
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(zoneX);
        buf.writeDouble(zoneY);
        buf.writeDouble(zoneZ);
        buf.writeFloat(zoneRadius);
    }

    public static RaidStopSoundS2CPacket decode(FriendlyByteBuf buf) {
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
