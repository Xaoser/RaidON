package ru.xaoser.raidon.api;

import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.runtime.raid.RaidGuiSettings;

import javax.annotation.Nullable;

/**
 * Builder for GUI settings when creating raids from code.
 */
public final class RaidGuiBuilder {
    private @Nullable ResourceLocation mainTexture;
    private @Nullable ResourceLocation progressTexture;
    private @Nullable ResourceLocation progressEmptyTexture;
    private @Nullable ResourceLocation progressFullTexture;
    private int width = RaidGuiSettings.DEFAULT_WIDTH;
    private int height = RaidGuiSettings.DEFAULT_HEIGHT;

    private RaidGuiBuilder() {}

    public static RaidGuiBuilder create() {
        return new RaidGuiBuilder();
    }

    public RaidGuiBuilder mainTexture(@Nullable ResourceLocation value) {
        this.mainTexture = value;
        return this;
    }

    public RaidGuiBuilder progressTexture(@Nullable ResourceLocation value) {
        this.progressTexture = value;
        return this;
    }

    public RaidGuiBuilder progressEmptyTexture(@Nullable ResourceLocation value) {
        this.progressEmptyTexture = value;
        return this;
    }

    public RaidGuiBuilder progressFullTexture(@Nullable ResourceLocation value) {
        this.progressFullTexture = value;
        return this;
    }

    public RaidGuiBuilder progressTextures(@Nullable ResourceLocation empty, @Nullable ResourceLocation full) {
        this.progressEmptyTexture = empty;
        this.progressFullTexture = full;
        return this;
    }

    public RaidGuiBuilder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public RaidGuiSettings build() {
        return new RaidGuiSettings(mainTexture, progressTexture, progressEmptyTexture, progressFullTexture, width, height);
    }
}
