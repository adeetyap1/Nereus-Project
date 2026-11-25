package my.nereus.duel;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Keadaan pemain sebelum duel: inventory (termasuk armor + offhand), posisi, HP, lapar, XP, efek. Disimpan ke file agar aman saat crash. */
final class Snapshot {
    final ItemStack[] contents;
    final Spot location;
    final GameMode gameMode;
    final double health;
    final int food;
    final float saturation;
    final int level;
    final float exp;
    final List<PotionEffect> effects;
    final boolean allowFlight;

    private Snapshot(ItemStack[] contents, Spot location, GameMode gm, double health, int food, float sat, int level,
                     float exp, List<PotionEffect> effects, boolean allowFlight) {
        this.contents = contents;
        this.location = location;
        this.gameMode = gm;
        this.health = health;
        this.food = food;
        this.saturation = sat;
        this.level = level;
        this.exp = exp;
        this.effects = effects;
        this.allowFlight = allowFlight;
    }

    static Snapshot capture(Player p) {
        ItemStack[] src = p.getInventory().getContents();
        ItemStack[] c = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) c[i] = src[i] == null ? null : src[i].clone();
        return new Snapshot(c, Spot.of(p.getLocation()), p.getGameMode(), p.getHealth(), p.getFoodLevel(), p.getSaturation(),
                p.getLevel(), p.getExp(), new ArrayList<>(p.getActivePotionEffects()), p.getAllowFlight());
    }

    ItemStack[] contentsCopy() {
        ItemStack[] c = new ItemStack[contents.length];
        for (int i = 0; i < c.length; i++) c[i] = contents[i] == null ? null : contents[i].clone();
        return c;
    }

    void save(File f) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        List<String> items = new ArrayList<>();
        for (ItemStack it : contents) {
            items.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        y.set("contents", items);
        y.set("world", location.world());
        y.set("x", location.x());
        y.set("y", location.y());
        y.set("z", location.z());
        y.set("yaw", location.yaw());
        y.set("pitch", location.pitch());
        y.set("gamemode", gameMode.name());
        y.set("health", health);
        y.set("food", food);
        y.set("saturation", saturation);
        y.set("level", level);
        y.set("exp", exp);
        y.set("effects", effects);
        y.set("allow-flight", allowFlight);
        y.save(f);
    }

    static Snapshot load(File f) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<String> items = y.getStringList("contents");
        ItemStack[] c = new ItemStack[items.size()];
        for (int i = 0; i < c.length; i++) {
            String s = items.get(i);
            c[i] = s.isEmpty() ? null : ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        }
        List<PotionEffect> eff = new ArrayList<>();
        List<?> raw = y.getList("effects");
        if (raw != null) for (Object o : raw) if (o instanceof PotionEffect pe) eff.add(pe);
        GameMode gm;
        try {
            gm = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException e) {
            gm = GameMode.SURVIVAL;
        }
        return new Snapshot(c, new Spot(y.getString("world", "world"), y.getDouble("x"), y.getDouble("y"), y.getDouble("z"),
                (float) y.getDouble("yaw"), (float) y.getDouble("pitch")), gm, y.getDouble("health", 20), y.getInt("food", 20),
                (float) y.getDouble("saturation", 5), y.getInt("level"), (float) y.getDouble("exp"), eff, y.getBoolean("allow-flight"));
    }

    /** Kembalikan pemain persis seperti sebelum duel (termasuk teleport ke lokasi asal). */
    void apply(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(contentsCopy());
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        for (PotionEffect e : effects) p.addPotionEffect(e);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20 : max.getValue();
        p.setHealth(Math.max(1, Math.min(maxHp, health)));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        p.setGameMode(gameMode);
        p.setAllowFlight(allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
        World w = Bukkit.getWorld(location.world());
        Location l = location.toLocation();
        if (l != null) p.teleport(l);
        else if (w == null) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
    }
}
