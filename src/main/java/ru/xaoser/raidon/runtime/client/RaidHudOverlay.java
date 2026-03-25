package ru.xaoser.raidon.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.gui.overlay.ExtendedGui;
import net.neoforged.neoforge.client.gui.overlay.IGuiOverlay;
import ru.xaoser.raidon.runtime.text.RaidTextFormatter;

import java.util.Locale;

public enum RaidHudOverlay implements IGuiOverlay {
    INSTANCE;

    private static final int DEFAULT_PANEL_WIDTH = 156;
    private static final int DEFAULT_PANEL_HEIGHT = 46;
    private static final int DEFAULT_BAR_HEIGHT = 10;
    private static final int PANEL_MARGIN = 6;
    private static final int PANEL_BOTTOM = 10;

    @Override
    public void render(ExtendedGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
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

        String raidName = RaidHudState.raidName();
        Component raidText = raidName == null || raidName.isBlank()
                ? Component.translatable("raidon.hud.raid")
                : RaidTextFormatter.parse(raidName);
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
        int barY = top + 19;
        int barWidth = width - 20;
        int filledWidth = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
        int infoY = barY + DEFAULT_BAR_HEIGHT + 5;
        int[] palette = resolveDefaultPalette(RaidHudState.hudTone());

        guiGraphics.fill(left + 2, top + 2, right + 2, bottom + 2, palette[0]);
        guiGraphics.fill(left, top, right, bottom, palette[1]);
        guiGraphics.fill(left + 2, top + 2, right - 2, bottom - 2, palette[2]);
        guiGraphics.fill(left + 2, top + 13, right - 2, top + 14, palette[3]);
        guiGraphics.fill(left + 7, barY - 2, right - 7, barY + DEFAULT_BAR_HEIGHT + 2, palette[4]);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + DEFAULT_BAR_HEIGHT, palette[5]);
        guiGraphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + DEFAULT_BAR_HEIGHT - 1, palette[6]);
        if (filledWidth > 0) {
            guiGraphics.fill(barX + 1, barY + 1, barX + filledWidth - 1, barY + DEFAULT_BAR_HEIGHT - 1, palette[7]);
            guiGraphics.fill(barX + 1, barY + 1, barX + filledWidth - 1, barY + 4, palette[8]);
        }

        guiGraphics.drawString(font, raidText, left + 10, top + 5, palette[9], true);
        guiGraphics.drawString(font, waveText, left + 10, infoY, palette[10], false);
        int mobsTextX = right - 10 - font.width(mobsText);
        guiGraphics.drawString(font, mobsText, mobsTextX, infoY, palette[11], false);
    }

    private static int[] resolveDefaultPalette(String tone) {
        String normalized = tone == null ? "" : tone.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "blue", "navy", "\u0441\u0438\u043d\u0438\u0439" -> new int[]{
                    0x55000000, 0xCC0A111C, 0xCC162437, 0x8869A8E4,
                    0xAA07111C, 0xFF14263A, 0xFF27486F, 0xFF5FA9FF,
                    0xFFA9D6FF, 0xFFE3F3FF, 0xFFF1F8FF, 0xFFD7EBFF
            };
            case "green", "emerald", "\u0437\u0435\u043b\u0451\u043d\u044b\u0439", "\u0437\u0435\u043b\u0435\u043d\u044b\u0439" -> new int[]{
                    0x55000000, 0xCC0A160F, 0xCC16281D, 0x8879C66B,
                    0xAA08110B, 0xFF173222, 0xFF214631, 0xFF49B36C,
                    0xFF9BE2A9, 0xFFE3F8D9, 0xFFF0FAEA, 0xFFD6F1D7
            };
            case "purple", "violet", "\u0444\u0438\u043e\u043b\u0435\u0442\u043e\u0432\u044b\u0439" -> new int[]{
                    0x55000000, 0xCC140A1A, 0xCC251531, 0x889C73D4,
                    0xAA0E0812, 0xFF2D1A3A, 0xFF42235A, 0xFF9460E8,
                    0xFFC9A3FF, 0xFFF0E6FF, 0xFFF7F0FF, 0xFFE6D7FF
            };
            case "gold", "yellow", "orange", "\u0437\u043e\u043b\u043e\u0442\u043e\u0439", "\u0436\u0451\u043b\u0442\u044b\u0439",
                    "\u0436\u0435\u043b\u0442\u044b\u0439", "\u043e\u0440\u0430\u043d\u0436\u0435\u0432\u044b\u0439" -> new int[]{
                    0x55000000, 0xCC1A1207, 0xCC2C1E0D, 0x88D29C32,
                    0xAA120B04, 0xFF3B2510, 0xFF5A3715, 0xFFE18A28,
                    0xFFFFD07A, 0xFFFFE7A3, 0xFFFFF0CC, 0xFFFFE2B8
            };
            case "gray", "grey", "silver", "\u0441\u0435\u0440\u044b\u0439", "\u0441\u0435\u0440\u0435\u0431\u0440\u044f\u043d\u044b\u0439" -> new int[]{
                    0x55000000, 0xCC121212, 0xCC242424, 0x888B8B8B,
                    0xAA0D0D0D, 0xFF2D2D2D, 0xFF444444, 0xFF8A8A8A,
                    0xFFCFCFCF, 0xFFF2F2F2, 0xFFFAFAFA, 0xFFE4E4E4
            };
            default -> new int[]{
                    0x55000000, 0xCC120A0A, 0xCC241313, 0x88C58B2A,
                    0xAA0A0505, 0xFF2D1717, 0xFF4A2323, 0xFFD86131,
                    0xFFF1B25E, 0xFFF1C46D, 0xFFF7EED7, 0xFFF7D0C5
            };
        };
    }
}
