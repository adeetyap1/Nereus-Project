package me.naook1.nereusrealm.input;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.joinleave.CustomMessages;
import me.naook1.nereusrealm.util.Text;
import me.naook1.nereusrealm.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** "Type your answer in chat" prompts for custom join/leave messages and feedback. */
public final class PromptManager {

    public enum Kind { JOIN, LEAVE, FEEDBACK, REDEEM, AFK }

    private record Pending(Kind kind, int stars, long expires) {
    }

    private final NereusRealm plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private int timeoutSeconds = 60;

    public PromptManager(NereusRealm plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        timeoutSeconds = Math.max(10, plugin.getConfig().getInt("input.timeout-seconds", 60));
    }

    /** Safe to call from any thread. */
    public boolean isActive(UUID id) {
        Pending p = pending.get(id);
        if (p == null) {
            return false;
        }
        if (System.currentTimeMillis() > p.expires()) {
            pending.remove(id, p);
            return false;
        }
        return true;
    }

    public void clear(UUID id) {
        pending.remove(id);
    }

    public void beginCustom(Player p, boolean join) {
        CustomMessages cm = plugin.custom();
        CustomMessages.Tier tier = cm.tierOf(p);
        if (tier == null) {
            plugin.messages().send(p, "custom.locked", "requirement", cm.requirement());
            return;
        }
        if (!p.hasPermission("nereus.bypass")) {
            long left = cm.cooldownLeft(p);
            if (left > 0) {
                plugin.messages().send(p, "custom.cooldown", "time", TimeUtil.format(left));
                return;
            }
        }
        p.closeInventory();
        start(p, join ? Kind.JOIN : Kind.LEAVE, 0);
        plugin.messages().send(p, join ? "custom.prompt-join" : "custom.prompt-leave",
                "max", String.valueOf(tier.maxLength()));
    }

    public void beginFeedback(Player p, int stars) {
        if (!p.hasPermission("nereus.rating")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (!plugin.feedback().check(p)) {
            return;
        }
        int s = Math.max(1, Math.min(5, stars));
        p.closeInventory();
        start(p, Kind.FEEDBACK, s);
        plugin.messages().send(p, "feedback.prompt", "stars", Text.stars(s),
                "max", String.valueOf(plugin.feedback().maxLength()));
    }

    public void beginAfk(Player p) {
        if (!p.hasPermission("nereus.afk")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (!plugin.afk().isEnabled()) {
            plugin.messages().send(p, "afk.disabled");
            return;
        }
        p.closeInventory();
        start(p, Kind.AFK, 0);
        plugin.messages().send(p, "afk.prompt");
    }

    public void beginRedeem(Player p) {
        if (!plugin.redeem().isEnabled()) {
            plugin.messages().send(p, "redeem.disabled");
            return;
        }
        if (!p.hasPermission("nereus.redeem")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        p.closeInventory();
        start(p, Kind.REDEEM, 0);
        plugin.messages().send(p, "redeem.prompt");
    }

    private void start(Player p, Kind kind, int stars) {
        UUID id = p.getUniqueId();
        Pending np = new Pending(kind, stars, System.currentTimeMillis() + timeoutSeconds * 1000L);
        pending.put(id, np);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(id, np) && p.isOnline()) {
                plugin.messages().send(p, "input.timeout");
            }
        }, timeoutSeconds * 20L);
    }

    /** Called on the main thread with what the player typed. */
    public void handle(Player p, String raw) {
        UUID id = p.getUniqueId();
        Pending pd = pending.get(id);
        if (pd == null || System.currentTimeMillis() > pd.expires()) {
            pending.remove(id);
            return;
        }
        String text = raw.trim();
        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("batal")) {
            pending.remove(id);
            plugin.messages().send(p, "input.cancelled");
            return;
        }
        switch (pd.kind()) {
            case JOIN, LEAVE -> {
                boolean join = pd.kind() == Kind.JOIN;
                CustomMessages.Check c = plugin.custom().set(p, join, text);
                if (!c.ok()) {
                    plugin.custom().sendError(p, c);
                    return;
                }
                pending.remove(id);
                plugin.messages().send(p, "custom.saved");
                plugin.styles().previewCurrent(p, join);
            }
            case AFK -> {
                pending.remove(id);
                plugin.afk().toggle(p, text.equalsIgnoreCase("skip") ? null : text);
                plugin.menus().open(p, "afk");
            }
            case REDEEM -> {
                pending.remove(id);
                plugin.redeem().claim(p, text);
            }
            case FEEDBACK -> {
                String msg = text.equalsIgnoreCase("skip") ? "" : text;
                String err = plugin.feedback().submit(p, pd.stars(), msg);
                if (err != null) {
                    plugin.messages().send(p, err, "max", String.valueOf(plugin.feedback().maxLength()));
                    return;
                }
                pending.remove(id);
                plugin.messages().send(p, "feedback.thanks", "stars", Text.stars(pd.stars()));
                plugin.menus().open(p, "feedback");
            }
        }
    }
}
