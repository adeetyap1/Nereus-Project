package me.naook1.nereushide.gui;

import java.util.ArrayList;
import java.util.List;
import me.naook1.nereushide.GameManager;
import me.naook1.nereushide.NereusHide;
import me.naook1.nereushide.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** /hide admin: start, stop, open, join as a player and tune seekers / time. */
public final class AdminMenu implements InventoryHolder {

    public static final int OPEN = 10;
    public static final int START = 11;
    public static final int STOP = 12;
    public static final int JOIN = 14;
    public static final int INFO = 16;
    public static final int SEEKERS_MINUS = 19;
    public static final int SEEKERS_SHOW = 20;
    public static final int SEEKERS_PLUS = 21;
    public static final int TIME_MINUS = 23;
    public static final int TIME_SHOW = 24;
    public static final int TIME_PLUS = 25;
    public static final int RESET = 31;
    public static final int CLOSE = 35;

    private final NereusHide plugin;
    private final Inventory inventory;

    public AdminMenu(NereusHide plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 36, Text.parse(plugin.messages().format("menu.title")));
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    public void refresh() {
        var m = plugin.messages();
        var g = plugin.game();
        var s = plugin.settings();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, m.format("gui.filler"), List.of()));
        }
        inventory.setItem(OPEN, item(Material.LIME_CONCRETE, m.format("menu.open"), List.of()));
        inventory.setItem(START, item(Material.EMERALD_BLOCK, m.format("menu.start"), List.of()));
        inventory.setItem(STOP, item(Material.RED_CONCRETE, m.format("menu.stop"), List.of()));
        inventory.setItem(JOIN, item(Material.PLAYER_HEAD, m.format("menu.join"), List.of()));
        List<String> info = new ArrayList<>();
        info.add(m.format("menu.info-phase", "phase", g.phase().name()));
        info.add(m.format("menu.info-players", "n", String.valueOf(g.count()), "min", String.valueOf(s.minPlayers()),
                "max", String.valueOf(s.maxPlayers())));
        info.add(plugin.arena().complete() ? m.format("menu.info-arena-ok") : m.format("menu.info-arena-missing", "missing", plugin.arena().missing()));
        inventory.setItem(INFO, item(Material.BOOK, m.format("menu.info"), info));

        int players = Math.max(g.count(), s.minPlayers());
        inventory.setItem(SEEKERS_MINUS, item(Material.RED_STAINED_GLASS_PANE, m.format("menu.minus"), List.of()));
        inventory.setItem(SEEKERS_SHOW, item(Material.COMPASS, m.format("menu.seekers", "n", String.valueOf(s.seekerCount(players))), List.of()));
        inventory.setItem(SEEKERS_PLUS, item(Material.LIME_STAINED_GLASS_PANE, m.format("menu.plus"), List.of()));
        inventory.setItem(TIME_MINUS, item(Material.RED_STAINED_GLASS_PANE, m.format("menu.minus-time"), List.of()));
        inventory.setItem(TIME_SHOW, item(Material.CLOCK, m.format("menu.seek-time", "seconds", String.valueOf(s.seekTime())), List.of()));
        inventory.setItem(TIME_PLUS, item(Material.LIME_STAINED_GLASS_PANE, m.format("menu.plus-time"), List.of()));
        inventory.setItem(RESET, item(Material.BARRIER, m.format("menu.reset"), List.of()));
        inventory.setItem(CLOSE, item(Material.OAK_DOOR, m.format("menu.close"), List.of()));
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        if (!lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Text.item(s));
            }
            meta.lore(l);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isRunning(GameManager g) {
        return g.phase() != GameManager.Phase.IDLE;
    }
}
