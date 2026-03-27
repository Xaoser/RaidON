package ru.xaoser.raidon.api;

import ru.xaoser.raidon.api.sup.MobEntry;
import ru.xaoser.raidon.api.sup.RaidAction;
import ru.xaoser.raidon.api.sup.WaveCompleteCondition;

import java.util.List;

public final class RaidWave {
    private final int index;
    private final List<MobEntry> mobs;
    private final int spawnRadius;
    private final WaveCompleteCondition completeCondition;
    private final RaidAction onWaveStart;
    private final RaidAction onWaveEnd;

    public RaidWave(
            int index,
            List<MobEntry> mobs,
            int spawnRadius,
            WaveCompleteCondition completeCondition,
            RaidAction onWaveStart,
            RaidAction onWaveEnd
    ) {
        this.index = index;
        this.mobs = List.copyOf(mobs);
        this.spawnRadius = spawnRadius;
        this.completeCondition = completeCondition;
        this.onWaveStart = onWaveStart;
        this.onWaveEnd = onWaveEnd;
    }

    public int index() { return index; }
    public List<MobEntry> mobs() { return mobs; }
    public int spawnRadius() { return spawnRadius; }
    public WaveCompleteCondition completeCondition() { return completeCondition; }
    public RaidAction onWaveStart() { return onWaveStart; }
    public RaidAction onWaveEnd() { return onWaveEnd; }
}