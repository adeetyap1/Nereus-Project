package me.naook1.nereusweapons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusWeapons extends JavaPlugin implements Listener, TabExecutor {

    public record Weapon(String id, String name, Material material, int cmd, double damage, double speed, String permission,
                         List<String> lore, SkillDef primary, SkillDef secondary) {
    }

    private final Map<String, Weapon> weapons = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private NamespacedKey tagKey;
    private Skills skills;
    private WeaponGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tagKey = new NamespacedKey(this, "weapon");
        skills = new Skills(this);
        gui = new WeaponGui(this);
        load();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getCommand("weapons").setExecutor(this);
        getCommand("weapons").setTabCompleter(this);
        getLogger().info("NereusWeapons v" + getDescription().getVersion() + " enabled, " + weapons.size() + " weapons | developer: naook1/adityap");
    }

    // ------------------------------------------------------------ config
    static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    private SkillDef skill(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(s.getString("particle", "CRIT").toUpperCase());
        } catch (Exception e) {
            particle = Particle.CRIT;
        }
        Color c;
        try {
            c = Color.fromRGB(Integer.parseInt(s.getString("color", "#FFFFFF").replace("#", ""), 16));
        } catch (Exception e) {
            c = Color.WHITE;
        }
        return new SkillDef(s.getString("type", "NOVA"), s.getString("name", "Skill"), s.getDouble("cooldown", 8), s.getDouble("damage", 5),
                s.getDouble("range", 12), s.getDouble("radius", 4), s.getDouble("power", 1.2), s.getDouble("knockback", 0),
                s.getDouble("angle", 35), s.getString("effect", ""), s.getInt("effect_seconds", 0), s.getDouble("heal_percent", 0),
                particle, c, s.getString("desc", ""));
    }

    void load() {
        weapons.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("weapons");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection w = sec.getConfigurationSection(id);
            Material m = Material.matchMaterial(w.getString("material", "NETHERITE_SWORD"));
            if (m == null) {
                getLogger().warning("Weapon " + id + ": unknown material");
                continue;
            }
            weapons.put(id, new Weapon(id, color(w.getString("name", id)), m, w.getInt("cmd", 0), w.getDouble("damage", 6),
                    w.getDouble("attack-speed", 1.6), w.getString("permission", "nereusweapons.use." + id), w.getStringList("lore"),
                    skill(w.getConfigurationSection("skills.primary")), skill(w.getConfigurationSection("skills.secondary"))));
        }
    }

    Map<String, Weapon> all() {
        return weapons;
    }

    String msg(String key, String def) {
        return color(getConfig().getString("messages.prefix", "&c[Weapons] &r")) + color(getConfig().getString("messages." + key, def));
    }

    boolean canUse(Player p, Weapon w) {
        return p.hasPermission("nereusweapons.admin") || p.hasPermission("nereusweapons.all") || p.hasPermission(w.permission());
    }

    // ------------------------------------------------------------ items
    ItemStack create(Weapon w) {
        ItemStack it = new ItemStack(w.material());
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(w.cmd());
        meta.setDisplayName(w.name());
        List<String> lore = new ArrayList<>();
        lore.addAll(w.lore().stream().map(NereusWeapons::color).toList());
        if (w.primary() != null) {
            lore.add("§7Klik kanan: §f" + w.primary().name() + " §8(" + (int) w.primary().cooldown() + "s)");
            lore.add("§8  " + w.primary().desc());
        }
        if (w.secondary() != null) {
            lore.add("§7Sneak + klik kanan: §f" + w.secondary().name() + " §8(" + (int) w.secondary().cooldown() + "s)");
            lore.add("§8  " + w.secondary().desc());
        }
        meta.setLore(lore);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(new NamespacedKey(this, "dmg"),
                w.damage() - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(new NamespacedKey(this, "spd"),
                w.speed() - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, w.id());
        it.setItemMeta(meta);
        return it;
    }

    Weapon weaponOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        return id == null ? null : weapons.get(id);
    }

    boolean has(Player p, Weapon w) {
        for (ItemStack it : p.getInventory().getContents()) {
            Weapon o = weaponOf(it);
            if (o != null && o.id().equals(w.id())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ casting
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = e.getPlayer();
        Weapon w = weaponOf(p.getInventory().getItemInMainHand());
        if (w == null) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) {
            return;
        }
        e.setCancelled(true);
        if (!canUse(p, w)) {
            p.sendMessage(msg("locked", "Rank kamu belum bisa memakai senjata ini."));
            return;
        }
        boolean secondary = p.isSneaking();
        SkillDef s = secondary ? w.secondary() : w.primary();
        if (s == null) {
            return;
        }
        String key = w.id() + (secondary ? ":s" : ":p");
        Map<String, Long> cd = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis();
        long ready = cd.getOrDefault(key, 0L);
        if (now < ready) {
            p.sendActionBar(net.kyori.adventure.text.Component.text(color(getConfig().getString("messages.cooldown", "&7{skill} &8| &cisi ulang {sec}s")
                    .replace("{skill}", s.name()).replace("{sec}", String.valueOf((ready - now + 999) / 1000)))));
            return;
        }
        if (skills.cast(p, s)) {
            cd.put(key, now + (long) (s.cooldown() * 1000));
            p.sendActionBar(net.kyori.adventure.text.Component.text("§d✦ " + s.name()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase();
        switch (sub) {
            case "menu", "gui" -> {
                if (s instanceof Player p) {
                    gui.open(p, 0);
                }
            }
            case "list" -> s.sendMessage(msg("list", "Weapons: ") + String.join(", ", weapons.keySet()));
            case "give" -> {
                if (!s.hasPermission("nereusweapons.admin")) {
                    s.sendMessage(msg("noperm", "No permission."));
                    return true;
                }
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                Weapon w = a.length > 2 ? weapons.get(a[2].toLowerCase()) : null;
                if (t == null || w == null) {
                    s.sendMessage("/weapons give <player> <id>");
                    return true;
                }
                t.getInventory().addItem(create(w));
                s.sendMessage(msg("done", "Done."));
            }
            case "reload" -> {
                if (!s.hasPermission("nereusweapons.admin")) {
                    s.sendMessage(msg("noperm", "No permission."));
                    return true;
                }
                reloadConfig();
                load();
                s.sendMessage(msg("reloaded", "Reloaded, ") + weapons.size() + " weapons.");
            }
            default -> s.sendMessage("/weapons [menu|list|give <player> <id>|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("menu", "list"));
            if (s.hasPermission("nereusweapons.admin")) {
                out.addAll(List.of("give", "reload"));
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            out.addAll(weapons.keySet());
        }
        String pre = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(pre));
        return out;
    }
}
