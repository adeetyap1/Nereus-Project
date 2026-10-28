package me.naook1.nereusclasses;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** particle / sound / vector helpers. */
final class Fx {
    static double particleMult = 2.0;
    static long soundGapMs = 300;
    private static final java.util.Map<String, Long> LAST_SOUND = new java.util.HashMap<>();

    private Fx() {
    }

    private static final Map<String, Particle> PART = new HashMap<>();
    private static final Map<String, String> POTION = new HashMap<>();

    static {
        PART.put("redstone", Particle.DUST);
        PART.put("reddust", Particle.DUST);
        PART.put("dust", Particle.DUST);
        PART.put("flame", Particle.FLAME);
        PART.put("smoke_large", Particle.LARGE_SMOKE);
        PART.put("large_smoke", Particle.LARGE_SMOKE);
        PART.put("smoke", Particle.SMOKE);
        PART.put("smoke_normal", Particle.SMOKE);
        PART.put("lava", Particle.LAVA);
        PART.put("glow", Particle.GLOW);
        PART.put("body", Particle.DUST);
        PART.put("drip_lava", Particle.DRIPPING_LAVA);
        PART.put("portal", Particle.PORTAL);
        PART.put("particle", Particle.DUST);
        PART.put("crit", Particle.CRIT);
        PART.put("crimson_spore", Particle.CRIMSON_SPORE);
        PART.put("block_crack", Particle.CRIT);
        PART.put("splash", Particle.SPLASH);
        PART.put("snowflake", Particle.SNOWFLAKE);
        PART.put("happyvillager", Particle.HAPPY_VILLAGER);
        PART.put("villager_happy", Particle.HAPPY_VILLAGER);
        PART.put("explosion_huge", Particle.EXPLOSION_EMITTER);
        PART.put("explosion_large", Particle.EXPLOSION);
        PART.put("explosion", Particle.EXPLOSION);
        PART.put("campfire_cosy_smoke", Particle.CAMPFIRE_COSY_SMOKE);
        PART.put("soul_fire_flame", Particle.SOUL_FIRE_FLAME);
        PART.put("soulflame", Particle.SOUL_FIRE_FLAME);
        PART.put("end_rod", Particle.END_ROD);
        PART.put("cloud", Particle.CLOUD);
        PART.put("heart", Particle.HEART);
        PART.put("witch", Particle.WITCH);
        PART.put("spell_witch", Particle.WITCH);
        PART.put("enchantmenttable", Particle.ENCHANT);
        PART.put("enchant", Particle.ENCHANT);
        PART.put("totem", Particle.TOTEM_OF_UNDYING);
        PART.put("sweep_attack", Particle.SWEEP_ATTACK);
        PART.put("dragonbreath", Particle.DRAGON_BREATH);
        PART.put("dragon_breath", Particle.DRAGON_BREATH);

        POTION.put("slow", "slowness");
        POTION.put("increase_damage", "strength");
        POTION.put("damage_resistance", "resistance");
        POTION.put("jump", "jump_boost");
        POTION.put("confusion", "nausea");
        POTION.put("fast_digging", "haste");
        POTION.put("slow_digging", "mining_fatigue");
        POTION.put("harm", "instant_damage");
        POTION.put("heal", "instant_health");
    }

    static Particle particle(String name) {
        if (name == null) {
            return Particle.DUST;
        }
        String k = name.toLowerCase(Locale.ROOT);
        Particle p = PART.get(k);
        if (p != null) {
            return p;
        }
        try {
            return Particle.valueOf(k.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.DUST;
        }
    }

    static Color color(String hex, Color def) {
        if (hex == null) {
            return def;
        }
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return Color.fromRGB(Integer.parseInt(h.substring(0, 6), 16));
        } catch (RuntimeException e) {
            return def;
        }
    }

    static void spawn(Location l, Particle p, int amount, double hs, double vs, double speed, Color c, float size) {
        World w = l.getWorld();
        if (w == null) {
            return;
        }
        int n = Math.min(300, Math.max(1, (int) Math.ceil(amount * particleMult)));
        if (p == Particle.DUST) {
            w.spawnParticle(p, l, n, hs, vs, hs, 0, new Particle.DustOptions(c == null ? Color.RED : c, Math.max(0.2f, size)));
        } else {
            w.spawnParticle(p, l, n, hs, vs, hs, speed);
        }
    }

    static void sound(Location l, String name, float vol, float pitch) {
        if (l.getWorld() == null || name == null || name.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = name + '|' + l.getWorld().getName() + '|' + (l.getBlockX() >> 3) + '|' + (l.getBlockZ() >> 3);
        Long last = LAST_SOUND.get(key);
        if (last != null && now - last < soundGapMs) {
            return;
        }
        if (LAST_SOUND.size() > 2000) {
            LAST_SOUND.clear();
        }
        LAST_SOUND.put(key, now);
        l.getWorld().playSound(l, name, SoundCategory.PLAYERS, vol, pitch);
    }

    static PotionEffectType potion(String name) {
        if (name == null) {
            return null;
        }
        String k = name.toLowerCase(Locale.ROOT);
        String key = POTION.getOrDefault(k, k);
        return Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
    }

    /** horizontal forward vector for a yaw (degrees, minecraft convention). */
    static Vector fwd(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    static Vector side(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.cos(r), 0, -Math.sin(r));
    }

    static Vector rotY(Vector v, double deg) {
        double r = Math.toRadians(deg);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new Vector(v.getX() * c - v.getZ() * s, v.getY(), v.getX() * s + v.getZ() * c);
    }

    static float yawOf(Vector v) {
        return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }
}
