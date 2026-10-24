package me.naook1.nereuscosmetics;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusCosmetics extends JavaPlugin implements Listener, TabExecutor {

    private final Map<String, Cosmetic> registry = new LinkedHashMap<>();
    private final Map<UUID, Map<Cosmetic.Type, String>> equipped = new HashMap<>();
    private final Map<UUID, Set<String>> granted = new HashMap<>();
    private final Map<UUID, Location> last = new HashMap<>();
    private File dataFile;
    private CosmeticsGui gui;
    private double t = 0;
    private int permTick = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        loadCosmetics();
        loadData();
        gui = new CosmeticsGui(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getCommand("cosmetics").setExecutor(this);
        getCommand("cosmetics").setTabCompleter(this);
        int iv = Math.max(1, getConfig().getInt("settings.interval", 3));
        Bukkit.getScheduler().runTaskTimer(this, () -> tick(iv), 20L, iv);
        getLogger().info("NereusCosmetics v" + getDescription().getVersion() + " enabled, " + registry.size() + " cosmetics | developer: naook1/adityap");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ------------------------------------------------------------ config
    void loadCosmetics() {
        registry.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("cosmetics");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Cosmetic.Type type = Cosmetic.Type.of(s.getString("type", "HAT"));
            if (type == null) {
                getLogger().warning("Cosmetic " + id + ": unknown type");
                continue;
            }
            Particle particle;
            try {
                particle = Particle.valueOf(s.getString("particle", "DUST").toUpperCase());
            } catch (Exception e) {
                getLogger().warning("Cosmetic " + id + ": unknown particle, using END_ROD");
                particle = Particle.END_ROD;
            }
            Material icon = Material.matchMaterial(s.getString("icon", "NETHER_STAR"));
            if (icon == null) {
                icon = Material.NETHER_STAR;
            }
            String perm = s.getString("permission", "nereuscosmetics.use." + id);
            registry.put(id, new Cosmetic(id, color(s.getString("name", id)), type, s.getString("effect", "halo").toLowerCase(), particle,
                    hex(s.getString("color", "#FFFFFF")), s.getString("color2") == null || s.getString("color2").isBlank() ? null : hex(s.getString("color2")),
                    (float) s.getDouble("size", 1.0), icon, perm == null ? "" : perm.trim(), color(s.getString("description", ""))));
        }
    }

    static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    static Color hex(String h) {
        try {
            h = h.replace("#", "");
            return Color.fromRGB(Integer.parseInt(h, 16));
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    // ------------------------------------------------------------ data
    private void loadData() {
        equipped.clear();
        granted.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps == null) {
            return;
        }
        for (String u : ps.getKeys(false)) {
            try {
                UUID id = UUID.fromString(u);
                Map<Cosmetic.Type, String> m = new EnumMap<>(Cosmetic.Type.class);
                ConfigurationSection e = ps.getConfigurationSection(u + ".equipped");
                if (e != null) {
                    for (String k : e.getKeys(false)) {
                        Cosmetic.Type ty = Cosmetic.Type.of(k);
                        if (ty != null) {
                            m.put(ty, e.getString(k));
                        }
                    }
                }
                equipped.put(id, m);
                granted.put(id, new HashSet<>(ps.getStringList(u + ".granted")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        Set<UUID> ids = new HashSet<>(equipped.keySet());
        ids.addAll(granted.keySet());
        for (UUID id : ids) {
            Map<Cosmetic.Type, String> m = equipped.getOrDefault(id, Collections.emptyMap());
            for (Map.Entry<Cosmetic.Type, String> e : m.entrySet()) {
                y.set("players." + id + ".equipped." + e.getKey().name(), e.getValue());
            }
            Set<String> g = granted.getOrDefault(id, Collections.emptySet());
            if (!g.isEmpty()) {
                y.set("players." + id + ".granted", new ArrayList<>(g));
            }
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (Exception ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ api
    Map<String, Cosmetic> all() {
        return registry;
    }

    boolean isAdmin(Player p) {
        return p.hasPermission("nereuscosmetics.admin");
    }

    /** Admin can take everything, a rank needs the permission (or nereuscosmetics.all), or the cosmetic was given with /cosmetics give. */
    boolean canUse(Player p, Cosmetic c) {
        if (isAdmin(p) || p.hasPermission("nereuscosmetics.all")) {
            return true;
        }
        if (c.permission().isEmpty() || p.hasPermission(c.permission())) {
            return true;
        }
        return granted.getOrDefault(p.getUniqueId(), Collections.emptySet()).contains(c.id());
    }

    String equippedId(Player p, Cosmetic.Type t) {
        return equipped.getOrDefault(p.getUniqueId(), Collections.emptyMap()).get(t);
    }

    void equip(Player p, Cosmetic c) {
        equipped.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Cosmetic.Type.class)).put(c.type(), c.id());
        saveData();
    }

    void unequip(Player p, Cosmetic.Type t) {
        Map<Cosmetic.Type, String> m = equipped.get(p.getUniqueId());
        if (m != null) {
            m.remove(t);
            saveData();
        }
    }

    void unequipAll(Player p) {
        equipped.remove(p.getUniqueId());
        saveData();
    }

    String msg(String key, String def) {
        return color(getConfig().getString("messages.prefix", "&b[Cosmetics] &r")) + color(getConfig().getString("messages." + key, def));
    }

    // ------------------------------------------------------------ rendering
    private void tick(int iv) {
        t += iv;
        boolean showSelf = getConfig().getBoolean("settings.show-self", true);
        double vd = getConfig().getDouble("settings.view-distance", 24);
        double vd2 = vd * vd;
        boolean checkPerm = (permTick += iv) >= 100;
        if (checkPerm) {
            permTick = 0;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<Cosmetic.Type, String> m = equipped.get(p.getUniqueId());
            if (m == null || m.isEmpty()) {
                continue;
            }
            if (p.isDead() || p.isInvisible() || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location now = p.getLocation();
            Location prev = last.put(p.getUniqueId(), now.clone());
            boolean moving = prev != null && prev.getWorld() == now.getWorld()
                    && prev.toVector().setY(0).distanceSquared(now.toVector().setY(0)) > 0.0009;
            List<Player> viewers = new ArrayList<>();
            for (Player v : p.getWorld().getPlayers()) {
                if ((v != p || showSelf) && v.getLocation().distanceSquared(now) <= vd2) {
                    viewers.add(v);
                }
            }
            for (Cosmetic.Type ty : new ArrayList<>(m.keySet())) {
                Cosmetic c = registry.get(m.get(ty));
                if (c == null || (checkPerm && !canUse(p, c))) {
                    m.remove(ty);
                    continue;
                }
                Effects.draw(c, p, t, moving, viewers);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "gui" : a[0].toLowerCase();
        switch (sub) {
            case "gui", "menu" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage("Players only.");
                    return true;
                }
                gui.open(p, null, 0);
            }
            case "list" -> {
                s.sendMessage(msg("list", "Cosmetics: ") + String.join(", ", registry.keySet()));
            }
            case "equip" -> {
                if (!(s instanceof Player p)) {
                    return true;
                }
                Cosmetic c = a.length > 1 ? registry.get(a[1].toLowerCase()) : null;
                if (c == null) {
                    p.sendMessage(msg("unknown", "Cosmetic not found."));
                } else if (!canUse(p, c)) {
                    p.sendMessage(msg("locked", "Your rank cannot use this cosmetic."));
                } else {
                    equip(p, c);
                    p.sendMessage(msg("equipped", "Equipped: ") + c.name());
                }
            }
            case "unequip" -> {
                if (!(s instanceof Player p)) {
                    return true;
                }
                String w = a.length > 1 ? a[1].toLowerCase() : "all";
                if (w.equals("all")) {
                    unequipAll(p);
                } else {
                    Cosmetic.Type ty = Cosmetic.Type.of(w);
                    if (ty == null) {
                        p.sendMessage(msg("unknown", "Type: hat, back, aura, trail, all"));
                        return true;
                    }
                    unequip(p, ty);
                }
                p.sendMessage(msg("removed", "Cosmetic removed."));
            }
            case "give", "take" -> {
                if (!s.hasPermission("nereuscosmetics.admin")) {
                    s.sendMessage(msg("noperm", "No permission."));
                    return true;
                }
                if (a.length < 3) {
                    s.sendMessage("/cosmetics " + sub + " <player> <id|all>");
                    return true;
                }
                Player tg = Bukkit.getPlayerExact(a[1]);
                if (tg == null) {
                    s.sendMessage(msg("offline", "Player is not online."));
                    return true;
                }
                Set<String> g = granted.computeIfAbsent(tg.getUniqueId(), k -> new HashSet<>());
                boolean give = sub.equals("give");
                if (a[2].equalsIgnoreCase("all")) {
                    if (give) {
                        g.addAll(registry.keySet());
                    } else {
                        g.clear();
                    }
                } else if (registry.containsKey(a[2].toLowerCase())) {
                    if (give) {
                        g.add(a[2].toLowerCase());
                    } else {
                        g.remove(a[2].toLowerCase());
                    }
                } else {
                    s.sendMessage(msg("unknown", "Cosmetic not found."));
                    return true;
                }
                saveData();
                s.sendMessage(msg("done", "Done."));
            }
            case "reload" -> {
                if (!s.hasPermission("nereuscosmetics.admin")) {
                    s.sendMessage(msg("noperm", "No permission."));
                    return true;
                }
                reloadConfig();
                loadCosmetics();
                s.sendMessage(msg("reloaded", "Reloaded, ") + registry.size() + " cosmetics.");
            }
            default -> s.sendMessage("/cosmetics [gui|list|equip <id>|unequip <type|all>|give|take|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("gui", "list", "equip", "unequip"));
            if (s.hasPermission("nereuscosmetics.admin")) {
                out.addAll(List.of("give", "take", "reload"));
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("equip")) {
            out.addAll(registry.keySet());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("unequip")) {
            out.addAll(List.of("hat", "back", "aura", "trail", "all"));
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take"))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take"))) {
            out.add("all");
            out.addAll(registry.keySet());
        }
        String pre = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(pre));
        return out;
    }
}
