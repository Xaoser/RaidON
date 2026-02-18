package ru.xaoser.raidon.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public enum RaidHudOverlay implements IGuiOverlay {
    INSTANCE;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!RaidHudState.shouldRender()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        Font font = mc.font;

        int wavesTotal = Math.max(1, RaidHudState.totalWaves());
        int waveIndex = Math.max(0, RaidHudState.waveIndex());
        int alive = RaidHudState.aliveInWave();
        int total = Math.max(1, RaidHudState.totalInWave());
        int barWidth = Math.max(1, RaidHudState.barWidth());
        int barHeight = Math.max(1, RaidHudState.barHeight());

        int x = screenWidth - barWidth - 15;
        int y = screenHeight - barHeight - 30;

        guiGraphics.fill(x - 4, y - 8, x + barWidth + 4, y + barHeight + 8, 0xAA000000);

        float waveProgress = Math.min(1.0F, (waveIndex + 1) / (float) wavesTotal);
        int waveBarWidth = (int) (barWidth * waveProgress);

        ResourceLocation mainTexture = RaidHudState.mainTexture();
        ResourceLocation progressTexture = RaidHudState.progressTexture();

        if (mainTexture != null) {
            guiGraphics.blit(mainTexture, x, y, 0, 0, barWidth, barHeight, barWidth, barHeight);
        } else {
            guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF333333);
        }

        if (progressTexture != null) {
            guiGraphics.blit(progressTexture, x, y, 0, 0, waveBarWidth, barHeight, barWidth, barHeight);
        } else {
            guiGraphics.fill(x, y, x + waveBarWidth, y + barHeight, 0xFF4CAF50);
        }

        float mobProgress = 1.0F - Math.min(1.0F, alive / (float) total);
        int mobBar = (int) (barWidth * mobProgress);
        guiGraphics.fill(x, y - 6, x + mobBar, y - 2, 0xFF444444);
        guiGraphics.fill(x, y - 6, x + barWidth, y - 2, 0xFFE0A030);

        Component title = Component.translatable("raidon.hud.wave", waveIndex + 1, wavesTotal);
        Component mobs = Component.translatable("raidon.hud.mobs", alive, total);
        guiGraphics.drawString(font, title, x, y - 18, 0xFFFFFF, false);
        guiGraphics.drawString(font, mobs, x, y + barHeight + 2, 0xFFFFFF, false);
    }
}
