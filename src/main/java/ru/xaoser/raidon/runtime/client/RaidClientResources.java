package ru.xaoser.raidon.runtime.client;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

public final class RaidClientResources {
    private static final String PACK_ID = "raidon_config_resources";
    private static final Path ROOT = FMLPaths.CONFIGDIR.get().resolve("raidon").resolve("resources");
    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 18,
                "description": "RaidON config resources"
              }
            }
            """;
    private static final String SOUNDS_JSON = """
            {
            }
            """;

    private RaidClientResources() {
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        ensureLayout();

        event.addRepositorySource(consumer -> {
            Pack.ResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(ROOT, false);
            Pack pack = Pack.readMetaAndCreate(
                    PACK_ID,
                    Component.literal("RaidON Config Resources"),
                    false,
                    supplier,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.create(UnaryOperator.identity(), true)
            );
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }

    public static Path root() {
        ensureLayout();
        return ROOT;
    }

    private static void ensureLayout() {
        try {
            Files.createDirectories(ROOT.resolve("assets").resolve("raidon_cfg").resolve("sounds"));
            writeIfMissing(ROOT.resolve("pack.mcmeta"), PACK_META);
            writeIfMissing(ROOT.resolve("assets").resolve("raidon_cfg").resolve("sounds.json"), SOUNDS_JSON);
        } catch (IOException ignored) {
        }
    }

    private static void writeIfMissing(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
