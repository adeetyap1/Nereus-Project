package my.nereus.intro.recovery;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Kondisi pemain sebelum intro. Sengaja TIDAK menyentuh inventory, balance, rank, level, XP, atau statistik.
 */
public final class PlayerSnapshot {

    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final GameMode gameMode;
    private final boolean allowFlight, flying, invulnerable;
    private final float walkSpeed, flySpeed;
    private final double health;
    private final int food;
    private final float saturation;
    // Blindness sebelumnya (dipakai fade); -1 = tidak ada
    private final int blindnessDuration;
    private final int blindnessAmplifier;

    private PlayerSnapshot(String world, double x, double y, double z, float yaw, float pitch, GameMode gameMode,
                           boolean allowFlight, boolean flying, boolean invulnerable, float walkSpeed, float flySpeed,
                           double health, int food, float saturation, int blindDur, int blindAmp) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.invulnerable = invulnerable;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.blindnessDuration = blindDur;
        this.blindnessAmplifier = blindAmp;
    }

    public static PlayerSnapshot capture(Player p) {
        Location l = p.getLocation();
        PotionEffect blind = p.getPotionEffect(PotionEffectType.BLINDNESS);
        float walk = p.getWalkSpeed();
        float fly = p.getFlySpeed();
        // pengaman: kecepatan 0 hampir pasti sisa sesi yang gagal dipulihkan
        if (walk <= 0.001f) walk = 0.2f;
        if (fly <= 0.001f) fly = 0.1f;
        return new PlayerSnapshot(
                l.getWorld() == null ? "" : l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(),
                p.getGameMode(), p.getAllowFlight(), p.isFlying(), p.isInvulnerable(),
                walk, fly, p.getHealth(), p.getFoodLevel(), p.getSaturation(),
                blind == null ? -1 : blind.getDuration(), blind == null ? 0 : blind.getAmplifier());
    }

    /** Lokasi asal, atau null bila world-nya sudah tidak ada. */
    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    /** Mengembalikan semua kondisi selain lokasi (lokasi dipulihkan lewat teleport). */
    public void applyState(Player p) {
        try {
            p.setGameMode(gameMode);
            p.setAllowFlight(allowFlight);
            if (allowFlight && flying) p.setFlying(true);
            p.setWalkSpeed(clamp(walkSpeed));
            p.setFlySpeed(clamp(flySpeed));
            p.setInvulnerable(invulnerable);
            p.setFallDistance(0f);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            if (blindnessDuration > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, blindnessAmplifier));
            }
            if (!p.isDead()) {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20.0 : max.getValue();
                p.setHealth(Math.max(0.5, Math.min(health, cap)));
                p.setFoodLevel(Math.max(0, Math.min(20, food)));
                p.setSaturation(Math.max(0f, saturation));
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[NereusIntro] Gagal memulihkan sebagian kondisi " + p.getName() + ": " + t);
        }
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    public void write(ConfigurationSection s) {
        s.set("world", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
        s.set("pitch", (double) pitch);
        s.set("gamemode", gameMode.name());
        s.set("allow-flight", allowFlight);
        s.set("flying", flying);
        s.set("invulnerable", invulnerable);
        s.set("walk-speed", (double) walkSpeed);
        s.set("fly-speed", (double) flySpeed);
        s.set("health", health);
        s.set("food", food);
        s.set("saturation", (double) saturation);
        s.set("blind-duration", blindnessDuration);
        s.set("blind-amplifier", blindnessAmplifier);
    }

    public static PlayerSnapshot read(ConfigurationSection s) {
        GameMode gm;
        try {
            gm = GameMode.valueOf(s.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException e) {
            gm = GameMode.SURVIVAL;
        }
        return new PlayerSnapshot(
                s.getString("world", ""), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"), gm,
                s.getBoolean("allow-flight"), s.getBoolean("flying"), s.getBoolean("invulnerable"),
                (float) s.getDouble("walk-speed", 0.2), (float) s.getDouble("fly-speed", 0.1),
                s.getDouble("health", 20.0), s.getInt("food", 20), (float) s.getDouble("saturation", 5.0),
                s.getInt("blind-duration", -1), s.getInt("blind-amplifier", 0));
    }
}
