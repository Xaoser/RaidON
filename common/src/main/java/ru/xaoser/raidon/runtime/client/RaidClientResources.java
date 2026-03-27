package ru.xaoser.raidon.runtime.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.item.RaidSummonItemConfigs;
import ru.xaoser.raidon.runtime.item.RaidSummonItemDefinition;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public final class RaidClientResources {
    public static final String PACK_ID = "raidon_config_resources";
    public static final String PACK_TITLE = "RaidON Config Resources";
    public static final String GENERATED_NAMESPACE = "raidon_cfg";

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private RaidClientResources() {
    }

    public static Pack createConfigPack() {
        ensureLayout();
        PackLocationInfo location = new PackLocationInfo(
                PACK_ID,
                Component.literal(PACK_TITLE),
                PackSource.create(UnaryOperator.identity(), true),
                Optional.empty()
        );
        Pack.ResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(root());
        return Pack.readMetaAndCreate(
                location,
                supplier,
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
    }

    public static Path root() {
        ensureLayout();
        return rootPath();
    }

    public static Path sourceRoot() {
        return rootPath();
    }

    public static Set<String> generatedSoundIds() {
        ensureLayout();
        try {
            return readGeneratedSoundIds(rootPath());
        } catch (IOException exception) {
            Raidon.LOGGER.warn("[Raidon] Failed to read generated sound ids from {}", rootPath(), exception);
            return Set.of();
        }
    }

    public static String resolveGeneratedSoundPath(String raw) {
        String normalized = normalizeGeneratedPath(raw);
        if (normalized == null) {
            return null;
        }

        Set<String> generatedIds = generatedSoundIds();
        if (generatedIds.isEmpty() || generatedIds.contains(normalized)) {
            return normalized;
        }

        int lastSlash = normalized.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        List<String> baseNameMatches = generatedIds.stream()
                .filter(id -> id.equals(baseName) || id.endsWith("/" + baseName))
                .sorted()
                .toList();
        if (baseNameMatches.size() == 1) {
            return baseNameMatches.get(0);
        }

        return normalized;
    }

    public static long computeSourceFingerprint(Path root) {
        if (root == null || !Files.exists(root)) {
            return Long.MIN_VALUE;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            long fingerprint = 1L;
            for (Path path : paths.sorted().toList()) {
                long entryHash = path.toString().hashCode();
                if (Files.isRegularFile(path)) {
                    entryHash = 31L * entryHash + Files.getLastModifiedTime(path).toMillis();
                    entryHash = 31L * entryHash + Files.size(path);
                }
                fingerprint = 31L * fingerprint + entryHash;
            }
            return fingerprint;
        } catch (IOException exception) {
            Raidon.LOGGER.debug("[Raidon] Failed to compute client resource fingerprint for {}", root, exception);
            return Long.MIN_VALUE;
        }
    }

    public static Path fabricMirrorRoot() {
        return RaidonPlatformPaths.gameRoot().resolve("resourcepacks").resolve(PACK_ID);
    }

    public static void mirrorTo(Path targetRoot) throws IOException {
        ensureLayout();
        deleteDirectory(targetRoot);
        Path sourceRoot = rootPath();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : (Iterable<Path>) paths::iterator) {
                Path relative = sourceRoot.relativize(source);
                Path target = targetRoot.resolve(relative.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void ensureLayout() {
        try {
            Path root = rootPath();
            Files.createDirectories(namespaceRoot(root).resolve("sounds"));
            Files.createDirectories(namespaceRoot(root).resolve("textures").resolve("item"));
            writeAlways(root.resolve("pack.mcmeta"), """
                    {
                      "pack": {
                        "pack_format": %d,
                        "description": "RaidON config resources"
                      }
                    }
                    """.formatted(SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)));
            syncGeneratedSoundDefinitions(root);
            syncGeneratedItemModels(root);
        } catch (IOException exception) {
            Raidon.LOGGER.warn("[Raidon] Failed to prepare client config resources at {}", rootPath(), exception);
        }
    }

    private static void syncGeneratedSoundDefinitions(Path root) throws IOException {
        Path soundsRoot = namespaceRoot(root).resolve("sounds");
        Path soundsJsonPath = namespaceRoot(root).resolve("sounds.json");

        JsonObject mergedDefinitions = readJsonObject(soundsJsonPath);
        for (String generatedId : readGeneratedSoundIds(root)) {
            mergedDefinitions.remove(generatedId);
        }

        Map<String, JsonObject> generatedDefinitions = new LinkedHashMap<>();
        LinkedHashSet<String> generatedIds = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(soundsRoot)) {
            List<Path> soundFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted()
                    .toList();

            for (Path soundFile : soundFiles) {
                GeneratedSoundDefinition definition = buildGeneratedSoundDefinition(soundsRoot, soundFile);
                if (definition == null) {
                    continue;
                }

                JsonObject event = generatedDefinitions.computeIfAbsent(definition.eventPath(), key -> new JsonObject());
                JsonArray sounds = event.has("sounds") && event.get("sounds").isJsonArray()
                        ? event.getAsJsonArray("sounds")
                        : new JsonArray();
                event.add("sounds", sounds);

                if (definition.subtitle() != null && !definition.subtitle().isBlank()) {
                    event.addProperty("subtitle", definition.subtitle().trim());
                }
                if (definition.replace() != null) {
                    event.addProperty("replace", definition.replace());
                }

                JsonObject entry = new JsonObject();
                entry.addProperty("name", GENERATED_NAMESPACE + ":" + definition.clipPath());
                if (definition.stream() != null) {
                    entry.addProperty("stream", definition.stream());
                }
                if (definition.preload() != null) {
                    entry.addProperty("preload", definition.preload());
                }
                if (definition.volume() != null) {
                    entry.addProperty("volume", definition.volume());
                }
                if (definition.pitch() != null) {
                    entry.addProperty("pitch", definition.pitch());
                }
                if (definition.weight() != null) {
                    entry.addProperty("weight", definition.weight());
                }
                if (definition.attenuationDistance() != null) {
                    entry.addProperty("attenuation_distance", definition.attenuationDistance());
                }
                sounds.add(entry);
                generatedIds.add(definition.eventPath());
            }
        }

        for (Map.Entry<String, JsonObject> entry : generatedDefinitions.entrySet()) {
            mergedDefinitions.add(entry.getKey(), entry.getValue());
        }

        writeAlways(soundsJsonPath, GSON.toJson(mergedDefinitions));
        writeAlways(generatedSoundsManifestPath(root), GSON.toJson(generatedIds));
    }

    private static GeneratedSoundDefinition buildGeneratedSoundDefinition(Path soundsRoot, Path soundFile) {
        String clipPath = normalizeGeneratedPath(stripExtension(soundsRoot.relativize(soundFile).toString()));
        if (clipPath == null) {
            Raidon.LOGGER.warn("[Raidon] Ignoring sound file with invalid path {}", soundFile);
            return null;
        }

        GeneratedSoundOptions options = readGeneratedSoundOptions(soundFile);
        String eventPath = normalizeGeneratedPath(options.id() == null || options.id().isBlank()
                ? clipPath
                : options.id());
        if (eventPath == null) {
            Raidon.LOGGER.warn("[Raidon] Ignoring sound file {} because it resolved to an invalid sound id", soundFile);
            return null;
        }

        return new GeneratedSoundDefinition(
                eventPath,
                clipPath,
                options.stream(),
                options.preload(),
                options.volume(),
                options.pitch(),
                options.weight(),
                options.attenuationDistance(),
                options.subtitle(),
                options.replace()
        );
    }

    private static GeneratedSoundOptions readGeneratedSoundOptions(Path soundFile) {
        Path sidecar = soundFile.resolveSibling(stripExtension(soundFile.getFileName().toString()) + ".sound.json");
        if (!Files.exists(sidecar)) {
            return GeneratedSoundOptions.EMPTY;
        }

        try (Reader reader = Files.newBufferedReader(sidecar)) {
            GeneratedSoundOptions parsed = GSON.fromJson(reader, GeneratedSoundOptions.class);
            return parsed == null ? GeneratedSoundOptions.EMPTY : parsed;
        } catch (IOException | JsonSyntaxException exception) {
            Raidon.LOGGER.warn("[Raidon] Failed to parse sound sidecar {}", sidecar, exception);
            return GeneratedSoundOptions.EMPTY;
        }
    }

    private static JsonObject readJsonObject(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new JsonObject();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject().deepCopy() : new JsonObject();
        } catch (JsonSyntaxException exception) {
            Raidon.LOGGER.warn("[Raidon] Failed to parse json object at {}", path, exception);
            return new JsonObject();
        }
    }

    private static Set<String> readGeneratedSoundIds(Path root) throws IOException {
        Path manifestPath = generatedSoundsManifestPath(root);
        if (!Files.exists(manifestPath)) {
            return Set.of();
        }

        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonArray()) {
                return Set.of();
            }

            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (JsonElement entry : parsed.getAsJsonArray()) {
                if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    String value = normalizeGeneratedPath(entry.getAsString());
                    if (value != null) {
                        ids.add(value);
                    }
                }
            }
            return Set.copyOf(ids);
        } catch (JsonSyntaxException exception) {
            Raidon.LOGGER.warn("[Raidon] Failed to parse generated sound manifest at {}", manifestPath, exception);
            return Set.of();
        }
    }

    private static Path generatedSoundsManifestPath(Path root) {
        return root.resolve(".raidon_generated_sounds.json");
    }

    private static String normalizeGeneratedPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().replace('\\', '/');
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9/_\\-.]", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("^[/._-]+|[/._-]+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
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

    private static void writeAlways(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path) && Files.readString(path).equals(content)) {
            return;
        }
        Files.writeString(path, content);
    }

    private static void syncGeneratedItemModels(Path root) throws IOException {
        Path legacyItemModelRoot = root
                .resolve("assets")
                .resolve(Raidon.MODID)
                .resolve("models")
                .resolve("item");
        deleteDirectory(legacyItemModelRoot);
        Files.createDirectories(legacyItemModelRoot);

        for (RaidSummonItemDefinition definition : RaidSummonItemConfigs.registeredDefinitions()) {
            Path modelPath = root
                    .resolve("assets")
                    .resolve(definition.id().getNamespace())
                    .resolve("models")
                    .resolve("item")
                    .resolve(definition.id().getPath() + ".json");

            writeAlways(modelPath, """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "%s"
                      }
                    }
                    """.formatted(definition.texture()));
        }
    }

    private static Path namespaceRoot(Path root) {
        return root.resolve("assets").resolve(GENERATED_NAMESPACE);
    }

    private static final class RaidonPlatformPaths {
        private RaidonPlatformPaths() {
        }

        private static Path rootPath() {
            return configRoot().resolve("raidon").resolve("resources");
        }

        private static Path configRoot() {
            return RaidPlatform.getConfigDir();
        }

        private static Path gameRoot() {
            return RaidPlatform.getGameDir();
        }
    }

    private static Path rootPath() {
        return RaidonPlatformPaths.rootPath();
    }

    private record GeneratedSoundDefinition(
            String eventPath,
            String clipPath,
            Boolean stream,
            Boolean preload,
            Float volume,
            Float pitch,
            Integer weight,
            Integer attenuationDistance,
            String subtitle,
            Boolean replace
    ) {
    }

    private record GeneratedSoundOptions(
            @SerializedName(value = "id", alternate = {"event", "sound_id", "soundId"}) String id,
            @SerializedName("stream") Boolean stream,
            @SerializedName("preload") Boolean preload,
            @SerializedName("volume") Float volume,
            @SerializedName("pitch") Float pitch,
            @SerializedName("weight") Integer weight,
            @SerializedName(value = "attenuation_distance", alternate = {"attenuationDistance"}) Integer attenuationDistance,
            @SerializedName("subtitle") String subtitle,
            @SerializedName("replace") Boolean replace
    ) {
        private static final GeneratedSoundOptions EMPTY = new GeneratedSoundOptions(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
