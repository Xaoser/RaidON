package ru.xaoser.raidon.api;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.MobEntry;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.RaidAction;
import ru.xaoser.raidon.api.sup.WaveCompleteCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WaveBuilder {
    private final int index;

    private final List<MobEntry> mobs = new ArrayList<>();
    private int spawnRadius = 16;

    private WaveCompleteCondition completeCondition = (rt, ctx) -> rt.aliveMobsInCurrentWave() <= 0;

    private RaidAction onWaveStart = ctx -> {};
    private RaidAction onWaveEnd = ctx -> {};

    WaveBuilder(int index) {
        this.index = index;
    }

    public WaveBuilder mob(int count, EntityType<? extends Mob> type) {
        return mob(count, type, ru.xaoser.raidon.api.sup.SpawnBehavior.NEUTRAL);
    }

    public WaveBuilder mob(int count, EntityType<? extends Mob> type, ru.xaoser.raidon.api.sup.SpawnBehavior behavior) {
        return mob(count, type, behavior, null, List.of(), MobTargeting.defaults());
    }

    public WaveBuilder mob(
            int count,
            EntityType<? extends Mob> type,
            ru.xaoser.raidon.api.sup.SpawnBehavior behavior,
            Float baseDamage,
            List<DropEntry> drops
    ) {
        return mob(count, type, behavior, baseDamage, drops, MobTargeting.defaults());
    }

    public WaveBuilder mob(
            int count,
            EntityType<? extends Mob> type,
            ru.xaoser.raidon.api.sup.SpawnBehavior behavior,
            Float baseDamage,
            List<DropEntry> drops,
            MobTargeting targeting
    ) {
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        mobs.add(new MobEntry(
                count,
                Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(behavior, "behavior"),
                baseDamage,
                drops,
                targeting
        ));
        return this;
    }

    public WaveBuilder spawnRadius(int blocks) {
        if (blocks < 1) throw new IllegalArgumentException("spawnRadius must be >= 1");
        this.spawnRadius = blocks;
        return this;
    }

    /** Стандарт: волна завершается, когда все мобы мертвы (по рантайму). */
    public WaveBuilder completeWhenAllDead() {
        this.completeCondition = (rt, ctx) -> rt.aliveMobsInCurrentWave() <= 0;
        return this;
    }

    /** Своя логика завершения волны. */
    public WaveBuilder completeCondition(WaveCompleteCondition condition) {
        this.completeCondition = Objects.requireNonNull(condition, "condition");
        return this;
    }

    public WaveBuilder onWaveStart(RaidAction action) {
        this.onWaveStart = Objects.requireNonNull(action, "action");
        return this;
    }

    public WaveBuilder onWaveEnd(RaidAction action) {
        this.onWaveEnd = Objects.requireNonNull(action, "action");
        return this;
    }

    RaidWave build() {
        // Можно разрешать пустые волны, если хотите “заглушки”
        return new RaidWave(index, mobs, spawnRadius, completeCondition, onWaveStart, onWaveEnd);
    }
}
