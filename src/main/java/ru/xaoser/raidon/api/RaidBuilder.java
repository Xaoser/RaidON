package ru.xaoser.raidon.api;

import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.api.sup.RaidAction;

import java.util.*;
import java.util.function.Consumer;

public final class RaidBuilder {
    private final ResourceLocation id;

    // waves по индексу, чтобы addWave(1) работал “точно”
    private final Map<Integer, WaveBuilder> waves = new TreeMap<>();

    private float difficulty = 0.0F; // 0..10
    private RaidAction endAction = ctx -> {};

    // индекс “по порядку” для addWave(Consumer)
    private int nextIndex = 0;

    public RaidBuilder(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    /** Создать волну “по порядку” и настроить её. */
    public RaidBuilder addWave(Consumer<WaveBuilder> config) {
        Objects.requireNonNull(config, "config");
        int idx = nextIndex++;
        WaveBuilder wb = waves.computeIfAbsent(idx, WaveBuilder::new);
        config.accept(wb);
        return this;
    }

    /** Создать пустую волну по индексу (можно потом наполнить через wave(index, ...)). */
    public RaidBuilder addWave(int index) {
        if (index < 0) throw new IllegalArgumentException("Wave index must be >= 0");
        waves.computeIfAbsent(index, WaveBuilder::new);
        // nextIndex двигаем вперёд, чтобы “по порядку” не пересекалось
        nextIndex = Math.max(nextIndex, index + 1);
        return this;
    }

    /** Донастроить уже существующую волну по индексу. */
    public RaidBuilder wave(int index, Consumer<WaveBuilder> config) {
        if (index < 0) throw new IllegalArgumentException("Wave index must be >= 0");
        Objects.requireNonNull(config, "config");
        WaveBuilder wb = waves.computeIfAbsent(index, WaveBuilder::new);
        config.accept(wb);
        nextIndex = Math.max(nextIndex, index + 1);
        return this;
    }

    /** 0..10 влияет на хп/урон (скейлинг делайте в рантайме). */
    public RaidBuilder difficulty(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            throw new IllegalArgumentException("Difficulty must be a finite number");
        this.difficulty = clamp(value, 0.0F, 10.0F);
        return this;
    }

    /** Глобальное действие после завершения всего рейда. */
    public RaidBuilder endAction(RaidAction action) {
        this.endAction = Objects.requireNonNull(action, "action");
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

        return new Raid(id, builtWaves, difficulty, endAction);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}