package ru.xaoser.raidon.runtime.raid;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public record RaidGuiSettings(
        @Nullable ResourceLocation mainTexture,
        @Nullable ResourceLocation progressTexture,
        int width,
        int height
) {
    public static final int DEFAULT_WIDTH = 120;
    public static final int DEFAULT_HEIGHT = 12;
    public static final RaidGuiSettings DEFAULT = new RaidGuiSettings(null, null, DEFAULT_WIDTH, DEFAULT_HEIGHT);

    public RaidGuiSettings {
        width = width > 0 ? width : DEFAULT_WIDTH;
        height = height > 0 ? height : DEFAULT_HEIGHT;
    }
}
