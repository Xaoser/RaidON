package ru.xaoser.raidon.runtime.item;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

public record RaidRecipeIngredient(ResourceLocation item, ResourceLocation tag) {
    public RaidRecipeIngredient {
        if (item == null && tag == null) {
            throw new IllegalArgumentException("Recipe ingredient must define item or tag");
        }
        if (item != null && tag != null) {
            throw new IllegalArgumentException("Recipe ingredient cannot define both item and tag");
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (item != null) {
            json.addProperty("item", item.toString());
        }
        if (tag != null) {
            json.addProperty("tag", tag.toString());
        }
        return json;
    }
}
