package my.nereus.duel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class Text {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {}

    static Component color(String s) {
        return L.deserialize(s == null ? "" : s);
    }

    static Component item(String s) {
        return color(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
