package ru.xaoser.raidon.api.sup;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

public record MobEntry(int count, EntityType<? extends Mob> type, SpawnBehavior behavior) { }
