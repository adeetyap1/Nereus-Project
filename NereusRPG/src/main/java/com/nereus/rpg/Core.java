package com.nereus.rpg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.nereus.rpg.guild.Guild;
import com.nereus.rpg.guild.Platform;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Inti plugin: data pemain, konfigurasi, XP, buff, rank guild, ujian, dan hadiah. */
public final class Core {
    public final NereusRPG plugin;
    public final Tracker tracker;
    public final CustomMenus menus;
    public final Random rng = new Random();

    private final Map<UUID, Profile> profiles = new HashMap<>();
    public final List<Defs.Rank> ranks = new ArrayList<>();
    public final Map<String, Defs.Quest> quests = new LinkedHashMap<>();
    public final List<Defs.Mission> missions = new ArrayList<>();
    public final Map<String, Defs.Race> classes = new LinkedHashMap<>();
    private final Map<String, List<String>> weapons = new HashMap<>();

    public PlayerStore store;
    public Guild guild;
    private ZoneId zone = ZoneId.of("Asia/Jakarta");

    public Core(NereusRPG plugin) {
        this.plugin = plugin;
        this.tracker = new Tracker(this);
        this.menus = new CustomMenus(this);
    }

    // ------------------------------------------------------------ muat

    public Display display;
    public final PlaceTracker placed = new PlaceTracker();

