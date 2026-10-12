package me.naook1.nereusthrone.util;

import me.naook1.nereusthrone.hook.PapiHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static boolean papi;

    private Text() {
    }

    public static void setPapi(boolean enabled) {
        papi = enabled;
    }

    public static String papi(Player player, String text) {
        if (!papi || player == null || text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            return PapiHook.set(player, text);
        } catch (Throwable t) {
            papi = false;
            return text;
        }
    }

    /** Legacy (&a, &#rrggbb) by default. Prefix the text with [mm] to use MiniMessage (gradients etc). */
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

    public static String toLegacy(Component component) {
        return LEGACY.serialize(component);
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

    /** Removes anything that could inject formatting or placeholders from player input. */
    public static String sanitize(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String s = input.replaceAll("[&§<>%{}\\[\\]]", "").trim();
        if (maxLength > 0 && s.length() > maxLength) {
            s = s.substring(0, maxLength);
        }
        return s;
    }
}
