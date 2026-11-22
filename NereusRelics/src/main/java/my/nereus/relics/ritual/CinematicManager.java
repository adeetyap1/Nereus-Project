package my.nereus.relics.ritual;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import my.nereus.relics.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.EulerAngle;
import java.util.Map;
import java.util.UUID;
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

    /** Sesi kamera aktif per pemain (untuk pemulihan aman). */
    private final Map<UUID, org.bukkit.entity.Entity> decoys = new ConcurrentHashMap<>();

    public boolean isFilming(UUID id) {
        return decoys.containsKey(id);
    }

    public void shutdown() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        for (UUID id : new ArrayList<>(decoys.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
            endCamera(id);
        }
    }

    private void endCamera(UUID id) {
        org.bukkit.entity.Entity st = decoys.remove(id);
        if (st != null) {
            plugin.clearLag().expect(st.getUniqueId());
            st.remove();
        }
    }

    /** Kembalikan mode permainan + lokasi pemain bila ada sisa sesi kamera (juga setelah crash). */
    public void restore(Player p) {
        String data = p.getPersistentDataContainer().get(plugin.keys().cameraRestore, PersistentDataType.STRING);
        if (data == null) return;
        p.getPersistentDataContainer().remove(plugin.keys().cameraRestore);
        endCamera(p.getUniqueId());
        try {
            String[] x = data.split("\\|");
            World w = Bukkit.getWorld(x[1]);
            Location l = w == null ? null : new Location(w, Double.parseDouble(x[2]), Double.parseDouble(x[3]),
                    Double.parseDouble(x[4]), Float.parseFloat(x[5]), Float.parseFloat(x[6]));
            GameMode gm = GameMode.valueOf(x[0]);
            if (l != null) p.teleport(l);
            p.setGameMode(gm);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Gagal memulihkan pemain setelah kamera: " + e);
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    /** Karakter pemain penuh (skin asli) memakai entity Mannequin (Minecraft 1.21.9+). */
    private org.bukkit.entity.Entity spawnMannequin(Player p, Location l) {
        return l.getWorld().spawn(l, org.bukkit.entity.Mannequin.class, m -> {
            m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
            m.setDescription(Text.color("&f" + p.getName()));
            m.setImmovable(true);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setPersistent(false);
            var eq = m.getEquipment();
            eq.setArmorContents(p.getInventory().getArmorContents());
            eq.setItemInMainHand(p.getInventory().getItemInMainHand().clone());
            eq.setItemInOffHand(p.getInventory().getItemInOffHand().clone());
        });
    }

    private org.bukkit.entity.Entity startCamera(Player p) {
        Location l = p.getLocation();
        String data = p.getGameMode().name() + "|" + l.getWorld().getName() + "|" + l.getX() + "|" + l.getY() + "|"
                + l.getZ() + "|" + l.getYaw() + "|" + l.getPitch();
        p.getPersistentDataContainer().set(plugin.keys().cameraRestore, PersistentDataType.STRING, data);
        org.bukkit.entity.Entity st = null;
        if (!"ARMOR_STAND".equalsIgnoreCase(plugin.getConfig().getString("cinematic.decoy", "MANNEQUIN"))) {
            try {
                st = spawnMannequin(p, l);
            } catch (Throwable t) {
                plugin.getLogger().warning("Mannequin tidak tersedia, memakai armor stand: " + t);
            }
        }
        if (st == null) {
            st = l.getWorld().spawn(l, ArmorStand.class, s -> {
            s.setArms(true);
            s.setBasePlate(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setPersistent(false);
            s.setRightArmPose(new EulerAngle(Math.toRadians(-165), 0, Math.toRadians(-10)));
            s.setLeftArmPose(new EulerAngle(Math.toRadians(-165), 0, Math.toRadians(10)));
            s.customName(Text.color("&f" + p.getName()));
            s.setCustomNameVisible(true);
            var eq = s.getEquipment();
            eq.setArmorContents(p.getInventory().getArmorContents());
            if (eq.getHelmet() == null || eq.getHelmet().getType().isAir()) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                sm.setOwningPlayer(p);
                head.setItemMeta(sm);
                eq.setHelmet(head);
            }
            eq.setItemInMainHand(p.getInventory().getItemInMainHand().clone());
        });
        }
        decoys.put(p.getUniqueId(), st);
        p.setGameMode(GameMode.SPECTATOR);
        return st;
    }

    public void play(Altar a, UUID winnerId, String winnerName, Runnable done) {
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
        final Player winner = Bukkit.getPlayer(winnerId);
        final boolean camera = c.getBoolean("cinematic.camera", true) && winner != null && winner.isOnline()
                && !winner.isDead() && winner.getWorld() == w && !decoys.containsKey(winnerId);
        final Location winnerLoc = camera ? winner.getLocation().clone() : null;
        final org.bukkit.entity.Entity decoy = camera ? startCamera(winner) : null;
        final Location mid = camera ? center.clone().add(winnerLoc).multiply(0.5) : center;
        final double camRadius = camera ? Math.min(16.0, center.distance(winnerLoc) / 2.0 + c.getDouble("cinematic.camera-extra-radius", 5.0)) : 0;
        final double camHeight = c.getDouble("cinematic.camera-height", 3.5);
        final double camTurns = c.getDouble("cinematic.camera-turns", 1.25);
        final double camStart = camera ? Math.atan2(winnerLoc.getZ() - center.getZ(), winnerLoc.getX() - center.getX()) + Math.PI : 0;
        final ItemDisplay disp = plugin.weapons().find(a);
        plugin.effects().sound(center, c.getString("cinematic.sound-start", ""), 3.0f, 0.7f);
        plugin.effects().sound(center, c.getString("cinematic.sound-rise", ""), 2.0f, 0.5f);

        if (darkness) {
            int dur = (int) (total * 0.75);
            for (Player p : viewers(w, center, radius)) {
                if (camera && p.getUniqueId().equals(winnerId)) continue;
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

            if (camera) {
                Player wp = Bukkit.getPlayer(winnerId);
                if (wp == null || !wp.isOnline() || !decoys.containsKey(winnerId)) {
                    endCamera(winnerId);
                } else {
                    double ca = camStart + camTurns * 2 * Math.PI * p;
                    double ch = mid.getY() + camHeight + 2.0 * p;
                    double cr = camRadius;
                    Location pos = new Location(w, mid.getX() + Math.cos(ca) * cr, ch, mid.getZ() + Math.sin(ca) * cr);
                    double tx = mid.getX(), ty = mid.getY() + 1.2 + lift * 0.5, tz = mid.getZ();
                    double dx = tx - pos.getX(), dy = ty - pos.getY(), dz = tz - pos.getZ();
                    pos.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                    pos.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                    wp.teleport(pos);
                    decoy.teleport(winnerLoc.clone().add(0, 1.6 * Math.min(1.0, p / 0.8), 0));
                }
            }

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
                if (camera) {
                    Player wp = Bukkit.getPlayer(winnerId);
                    if (wp != null) restore(wp);
                    endCamera(winnerId);
                }
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
