package ru.xaoser.raidon.runtime.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import org.slf4j.Logger;
import ru.xaoser.raidon.RaidPlatform;
import ru.xaoser.raidon.Raidon;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class RaidSummonItemConfigs {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    private static final String DEFAULT_FILE_NAME = "example_raid_summon.json";
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "item/goat_horn");
    private static final String DEFAULT_FILE = """
            {
              "id": "example_raid_horn",
              "raid": "raidon:example_raid",
              "display_name": "Example Raid Horn",
              "description": [
                "Starts the example raid at the clicked block or near the player."
              ],
              "usage": [
                "Right click on a block to center the raid there.",
                "Right click in air to start it from your current position."
              ],
              "tooltip": [
                "Recipes and creative tabs are configured from this file too."
              ],
              "texture": "minecraft:item/goat_horn",
              "creative_tab": "tools_and_utilities",
              "consume": false,
              "use_duration_ticks": 32,
              "use_animation": "toot_horn",
              "cooldown_ticks": 200,
              "max_stack_size": 1,
              "rarity": "rare",
              "glint": true,
              "recipes": [
                {
                  "name": "crafting",
                  "type": "crafting_shaped",
                  "category": "equipment",
                  "pattern": [
                    " EI",
                    " HE",
                    "I  "
                  ],
                  "key": {
                    "E": { "item": "minecraft:emerald" },
                    "H": { "item": "minecraft:goat_horn" },
                    "I": { "tag": "c:ingots/iron" }
                  }
                }
              ]
            }
            """;

    private static final Map<ResourceLocation, RaidSummonItemDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final LinkedHashSet<ResourceLocation> BOOTSTRAP_IDS = new LinkedHashSet<>();
    private static boolean bootstrapped;

    private RaidSummonItemConfigs() {
    }

    public static synchronized void bootstrap(Logger logger) {
        if (bootstrapped) {
            return;
        }
        loadInternal(logger);
        BOOTSTRAP_IDS.clear();
        BOOTSTRAP_IDS.addAll(DEFINITIONS.keySet());
        bootstrapped = true;
    }

    public static synchronized List<RaidSummonItemDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static synchronized List<RaidSummonItemDefinition> registeredDefinitions() {
        if (BOOTSTRAP_IDS.isEmpty()) {
            return definitions();
        }

        List<RaidSummonItemDefinition> values = new ArrayList<>();
        for (ResourceLocation id : BOOTSTRAP_IDS) {
            RaidSummonItemDefinition definition = DEFINITIONS.get(id);
            if (definition != null) {
                values.add(definition);
            }
        }
        return List.copyOf(values);
    }

    public static synchronized java.util.Optional<RaidSummonItemDefinition> definition(ResourceLocation id) {
        return java.util.Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static synchronized void reload(Logger logger) {
        loadInternal(logger);
        bootstrapped = true;
    }

    public static Path configDirectory() {
        return RaidPlatform.getConfigDir().resolve("raidon").resolve("items");
    }

    private static void loadInternal(Logger logger) {
        DEFINITIONS.clear();
        Path baseDir = configDirectory();

        try {
            Files.createDirectories(baseDir);
        } catch (IOException exception) {
            logger.error("[Raidon] Failed to create summon item config directory {}", baseDir, exception);
            return;
        }

        ensureDefaultConfig(baseDir, logger);

        try (Stream<Path> stream = Files.list(baseDir)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();

            for (Path file : files) {
                RaidSummonItemDefinition definition = parse(file, logger);
                if (definition == null) {
                    continue;
                }
                RaidSummonItemDefinition previous = DEFINITIONS.put(definition.id(), definition);
                if (previous != null) {
                    logger.warn("[Raidon] Replacing summon item config {} with {}", previous.id(), file.getFileName());
                }
            }
        } catch (IOException exception) {
            logger.error("[Raidon] Failed to read summon item configs from {}", baseDir, exception);
        }

        logger.info("[Raidon] Loaded {} summon item config(s) from {}", DEFINITIONS.size(), baseDir.toAbsolutePath());
        refreshClientResources();
    }

    private static void ensureDefaultConfig(Path baseDir, Logger logger) {
        Path defaultFile = baseDir.resolve(DEFAULT_FILE_NAME);
        if (Files.exists(defaultFile)) {
            return;
        }

        try {
            Files.writeString(defaultFile, DEFAULT_FILE);
            logger.info("[Raidon] Generated default summon item config at {}", defaultFile.toAbsolutePath());
        } catch (IOException exception) {
            logger.warn("[Raidon] Failed to write default summon item config {}", defaultFile, exception);
        }
    }

    private static RaidSummonItemDefinition parse(Path file, Logger logger) {
        try (Reader reader = Files.newBufferedReader(file)) {
            ItemFile model = GSON.fromJson(reader, ItemFile.class);
            if (model == null) {
                logger.warn("[Raidon] Ignoring empty summon item config {}", file.getFileName());
                return null;
            }

            String fallbackPath = sanitizePath(stripJsonExtension(file.getFileName().toString()));
            ResourceLocation id = parseItemId(model.id(), fallbackPath, logger, file);
            ResourceLocation raidId = parseRaidId(model.raid(), logger, file);
            if (raidId == null) {
                return null;
            }

            String displayName = model.display_name();
            if (displayName == null || displayName.isBlank()) {
                displayName = humanize(id.getPath());
            }

            List<String> description = parseTextLines(model.description(), logger, file, "description");
            List<String> usage = parseTextLines(model.usage(), logger, file, "usage");
            List<String> tooltip = parseTextLines(model.tooltip(), logger, file, "tooltip");

            ResourceLocation texture = parseTexture(model.texture(), logger, file);
            boolean consume = model.consume() != null && model.consume();
            int useDurationTicks = model.use_duration_ticks() == null ? 0 : model.use_duration_ticks();
            String useAnimation = model.use_animation() == null || model.use_animation().isBlank()
                    ? "none"
                    : model.use_animation();
            int cooldownTicks = model.cooldown_ticks() == null ? 20 : model.cooldown_ticks();
            int maxStackSize = model.max_stack_size() == null ? 1 : model.max_stack_size();
            String rarity = model.rarity() == null || model.rarity().isBlank() ? "rare" : model.rarity();
            boolean glint = model.glint() != null && model.glint();
            List<ResourceKey<CreativeModeTab>> creativeTabs = parseCreativeTabs(model.creative_tabs(), logger, file);
            List<RaidSummonRecipeDefinition> recipes = parseRecipes(model.recipes(), logger, file);

            return new RaidSummonItemDefinition(
                    id,
                    raidId,
                    displayName,
                    description,
                    usage,
                    tooltip,
                    texture,
                    consume,
                    useDurationTicks,
                    useAnimation,
                    cooldownTicks,
                    maxStackSize,
                    rarity,
                    glint,
                    creativeTabs,
                    recipes
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("[Raidon] Failed to parse summon item config {}", file.getFileName(), exception);
            return null;
        }
    }

    private static List<String> parseTextLines(JsonElement element, Logger logger, Path file, String key) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            for (String line : element.getAsString().split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return List.copyOf(lines);
        }

        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    logger.warn("[Raidon] Expected string lines in {} of {}", key, file.getFileName());
                    continue;
                }
                String line = entry.getAsString();
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return List.copyOf(lines);
        }

        logger.warn("[Raidon] Expected string or string array in {} of {}", key, file.getFileName());
        return List.of();
    }

    private static List<ResourceKey<CreativeModeTab>> parseCreativeTabs(JsonElement element, Logger logger, Path file) {
        if (element == null || element.isJsonNull()) {
            return List.of(RaidCreativeTabs.DEFAULT_TAB);
        }

        List<String> tokens = extractStrings(element);
        LinkedHashSet<ResourceKey<CreativeModeTab>> tabs = new LinkedHashSet<>();
        for (String token : tokens) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            if ("none".equals(normalized) || "hidden".equals(normalized)) {
                continue;
            }
            ResourceKey<CreativeModeTab> tab = RaidCreativeTabs.resolve(normalized);
            if (tab == null) {
                logger.warn("[Raidon] Unknown creative tab '{}' in {}", token, file.getFileName());
                continue;
            }
            tabs.add(tab);
        }

        if (tabs.isEmpty() && !tokens.isEmpty()) {
            return List.of();
        }
        if (tabs.isEmpty()) {
            return List.of(RaidCreativeTabs.DEFAULT_TAB);
        }
        return List.copyOf(tabs);
    }

    private static List<RaidSummonRecipeDefinition> parseRecipes(JsonElement element, Logger logger, Path file) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }

        List<RaidSummonRecipeDefinition> recipes = new ArrayList<>();
        if (element.isJsonObject()) {
            RaidSummonRecipeDefinition recipe = parseRecipe(element.getAsJsonObject(), logger, file, 0);
            if (recipe != null) {
                recipes.add(recipe);
            }
            return List.copyOf(recipes);
        }
        if (!element.isJsonArray()) {
            logger.warn("[Raidon] Expected 'recipes' to be an array or object in {}", file.getFileName());
            return List.of();
        }

        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                logger.warn("[Raidon] Recipe #{} in {} is not an object", index + 1, file.getFileName());
                index++;
                continue;
            }
            RaidSummonRecipeDefinition recipe = parseRecipe(entry.getAsJsonObject(), logger, file, index);
            if (recipe != null) {
                recipes.add(recipe);
            }
            index++;
        }
        return List.copyOf(recipes);
    }

    private static RaidSummonRecipeDefinition parseRecipe(JsonObject obj, Logger logger, Path file, int index) {
        RaidSummonRecipeType type = RaidSummonRecipeType.parse(getString(obj, "type"));
        if (type == null) {
            logger.warn("[Raidon] Recipe #{} in {} has unknown or missing type", index + 1, file.getFileName());
            return null;
        }

        String name = getStringAny(obj, "name", "id", "key");
        String group = getString(obj, "group");
        String category = getStringAny(obj, "category", "book_category", "recipe_category");
        int count = getInt(obj, 1, "count");
        List<String> pattern = parsePattern(obj.get("pattern"), logger, file, index);
        Map<String, RaidRecipeIngredient> key = parseKey(obj.get("key"), logger, file, index);
        List<RaidRecipeIngredient> ingredients = parseIngredientList(obj.get("ingredients"), logger, file, index);
        RaidRecipeIngredient ingredient = parseIngredient(obj.get("ingredient"), logger, file, "recipes[" + index + "].ingredient");
        RaidRecipeIngredient template = parseIngredient(obj.get("template"), logger, file, "recipes[" + index + "].template");
        RaidRecipeIngredient base = parseIngredient(obj.get("base"), logger, file, "recipes[" + index + "].base");
        RaidRecipeIngredient addition = parseIngredient(obj.get("addition"), logger, file, "recipes[" + index + "].addition");
        Float experience = getFloat(obj, "experience");
        Integer cookingTime = getIntegerAny(obj, "cooking_time", "cookingtime", "cook_time");

        if (!validateRecipe(type, pattern, key, ingredients, ingredient, template, base, addition, logger, file, index)) {
            return null;
        }

        String normalizedName = name == null || name.isBlank() ? null : sanitizePath(name);

        return new RaidSummonRecipeDefinition(
                normalizedName,
                type,
                group,
                category,
                count,
                pattern,
                key,
                ingredients,
                ingredient,
                template,
                base,
                addition,
                experience,
                cookingTime
        );
    }

    private static boolean validateRecipe(RaidSummonRecipeType type, List<String> pattern, Map<String, RaidRecipeIngredient> key,
                                          List<RaidRecipeIngredient> ingredients, RaidRecipeIngredient ingredient,
                                          RaidRecipeIngredient template, RaidRecipeIngredient base,
                                          RaidRecipeIngredient addition, Logger logger, Path file, int index) {
        return switch (type) {
            case CRAFTING_SHAPED -> {
                boolean valid = !pattern.isEmpty() && !key.isEmpty();
                if (!valid) {
                    logger.warn("[Raidon] Shaped recipe #{} in {} requires non-empty pattern and key", index + 1, file.getFileName());
                }
                yield valid;
            }
            case CRAFTING_SHAPELESS -> {
                boolean valid = !ingredients.isEmpty();
                if (!valid) {
                    logger.warn("[Raidon] Shapeless recipe #{} in {} requires ingredients", index + 1, file.getFileName());
                }
                yield valid;
            }
            case SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING, STONECUTTING -> {
                boolean valid = ingredient != null;
                if (!valid) {
                    logger.warn("[Raidon] Recipe #{} in {} requires ingredient", index + 1, file.getFileName());
                }
                yield valid;
            }
            case SMITHING_TRANSFORM -> {
                boolean valid = template != null && base != null && addition != null;
                if (!valid) {
                    logger.warn("[Raidon] Smithing recipe #{} in {} requires template, base and addition", index + 1, file.getFileName());
                }
                yield valid;
            }
        };
    }

    private static List<String> parsePattern(JsonElement element, Logger logger, Path file, int index) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            logger.warn("[Raidon] Recipe #{} in {} has non-array pattern", index + 1, file.getFileName());
            return List.of();
        }
        List<String> pattern = new ArrayList<>();
        for (JsonElement row : element.getAsJsonArray()) {
            if (row.isJsonPrimitive() && row.getAsJsonPrimitive().isString()) {
                String value = row.getAsString();
                if (!value.isBlank()) {
                    pattern.add(value);
                }
            }
        }
        return List.copyOf(pattern);
    }

    private static Map<String, RaidRecipeIngredient> parseKey(JsonElement element, Logger logger, Path file, int index) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return Map.of();
        }
        Map<String, RaidRecipeIngredient> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String symbol = entry.getKey();
            if (symbol == null || symbol.length() != 1 || " ".equals(symbol)) {
                logger.warn("[Raidon] Recipe #{} in {} has invalid key symbol '{}'", index + 1, file.getFileName(), symbol);
                continue;
            }
            RaidRecipeIngredient ingredient = parseIngredient(entry.getValue(), logger, file, "recipes[" + index + "].key." + symbol);
            if (ingredient != null) {
                parsed.put(symbol, ingredient);
            }
        }
        return Map.copyOf(parsed);
    }

    private static List<RaidRecipeIngredient> parseIngredientList(JsonElement element, Logger logger, Path file, int index) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            logger.warn("[Raidon] Recipe #{} in {} has non-array ingredients", index + 1, file.getFileName());
            return List.of();
        }
        List<RaidRecipeIngredient> parsed = new ArrayList<>();
        int ingredientIndex = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            RaidRecipeIngredient ingredient = parseIngredient(entry, logger, file, "recipes[" + index + "].ingredients[" + ingredientIndex + "]");
            if (ingredient != null) {
                parsed.add(ingredient);
            }
            ingredientIndex++;
        }
        return List.copyOf(parsed);
    }

    private static RaidRecipeIngredient parseIngredient(JsonElement element, Logger logger, Path file, String key) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString().trim();
            if (raw.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(raw.substring(1));
                if (tag == null) {
                    logger.warn("[Raidon] Invalid tag ingredient '{}' in {}", raw, file.getFileName());
                    return null;
                }
                return new RaidRecipeIngredient(null, tag);
            }
            ResourceLocation item = ResourceLocation.tryParse(raw);
            if (item == null) {
                logger.warn("[Raidon] Invalid item ingredient '{}' in {}", raw, file.getFileName());
                return null;
            }
            return new RaidRecipeIngredient(item, null);
        }
        if (!element.isJsonObject()) {
            logger.warn("[Raidon] Invalid ingredient format for {} in {}", key, file.getFileName());
            return null;
        }

        JsonObject obj = element.getAsJsonObject();
        ResourceLocation item = parseOptionalResource(getStringAny(obj, "item", "id"));
        ResourceLocation tag = parseOptionalResource(getString(obj, "tag"));
        if (item == null && tag == null) {
            logger.warn("[Raidon] Ingredient {} in {} must define item/id or tag", key, file.getFileName());
            return null;
        }
        if (item != null && tag != null) {
            logger.warn("[Raidon] Ingredient {} in {} cannot define both item and tag", key, file.getFileName());
            return null;
        }
        return new RaidRecipeIngredient(item, tag);
    }

    private static ResourceLocation parseOptionalResource(String raw) {
        return raw == null || raw.isBlank() ? null : ResourceLocation.tryParse(raw.trim());
    }

    private static ResourceLocation parseItemId(String raw, String fallbackPath, Logger logger, Path file) {
        String candidate = raw == null || raw.isBlank()
                ? Raidon.MODID + ":" + fallbackPath
                : raw.contains(":") ? raw.trim() : Raidon.MODID + ":" + raw.trim();

        ResourceLocation parsed = ResourceLocation.tryParse(candidate);
        if (parsed == null) {
            logger.warn("[Raidon] Invalid summon item id '{}' in {}, falling back to {}:{}",
                    raw, file.getFileName(), Raidon.MODID, fallbackPath);
            return ResourceLocation.fromNamespaceAndPath(Raidon.MODID, fallbackPath);
        }

        if (!Raidon.MODID.equals(parsed.getNamespace())) {
            logger.warn("[Raidon] Summon item '{}' in {} uses unsupported namespace '{}'; it will be registered as '{}:{}'",
                    parsed, file.getFileName(), parsed.getNamespace(), Raidon.MODID, parsed.getPath());
            return ResourceLocation.fromNamespaceAndPath(Raidon.MODID, parsed.getPath());
        }

        return parsed;
    }

    private static ResourceLocation parseRaidId(String raw, Logger logger, Path file) {
        ResourceLocation parsed = raw == null ? null : ResourceLocation.tryParse(raw.trim());
        if (parsed == null) {
            logger.warn("[Raidon] Summon item config {} has invalid or missing raid id '{}'", file.getFileName(), raw);
        }
        return parsed;
    }

    private static ResourceLocation parseTexture(String raw, Logger logger, Path file) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TEXTURE;
        }

        String trimmed = raw.trim();
        ResourceLocation parsed = trimmed.contains(":")
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.fromNamespaceAndPath("raidon_cfg", normalizeTexturePath(trimmed));

        if (parsed == null) {
            logger.warn("[Raidon] Invalid summon item texture '{}' in {}, falling back to {}", raw, file.getFileName(), DEFAULT_TEXTURE);
            return DEFAULT_TEXTURE;
        }

        return parsed;
    }

    private static String normalizeTexturePath(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("item/")) {
            return normalized;
        }
        return "item/" + normalized;
    }

    private static List<String> extractStrings(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return List.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    values.add(entry.getAsString());
                }
            }
            return List.copyOf(values);
        }
        return List.of();
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString();
    }

    private static String getStringAny(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = getString(object, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int getInt(JsonObject object, int fallback, String key) {
        if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return object.get(key).getAsInt();
    }

    private static Float getFloat(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        return object.get(key).getAsFloat();
    }

    private static Integer getIntegerAny(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object != null && key != null && object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsInt();
            }
        }
        return null;
    }

    private static String stripJsonExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private static String sanitizePath(String value) {
        if (value == null || value.isBlank()) {
            return "entry";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String sanitized = lower.replaceAll("[^a-z0-9/_\\-.]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("/+", "/");
        sanitized = sanitized.replaceAll("^[/._-]+|[/._-]+$", "");
        return sanitized.isBlank() ? "entry" : sanitized;
    }

    private static String humanize(String path) {
        String[] parts = path.replace('/', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "Raid Summon Item" : builder.toString();
    }

    private static void refreshClientResources() {
        invokeStatic("ru.xaoser.raidon.runtime.client.RaidClientResources", "ensureLayout");
        invokeStatic("ru.xaoser.raidon.fabric.RaidonFabricResources", "prepare");
    }

    private static void invokeStatic(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            type.getMethod(methodName).invoke(null);
        } catch (Throwable ignored) {
            // Dedicated servers and non-client loaders do not expose these classes.
        }
    }

    private record ItemFile(
            @SerializedName("id") String id,
            @SerializedName(value = "raid", alternate = {"raid_id", "raidId"}) String raid,
            @SerializedName(value = "display_name", alternate = {"displayName", "name"}) String display_name,
            @SerializedName(value = "tooltip", alternate = {"lore"}) JsonElement tooltip,
            @SerializedName(value = "description", alternate = {"desc"}) JsonElement description,
            @SerializedName(value = "usage", alternate = {"use", "used", "how_to_use", "howToUse"}) JsonElement usage,
            @SerializedName(value = "texture", alternate = {"icon"}) String texture,
            @SerializedName(value = "creative_tabs", alternate = {"creative_tab", "tab", "tabs", "item_group", "itemGroup"}) JsonElement creative_tabs,
            @SerializedName(value = "recipes", alternate = {"crafting", "recipe"}) JsonElement recipes,
            @SerializedName(value = "consume", alternate = {"consume_on_use", "consumeOnUse"}) Boolean consume,
            @SerializedName(value = "use_duration_ticks", alternate = {"useDurationTicks", "use_duration", "use_time_ticks", "charge_time_ticks", "charge_ticks"}) Integer use_duration_ticks,
            @SerializedName(value = "use_animation", alternate = {"useAnimation", "animation", "use_anim", "item_use_animation"}) String use_animation,
            @SerializedName(value = "cooldown_ticks", alternate = {"cooldownTicks", "cooldown"}) Integer cooldown_ticks,
            @SerializedName(value = "max_stack_size", alternate = {"maxStackSize", "stack_size"}) Integer max_stack_size,
            @SerializedName("rarity") String rarity,
            @SerializedName(value = "glint", alternate = {"foil"}) Boolean glint
    ) {
    }
}
