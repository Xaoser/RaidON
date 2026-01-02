package ru.xaoser.raidon.api;

import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.api.sup.RaidAction;

import java.util.List;

public final class Raid {
    private final ResourceLocation id;
    private final List<RaidWave> waves;
    private final float difficulty; // 0..10
    private final RaidAction endAction;

    public Raid(ResourceLocation id, List<RaidWave> waves, float difficulty, RaidAction endAction) {
        this.id = id;
        this.waves = List.copyOf(waves);
        this.difficulty = difficulty;
        this.endAction = endAction;
    }

    public ResourceLocation id() { return id; }
    public List<RaidWave> waves() { return waves; }
    public float difficulty() { return difficulty; }
    public RaidAction endAction() { return endAction; }
}