package me.naook1.nereusfarmprice;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import me.naook1.nereusfarmprice.FarmManager.Crop;
import me.naook1.nereusfarmprice.util.Text;
import me.naook1.nereusfarmprice.util.TimeUtil;
import org.bukkit.entity.Player;

/** The text menu: a book (clean, scrollable pages) or a chat panel. No icons, no item lore. */
public final class FarmDisplay {

    private static final int ROWS_PER_PAGE = 12;

    private final NereusFarmPrice plugin;

    public FarmDisplay(NereusFarmPrice plugin) {
        this.plugin = plugin;
    }

    public void show(Player p, String mode) {
        if (mode.equalsIgnoreCase("chat")) {
            chat(p);
        } else {
            book(p);
        }
    }

    // ------------------------------------------------------------------ chat

    private void chat(Player p) {
        var m = plugin.messages();
        FarmManager fm = plugin.farm();
        long now = System.currentTimeMillis();
        List<Crop> buffed = new ArrayList<>();
        for (Crop c : fm.crops()) {
            if (fm.buffOf(c) != null) {
                buffed.add(c);
            }
        }
        p.sendMessage(Text.parse(m.format("ui.chat-divider")));
        p.sendMessage(Text.parse(m.format("ui.chat-title")));
        if (fm.nextRotation() > 0) {
            p.sendMessage(Text.parse(m.format("ui.chat-next", "time", TimeUtil.left(plugin, fm.nextRotation() - now))));
        }
        p.sendMessage(Text.parse(m.format("ui.chat-divider")));
        p.sendMessage(Text.parse(m.format("ui.chat-buff-header")));
        if (buffed.isEmpty()) {
            p.sendMessage(Text.parse(m.format("ui.chat-buff-none")));
        }
        for (Crop c : buffed) {
            p.sendMessage(Text.parse(m.format("ui.chat-buff", fm.priceVars(c, now))));
        }
        p.sendMessage(Component.empty());
        p.sendMessage(Text.parse(m.format("ui.chat-all-header")));
        for (Crop c : fm.crops()) {
            p.sendMessage(Text.parse(row("ui.chat-row", c, now, "&a", "&c", "&f")));
        }
        p.sendMessage(Text.parse(m.format("ui.chat-divider")));
        p.sendMessage(Text.parse(m.format("ui.chat-footer")));
    }

    // ------------------------------------------------------------------ book

    private void book(Player p) {
        var m = plugin.messages();
        FarmManager fm = plugin.farm();
        long now = System.currentTimeMillis();
        List<Component> pages = new ArrayList<>();

        // page 1: active buffs
        List<String> first = new ArrayList<>();
        first.add(m.format("ui.book-heading"));
        if (fm.nextRotation() > 0) {
            first.add(m.format("ui.book-next"));
            first.add(m.format("ui.book-next-time", "time", TimeUtil.left(plugin, fm.nextRotation() - now)));
        }
        first.add("");
        first.add(m.format("ui.book-buff-header"));
        boolean any = false;
        for (Crop c : fm.crops()) {
            if (fm.buffOf(c) == null) {
                continue;
            }
            any = true;
            String[] vars = fm.priceVars(c, now);
            first.add(m.format("ui.book-buff-1", vars));
            first.add(m.format("ui.book-buff-2", vars));
            first.add(m.format("ui.book-buff-3", vars));
        }
        if (!any) {
            first.add(m.format("ui.book-buff-none"));
        }
        pages.add(join(first));

        // all crops, paged
        List<Crop> crops = fm.crops();
        for (int i = 0; i < crops.size(); i += ROWS_PER_PAGE) {
            List<String> lines = new ArrayList<>();
            lines.add(m.format("ui.book-all-header"));
            lines.add("");
            for (Crop c : crops.subList(i, Math.min(crops.size(), i + ROWS_PER_PAGE))) {
                lines.add(row("ui.book-row", c, now, "&2", "&4", "&0"));
            }
            pages.add(join(lines));
        }

        // how it works
        pages.add(join(m.list("ui.book-how")));

        p.openBook(Book.book(Text.parse(m.format("ui.book-title")), Component.text("naook1/adityap"), pages));
    }

    private Component join(List<String> lines) {
        List<Component> parts = new ArrayList<>();
        for (String l : lines) {
            parts.add(Text.parse(l));
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), parts);
    }

    // ------------------------------------------------------------------ shared

    /** One crop row: current price, colored by buff (up), saturation (down) or normal. */
    private String row(String key, Crop c, long now, String up, String down, String normal) {
        var m = plugin.messages();
        FarmManager fm = plugin.farm();
        double base = fm.basePrice(c);
        double f = fm.factor(c);
        String color = normal;
        String arrow = "";
        if (f > 1.001) {
            color = up;
            arrow = m.raw("ui.arrow-up");
        } else if (f < 0.999) {
            color = down;
            arrow = m.raw("ui.arrow-down");
        }
        String price = base < 0 ? m.raw("ui.unknown-price") : fm.fmt(base * f);
        return m.format(key, "crop", c.name(), "price", price, "color", color, "arrow", arrow);
    }
}
