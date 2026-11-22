package my.nereus.relics.ritual;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import my.nereus.relics.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scene klaim: senjata naik berputar makin cepat, heliks partikel menyatu, layar menggelap, cincin kejut,
 * petir visual, lalu ledakan kembang api + judul besar. Hanya efek visual/suara; tidak mengubah data.
 */
public final class CinematicManager {

    private final NereusRelics plugin;
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();

    public CinematicManager(NereusRelics plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
    }

    public void play(Altar a, String winnerName, Runnable done) {
        FileConfiguration c = plugin.getConfig();
        Location base = plugin.altars().baseLocation(a);
        if (base == null || !base.getWorld().isChunkLoaded(a.location().chunkX(), a.location().chunkZ())) {
            done.run();
            return;
        }
        final World w = base.getWorld();
        final Location center = base.clone().add(0, plugin.altars().weaponHeight(a), 0);
        if (!c.getBoolean("cinematic.enabled", true)) {
            climax(a, winnerName, center);
            done.run();
            return;
        }

        final int total = Math.max(3, Math.min(30, c.getInt("cinematic.duration-seconds", 9))) * 20;
        final double rise = c.getDouble("cinematic.rise-height", 3.0);
        final double radius = c.getDouble("cinematic.viewer-radius", 48.0);
        final double baseScale = c.getDouble("weapon.scale", 1.4);
        final String helix = c.getString("cinematic.helix-particle", "end_rod");
        final String aura = c.getString("cinematic.aura-particle", "enchant");
        final String shock = c.getString("cinematic.shockwave-particle", "firework");
        final int strikes = Math.max(0, c.getInt("cinematic.lightning-strikes", 3));
        final boolean darkness = c.getBoolean("cinematic.darkness", true);

        plugin.holograms().remove(a);
        final ItemDisplay disp = plugin.weapons().find(a);
        plugin.effects().sound(center, c.getString("cinematic.sound-start", ""), 3.0f, 0.7f);
        plugin.effects().sound(center, c.getString("cinematic.sound-rise", ""), 2.0f, 0.5f);

        if (darkness) {
            int dur = (int) (total * 0.75);
            for (Player p : viewers(w, center, radius)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, dur, 0, false, false, false));
            }
        }

        final int[] tick = {0};
        final double[] angle = {0};
        final List<Integer> rings = new ArrayList<>();
        final boolean[] struck = new boolean[Math.max(1, strikes)];
        final BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0] += 2;
            double p = Math.min(1.0, tick[0] / (double) total);
            double lift = rise * Math.min(1.0, p / 0.8);
            Location cur = center.clone().add(0, lift, 0);

            if (disp != null && disp.isValid() && tick[0] % 10 == 0) {
                angle[0] += Math.toRadians(25 + 130 * p); // < 180 derajat per langkah agar interpolasi berputar searah
                float sc = (float) (baseScale * (1.0 + 0.8 * p));
                disp.setInterpolationDelay(0);
                disp.setInterpolationDuration(10);
                disp.setTransformation(new Transformation(new Vector3f(0, (float) lift, 0),
                        new Quaternionf().rotateY((float) angle[0]), new Vector3f(sc, sc, sc), new Quaternionf()));
            }

            // heliks yang menyatu ke senjata
            double rad = 3.0 * (1.0 - p) + 0.35;
            double t = tick[0] * 0.4;
            double yoff = ((tick[0] * 0.12) % 3.0) - 1.5;
            for (int k = 0; k < 2; k++) {
                double ang = t + k * Math.PI;
                plugin.effects().particle(cur.clone().add(Math.cos(ang) * rad, yoff, Math.sin(ang) * rad), helix, 1, 0, 0, 0, 0);
            }
            plugin.effects().particle(cur, aura, 6, 1.2 * (1.0 - p) + 0.3, 0.8, 1.2 * (1.0 - p) + 0.3, 0.4);

            // cincin kejut dari dasar altar
            if (p > 0.5 && tick[0] % 20 == 0) rings.add(0);
            for (int i = rings.size() - 1; i >= 0; i--) {
                int age = rings.get(i) + 1;
                if (age > 14) {
                    rings.remove(i);
                    continue;
                }
                rings.set(i, age);
                double r = age * 0.6;
                int points = 36;
                for (int n = 0; n < points; n++) {
                    double ang = 2 * Math.PI * n / points;
                    plugin.effects().particle(base.clone().add(Math.cos(ang) * r, 0.25, Math.sin(ang) * r), shock, 1, 0, 0, 0, 0);
                }
            }

            // sambaran petir visual
            for (int s = 0; s < strikes; s++) {
                double at = 0.55 + (0.4 * (s + 1) / (strikes + 1));
                if (!struck[s] && p >= at) {
                    struck[s] = true;
                    double ang = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                    w.strikeLightningEffect(base.clone().add(Math.cos(ang) * 4.0, 0, Math.sin(ang) * 4.0));
                }
            }

            if (tick[0] >= total) {
                self[0].cancel();
                tasks.remove(self[0]);
                for (Player pl : viewers(w, center, radius + 16)) pl.removePotionEffect(PotionEffectType.DARKNESS);
                climax(a, winnerName, cur);
                done.run();
            }
        }, 2L, 2L);
        tasks.add(self[0]);
    }

    private List<Player> viewers(World w, Location at, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= radius * radius) out.add(p);
        }
        return out;
    }

    private void climax(Altar a, String winnerName, Location at) {
        FileConfiguration c = plugin.getConfig();
        World w = at.getWorld();
        plugin.effects().particle(at, c.getString("effects.ritual-finish-particle", "totem_of_undying"), 160, 0.8, 0.8, 0.8, 0.45);
        plugin.effects().particle(at, "explosion_emitter", 1, 0, 0, 0, 0);
        plugin.effects().sound(at, plugin.altars().str(a, "claim-sound", "effects.claim-sound"), 3.0f, 1.0f);
        plugin.effects().sound(at, c.getString("cinematic.sound-climax", ""), 3.0f, 1.0f);

        if (c.getBoolean("cinematic.fireworks", true)) {
            Color[][] palettes = {{Color.ORANGE, Color.YELLOW}, {Color.fromRGB(0xFFD700), Color.WHITE}, {Color.fromRGB(0xFF5500), Color.fromRGB(0xFFAA00)}};
            for (int i = 0; i < 3; i++) {
                Location l = at.clone().add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0.5, ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
                final Color[] pal = palettes[i];
                Firework fw = w.spawn(l, Firework.class, f -> {
                    FireworkMeta m = f.getFireworkMeta();
                    m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(pal).withFade(Color.WHITE).flicker(true).trail(true).build());
                    m.setPower(0);
                    f.setFireworkMeta(m);
                });
                fw.detonate();
            }
        }

        String t = c.getString("cinematic.title", "&6&l{weapon}").replace("{weapon}", a.weaponName()).replace("{player}", winnerName);
        String st = c.getString("cinematic.subtitle", "").replace("{weapon}", a.weaponName()).replace("{player}", winnerName);
        Title title = Title.title(Text.color(t), Text.color(st),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        boolean everyone = c.getBoolean("cinematic.server-wide-title", true);
        double radius = c.getDouble("cinematic.viewer-radius", 48.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (everyone || (p.getWorld() == w && p.getLocation().distanceSquared(at) <= radius * radius)) p.showTitle(title);
        }
    }
}
