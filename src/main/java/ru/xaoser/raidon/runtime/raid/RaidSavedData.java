package ru.xaoser.raidon.runtime.raid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class RaidSavedData extends SavedData {
    private static final String DATA_NAME = "raidon_active_raids";

    private final List<CompoundTag> activeRaidTags = new ArrayList<>();

    static RaidSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RaidSavedData::load, RaidSavedData::new, DATA_NAME);
    }

    static RaidSavedData load(CompoundTag tag) {
        RaidSavedData data = new RaidSavedData();
        ListTag raids = tag.getList("raids", Tag.TAG_COMPOUND);
        for (int i = 0; i < raids.size(); i++) {
            data.activeRaidTags.add(raids.getCompound(i).copy());
        }
        return data;
    }

    List<CompoundTag> activeRaidTags() {
        return List.copyOf(activeRaidTags);
    }

    void replaceFromActive(Collection<ActiveRaid> raids) {
        activeRaidTags.clear();
        for (ActiveRaid raid : raids) {
            activeRaidTags.add(raid.saveState());
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag raids = new ListTag();
        for (CompoundTag activeRaidTag : activeRaidTags) {
            raids.add(activeRaidTag.copy());
        }
        tag.put("raids", raids);
        return tag;
    }
}
