package me.naook1.nereusicons.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** Legacy (&a, &#rrggbb) by default. Prefix with [mm] for MiniMessage. */
    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (raw.startsWith("[mm]")) {
            return MM.deserialize(raw.substring(4).stripLeading());
        }
        return LEGACY.deserialize(raw.replace('§', '&'));
    }

    /** For item names/lore: no default italic. */
    public static Component item(String raw) {
        return parse(raw).decoration(TextDecoration.ITALIC, false);
    }

    public static String fill(String text, String... kv) {
        if (text == null) {
            return "";
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            text = text.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return text;
    }
}
