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
    public final NereusRpg plugin;
    public final Tracker tracker;
    public final Random rng = new Random();

    private final Map<UUID, Profile> profiles = new HashMap<>();
    public final List<Defs.Rank> ranks = new ArrayList<>();
    public final Map<String, Defs.Quest> quests = new LinkedHashMap<>();
    public final List<Defs.Mission> missions = new ArrayList<>();
    public final Map<String, Defs.Clazz> classes = new LinkedHashMap<>();
    private final Map<String, List<String>> weapons = new HashMap<>();

    private File dataFile;
    private YamlConfiguration data;
    private ZoneId zone = ZoneId.of("Asia/Jakarta");

    public Core(NereusRpg plugin) {
        this.plugin = plugin;
        this.tracker = new Tracker(this);
    }

    // ------------------------------------------------------------ muat

    public void start() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        reload();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, 6000L, 6000L);
    }

    public void shutdown() {
        saveAll();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        try {
            zone = ZoneId.of(cfg.getString("timezone", "Asia/Jakarta"));
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Jakarta");
        }
        loadRanks(cfg);
        loadClasses();
        loadQuests();
        loadMissions();
        loadWeapons();
        plugin.getLogger().info(ranks.size() + " rank, " + classes.size() + " class, "
                + quests.size() + " quest, " + missions.size() + " misi dimuat.");
    }

    private YamlConfiguration file(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    private void loadRanks(FileConfiguration cfg) {
        ranks.clear();
        ConfigurationSection rs = cfg.getConfigurationSection("ranks");
        if (rs == null) {
            return;
        }
        int i = 0;
        for (String key : rs.getKeys(false)) {
            ConfigurationSection r = rs.getConfigurationSection(key);
            if (r == null) {
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
            ranks.add(new Defs.Rank(i++, key.toUpperCase(Locale.ROOT), r.getString("name", "Rank " + key),
                    r.getString("color", "&f"), r.getInt("gp", 0), exam, desc, minutes,
                    r.getStringList("commands"), r.getBoolean("broadcast", false)));
        }
    }

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
        ConfigurationSection cs = file("classes.yml").getConfigurationSection("classes");
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
            classes.put(key.toLowerCase(Locale.ROOT), new Defs.Clazz(key.toLowerCase(Locale.ROOT),
                    c.getString("name", key), mat(c.getString("icon"), Material.IRON_SWORD),
                    c.getStringList("description"), attrs, passives, c.getStringList("buff-lore"), skills));
        }
    }

    private void loadQuests() {
        quests.clear();
        ConfigurationSection qs = file("quests.yml").getConfigurationSection("quests");
        if (qs == null) {
            return;
        }
        for (String id : qs.getKeys(false)) {
            ConfigurationSection q = qs.getConfigurationSection(id);
            if (q == null) {
                continue;
            }
            int rank = rankIndex(q.getString("rank", ""));
            if (rank < 0) {
                plugin.getLogger().warning("Quest " + id + " dilewati: rank tidak dikenal.");
                continue;
            }
            Objective o = Objective.parse(q.getString("type", "KILL"), q.getString("target", ""), q.getInt("amount", 1));
            quests.put(id, new Defs.Quest(id, rank, q.getBoolean("challenge", false), q.getString("name", id),
                    mat(q.getString("icon"), Material.PAPER), o, q.getInt("gp", 0),
                    q.getStringList("commands"), q.getStringList("reward-lore")));
        }
    }

    private void loadMissions() {
        missions.clear();
        for (Map<?, ?> m : file("missions.yml").getMapList("missions")) {
            int minRank = rankIndex(str(m, "min-rank", ""));
            missions.add(new Defs.Mission(str(m, "id", "m" + missions.size()), str(m, "title", "Misi"),
                    strList(m.get("story")), Math.max(0, minRank),
                    Objective.parse(str(m, "type", "KILL"), str(m, "target", ""), (int) num(m, "amount", 1)),
                    (int) num(m, "xp", 0), (int) num(m, "gp", 0), strList(m.get("commands"))));
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
        return profiles.computeIfAbsent(p.getUniqueId(), id -> {
            Profile pr = new Profile(id);
            ConfigurationSection s = data.getConfigurationSection("players." + id);
            if (s != null) {
                pr.load(s);
            }
            pr.name = p.getName();
            return pr;
        });
    }

    public Profile loaded(UUID id) {
        return profiles.get(id);
    }

    private void save(Profile pr) {
        pr.save(data.createSection("players." + pr.id));
    }

    private void flush() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan players.yml: " + e.getMessage());
        }
    }

    public void saveAll() {
        for (Profile pr : profiles.values()) {
            save(pr);
        }
        flush();
    }

    public void unload(Player p) {
        Profile pr = profiles.remove(p.getUniqueId());
        if (pr != null) {
            save(pr);
            flush();
        }
    }

    public String today() {
        return LocalDate.now(zone).toString();
    }

    public void checkDay(Profile pr) {
        String t = today();
        if (!t.equals(pr.day)) {
            pr.day = t;
            pr.active.clear();
            pr.done.clear();
            pr.completedToday = 0;
        }
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
        applyBuffs(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                syncRank(p, rank(pr.rank) == null ? "" : rank(pr.rank).key());
            }
        }, 40L);
        if (!fresh) {
            return;
        }
        dailyLogin(p, pr);
        FileConfiguration c = plugin.getConfig();
        if (!pr.introSeen && c.getBoolean("intro.enabled", true)) {
            pr.introSeen = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> intro(p), 40L);
        } else if (pr.cls == null && c.getBoolean("intro.force-class-choice", true)) {
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
            if (p.isOnline() && profile(p).cls == null && c.getBoolean("intro.force-class-choice", true)) {
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
        return c.getDouble("mana.base", 50) + (pr.level - 1) * c.getDouble("mana.per-level", 2)
                + pr.stat("int") * c.getDouble("stats.int-mana-per-point", 5);
    }

    public void addXp(Player p, long amount) {
        Profile pr = profile(p);
        FileConfiguration c = plugin.getConfig();
        int max = c.getInt("level.max", 100);
        if (amount <= 0 || pr.level >= max) {
            return;
        }
        pr.xp += amount;
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

    public Defs.Clazz clazz(Profile pr) {
        return pr.cls == null ? null : classes.get(pr.cls);
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
                if (m.getKey().getNamespace().equals(ns)) {
                    inst.removeModifier(m);
                }
            }
        }
        Defs.Clazz c = clazz(pr);
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
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double vit = pr.stat("vit") * plugin.getConfig().getDouble("stats.vit-health-per-point", 1.0);
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
    public boolean weaponOk(Player p, Defs.Clazz c) {
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

    public int rankIndex(String key) {
        for (Defs.Rank r : ranks) {
            if (r.key().equalsIgnoreCase(key)) {
                return r.index();
            }
        }
        return -1;
    }

    public void syncRank(Player p, String oldKey) {
        Profile pr = profile(p);
        Defs.Rank r = rank(pr.rank);
        if (r == null) {
            return;
        }
        runConsole(p, plugin.getConfig().getStringList("sync-commands"), "%rank%", r.key(), "%old_rank%", oldKey);
        updateDisplay(p);
    }

    public void updateDisplay(Player p) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("display.builtin-nametag", false)) {
            return;
        }
        Defs.Rank r = rank(profile(p).rank);
        if (r == null) {
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : sb.getTeams()) {
            if (t.getName().startsWith("nrpg") && t.hasEntry(p.getName())) {
                t.removeEntry(p.getName());
            }
        }
        String tn = "nrpg" + r.index();
        Team team = sb.getTeam(tn);
        if (team == null) {
            team = sb.registerNewTeam(tn);
        }
        Component prefix = Text.L.deserialize(c.getString("display.nametag-format", "&8[%rank%&8] ").replace("%rank%", r.tag()));
        team.prefix(prefix);
        team.addEntry(p.getName());
        if (c.getBoolean("display.tab-list-name", false)) {
            p.playerListName(prefix.append(Component.text(p.getName())));
        }
    }

    public void setRank(Player p, int idx) {
        Profile pr = profile(p);
        String old = rank(pr.rank) == null ? "" : rank(pr.rank).key();
        pr.rank = Math.max(0, Math.min(ranks.size() - 1, idx));
        pr.examTarget = -1;
        pr.examProgress = 0;
        pr.examDeadline = 0;
        syncRank(p, old);
        tracker.rankChanged(p);
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
        } else if (pr.gp < next.gp()) {
            msg(p, "&cGP belum cukup untuk ujian " + next.label() + "&c. Kamu punya &f" + pr.gp + "&c, butuh &f" + next.gp() + "&c.");
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
        String old = rank(pr.rank).key();
        pr.rank = next.index();
        pr.examTarget = -1;
        pr.examProgress = 0;
        pr.examDeadline = 0;
        syncRank(p, old);
        runConsole(p, next.commands());
        runConsole(p, plugin.getConfig().getStringList("promote-commands"), "%rank%", next.key(), "%old_rank%", old);
        p.showTitle(Title.title(Text.c("&6Naik Rank!"), Text.c(next.label())));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        msg(p, "&aSelamat! Kamu naik ke " + next.label() + "&a!");
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

    // ------------------------------------------------------------ tick

    private void tick() {
        FileConfiguration c = plugin.getConfig();
        double regen = c.getDouble("mana.regen-per-second", 2.0);
        boolean hud = c.getBoolean("mana.actionbar", true);
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile pr = profiles.get(p.getUniqueId());
            if (pr == null) {
                continue;
            }
            checkDay(pr);
            double max = maxMana(pr);
            pr.mana = Math.min(max, (pr.mana < 0 ? max : pr.mana) + regen);
            if (pr.examTarget >= 0 && pr.examDeadline > 0 && now > pr.examDeadline) {
                failExam(p, "&cWaktu ujian habis. Kamu gagal.");
            }
            if (pr.examTarget >= 0) {
                Defs.Rank t = rank(pr.examTarget);
                if (t != null && t.exam() != null) {
                    String prog = t.exam().type() == Objective.Type.MANUAL ? t.desc()
                            : pr.examProgress + "&7/&f" + t.exam().amount();
                    String left = pr.examDeadline > 0 ? " &8| &c" + Text.time(pr.examDeadline - now) : "";
                    p.sendActionBar(Text.c("&6Ujian " + t.tag() + " &8| &f" + prog + left));
                    continue;
                }
            }
            if (hud && pr.cls != null) {
                Defs.Clazz cl = clazz(pr);
                String skill = "";
                if (cl != null && !cl.skills().isEmpty()) {
                    int idx = Math.min(pr.skillSel, cl.skills().size() - 1);
                    Defs.Skill s = cl.skills().get(idx);
                    skill = " &8| &e" + (pr.level >= s.level() ? s.name() : s.name() + " &c(Lv " + s.level() + ")");
                }
                p.sendActionBar(Text.c("&cHP &f" + (int) Math.ceil(p.getHealth()) + " &8| &9Mana &f"
                        + (int) pr.mana + "&7/&f" + (int) max + " &8| &6Lv &f" + pr.level + skill));
            }
        }
    }
}
