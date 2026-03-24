package ru.xaoser.raidon.runtime.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;

import javax.annotation.Nullable;

public final class RaidHudState {
    private static @Nullable RaidProgressS2CPacket.Progress progress;

    private static float displayedWaveProgress = 0.0F;
    private static int displayedWaveIndex = -1;
    private static long lastUpdateMs = -1L;

    private RaidHudState() {}

    public static void update(@Nullable RaidProgressS2CPacket.Progress newProgress) {
        progress = newProgress;
        if (newProgress == null) {
            displayedWaveProgress = 0.0F;
            displayedWaveIndex = -1;
            lastUpdateMs = -1L;
        }
    }

    public static boolean shouldRender() {
        return progress != null && Minecraft.getInstance().player != null;
    }

    public static float smoothWaveProgress() {
        if (progress == null) {
            displayedWaveProgress = 0.0F;
            displayedWaveIndex = -1;
            lastUpdateMs = -1L;
            return 0.0F;
        }

        int wave = Math.max(0, progress.waveIndex());
        int total = Math.max(1, progress.totalInWave());
        int alive = Math.max(0, progress.aliveInWave());
        float target = 1.0F - Math.min(1.0F, alive / (float) total);

        long now = Util.getMillis();
        if (lastUpdateMs < 0L) {
            lastUpdateMs = now;
        }
        float deltaSeconds = Math.max(0.0F, Math.min(0.2F, (now - lastUpdateMs) / 1000.0F));
        lastUpdateMs = now;

        if (displayedWaveIndex != wave) {
            displayedWaveIndex = wave;
            if (displayedWaveProgress < target) {
                displayedWaveProgress = target;
            }
        }

        float speed = displayedWaveProgress > target ? 7.0F : 4.5F;
        float alpha = Math.min(1.0F, speed * deltaSeconds);
        displayedWaveProgress += (target - displayedWaveProgress) * alpha;

        if (Math.abs(target - displayedWaveProgress) < 0.002F) {
            displayedWaveProgress = target;
        }

        return Math.max(0.0F, Math.min(1.0F, displayedWaveProgress));
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

    public static ResourceLocation progressEmptyTexture() {
        return progress == null ? null : progress.progressEmptyTexture();
    }

    public static ResourceLocation progressFullTexture() {
        return progress == null ? null : progress.progressFullTexture();
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
