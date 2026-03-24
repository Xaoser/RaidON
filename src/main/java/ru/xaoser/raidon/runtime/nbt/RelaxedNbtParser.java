package ru.xaoser.raidon.runtime.nbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.util.regex.Pattern;

public final class RelaxedNbtParser {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?[bBsSlLfFdD]?$");
    private static final Pattern BARE_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._+-]+$");

    private RelaxedNbtParser() {}

    public static CompoundTag parseCompound(String raw) throws CommandSyntaxException {
        return TagParser.parseTag(normalizeCompoundString(raw));
    }

    public static String normalizeCompoundString(String raw) throws CommandSyntaxException {
        String value = raw == null ? "" : raw.trim();
        try {
            TagParser.parseTag(value);
            return value;
        } catch (CommandSyntaxException first) {
            String normalized = new Parser(value).parse();
            TagParser.parseTag(normalized);
            return normalized;
        }
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private String parse() {
            String value = parseValue();
            skipWhitespace();
            if (!isEnd()) {
                throw new IllegalArgumentException("Unexpected trailing data at index " + index);
            }
            return value;
        }

        private String parseValue() {
            skipWhitespace();
            if (isEnd()) {
                throw new IllegalArgumentException("Expected value");
            }
            char current = peek();
            if (current == '{') {
                return parseCompound();
            }
            if (current == '[') {
                return parseList();
            }
            if (current == '"' || current == '\'') {
                return readQuoted();
            }
            return normalizeScalar(readScalarToken());
        }

        private String parseCompound() {
            expect('{');
            StringBuilder output = new StringBuilder();
            output.append('{');
            skipWhitespace();
            boolean first = true;
            while (!isEnd() && peek() != '}') {
                if (!first) {
                    expect(',');
                    output.append(',');
                    skipWhitespace();
                }
                output.append(parseKey());
                skipWhitespace();
                expect(':');
                output.append(':');
                output.append(parseValue());
                skipWhitespace();
                first = false;
            }
            expect('}');
            output.append('}');
            return output.toString();
        }

        private String parseList() {
            expect('[');
            StringBuilder output = new StringBuilder();
            output.append('[');
            skipWhitespace();

            if (isTypedArrayPrefix()) {
                char type = Character.toUpperCase(read());
                skipWhitespace();
                expect(';');
                output.append(type).append(';');
                skipWhitespace();
                boolean first = true;
                while (!isEnd() && peek() != ']') {
                    if (!first) {
                        expect(',');
                        output.append(',');
                        skipWhitespace();
                    }
                    output.append(normalizeScalar(readScalarToken()));
                    skipWhitespace();
                    first = false;
                }
                expect(']');
                output.append(']');
                return output.toString();
            }

            boolean first = true;
            while (!isEnd() && peek() != ']') {
                if (!first) {
                    expect(',');
                    output.append(',');
                    skipWhitespace();
                }
                output.append(parseValue());
                skipWhitespace();
                first = false;
            }
            expect(']');
            output.append(']');
            return output.toString();
        }

        private String parseKey() {
            skipWhitespace();
            if (isEnd()) {
                throw new IllegalArgumentException("Expected key");
            }
            char current = peek();
            if (current == '"' || current == '\'') {
                return readQuoted();
            }
            int start = index;
            while (!isEnd() && peek() != ':') {
                index++;
            }
            if (isEnd()) {
                throw new IllegalArgumentException("Expected ':' after key");
            }
            String key = input.substring(start, index).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Empty key");
            }
            return BARE_KEY_PATTERN.matcher(key).matches() ? key : quote(key);
        }

        private String readScalarToken() {
            int start = index;
            while (!isEnd()) {
                char current = peek();
                if (current == ',' || current == '}' || current == ']') {
                    break;
                }
                index++;
            }
            String token = input.substring(start, index).trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Expected scalar value");
            }
            return token;
        }

        private String readQuoted() {
            char quote = read();
            StringBuilder output = new StringBuilder();
            output.append(quote);
            boolean escaped = false;
            while (!isEnd()) {
                char current = read();
                output.append(current);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == quote) {
                    return output.toString();
                }
            }
            throw new IllegalArgumentException("Unterminated quoted value");
        }

        private boolean isTypedArrayPrefix() {
            int cursor = index;
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= input.length()) {
                return false;
            }
            char type = input.charAt(cursor);
            if (type != 'B' && type != 'b' && type != 'I' && type != 'i' && type != 'L' && type != 'l') {
                return false;
            }
            cursor++;
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) {
                cursor++;
            }
            return cursor < input.length() && input.charAt(cursor) == ';';
        }

        private String normalizeScalar(String token) {
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                return quote("");
            }
            if (isBoolean(normalized) || NUMERIC_PATTERN.matcher(normalized).matches()) {
                return normalized;
            }
            return quote(normalized);
        }

        private boolean isBoolean(String value) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        }

        private String quote(String value) {
            StringBuilder output = new StringBuilder(value.length() + 8);
            output.append('"');
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                if (current == '\\' || current == '"') {
                    output.append('\\');
                }
                output.append(current);
            }
            output.append('"');
            return output.toString();
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(peek())) {
                index++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (isEnd() || peek() != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at index " + index);
            }
            index++;
        }

        private char peek() {
            return input.charAt(index);
        }

        private char read() {
            return input.charAt(index++);
        }

        private boolean isEnd() {
            return index >= input.length();
        }
    }
}
