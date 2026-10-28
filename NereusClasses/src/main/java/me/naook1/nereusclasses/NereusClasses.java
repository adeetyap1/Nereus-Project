package me.naook1.nereusclasses;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.naook1.nereusclasses.Model.Line;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusClasses extends JavaPlugin implements TabExecutor {

    /** weapon / gear definition (classes.yml). */
    static final class Gear {
        String key;
        String cls;
        double damage;
        double speed = 1.6;
        List<String> lore = new ArrayList<>();
        final List<Line> swing = new ArrayList<>();
        final List<Line> attack = new ArrayList<>();
        final List<Line> damaged = new ArrayList<>();
        final List<Line> timers = new ArrayList<>();
        final List<Integer> timerPeriods = new ArrayList<>();
        final List<Line> slots = new ArrayList<>();
    }

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    me.naook1.nereuscosmetics.NereusCosmetics cosmetics;
    me.naook1.nereusweapons.NereusWeapons weapons;
    Items items;
    Engine engine;
    Triggers triggers;
    ClassGui gui;
    final Map<String, Gear> gear = new LinkedHashMap<>();
    final Map<String, String> classNames = new LinkedHashMap<>();
    final Map<UUID, Map<String, Integer>> selected = new HashMap<>();
    final Map<String, Double> baseMods = new HashMap<>();

    static String color(String s) {
        if (s == null) {
            return "";
        }
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) {
                r.append('§').append(c);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        extractResources();
        items = new Items(this);
        engine = new Engine(this, items);
        triggers = new Triggers(this);
        gui = new ClassGui(this);
        loadAll();
        engine.start();
        Bukkit.getPluginManager().registerEvents(triggers, this);
        Bukkit.getPluginManager().registerEvents(gui, this);
        if (getCommand("classes") != null) {
            getCommand("classes").setExecutor(this);
            getCommand("classes").setTabCompleter(this);
        }
        triggers.start();
        cosmetics = new me.naook1.nereuscosmetics.NereusCosmetics(this);
        cosmetics.onEnable();
        weapons = new me.naook1.nereusweapons.NereusWeapons(this);
        weapons.onEnable();
        // sweep leftover VFX stands (after crash)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            int n = 0;
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                for (org.bukkit.entity.ArmorStand as : w.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                    if (as.getPersistentDataContainer().has(engine.vfxKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                        as.remove();
                        n++;
                    }
                }
            }
            if (n > 0) {
                getLogger().info("removed " + n + " stray VFX stands");
            }
        }, 40L);
    }

    @Override
    public void onDisable() {
        if (cosmetics != null) {
            cosmetics.onDisable();
        }
        if (weapons != null) {
            weapons.onDisable();
        }
        if (engine != null) {
            engine.stop();
        }
    }

    /** copy bundled yml (skills, mobs, classes) to the data folder on first run (or when version changes). */
    private void extractResources() {
        File flag = new File(getDataFolder(), ".resources-version");
        String ver = getDescription().getVersion();
        String have = "";
        try {
            if (flag.exists()) {
                have = Files.readString(flag.toPath()).trim();
            }
        } catch (IOException ignored) {
            have = "";
        }
        boolean force = !ver.equals(have);
        try (InputStream in = getResource("index.txt")) {
            if (in == null) {
                return;
            }
            for (String name : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                name = name.trim();
                if (name.isEmpty()) {
                    continue;
                }
                File out = new File(getDataFolder(), name);
                if (force || !out.exists()) {
                    saveResource(name, true);
                }
            }
            getDataFolder().mkdirs();
            Files.writeString(flag.toPath(), ver);
        } catch (IOException e) {
            getLogger().warning("resources: " + e);
        }
    }

    void loadAll() {
        reloadConfig();
        baseMods.clear();
        ConfigurationSection ms = getConfig().getConfigurationSection("modifiers");
        if (ms != null) {
            for (String k : ms.getKeys(false)) {
                baseMods.put(k.toLowerCase(Locale.ROOT), ms.getDouble(k));
            }
        }
        engine.load();
        gear.clear();
        classNames.clear();
        File f = new File(getDataFolder(), "classes.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection cs = y.getConfigurationSection("classes");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                classNames.put(k, cs.getString(k + ".name", k));
            }
        }
        ConfigurationSection ws = y.getConfigurationSection("weapons");
        if (ws != null) {
            for (String k : ws.getKeys(false)) {
                Gear g = new Gear();
                g.key = k;
                g.cls = ws.getString(k + ".class", "");
                g.damage = ws.getDouble(k + ".damage", 0);
                g.speed = ws.getDouble(k + ".speed", 1.6);
                g.lore = ws.getStringList(k + ".lore");
                for (String raw : ws.getStringList(k + ".skills")) {
                    Line l = Model.parseLine(raw);
                    String tr = l.trigger == null ? "onuse" : l.trigger.toLowerCase(Locale.ROOT);
                    if (tr.equals("onswing")) {
                        g.swing.add(l);
                    } else if (tr.equals("onattack")) {
                        g.attack.add(l);
                    } else if (tr.equals("ondamaged")) {
                        g.damaged.add(l);
                    } else if (tr.startsWith("ontimer")) {
                        int p = 20;
                        int i = tr.indexOf(':');
                        if (i > 0) {
                            try {
                                p = Math.max(1, Integer.parseInt(tr.substring(i + 1).replaceAll("[^0-9]", "")));
                            } catch (NumberFormatException ignored) {
                                p = 20;
                            }
                        }
                        g.timers.add(l);
                        g.timerPeriods.add(p);
                    } else {
                        g.slots.add(l);
                    }
                }
                gear.put(k, g);
            }
        }
        getLogger().info("Gear: " + gear.size() + " weapons/armor, " + classNames.size() + " classes");
    }

    // ------------------------------------------------------------------ helpers
    ItemStack giveItem(String key) {
        Gear g = gear.get(key);
        Items.Reg r = items.get(key);
        if (r == null) {
            return null;
        }
        List<String> lore = new ArrayList<>();
        if (g != null) {
            lore.addAll(g.lore);
            if (!g.slots.isEmpty()) {
                lore.add("&8");
                lore.add("&7Klik kanan: &fskill terpilih");
                lore.add("&7Sneak + klik kiri: &fganti skill");
            }
        }
        return items.make(key, g == null ? 0 : g.damage, g == null ? 1.6 : g.speed, lore);
    }

    static String skillName(Line l) {
        String s = l.o.str(l.name, "s", "skill", "meta", "m");
        StringBuilder b = new StringBuilder();
        for (String p : s.split("_")) {
            if (p.isEmpty()) {
                continue;
            }
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT)).append(' ');
        }
        return b.toString().trim();
    }

    boolean canUse(Player p, String cls) {
        return p.hasPermission("nereusclasses.admin") || p.hasPermission("nereusclasses.all") || p.hasPermission("nereusclasses.class." + cls);
    }

    // ------------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                if (s instanceof Player p) {
                    gui.open(p, null, 0);
                } else {
                    s.sendMessage("Hanya untuk pemain.");
                }
            }
            case "list" -> {
                for (Map.Entry<String, String> e : classNames.entrySet()) {
                    StringBuilder sb = new StringBuilder(color(e.getValue())).append("§7: ");
                    for (Gear g : gear.values()) {
                        if (g.cls.equals(e.getKey())) {
                            sb.append(g.key).append(' ');
                        }
                    }
                    s.sendMessage(sb.toString());
                }
            }
            case "give" -> {
                if (!s.hasPermission("nereusclasses.admin")) {
                    s.sendMessage("§cTidak ada izin.");
                    return true;
                }
                if (a.length < 3) {
                    s.sendMessage("§7/classes give <player> <item>");
                    return true;
                }
                Player t = Bukkit.getPlayer(a[1]);
                ItemStack it = giveItem(a[2].toLowerCase(Locale.ROOT));
                if (t == null || it == null) {
                    s.sendMessage("§cPemain/item tidak ditemukan.");
                    return true;
                }
                t.getInventory().addItem(it).values().forEach(l -> t.getWorld().dropItemNaturally(t.getLocation(), l));
                s.sendMessage("§aDiberikan " + a[2] + " ke " + t.getName());
            }
            case "skill" -> {
                if (s instanceof Player p) {
                    triggers.cycle(p, a.length > 1 && a[1].equalsIgnoreCase("prev") ? -1 : 1);
                }
            }
            case "cast" -> {
                if (!(s instanceof Player p) || !p.hasPermission("nereusclasses.admin") || a.length < 2) {
                    s.sendMessage("§cAdmin saja: /classes cast <skill>");
                    return true;
                }
                boolean ok = engine.castByName(a[1], p, p.getLocation(), null, new HashMap<>(baseMods));
                p.sendMessage(ok ? "§aCast " + a[1] : "§cSkill tidak ada / cooldown / syarat belum terpenuhi.");
            }
            case "reload" -> {
                if (!s.hasPermission("nereusclasses.admin")) {
                    s.sendMessage("§cTidak ada izin.");
                    return true;
                }
                engine.stop();
                engine.start();
                loadAll();
                s.sendMessage("§aNereusClasses dimuat ulang.");
            }
            default -> s.sendMessage("§7/classes [menu|list|skill|give|cast|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("menu", "list", "skill", "give", "cast", "reload")) {
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) {
                    out.add(x);
                }
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (String k : gear.keySet()) {
                if (k.startsWith(a[2].toLowerCase(Locale.ROOT))) {
                    out.add(k);
                }
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("cast")) {
            for (String k : engine.skills.keySet()) {
                if (k.startsWith(a[1].toLowerCase(Locale.ROOT)) && out.size() < 40) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    Entity entity(UUID id) {
        return Bukkit.getEntity(id);
    }
}
