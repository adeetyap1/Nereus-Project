package me.naook1.nereusrealm.command;

import java.util.List;
import me.naook1.nereusrealm.NereusRealm;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /redeem [kode] - without a code, asks for it in chat (easy for Bedrock players). */
public final class RedeemCommand implements TabExecutor {

    private final NereusRealm plugin;

    public RedeemCommand(NereusRealm plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            plugin.prompts().beginRedeem(p);
        } else {
            plugin.redeem().claim(p, args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
