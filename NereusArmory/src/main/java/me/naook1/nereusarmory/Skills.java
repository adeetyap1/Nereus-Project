package me.naook1.nereusarmory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Weapon skills, passives and the Abyssal set bonus. */
public final class Skills implements Listener {

    private final NereusArmory plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private boolean inSkill;

    public Skills(NereusArmory plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- helpers

    private boolean ready(Player p, String skill, String label, int seconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> m = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Long until = m.get(skill);
        if (until != null && until > now) {
            long left = (until - now + 999) / 1000;
            p.sendActionBar(Text.parse("&b" + label + " &7- ready in &f" + left + "s"));
            return false;
        }
        m.put(skill, now + seconds * 1000L);
        p.sendActionBar(Text.parse("&b&l" + label + "!"));
        return true;
    }

    private boolean canHit(Player p, Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand || e.equals(p) || e.isDead()) {
            return false;
        }
        if (e instanceof Player t && (t.getGameMode() == GameMode.CREATIVE || t.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        if (e instanceof Tameable tm && p.equals(tm.getOwner())) {
            return false;
        }
        return true;
    }

    private void hurt(Player p, LivingEntity t, double dmg) {
        inSkill = true;
        try {
            t.damage(dmg, p);
        } finally {
            inSkill = false;
        }
    }

    private List<LivingEntity> near(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (canHit(p, e) && e.getLocation().distanceSquared(c) <= r * r) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    private static void line(World w, Location a, Location b, Particle pt, int per) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) {
            return;
        }
        d.normalize();
        for (double i = 0; i < len; i += 0.35) {
            Location l = a.clone().add(d.clone().multiply(i));
            w.spawnParticle(pt, l, per, 0.05, 0.05, 0.05, 0);
        }
    }

    private static boolean wet(Player p) {
        if (p.isInWater() || p.isUnderWater()) {
            return true;
        }
        World w = p.getWorld();
        return w.hasStorm() && p.getLocation().getY() >= w.getHighestBlockYAt(p.getLocation());
    }

    // ---------------------------------------------------------------- right click skills

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = e.getPlayer();
        String id = plugin.items().idOf(p.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        var c = plugin.getConfig();
        switch (id) {
            case "tidecaller" -> {
                if (ready(p, id, "Tidal Surge", c.getInt("skills.tidal-surge.cooldown", 12))) {
                    tidalSurge(p);
                }
            }
            case "frostpearl" -> {
                if (ready(p, id, "Frost Nova", c.getInt("skills.frost-nova.cooldown", 16))) {
                    frostNova(p);
                }
            }
            case "stormpiercer" -> {
                if (ready(p, id, "Thunder Judgment", c.getInt("skills.thunder-judgment.cooldown", 18))) {
                    thunder(p);
                }
            }
            default -> {
                return;
            }
        }
        e.setCancelled(true);
    }

    private void tidalSurge(Player p) {
        double dmg = plugin.getConfig().getDouble("skills.tidal-surge.damage", 8.0);
        double range = plugin.getConfig().getDouble("skills.tidal-surge.range", 14);
        World w = p.getWorld();
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(0, 0, 1);
        }
        final Vector fwd = dir.normalize();
        final Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.4f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1.2f, 0.6f);
        final Set<UUID> hit = new HashSet<>();
        final Location origin = p.getLocation().clone().add(0, 0.2, 0);
        new BukkitRunnable() {
            double d = 1.5;

            @Override
            public void run() {
                if (d > range || !p.isOnline()) {
                    cancel();
                    return;
                }
                Location c = origin.clone().add(fwd.clone().multiply(d));
                // snap to ground
                int gy = w.getHighestBlockYAt(c);
                if (Math.abs(gy - c.getY()) < 4) {
                    c.setY(gy + 0.3);
                }
                double width = 1.8 + d * 0.12;
                for (double s = -width; s <= width; s += 0.45) {
                    Location l = c.clone().add(side.clone().multiply(s));
                    double h = 0.6 + 0.9 * Math.cos(s / width * Math.PI / 2);
                    w.spawnParticle(Particle.SPLASH, l.clone().add(0, h * 0.5, 0), 6, 0.2, h * 0.4, 0.2, 0.1);
                    w.spawnParticle(Particle.BUBBLE_POP, l.clone().add(0, h, 0), 2, 0.2, 0.2, 0.2, 0.02);
                    w.spawnParticle(Particle.DUST, l.clone().add(0, h * 0.8, 0), 3, 0.2, 0.25, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(47, 208, 200), 1.6f));
                }
                w.spawnParticle(Particle.NAUTILUS, c.clone().add(0, 1.2, 0), 10, width * 0.5, 0.5, width * 0.5, 0.4);
                for (LivingEntity t : near(p, c.clone().add(0, 1, 0), width + 0.6)) {
                    if (hit.add(t.getUniqueId())) {
                        hurt(p, t, dmg);
                        t.setVelocity(fwd.clone().multiply(1.1).setY(0.55));
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        w.playSound(t.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 0.8f);
                    }
                }
                d += 1.3;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void frostNova(Player p) {
        double dmg = plugin.getConfig().getDouble("skills.frost-nova.damage", 7.0);
        double radius = plugin.getConfig().getDouble("skills.frost-nova.radius", 7);
        World w = p.getWorld();
        final Location c = p.getLocation().clone();
        w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        w.playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.8f);
        w.playSound(c, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 0.5f);
        for (LivingEntity t : near(p, c, radius)) {
            hurt(p, t, dmg);
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3));
            t.setFreezeTicks(t.getMaxFreezeTicks() + 100);
        }
        new BukkitRunnable() {
            double r = 0.8;

            @Override
            public void run() {
                if (r > radius) {
                    cancel();
                    return;
                }
                int pts = (int) (14 + r * 10);
                for (int i = 0; i < pts; i++) {
                    double a = i * 2 * Math.PI / pts;
                    double x = c.getX() + Math.cos(a) * r, z = c.getZ() + Math.sin(a) * r;
                    Location l = new Location(w, x, c.getY() + 0.15, z);
                    w.spawnParticle(Particle.SNOWFLAKE, l, 2, 0.05, 0.25, 0.05, 0.02);
                    w.spawnParticle(Particle.DUST, l.clone().add(0, 0.4 + Math.random() * 0.8, 0), 1, 0.05, 0.3, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(160, 232, 255), 1.7f));
                    if (i % 4 == 0) {
                        w.spawnParticle(Particle.END_ROD, l.clone().add(0, 0.6, 0), 1, 0, 0.5, 0, 0.01);
                    }
                }
                r += 0.9;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        // ice spikes bursting upward around the caster
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            double rr = 2 + (i % 3) * 1.4;
            Location b = c.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            for (double h = 0; h < 2.2 + (i % 3) * 0.6; h += 0.3) {
                w.spawnParticle(Particle.DUST, b.clone().add(0, h, 0), 2, 0.04, 0.04, 0.04, 0,
                        new Particle.DustOptions(Color.fromRGB(210, 245, 255), 1.9f - (float) h * 0.4f));
            }
        }
    }

    private void thunder(Player p) {
        var c = plugin.getConfig();
        double dmg = c.getDouble("skills.thunder-judgment.damage", 10.0);
        double chainDmg = c.getDouble("skills.thunder-judgment.chain-damage", 6.0);
        int chains = c.getInt("skills.thunder-judgment.chain-targets", 3);
        double range = c.getDouble("skills.thunder-judgment.range", 28);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        RayTraceResult r = w.rayTraceEntities(eye, eye.getDirection(), range, 0.9, e -> canHit(p, e));
        LivingEntity first = null;
        Location strike;
        if (r != null && r.getHitEntity() instanceof LivingEntity le) {
            first = le;
            strike = le.getLocation();
        } else {
            RayTraceResult b = w.rayTraceBlocks(eye, eye.getDirection(), range, org.bukkit.FluidCollisionMode.NEVER);
            strike = b != null ? b.getHitPosition().toLocation(w) : eye.clone().add(eye.getDirection().multiply(range));
        }
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.4f, 1.2f);
        line(w, eye.clone().add(0, -0.3, 0), strike.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 2);
        w.strikeLightningEffect(strike);
        List<LivingEntity> victims = new ArrayList<>();
        if (first != null) {
            victims.add(first);
        } else {
            victims.addAll(near(p, strike, 3.5));
        }
        Set<UUID> done = new HashSet<>();
        Location from = strike;
        for (LivingEntity t : new ArrayList<>(victims)) {
            hurt(p, t, dmg);
            done.add(t.getUniqueId());
        }
        LivingEntity cur = first;
        for (int i = 0; i < chains && (cur != null || !victims.isEmpty()); i++) {
            Location at = cur != null ? cur.getLocation() : victims.get(0).getLocation();
            LivingEntity next = null;
            double best = 36;
            for (LivingEntity t : near(p, at, 6)) {
                double d = t.getLocation().distanceSquared(at);
                if (!done.contains(t.getUniqueId()) && d < best) {
                    best = d;
                    next = t;
                }
            }
            if (next == null) {
                break;
            }
            done.add(next.getUniqueId());
            line(w, at.clone().add(0, 1, 0), next.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 2);
            w.strikeLightningEffect(next.getLocation());
            hurt(p, next, chainDmg);
            cur = next;
        }
        w.spawnParticle(Particle.FLASH, strike, 1);
        w.spawnParticle(Particle.DUST, strike.clone().add(0, 1, 0), 30, 1.2, 1.2, 1.2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 226, 80), 2.2f));
    }

    // ---------------------------------------------------------------- weapon passives

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (inSkill || !(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity t)) {
            return;
        }
        String id = plugin.items().idOf(p.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        World w = t.getWorld();
        Location l = t.getLocation().add(0, 1, 0);
        switch (id) {
            case "tidecaller" -> {
                if (wet(p)) {
                    e.setDamage(e.getDamage() * 1.25);
                    w.spawnParticle(Particle.SPLASH, l, 25, 0.4, 0.5, 0.4, 0.2);
                }
            }
            case "frostpearl" -> {
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                t.setFreezeTicks(Math.min(t.getMaxFreezeTicks() + 60, t.getFreezeTicks() + 60));
                w.spawnParticle(Particle.SNOWFLAKE, l, 18, 0.4, 0.5, 0.4, 0.05);
            }
            case "stormpiercer" -> {
                if (Math.random() < 0.15) {
                    e.setDamage(e.getDamage() + 3.0);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, l, 30, 0.4, 0.6, 0.4, 0.3);
                    w.playSound(l, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- Abyssal set

    /** How many pieces of the given set the player wears (each in its own slot). */
    public int pieces(Player p, String set) {
        int n = 0;
        var inv = p.getInventory();
        ItemStack[] worn = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        for (int i = 0; i < 4; i++) {
            String id = plugin.items().idOf(worn[i]);
            if (id != null && ArmoryItems.slotOf(id) == i) {
                var d = plugin.items().def(id);
                if (d != null && !d.weapon() && d.set().equals(set)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void eff(Player p, PotionEffectType t, int amp) {
        p.addPotionEffect(new PotionEffect(t, 80, amp, true, false, false));
    }

    /** Every second: passive set effects. */
    public void tickSet() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int n = pieces(p, "pearl");
            if (n >= 2) {
                boolean inWet = wet(p);
                eff(p, PotionEffectType.WATER_BREATHING, 0);
                if (n >= 3 && p.isInWater()) {
                    eff(p, PotionEffectType.DOLPHINS_GRACE, 0);
                }
                if (n >= 4 && inWet) {
                    eff(p, PotionEffectType.CONDUIT_POWER, 0);
                    if (p.getTicksLived() % 80 < 20) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, false));
                    }
                }
            }
            n = pieces(p, "dragon");
            if (n >= 2) {
                eff(p, PotionEffectType.FIRE_RESISTANCE, 0);
            }
            if (n >= 3) {
                eff(p, PotionEffectType.STRENGTH, 0);
            }
            if (n >= 4) {
                eff(p, PotionEffectType.RESISTANCE, 0);
            }
            n = pieces(p, "frost");
            if (n >= 2) {
                eff(p, PotionEffectType.SPEED, 0);
            }
            if (n >= 3) {
                eff(p, PotionEffectType.SLOW_FALLING, 0);
            }
            if (n >= 4) {
                eff(p, PotionEffectType.JUMP_BOOST, 0);
            }
        }
    }

    private double auraStep;

    /** Every 4 ticks: water aura around full-set wearers (server particles, visible to Java and Bedrock). */
    public void aura() {
        if (!plugin.getConfig().getBoolean("set-bonus.aura", true)) {
            return;
        }
        auraStep += 0.35;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isInvisible() || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location b = p.getLocation();
            World w = p.getWorld();
            if (pieces(p, "pearl") >= 4) {
                for (int i = 0; i < 3; i++) {
                    double a = auraStep * 2 + i * 2.094;
                    double h = 0.2 + ((auraStep * 0.6 + i * 0.7) % 1.8);
                    w.spawnParticle(Particle.DUST, b.getX() + Math.cos(a) * 0.75, b.getY() + h, b.getZ() + Math.sin(a) * 0.75,
                            1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(47, 208, 200), 1.1f));
                }
                if (Math.random() < 0.3) {
                    w.spawnParticle(Particle.DRIPPING_WATER, b.clone().add(0, 1.9, 0), 1, 0.3, 0.1, 0.3, 0);
                }
            }
            if (pieces(p, "dragon") >= 4) {
                for (int i = 0; i < 2; i++) {
                    double a = auraStep * 2.4 + i * Math.PI;
                    double h = 0.1 + ((auraStep * 0.7 + i) % 1.9);
                    w.spawnParticle(Particle.FLAME, b.getX() + Math.cos(a) * 0.7, b.getY() + h, b.getZ() + Math.sin(a) * 0.7, 1, 0, 0.02, 0, 0.005);
                }
                if (Math.random() < 0.25) {
                    w.spawnParticle(Particle.SMOKE, b.clone().add(0, 2.1, 0), 1, 0.25, 0.1, 0.25, 0.01);
                }
            }
            if (pieces(p, "frost") >= 4) {
                for (int i = 0; i < 2; i++) {
                    double a = auraStep * 1.8 + i * Math.PI;
                    double h = 0.1 + ((auraStep * 0.5 + i) % 2.0);
                    w.spawnParticle(Particle.SNOWFLAKE, b.getX() + Math.cos(a) * 0.8, b.getY() + h, b.getZ() + Math.sin(a) * 0.8, 1, 0.05, 0.05, 0.05, 0.0);
                }
                if (Math.random() < 0.3) {
                    w.spawnParticle(Particle.END_ROD, b.clone().add(0, 2.0, 0), 1, 0.3, 0.2, 0.3, 0.01);
                }
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) {
            return;
        }
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastSneak.put(p.getUniqueId(), now);
        if (last != null && now - last < 500) {
            var c = plugin.getConfig();
            if (pieces(p, "pearl") >= 4) {
                lastSneak.remove(p.getUniqueId());
                if (ready(p, "tsunami", "TSUNAMI", c.getInt("set-bonus.tsunami.cooldown", 45))) {
                    tsunami(p);
                }
            } else if (pieces(p, "dragon") >= 4) {
                lastSneak.remove(p.getUniqueId());
                if (ready(p, "roar", "DRAGON ROAR", c.getInt("set-bonus.dragon-roar.cooldown", 40))) {
                    shockwave(p, c.getDouble("set-bonus.dragon-roar.damage", 8.0), c.getDouble("set-bonus.dragon-roar.radius", 8), true);
                }
            } else if (pieces(p, "frost") >= 4) {
                lastSneak.remove(p.getUniqueId());
                if (ready(p, "blizzard", "BLIZZARD", c.getInt("set-bonus.blizzard.cooldown", 40))) {
                    shockwave(p, c.getDouble("set-bonus.blizzard.damage", 6.0), c.getDouble("set-bonus.blizzard.radius", 9), false);
                }
            }
        }
    }

    /** Dragon Roar (fire) and Blizzard (ice): expanding ring that hits everything around the wearer. */
    private void shockwave(Player p, double dmg, double radius, boolean fire) {
        World w = p.getWorld();
        final Location c = p.getLocation().clone();
        if (fire) {
            w.playSound(c, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.8f);
            w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
        } else {
            w.playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.6f);
            w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
            w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.6f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2));
        }
        final Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double r = 1.0;

            @Override
            public void run() {
                if (r > radius) {
                    cancel();
                    return;
                }
                int pts = (int) (20 + r * 12);
                for (int i = 0; i < pts; i++) {
                    double a = i * 2 * Math.PI / pts;
                    Location l = new Location(w, c.getX() + Math.cos(a) * r, c.getY() + 0.2, c.getZ() + Math.sin(a) * r);
                    if (fire) {
                        w.spawnParticle(Particle.FLAME, l, 2, 0.1, 0.4, 0.1, 0.03);
                        if (i % 5 == 0) {
                            w.spawnParticle(Particle.LAVA, l.clone().add(0, 0.5, 0), 1);
                            w.spawnParticle(Particle.SMOKE, l.clone().add(0, 1.0, 0), 2, 0.2, 0.3, 0.2, 0.02);
                        }
                    } else {
                        w.spawnParticle(Particle.SNOWFLAKE, l, 3, 0.1, 0.5, 0.1, 0.03);
                        if (i % 5 == 0) {
                            w.spawnParticle(Particle.END_ROD, l.clone().add(0, 0.8, 0), 1, 0.1, 0.3, 0.1, 0.02);
                        }
                    }
                }
                for (LivingEntity t : near(p, c, r + 0.8)) {
                    if (hit.add(t.getUniqueId())) {
                        hurt(p, t, dmg);
                        Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
                        if (away.lengthSquared() < 0.01) {
                            away = new Vector(0, 0, 1);
                        }
                        t.setVelocity(away.normalize().multiply(1.3).setY(0.5));
                        if (fire) {
                            t.setFireTicks(80);
                        } else {
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
                            t.setFreezeTicks(t.getMaxFreezeTicks() + 100);
                        }
                    }
                }
                r += 0.8;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tsunami(Player p) {
        double dmg = plugin.getConfig().getDouble("set-bonus.tsunami.damage", 9.0);
        double radius = plugin.getConfig().getDouble("set-bonus.tsunami.radius", 9);
        World w = p.getWorld();
        final Location c = p.getLocation().clone();
        w.playSound(c, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2f, 0.5f);
        w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
        w.playSound(c, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
        p.showTitle(net.kyori.adventure.title.Title.title(Text.parse("&#2fd0c8&lTSUNAMI"), Text.parse("&7The abyss answers"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(700), java.time.Duration.ofMillis(300))));
        final Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double r = 1.0;

            @Override
            public void run() {
                if (r > radius) {
                    cancel();
                    return;
                }
                int pts = (int) (20 + r * 12);
                for (int i = 0; i < pts; i++) {
                    double a = i * 2 * Math.PI / pts;
                    Location l = new Location(w, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r);
                    double h = 1.2 + Math.sin(a * 5 + r) * 0.5;
                    for (double y = 0.2; y < h; y += 0.45) {
                        w.spawnParticle(Particle.SPLASH, l.clone().add(0, y, 0), 2, 0.1, 0.1, 0.1, 0.05);
                    }
                    w.spawnParticle(Particle.DUST, l.clone().add(0, h, 0), 1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(47, 208, 200), 2.2f));
                    if (i % 6 == 0) {
                        w.spawnParticle(Particle.BUBBLE_COLUMN_UP, l.clone().add(0, 0.4, 0), 3, 0.2, 0.3, 0.2, 0);
                    }
                }
                for (LivingEntity t : near(p, c, r + 0.8)) {
                    if (hit.add(t.getUniqueId())) {
                        hurt(p, t, dmg);
                        Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
                        if (away.lengthSquared() < 0.01) {
                            away = new Vector(0, 0, 1);
                        }
                        t.setVelocity(away.normalize().multiply(1.6).setY(0.7));
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                    }
                }
                r += 0.8;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
