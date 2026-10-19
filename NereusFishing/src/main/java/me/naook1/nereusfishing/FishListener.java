package me.naook1.nereusfishing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.naook1.nereusfishing.util.Text;
import org.bukkit.Sound;
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
            if (rod != null && rod.speed() > 0) {
                FishHook hook = e.getHook();
                double f = 1.0 - Math.min(0.9, rod.speed() / 100.0);
                hook.setMinWaitTime(Math.max(20, (int) (hook.getMinWaitTime() * f)));
                hook.setMaxWaitTime(Math.max(40, (int) (hook.getMaxWaitTime() * f)));
            }
            return;
        }
        if (e.getState() == PlayerFishEvent.State.BITE) {
            if (rod != null || plugin.getConfig().getBoolean("vanilla-rods-too", false)) {
                p.sendActionBar(Text.parse(plugin.messages().format("bite")));
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.4f);
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
        startFight(p, c, use);
    }

    // ---------- minigame ----------

    private void startFight(Player p, FishManager.Catch c, FishManager.Rod rod) {
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
        fights.put(p.getUniqueId(), f);
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
            if (f.lives <= 0) {
                end(p, f);
                m.send(p, "fight-escape");
                return;
            }
            f.lives--;
            m.send(p, "fight-miss", "lives", String.valueOf(f.lives));
        }
    }

    private void win(Player p, Fight f) {
        FishManager mgr = plugin.fish();
        ItemStack stack = mgr.createFish(f.c, p.getName());
        var left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        announce(p, f.c, f.rod);
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
        if (c.fish().rarity().broadcast()) {
            m.broadcast("caught-broadcast", "player", p.getName(), "fish", fishName, "weight", weight,
                    "rod", mgr.sc(use.name()));
        }
    }
}
