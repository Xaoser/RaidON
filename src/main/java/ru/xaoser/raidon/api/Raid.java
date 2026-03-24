package ru.xaoser.raidon.api;

import net.minecraft.resources.ResourceLocation;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.RaidAction;

import java.util.List;

public final class Raid {
    private final ResourceLocation id;
    private final String name;
    private final List<RaidWave> waves;
    private final float difficulty;
    private final RaidAction startAction;
    private final RaidAction endAction;
    private final List<DropEntry> globalDrops;

    public Raid(ResourceLocation id, String name, List<RaidWave> waves, float difficulty, RaidAction startAction, RaidAction endAction, List<DropEntry> globalDrops) {
        this.id = id;
        this.name = name == null || name.isBlank() ? id.getPath() : name;
        this.waves = List.copyOf(waves);
        this.difficulty = difficulty;
        this.startAction = startAction;
        this.endAction = endAction;
        this.globalDrops = globalDrops == null ? List.of() : List.copyOf(globalDrops);
    }

    public ResourceLocation id() { return id; }
    public String name() { return name; }
    public List<RaidWave> waves() { return waves; }
    public float difficulty() { return difficulty; }
    public RaidAction startAction() { return startAction; }
    public RaidAction endAction() { return endAction; }
    public List<DropEntry> globalDrops() { return globalDrops; }
}
