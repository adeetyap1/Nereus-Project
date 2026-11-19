package my.nereus.intro.cinematic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/** MiniMessage dengan placeholder aman: nama pemain tidak pernah di-parse sebagai tag. */
public final class TextFormatter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private TextFormatter() {}

    public static Component plain(String text, Player p) {
        return MM.deserialize(text == null ? "" : text, Placeholder.unparsed("player", p.getName()));
    }

    public static Component formatted(String template, String text, Player p) {
        return MM.deserialize(template,
                Placeholder.parsed("text", text == null ? "" : text),
                Placeholder.unparsed("player", p.getName()));
    }

    public static TagResolver resolver(Player p) {
        return Placeholder.unparsed("player", p.getName());
    }
}
