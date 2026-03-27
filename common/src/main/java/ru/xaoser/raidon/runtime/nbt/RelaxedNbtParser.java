package ru.xaoser.raidon.runtime.nbt;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RelaxedNbtParser {
    private static final Gson GSON = new Gson();

    private RelaxedNbtParser() {}

    public static Tag parseTag(String raw) throws CommandSyntaxException {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw syntax("NBT value is blank");
        }
        try {
            return parseExactString(value);
        } catch (CommandSyntaxException exception) {
            if (value.startsWith("{")) {
                String normalized = new RelaxedCompoundNormalizer(value).normalize();
                if (!normalized.equals(value)) {
                    return parseExactString(normalized);
                }
            }
            throw exception;
        }
    }

    public static Tag parseTag(JsonElement element) throws CommandSyntaxException {
        return toTag(element, NbtPath.root());
    }

    public static CompoundTag parseCompound(String raw) throws CommandSyntaxException {
        Tag tag = parseTag(raw);
        if (tag instanceof CompoundTag compoundTag) {
            return compoundTag;
        }
        throw syntax("Expected compound NBT");
    }

    public static CompoundTag parseStrictCompound(String raw) throws CommandSyntaxException {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw syntax("NBT value is blank");
        }
        Tag tag = TagParser.parseTag(value);
        if (tag instanceof CompoundTag compoundTag) {
            return compoundTag;
        }
        throw syntax("Expected compound NBT");
    }

    public static CompoundTag parseCompound(JsonElement element) throws CommandSyntaxException {
        Tag tag = parseTag(element);
        if (tag instanceof CompoundTag compoundTag) {
            return compoundTag;
        }
        throw syntax("Expected compound NBT object");
    }

    public static String normalizeCompoundString(String raw) throws CommandSyntaxException {
        return parseCompound(raw).toString();
    }

    public static String normalizeStrictCompoundString(String raw) throws CommandSyntaxException {
        return parseStrictCompound(raw).toString();
    }

    public static String normalizeCompoundString(JsonElement element) throws CommandSyntaxException {
        return parseCompound(element).toString();
    }

    private static Tag parseExactString(String value) throws CommandSyntaxException {
        if (value.startsWith("{")) {
            return TagParser.parseTag(value);
        }
        CompoundTag wrapper = TagParser.parseTag("{value:" + value + "}");
        Tag tag = wrapper.get("value");
        if (tag == null) {
            throw syntax("Invalid NBT value");
        }
        return tag;
    }

    private static Tag toTag(JsonElement element, NbtPath path) throws CommandSyntaxException {
        if (element == null || element.isJsonNull()) {
            throw syntax("Null is not a valid NBT value at " + path.render());
        }
        if (path.isTextComponentKey()) {
            return StringTag.valueOf(toTextComponentJson(element));
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.size() == 1 && object.has("$snbt")) {
                JsonElement snbt = object.get("$snbt");
                if (snbt == null || snbt.isJsonNull() || !snbt.isJsonPrimitive()) {
                    throw syntax("Invalid $snbt wrapper at " + path.render());
                }
                return parseTag(snbt.getAsString());
            }
            CompoundTag tag = new CompoundTag();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                tag.put(entry.getKey(), toTag(entry.getValue(), path.child(entry.getKey())));
            }
            return tag;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (path.isTextComponentListKey()) {
                return toTextComponentList(array);
            }
            ListTag list = new ListTag();
            int listType = -1;
            for (int i = 0; i < array.size(); i++) {
                Tag child = toTag(array.get(i), path.index(i));
                int childType = child.getId();
                if (listType == -1) {
                    listType = childType;
                } else if (listType != childType) {
                    throw syntax("NBT list at " + path.render() + " has mixed tag types");
                }
                list.add(child);
            }
            return list;
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return ByteTag.valueOf((byte) (primitive.getAsBoolean() ? 1 : 0));
        }
        if (primitive.isNumber()) {
            return parseNumberTag(primitive.getAsString(), path);
        }
        if (primitive.isString()) {
            String value = primitive.getAsString();
            try {
                return parseTag(value);
            } catch (CommandSyntaxException ignored) {
                return StringTag.valueOf(value);
            }
        }
        throw syntax("Unsupported JSON value at " + path.render());
    }

    private static Tag parseNumberTag(String raw, NbtPath path) throws CommandSyntaxException {
        try {
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return DoubleTag.valueOf(Double.parseDouble(raw));
            }
            try {
                return IntTag.valueOf(Integer.parseInt(raw));
            } catch (NumberFormatException ignored) {
                return LongTag.valueOf(Long.parseLong(raw));
            }
        } catch (NumberFormatException exception) {
            throw syntax("Invalid number '" + raw + "' at " + path.render());
        }
    }

    private static ListTag toTextComponentList(JsonArray array) {
        ListTag list = new ListTag();
        for (JsonElement element : array) {
            list.add(StringTag.valueOf(toTextComponentJson(element)));
        }
        return list;
    }

    private static String toTextComponentJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return GSON.toJson(plainTextComponent(""));
        }
        if (element.isJsonObject() || element.isJsonArray()) {
            return GSON.toJson(element);
        }
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                String value = primitive.getAsString();
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\""))) {
                    try {
                        JsonParser.parseString(trimmed);
                        return trimmed;
                    } catch (JsonParseException ignored) {
                    }
                }
                return GSON.toJson(plainTextComponent(value));
            }
            return GSON.toJson(plainTextComponent(primitive.getAsString()));
        }
        return GSON.toJson(plainTextComponent(element.toString()));
    }

    private static JsonObject plainTextComponent(String value) {
        JsonObject object = new JsonObject();
        object.addProperty("text", value);
        return object;
    }

    private static CommandSyntaxException syntax(String message) {
        return new SimpleCommandExceptionType(new LiteralMessage(message)).create();
    }

    private record NbtPath(List<String> segments) {
        static NbtPath root() {
            return new NbtPath(List.of());
        }

        NbtPath child(String key) {
            List<String> next = new ArrayList<>(segments);
            next.add(key);
            return new NbtPath(List.copyOf(next));
        }

        NbtPath index(int index) {
            return this;
        }

        boolean isTextComponentKey() {
            return endsWith("CustomName") || endsWith("display", "Name");
        }

        boolean isTextComponentListKey() {
            return endsWith("display", "Lore");
        }

        boolean endsWith(String... keys) {
            if (segments.size() < keys.length) {
                return false;
            }
            int offset = segments.size() - keys.length;
            for (int i = 0; i < keys.length; i++) {
                if (!segments.get(offset + i).equals(keys[i])) {
                    return false;
                }
            }
            return true;
        }

        String render() {
            return segments.isEmpty() ? "<root>" : String.join(".", segments);
        }
    }

    private static final class RelaxedCompoundNormalizer {
        private final String input;
        private int index;

        private RelaxedCompoundNormalizer(String input) {
            this.input = input;
        }

        private String normalize() throws CommandSyntaxException {
            skipWhitespace();
            String value = parseValue(',', '\0');
            skipWhitespace();
            if (index != input.length()) {
                throw syntax("Unexpected trailing data in relaxed NBT");
            }
            return value;
        }

        private String parseValue(char delimiter, char fallbackDelimiter) throws CommandSyntaxException {
            skipWhitespace();
            if (index >= input.length()) {
                throw syntax("Unexpected end of relaxed NBT");
            }
            char current = input.charAt(index);
            if (current == '{') {
                return parseCompound();
            }
            if (current == '[') {
                return parseList();
            }
            if (current == '"' || current == '\'') {
                return readQuoted();
            }
            return normalizeBareToken(readBareToken(delimiter, fallbackDelimiter));
        }

        private String parseCompound() throws CommandSyntaxException {
            expect('{');
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            skipWhitespace();
            boolean first = true;
            while (!peek('}')) {
                if (!first) {
                    expect(',');
                    builder.append(',');
                    skipWhitespace();
                }
                builder.append(readKey());
                skipWhitespace();
                expect(':');
                builder.append(':');
                builder.append(parseValue(',', '}'));
                skipWhitespace();
                first = false;
            }
            expect('}');
            builder.append('}');
            return builder.toString();
        }

        private String parseList() throws CommandSyntaxException {
            if (isTypedArray()) {
                return readRawStructure('[', ']');
            }
            expect('[');
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            skipWhitespace();
            boolean first = true;
            while (!peek(']')) {
                if (!first) {
                    expect(',');
                    builder.append(',');
                    skipWhitespace();
                }
                builder.append(parseValue(',', ']'));
                skipWhitespace();
                first = false;
            }
            expect(']');
            builder.append(']');
            return builder.toString();
        }

        private String readKey() throws CommandSyntaxException {
            skipWhitespace();
            if (index >= input.length()) {
                throw syntax("Expected key");
            }
            char current = input.charAt(index);
            if (current == '"' || current == '\'') {
                return readQuoted();
            }
            int start = index;
            while (index < input.length()) {
                char ch = input.charAt(index);
                if (ch == ':' || Character.isWhitespace(ch)) {
                    break;
                }
                index++;
            }
            String key = input.substring(start, index).trim();
            if (key.isEmpty()) {
                throw syntax("Expected key");
            }
            return key;
        }

        private String readBareToken(char delimiter, char fallbackDelimiter) {
            int start = index;
            while (index < input.length()) {
                char ch = input.charAt(index);
                if (ch == delimiter || ch == fallbackDelimiter) {
                    break;
                }
                index++;
            }
            return input.substring(start, index).trim();
        }

        private String normalizeBareToken(String token) throws CommandSyntaxException {
            if (token.isEmpty()) {
                return "\"\"";
            }
            try {
                parseExactString(token);
                return token;
            } catch (CommandSyntaxException ignored) {
                return '"' + escapeString(token) + '"';
            }
        }

        private String readQuoted() throws CommandSyntaxException {
            char quote = input.charAt(index++);
            StringBuilder builder = new StringBuilder();
            builder.append(quote);
            boolean escaped = false;
            while (index < input.length()) {
                char ch = input.charAt(index++);
                builder.append(ch);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == quote) {
                    return builder.toString();
                }
            }
            throw syntax("Unclosed quoted string in relaxed NBT");
        }

        private String readRawStructure(char open, char close) throws CommandSyntaxException {
            int start = index;
            int depth = 0;
            boolean escaped = false;
            char quote = 0;
            while (index < input.length()) {
                char ch = input.charAt(index++);
                if (quote != 0) {
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == quote) {
                        quote = 0;
                    }
                    continue;
                }
                if (ch == '"' || ch == '\'') {
                    quote = ch;
                    continue;
                }
                if (ch == open) {
                    depth++;
                    continue;
                }
                if (ch == close) {
                    depth--;
                    if (depth == 0) {
                        return input.substring(start, index);
                    }
                }
            }
            throw syntax("Unclosed structure in relaxed NBT");
        }

        private boolean isTypedArray() {
            return index + 2 < input.length()
                    && input.charAt(index) == '['
                    && (input.charAt(index + 1) == 'B' || input.charAt(index + 1) == 'I' || input.charAt(index + 1) == 'L')
                    && input.charAt(index + 2) == ';';
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            skipWhitespace();
            return index < input.length() && input.charAt(index) == expected;
        }

        private void expect(char expected) throws CommandSyntaxException {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != expected) {
                throw syntax("Expected '" + expected + "' in relaxed NBT");
            }
            index++;
        }

        private String escapeString(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
