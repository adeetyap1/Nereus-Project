package me.naook1.nereusrealm.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.afk.AfkManager;
import me.naook1.nereusrealm.daily.DailyManager;
import me.naook1.nereusrealm.feedback.FeedbackManager;
import me.naook1.nereusrealm.joinleave.CustomMessages;
import me.naook1.nereusrealm.joinleave.StyleManager;
import me.naook1.nereusrealm.rank.RankManager;
import me.naook1.nereusrealm.util.SmallCaps;
import me.naook1.nereusrealm.util.Text;
import me.naook1.nereusrealm.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

/** Builds and runs the menus defined in gui.yml. */
public final class MenuManager {

    private record ItemDef(List<Integer> slots, String material, String name, List<String> lore,
                           List<String> actions, List<String> rightActions, int amount,
                           boolean glow, String glowIf, boolean head, int modelData, String showIf) {
    }

    private record MenuDef(String id, String title, int rows, String type, boolean useFiller, String border,
                           boolean refresh, List<ItemDef> items, List<Integer> dynamicSlots) {
    }

    private record View(int page, int pages) {
    }

    private static final java.util.Set<String> FLAGS = java.util.Set.of("show-joinleave", "show-afk", "show-rewards");

    private final NereusRealm plugin;
    private final Map<String, MenuDef> defs = new HashMap<>();
    private FileConfiguration gui;
    private boolean smallCaps;
    private ItemDef filler;
    private String clickSound = "";
    private float clickVolume = 0.6f;
    private String ladderLine = "";
    private int ladderCount = 5;
    private String moneyOffLine = "";
    private String keyLine = "";
    private String keyOffLine = "";
    private int wrapWidth = 34;
    private BukkitTask refreshTask;

