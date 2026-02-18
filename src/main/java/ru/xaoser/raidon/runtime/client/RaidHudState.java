package ru.xaoser.raidon.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;

import javax.annotation.Nullable;

public final class RaidHudState {
    private static @Nullable RaidProgressS2CPacket.Progress progress;

    private RaidHudState() {}

    public static void update(@Nullable RaidProgressS2CPacket.Progress newProgress) {
        progress = newProgress;
    }

    public static boolean shouldRender() {
        return progress != null && Minecraft.getInstance().player != null;
    }

    public static int waveIndex() {
        return progress == null ? -1 : progress.waveIndex();
    }

    public static int totalWaves() {
        return progress == null ? 0 : progress.totalWaves();
    }

    public static int aliveInWave() {
        return progress == null ? 0 : progress.aliveInWave();
    }

    public static int totalInWave() {
        return progress == null ? 0 : progress.totalInWave();
    }


    public static ResourceLocation mainTexture() {
        return progress == null ? null : progress.mainTexture();
    }

    public static ResourceLocation progressTexture() {
        return progress == null ? null : progress.progressTexture();
    }

    public static int barWidth() {
        return progress == null ? 120 : progress.barWidth();
    }

    public static int barHeight() {
        return progress == null ? 12 : progress.barHeight();
    }

    public static ResourceLocation raidId() {
        return progress == null ? null : progress.raidId();
    }
}
