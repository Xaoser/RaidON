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

        ResourceLocation mainTexture = RaidHudState.mainTexture();
        ResourceLocation progressTexture = RaidHudState.progressTexture();
        boolean useCustomGui = mainTexture != null && progressTexture != null;

        int barWidth = useCustomGui ? Math.max(1, RaidHudState.barWidth()) : 160;
        int barHeight = useCustomGui ? Math.max(1, RaidHudState.barHeight()) : 12;

        ResourceLocation mainTexture = RaidHudState.mainTexture();
        ResourceLocation progressTexture = RaidHudState.progressTexture();
        boolean useCustomGui = mainTexture != null && progressTexture != null;

        int barWidth = useCustomGui ? Math.max(1, RaidHudState.barWidth()) : 160;
        int barHeight = useCustomGui ? Math.max(1, RaidHudState.barHeight()) : 12;

        int x = screenWidth - barWidth - 15;
        int y = screenHeight - barHeight - 30;

        guiGraphics.fill(x - 4, y - 8, x + barWidth + 4, y + barHeight + 18, 0xAA000000);

        float wavePartProgress = 1.0F - Math.min(1.0F, alive / (float) total);
        float raidProgress = Math.min(1.0F, (waveIndex + wavePartProgress) / (float) wavesTotal);
        int waveBarWidth = (int) (barWidth * raidProgress);

        if (useCustomGui) {
            guiGraphics.blit(mainTexture, x, y, 0, 0, barWidth, barHeight, barWidth, barHeight);
            guiGraphics.blit(progressTexture, x, y, 0, 0, waveBarWidth, barHeight, barWidth, barHeight);
        } else {
            guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF2A2A2A);
            int fillRight = Math.max(x + 1, Math.min(x + barWidth - 1, x + waveBarWidth - 1));
            guiGraphics.fill(x + 1, y + 1, fillRight, y + barHeight - 1, 0xFF57B957);
        }

        Component waveText = Component.translatable("raidon.hud.wave", waveIndex + 1, wavesTotal);
        Component mobsText = Component.translatable("raidon.hud.mobs", alive, total);

        ResourceLocation mainTexture = RaidHudState.mainTexture();
        ResourceLocation progressTexture = RaidHudState.progressTexture();
        boolean useCustomGui = mainTexture != null && progressTexture != null;

        int barWidth = useCustomGui ? Math.max(1, RaidHudState.barWidth()) : 160;
        int barHeight = useCustomGui ? Math.max(1, RaidHudState.barHeight()) : 12;

        int x = screenWidth - barWidth - 15;
        int y = screenHeight - barHeight - 30;

        guiGraphics.fill(x - 4, y - 8, x + barWidth + 4, y + barHeight + 18, 0xAA000000);

        float wavePartProgress = 1.0F - Math.min(1.0F, alive / (float) total);
        float raidProgress = Math.min(1.0F, (waveIndex + wavePartProgress) / (float) wavesTotal);
        int waveBarWidth = (int) (barWidth * raidProgress);

        if (useCustomGui) {
            guiGraphics.blit(mainTexture, x, y, 0, 0, barWidth, barHeight, barWidth, barHeight);
            guiGraphics.blit(progressTexture, x, y, 0, 0, waveBarWidth, barHeight, barWidth, barHeight);
        } else {
            guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF2A2A2A);
            int fillRight = Math.max(x + 1, Math.min(x + barWidth - 1, x + waveBarWidth - 1));
            guiGraphics.fill(x + 1, y + 1, fillRight, y + barHeight - 1, 0xFF57B957);
        }

        Component waveText = Component.translatable("raidon.hud.wave", waveIndex + 1, wavesTotal);
        Component mobsText = Component.translatable("raidon.hud.mobs", alive, total);

        if (useCustomGui) {
            guiGraphics.drawString(font, waveText, x, y - 18, 0xFFFFFF, false);
            guiGraphics.drawString(font, mobsText, x, y + barHeight + 2, 0xFFFFFF, false);
        } else {
            int waveTextX = x + (barWidth - font.width(waveText)) / 2;
            guiGraphics.drawString(font, waveText, waveTextX, y + 2, 0xFFFFFF, false);
            guiGraphics.drawString(font, mobsText, x, y + barHeight + 4, 0xFFFFFF, false);
        }
    }
}
