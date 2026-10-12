package me.naook1.nereusistent.gadget;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.naook1.nereusistent.Nereusistent;
import me.naook1.nereusistent.util.SmallCaps;
import me.naook1.nereusistent.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Builds the gadget item and keeps it locked in its hotbar slot. */
public final class GadgetManager {

    private final Nereusistent plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> lastOpen = new HashMap<>();
    private final List<String> disabledWorlds = new ArrayList<>();
    private final Set<UUID> disabled = new HashSet<>();
    private final File dataFile;
    private BukkitTask task;

    private int slot = 8;
    private Material material = Material.NETHER_STAR;
    private String name = "";
    private List<String> lore = new ArrayList<>();
    private boolean glow;
    private int modelData;
    private long cooldown = 300;
    private int intervalTicks = 40;

    public GadgetManager(Nereusistent plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "gadget");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadData();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        boolean sc = c.getBoolean("small-caps.gui", true);
        slot = Math.max(1, Math.min(9, c.getInt("gadget.slot", 9))) - 1;
        Material m = Material.matchMaterial(c.getString("gadget.material", "NETHER_STAR"));
        material = (m == null || !m.isItem()) ? Material.NETHER_STAR : m;
        name = c.getString("gadget.name", "Nereusistent");
        lore = new ArrayList<>(c.getStringList("gadget.lore"));
        if (sc) {
            name = SmallCaps.convert(name);
            List<String> converted = new ArrayList<>();
            for (String line : lore) {
                converted.add(SmallCaps.convert(line));
            }
            lore = converted;
        }
        glow = c.getBoolean("gadget.glow", true);
        modelData = c.getInt("gadget.custom-model-data", 0);
        cooldown = c.getLong("gadget.open-cooldown-ms", 300L);
        intervalTicks = Math.max(1, c.getInt("gadget.check-interval-seconds", 2)) * 20;
        disabledWorlds.clear();
        disabledWorlds.addAll(c.getStringList("gadget.disabled-worlds"));
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ensure(p, false);
            }
        }, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public Material material() {
        return material;
    }

    public boolean isGadget(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public boolean canHave(Player p) {
        return p.hasPermission("nereusistent.use")
                && !disabledWorlds.contains(p.getWorld().getName())
                && !disabled.contains(p.getUniqueId());
    }

    public ItemStack build(Player p) {
        String[] kv = plugin.menus().ctx(p);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(Text.papi(p, Text.fill(name, kv))));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.item(Text.papi(p, Text.fill(line, kv))));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Puts the gadget in its slot, removes copies elsewhere. force = rebuild even if present. */
    public void ensure(Player p, boolean force) {
        PlayerInventory inv = p.getInventory();
        if (!canHave(p)) {
            strip(p);
            return;
        }
        boolean ok = false;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (!isGadget(contents[i])) {
                continue;
            }
            if (i == slot && !force && contents[i].getAmount() == 1) {
                ok = true;
            } else {
                inv.setItem(i, null);
            }
        }
        if (ok) {
            return;
        }
        ItemStack displaced = inv.getItem(slot);
        inv.setItem(slot, build(p));
        if (displaced != null && !displaced.getType().isAir()) {
            for (ItemStack left : inv.addItem(displaced).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    public void strip(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGadget(contents[i])) {
                inv.setItem(i, null);
            }
        }
    }

    public void open(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(p.getUniqueId());
        if (last != null && now - last < cooldown) {
            return;
        }
        lastOpen.put(p.getUniqueId(), now);
        plugin.menus().open(p, "main");
    }

    public boolean isDisabled(UUID uuid) {
        return disabled.contains(uuid);
    }

    /** A player turns their own gadget off/on (saved). Returns false if toggling is not allowed. */
    public boolean toggle(Player p) {
        if (!plugin.getConfig().getBoolean("gadget.allow-toggle", true) || !p.hasPermission("nereusistent.toggle")) {
            plugin.messages().send(p, "toggle-denied");
            return false;
        }
        if (disabled.remove(p.getUniqueId())) {
            saveData();
            ensure(p, false);
            plugin.messages().send(p, "gadget-on");
        } else {
            disabled.add(p.getUniqueId());
            saveData();
            strip(p);
            plugin.messages().send(p, "gadget-off");
        }
        return true;
    }

    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        for (String s : y.getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
    }

    private void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID u : disabled) {
            ids.add(u.toString());
        }
        y.set("disabled", ids);
        try {
            y.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    public void forget(UUID uuid) {
        lastOpen.remove(uuid);
    }
}
