package me.naook1.nereusfarmprice;

import java.util.ArrayList;
import java.util.List;
import me.naook1.nereusfarmprice.FarmManager.Candidate;
import me.naook1.nereusfarmprice.util.Text;
import me.naook1.nereusfarmprice.util.TimeUtil;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** /farmprice: shows today's boosted farms as text, in chat or in a book. */
public final class FarmDisplay {

    private final NereusFarmPrice plugin;

    public FarmDisplay(NereusFarmPrice plugin) {
        this.plugin = plugin;
    }

    public void show(Player p, String mode) {
        plugin.farm().checkRotation();
        if (mode.equalsIgnoreCase("book")) {
            book(p);
        } else {
            chat(p);
        }
    }

    private String[] common() {
        FarmManager fm = plugin.farm();
        long left = fm.periodEnd() - System.currentTimeMillis();
        return new String[]{
                "percent", fm.percentText(),
                "count", String.valueOf(fm.active().size()),
                "hours", String.valueOf(fm.durationHours()),
                "time", TimeUtil.left(plugin, left)
        };
    }

    private void chat(Player p) {
        var m = plugin.messages();
        FarmManager fm = plugin.farm();
        String[] vars = common();
        for (String line : m.list("chat.header", vars)) {
            p.sendMessage(Text.parse(line));
        }
        List<Material> active = fm.active();
        if (active.isEmpty()) {
            for (String line : m.list("chat.empty", vars)) {
                p.sendMessage(Text.parse(line));
            }
        }
        for (Material mat : active) {
            Candidate c = fm.candidate(mat);
            if (c == null) {
                continue;
            }
            for (String line : m.list("chat.item", vars)) {
                p.sendMessage(Text.parse(fm.fillItem(line, c, false)));
            }
        }
        for (String line : m.list("chat.footer", vars)) {
            p.sendMessage(Text.parse(line));
        }
    }

    private void book(Player p) {
        var m = plugin.messages();
        FarmManager fm = plugin.farm();
        String[] vars = common();

        List<String> first = new ArrayList<>(m.list("book.header", vars));
        for (Material mat : fm.active()) {
            Candidate c = fm.candidate(mat);
            if (c == null) {
                continue;
            }
            for (String line : m.list("book.item", vars)) {
                first.add(fm.fillItem(line, c, false));
            }
            first.add("");
        }
        first.addAll(m.list("book.footer", vars));

        List<Component> pages = new ArrayList<>();
        pages.add(join(first));
        pages.add(join(m.list("book.rules", vars)));
        p.openBook(Book.book(Text.parse(m.format("book.title")), Component.text("naook1/adityap"), pages));
    }

    private Component join(List<String> lines) {
        List<Component> parts = new ArrayList<>();
        for (String l : lines) {
            parts.add(Text.parse(l));
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), parts);
    }
}
