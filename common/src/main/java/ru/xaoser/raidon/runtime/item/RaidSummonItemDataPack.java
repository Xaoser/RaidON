package ru.xaoser.raidon.runtime.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import ru.xaoser.raidon.RaidPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

public final class RaidSummonItemDataPack {
    public static final String PACK_DIR_NAME = "raidon_config_data";

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private RaidSummonItemDataPack() {
    }

    public static void install(MinecraftServer server, Logger logger) {
        if (server == null) {
            return;
        }

        Path generatedRoot = generatedRoot();
        logger.info("[Raidon] Preparing summon-item datapack {} in {}", PACK_DIR_NAME, generatedRoot.toAbsolutePath());
        try {
            recreateRoot(generatedRoot);
            writePackMeta(generatedRoot);
            int recipesWritten = writeRecipes(generatedRoot);
            mirrorToWorld(server, generatedRoot);
            enableAndReload(server, logger);
            logger.info("[Raidon] Installed summon-item datapack {} with {} recipe file(s)",
                    PACK_DIR_NAME, recipesWritten);
        } catch (IOException exception) {
            logger.error("[Raidon] Failed to install summon-item datapack {}", PACK_DIR_NAME, exception);
        } catch (CompletionException exception) {
            logger.error("[Raidon] Failed to reload resources after updating {}", PACK_DIR_NAME, exception);
        }
    }

    private static Path generatedRoot() {
        return RaidPlatform.getConfigDir().resolve("raidon").resolve("datapacks").resolve(PACK_DIR_NAME);
    }

    private static void recreateRoot(Path root) throws IOException {
        deleteDirectory(root);
        Files.createDirectories(root);
    }

    private static void writePackMeta(Path root) throws IOException {
        String meta = """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "RaidON generated summon item recipes"
                  }
                }
                """.formatted(SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        Files.writeString(root.resolve("pack.mcmeta"), meta);
    }

    private static int writeRecipes(Path root) throws IOException {
        int written = 0;
        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            for (int index = 0; index < definition.recipes().size(); index++) {
                RaidSummonRecipeDefinition recipe = definition.recipes().get(index);
                Path recipePath = root
                        .resolve("data")
                        .resolve(definition.id().getNamespace())
                        .resolve("recipes")
                        .resolve(recipe.fileName(definition.id(), index));
                Files.createDirectories(recipePath.getParent());
                Files.writeString(recipePath, GSON.toJson(recipe.toJson(definition.id())));
                written++;
            }
        }
        return written;
    }

    private static void mirrorToWorld(MinecraftServer server, Path sourceRoot) throws IOException {
        Path worldPackRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_DIR_NAME);
        deleteDirectory(worldPackRoot);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : (Iterable<Path>) paths::iterator) {
                Path relative = sourceRoot.relativize(source);
                Path target = worldPackRoot.resolve(relative.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void enableAndReload(MinecraftServer server, Logger logger) {
        PackRepository repository = server.getPackRepository();
        repository.reload();

        String packId = repository.getAvailableIds().stream()
                .filter(id -> id != null && id.contains(PACK_DIR_NAME))
                .findFirst()
                .orElse(null);
        if (packId == null) {
            logger.warn("[Raidon] Could not find generated datapack {} in available pack ids {}", PACK_DIR_NAME, repository.getAvailableIds());
            return;
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>(repository.getSelectedIds());
        selected.add(packId);
        repository.setSelected(selected);
        server.reloadResources(selected).join();
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
