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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class NereusCosmetics extends JavaPlugin implements Listener, TabExecutor {

    private final Map<String, Cosmetic> registry = new LinkedHashMap<>();
    private final Map<UUID, Map<Cosmetic.Type, String>> equipped = new HashMap<>();
    private final Map<UUID, Set<String>> granted = new HashMap<>();
    private File dataFile;
    private CosmeticsGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tagKey = new NamespacedKey(this, "carrier");
        dataFile = new File(getDataFolder(), "data.yml");
        loadCosmetics();
        loadData();
        gui = new CosmeticsGui(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getCommand("cosmetics").setExecutor(this);
        getCommand("cosmetics").setTabCompleter(this);
        int iv = Math.max(1, getConfig().getInt("settings.interval", 2));
        Bukkit.getScheduler().runTaskTimer(this, () -> tick(iv), 20L, iv);
        getLogger().info("NereusCosmetics v" + getDescription().getVersion() + " enabled, " + registry.size() + " cosmetics | developer: naook1/adityap");
    }

    @Override
    public void onDisable() {
        for (ItemDisplay d : displays.values()) {
            d.remove();
        }
        displays.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearCarriers(p);
        }
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
            String perm = s.getString("permission", "nereuscosmetics.use." + id);
            registry.put(id, new Cosmetic(id, color(s.getString("name", id)), type, s.getInt("cmd", 0),
                    perm == null ? "" : perm.trim(), color(s.getString("description", ""))));
        }
    }

    static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
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
        if (c.permission().isEmpty() || p.hasPermission(c.permission()) || p.hasPermission("nereuscosmetics.type." + c.type().name().toLowerCase())) {
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
        refresh(p);
    }

    void unequip(Player p, Cosmetic.Type t) {
        Map<Cosmetic.Type, String> m = equipped.get(p.getUniqueId());
        if (m != null) {
            m.remove(t);
            saveData();
            refresh(p);
        }
    }

    void unequipAll(Player p) {
        equipped.remove(p.getUniqueId());
        saveData();
        refresh(p);
    }

    String msg(String key, String def) {
        return color(getConfig().getString("messages.prefix", "&b[Cosmetics] &r")) + color(getConfig().getString("messages." + key, def));
    }

    // ------------------------------------------------------------ items
    private NamespacedKey tagKey;
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();
    private final Map<UUID, Float> displayYaw = new HashMap<>();
    private final Map<UUID, Set<EquipmentSlot>> shown = new HashMap<>();
    private int tickCount = 0;

    ItemStack carrier(Cosmetic c, boolean tagged) {
        ItemStack it = new ItemStack(Material.COAL);
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(c.cmd());
        meta.setDisplayName(c.name());
        if (tagged) {
            meta.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, c.cmd());
        }
        it.setItemMeta(meta);
        return it;
    }

    private int tagOf(ItemStack it) {
        if (it == null || it.getType() != Material.COAL || !it.hasItemMeta()) {
            return -1;
        }
        Integer v = it.getItemMeta().getPersistentDataContainer().get(tagKey, PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }

    static boolean isBedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    private static EquipmentSlot slotOf(Cosmetic.Type t) {
        return switch (t) {
            case HAT -> EquipmentSlot.HEAD;
            case BACK -> EquipmentSlot.CHEST;
            case HAND -> EquipmentSlot.OFF_HAND;
        };
    }

    private static int invIndex(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> 39;
            case CHEST -> 38;
            default -> 40;
        };
    }

    /** Put a hidden carrier item in the player's own slot so they can see it themselves (only if the slot is free). */
    private void syncReal(Player p, EquipmentSlot slot, Cosmetic c) {
        ItemStack cur = p.getInventory().getItem(slot);
        boolean empty = cur == null || cur.getType().isAir();
        int tag = tagOf(cur);
        if (c == null) {
            if (tag >= 0) {
                p.getInventory().setItem(slot, null);
            }
            return;
        }
        if (empty || (tag >= 0 && tag != c.cmd())) {
            p.getInventory().setItem(slot, carrier(c, true));
        }
    }

    private void sweepStray(Player p) {
        ItemStack[] cont = p.getInventory().getContents();
        for (int i = 0; i < cont.length; i++) {
            if (tagOf(cont[i]) >= 0 && i != 38 && i != 39 && i != 40) {
                p.getInventory().setItem(i, null);
            }
        }
        if (tagOf(p.getItemOnCursor()) >= 0) {
            p.setItemOnCursor(null);
        }
    }

    private void clearCarriers(Player p) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.OFF_HAND}) {
            syncReal(p, s, null);
        }
        sweepStray(p);
    }

    // ------------------------------------------------------------ rendering
    private void tick(int iv) {
        tickCount += iv;
        boolean checkPerm = tickCount % 100 < iv;
        boolean sendFake = tickCount % 10 < iv;
        boolean showOwnBack = getConfig().getBoolean("settings.show-own-back", true);
        double vd = getConfig().getDouble("settings.view-distance", 40);
        double vd2 = vd * vd;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean wasDirty = dirty.remove(id);
            Map<Cosmetic.Type, String> m = equipped.get(id);
            boolean has = m != null && !m.isEmpty();
            if (!has && !shown.containsKey(id) && !displays.containsKey(id)) {
                continue;
            }
            if (has && checkPerm) {
                for (Cosmetic.Type ty : new ArrayList<>(m.keySet())) {
                    Cosmetic c = registry.get(m.get(ty));
                    if (c == null || !canUse(p, c)) {
                        m.remove(ty);
                    }
                }
            }
            has = m != null && !m.isEmpty();
            boolean hidden = p.isDead() || p.isInvisible() || p.getGameMode() == GameMode.SPECTATOR;
            boolean bedrock = isBedrock(p);
            Cosmetic hat = has && !hidden ? registry.get(m.get(Cosmetic.Type.HAT)) : null;
            Cosmetic back = has && !hidden ? registry.get(m.get(Cosmetic.Type.BACK)) : null;
            Cosmetic hand = has && !hidden ? registry.get(m.get(Cosmetic.Type.HAND)) : null;

            // own view: real carrier items
            if (checkPerm || sendFake || wasDirty) {
                syncReal(p, EquipmentSlot.HEAD, hat);
                syncReal(p, EquipmentSlot.OFF_HAND, hand);
                syncReal(p, EquipmentSlot.CHEST, bedrock ? back : null);
                sweepStray(p);
            }

            // other players: fake equipment packets
            if (sendFake || wasDirty) {
                Set<EquipmentSlot> prev = shown.getOrDefault(id, Collections.emptySet());
                Set<EquipmentSlot> now = new HashSet<>();
                for (Player v : p.getWorld().getPlayers()) {
                    if (v == p || v.getLocation().distanceSquared(p.getLocation()) > vd2) {
                        continue;
                    }
                    boolean vb = isBedrock(v);
                    Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
                    if (hat != null) {
                        map.put(EquipmentSlot.HEAD, carrier(hat, false));
                    }
                    if (hand != null) {
                        map.put(EquipmentSlot.OFF_HAND, carrier(hand, false));
                    }
                    if (back != null && vb) {
                        map.put(EquipmentSlot.CHEST, carrier(back, false));
                    }
                    for (EquipmentSlot s : prev) {
                        if (!map.containsKey(s)) {
                            ItemStack real = p.getInventory().getItem(s);
                            map.put(s, tagOf(real) >= 0 || real == null ? new ItemStack(Material.AIR) : real);
                        }
                    }
                    if (!map.isEmpty()) {
                        v.sendEquipmentChange(p, map);
                    }
                    now.addAll(map.keySet());
                }
                Set<EquipmentSlot> keep = new HashSet<>();
                if (hat != null) {
                    keep.add(EquipmentSlot.HEAD);
                }
                if (hand != null) {
                    keep.add(EquipmentSlot.OFF_HAND);
                }
                if (back != null) {
                    keep.add(EquipmentSlot.CHEST);
                }
                if (keep.isEmpty()) {
                    shown.remove(id);
                } else {
                    shown.put(id, keep);
                }
            }

            // Java viewers: item display for BACK
            ItemDisplay d = displays.get(id);
            if (back == null) {
                if (d != null) {
                    d.remove();
                    displays.remove(id);
                    displayYaw.remove(id);
                }
                continue;
            }
            Location base = p.getLocation();
            boolean fresh = false;
            if (d == null || !d.isValid() || d.getWorld() != p.getWorld()) {
                if (d != null) {
                    d.remove();
                }
                d = p.getWorld().spawn(base, ItemDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                    e.setTeleportDuration(0);
                    e.setInterpolationDelay(0);
                    e.setInterpolationDuration(2);
                    e.setViewRange(1.5f);
                });
                displays.put(id, d);
                displayYaw.put(id, Float.NaN);
                d.setMetadata("nereuscos_cmd", new FixedMetadataValue(this, -1));
                fresh = true;
            }
            int shownCmd = d.getMetadata("nereuscos_cmd").isEmpty() ? -1 : d.getMetadata("nereuscos_cmd").get(0).asInt();
            if (shownCmd != back.cmd()) {
                d.setItemStack(carrier(back, false));
                d.setMetadata("nereuscos_cmd", new FixedMetadataValue(this, back.cmd()));
            }
            // the display rides the player (client moves it together with the body = no lag, follows crouch/swim)
            if (d.getVehicle() != p) {
                d.leaveVehicle();
                p.addPassenger(d);
                fresh = true;
            }
            float yaw = p.getBodyYaw();
            Float last = displayYaw.get(id);
            float dy = last == null || last.isNaN() ? 999f : Math.abs(((yaw - last + 540f) % 360f) - 180f);
            if (fresh || dy > 1.0f || wasDirty) {
                displayYaw.put(id, yaw);
                float oy = (float) getConfig().getDouble("settings.back-offset-y", 0.0);
                float oz = (float) getConfig().getDouble("settings.back-offset-z", 0.0);
                float rad = (float) Math.toRadians(-yaw);
                // rider sits at player y + height (1.8); item centre must be at 1.406 + 0.234
                // back offset (z) is applied along the body direction
                float sx = (float) (-Math.sin(Math.toRadians(yaw)) * -oz);
                float sz = (float) (Math.cos(Math.toRadians(yaw)) * -oz);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(fresh ? 0 : 2);
                d.setTransformation(new Transformation(new Vector3f(sx, 1.406f + 0.234f - 1.8f + oy, sz),
                        new AxisAngle4f(rad, 0, 1, 0), new Vector3f(0.586f, 0.586f, 0.586f), new AxisAngle4f()));
            }
            if (checkPerm || sendFake || wasDirty) {
                for (Player v : Bukkit.getOnlinePlayers()) {
                    boolean hide = isBedrock(v) || (v == p && !showOwnBack);
                    if (hide) {
                        v.hideEntity(this, d);
                    } else {
                        v.showEntity(this, d);
                    }
                }
            }
        }
    }

    private final Set<UUID> dirty = new HashSet<>();

    void refresh(Player p) {
        dirty.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> tick(0));
    }

    private void cleanup(Player p) {
        ItemDisplay d = displays.remove(p.getUniqueId());
        if (d != null) {
            d.remove();
        }
        displayYaw.remove(p.getUniqueId());
        shown.remove(p.getUniqueId());
        clearCarriers(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            sweepStray(e.getPlayer());
            dirty.add(e.getPlayer().getUniqueId());
        }, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        dirty.add(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(it -> tagOf(it) >= 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClickCarrier(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof CosmeticsGui.Holder) {
            return;
        }
        boolean bad = tagOf(e.getCurrentItem()) >= 0 || tagOf(e.getCursor()) >= 0;
        if (!bad && e.getClick() == ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p) {
            bad = tagOf(p.getInventory().getItem(e.getHotbarButton())) >= 0;
        }
        if (!bad && e.getClick() == ClickType.SWAP_OFFHAND && e.getWhoClicked() instanceof Player p) {
            bad = tagOf(p.getInventory().getItemInOffHand()) >= 0;
        }
        if (bad) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDragCarrier(InventoryDragEvent e) {
        if (tagOf(e.getOldCursor()) >= 0) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (tagOf(e.getItemDrop().getItemStack()) >= 0) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (tagOf(e.getMainHandItem()) >= 0 || tagOf(e.getOffHandItem()) >= 0) {
            e.setCancelled(true);
        }
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
                        p.sendMessage(msg("unknown", "Type: hat, back, hand, all"));
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
            default -> s.sendMessage("/cosmetics [gui|list|equip <id>|unequip <hat|back|hand|all>|give|take|reload]");
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
            out.addAll(List.of("hat", "back", "hand", "all"));
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
