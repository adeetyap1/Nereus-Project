package me.naook1.nereusthrone.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.naook1.nereusthrone.NereusThrone;
import me.naook1.nereusthrone.daily.DailyManager;
import me.naook1.nereusthrone.joinleave.StyleManager;
import me.naook1.nereusthrone.util.SmallCaps;
import me.naook1.nereusthrone.util.Text;
import me.naook1.nereusthrone.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Builds and runs the menus defined in gui.yml. */
public final class MenuManager {

    private record ItemDef(List<Integer> slots, String material, String name, List<String> lore,
                           List<String> actions, List<String> rightActions, int amount,
                           boolean glow, boolean head, int modelData) {
    }

    private record MenuDef(String id, String title, int rows, String type, boolean useFiller,
                           List<ItemDef> items, List<Integer> dynamicSlots) {
    }

    private static final Set<String> FLAGS = Set.of("show-joinleave", "show-afk", "show-rewards");

    private final NereusThrone plugin;
    private final Map<String, MenuDef> defs = new HashMap<>();
    private FileConfiguration gui;
    private boolean smallCaps;
    private ItemDef filler;
    private String clickSound = "";
    private float clickVolume = 0.6f;

    public MenuManager(NereusThrone plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ loading

    public void reload() {
        gui = plugin.loadYaml("gui.yml");
        smallCaps = plugin.getConfig().getBoolean("small-caps.gui", true);
        defs.clear();
        clickSound = gui.getString("click-sound", "");
        clickVolume = (float) gui.getDouble("click-volume", 0.6);

        ConfigurationSection f = gui.getConfigurationSection("filler");
        filler = f == null ? null : parseItem(f);

        ConfigurationSection menus = gui.getConfigurationSection("menus");
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
                    Math.max(1, Math.min(6, m.getInt("rows", 3))),
                    m.getString("type", "static").toLowerCase(Locale.ROOT),
                    m.getBoolean("filler", true), items, m.getIntegerList("dynamic-slots")));
        }
    }

    private ItemDef parseItem(ConfigurationSection s) {
        List<Integer> slots = new ArrayList<>();
        if (s.isList("slots")) {
            slots.addAll(s.getIntegerList("slots"));
        } else if (s.contains("slot")) {
            slots.add(s.getInt("slot"));
        }
        return new ItemDef(slots, s.getString("material", "STONE"), cv(s.getString("name", " ")),
                cv(s.getStringList("lore")), s.getStringList("actions"), s.getStringList("right-actions"),
                Math.max(1, s.getInt("amount", 1)), s.getBoolean("glow", false), s.getBoolean("head", false),
                s.getInt("custom-model-data", 0));
    }

    private String cv(String s) {
        if (s == null) {
            return "";
        }
        return smallCaps ? SmallCaps.convert(s) : s;
    }

    private List<String> cv(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String s : list) {
            out.add(cv(s));
        }
        return out;
    }

    // ------------------------------------------------------------------ opening

    public boolean exists(String id) {
        return defs.containsKey(id.toLowerCase(Locale.ROOT));
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
            ItemStack fill = buildItem(p, mat(filler.material()), 1, filler.name(), filler.lore(),
                    false, false, 0, kv);
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, fill);
            }
        }

        for (ItemDef d : def.items()) {
            ItemStack item = buildItem(p, mat(d.material()), d.amount(), d.name(), d.lore(),
                    d.glow(), d.head(), d.modelData(), kv);
            for (int slot : d.slots()) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, item);
                    menu.bind(slot, d.actions(), d.rightActions());
                }
            }
        }

        switch (def.type()) {
            case "join-styles" -> addStyles(p, def, menu, true, kv);
            case "leave-styles" -> addStyles(p, def, menu, false, kv);
            case "daily" -> addDaily(p, def, menu, kv);
            default -> {
            }
        }
        p.openInventory(inv);
    }

    private void addStyles(Player p, MenuDef def, NereusMenu menu, boolean join, String[] kv) {
        ConfigurationSection t = gui.getConfigurationSection("style-item");
        if (t == null) {
            return;
        }
        StyleManager sm = plugin.styles();
        StyleManager.Style selected = sm.resolve(p, join);
        Material locked = mat(t.getString("locked-material", "BARRIER"));
        String nameTpl = cv(t.getString("name", "{style_name}"));
        int i = 0;
        for (StyleManager.Style s : sm.styles(join).values()) {
            if (i >= def.dynamicSlots().size()) {
                plugin.getLogger().warning("Menu '" + def.id() + "' has more styles than dynamic-slots.");
                break;
            }
            int slot = def.dynamicSlots().get(i++);
            if (slot < 0 || slot >= menu.getInventory().getSize()) {
                continue;
            }
            boolean unlocked = sm.canUse(p, s);
            boolean sel = selected != null && selected.id().equals(s.id());
            String loreKey = sel ? "lore-selected" : unlocked ? "lore-unlocked" : "lore-locked";
            String preview = Text.toLegacy(sm.render(p, s));
            String[] k2 = concat(kv, "style_name", s.name(), "requirement", sm.requirement(s), "preview", preview);
            ItemStack item = buildItem(p, unlocked ? s.icon() : locked, 1, nameTpl,
                    cv(t.getStringList(loreKey)), sel, false, 0, k2);
            menu.getInventory().setItem(slot, item);
            String tag = join ? "join" : "leave";
            menu.bind(slot, List.of("[select-" + tag + "] " + s.id()), List.of("[preview-" + tag + "] " + s.id()));
        }
    }

    private void addDaily(Player p, MenuDef def, NereusMenu menu, String[] kv) {
        ConfigurationSection t = gui.getConfigurationSection("daily-item");
        if (t == null) {
            return;
        }
        DailyManager dm = plugin.daily();
        int max = dm.maxDay();
        int next = dm.nextDay(p);
        int streak = dm.streak(p);
        boolean claimedToday = dm.claimedToday(p);
        Material claimedMat = mat(t.getString("material-claimed", "LIME_STAINED_GLASS_PANE"));

        for (int day = 1; day <= max && day - 1 < def.dynamicSlots().size(); day++) {
            DailyManager.Reward r = dm.reward(day);
            if (r == null) {
                continue;
            }
            int slot = def.dynamicSlots().get(day - 1);
            if (slot < 0 || slot >= menu.getInventory().getSize()) {
                continue;
            }
            boolean claimed = claimedToday ? day <= streak : day < next;
            boolean available = !claimedToday && day == next;
            String state = claimed ? "claimed" : available ? "available" : "locked";
            List<String> lore = expand(cv(t.getStringList("lore-" + state)), "reward_lore", r.lore());
            String[] k2 = concat(kv, "reward_name", r.name(), "day", String.valueOf(day));
            ItemStack item = buildItem(p, claimed ? claimedMat : r.icon(), day,
                    cv(t.getString("name-" + state, "{reward_name}")), lore, available, false, 0, k2);
            menu.getInventory().setItem(slot, item);
            if (available) {
                menu.bind(slot, List.of("[daily]"), null);
            }
        }
    }

    // ------------------------------------------------------------------ items

    private Material mat(String name) {
        Material m = Material.matchMaterial(name == null ? "" : name);
        if (m == null || !m.isItem()) {
            plugin.getLogger().warning("Unknown material in gui.yml: " + name);
            return Material.PAPER;
        }
        return m;
    }

    private ItemStack buildItem(Player p, Material mat, int amount, String name, List<String> lore,
                                boolean glow, boolean head, int modelData, String[] kv) {
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
        if (head && meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(p);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<String> expand(List<String> lines, String key, List<String> replacement) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().equals("{" + key + "}")) {
                out.addAll(replacement);
            } else {
                out.add(line);
            }
        }
        return out;
    }

    private static String[] concat(String[] a, String... b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private String[] ctx(Player p) {
        UUID u = p.getUniqueId();
        StyleManager sm = plugin.styles();
        StyleManager.Style js = sm.resolve(p, true);
        StyleManager.Style ls = sm.resolve(p, false);
        DailyManager dm = plugin.daily();
        return new String[]{
                "player", p.getName(),
                "developer", plugin.getConfig().getString("developer", "naook1/adityap"),
                "join_style", js == null ? "-" : js.name(),
                "leave_style", ls == null ? "-" : ls.name(),
                "streak", String.valueOf(dm.currentStreak(p)),
                "daily_status", plugin.messages().raw(dm.claimedToday(p) ? "daily.status-claimed" : "daily.status-ready"),
                "reset", TimeUtil.format(dm.secondsUntilReset()),
                "afk_state", plugin.messages().raw(plugin.afk().isAfk(p) ? "afk.state-on" : "afk.state-off"),
                "afk_interval", String.valueOf(plugin.afk().intervalMinutes()),
                "afk_money_base", String.valueOf(plugin.afk().moneyBase()),
                "afk_money_step", String.valueOf(plugin.afk().moneyStep()),
                "afk_money_max", String.valueOf(plugin.afk().moneyMax()),
                "afk_key_chance", String.valueOf(plugin.afk().keyChance()),
                "kits_command", plugin.getConfig().getString("kits.command", "kit"),
                "state_show_joinleave", state(plugin.data().getBoolean(u, "show-joinleave", true)),
                "state_show_afk", state(plugin.data().getBoolean(u, "show-afk", true)),
                "state_show_rewards", state(plugin.data().getBoolean(u, "show-rewards", true)),
        };
    }

    private String state(boolean on) {
        return plugin.messages().raw(on ? "status.on" : "status.off");
    }

    // ------------------------------------------------------------------ actions

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
                case "select-join" -> {
                    plugin.styles().select(p, true, arg);
                    open(p, menuId);
                }
                case "select-leave" -> {
                    plugin.styles().select(p, false, arg);
                    open(p, menuId);
                }
                case "preview-join" -> plugin.styles().preview(p, true, arg);
                case "preview-leave" -> plugin.styles().preview(p, false, arg);
                case "toggle" -> {
                    toggle(p, arg);
                    open(p, menuId);
                }
                case "daily" -> {
                    plugin.daily().claim(p);
                    open(p, menuId);
                }
                case "afk" -> {
                    p.closeInventory();
                    if (plugin.afk().isEnabled()) {
                        plugin.afk().toggle(p, null);
                    } else {
                        plugin.messages().send(p, "afk.disabled");
                    }
                }
                default -> plugin.getLogger().warning("Unknown gui action: " + line);
            }
        }
    }

    private void toggle(Player p, String flag) {
        if (!FLAGS.contains(flag)) {
            return;
        }
        boolean now = !plugin.data().getBoolean(p.getUniqueId(), flag, true);
        plugin.data().set(p.getUniqueId(), flag, now);
        plugin.messages().send(p, "settings.toggled",
                "setting", plugin.messages().raw("settings.name-" + flag),
                "state", state(now));
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
