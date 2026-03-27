package ru.xaoser.raidon.runtime.raid;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RaidGuiSettings(
        @Nullable ResourceLocation mainTexture,
        @Nullable ResourceLocation progressTexture,
        @Nullable ResourceLocation progressEmptyTexture,
        @Nullable ResourceLocation progressFullTexture,
        @Nullable String tone,
        int width,
        int height
) {
    public static final int DEFAULT_WIDTH = 120;
    public static final int DEFAULT_HEIGHT = 12;
    public static final RaidGuiSettings DEFAULT = new RaidGuiSettings(null, null, null, null, null, DEFAULT_WIDTH, DEFAULT_HEIGHT);

    public RaidGuiSettings(@Nullable ResourceLocation mainTexture, @Nullable ResourceLocation progressTexture, int width, int height) {
        this(mainTexture, progressTexture, null, null, null, width, height);
    }

    public RaidGuiSettings(@Nullable ResourceLocation mainTexture, @Nullable ResourceLocation progressTexture,
                           @Nullable ResourceLocation progressEmptyTexture, @Nullable ResourceLocation progressFullTexture,
                           int width, int height) {
        this(mainTexture, progressTexture, progressEmptyTexture, progressFullTexture, null, width, height);
    }

    public RaidGuiSettings {
        tone = tone == null || tone.isBlank() ? null : tone.trim();
        width = width > 0 ? width : DEFAULT_WIDTH;
        height = height > 0 ? height : DEFAULT_HEIGHT;
    }

    public @Nullable ResourceLocation resolvedProgressEmptyTexture() {
        return progressEmptyTexture != null ? progressEmptyTexture : mainTexture;
    }

    public @Nullable ResourceLocation resolvedProgressFullTexture() {
        return progressFullTexture != null ? progressFullTexture : progressTexture;
    }
}
