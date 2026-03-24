package ru.xaoser.raidon.runtime.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.xaoser.raidon.runtime.client.RaidHudState;

import java.util.function.Supplier;

public final class RaidProgressS2CPacket {
    private final Progress progress;

    public RaidProgressS2CPacket(Progress progress) {
        this.progress = progress;
    }

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

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RaidHudState.update(progress)));
        ctx.setPacketHandled(true);
    }

    public Progress progress() {
        return progress;
    }

    public record Progress(
            ResourceLocation raidId,
            int waveIndex,
            int totalWaves,
            int aliveInWave,
            int totalInWave,
            ResourceLocation progressEmptyTexture,
            ResourceLocation progressFullTexture,
            int barWidth,
            int barHeight
    ) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(raidId);
            buf.writeVarInt(waveIndex);
            buf.writeVarInt(totalWaves);
            buf.writeVarInt(aliveInWave);
            buf.writeVarInt(totalInWave);
            buf.writeNullable(progressEmptyTexture, FriendlyByteBuf::writeResourceLocation);
            buf.writeNullable(progressFullTexture, FriendlyByteBuf::writeResourceLocation);
            buf.writeVarInt(barWidth);
            buf.writeVarInt(barHeight);
        }

        static Progress decode(FriendlyByteBuf buf) {
            return new Progress(
                    buf.readResourceLocation(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readNullable(FriendlyByteBuf::readResourceLocation),
                    buf.readNullable(FriendlyByteBuf::readResourceLocation),
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }
    }
}
