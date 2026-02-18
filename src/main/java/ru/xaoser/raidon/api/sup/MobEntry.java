package ru.xaoser.raidon.api.sup;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Objects;

public record MobEntry(
        int count,
        EntityType<? extends Mob> type,
        SpawnBehavior behavior,
        Float baseDamage,
        List<DropEntry> drops,
        MobTargeting targeting,
        MobTraits tuning
) {
    public MobEntry(int count, EntityType<? extends Mob> type, SpawnBehavior behavior) {
        this(count, type, behavior, null, List.of(), MobTargeting.defaults(), MobTraits.defaults());
    }

    public MobEntry(int count, EntityType<? extends Mob> type, SpawnBehavior behavior, Float baseDamage) {
        this(count, type, behavior, baseDamage, List.of(), MobTargeting.defaults(), MobTraits.defaults());
    }

    public MobEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(behavior, "behavior");
        drops = drops == null ? List.of() : List.copyOf(drops);
        targeting = targeting == null ? MobTargeting.defaults() : targeting;
        tuning = tuning == null ? MobTraits.defaults() : tuning;
    }
}
