package ru.xaoser.raidon.api;

import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.RaidAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class RaidBuilder {
    private final ResourceLocation id;

    /** Stores waves by index. */
    private final Map<Integer, WaveBuilder> waves = new TreeMap<>();

    private float difficulty = 0.0F;
    private RaidAction endAction = ctx -> {};
    private List<DropEntry> globalDrops = List.of();

    /** Tracks the next free wave index. */
    private int nextIndex = 0;

    public RaidBuilder(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    /** Adds a wave at the next free index. */
    public RaidBuilder addWave(Consumer<WaveBuilder> config) {
        Objects.requireNonNull(config, "config");
        int idx = nextIndex++;
        WaveBuilder wb = waves.computeIfAbsent(idx, WaveBuilder::new);
        config.accept(wb);
        return this;
    }

    /** Reserves an empty wave slot. */
    public RaidBuilder addWave(int index) {
        if (index < 0) throw new IllegalArgumentException("Wave index must be >= 0");
        waves.computeIfAbsent(index, WaveBuilder::new);
        nextIndex = Math.max(nextIndex, index + 1);
        return this;
    }

    /** Configures a wave at a specific index. */
    public RaidBuilder wave(int index, Consumer<WaveBuilder> config) {
        if (index < 0) throw new IllegalArgumentException("Wave index must be >= 0");
        Objects.requireNonNull(config, "config");
        WaveBuilder wb = waves.computeIfAbsent(index, WaveBuilder::new);
        config.accept(wb);
        nextIndex = Math.max(nextIndex, index + 1);
        return this;
    }

    /** Sets raid difficulty in the 0..10 range. */
    public RaidBuilder difficulty(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Difficulty must be a finite number");
        }
        this.difficulty = clamp(value, 0.0F, 10.0F);
        return this;
    }

    /** Sets the action that runs after the raid ends. */
    public RaidBuilder endAction(RaidAction action) {
        this.endAction = Objects.requireNonNull(action, "action");
        return this;
    }

    /** Sets drops shared by all raid mobs. */
    public RaidBuilder globalDrops(List<DropEntry> drops) {
        this.globalDrops = drops == null ? List.of() : List.copyOf(drops);
        return this;
    }

    public Raid build() {
        if (waves.isEmpty()) {
            throw new IllegalStateException("Raid must contain at least one wave");
        }

        List<RaidWave> builtWaves = new ArrayList<>();
        for (Map.Entry<Integer, WaveBuilder> entry : waves.entrySet()) {
            builtWaves.add(entry.getValue().build());
        }

        return new Raid(id, builtWaves, difficulty, endAction, globalDrops);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
