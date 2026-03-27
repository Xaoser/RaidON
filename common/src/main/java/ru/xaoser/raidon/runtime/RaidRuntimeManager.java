package ru.xaoser.raidon.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RaidRuntimeManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, RaidDefinition> REGISTERED = new ConcurrentHashMap<>();

    private RaidRuntimeManager() { }

    public static void clear() {
        REGISTERED.clear();
    }

    public static void register(RaidDefinition definition) {
        REGISTERED.compute(definition.raid().id(), (id, existing) -> {
            if (existing != null) {
                LOGGER.warn("Replacing raid definition for {} with a new one from configuration.", id);
            }
            return definition;
        });
    }

    public static Optional<RaidDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(REGISTERED.get(id));
    }

    public static Collection<RaidDefinition> all() {
        return Collections.unmodifiableCollection(REGISTERED.values());
    }
}
