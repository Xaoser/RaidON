package ru.xaoser.raidon.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.client.RaidClientResources;

import java.io.IOException;
import java.util.Objects;

public final class RaidonFabricResources {
    private static long tickCounter;
    private static long lastSourceFingerprint = Long.MIN_VALUE;

    private RaidonFabricResources() {
    }

    public static void prepare() {
        RaidClientResources.ensureLayout();
        try {
            RaidClientResources.mirrorTo(RaidClientResources.fabricMirrorRoot());
            lastSourceFingerprint = RaidClientResources.computeSourceFingerprint(RaidClientResources.sourceRoot());
            Raidon.LOGGER.info("Prepared Fabric config resource pack mirror at {}", RaidClientResources.fabricMirrorRoot());
            enableMirroredPack();
        } catch (IOException exception) {
            Raidon.LOGGER.warn("Failed to mirror Fabric config resource pack to {}", RaidClientResources.fabricMirrorRoot(), exception);
        }
    }

    public static void onClientTick() {
        if ((tickCounter++ % 20L) != 0L) {
            return;
        }

        long fingerprint = RaidClientResources.computeSourceFingerprint(RaidClientResources.sourceRoot());
        if (fingerprint == lastSourceFingerprint) {
            return;
        }

        lastSourceFingerprint = fingerprint;
        prepare();
    }

    private static void enableMirroredPack() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            PackRepository repository = minecraft.getResourcePackRepository();
            repository.reload();

            String selectedId = repository.getSelectedIds().stream()
                    .filter(RaidonFabricResources::matchesRaidonPack)
                    .findFirst()
                    .orElse(null);
            if (selectedId != null) {
                return;
            }

            Pack pack = repository.getAvailablePacks().stream()
                    .filter(candidate -> matchesRaidonPack(candidate.getId())
                            || Objects.equals(candidate.getTitle().getString(), RaidClientResources.PACK_TITLE))
                    .findFirst()
                    .orElse(null);
            if (pack == null) {
                Raidon.LOGGER.warn("Fabric config resource pack {} is not visible in repository after mirroring",
                        RaidClientResources.PACK_ID);
                return;
            }

            if (repository.addPack(pack.getId())) {
                minecraft.options.updateResourcePacks(repository);
                Raidon.LOGGER.info("Enabled Fabric config resource pack {}", pack.getId());
            }
        });
    }

    private static boolean matchesRaidonPack(String id) {
        return id != null && (id.equals(RaidClientResources.PACK_ID) || id.endsWith("/" + RaidClientResources.PACK_ID));
    }
}
