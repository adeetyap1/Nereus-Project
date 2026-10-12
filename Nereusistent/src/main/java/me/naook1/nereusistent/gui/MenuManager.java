package me.naook1.nereusistent.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.naook1.nereusistent.Nereusistent;
import me.naook1.nereusistent.util.SmallCaps;
import me.naook1.nereusistent.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Builds and runs the menus defined in menus.yml. */
public final class MenuManager {

    private record ItemDef(List<Integer> slots, String material, String name, List<String> lore,
                           List<String> actions, int amount, boolean glow, int modelData) {
    }

    private record MenuDef(String id, String title, int rows, boolean useFiller, List<ItemDef> items) {
    }

    private final Nereusistent plugin;
    private final Map<String, MenuDef> defs = new HashMap<>();
    private boolean smallCaps;
    private ItemDef filler;
    private String clickSound = "";
    private float clickVolume = 0.6f;

    public MenuManager(Nereusistent plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration cfg = plugin.loadYaml("menus.yml");
        smallCaps = plugin.getConfig().getBoolean("small-caps.gui", true);
        clickSound = plugin.getConfig().getString("gui.click-sound", "");
        clickVolume = (float) plugin.getConfig().getDouble("gui.click-volume", 0.6);
        defs.clear();

        ConfigurationSection f = cfg.getConfigurationSection("filler");
        filler = f == null ? null : parseItem(f);

        ConfigurationSection menus = cfg.getConfigurationSection("menus");
        if (menus == null) {
            return;
        }
        for (String id : menus.getKeys(false)) {
            ConfigurationSection m = menus.getConfigurationSection(id);
            if (m == null) {
                continue;
            }
            List<ItemDef> items = new ArrayList<>();
            ConfigurationSection is = m.getConfigurationSection("items");
            if (is != null) {
                for (String key : is.getKeys(false)) {
                    ConfigurationSection s = is.getConfigurationSection(key);
                    if (s != null) {
                        items.add(parseItem(s));
                    }
                }
            }
            String key = id.toLowerCase(Locale.ROOT);
            defs.put(key, new MenuDef(key, cv(m.getString("title", id)),
                    Math.max(1, Math.min(6, m.getInt("rows", 3))), m.getBoolean("filler", true), items));
        }
    }

    private ItemDef parseItem(ConfigurationSection s) {
        List<Integer> slots = new ArrayList<>();
        if (s.isList("slots")) {
            slots.addAll(s.getIntegerList("slots"));
        } else if (s.contains("slot")) {
            slots.add(s.getInt("slot"));
        }
        List<String> lore = new ArrayList<>();
        for (String line : s.getStringList("lore")) {
            lore.add(cv(line));
        }
        return new ItemDef(slots, s.getString("material", "STONE"), cv(s.getString("name", " ")), lore,
                s.getStringList("actions"), Math.max(1, s.getInt("amount", 1)),
                s.getBoolean("glow", false), s.getInt("custom-model-data", 0));
    }

    private String cv(String s) {
        if (s == null) {
            return "";
        }
        return smallCaps ? SmallCaps.convert(s) : s;
    }

    public String[] ctx(Player p) {
        return new String[]{
                "player", p.getName(),
                "developer", plugin.getConfig().getString("developer", "naook1/adityap"),
                "version", plugin.getDescription().getVersion(),
                "server_name", plugin.getConfig().getString("server-name", "Nereus Throne"),
                "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "max", String.valueOf(Bukkit.getMaxPlayers()),
        };
    }

    public void open(Player p, String id) {
        MenuDef def = defs.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            plugin.messages().send(p, "unknown-menu", "menu", id);
            return;
        }
        String[] kv = ctx(p);
        Component title = Text.parse(Text.papi(p, Text.fill(def.title(), kv)));
        NereusMenu menu = new NereusMenu(def.id(), def.rows() * 9, title);
        Inventory inv = menu.getInventory();

        if (def.useFiller() && filler != null) {
            ItemStack fill = buildItem(p, mat(filler.material()), 1, filler.name(), filler.lore(), false, 0, kv);
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, fill);
            }
        }
        for (ItemDef d : def.items()) {
            ItemStack item = buildItem(p, mat(d.material()), d.amount(), d.name(), d.lore(), d.glow(), d.modelData(), kv);
            for (int slot : d.slots()) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, item);
                    menu.bind(slot, d.actions());
                }
            }
        }
        p.openInventory(inv);
    }

    private Material mat(String name) {
        Material m = Material.matchMaterial(name == null ? "" : name);
        if (m == null || !m.isItem()) {
            plugin.getLogger().warning("Unknown material in menus.yml: " + name);
            return Material.PAPER;
        }
        return m;
    }

    private ItemStack buildItem(Player p, Material mat, int amount, String name, List<String> lore,
                                boolean glow, int modelData, String[] kv) {
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.item(Text.papi(p, Text.fill(name, kv))));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Text.item(Text.papi(p, Text.fill(line, kv))));
            }
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.values());
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);
        return item;
    }

    public void playClick(Player p) {
        if (clickSound != null && !clickSound.isBlank()) {
            p.playSound(p.getLocation(), clickSound, clickVolume, 1f);
        }
    }

    public void runActions(Player p, String menuId, List<String> actions) {
        if (!p.isOnline()) {
            return;
        }
        String[] kv = ctx(p);
        for (String raw : actions) {
            String line = raw.trim();
            int end = line.indexOf(']');
            if (!line.startsWith("[") || end < 0) {
                continue;
            }
            String tag = line.substring(1, end).toLowerCase(Locale.ROOT);
            String arg = Text.fill(line.substring(end + 1).trim(), kv);
            switch (tag) {
                case "close" -> p.closeInventory();
                case "menu" -> open(p, arg);
                case "refresh" -> open(p, menuId);
                case "player" -> p.performCommand(stripSlash(arg));
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(arg));
                case "message" -> p.sendMessage(Text.parse(Text.papi(p, arg)));
                case "sound" -> p.playSound(p.getLocation(), arg, clickVolume, 1f);
                case "link" -> sendLink(p, arg);
                default -> plugin.getLogger().warning("Unknown menu action: " + line);
            }
        }
    }

    private void sendLink(Player p, String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return;
        }
        String msg = plugin.messages().format("link", "url", url);
        p.sendMessage(Text.parse(msg).clickEvent(ClickEvent.openUrl(url)));
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
