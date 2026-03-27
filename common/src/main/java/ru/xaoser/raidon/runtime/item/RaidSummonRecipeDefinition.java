package ru.xaoser.raidon.runtime.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public record RaidSummonRecipeDefinition(
        String name,
        RaidSummonRecipeType type,
        String group,
        String category,
        int count,
        List<String> pattern,
        Map<String, RaidRecipeIngredient> key,
        List<RaidRecipeIngredient> ingredients,
        RaidRecipeIngredient ingredient,
        RaidRecipeIngredient template,
        RaidRecipeIngredient base,
        RaidRecipeIngredient addition,
        Float experience,
        Integer cookingTime
) {
    public RaidSummonRecipeDefinition {
        count = Math.max(1, count);
        pattern = pattern == null ? List.of() : List.copyOf(pattern);
        key = key == null ? Map.of() : Map.copyOf(key);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        group = normalized(group);
        category = normalized(category);
        name = normalized(name);
    }

    public JsonObject toJson(ResourceLocation definitionId) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type.serializerId().toString());
        if (group != null) {
            json.addProperty("group", group);
        }

        switch (type) {
            case CRAFTING_SHAPED -> addShaped(json, definitionId);
            case CRAFTING_SHAPELESS -> addShapeless(json, definitionId);
            case SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING -> addCooking(json, definitionId);
            case STONECUTTING -> addStonecutting(json, definitionId);
            case SMITHING_TRANSFORM -> addSmithingTransform(json, definitionId);
        }
        return json;
    }

    public String fileName(ResourceLocation itemId, int index) {
        if (name != null) {
            return itemId.getPath() + "_" + name + ".json";
        }
        return itemId.getPath() + "_" + type.name().toLowerCase(java.util.Locale.ROOT) + "_" + (index + 1) + ".json";
    }

    private void addShaped(JsonObject json, ResourceLocation definitionId) {
        addOptionalCategory(json, "misc");
        JsonArray patternArray = new JsonArray();
        for (String line : pattern) {
            patternArray.add(line);
        }
        json.add("pattern", patternArray);

        JsonObject keyObject = new JsonObject();
        for (Map.Entry<String, RaidRecipeIngredient> entry : key.entrySet()) {
            keyObject.add(entry.getKey(), entry.getValue().toJson());
        }
        json.add("key", keyObject);
        json.add("result", resultJson(definitionId));
    }

    private void addShapeless(JsonObject json, ResourceLocation definitionId) {
        addOptionalCategory(json, "misc");
        JsonArray ingredientsArray = new JsonArray();
        for (RaidRecipeIngredient entry : ingredients) {
            ingredientsArray.add(entry.toJson());
        }
        json.add("ingredients", ingredientsArray);
        json.add("result", resultJson(definitionId));
    }

    private void addCooking(JsonObject json, ResourceLocation definitionId) {
        addOptionalCategory(json, "misc");
        json.add("ingredient", ingredient.toJson());
        json.add("result", resultJson(definitionId));
        json.addProperty("experience", experience == null ? 0.0F : Math.max(0.0F, experience));
        json.addProperty("cookingtime", cookingTime == null ? type.defaultCookingTime() : Math.max(1, cookingTime));
    }

    private void addStonecutting(JsonObject json, ResourceLocation definitionId) {
        json.add("ingredient", ingredient.toJson());
        json.add("result", resultJson(definitionId));
    }

    private void addSmithingTransform(JsonObject json, ResourceLocation definitionId) {
        json.add("template", template.toJson());
        json.add("base", base.toJson());
        json.add("addition", addition.toJson());
        json.add("result", resultJson(definitionId));
    }

    private JsonElement resultJson(ResourceLocation definitionId) {
        ItemStack resultStack = RaidSummonItemStacks.createStack(
                RaidSummonItemConfigs.definition(definitionId).orElse(null),
                count
        );
        if (resultStack.isEmpty()) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("id", definitionId.toString());
            fallback.addProperty("count", count);
            return fallback;
        }
        return ItemStack.STRICT_CODEC.encodeStart(JsonOps.INSTANCE, resultStack).getOrThrow();
    }

    private void addOptionalCategory(JsonObject json, String fallback) {
        json.addProperty("category", category == null ? fallback : category);
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