    public MenuManager(NereusRealm plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ loading

    public void reload() {
        gui = plugin.loadYaml("gui.yml");
        smallCaps = plugin.getConfig().getBoolean("small-caps.gui", true);
        defs.clear();
        clickSound = gui.getString("click-sound", "");
        clickVolume = (float) gui.getDouble("click-volume", 0.6);

        ladderLine = cv(gui.getString("lists.afk-ladder-line", ""));
        ladderCount = Math.max(1, Math.min(10, gui.getInt("lists.afk-ladder-count", 5)));
        moneyOffLine = cv(gui.getString("lists.afk-money-off", ""));
        keyLine = cv(gui.getString("lists.afk-key-line", ""));
        keyOffLine = cv(gui.getString("lists.afk-key-off", ""));
        wrapWidth = Math.max(16, gui.getInt("lists.feedback-wrap", 34));

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
                    m.getBoolean("filler", true), m.getString("border", ""), m.getBoolean("refresh", false),
                    items, m.getIntegerList("dynamic-slots")));
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
                Math.max(1, s.getInt("amount", 1)), s.getBoolean("glow", false), s.getString("glow-if", ""),
                s.getBoolean("head", false), s.getInt("custom-model-data", 0), s.getString("show-if", ""));
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

    // ------------------------------------------------------------------ refresh task

    /** Menus with refresh: true (AFK, daily) update their numbers every second while open. */
    public void start() {
        stop();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpen, 20L, 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void refreshOpen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof NereusMenu m) {
                MenuDef d = defs.get(m.id());
                if (d != null && d.refresh()) {
                    populate(p, d, m);
                }
            }
        }
    }

    // ------------------------------------------------------------------ opening

    public boolean exists(String id) {
        return defs.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public void open(Player p, String id) {
        open(p, id, 0);
    }

    public void open(Player p, String id, int page) {
        MenuDef def = defs.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            plugin.messages().send(p, "unknown-menu", "menu", id);
            return;
        }
        int pages = pagesOf(def);
        int pg = Math.max(0, Math.min(page, pages - 1));
        String[] kv = concat(ctx(p), "page", String.valueOf(pg + 1), "pages", String.valueOf(pages));
        Component title = Text.parse(Text.papi(p, Text.fill(def.title(), kv)));
        NereusMenu menu = new NereusMenu(def.id(), def.rows() * 9, title, pg, pages);
        populate(p, def, menu);
        p.openInventory(menu.getInventory());
    }

    private int pagesOf(MenuDef def) {
        if (def.type().equals("feedback-list")) {
            int per = Math.max(1, def.dynamicSlots().size());
            return Math.max(1, (int) Math.ceil(plugin.feedback().count() / (double) per));
        }
        return 1;
    }

    private void populate(Player p, MenuDef def, NereusMenu menu) {
        Inventory inv = menu.getInventory();
        inv.clear();
        menu.clearBindings();
        View view = new View(menu.page(), menu.pages());
        String[] kv = concat(ctx(p), "page", String.valueOf(view.page() + 1), "pages", String.valueOf(view.pages()));
        Map<String, List<String>> lists = lists(p, def);

        if (def.useFiller() && filler != null) {
            ItemStack fill = buildItem(p, null, mat(filler.material()), 1, filler.name(), filler.lore(),
                    false, 0, kv, lists);
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, fill);
            }
        }
        if (def.border() != null && !def.border().isBlank()) {
            ItemStack b = buildItem(p, null, mat(def.border()), 1, " ", List.of(), false, 0, kv, lists);
            for (int i = 0; i < inv.getSize(); i++) {
                int row = i / 9;
                int col = i % 9;
                if (row == 0 || row == def.rows() - 1 || col == 0 || col == 8) {
                    inv.setItem(i, b);
                }
            }
        }

        for (ItemDef d : def.items()) {
            if (!cond(p, view, d.showIf())) {
                continue;
            }
            boolean glow = d.glow() || (!d.glowIf().isBlank() && cond(p, view, d.glowIf()));
            ItemStack item = buildItem(p, d.head() ? p : null, mat(d.material()), d.amount(), d.name(), d.lore(),
                    glow, d.modelData(), kv, lists);
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
            case "feedback-list" -> addFeedbackList(p, def, menu, kv);
            case "ranks" -> addRanks(p, def, menu, kv);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ conditions

    /** show-if / glow-if: afk, has-review, custom, admin, has-prev, has-next, stars-N. Prefix ! to negate. */
    private boolean cond(Player p, View v, String c) {
        if (c == null || c.isBlank()) {
            return true;
        }
        String s = c.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("!")) {
            return !cond(p, v, s.substring(1));
        }
        if (s.startsWith("stars-")) {
            try {
                return plugin.feedback().stars(p.getUniqueId()) == Integer.parseInt(s.substring(6));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return switch (s) {
            case "afk" -> plugin.afk().isAfk(p);
            case "has-review" -> plugin.feedback().has(p.getUniqueId());
            case "custom" -> plugin.custom().tierOf(p) != null;
            case "admin" -> p.hasPermission("nereus.admin");
            case "has-prev" -> v.page() > 0;
            case "has-next" -> v.page() < v.pages() - 1;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ dynamic menu parts

    private void addStyles(Player p, MenuDef def, NereusMenu menu, boolean join, String[] kv) {
        ConfigurationSection t = gui.getConfigurationSection("style-item");
        if (t == null) {
            return;
        }
        StyleManager sm = plugin.styles();
        StyleManager.Style selected = sm.selected(p, join);
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
            ItemStack item = buildItem(p, null, unlocked ? s.icon() : locked, 1, nameTpl,
                    cv(t.getStringList(loreKey)), sel, 0, k2, Map.of());
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
            String[] k2 = concat(kv, "reward_name", r.name(), "day", String.valueOf(day));
            ItemStack item = buildItem(p, null, claimed ? claimedMat : r.icon(), day,
                    cv(t.getString("name-" + state, "{reward_name}")), cv(t.getStringList("lore-" + state)),
                    available, 0, k2, Map.of("reward_lore", r.lore()));
            menu.getInventory().setItem(slot, item);
            if (available) {
                menu.bind(slot, List.of("[daily]"), null);
            }
        }
    }

    private void addFeedbackList(Player p, MenuDef def, NereusMenu menu, String[] kv) {
        ConfigurationSection t = gui.getConfigurationSection("feedback-item");
        if (t == null) {
            return;
        }
        List<FeedbackManager.Review> all = plugin.feedback().newestFirst();
        int per = def.dynamicSlots().size();
        int start = menu.page() * per;
        boolean admin = p.hasPermission("nereus.admin");
        String nameTpl = cv(t.getString("name", "&e{review_name}"));
        List<String> loreTpl = new ArrayList<>(cv(t.getStringList("lore")));
        if (admin) {
            loreTpl.add("");
            loreTpl.add(plugin.messages().raw("feedback.admin-hint"));
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < per && start + i < all.size(); i++) {
            FeedbackManager.Review r = all.get(start + i);
            int slot = def.dynamicSlots().get(i);
            if (slot < 0 || slot >= menu.getInventory().getSize()) {
                continue;
            }
            List<String> msg = new ArrayList<>();
            if (r.message().isBlank()) {
                msg.add(plugin.messages().raw("feedback.no-message"));
            } else {
                for (String line : Text.wrap(r.message(), wrapWidth)) {
                    msg.add("&f" + line);
                }
            }
            String[] k2 = concat(kv, "review_name", r.name(), "review_stars", Text.stars(r.stars()),
                    "review_rating", r.stars() + "/5", "review_ago", TimeUtil.ago(now - r.time()));
            ItemStack item = buildItem(p, Bukkit.getOfflinePlayer(r.uuid()), Material.PLAYER_HEAD, 1, nameTpl,
                    loreTpl, false, 0, k2, Map.of("review_message", msg));
            menu.getInventory().setItem(slot, item);
            if (admin) {
                menu.bind(slot, List.of(), List.of("[feedback-delete-other] " + r.uuid()));
            }
        }
    }

    private void addRanks(Player p, MenuDef def, NereusMenu menu, String[] kv) {
        ConfigurationSection t = gui.getConfigurationSection("rank-item");
        if (t == null) {
            return;
        }
        RankManager rm = plugin.ranks();
        String nameTpl = cv(t.getString("name", "{rank_name}"));
        List<String> loreTpl = cv(t.getStringList("lore"));
        int i = 0;
        for (RankManager.Rank r : rm.all()) {
            if (i >= def.dynamicSlots().size()) {
                plugin.getLogger().warning("ranks.yml has more ranks than dynamic-slots in menu '" + def.id() + "'.");
                break;
            }
            int slot = def.dynamicSlots().get(i++);
            if (slot < 0 || slot >= menu.getInventory().getSize()) {
                continue;
            }
            boolean owned = rm.owns(p, r);
            String status = plugin.messages().raw(owned ? "ranks.status-owned" : "ranks.status-buy");
            String[] k2 = concat(kv, "rank_name", r.name(), "rank_price", r.price(), "rank_duration", r.duration(),
                    "rank_id", r.id(), "rank_status", status);
            ItemStack item = buildItem(p, null, r.icon(), 1, nameTpl, loreTpl, owned, 0, k2,
                    Map.of("rank_features", r.features()));
            menu.getInventory().setItem(slot, item);
            List<String> acts = new ArrayList<>();
            for (String a : r.actions()) {
                acts.add(Text.fill(a, "rank", r.name(), "rank_id", r.id(), "price", r.price()));
            }
            menu.bind(slot, acts, null);
        }
    }

    // ------------------------------------------------------------------ list placeholders

    /** Multi-line placeholders: a lore line that is exactly {name} is replaced by these lines. */
    private Map<String, List<String>> lists(Player p, MenuDef def) {
        Map<String, List<String>> out = new HashMap<>();
        switch (def.type()) {
            case "afk" -> {
                AfkManager am = plugin.afk();
                List<String> ladder = new ArrayList<>();
                if (am.moneyEnabled()) {
                    long interval = am.intervalMinutes();
                    for (int c = 1; c <= ladderCount; c++) {
                        ladder.add(Text.fill(ladderLine, "cycle", String.valueOf(c),
                                "minutes", String.valueOf(c * interval),
                                "amount", AfkManager.fmt(am.moneyForCycle(c))));
                    }
                } else {
                    ladder.add(moneyOffLine);
                }
                out.put("afk_ladder", ladder);
                List<String> keys = new ArrayList<>();
                if (am.keysEnabled()) {
                    for (AfkManager.KeyRate k : am.keyRates()) {
                        keys.add(Text.fill(keyLine, "key", k.display(),
                                "share", pct(k.share()), "overall", pct(k.overall())));
                    }
                } else {
                    keys.add(keyOffLine);
                }
                out.put("afk_keys", keys);
            }
            case "feedback" -> {
                FeedbackManager fm = plugin.feedback();
                int[] dist = fm.distribution();
                int max = 1;
                for (int s = 1; s <= 5; s++) {
                    max = Math.max(max, dist[s]);
                }
                List<String> lines = new ArrayList<>();
                for (int s = 5; s >= 1; s--) {
                    int filled = dist[s] == 0 ? 0 : Math.max(1, Math.round(dist[s] * 10f / max));
                    lines.add("&e" + s + "★ &8▏" + Text.bar(filled, 10, "&e", "&8") + " &f" + dist[s]);
                }
                out.put("rating_dist", lines);
                FeedbackManager.Review mine = fm.get(p.getUniqueId());
                List<String> my = new ArrayList<>();
                if (mine == null || mine.message().isBlank()) {
                    my.add(plugin.messages().raw("feedback.no-message"));
                } else {
                    for (String line : Text.wrap(mine.message(), wrapWidth)) {
                        my.add("&f" + line);
                    }
                }
                out.put("my_feedback", my);
            }
            default -> {
            }
        }
        return out;
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
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

    private ItemStack buildItem(Player p, OfflinePlayer owner, Material mat, int amount, String name,
                                List<String> lore, boolean glow, int modelData, String[] kv,
                                Map<String, List<String>> lists) {
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.item(Text.papi(p, Text.fill(name, kv))));
        List<Component> lines = new ArrayList<>();
        for (String line : expand(lore, lists)) {
            lines.add(Text.item(Text.papi(p, Text.fill(line, kv))));
        }
        if (!lines.isEmpty()) {
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.values());
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }
        if (owner != null && meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<String> expand(List<String> lines, Map<String, List<String>> lists) {
        if (lists.isEmpty()) {
            return lines;
        }
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.length() > 2 && t.startsWith("{") && t.endsWith("}")
                    && lists.containsKey(t.substring(1, t.length() - 1))) {
                out.addAll(lists.get(t.substring(1, t.length() - 1)));
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

    /** All {placeholders} usable in gui.yml, as key/value pairs. */
    public String[] ctx(Player p) {
        UUID u = p.getUniqueId();
        StyleManager sm = plugin.styles();
        CustomMessages cm = plugin.custom();
        DailyManager dm = plugin.daily();
        AfkManager am = plugin.afk();
        FeedbackManager fm = plugin.feedback();
        AfkManager.Info ai = am.info(p);
        CustomMessages.Tier tier = cm.tierOf(p);
        RankManager.Rank cur = plugin.ranks().current(p);
        int mine = fm.stars(u);
        long fbLeft = fm.cooldownLeft(u);
        return new String[]{
                "player", p.getName(),
                "developer", plugin.getConfig().getString("developer", "naook1/adityap"),
                "version", plugin.getDescription().getVersion(),
                "server_name", plugin.getConfig().getString("server-name", "Nereus Throne"),
                "current_rank", plugin.ranks().currentName(p),
                "join_style", sm.displayName(p, true),
                "leave_style", sm.displayName(p, false),
                "streak", String.valueOf(dm.currentStreak(p)),
                "daily_status", plugin.messages().raw(dm.claimedToday(p) ? "daily.status-claimed" : "daily.status-ready"),
                "reset", TimeUtil.format(dm.secondsUntilReset()),
                "afk_state", plugin.messages().raw(ai.afk() ? "afk.state-on" : "afk.state-off"),
                "afk_time", TimeUtil.clock(ai.seconds()),
                "afk_reason", ai.reason(),
                "afk_next_in", TimeUtil.clock(ai.nextIn()),
                "afk_next_money", AfkManager.fmt(ai.nextMoney()),
                "afk_progress", Text.bar(ai.percent() / 10, 10, "&a", "&8"),
                "afk_progress_pct", String.valueOf(ai.percent()),
                "afk_reward_state", plugin.messages().raw(ai.capped() ? "afk.capped" : "afk.reward-active"),
                "afk_session_money", AfkManager.fmt(ai.sessionMoney()),
                "afk_session_keys", String.valueOf(ai.sessionKeys()),
                "afk_interval", String.valueOf(am.intervalMinutes()),
                "afk_auto", String.valueOf(am.autoAfkMinutes()),
                "afk_money_base", AfkManager.fmt(am.moneyBase()),
                "afk_money_step", AfkManager.fmt(am.moneyStep()),
                "afk_money_max", am.moneyMax() > 0 ? AfkManager.fmt(am.moneyMax()) : "∞",
                "afk_key_chance", pct(am.keyChance()),
                "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "max", String.valueOf(Bukkit.getMaxPlayers()),
                "gadget_state", plugin.messages().raw(plugin.gadgets().isDisabled(u) ? "status.disabled" : "status.enabled"),
                "kits_command", plugin.getConfig().getString("kits.command", "kit"),
                "state_show_joinleave", state(plugin.data().getBoolean(u, "show-joinleave", true)),
                "state_show_afk", state(plugin.data().getBoolean(u, "show-afk", true)),
                "state_show_rewards", state(plugin.data().getBoolean(u, "show-rewards", true)),
                "custom_join_text", customPreview(p, true),
                "custom_leave_text", customPreview(p, false),
                "custom_join_active", plugin.messages().raw(sm.customActive(p, true) ? "custom.active" : "custom.inactive"),
                "custom_leave_active", plugin.messages().raw(sm.customActive(p, false) ? "custom.active" : "custom.inactive"),
                "custom_max", tier == null ? "-" : String.valueOf(tier.maxLength()),
                "custom_colors", tier == null ? "-" : cm.describe(tier),
                "custom_requirement", cm.requirement(),
                "rating_avg", String.format(Locale.ROOT, "%.1f", fm.average()),
                "rating_count", String.valueOf(fm.count()),
                "rating_stars", Text.stars((int) Math.round(fm.average())),
                "my_stars", mine > 0 ? Text.stars(mine) : plugin.messages().raw("feedback.not-rated"),
                "feedback_next", fbLeft > 0 ? TimeUtil.format(fbLeft) : plugin.messages().raw("feedback.can-rate-now"),
        };
    }

    private String customPreview(Player p, boolean join) {
        if (plugin.custom().hasText(p, join) && plugin.custom().tierOf(p) != null) {
            return Text.toLegacy(plugin.styles().renderCustom(p, join));
        }
        return plugin.messages().raw("custom.empty-preview");
    }

    private String state(boolean on) {
        return plugin.messages().raw(on ? "status.enabled" : "status.disabled");
    }

    // ------------------------------------------------------------------ actions

    public void playClick(Player p) {
        if (clickSound != null && !clickSound.isBlank()) {
            p.playSound(p.getLocation(), clickSound, clickVolume, 1f);
        }
    }

    public void runActions(Player p, NereusMenu menu, List<String> actions) {
        if (!p.isOnline()) {
            return;
        }
        String menuId = menu.id();
        String[] kv = ctx(p);
        for (String raw : actions) {
            String line = raw.trim();
            int end = line.indexOf(']');
            if (!line.startsWith("[") || end < 0) {
                continue;
            }
            String tag = line.substring(1, end).toLowerCase(Locale.ROOT);
            String arg = Text.fill(line.substring(end + 1).trim(), kv);
            boolean join = arg.equalsIgnoreCase("join");
            switch (tag) {
                case "close" -> p.closeInventory();
                case "menu" -> open(p, arg);
                case "refresh" -> open(p, menuId, menu.page());
                case "page" -> open(p, menuId, arg.equalsIgnoreCase("prev") ? menu.page() - 1 : menu.page() + 1);
                case "player" -> p.performCommand(stripSlash(arg));
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(arg));
                case "message" -> p.sendMessage(Text.parse(Text.papi(p, arg)));
                case "sound" -> p.playSound(p.getLocation(), arg, clickVolume, 1f);
                case "link" -> sendLink(p, arg);
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
                case "custom-set" -> plugin.prompts().beginCustom(p, join);
                case "custom-use" -> {
                    if (plugin.custom().use(p, join)) {
                        plugin.styles().previewCurrent(p, join);
                    }
                    open(p, menuId);
                }
                case "custom-reset" -> {
                    plugin.custom().reset(p, join);
                    plugin.messages().send(p, "custom.reset");
                    open(p, menuId);
                }
                case "toggle" -> {
                    toggle(p, arg);
                    open(p, menuId);
                }
                case "daily" -> {
                    plugin.daily().claim(p);
                    open(p, menuId);
                }
                case "afk" -> {
                    if (plugin.afk().isEnabled()) {
                        plugin.afk().toggle(p, null);
                    } else {
                        plugin.messages().send(p, "afk.disabled");
                    }
                    open(p, menuId);
                }
                case "teleport" -> teleport(p, arg);
                case "toggle-gadget" -> {
                    plugin.gadgets().toggle(p);
                    open(p, menuId);
                }
                case "redeem" -> plugin.prompts().beginRedeem(p);
                case "afk-reason" -> plugin.prompts().beginAfk(p);
                case "feedback-start" -> {
                    if (!p.hasPermission("nereus.rating")) {
                        plugin.messages().send(p, "no-permission");
                    } else if (plugin.feedback().check(p)) {
                        open(p, "feedback-stars");
                    }
                }
                case "rate" -> {
                    try {
                        plugin.prompts().beginFeedback(p, Integer.parseInt(arg));
                    } catch (NumberFormatException ignored) {
                        // bad number in gui.yml
                    }
                }
                case "feedback-delete" -> {
                    if (plugin.feedback().delete(p.getUniqueId())) {
                        plugin.messages().send(p, "feedback.deleted-self");
                    }
                    open(p, menuId);
                }
                case "feedback-delete-other" -> {
                    if (p.hasPermission("nereus.admin")) {
                        deleteOther(p, arg);
                    }
                    open(p, menuId, menu.page());
                }
                default -> plugin.getLogger().warning("Unknown gui action: " + line);
            }
        }
    }

    private void teleport(Player p, String point) {
        Location loc = plugin.points().get(point);
        if (loc == null) {
            plugin.messages().send(p, "point.missing", "point", point);
            return;
        }
        p.closeInventory();
        p.teleportAsync(loc).thenAccept(ok -> {
            if (ok) {
                plugin.messages().send(p, "point.teleported", "point", point);
            }
        });
    }

    private void deleteOther(Player admin, String uuid) {
        try {
            UUID id = UUID.fromString(uuid);
            FeedbackManager.Review r = plugin.feedback().get(id);
            if (r != null && plugin.feedback().delete(id)) {
                plugin.messages().send(admin, "feedback.deleted", "player", r.name());
            } else {
                plugin.messages().send(admin, "feedback.not-found");
            }
        } catch (IllegalArgumentException e) {
            plugin.messages().send(admin, "feedback.not-found");
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

    private void sendLink(Player p, String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return;
        }
        String msg = plugin.messages().format("link", "url", url);
        p.sendMessage(Text.parse(Text.papi(p, msg)).clickEvent(ClickEvent.openUrl(url)));
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