    public void start() {
        plugin.getDataFolder().mkdirs();
        store = new PlayerStore(new File(plugin.getDataFolder(), "playerdata"), plugin.getLogger());
        new File(plugin.getDataFolder(), "logs").mkdirs();
        reload();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, 6000L, 6000L);
    }

    public void shutdown() {
        for (Profile pr : profiles.values()) {
            store.saveNow(pr);
        }
        store.shutdown();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        try {
            zone = ZoneId.of(cfg.getString("timezone", "Asia/Jakarta"));
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Jakarta");
        }
        loadRanks();
        loadClasses();
        loadQuests();
        loadMissions();
        loadWeapons();
        menus.load();
        plugin.getLogger().info(ranks.size() + " rank, " + classes.size() + " ras, "
                + quests.size() + " quest, " + missions.size() + " misi dimuat.");
    }

    private YamlConfiguration file(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    private static boolean hex(String c) {
        return c != null && c.matches("#[0-9A-Fa-f]{6}");
    }

    /** Memuat ranks.yml (F sampai S). Maksimal 7 rank; rank di atas S ditolak. */
    private void loadRanks() {
        ranks.clear();
        YamlConfiguration y = file("ranks.yml");
        ConfigurationSection rs = y.getConfigurationSection("ranks");
        if (rs == null) {
            plugin.getLogger().severe("ranks.yml tidak berisi bagian 'ranks'.");
            return;
        }
        int i = 0;
        for (String key : rs.getKeys(false)) {
            ConfigurationSection r = rs.getConfigurationSection(key);
            if (r == null) {
                continue;
            }
            String k = key.toUpperCase(Locale.ROOT);
            if (!k.matches("[FEDCBAS]") || i >= RANK_ORDER.length() || k.charAt(0) != RANK_ORDER.charAt(i)) {
                plugin.getLogger().warning("ranks.yml: rank '" + key + "' dilewati. Hanya F, E, D, C, B, A, S (urut).");
                continue;
            }
            Objective exam = null;
            String desc = "";
            int minutes = 0;
            ConfigurationSection ex = r.getConfigurationSection("exam");
            if (ex != null) {
                String target = ex.getString("entity", ex.getString("block", ex.getString("item", "")));
                exam = Objective.parse(ex.getString("type", "NONE"), target, ex.getInt("amount", 1));
                desc = ex.getString("description", exam.describe());
                minutes = ex.getInt("minutes", 0);
            }
            String color = r.getString("color", "#FFFFFF");
            if (!hex(color)) {
                plugin.getLogger().warning("ranks.yml: warna rank " + k + " bukan #RRGGBB, dipakai #FFFFFF.");
                color = "#FFFFFF";
            }
            String sec = r.getString("secondary-color", "");
            if (!sec.isEmpty() && !hex(sec)) {
                sec = "";
            }
            ConfigurationSection rq = r.getConfigurationSection("requirements");
            ranks.add(new Defs.Rank(i++, k, r.getString("name", "Rank " + k), color.toUpperCase(Locale.ROOT),
                    sec.toUpperCase(Locale.ROOT), r.getString("legacy-color", "&f"), r.getString("rarity", ""),
                    r.getBoolean("bold", false), rq == null ? 0 : rq.getInt("gp", 0), exam, desc, minutes,
                    r.getStringList("commands"), r.getBoolean("broadcast", false),
                    rq == null ? 0 : rq.getInt("min-level", 0), rq == null ? 0 : rq.getLong("min-exp", 0),
                    rq == null ? 0 : rq.getInt("min-quests", 0), rq == null ? 0 : rq.getInt("story-missions", 0),
                    rq == null ? List.of() : rq.getStringList("quest-ids"), rq == null ? 0 : rq.getInt("min-kills", 0),
                    rq == null ? "" : rq.getString("permission", "")));
        }
        if (ranks.isEmpty()) {
            plugin.getLogger().severe("ranks.yml kosong atau tidak valid: rank tidak tersedia!");
        }
    }

    public static final String RANK_ORDER = "FEDCBAS";

    private static String str(Map<?, ?> m, String k, String d) {
        Object o = m.get(k);
        return o == null ? d : String.valueOf(o);
    }

    private static double num(Map<?, ?> m, String k, double d) {
        Object o = m.get(k);
        if (o == null) {
            return d;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(String.valueOf(x));
            }
        }
        return out;
    }

    private static Material mat(String s, Material def) {
        Material m = s == null ? null : Material.matchMaterial(s);
        return m == null ? def : m;
    }

    private void loadClasses() {
        classes.clear();
        ConfigurationSection cs = file("races.yml").getConfigurationSection("races");
        if (cs == null) {
            return;
        }
        for (String key : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(key);
            if (c == null) {
                continue;
            }
            List<Defs.Attr> attrs = new ArrayList<>();
            ConfigurationSection as = c.getConfigurationSection("attributes");
            if (as != null) {
                for (String k : as.getKeys(false)) {
                    ConfigurationSection a = as.getConfigurationSection(k);
                    if (a != null) {
                        attrs.add(new Defs.Attr(k, "SCALAR".equalsIgnoreCase(a.getString("operation", "ADD")),
                                a.getDouble("amount")));
                    }
                }
            }
            Map<String, Double> passives = new HashMap<>();
            ConfigurationSection ps = c.getConfigurationSection("passives");
            if (ps != null) {
                for (String k : ps.getKeys(false)) {
                    passives.put(k, ps.getDouble(k));
                }
            }
            List<Defs.Skill> skills = new ArrayList<>();
            for (Map<?, ?> m : c.getMapList("skills")) {
                skills.add(new Defs.Skill(str(m, "type", "cleave").toLowerCase(Locale.ROOT), str(m, "name", "Skill"),
                        (int) num(m, "level", 1), (int) num(m, "mana", 10), num(m, "cooldown", 5),
                        num(m, "power", 5), str(m, "description", "")));
            }
            Map<String, Defs.Ability> abilities = new LinkedHashMap<>();
            for (Map<?, ?> m : c.getMapList("abilities")) {
                String aid = str(m, "id", "").toLowerCase(Locale.ROOT);
                if (aid.isEmpty()) {
                    continue;
                }
                Map<String, Double> vals = new HashMap<>();
                if (m.get("values") instanceof Map<?, ?> vm) {
                    for (Map.Entry<?, ?> en : vm.entrySet()) {
                        if (en.getValue() instanceof Number n) {
                            vals.put(String.valueOf(en.getKey()), n.doubleValue());
                        }
                    }
                }
                abilities.put(aid, new Defs.Ability(aid, str(m, "name", aid), str(m, "desc", ""),
                        Boolean.TRUE.equals(m.get("weakness")), vals));
            }
            classes.put(key.toLowerCase(Locale.ROOT), new Defs.Race(key.toLowerCase(Locale.ROOT),
                    c.getString("name", key), mat(c.getString("icon"), Material.IRON_SWORD),
                    c.getStringList("description"), attrs, passives, c.getStringList("buff-lore"), skills, abilities));
        }
    }

    public final Map<String, Defs.QuestCat> questCats = new LinkedHashMap<>();

    private void loadQuests() {
        quests.clear();
        questCats.clear();
        YamlConfiguration qy = file("quests.yml");
        ConfigurationSection cs = qy.getConfigurationSection("categories");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(k);
                if (c != null) {
                    questCats.put(k.toUpperCase(Locale.ROOT), new Defs.QuestCat(k.toUpperCase(Locale.ROOT),
                            c.getString("name", k), mat(c.getString("icon"), Material.PAPER), c.getString("description", "")));
                }
            }
        }
        FileConfiguration cfg = plugin.getConfig();
        double mul = cfg.getDouble("quests.reward-multiplier", 1.0);
        ConfigurationSection qs = qy.getConfigurationSection("quests");
        if (qs != null) {
            for (String id : qs.getKeys(false)) {
                ConfigurationSection q = qs.getConfigurationSection(id);
                if (q == null) {
                    continue;
                }
                int rank = rankIndex(q.getString("tier", ""));
                if (rank < 0) {
                    plugin.getLogger().warning("Quest " + id + " dilewati: tier tidak dikenal.");
                    continue;
                }
                String cat = q.getString("category", "").toUpperCase(Locale.ROOT);
                if (!questCats.containsKey(cat)) {
                    plugin.getLogger().warning("Quest " + id + " dilewati: kategori tidak dikenal (" + cat + ").");
                    continue;
                }
                Objective o = Objective.parse(q.getString("type", "KILL"), q.getString("target", ""),
                        q.getInt("amount", 1), q.getString("cond", ""));
                if (o.type() == Objective.Type.NONE) {
                    plugin.getLogger().warning("Quest " + id + " dilewati: tipe tugas tidak dikenal.");
                    continue;
                }
                validateTargets(id, o);
                String tk = RANK_ORDER.substring(rank, rank + 1);
                double w = q.getDouble("weight", 1.0);
                ConfigurationSection tier = cfg.getConfigurationSection("quests.tiers." + tk);
                long exp = Math.round((tier == null ? 50 : tier.getDouble("exp", 50)) * w * mul);
                int nr = (int) Math.round((tier == null ? 30 : tier.getDouble("nr", 30)) * w * mul);
                int gp = (int) Math.round((tier == null ? 5 : tier.getDouble("gp", 5)) * w * mul);
                quests.put(id.toLowerCase(Locale.ROOT), new Defs.Quest(id.toLowerCase(Locale.ROOT), rank, false,
                        q.getString("name", id), mat(q.getString("icon"), questCats.get(cat).icon()), o, gp,
                        List.of(), List.of(), cat, q.getString("description", ""), nr, exp, false));
            }
        }
        loadLegacyQuests();
    }

    /** Peringatkan di console jika target quest tidak dikenal (quest seperti itu tidak akan pernah maju). */
    private void validateTargets(String id, Objective o) {
        for (String t : o.targets()) {
            if (t.contains("*")) {
                continue;
            }
            String u = t.toUpperCase(Locale.ROOT);
            boolean ok = switch (o.type()) {
                case BREAK, GET, HARVEST, PLANT, PLACE, CRAFT, SMELT, SMITH, SELL, EAT -> Material.getMaterial(u) != null;
                case KILL, BREED, TAME, SHEAR, MILK -> {
                    try {
                        org.bukkit.entity.EntityType.valueOf(u);
                        yield true;
                    } catch (IllegalArgumentException e) {
                        yield false;
                    }
                }
                case STAT -> {
                    try {
                        org.bukkit.Statistic.valueOf(u);
                        yield true;
                    } catch (IllegalArgumentException e) {
                        yield false;
                    }
                }
                default -> true;
            };
            if (!ok) {
                plugin.getLogger().warning("Quest " + id + ": target '" + t + "' tidak dikenal (cek ejaan di quests.yml).");
            }
        }
    }

    /** Quest lama (quests-legacy.yml) hanya dimuat supaya quest aktif pemain tetap bisa diselesaikan. */
    private void loadLegacyQuests() {
        File lf = new File(plugin.getDataFolder(), "quests-legacy.yml");
        if (!lf.isFile()) {
            return;
        }
        ConfigurationSection qs = YamlConfiguration.loadConfiguration(lf).getConfigurationSection("quests");
        if (qs == null) {
            return;
        }
        for (String id : qs.getKeys(false)) {
            ConfigurationSection q = qs.getConfigurationSection(id);
            if (q == null || quests.containsKey(id.toLowerCase(Locale.ROOT)) || quests.containsKey(id)) {
                continue;
            }
            int rank = rankIndex(q.getString("rank", ""));
            if (rank < 0) {
                continue;
            }
            double gpMul = plugin.getConfig().getDouble("quests.gp-multiplier", 1.0);
            double amtMul = plugin.getConfig().getDouble("quests.amount-multiplier", 1.0);
            Objective o = Objective.parse(q.getString("type", "KILL"), q.getString("target", ""),
                    (int) Math.round(q.getInt("amount", 1) * amtMul));
            long exp = Math.round(plugin.getConfig().getInt("level.xp-quest-base", 40) * (rank + 1)
                    * (q.getBoolean("challenge", false) ? 1.5 : 1.0));
            quests.put(id, new Defs.Quest(id, rank, q.getBoolean("challenge", false), q.getString("name", id),
                    mat(q.getString("icon"), Material.PAPER), o, (int) Math.round(q.getInt("gp", 0) * gpMul),
                    q.getStringList("commands"), q.getStringList("reward-lore"), "LEGACY", "", 0, exp, true));
        }
    }

    private void loadMissions() {
        missions.clear();
        for (Map<?, ?> m : file("missions.yml").getMapList("missions")) {
            int minRank = rankIndex(str(m, "min-rank", ""));
            missions.add(new Defs.Mission(str(m, "id", "m" + missions.size()), str(m, "title", "Misi"),
                    strList(m.get("story")), Math.max(0, minRank),
                    Objective.parse(str(m, "type", "KILL"), str(m, "target", ""), (int) num(m, "amount", 1)),
                    (int) num(m, "xp", 0), (int) Math.round(num(m, "gp", 0) * plugin.getConfig().getDouble("quests.gp-multiplier", 1.0)),
                    strList(m.get("commands"))));
        }
    }

    private void loadWeapons() {
        weapons.clear();
        File f = new File(plugin.getDataFolder(), "weapons.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String k : y.getKeys(false)) {
            weapons.put(k.toLowerCase(Locale.ROOT), new ArrayList<>(y.getStringList(k)));
        }
    }

    public void saveWeapons() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, List<String>> e : weapons.entrySet()) {
            y.set(e.getKey(), e.getValue());
        }
        try {
            y.save(new File(plugin.getDataFolder(), "weapons.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan weapons.yml: " + e.getMessage());
        }
    }

    public List<String> weaponList(String cls) {
        return weapons.computeIfAbsent(cls.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
    }

    // ------------------------------------------------------------ data pemain

    public Profile profile(Player p) {
        Profile have = profiles.get(p.getUniqueId());
        if (have != null) {
            return have;
        }
        UUID id = p.getUniqueId();
        Profile pr = store.takePreloaded(id);
        if (pr == null) {
            pr = store.read(id);
        }
        if (pr == null) {
            pr = new Profile(id);
        }
        pr.name = p.getName();
        profiles.put(id, pr);
        return pr;
    }

    public Profile loaded(UUID id) {
        return profiles.get(id);
    }

    public void saveAll() {
        for (Profile pr : profiles.values()) {
            store.saveAsync(pr);
        }
    }

    public void save(Profile pr) {
        store.saveAsync(pr);
    }

    public void unload(Player p) {
        Profile pr = profiles.remove(p.getUniqueId());
        if (pr != null) {
            store.saveAsync(pr);
        }
    }

    /** Pemain adalah anggota aktif Nerus Guild (REGISTERED). */
    public boolean member(Player p) {
        return profile(p).member();
    }

    /** Indeks rank efektif: -1 untuk non-anggota (tanpa rank). */
    public int effRank(Profile pr) {
        return pr.member() ? pr.rank : -1;
    }

    /** Rank untuk tampilan (null = NONE) bagi anggota aktif. */
    public Defs.Rank activeRank(Profile pr) {
        return pr.member() ? rank(pr.rank) : null;
    }

    public String today() {
        return LocalDate.now(zone).toString();
    }

    /** Catat satu penyelesaian ke riwayat pemain (maksimal 50 terakhir). */
    public void logHistory(Profile pr, String kind, String name) {
        pr.history.add(System.currentTimeMillis() + "|" + kind + "|" + name);
        while (pr.history.size() > 50) {
            pr.history.remove(0);
        }
    }

    /** Hanya statistik harian yang direset. Quest aktif dan progresnya TIDAK dihapus. */
    public void checkDay(Profile pr) {
        String t = today();
        if (!t.equals(pr.day)) {
            pr.day = t;
            pr.done.clear();
            pr.completedToday = 0;
        }
    }

    // ------------------------------------------------------------ cooldown per ID quest

    public String categoryName(String cat) {
        Defs.QuestCat qc = questCats.get(cat);
        if (qc != null) {
            return qc.name();
        }
        String s = cat.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? cat : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Cooldown ambil ulang satu quest (ms): quests.cooldown-hours (5 jam), bisa per ID lewat cooldown-hours-by-id. */
    public long cooldownMillis(String questId) {
        double d = plugin.getConfig().getDouble("quests.cooldown-hours-by-id." + questId,
                plugin.getConfig().getDouble("quests.cooldown-hours", 5.0));
        return d <= 0 ? 0 : (long) (d * 3_600_000L);
    }

    /** Sisa cooldown (ms) quest ini untuk pemain; 0 bila siap diambil. */
    public long cooldownLeft(Profile pr, String questId) {
        Long until = pr.questCooldowns.get(questId);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    // ------------------------------------------------------------ pesan

    public void msg(CommandSender to, String text) {
        to.sendMessage(Text.c(Text.PREFIX + text));
    }

    public void runConsole(Player p, List<String> cmds, String... kv) {
        for (String cmd : cmds) {
            String s = cmd.replace("%player%", p.getName());
            for (int i = 0; i + 1 < kv.length; i += 2) {
                s = s.replace(kv[i], kv[i + 1]);
            }
            if (s.startsWith("/")) {
                s = s.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s);
        }
    }

    // ------------------------------------------------------------ join

    public void handleJoin(Player p, boolean fresh) {
        Profile pr = profile(p);
        checkDay(pr);
        if (pr.mana < 0) {
            pr.mana = maxMana(pr);
        }
        migrateRace(pr);
        applyBuffs(p);
        if (pr.raceNotice && pr.member()) {
            pr.raceNotice = false;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    msg(p, "&6Sistem Class diganti &lSistem Ras&6. Class lamamu otomatis jadi ras "
                            + (clazz(pr) == null ? "-" : clazz(pr).name()) + "&6. Level, EXP, dan stat tetap utuh. "
                            + "Kamu boleh memilih ras lain sekali tanpa cooldown lewat &f/race&6.");
                }
            }, 100L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                syncRank(p, "");
            }
        }, 40L);
        if (!fresh) {
            return;
        }
        if (pr.member()) {
            dailyLogin(p, pr);
        }
        FileConfiguration c = plugin.getConfig();
        if (!pr.introSeen && c.getBoolean("intro.enabled", true)) {
            pr.introSeen = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> intro(p), 40L);
        } else if (pr.member() && pr.cls == null && c.getBoolean("intro.force-class-choice", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> new Menus.ClassMenu(this, p).open(), 60L);
        }
    }

    private void intro(Player p) {
        if (!p.isOnline()) {
            return;
        }
        FileConfiguration c = plugin.getConfig();
        p.showTitle(Title.title(Text.c(c.getString("intro.title", "")), Text.c(c.getString("intro.subtitle", ""))));
        List<String> lines = c.getStringList("intro.lines");
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.sendMessage(Text.c(line));
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.6f);
                }
            }, 40L * (i + 1));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Profile pr = profile(p);
            if (!pr.member()) {
                p.sendMessage(Text.c(guild.msgs.get("intro-unregistered")));
            } else if (pr.cls == null && c.getBoolean("intro.force-class-choice", true)) {
                new Menus.ClassMenu(this, p).open();
            }
        }, 40L * (lines.size() + 2));
    }

    private void dailyLogin(Player p, Profile pr) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("daily-login.enabled", true) || pr.lastLogin.equals(today())) {
            return;
        }
        String yesterday = LocalDate.now(zone).minusDays(1).toString();
        pr.streak = pr.lastLogin.equals(yesterday) ? pr.streak + 1 : 1;
        pr.lastLogin = today();
        int day = ((pr.streak - 1) % 7) + 1;
        List<String> cmds = c.getStringList("daily-login.rewards." + day);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                runConsole(p, cmds);
                msg(p, "&aLogin harian hari ke-&f" + day + "&a (beruntun &f" + pr.streak + "&a hari). Hadiah diterima!");
            }
        }, 100L);
    }

    // ------------------------------------------------------------ level, mana, buff

    public long xpNeeded(int level) {
        FileConfiguration c = plugin.getConfig();
        return (long) (c.getInt("level.xp-base", 100) + (double) level * level * c.getDouble("level.xp-per-level-squared", 25));
    }

    public double maxMana(Profile pr) {
        FileConfiguration c = plugin.getConfig();
        return c.getDouble("mana.base", 50) + Math.max(0, pr.level - 1) * c.getDouble("mana.per-level", 2)
                + pr.stat("int") * c.getDouble("stats.int-mana-per-point", 5);
    }

    public void addXp(Player p, long amount) {
        Profile pr = profile(p);
        FileConfiguration c = plugin.getConfig();
        int max = c.getInt("level.max", 100);
        if (amount <= 0 || !pr.member() || pr.level >= max) {
            return;
        }
        Defs.Ability ql = ability(p, "quick_learner");
        if (ql != null) {
            amount = Math.round(amount * (1 + ql.v("exp_percent", 8) / 100.0));
        }
        pr.xp += amount;
        pr.totalXp += amount;
        boolean up = false;
        while (pr.level < max && pr.xp >= xpNeeded(pr.level)) {
            pr.xp -= xpNeeded(pr.level);
            pr.level++;
            pr.statPoints += c.getInt("level.stat-points-per-level", 3);
            up = true;
        }
        if (pr.level >= max) {
            pr.xp = 0;
        }
        if (up) {
            pr.mana = maxMana(pr);
            applyBuffs(p);
            p.showTitle(Title.title(Text.c("&6&lLEVEL UP!"), Text.c("&eLevel &f" + pr.level)));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            msg(p, "&6Naik ke level &f" + pr.level + "&6! Kamu dapat &f" + pr.statPoints + " &6poin stat (/stats).");
        }
    }

    public void addGp(Player p, int amount) {
        profile(p).gp = Math.max(0, profile(p).gp + amount);
    }

    public Defs.Race clazz(Profile pr) {
        return pr.cls == null ? null : classes.get(pr.cls);
    }

    public Defs.Race race(Profile pr) {
        return clazz(pr);
    }

    /** Kemampuan ras pemain (null jika bukan anggota guild atau rasnya tidak punya kemampuan itu). */
    public Defs.Ability ability(Player p, String id) {
        Profile pr = profiles.get(p.getUniqueId());
        if (pr == null || !pr.member()) {
            return null;
        }
        Defs.Race r = clazz(pr);
        return r == null ? null : r.abilities().get(id);
    }

    /** Class lama (warrior/assassin/archer) dipetakan ke ras. Data class asli disimpan di legacyClass. */
    public void migrateRace(Profile pr) {
        if (pr.cls == null || classes.containsKey(pr.cls)) {
            return;
        }
        String legacy = pr.cls;
        String to = plugin.getConfig().getString("races.class-migration." + legacy, "");
        if (to.isEmpty()) {
            to = switch (legacy) {
                case "warrior" -> "orc";
                case "assassin" -> "dark_elf";
                case "archer" -> "elf";
                default -> "human";
            };
        }
        if (!classes.containsKey(to)) {
            return;
        }
        pr.legacyClass = legacy;
        pr.cls = to;
        pr.raceNotice = true;
        pr.classChangedAt = 0; // boleh memilih ulang gratis sekali
        plugin.getLogger().info("Migrasi: " + pr.name + " class " + legacy + " -> ras " + to + " (class asli disimpan)");
    }

    public void applyBuffs(Player p) {
        Profile pr = profile(p);
        String ns = new NamespacedKey(plugin, "x").getNamespace();
        for (Attribute a : Registry.ATTRIBUTE) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) {
                continue;
            }
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                // "nerusrpg" = namespace plugin lama (NerusRPG); modifier-nya tersimpan di data pemain dan harus dibersihkan
                String mns = m.getKey().getNamespace();
                if (mns.equals(ns) || mns.equals("nerusrpg")) {
                    inst.removeModifier(m);
                }
            }
        }
        Defs.Race c = pr.member() ? clazz(pr) : null;
        if (c != null) {
            for (Defs.Attr at : c.attrs()) {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(at.name().toLowerCase(Locale.ROOT)));
                AttributeInstance inst = a == null ? null : p.getAttribute(a);
                if (inst == null) {
                    plugin.getLogger().warning("Atribut tidak dikenal untuk class " + c.key() + ": " + at.name());
                    continue;
                }
                inst.addModifier(new AttributeModifier(new NamespacedKey(plugin, "cls_" + at.name().toLowerCase(Locale.ROOT)),
                        at.amount(), at.scalar() ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        if (pr.member()) {
            var sc = plugin.getConfig();
            double agi = Math.min(sc.getDouble("stats.agi-speed-cap-percent", 20), pr.stat("agi") * sc.getDouble("stats.agi-speed-percent", 0.2)) / 100.0;
            AttributeInstance spd = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null && agi > 0) {
                spd.addModifier(new AttributeModifier(new NamespacedKey(plugin, "stat_agi"), agi, AttributeModifier.Operation.ADD_SCALAR));
            }
            AttributeInstance lk = p.getAttribute(Attribute.LUCK);
            double lkv = Math.min(sc.getDouble("stats.luk-luck-cap", 3.0), pr.stat("luk") * sc.getDouble("stats.luk-luck-per-point", 0.05));
            if (lk != null && lkv > 0) {
                lk.addModifier(new AttributeModifier(new NamespacedKey(plugin, "stat_luk"), lkv, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double vit = !pr.member() ? 0 : Math.min(plugin.getConfig().getDouble("stats.vit-health-cap", 40),
                    pr.stat("vit") * plugin.getConfig().getDouble("stats.vit-health-per-point", 1.0));
            if (vit != 0) {
                hp.addModifier(new AttributeModifier(new NamespacedKey(plugin, "stat_vit"), vit,
                        AttributeModifier.Operation.ADD_NUMBER));
            }
            if (p.getHealth() > hp.getValue()) {
                p.setHealth(hp.getValue());
            }
        }
    }

    public void setClass(Player p, String key) {
        Profile pr = profile(p);
        pr.cls = key;
        pr.classChangedAt = System.currentTimeMillis();
        pr.skillSel = 0;
        pr.cooldowns.clear();
        pr.mana = maxMana(pr);
        applyBuffs(p);
    }

    // ------------------------------------------------------------ senjata

    public static String plainName(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) {
            Component d = m.displayName();
            if (d != null) {
                return PlainTextComponentSerializer.plainText().serialize(d);
            }
        }
        return "";
    }

    /** Senjata class kosong berarti semua item diterima. */
    public boolean weaponOk(Player p, Defs.Race c) {
        List<String> list = weapons.get(c.key());
        if (list == null || list.isEmpty()) {
            return true;
        }
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir()) {
            return false;
        }
        String name = plainName(it);
        for (String e : list) {
            String[] parts = e.split("\\|", 2);
            if (!it.getType().name().equalsIgnoreCase(parts[0])) {
                continue;
            }
            if (parts.length < 2 || parts[1].isEmpty() || parts[1].equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ rank dan ujian

    public Defs.Rank rank(int i) {
        return i >= 0 && i < ranks.size() ? ranks.get(i) : null;
    }

    /** Label rank; untuk Bedrock memakai warna cadangan (legacy) jika colors.bedrock-fallback aktif. */
    public String label(Defs.Rank r, Player viewer) {
        if (r == null) {
            return plugin.getConfig().getString("ranks-display.none", "&7NONE");
        }
        if (viewer != null && plugin.getConfig().getBoolean("colors.bedrock-fallback", true) && Platform.isBedrock(viewer)) {
            return r.legacyLabel();
        }
        return r.label();
    }

    public String tag(Defs.Rank r, Player viewer) {
        if (r == null) {
            return plugin.getConfig().getString("ranks-display.none", "&7NONE");
        }
        if (viewer != null && plugin.getConfig().getBoolean("colors.bedrock-fallback", true) && Platform.isBedrock(viewer)) {
            return r.legacyTag();
        }
        return r.tag();
    }

    public int rankIndex(String key) {
        if (key != null && (key.equalsIgnoreCase("SS") || key.equalsIgnoreCase("SSS"))) {
            key = "S"; // kompatibilitas data lama: tidak ada rank di atas S
        }
        for (Defs.Rank r : ranks) {
            if (r.key().equalsIgnoreCase(key)) {
                return r.index();
            }
        }
        return -1;
    }

    public void syncRank(Player p, String oldKey) {
        Profile pr = profile(p);
        Defs.Rank r = activeRank(pr);
        runConsole(p, plugin.getConfig().getStringList("sync-commands"), "%rank%", r == null ? "NONE" : r.key(),
                "%old_rank%", oldKey);
        updateDisplay(p);
    }

    public void updateDisplay(Player p) {
        if (display != null) {
            display.refresh(p);
        }
    }

    public void setRank(Player p, int idx) {
        Profile pr = profile(p);
        String old = pr.member() && rank(pr.rank) != null ? rank(pr.rank).key() : "";
        applyRank(pr, idx, "ADMIN");
        syncRank(p, old);
        tracker.rankChanged(p);
    }

    /** Ubah rank di data (tanpa efek pemain). Mencatat riwayat. */
    public void applyRank(Profile pr, int idx, String reason) {
        Defs.Rank from = rank(pr.rank);
        pr.rank = Math.max(0, Math.min(ranks.size() - 1, idx));
        pr.examTarget = -1;
        pr.examProgress = 0;
        pr.examDeadline = 0;
        logRank(pr, from == null ? "NONE" : from.key(), rank(pr.rank) == null ? "?" : rank(pr.rank).key(), reason);
    }

    public void logRank(Profile pr, String from, String to, String reason) {
        pr.rankHistory.add(System.currentTimeMillis() + "|" + from + "|" + to + "|" + reason);
        while (pr.rankHistory.size() > 100) {
            pr.rankHistory.remove(0);
        }
    }

    /** Daftar syarat naik ke rank {@code next} yang belum terpenuhi (kosong = boleh). */
    public List<String> unmet(Player p, Profile pr, Defs.Rank next) {
        List<String> out = new ArrayList<>();
        if (pr.level < next.minLevel()) {
            out.add("Level " + next.minLevel() + " (kamu " + pr.level + ")");
        }
        if (pr.totalXp < next.minExp()) {
            out.add("Total EXP " + next.minExp() + " (kamu " + pr.totalXp + ")");
        }
        if (pr.totalQuests < next.minQuests()) {
            out.add("Quest selesai " + next.minQuests() + " (kamu " + pr.totalQuests + ")");
        }
        if (pr.story < next.storyMissions()) {
            out.add("Misi cerita " + next.storyMissions() + " (kamu " + pr.story + ")");
        }
        for (String q : next.questIds()) {
            if (!pr.completedQuestIds.contains(q)) {
                Defs.Quest dq = quests.get(q);
                out.add("Selesaikan quest: " + Text.strip(dq == null ? q : dq.name()));
            }
        }
        if (pr.totalKills < next.minKills()) {
            out.add("Bunuh monster " + next.minKills() + " (kamu " + pr.totalKills + ")");
        }
        if (pr.gp < next.gp()) {
            out.add("GP " + next.gp() + " (kamu " + pr.gp + ")");
        }
        if (!next.permission().isEmpty() && (p == null || !p.hasPermission(next.permission()))) {
            out.add("Izin khusus (" + next.permission() + ")");
        }
        return out;
    }

    public void startExam(Player p) {
        Profile pr = profile(p);
        Defs.Rank next = rank(pr.rank + 1);
        long now = System.currentTimeMillis();
        if (next == null) {
            msg(p, "&6Kamu sudah di rank tertinggi.");
        } else if (pr.examTarget >= 0) {
            msg(p, "&cKamu masih punya ujian yang berjalan.");
        } else if (pr.examCooldown > now) {
            msg(p, "&cUjian bisa diulang dalam &f" + Text.time(pr.examCooldown - now) + "&c.");
        } else if (!pr.member()) {
            msg(p, "&cDaftar dulu di Nerus Guild lewat Caylin.");
        } else if (!unmet(p, pr, next).isEmpty()) {
            msg(p, "&cSyarat " + next.label() + "&c belum terpenuhi:");
            for (String u : unmet(p, pr, next)) {
                p.sendMessage(Text.c("&8 - &7" + u));
            }
        } else if (next.exam() == null || next.exam().type() == Objective.Type.NONE) {
            promote(p);
        } else {
            pr.examTarget = next.index();
            pr.examProgress = 0;
            pr.examDeadline = next.minutes() > 0 ? now + next.minutes() * 60_000L : 0;
            msg(p, "&aUjian " + next.label() + "&a dimulai! &f" + next.desc());
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    public boolean cancelExam(Player p) {
        Profile pr = profile(p);
        if (pr.examTarget < 0) {
            return false;
        }
        pr.examTarget = -1;
        pr.examProgress = 0;
        pr.examDeadline = 0;
        return true;
    }

    public void failExam(Player p, String reason) {
        Profile pr = profile(p);
        if (pr.examTarget < 0) {
            return;
        }
        cancelExam(p);
        pr.examCooldown = System.currentTimeMillis() + plugin.getConfig().getLong("exam.cooldown-minutes", 10) * 60_000L;
        msg(p, reason);
    }

    public boolean completeExam(Player p) {
        Profile pr = profile(p);
        if (pr.examTarget < 0) {
            return false;
        }
        promote(p);
        return true;
    }

    public void promote(Player p) {
        Profile pr = profile(p);
        Defs.Rank next = rank(pr.rank + 1);
        if (next == null) {
            return;
        }
        if (!pr.member() || !unmet(p, pr, next).isEmpty()) {
            return;
        }
        String old = rank(pr.rank).key();
        applyRank(pr, next.index(), "PROMOTE");
        syncRank(p, old);
        runConsole(p, next.commands());
        runConsole(p, plugin.getConfig().getStringList("promote-commands"), "%rank%", next.key(), "%old_rank%", old);
        p.showTitle(Title.title(Text.c("&6Naik Rank!"), Text.c(label(next, p))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        msg(p, "&aSelamat! Kamu naik ke " + label(next, p) + "&a!");
        if (next.broadcast()) {
            Bukkit.broadcast(Text.c("&6" + p.getName() + " &etelah naik ke " + next.label() + "&e!"));
        }
        tracker.rankChanged(p);
    }

    // ------------------------------------------------------------ hadiah

    public void reward(Player p, int gp, long xp, List<String> cmds) {
        Profile pr = profile(p);
        double luck = pr.stat("luk") * plugin.getConfig().getDouble("stats.luk-reward-chance-percent", 0.5);
        if (luck > 0 && rng.nextDouble() * 100 < luck) {
            gp = (int) Math.round(gp * 1.5);
            xp = Math.round(xp * 1.5);
            msg(p, "&6Keberuntungan! Hadiah x1.5");
        }
        if (gp > 0) {
            pr.gp += gp;
        }
        if (xp > 0) {
            addXp(p, xp);
        }
        runConsole(p, cmds);
        msg(p, "&7Hadiah: &b+" + gp + " GP &7| &a+" + xp + " XP" + (cmds.isEmpty() ? "" : " &7| &6item/gold"));
    }

    /** Hadiah quest: EXP, Guild Point, dan Nerith (lewat perintah ekonomi) atau perintah lama untuk quest lama. */
    public void rewardQuest(Player p, Defs.Quest q) {
        Profile pr = profile(p);
        int gp = q.gp();
        long xp = q.exp();
        int nr = q.nr();
        double luck = pr.stat("luk") * plugin.getConfig().getDouble("stats.luk-reward-chance-percent", 0.5);
        boolean lucky = luck > 0 && rng.nextDouble() * 100 < luck;
        if (lucky) {
            gp = (int) Math.round(gp * 1.5);
            xp = Math.round(xp * 1.5);
            nr = (int) Math.round(nr * 1.5);
            msg(p, "&6Keberuntungan! Hadiah x1.5");
        }
        Defs.Ability st = ability(p, "silver_tongue");
        if (st != null && nr > 0) {
            nr = (int) Math.round(nr * (1 + st.v("nr_percent", 10) / 100.0));
        }
        if (gp > 0) {
            pr.gp += gp;
        }
        if (xp > 0) {
            addXp(p, xp);
        }
        if (nr > 0) {
            runConsole(p, List.of(plugin.getConfig().getString("quests.nr-command", "eco give %player% %amount%")),
                    "%amount%", String.valueOf(nr));
        }
        if (!q.commands().isEmpty()) {
            runConsole(p, q.commands());
        }
        msg(p, "&7Hadiah: &a+" + xp + " EXP &8| &6+" + nr + " NR &8| &b+" + gp + " GP"
                + (q.commands().isEmpty() ? "" : " &8| &7item"));
    }

    // ------------------------------------------------------------ tick

    private static final String DEF_HUD = "&cHP &f%hp% &8| &9Mana &f%mana%&7/&f%maxmana% &8| &6Lv &f%level% &8| &e%skill% &7%cd%";
    private static final String DEF_EXAM = "&6Ujian %exam% &8| &f%exam_progress% &8| &c%exam_time%";

    /** Isi placeholder HUD action bar. */
    private String hudText(Player p, Profile pr, String fmt) {
        Defs.Race cl = clazz(pr);
        String skill = "-";
        String cd = "";
        if (cl != null && !cl.skills().isEmpty()) {
            int idx = Math.min(pr.skillSel, cl.skills().size() - 1);
            Defs.Skill s = cl.skills().get(idx);
            skill = pr.level >= s.level() ? s.name() : s.name() + " &c(Lv " + s.level() + ")";
            Long until = pr.cooldowns.get(s.type());
            long left = until == null ? 0 : until - System.currentTimeMillis();
            cd = left > 0 ? "&c" + Text.time(left) : "&aSiap";
        }
        var hp = p.getAttribute(Attribute.MAX_HEALTH);
        Defs.Rank r = activeRank(pr);
        String examRank = "";
        String examProg = "";
        String examTime = "";
        Defs.Rank t = rank(pr.examTarget);
        if (t != null && t.exam() != null) {
            examRank = t.tag();
            examProg = t.exam().type() == Objective.Type.MANUAL ? t.desc() : pr.examProgress + "&7/&f" + t.exam().amount();
            examTime = pr.examDeadline > 0 ? Text.time(pr.examDeadline - System.currentTimeMillis()) : "";
        }
        return fmt.replace("%hp%", String.valueOf((int) Math.ceil(p.getHealth())))
                .replace("%maxhp%", String.valueOf(hp == null ? 20 : (int) hp.getValue()))
                .replace("%mana%", String.valueOf((int) pr.mana))
                .replace("%maxmana%", String.valueOf((int) maxMana(pr)))
                .replace("%level%", String.valueOf(pr.level))
                .replace("%xp%", String.valueOf(pr.xp))
                .replace("%xpneed%", String.valueOf(xpNeeded(pr.level)))
                .replace("%gp%", String.valueOf(pr.gp))
                .replace("%class%", cl == null ? "-" : cl.name())
                .replace("%rank%", r == null ? "" : tag(r, p))
                .replace("%skill%", skill)
                .replace("%cd%", cd)
                .replace("%exam%", examRank)
                .replace("%exam_progress%", examProg)
                .replace("%exam_time%", examTime);
    }

    private void tick() {
        FileConfiguration c = plugin.getConfig();
        double regen = c.getDouble("mana.regen-per-second", 2.0);
        boolean hud = c.getBoolean("mana.actionbar", true);
        String hudFmt = c.getString("hud.format", DEF_HUD);
        String examFmt = c.getString("hud.exam-format", DEF_EXAM);
        boolean iaHud = c.getBoolean("hud.itemsadder", false) && IA.present();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile pr = profiles.get(p.getUniqueId());
            if (pr == null) {
                continue;
            }
            if (!pr.member()) {
                continue;
            }
            checkDay(pr);
            double max = maxMana(pr);
            pr.mana = Math.min(max, (pr.mana < 0 ? max : pr.mana) + regen);
            if (pr.examTarget >= 0 && pr.examDeadline > 0 && now > pr.examDeadline) {
                failExam(p, "&cWaktu ujian habis. Kamu gagal.");
            }
            if (pr.examTarget >= 0 && rank(pr.examTarget) != null && rank(pr.examTarget).exam() != null) {
                p.sendActionBar(Text.c(hudText(p, pr, examFmt)));
            } else if (hud && pr.cls != null) {
                String glyph = null;
                if (iaHud && !IA.bedrock(p)) {
                    var mh = p.getAttribute(Attribute.MAX_HEALTH);
                    int h = (int) Math.round(Math.min(1.0, p.getHealth() / (mh == null ? 20 : mh.getValue())) * 10);
                    int m = (int) Math.round(Math.min(1.0, pr.mana / Math.max(1, max)) * 10);
                    glyph = IA.image("nereus_ui:hud_" + h + "_" + m);
                }
                if (glyph != null) {
                    p.sendActionBar(Component.text(glyph).color(net.kyori.adventure.text.format.NamedTextColor.WHITE));
                } else {
                    p.sendActionBar(Text.c(hudText(p, pr, hudFmt)));
                }
            }
        }
    }
}
