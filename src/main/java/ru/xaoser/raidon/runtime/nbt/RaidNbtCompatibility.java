package ru.xaoser.raidon.runtime.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RaidNbtCompatibility {
    private RaidNbtCompatibility() {
    }

    public static CompoundTag normalizeEntityData(CompoundTag root) {
        if (root == null) {
            return new CompoundTag();
        }
        normalizeCompound(root);
        return root;
    }

    private static void normalizeCompound(CompoundTag compound) {
        if (compound.contains("attributes", Tag.TAG_LIST) && !compound.contains("Attributes", Tag.TAG_LIST)) {
            Tag attributes = compound.get("attributes");
            if (attributes != null) {
                compound.put("Attributes", attributes.copy());
            }
            compound.remove("attributes");
        }

        if (looksLikeItemStack(compound)) {
            normalizeItemStack(compound);
        }

        for (String key : List.copyOf(compound.getAllKeys())) {
            Tag value = compound.get(key);
            if (value instanceof CompoundTag child) {
                normalizeCompound(child);
            } else if (value instanceof ListTag list) {
                normalizeList(list);
            }
        }
    }

    private static void normalizeList(ListTag list) {
        for (int index = 0; index < list.size(); index++) {
            Tag value = list.get(index);
            if (value instanceof CompoundTag compound) {
                normalizeCompound(compound);
            } else if (value instanceof ListTag nested) {
                normalizeList(nested);
            }
        }
    }

    private static boolean looksLikeItemStack(CompoundTag compound) {
        return compound.contains("id", Tag.TAG_STRING)
                && (compound.contains("Count", Tag.TAG_ANY_NUMERIC)
                || compound.contains("count", Tag.TAG_ANY_NUMERIC)
                || compound.contains("components", Tag.TAG_COMPOUND)
                || compound.contains("tag", Tag.TAG_COMPOUND));
    }

    private static void normalizeItemStack(CompoundTag stack) {
        if (stack.contains("count", Tag.TAG_ANY_NUMERIC) && !stack.contains("Count", Tag.TAG_ANY_NUMERIC)) {
            int count = Math.max(0, Math.min(127, stack.getInt("count")));
            stack.putByte("Count", (byte) count);
            stack.remove("count");
        }

        if (stack.contains("components", Tag.TAG_COMPOUND)) {
            CompoundTag components = stack.getCompound("components");
            CompoundTag itemTag = stack.contains("tag", Tag.TAG_COMPOUND) ? stack.getCompound("tag").copy() : new CompoundTag();
            translateComponents(components, itemTag);
            if (!itemTag.isEmpty()) {
                stack.put("tag", itemTag);
            }
            stack.remove("components");
        }
    }

    private static void translateComponents(CompoundTag components, CompoundTag itemTag) {
        if (components.contains("minecraft:enchantments", Tag.TAG_COMPOUND)) {
            itemTag.put("Enchantments", translateEnchantments(components.getCompound("minecraft:enchantments")));
        }
        if (components.contains("minecraft:custom_name")) {
            applyCustomName(itemTag, components.get("minecraft:custom_name"));
        }
        if (components.contains("minecraft:attribute_modifiers", Tag.TAG_COMPOUND)) {
            ListTag modifiers = translateAttributeModifiers(components.getCompound("minecraft:attribute_modifiers"));
            if (!modifiers.isEmpty()) {
                itemTag.put("AttributeModifiers", modifiers);
            }
        }
        if (components.contains("minecraft:trim", Tag.TAG_COMPOUND)) {
            itemTag.put("Trim", components.getCompound("minecraft:trim").copy());
        }
        if (components.contains("minecraft:dyed_color", Tag.TAG_COMPOUND)) {
            CompoundTag dyedColor = components.getCompound("minecraft:dyed_color");
            if (dyedColor.contains("rgb", Tag.TAG_ANY_NUMERIC)) {
                CompoundTag display = getOrCreateDisplay(itemTag);
                display.putInt("color", dyedColor.getInt("rgb"));
                itemTag.put("display", display);
            }
        }
        if (components.contains("minecraft:profile", Tag.TAG_COMPOUND)) {
            CompoundTag skullOwner = translateProfile(components.getCompound("minecraft:profile"));
            if (!skullOwner.isEmpty()) {
                itemTag.put("SkullOwner", skullOwner);
            }
        }
    }

    private static ListTag translateEnchantments(CompoundTag component) {
        ListTag enchantments = new ListTag();
        if (!component.contains("levels", Tag.TAG_COMPOUND)) {
            return enchantments;
        }
        CompoundTag levels = component.getCompound("levels");
        for (String key : levels.getAllKeys()) {
            if (!levels.contains(key, Tag.TAG_ANY_NUMERIC)) {
                continue;
            }
            CompoundTag enchantment = new CompoundTag();
            enchantment.putString("id", key);
            enchantment.putShort("lvl", (short) levels.getInt(key));
            enchantments.add(enchantment);
        }
        return enchantments;
    }

    private static void applyCustomName(CompoundTag itemTag, Tag rawName) {
        if (rawName == null) {
            return;
        }
        CompoundTag display = getOrCreateDisplay(itemTag);
        display.putString("Name", rawName.getAsString());
        itemTag.put("display", display);
    }

    private static ListTag translateAttributeModifiers(CompoundTag component) {
        ListTag modifiers = new ListTag();
        if (!component.contains("modifiers", Tag.TAG_LIST)) {
            return modifiers;
        }
        ListTag rawModifiers = component.getList("modifiers", Tag.TAG_COMPOUND);
        for (int index = 0; index < rawModifiers.size(); index++) {
            CompoundTag raw = rawModifiers.getCompound(index);
            String attributeName = firstNonBlank(readString(raw, "type"), readString(raw, "attribute"));
            if (attributeName == null) {
                continue;
            }
            CompoundTag modifier = new CompoundTag();
            modifier.putString("AttributeName", attributeName);
            modifier.putString("Name", attributeName);
            if (raw.contains("amount", Tag.TAG_ANY_NUMERIC)) {
                modifier.putDouble("Amount", raw.getDouble("amount"));
            }
            modifier.putInt("Operation", parseOperation(readString(raw, "operation")));
            String slot = normalizeSlot(readString(raw, "slot"));
            if (slot != null) {
                modifier.putString("Slot", slot);
            }
            modifier.putUUID("UUID", toStableUuid(readString(raw, "id"), attributeName, index));
            modifiers.add(modifier);
        }
        return modifiers;
    }

    private static CompoundTag translateProfile(CompoundTag profile) {
        CompoundTag skullOwner = new CompoundTag();
        String name = readString(profile, "name");
        if (name != null) {
            skullOwner.putString("Name", name);
        }
        String id = readString(profile, "id");
        if (id != null) {
            try {
                skullOwner.putUUID("Id", UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (profile.contains("properties", Tag.TAG_LIST)) {
            CompoundTag properties = new CompoundTag();
            Map<String, ListTag> grouped = new LinkedHashMap<>();
            ListTag source = profile.getList("properties", Tag.TAG_COMPOUND);
            for (int index = 0; index < source.size(); index++) {
                CompoundTag property = source.getCompound(index);
                String propertyName = readString(property, "name");
                String value = readString(property, "value");
                if (propertyName == null || value == null) {
                    continue;
                }
                ListTag values = grouped.computeIfAbsent(propertyName, key -> new ListTag());
                CompoundTag entry = new CompoundTag();
                entry.putString("Value", value);
                String signature = readString(property, "signature");
                if (signature != null) {
                    entry.putString("Signature", signature);
                }
                values.add(entry);
            }
            for (Map.Entry<String, ListTag> entry : grouped.entrySet()) {
                properties.put(entry.getKey(), entry.getValue());
            }
            if (!properties.isEmpty()) {
                skullOwner.put("Properties", properties);
            }
        }

        return skullOwner;
    }

    private static CompoundTag getOrCreateDisplay(CompoundTag itemTag) {
        return itemTag.contains("display", Tag.TAG_COMPOUND) ? itemTag.getCompound("display").copy() : new CompoundTag();
    }

    private static int parseOperation(String raw) {
        if (raw == null) {
            return 0;
        }
        return switch (raw.trim().toLowerCase()) {
            case "add_multiplied_base", "multiply_base" -> 1;
            case "add_multiplied_total", "multiply_total" -> 2;
            default -> 0;
        };
    }

    private static String normalizeSlot(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "hand", "main_hand", "mainhand" -> "mainhand";
            case "off_hand", "offhand" -> "offhand";
            default -> raw.trim().toLowerCase();
        };
    }

    private static UUID toStableUuid(String id, String attributeName, int index) {
        String basis = firstNonBlank(id, attributeName) + "#" + index;
        return UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));
    }

    private static String readString(CompoundTag tag, String key) {
        if (tag == null || key == null || !tag.contains(key)) {
            return null;
        }
        Tag value = tag.get(key);
        if (value == null) {
            return null;
        }
        String result = value.getAsString();
        return result == null || result.isBlank() ? null : result;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
