package ru.xaoser.raidon.runtime.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import javax.annotation.Nullable;
import java.util.Locale;

public final class RaidTextFormatter {
    private RaidTextFormatter() {
    }

    public static Component parse(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }

        Component json = tryParseJson(raw);
        if (json != null) {
            return json;
        }

        MutableComponent root = null;
        Style style = Style.EMPTY;
        StringBuilder buffer = new StringBuilder();

        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if ((current == '&' || current == '\u00A7') && index + 1 < raw.length()) {
                String hex = tryReadHexColor(raw, index + 1);
                if (hex != null) {
                    root = appendSegment(root, buffer, style);
                    style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(hex, 16)));
                    index += 7;
                    continue;
                }

                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(index + 1));
                if (formatting != null) {
                    root = appendSegment(root, buffer, style);
                    style = applyFormatting(style, formatting);
                    index++;
                    continue;
                }
            }
            buffer.append(current);
        }

        root = appendSegment(root, buffer, style);
        return root == null ? Component.empty() : root;
    }

    @Nullable
    private static Component tryParseJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Component.empty();
        }
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
            return null;
        }
        try {
            return Component.Serializer.fromJson(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static String tryReadHexColor(String raw, int markerIndex) {
        if (markerIndex >= raw.length() || raw.charAt(markerIndex) != '#') {
            return null;
        }
        int end = markerIndex + 7;
        if (end > raw.length()) {
            return null;
        }
        String hex = raw.substring(markerIndex + 1, end).toLowerCase(Locale.ROOT);
        for (int i = 0; i < hex.length(); i++) {
            char ch = hex.charAt(i);
            boolean digit = ch >= '0' && ch <= '9';
            boolean alpha = ch >= 'a' && ch <= 'f';
            if (!digit && !alpha) {
                return null;
            }
        }
        return hex;
    }

    private static Style applyFormatting(Style style, ChatFormatting formatting) {
        if (formatting == ChatFormatting.RESET) {
            return Style.EMPTY;
        }
        if (formatting.isColor()) {
            return Style.EMPTY.applyFormat(formatting);
        }
        return style.applyFormat(formatting);
    }

    private static MutableComponent appendSegment(MutableComponent root, StringBuilder buffer, Style style) {
        if (buffer.isEmpty()) {
            return root;
        }
        MutableComponent segment = Component.literal(buffer.toString()).setStyle(style);
        if (root == null) {
            root = segment;
        } else {
            root.append(segment);
        }
        buffer.setLength(0);
        return root;
    }
}
