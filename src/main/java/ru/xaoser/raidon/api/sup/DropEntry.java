package ru.xaoser.raidon.api.sup;

import net.minecraft.world.item.Item;

import java.util.Objects;

public record DropEntry(Item item, int min, int max, double chance) {
    public DropEntry {
        Objects.requireNonNull(item, "item");
        if (min < 0 || max < 0) {
            throw new IllegalArgumentException("min/max must be >= 0");
        }
        if (max < min) {
            throw new IllegalArgumentException("max must be >= min");
        }
        if (Double.isNaN(chance) || Double.isInfinite(chance)) {
            throw new IllegalArgumentException("chance must be a finite number");
        }
    }
}
