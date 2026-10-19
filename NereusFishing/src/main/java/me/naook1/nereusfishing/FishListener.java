package me.naook1.nereusfishing;

import me.naook1.nereusfishing.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public final class FishListener implements Listener {

    private final NereusFishing plugin;

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
            if (rod != null && rod.speed() > 0) {
                FishHook hook = e.getHook();
                double f = 1.0 - Math.min(0.9, rod.speed() / 100.0);
                hook.setMinWaitTime(Math.max(20, (int) (hook.getMinWaitTime() * f)));
                hook.setMaxWaitTime(Math.max(40, (int) (hook.getMaxWaitTime() * f)));
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
        ItemStack stack = mgr.createFish(c, p.getName());
        item.setItemStack(stack);

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
