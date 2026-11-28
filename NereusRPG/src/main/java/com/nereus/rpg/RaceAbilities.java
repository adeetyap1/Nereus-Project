package com.nereus.rpg;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kemampuan pasif 8 ras. Hampir semuanya berbasis event; hanya kemampuan yang bergantung pada lingkungan
 * (cahaya, biome, ketinggian) dicek oleh SATU timer 2 detik yang melewati pemain tanpa kemampuan itu dengan cepat.
 */
public final class RaceAbilities implements Listener {
    private static final Set<Material> PLANT_FOOD = Set.of(Material.APPLE, Material.GOLDEN_APPLE, Material.CARROT,
            Material.GOLDEN_CARROT, Material.BAKED_POTATO, Material.BEETROOT, Material.BEETROOT_SOUP,
            Material.MELON_SLICE, Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.DRIED_KELP,
            Material.MUSHROOM_STEW, Material.PUMPKIN_PIE, Material.POTATO);
    private static final Set<Material> MEAT = Set.of(Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP,
            Material.COOKED_PORKCHOP, Material.MUTTON, Material.COOKED_MUTTON, Material.CHICKEN,
            Material.COOKED_CHICKEN, Material.RABBIT, Material.COOKED_RABBIT, Material.COD, Material.SALMON,
            Material.COOKED_COD, Material.COOKED_SALMON);
    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART);
    private static final String[] FOREST = {"forest", "taiga", "jungle", "grove", "cherry", "birch", "old_growth"};

    private final NereusRPG plugin;
    private final Core core;
    private final Map<String, Long> cd = new HashMap<>();
    private final Set<Long> skipDrops = new HashSet<>();

    public RaceAbilities(NereusRPG plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
        Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 100L, 40L);
    }

    private boolean ready(Player p, String id, double seconds) {
        String k = p.getUniqueId() + id;
        long now = System.currentTimeMillis();
        Long until = cd.get(k);
        if (until != null && until > now) {
            return false;
        }
        cd.put(k, now + (long) (seconds * 1000));
        return true;
    }

    private boolean roll(double percent) {
        return percent > 0 && core.rng.nextDouble() * 100 < percent;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String id = e.getPlayer().getUniqueId().toString();
        cd.keySet().removeIf(k -> k.startsWith(id));
    }

    // ------------------------------------------------------------ lingkungan (1 timer, 2 detik)

    private static void effect(Player p, PotionEffectType t, int amp) {
        p.addPotionEffect(new PotionEffect(t, 60, amp, true, false, true));
    }

    private static boolean sunlit(Player p) {
        World w = p.getWorld();
        return w.getEnvironment() == World.Environment.NORMAL && w.isDayTime() && !w.hasStorm()
                && p.getLocation().getBlock().getLightFromSky() >= 15;
    }

    private void pulse() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) {
                continue;
            }
            Profile pr = core.profile(p);
            Defs.Race r = pr.member() ? core.clazz(pr) : null;
            if (r == null || r.abilities().isEmpty()) {
                continue;
            }
            Map<String, Defs.Ability> a = r.abilities();
            Block b = p.getLocation().getBlock();
            double y = p.getLocation().getY();
            if (a.containsKey("forest_stride")) {
                String biome = b.getBiome().getKey().getKey();
                for (String f : FOREST) {
                    if (biome.contains(f)) {
                        effect(p, PotionEffectType.SPEED, (int) a.get("forest_stride").v("amplifier", 0));
                        break;
                    }
                }
            }
            if (a.containsKey("starlight_eyes") && b.getLightLevel() <= a.get("starlight_eyes").v("max_light", 4)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, false));
            }
            if (a.containsKey("dark_regeneration") && b.getLightLevel() <= a.get("dark_regeneration").v("max_light", 4)) {
                effect(p, PotionEffectType.REGENERATION, 0);
            }
            if (a.containsKey("deep_dweller") && y < a.get("deep_dweller").v("max_y", 40)) {
                effect(p, PotionEffectType.HASTE, 0);
            }
            if (a.containsKey("stonesense") && y < a.get("stonesense").v("max_y", 60)
                    && p.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")) {
                effect(p, PotionEffectType.HASTE, 0);
            }
            if (a.containsKey("sun_sickness") && sunlit(p)) {
                ItemStack helm = p.getInventory().getHelmet();
                if (helm == null || helm.getType().isAir()) {
                    effect(p, PotionEffectType.WEAKNESS, 0);
                }
            }
            if (a.containsKey("cold_blooded") && b.getTemperature() < 0.2) {
                effect(p, PotionEffectType.SLOWNESS, 0);
            }
        }
    }

    // ------------------------------------------------------------ pertarungan

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        boolean proj = d instanceof Projectile;
        Player atk = null;
        if (d instanceof Player p) {
            atk = p;
        } else if (d instanceof Projectile pj && pj.getShooter() instanceof Player p
                && !pj.getScoreboardTags().contains(Skills.SKILL_TAG)) {
            atk = p;
        }
        // sisi korban: menghindar
        if (e.getEntity() instanceof Player v) {
            Defs.Ability dodge = proj ? core.ability(v, "pixie_dust") : core.ability(v, "evasion");
            if (dodge != null && roll(dodge.v("chance", 10))) {
                e.setCancelled(true);
                v.getWorld().spawnParticle(Particle.CLOUD, v.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.02);
                v.sendActionBar(Text.c("&bKamu menghindar!"));
                return;
            }
            Defs.Ability del = core.ability(v, "delicate");
            if (del != null && !proj) {
                e.setDamage(e.getDamage() * (1 + del.v("melee_taken", 10) / 100.0));
            }
        }
        if (atk == null || Skills.damaging || e.getEntity().equals(atk)) {
            return;
        }
        double pos = 0;
        double neg = 0;
        LivingEntity target = e.getEntity() instanceof LivingEntity le ? le : null;
        ItemStack held = atk.getInventory().getItemInMainHand();
        boolean arrow = d instanceof AbstractArrow && !(d instanceof Trident);
        Defs.Ability x;
        if (!proj) {
            if ((x = core.ability(atk, "brute_force")) != null) {
                pos += x.v("melee_percent", 10);
            }
            if ((x = core.ability(atk, "heavy_blows")) != null && held.getType().name().endsWith("_AXE")) {
                pos += x.v("percent", 15);
            }
            if ((x = core.ability(atk, "backstab")) != null && target != null) {
                Vector facing = target.getLocation().getDirection().setY(0);
                Vector toAtk = atk.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                if (facing.lengthSquared() > 0 && toAtk.lengthSquared() > 0
                        && facing.normalize().dot(toAtk.normalize()) < -0.5) {
                    pos += x.v("percent", 25);
                    atk.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                }
            }
            if (target != null) {
                if ((x = core.ability(atk, "dragons_bite")) != null && roll(x.v("chance", 10))) {
                    target.setFireTicks((int) (x.v("seconds", 4) * 20));
                }
                if ((x = core.ability(atk, "venom_edge")) != null && roll(x.v("chance", 12))) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int) (x.v("seconds", 4) * 20), 0));
                }
            }
        } else if (arrow) {
            if ((x = core.ability(atk, "elven_archery")) != null) {
                pos += x.v("bow_percent", 12);
            }
            if ((x = core.ability(atk, "clumsy_hands")) != null) {
                neg += x.v("bow_penalty", 25);
            }
        }
        if ((x = core.ability(atk, "berserk_heart")) != null) {
            var hp = atk.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = hp == null ? 20 : hp.getValue();
            if (atk.getHealth() <= max * x.v("threshold", 0.5)) {
                pos += x.v("percent", 15);
            }
        }
        World w = atk.getWorld();
        if ((x = core.ability(atk, "night_predator")) != null && w.getEnvironment() == World.Environment.NORMAL && !w.isDayTime()) {
            pos += x.v("percent", 12);
        }
        if ((x = core.ability(atk, "ancient_might")) != null && w.getEnvironment() != World.Environment.NORMAL) {
            pos += x.v("percent", 12);
        }
        if ((x = core.ability(atk, "sun_blinded")) != null && sunlit(atk)) {
            neg += x.v("percent", 12);
        }
        if (e.getEntity() instanceof Player) {
            pos *= plugin.getConfig().getDouble("races.pvp-scale", 0.6);
        }
        double m = Math.max(0.5, Math.min(1.75, 1 + (pos - neg) / 100.0));
        e.setDamage(e.getDamage() * m);
        if (!proj && (x = core.ability(atk, "blood_drain")) != null) {
            double scale = e.getEntity() instanceof Player ? plugin.getConfig().getDouble("races.pvp-scale", 0.6) : 1.0;
            double heal = Math.min(x.v("cap", 3), e.getDamage() * x.v("percent", 10) / 100.0) * scale;
            var hp = atk.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (heal > 0 && hp != null) {
                atk.setHealth(Math.min(hp.getValue(), atk.getHealth() + heal));
                atk.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, atk.getLocation().add(0, 1.4, 0), 3, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    /** Pengurangan/penambahan damage yang diterima: ras, ketinggian, dan stat DEF. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v)) {
            return;
        }
        EntityDamageEvent.DamageCause c = e.getCause();
        Defs.Ability x;
        if (c == EntityDamageEvent.DamageCause.FALL && core.ability(v, "featherweight") != null) {
            e.setCancelled(true);
            return;
        }
        double f = 1.0;
        switch (c) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                if ((x = core.ability(v, "scaled_hide")) != null) {
                    f *= 1 - x.v("percent", 40) / 100.0;
                }
            }
            case MAGIC, POISON, WITHER, DRAGON_BREATH -> {
                if ((x = core.ability(v, "mortal_frame")) != null) {
                    f *= 1 + x.v("percent", 15) / 100.0;
                }
                if ((c == EntityDamageEvent.DamageCause.POISON || c == EntityDamageEvent.DamageCause.WITHER)
                        && (x = core.ability(v, "iron_constitution")) != null) {
                    f *= 1 - x.v("percent", 50) / 100.0;
                }
            }
            default -> {
            }
        }
        if ((x = core.ability(v, "mountain_heart")) != null && v.getLocation().getY() < x.v("max_y", 50)) {
            f *= 1 - x.v("percent", 10) / 100.0;
        }
        if (c != EntityDamageEvent.DamageCause.VOID && c != EntityDamageEvent.DamageCause.SUICIDE
                && c != EntityDamageEvent.DamageCause.KILL && c != EntityDamageEvent.DamageCause.STARVATION) {
            Profile pr = core.profile(v);
            if (pr.member()) {
                var cfg = plugin.getConfig();
                double red = Math.min(cfg.getDouble("stats.def-reduction-cap-percent", 35),
                        pr.stat("def") * cfg.getDouble("stats.def-reduction-percent", 0.5));
                f *= 1 - red / 100.0;
            }
        }
        if (f != 1.0) {
            e.setDamage(e.getDamage() * Math.max(0.2, f));
        }
    }

    /** Pemicu saat darah rendah: Napas Kedua (Human) dan Raungan Naga (Dragonborn). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v)) {
            return;
        }
        var hp = v.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = hp == null ? 20 : hp.getValue();
        double after = v.getHealth() - e.getFinalDamage();
        if (after <= 0) {
            return;
        }
        Defs.Ability x = core.ability(v, "second_wind");
        if (x != null && after <= max * x.v("threshold", 0.3) && ready(v, "second_wind", x.v("cooldown", 120))) {
            double heal = x.v("heal", 6);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (v.isOnline() && !v.isDead()) {
                    v.setHealth(Math.min(max, v.getHealth() + heal));
                    v.getWorld().spawnParticle(Particle.HEART, v.getLocation().add(0, 1.8, 0), 6, 0.3, 0.2, 0.3, 0);
                    v.sendActionBar(Text.c("&aNapas Kedua!"));
                }
            });
        }
        x = core.ability(v, "draconic_roar");
        if (x != null && after <= max * x.v("threshold", 0.25) && ready(v, "draconic_roar", x.v("cooldown", 90))) {
            v.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int) (x.v("seconds", 5) * 20), 0));
            v.getWorld().spawnParticle(Particle.FLAME, v.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
            v.sendActionBar(Text.c("&6Raungan Naga!"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player k = dead.getKiller();
        if (k == null || dead instanceof Player || dead instanceof ArmorStand) {
            return;
        }
        Defs.Ability x;
        if ((x = core.ability(k, "bloodlust")) != null) {
            k.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int) (x.v("seconds", 6) * 20), 0));
        }
        if ((x = core.ability(k, "hunters_rush")) != null) {
            k.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (x.v("seconds", 4) * 20), 0));
        }
        if ((x = core.ability(k, "sanguine")) != null) {
            k.setFoodLevel(Math.min(20, k.getFoodLevel() + (int) x.v("food", 2)));
            k.setSaturation((float) Math.min(k.getFoodLevel(), k.getSaturation() + x.v("saturation", 3)));
        }
    }

    // ------------------------------------------------------------ makanan, alat, pertanian

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFoodLevel() >= p.getFoodLevel()) {
            return;
        }
        Defs.Ability x = core.ability(p, "survivors_grit");
        if (x != null && roll(x.v("chance", 15))) {
            e.setCancelled(true);
            return;
        }
        x = core.ability(p, "cold_blooded");
        if (x != null && roll(x.v("hunger_chance", 20))) {
            e.setFoodLevel(Math.max(0, e.getFoodLevel() - 1));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        Material m = e.getItem().getType();
        double sat = 0;
        Defs.Ability x;
        if (PLANT_FOOD.contains(m) && (x = core.ability(p, "natures_meal")) != null) {
            sat += x.v("saturation", 3);
        }
        if (MEAT.contains(m) && (x = core.ability(p, "carnivore")) != null) {
            sat += x.v("saturation", 3);
        }
        if (sat > 0) {
            double add = sat;
            Bukkit.getScheduler().runTask(plugin, () -> p.setSaturation((float) Math.min(p.getFoodLevel(), p.getSaturation() + add)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolDamage(PlayerItemDamageEvent e) {
        Defs.Ability x = core.ability(e.getPlayer(), "efficient_hands");
        if (x != null && roll(x.v("chance", 8))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.FARMLAND && core.ability(e.getPlayer(), "light_step") != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (e.getEntity() instanceof Player p && e.getNewEffect() != null && core.ability(p, "iron_gut") != null) {
            PotionEffectType t = e.getNewEffect().getType();
            if (t.equals(PotionEffectType.HUNGER) || t.equals(PotionEffectType.NAUSEA)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Mob) || !(e.getTarget() instanceof Player p) || !p.isSneaking()) {
            return;
        }
        Defs.Ability x = core.ability(p, "shadow_cloak");
        if (x != null && p.getLocation().getBlock().getLightLevel() <= x.v("max_light", 6)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeal(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player t)) {
            return;
        }
        Player p = e.getPlayer();
        Defs.Ability x = core.ability(p, "healing_touch");
        if (x == null || !p.isSneaking() || !p.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        var hp = t.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = hp == null ? 20 : hp.getValue();
        if (t.getHealth() >= max) {
            core.msg(p, "&7" + t.getName() + " sudah sehat.");
            return;
        }
        if (!ready(p, "healing_touch", x.v("cooldown", 40))) {
            core.msg(p, "&cSentuhan Penyembuh masih cooldown.");
            return;
        }
        t.setHealth(Math.min(max, t.getHealth() + x.v("heal", 4)));
        t.getWorld().spawnParticle(Particle.HEART, t.getLocation().add(0, 1.8, 0), 6, 0.3, 0.2, 0.3, 0);
        core.msg(p, "&dKamu menyembuhkan " + t.getName() + ".");
        core.msg(t, "&d" + p.getName() + " menyembuhkanmu.");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakMark(BlockBreakEvent e) {
        if (core.placed.contains(e.getBlock())) {
            if (skipDrops.size() > 2000) {
                skipDrops.clear();
            }
            skipDrops.add(e.getBlock().getBlockKey() * 31L + e.getBlock().getWorld().getUID().hashCode());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        Block b = e.getBlock();
        if (skipDrops.remove(b.getBlockKey() * 31L + b.getWorld().getUID().hashCode()) || e.getItems().isEmpty()) {
            return;
        }
        Player p = e.getPlayer();
        Material type = e.getBlockState().getType();
        Defs.Ability x = null;
        if (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) {
            x = core.ability(p, "prospector");
        } else if (CROPS.contains(type) && e.getBlockState().getBlockData() instanceof Ageable a
                && a.getAge() >= a.getMaximumAge()) {
            x = core.ability(p, "green_thumb");
        }
        if (x == null || !roll(x.v("chance", 10))) {
            return;
        }
        Item first = e.getItems().get(0);
        ItemStack extra = first.getItemStack().clone();
        extra.setAmount(1);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), extra);
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.7, 0.5), 5, 0.3, 0.3, 0.3, 0);
    }
}
