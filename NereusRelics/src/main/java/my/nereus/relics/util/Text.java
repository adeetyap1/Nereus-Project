package my.nereus.relics.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Color code (&amp; dan &amp;#RRGGBB) ke Component, plus helper nama item. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private Text() {}

    public static Component color(String s) {
        return LEGACY.deserialize(s == null ? "" : s);
    }

    /** Untuk nama/lore item: tanpa italic bawaan. */
    public static Component itemText(String s) {
        return color(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /** Nama yang aman dibaca Java dan Bedrock (teks biasa, tanpa translatable). */
    public static String itemLabel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return plain(meta.displayName());
        }
        return pretty(item.getType());
    }

    public static List<String> splitPipe(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.split("\\|")) out.add(p.strip());
        return out;
    }
}
