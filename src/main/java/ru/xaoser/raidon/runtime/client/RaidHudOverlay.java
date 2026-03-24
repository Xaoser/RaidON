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

    private static final int DEFAULT_PANEL_WIDTH = 168;
    private static final int DEFAULT_PANEL_HEIGHT = 40;
    private static final int DEFAULT_BAR_HEIGHT = 10;
    private static final int PANEL_MARGIN = 14;
    private static final int PANEL_BOTTOM = 54;

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

        ResourceLocation progressEmptyTexture = RaidHudState.progressEmptyTexture();
        ResourceLocation progressFullTexture = RaidHudState.progressFullTexture();
        boolean useCustomGui = progressEmptyTexture != null || progressFullTexture != null;
        float waveProgress = RaidHudState.smoothWaveProgress();
        int panelWidth = useCustomGui ? Math.max(1, RaidHudState.barWidth()) : DEFAULT_PANEL_WIDTH;
        int panelHeight = useCustomGui ? Math.max(1, RaidHudState.barHeight()) : DEFAULT_PANEL_HEIGHT;
        int x = screenWidth - panelWidth - PANEL_MARGIN;
        int y = screenHeight - panelHeight - PANEL_BOTTOM;

        Component raidText = Component.translatable("raidon.hud.raid");
        Component waveText = Component.translatable("raidon.hud.wave", waveIndex + 1, wavesTotal);
        Component mobsText = Component.translatable("raidon.hud.mobs", alive, total);

        if (useCustomGui) {
            renderCustomHud(guiGraphics, font, x, y, panelWidth, panelHeight, waveProgress,
                    progressEmptyTexture, progressFullTexture, raidText, waveText, mobsText);
            return;
        }

        renderDefaultHud(guiGraphics, font, x, y, panelWidth, panelHeight, waveProgress, raidText, waveText, mobsText);
    }

    private static void renderCustomHud(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height,
                                        float progress, ResourceLocation emptyTexture, ResourceLocation fullTexture,
                                        Component raidText, Component waveText, Component mobsText) {
        int filledWidth = Math.max(0, Math.min(width, Math.round(width * progress)));
        int infoY = y + height + 4;
        int mobsTextX = x + width - font.width(mobsText);

        if (emptyTexture != null) {
            guiGraphics.blit(emptyTexture, x, y, 0, 0, width, height, width, height);
        }
        if (fullTexture != null && filledWidth > 0) {
            guiGraphics.blit(fullTexture, x, y, 0, 0, filledWidth, height, width, height);
        }

        guiGraphics.drawString(font, raidText, x, y - 12, 0xF5E6BA, true);
        guiGraphics.drawString(font, waveText, x, infoY, 0xFFF4DE, true);
        guiGraphics.drawString(font, mobsText, mobsTextX, infoY, 0xFFF4DE, true);
    }

    private static void renderDefaultHud(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height,
                                         float progress, Component raidText, Component waveText, Component mobsText) {
        int left = x;
        int top = y;
        int right = x + width;
        int bottom = y + height;
        int barX = left + 10;
        int barY = top + 16;
        int barWidth = width - 20;
        int filledWidth = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
        int infoY = barY + DEFAULT_BAR_HEIGHT + 5;

        guiGraphics.fill(left + 2, top + 2, right + 2, bottom + 2, 0x55000000);
        guiGraphics.fill(left, top, right, bottom, 0xCC120A0A);
        guiGraphics.fill(left + 2, top + 2, right - 2, bottom - 2, 0xCC241313);
        guiGraphics.fill(left + 2, top + 14, right - 2, top + 15, 0x88C58B2A);
        guiGraphics.fill(left + 7, barY - 2, right - 7, barY + DEFAULT_BAR_HEIGHT + 2, 0xAA0A0505);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + DEFAULT_BAR_HEIGHT, 0xFF2D1717);
        guiGraphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + DEFAULT_BAR_HEIGHT - 1, 0xFF4A2323);
        if (filledWidth > 0) {
            guiGraphics.fill(barX + 1, barY + 1, barX + filledWidth - 1, barY + DEFAULT_BAR_HEIGHT - 1, 0xFFD86131);
            guiGraphics.fill(barX + 1, barY + 1, barX + filledWidth - 1, barY + 4, 0xFFF1B25E);
        }

        guiGraphics.drawString(font, raidText, left + 10, top + 5, 0xFFF1C46D, true);
        guiGraphics.drawString(font, waveText, left + 10, infoY, 0xFFF7EED7, false);
        int mobsTextX = right - 10 - font.width(mobsText);
        guiGraphics.drawString(font, mobsText, mobsTextX, infoY, 0xFFF7D0C5, false);
    }
}
