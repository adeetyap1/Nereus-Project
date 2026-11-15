package id.nereusmc.totemstack;

import org.bukkit.EntityEffect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Automatic activation through EntityResurrectEvent. Vanilla fires it on every fatal hit; it is
 * cancelled by default when neither hand holds a totem. In that case we look in the inventory,
 * consume exactly one totem and un-cancel the event so the server keeps the player alive.
 * Nothing runs outside that event.
 */
public final class ActivationManager implements Listener {

    private final Main plugin;
    /** Players already handled in the current tick: one fatal event consumes one totem. */
    private final Set<UUID> handling = new HashSet<>();

    public ActivationManager(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ConfigManager cfg = plugin.config();
        if (!cfg.isPluginEnabled() || !cfg.isActivationEnabled()) {
            return;
        }
        TotemManager totems = plugin.totems();
        UUID id = player.getUniqueId();
        boolean handTotem = totems.handHasTotem(player);

        if (!event.isCancelled()) {
            // Vanilla found a totem in a hand and consumes it itself; we only record and tune effects.
            if (handTotem && handling.add(id)) {
                plugin.statistics().record(player);
                afterActivation(player, false);
            }
            return;
        }

        // Cancelled: no totem in hand (vanilla default) or another plugin cancelled on purpose.
        if (handTotem || handling.contains(id) || !player.hasPermission("nereustotem.use")) {
            return;
        }
        int slot = totems.findSlot(player);
        if (slot < 0) {
            player.sendMessage(cfg.message("no-totem"));
            return;
        }
        if (!totems.consumeOne(player, slot)) {
            return;
        }
        handling.add(id);
        event.setCancelled(false);
        if (player.getHealth() <= 0.0) {
            player.setHealth(1.0);
        }
        plugin.statistics().record(player);
        afterActivation(player, true);
    }

    /** Runs next tick, after vanilla has finished its own resurrection handling. */
    private void afterActivation(Player player, boolean manual) {
        UUID id = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            handling.remove(id);
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            if (manual) {
                for (PotionEffect active : player.getActivePotionEffects()) {
                    player.removePotionEffect(active.getType());
                }
                player.playEffect(EntityEffect.TOTEM_RESURRECT);
            }
            applyConfiguredEffects(player);
            player.sendMessage(plugin.config().message("activation",
                    "%amount%", String.valueOf(plugin.totems().count(player))));
        });
    }

    private void applyConfiguredEffects(Player player) {
        ConfigManager cfg = plugin.config();
        apply(player, "regeneration", PotionEffectType.REGENERATION, cfg);
        apply(player, "absorption", PotionEffectType.ABSORPTION, cfg);
        apply(player, "fire-resistance", PotionEffectType.FIRE_RESISTANCE, cfg);
    }

    private void apply(Player player, String key, PotionEffectType type, ConfigManager cfg) {
        if (cfg.isEffectEnabled(key)) {
            player.addPotionEffect(new PotionEffect(type, cfg.getEffectDuration(key), cfg.getEffectAmplifier(key)));
        } else {
            player.removePotionEffect(type);
        }
    }
}
