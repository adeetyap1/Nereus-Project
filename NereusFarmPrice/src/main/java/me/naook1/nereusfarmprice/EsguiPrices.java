package me.naook1.nereusfarmprice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads SELL prices straight from the EconomyShopGUI files (shops.yml and shops/**.yml), so base prices always
 * follow what the shop uses. It walks every section that has a "material" and a numeric "sell", which works
 * for both the old and the new file layout.
 */
public final class EsguiPrices {

    private final NereusFarmPrice plugin;
    private final Map<Material, Double> sell = new EnumMap<>(Material.class);
    private int files;

    public EsguiPrices(NereusFarmPrice plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Map<Material, Double> fresh = new EnumMap<>(Material.class);
        int count = 0;
        File plugins = plugin.getDataFolder().getParentFile();
        for (String folder : plugin.getConfig().getStringList("esgui.folders")) {
            File dir = new File(plugins, folder);
            if (!dir.isDirectory()) {
                continue;
            }
            for (File f : candidates(dir)) {
                try {
                    scan(YamlConfiguration.loadConfiguration(f), fresh);
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not read " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        synchronized (sell) {
            sell.clear();
            sell.putAll(fresh);
        }
        files = count;
    }

    private List<File> candidates(File dir) {
        List<File> out = new ArrayList<>();
        File shops = new File(dir, "shops.yml");
        if (shops.isFile()) {
            out.add(shops);
        }
        Path folder = new File(dir, "shops").toPath();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> walk = Files.walk(folder, 3)) {
                walk.filter(p -> p.toString().endsWith(".yml")).forEach(p -> out.add(p.toFile()));
            } catch (IOException e) {
                plugin.getLogger().warning("Could not list " + folder + ": " + e.getMessage());
            }
        }
        return out;
    }

    private void scan(ConfigurationSection section, Map<Material, Double> into) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            String mat = child.getString("material");
            if (mat != null) {
                Material m = Material.matchMaterial(mat);
                double price = number(child.get("sell"));
                if (m != null && price > 0) {
                    into.putIfAbsent(m, price);
                }
            }
            scan(child, into);
        }
    }

    private static double number(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    /** Sell price per item in the shop, or -1 when unknown. */
    public double sellPrice(Material m) {
        synchronized (sell) {
            return sell.getOrDefault(m, -1.0);
        }
    }

    public int itemCount() {
        synchronized (sell) {
            return sell.size();
        }
    }

    public int fileCount() {
        return files;
    }
}
