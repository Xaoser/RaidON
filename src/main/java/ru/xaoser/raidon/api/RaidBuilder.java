package ru.xaoser.raidon.api;

import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.RaidAction;

import java.util.*;
import java.util.function.Consumer;

public final class RaidBuilder {
    private final ResourceLocation id;

    // waves по индексу, чтобы addWave(1) работал “точно”
    private final Map<Integer, WaveBuilder> waves = new TreeMap<>();

    private float difficulty = 0.0F; // 0..10
    private RaidAction endAction = ctx -> {};
    private List<DropEntry> globalDrops = List.of();

    // индекс “по порядку” для addWave(Consumer)
    private int nextIndex = 0;

    public RaidBuilder(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    // Create and configure Wave
    public RaidBuilder addWave(Consumer<WaveBuilder> config) {
        Objects.requireNonNull(config, "config");
        int idx = nextIndex++;
        WaveBuilder wb = waves.computeIfAbsent(idx, WaveBuilder::new);
        config.accept(wb);
        return this;
    }

    // Creating empty Wave (you can add a content with wave(int index, ....)
    public RaidBuilder addWave(int index) {
        if (index < 0) throw new IllegalArgumentException("Wave index must be >= 0");
        waves.computeIfAbsent(index, WaveBuilder::new);
        // nextIndex двигаем вперёд, чтобы “по порядку” не пересекалось
        nextIndex = Math.max(nextIndex, index + 1);
        return this;
    }

    // Add a content to empty Wave
    public RaidBuilder wave(int index, Consumer<WaveBuilder> config) {
        if (index < 0) throw new IllegalArgumentException("Wave index must be >= 0");
        Objects.requireNonNull(config, "config");
        WaveBuilder wb = waves.computeIfAbsent(index, WaveBuilder::new);
        config.accept(wb);
        nextIndex = Math.max(nextIndex, index + 1);
        return this;
    }

    // Difficulty scale a HP and damage
    public RaidBuilder difficulty(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            throw new IllegalArgumentException("Difficulty must be a finite number");
        this.difficulty = clamp(value, 0.0F, 10.0F);
        return this;
    }

    // Action after end of Raid
    public RaidBuilder endAction(RaidAction action) {
        this.endAction = Objects.requireNonNull(action, "action");
        return this;
    }

    // Drops from all mobs of Raid
    public RaidBuilder globalDrops(List<DropEntry> drops) {
        this.globalDrops = drops == null ? List.of() : List.copyOf(drops);
        return this;
    }

    public Raid build() {
        if (waves.isEmpty()) {
            throw new IllegalStateException("Raid must contain at least one wave");
        }

        List<RaidWave> builtWaves = new ArrayList<>();
        for (var e : waves.entrySet()) {
            builtWaves.add(e.getValue().build());
        }

        return new Raid(id, builtWaves, difficulty, endAction, globalDrops);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
