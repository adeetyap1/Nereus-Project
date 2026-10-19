package me.naook1.nereusfishing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import me.naook1.nereusfishing.util.SmallCaps;
import me.naook1.nereusfishing.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Loads rods, rarities and fish from config.yml and builds the items. */
public final class FishManager {

    public record Rarity(String id, String name, double weight, double luckBoost, boolean broadcast,
                  boolean junk) {
    }

    public record Fish(String id, String name, Material material, Rarity rarity, double minWeight,
                       double maxWeight, double pricePerKg, int model) {
    }

    public record Rod(String id, String name, Material material, int model, List<String> lore,
                      double luck, double speed) {
    }

    public record Catch(Fish fish, double weight, double value) {
    }

    private final NereusFishing plugin;
    private final Random random = new Random();
    private final NamespacedKey keyRod;
    private final NamespacedKey keyFish;
    private final NamespacedKey keyWeight;
    private final NamespacedKey keyValue;

    private final Map<String, Rarity> rarities = new LinkedHashMap<>();
    private final Map<String, Fish> fish = new LinkedHashMap<>();
    private final Map<String, Rod> rods = new LinkedHashMap<>();
    private final Rod vanillaRod = new Rod("vanilla", "Vanilla", Material.FISHING_ROD, 0, List.of(), 0, 0);

    public FishManager(NereusFishing plugin) {
        this.plugin = plugin;
        keyRod = new NamespacedKey(plugin, "rod");
        keyFish = new NamespacedKey(plugin, "fish");
        keyWeight = new NamespacedKey(plugin, "weight");
        keyValue = new NamespacedKey(plugin, "value");
    }

    public void reload() {
        rarities.clear();
        fish.clear();
        rods.clear();
        var cfg = plugin.getConfig();

        ConfigurationSection rs = cfg.getConfigurationSection("rarities");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                rarities.put(id.toUpperCase(), new Rarity(id.toUpperCase(), s.getString("name", id),
                        s.getDouble("weight", 1), s.getDouble("luck-boost", 0), s.getBoolean("broadcast", false), s.getBoolean("junk", false)));
            }
        }
        ConfigurationSection fs = cfg.getConfigurationSection("fish");
        if (fs != null) {
            for (String id : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Material mat = Material.matchMaterial(s.getString("material", "COD"));
                Rarity rarity = rarities.get(s.getString("rarity", "COMMON").toUpperCase());
                if (mat == null || rarity == null) {
                    plugin.getLogger().warning("Skipping fish '" + id + "': bad material or rarity.");
                    continue;
                }
                double min = s.getDouble("min-weight", 0.5);
                double max = Math.max(min, s.getDouble("max-weight", min));
                fish.put(id, new Fish(id, s.getString("name", id), mat, rarity, min, max,
                        s.getDouble("price-per-kg", 1), s.getInt("model", 0)));
            }
        }
        ConfigurationSection ds = cfg.getConfigurationSection("rods");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection s = ds.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Material mat = Material.matchMaterial(s.getString("material", "FISHING_ROD"));
                if (mat == null) {
                    mat = Material.FISHING_ROD;
                }
                rods.put(id.toLowerCase(), new Rod(id.toLowerCase(), s.getString("name", id), mat,
                        s.getInt("model", 0), s.getStringList("lore"), s.getDouble("luck", 0),
                        Math.min(90, s.getDouble("speed", 0))));
            }
        }
        plugin.getLogger().info("Loaded " + rods.size() + " rods, " + fish.size() + " fish, "
                + rarities.size() + " rarities.");
    }

    public Map<String, Rod> rods() {
        return rods;
    }

    public Rod rod(String id) {
        return id == null ? null : rods.get(id.toLowerCase());
    }

    public Rod vanillaRod() {
        return vanillaRod;
    }

    public boolean smallCaps() {
        return plugin.getConfig().getBoolean("small-caps", true);
    }

    public String sc(String s) {
        return smallCaps() ? SmallCaps.convert(s) : s;
    }

    public String rarityLabel(Rarity r) {
        return sc(r.name());
    }

    // ---------- rod items ----------

    public ItemStack createRod(Rod rod) {
        ItemStack item = new ItemStack(rod.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(sc(rod.name())));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : rod.lore()) {
            lore.add(Text.item(sc(line)));
        }
        for (String line : plugin.messages().list("rod-stats", "luck", fmt(rod.luck()), "speed", fmt(rod.speed()))) {
            lore.add(Text.item(line));
        }
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (rod.model() > 0) {
            meta.setCustomModelData(rod.model());
        }
        meta.getPersistentDataContainer().set(keyRod, PersistentDataType.STRING, rod.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The Nereus rod an item is, or null. */
    public Rod rodOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(keyRod, PersistentDataType.STRING);
        return id == null ? null : rods.get(id);
    }

    // ---------- catching ----------

    /** Picks a catch for a rod, or null if no fish is configured. */
    public Catch roll(Rod rod) {
        Map<Rarity, Double> chances = new LinkedHashMap<>();
        double total = 0;
        for (Rarity r : rarities.values()) {
            boolean has = fish.values().stream().anyMatch(f -> f.rarity() == r);
            if (!has) {
                continue;
            }
            double w = Math.max(0, r.weight() * (1 + rod.luck() * r.luckBoost()));
            if (w > 0) {
                chances.put(r, w);
                total += w;
            }
        }
        if (total <= 0) {
            return null;
        }
        double pick = random.nextDouble() * total;
        Rarity chosen = null;
        for (Map.Entry<Rarity, Double> e : chances.entrySet()) {
            pick -= e.getValue();
            chosen = e.getKey();
            if (pick <= 0) {
                break;
            }
        }
        List<Fish> pool = new ArrayList<>();
        for (Fish f : fish.values()) {
            if (f.rarity() == chosen) {
                pool.add(f);
            }
        }
        Fish f = pool.get(random.nextInt(pool.size()));
        double weight = f.minWeight() + (f.maxWeight() - f.minWeight()) * Math.pow(random.nextDouble(), 1.6);
        weight = Math.round(weight * 100.0) / 100.0;
        double value = Math.round(weight * f.pricePerKg() * plugin.getConfig().getDouble("sell-multiplier", 1.0) * 100.0) / 100.0;
        return new Catch(f, weight, value);
    }

    public ItemStack createFish(Catch c, String catcher) {
        Fish f = c.fish();
        ItemStack item = new ItemStack(f.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(sc(f.name())));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : plugin.messages().list("fish-lore", "rarity", rarityLabel(f.rarity()),
                "weight", fmt(c.weight()), "value", fmt(c.value()), "player", catcher)) {
            lore.add(Text.item(line));
        }
        meta.lore(lore);
        if (f.model() > 0) {
            meta.setCustomModelData(f.model());
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyFish, PersistentDataType.STRING, f.id());
        pdc.set(keyWeight, PersistentDataType.DOUBLE, c.weight());
        pdc.set(keyValue, PersistentDataType.DOUBLE, c.value());
        item.setItemMeta(meta);
        return item;
    }

    /** Stored sell value of a custom fish item, or -1 if it is not one. */
    public double valueOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return -1;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(keyFish, PersistentDataType.STRING)) {
            return -1;
        }
        Double v = pdc.get(keyValue, PersistentDataType.DOUBLE);
        return v == null ? 0 : v;
    }

    public static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(java.util.Locale.US, "%.2f", d);
    }
}
