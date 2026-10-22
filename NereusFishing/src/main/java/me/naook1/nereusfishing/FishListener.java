package me.naook1.nereusfishing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.naook1.nereusfishing.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class FishListener implements Listener {

    /** One running "tarik ikan" minigame. */
    private static final class Fight {
        FishManager.Catch c;
        FishManager.Rod rod;
        int len, zoneStart, zoneSize, pos, dir = 1, step, hits, need, lives, ticksLeft;
        BukkitTask task;
        Location spot;
        BossBar bar;
        Color color;
        int age;
    }

    private static Color colorOf(String rarityId) {
        return switch (rarityId) {
            case "RARE" -> Color.fromRGB(70, 140, 255);
            case "EPIC" -> Color.fromRGB(175, 80, 235);
            case "LEGENDARY" -> Color.fromRGB(255, 190, 40);
            default -> Color.fromRGB(235, 235, 235);
        };
    }

    private final NereusFishing plugin;
    private final Map<UUID, Fight> fights = new HashMap<>();

    public FishListener(NereusFishing plugin) {
        this.plugin = plugin;
    }

    private FishManager.Rod rodInHand(Player p) {
        FishManager.Rod rod = plugin.fish().rodOf(p.getInventory().getItemInMainHand());
        if (rod == null) {
            rod = plugin.fish().rodOf(p.getInventory().getItemInOffHand());
        }
        return rod;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        FishManager mgr = plugin.fish();
        FishManager.Rod rod = rodInHand(p);

        if (e.getState() == PlayerFishEvent.State.FISHING) {
            if (fights.containsKey(p.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            double evSpeed = EventsHook.bonus("fishing_speed");
            double sp = (rod != null ? rod.speed() : 0) + evSpeed;
            if ((rod != null || plugin.getConfig().getBoolean("vanilla-rods-too", false)) && sp > 0) {
                FishHook hook = e.getHook();
                double f = 1.0 - Math.min(0.9, sp / 100.0);
                hook.setMinWaitTime(Math.max(20, (int) (hook.getMinWaitTime() * f)));
                hook.setMaxWaitTime(Math.max(40, (int) (hook.getMaxWaitTime() * f)));
            }
            return;
        }
        if (e.getState() == PlayerFishEvent.State.BITE) {
            if (rod != null || plugin.getConfig().getBoolean("vanilla-rods-too", false)) {
                p.sendActionBar(Text.parse(plugin.messages().format("bite")));
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.4f);
                Location h = e.getHook().getLocation();
                h.getWorld().spawnParticle(Particle.SPLASH, h, 25, 0.25, 0.1, 0.25, 0.05);
                h.getWorld().spawnParticle(Particle.BUBBLE_POP, h, 10, 0.2, 0.1, 0.2, 0.02);
            }
            return;
        }
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (rod == null && !plugin.getConfig().getBoolean("vanilla-rods-too", false)) {
            return;
        }
        if (!(e.getCaught() instanceof Item item)) {
            return;
        }
        FishManager.Rod use = rod != null ? rod : mgr.vanillaRod();
        FishManager.Catch c = mgr.roll(use);
        if (c == null) {
            return;
        }
        boolean game = plugin.getConfig().getBoolean("minigame.enabled", true) && !c.fish().rarity().junk();
        if (!game) {
            item.setItemStack(mgr.createFish(c, p.getName()));
            announce(p, c, use);
            return;
        }
        // minigame: remove the vanilla loot, the player has to win the fish first
        item.remove();
        e.setExpToDrop(0);
        startFight(p, c, use, e.getHook().getLocation().clone());
    }

    // ---------- minigame ----------

    private void startFight(Player p, FishManager.Catch c, FishManager.Rod rod, Location spot) {
        var cfg = plugin.getConfig();
        String key = "minigame.difficulty." + c.fish().rarity().id();
        Fight f = new Fight();
        f.c = c;
        f.rod = rod;
        f.len = Math.max(9, cfg.getInt("minigame.bar-length", 21));
        f.need = Math.max(1, cfg.getInt(key + ".hits", 1));
        f.zoneSize = Math.min(f.len - 2, Math.max(1, cfg.getInt(key + ".zone", 5) + (int) Math.round(rod.control())));
        f.step = Math.max(1, cfg.getInt(key + ".speed", 1));
        f.lives = Math.max(0, cfg.getInt("minigame.lives", 2));
        f.ticksLeft = Math.max(5, cfg.getInt("minigame.timeout-seconds", 14)) * 10; // runs every 2 ticks
        f.pos = ThreadLocalRandom.current().nextInt(f.len);
        newZone(f);
        f.spot = spot;
        f.color = colorOf(c.fish().rarity().id());
        BossBar.Color bc;
        try {
            bc = BossBar.Color.valueOf(cfg.getString(key + ".color", "WHITE").toUpperCase());
        } catch (IllegalArgumentException ex) {
            bc = BossBar.Color.WHITE;
        }
        f.bar = BossBar.bossBar(Text.parse(plugin.messages().format("fight-bar", "lives", String.valueOf(f.lives))),
                0f, bc, BossBar.Overlay.PROGRESS);
        p.showBossBar(f.bar);
        fights.put(p.getUniqueId(), f);
        p.swingMainHand();
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
        plugin.messages().send(p, "fight-start");
        f.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(p, f), 2L, 2L);
    }

    private void newZone(Fight f) {
        f.zoneStart = ThreadLocalRandom.current().nextInt(f.len - f.zoneSize + 1);
    }

    private void tick(Player p, Fight f) {
        if (!p.isOnline() || fights.get(p.getUniqueId()) != f) {
            end(p, f);
            return;
        }
        if (--f.ticksLeft <= 0) {
            end(p, f);
            plugin.messages().send(p, "fight-timeout");
            escapeFx(f);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        f.pos += f.dir * f.step;
        if (f.pos >= f.len - 1) {
            f.pos = f.len - 1;
            f.dir = -1;
        } else if (f.pos <= 0) {
            f.pos = 0;
            f.dir = 1;
        }
        p.sendActionBar(Text.parse(bar(f)));
        effects(p, f);
    }

    /** Splashing fish + a taut line from the player to the spot. */
    private void effects(Player p, Fight f) {
        f.age++;
        var w = f.spot.getWorld();
        int power = 4 + f.need * 2;
        w.spawnParticle(Particle.SPLASH, f.spot, power, 0.35, 0.05, 0.35, 0.08);
        if (f.age % 3 == 0) {
            w.spawnParticle(Particle.FISHING, f.spot, 4, 0.3, 0.0, 0.3, 0.0);
            w.spawnParticle(Particle.DUST, f.spot.clone().add(0, 0.2, 0), 6, 0.3, 0.2, 0.3, 0,
                    new Particle.DustOptions(f.color, 1.2f));
        }
        if (f.age % 8 == 0) {
            w.playSound(f.spot, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.5f, 0.7f + 0.1f * f.need);
        }
        Location from = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6)).subtract(0, 0.3, 0);
        var dir = f.spot.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len > 0.5 && len < 40) {
            dir.normalize().multiply(0.8);
            Location cur = from.clone();
            for (double d = 0; d < len; d += 0.8) {
                w.spawnParticle(Particle.DUST, cur, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 0.5f));
                cur.add(dir);
            }
        }
    }

    private void hideBar(Player p, Fight f) {
        if (f.bar != null) {
            p.hideBossBar(f.bar);
        }
    }

    private String bar(Fight f) {
        StringBuilder sb = new StringBuilder("&8[");
        for (int i = 0; i < f.len; i++) {
            boolean zone = i >= f.zoneStart && i < f.zoneStart + f.zoneSize;
            if (i == f.pos) {
                sb.append(zone ? "&f&l█" : "&e&l█");
            } else {
                sb.append(zone ? "&a█" : "&8█");
            }
        }
        sb.append("&8] &7").append(f.hits).append("/").append(f.need);
        return sb.toString();
    }

    private void end(Player p, Fight f) {
        hideBar(p, f);
        if (f.task != null) {
            f.task.cancel();
        }
        fights.remove(p.getUniqueId(), f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        Fight f = fights.get(p.getUniqueId());
        if (f == null) {
            return;
        }
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getHand() != null && e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        boolean in = f.pos >= f.zoneStart && f.pos < f.zoneStart + f.zoneSize;
        var m = plugin.messages();
        if (in) {
            f.hits++;
            p.swingMainHand();
            f.bar.progress(Math.min(1f, f.hits / (float) f.need));
            f.spot.getWorld().spawnParticle(Particle.DUST, f.spot, 25, 0.4, 0.3, 0.4, 0,
                    new Particle.DustOptions(f.color, 1.6f));
            f.spot.getWorld().spawnParticle(Particle.BUBBLE_POP, f.spot, 12, 0.3, 0.2, 0.3, 0.05);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f + 0.2f * f.hits);
            if (f.hits >= f.need) {
                end(p, f);
                win(p, f);
                return;
            }
            m.send(p, "fight-hit", "hits", String.valueOf(f.hits), "need", String.valueOf(f.need));
            newZone(f);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            f.spot.getWorld().spawnParticle(Particle.SMOKE, f.spot, 10, 0.3, 0.1, 0.3, 0.02);
            if (f.lives > 0) {
                f.bar.name(Text.parse(m.format("fight-bar", "lives", String.valueOf(f.lives - 1))));
            }
            if (f.lives <= 0) {
                end(p, f);
                m.send(p, "fight-escape");
                escapeFx(f);
                return;
            }
            f.lives--;
            m.send(p, "fight-miss", "lives", String.valueOf(f.lives));
        }
    }

    private void escapeFx(Fight f) {
        var w = f.spot.getWorld();
        w.spawnParticle(Particle.SPLASH, f.spot, 40, 0.4, 0.2, 0.4, 0.15);
        w.spawnParticle(Particle.POOF, f.spot, 8, 0.3, 0.1, 0.3, 0.03);
        w.playSound(f.spot, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.2f);
    }

    private void win(Player p, Fight f) {
        FishManager mgr = plugin.fish();
        ItemStack stack = mgr.createFish(f.c, p.getName());
        var w = f.spot.getWorld();
        String rar = f.c.fish().rarity().id();
        // big splash where the fish leaves the water
        w.spawnParticle(Particle.SPLASH, f.spot, 60, 0.4, 0.3, 0.4, 0.2);
        w.spawnParticle(Particle.DUST, f.spot, 40, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(f.color, 1.8f));
        w.playSound(f.spot, Sound.ENTITY_GENERIC_SPLASH, 1f, 1f);

        if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(f.spot) > 1600) {
            giveDirect(p, stack);
        } else {
            // the fish jumps out of the water in an arc towards the player
            Location start = f.spot.clone().add(0, 0.4, 0);
            Item it = w.dropItem(start, stack);
            it.setPickupDelay(12);
            it.setOwner(p.getUniqueId());
            it.setThrower(p.getUniqueId());
            var to = p.getEyeLocation().toVector().subtract(start.toVector());
            double dist = to.length();
            var vel = to.clone().multiply(0.085);
            vel.setY(Math.min(0.9, 0.32 + Math.max(0, to.getY()) * 0.06 + dist * 0.012));
            it.setVelocity(vel);
            it.setGlowing(!rar.equals("COMMON"));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (it.isValid()) {
                    it.setOwner(null);
                }
            }, 600L);
            Particle trail = switch (rar) {
                case "LEGENDARY" -> Particle.END_ROD;
                case "EPIC" -> Particle.WITCH;
                default -> Particle.DUST;
            };
            var task = new org.bukkit.scheduler.BukkitRunnable() {
                int t;

                @Override
                public void run() {
                    if (!it.isValid() || ++t > 30 || it.isOnGround()) {
                        cancel();
                        return;
                    }
                    Location l = it.getLocation();
                    if (trail == Particle.DUST) {
                        w.spawnParticle(trail, l, 3, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(f.color, 1.1f));
                    } else {
                        w.spawnParticle(trail, l, 2, 0.1, 0.1, 0.1, 0.01);
                    }
                    w.spawnParticle(Particle.SPLASH, l, 3, 0.1, 0.1, 0.1, 0.02);
                }
            };
            task.runTaskTimer(plugin, 1L, 1L);
        }

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        plugin.messages().send(p, "fight-win", "fish", mgr.sc(f.c.fish().name()));
        switch (rar) {
            case "RARE" -> p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 12, 0.5, 0.6, 0.5, 0);
            case "EPIC" -> {
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.25);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            }
            case "LEGENDARY" -> {
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 120, 0.7, 1.0, 0.7, 0.35);
                p.getWorld().spawnParticle(Particle.FIREWORK, f.spot, 50, 0.5, 0.8, 0.5, 0.15);
                w.strikeLightningEffect(f.spot);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.showTitle(Title.title(Text.parse(plugin.messages().format("legendary-title")),
                        Text.parse(plugin.messages().format("legendary-sub", "fish", mgr.sc(f.c.fish().name()))),
                        Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1800),
                                java.time.Duration.ofMillis(500))));
            }
            default -> { }
        }
        announce(p, f.c, f.rod);
    }

    private void giveDirect(Player p, ItemStack stack) {
        var left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Fight f = fights.get(e.getPlayer().getUniqueId());
        if (f != null) {
            end(e.getPlayer(), f);
        }
    }

    // ---------- result ----------

    private void announce(Player p, FishManager.Catch c, FishManager.Rod use) {
        FishManager mgr = plugin.fish();
        String fishName = mgr.sc(c.fish().name());
        String weight = FishManager.fmt(c.weight());
        var m = plugin.messages();
        if (c.fish().rarity().junk()) {
            p.sendActionBar(Text.parse(m.format("caught-junk", "fish", fishName, "weight", weight)));
            return;
        }
        p.sendActionBar(Text.parse(m.format("caught", "fish", fishName, "weight", weight,
                "rarity", mgr.rarityLabel(c.fish().rarity()))));
        boolean record = plugin.store().record(p.getUniqueId(), p.getName(), fishName, c.weight());
        if (record && c.weight() > 0) {
            m.send(p, "caught-record", "fish", fishName, "weight", weight);
        }
        var rar = c.fish().rarity();
        if (rar.broadcast()) {
            String key = m.has("caught-broadcast-" + rar.id().toLowerCase())
                    ? "caught-broadcast-" + rar.id().toLowerCase() : "caught-broadcast";
            m.broadcast(key, "player", p.getName(), "fish", fishName, "weight", weight, "rod", mgr.sc(use.name()));
        }
        if (rar.broadcastTitle()) {
            var title = Title.title(Text.parse(m.format("broadcast-title")),
                    Text.parse(m.format("broadcast-sub", "player", p.getName(), "fish", fishName, "weight", weight)),
                    Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(3000),
                            java.time.Duration.ofMillis(800)));
            for (Player o : plugin.getServer().getOnlinePlayers()) {
                o.showTitle(title);
                o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            }
        }
    }
}
