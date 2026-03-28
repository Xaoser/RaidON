package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.network.RaidPacket;

public final class RaidProgressS2CPacket implements RaidPacket {
    public static final ResourceLocation ID = new ResourceLocation(Raidon.MODID, "raid_progress");

    private final Progress progress;

    public RaidProgressS2CPacket(Progress progress) {
        this.progress = progress;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(progress != null);
        if (progress != null) {
            progress.encode(buf);
        }
    }

    public static RaidProgressS2CPacket decode(FriendlyByteBuf buf) {
        Progress progress = null;
        if (buf.readBoolean()) {
            progress = Progress.decode(buf);
        }
        return new RaidProgressS2CPacket(progress);
    }

    public static void handle(RaidProgressS2CPacket payload) {
        try {
            Class<?> handlerClass = Class.forName("ru.xaoser.raidon.runtime.client.RaidClientPacketHandlers");
            handlerClass.getMethod("handleRaidProgress", RaidProgressS2CPacket.class).invoke(null, payload);
        } catch (ReflectiveOperationException ignored) {
            // Dedicated servers do not load client packet handlers.
        }
    }

    public Progress progress() {
        return progress;
    }

    public record Progress(
            ResourceLocation raidId,
            String raidName,
            int waveIndex,
            int totalWaves,
            int aliveInWave,
            int totalInWave,
            ResourceLocation progressEmptyTexture,
            ResourceLocation progressFullTexture,
            String hudTone,
            int barWidth,
            int barHeight
    ) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(raidId);
            buf.writeUtf(raidName == null ? "" : raidName, 256);
            buf.writeVarInt(waveIndex);
            buf.writeVarInt(totalWaves);
            buf.writeVarInt(aliveInWave);
            buf.writeVarInt(totalInWave);
            buf.writeBoolean(progressEmptyTexture != null);
            if (progressEmptyTexture != null) {
                buf.writeResourceLocation(progressEmptyTexture);
            }
            buf.writeBoolean(progressFullTexture != null);
            if (progressFullTexture != null) {
                buf.writeResourceLocation(progressFullTexture);
            }
            buf.writeUtf(hudTone == null ? "" : hudTone, 64);
            buf.writeVarInt(barWidth);
            buf.writeVarInt(barHeight);
        }

        static Progress decode(FriendlyByteBuf buf) {
            ResourceLocation raidId = buf.readResourceLocation();
            String raidName = buf.readUtf(256);
            int waveIndex = buf.readVarInt();
            int totalWaves = buf.readVarInt();
            int aliveInWave = buf.readVarInt();
            int totalInWave = buf.readVarInt();
            ResourceLocation progressEmptyTexture = buf.readBoolean() ? buf.readResourceLocation() : null;
            ResourceLocation progressFullTexture = buf.readBoolean() ? buf.readResourceLocation() : null;
            String hudTone = buf.readUtf(64);
            int barWidth = buf.readVarInt();
            int barHeight = buf.readVarInt();
            return new Progress(
                    raidId,
                    raidName,
                    waveIndex,
                    totalWaves,
                    aliveInWave,
                    totalInWave,
                    progressEmptyTexture,
                    progressFullTexture,
                    hudTone,
                    barWidth,
                    barHeight
            );
        }
    }
}
