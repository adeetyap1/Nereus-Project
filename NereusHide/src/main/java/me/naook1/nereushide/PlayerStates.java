package me.naook1.nereushide;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Saves everything a player owns before the game touches it (inventory, location, health, effects...) to
 * states/&lt;uuid&gt;.yml and gives it back afterwards. The file survives crashes and restarts: a player who
 * logs in with a pending file is restored automatically.
 */
public final class PlayerStates {

    private final NereusHide plugin;
    private final File dir;

    public PlayerStates(NereusHide plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "states");
    }

    private File fileOf(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean has(UUID id) {
        return fileOf(id).exists();
    }

    /** Saves the state. An existing file is kept (it holds the original state of an interrupted game). */
    public boolean save(Player p) {
        File f = fileOf(p.getUniqueId());
        if (f.exists()) {
            return true;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("loc", p.getLocation());
        y.set("gm", p.getGameMode().name());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("sat", (double) p.getSaturation());
        y.set("level", p.getLevel());
        y.set("exp", (double) p.getExp());
        y.set("fly", p.getAllowFlight());
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            items.add(it == null ? new ItemStack(Material.AIR) : it);
        }
        y.set("items", items);
        y.set("effects", new ArrayList<>(p.getActivePotionEffects()));
        try {
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Files.writeString(f.toPath(), y.saveToString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save the state of " + p.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Clears the player for the game (call only after a successful save). */
    public void clear(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(null);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        var max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        p.setHealth(max == null ? 20.0 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }

    /** Gives the saved state back and deletes the file. Returns false when there was nothing to restore. */
    public boolean restore(Player p, Location overrideLocation) {
        File f = fileOf(p.getUniqueId());
        if (!f.exists()) {
            return false;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try {
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(e.getType());
            }
            List<?> raw = y.getList("items", List.of());
            ItemStack[] contents = new ItemStack[p.getInventory().getContents().length];
            for (int i = 0; i < contents.length && i < raw.size(); i++) {
                Object o = raw.get(i);
                contents[i] = o instanceof ItemStack it && !it.getType().isAir() ? it : null;
            }
            p.getInventory().setContents(contents);
            for (Object o : y.getList("effects", List.of())) {
                if (o instanceof PotionEffect pe) {
                    p.addPotionEffect(pe);
                }
            }
            GameMode gm;
            try {
                gm = GameMode.valueOf(y.getString("gm", "SURVIVAL"));
            } catch (IllegalArgumentException ex) {
                gm = GameMode.SURVIVAL;
            }
            p.setGameMode(gm);
            p.setAllowFlight(y.getBoolean("fly", false) || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR);
            var max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double limit = max == null ? 20.0 : max.getValue();
            p.setHealth(Math.max(1.0, Math.min(limit, y.getDouble("health", limit))));
            p.setFoodLevel(y.getInt("food", 20));
            p.setSaturation((float) y.getDouble("sat", 5.0));
            p.setLevel(y.getInt("level", 0));
            p.setExp((float) y.getDouble("exp", 0));
            Location target = overrideLocation != null ? overrideLocation : y.getLocation("loc");
            if (target != null && target.getWorld() != null) {
                p.teleport(target);
            }
            p.updateInventory();
        } catch (Exception e) {
            plugin.getLogger().severe("Restoring " + p.getName() + " failed: " + e);
            return false;       // keep the file so nothing is lost
        }
        if (!f.delete()) {
            plugin.getLogger().warning("Could not delete " + f.getName());
        }
        return true;
    }
}
