package ru.xaoser.raidon.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public enum RaidHudOverlay implements IGuiOverlay {
    INSTANCE;

    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 12;

    @Override
    public void render(GuiGraphics graphics, float partialTick, int width, int height) {
        if (!RaidHudState.shouldRender()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        Font font = mc.font;

        int wavesTotal = Math.max(1, RaidHudState.totalWaves());
        int waveIndex = Math.max(0, RaidHudState.waveIndex());
        int alive = RaidHudState.aliveInWave();
        int total = Math.max(1, RaidHudState.totalInWave());

        int x = width - BAR_WIDTH - 15;
        int y = height - BAR_HEIGHT - 30;

        // Background
        graphics.fill(x - 4, y - 8, x + BAR_WIDTH + 4, y + BAR_HEIGHT + 8, 0xAA000000);

        // Wave progress bar
        float waveProgress = Math.min(1.0F, (waveIndex + 1) / (float) wavesTotal);
        int waveBarWidth = (int) (BAR_WIDTH * waveProgress);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF333333);
        graphics.fill(x, y, x + waveBarWidth, y + BAR_HEIGHT, 0xFF4CAF50);

        // Mob progress bar on top
        float mobProgress = 1.0F - Math.min(1.0F, alive / (float) total);
        int mobBar = (int) (BAR_WIDTH * mobProgress);
        graphics.fill(x, y - 6, x + BAR_WIDTH, y - 2, 0xFF444444);
        graphics.fill(x, y - 6, x + mobBar, y - 2, 0xFFE0A030);

        // Text
        Component title = Component.translatable("raidon.hud.wave", waveIndex + 1, wavesTotal);
        Component mobs = Component.translatable("raidon.hud.mobs", alive, total);
        graphics.drawString(font, title, x, y - 18, 0xFFFFFF, false);
        graphics.drawString(font, mobs, x, y + BAR_HEIGHT + 2, 0xFFFFFF, false);
    }
}
