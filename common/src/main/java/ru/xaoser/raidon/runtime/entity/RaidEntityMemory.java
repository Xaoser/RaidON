package ru.xaoser.raidon.runtime.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class RaidEntityMemory {
    private static final Map<Entity, CompoundTag> MEMORY = Collections.synchronizedMap(new WeakHashMap<>());

    private RaidEntityMemory() {
    }

    public static CompoundTag of(Entity entity) {
        return MEMORY.computeIfAbsent(entity, key -> new CompoundTag());
    }
}
