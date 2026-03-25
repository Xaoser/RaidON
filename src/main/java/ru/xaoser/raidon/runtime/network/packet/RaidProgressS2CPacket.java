package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.client.RaidHudState;

public final class RaidProgressS2CPacket implements CustomPacketPayload {
    public static final Type<RaidProgressS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Raidon.MODID, "raid_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidProgressS2CPacket> STREAM_CODEC =
            StreamCodec.ofMember(RaidProgressS2CPacket::encode, RaidProgressS2CPacket::decode);

    private final Progress progress;

    public RaidProgressS2CPacket(Progress progress) {
        this.progress = progress;
    }

    @Override
    public Type<RaidProgressS2CPacket> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(progress != null);
        if (progress != null) {
            progress.encode(buf);
        }
    }

    public static RaidProgressS2CPacket decode(RegistryFriendlyByteBuf buf) {
        Progress progress = null;
        if (buf.readBoolean()) {
            progress = Progress.decode(buf);
        }
        return new RaidProgressS2CPacket(progress);
    }

    public static void handle(RaidProgressS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> RaidHudState.update(payload.progress()));
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
        void encode(RegistryFriendlyByteBuf buf) {
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

        static Progress decode(RegistryFriendlyByteBuf buf) {
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
