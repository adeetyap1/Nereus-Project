package me.naook1.nereusthrone.command;

import me.naook1.nereusthrone.NereusThrone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /afk [alasan] */
public final class AfkCommand implements CommandExecutor {

    private final NereusThrone plugin;

    public AfkCommand(NereusThrone plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("nereus.afk")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }
        if (!plugin.afk().isEnabled()) {
            plugin.messages().send(p, "afk.disabled");
            return true;
        }
        plugin.afk().toggle(p, String.join(" ", args));
        return true;
    }
}
