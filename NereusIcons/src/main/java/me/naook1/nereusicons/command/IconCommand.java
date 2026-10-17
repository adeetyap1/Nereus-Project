package me.naook1.nereusicons.command;

import java.util.ArrayList;
import java.util.List;
import me.naook1.nereusicons.NereusIcons;
import me.naook1.nereusicons.util.SmallCaps;
import me.naook1.nereusicons.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class IconCommand implements CommandExecutor, TabCompleter {

    private final NereusIcons plugin;

    public IconCommand(NereusIcons plugin) {
        this.plugin = plugin;
    }

    private String msg(String key, String... kv) {
        String s = plugin.getConfig().getString("messages." + key, key);
        if (plugin.getConfig().getBoolean("small-caps", true)) {
            s = SmallCaps.convert(s);
        }
        s = s.replace("{prefix}", plugin.getConfig().getString("messages.prefix", ""));
        return Text.fill(s, kv);
    }

    private void send(CommandSender to, String key, String... kv) {
        to.sendMessage(Text.parse(msg(key, kv)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("nereusicons.admin")) {
                    send(sender, "no-permission");
                    return true;
                }
                plugin.icons().reload();
                send(sender, "reloaded");
            }
            case "me" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                String r = plugin.icons().rank(p);
                if (r == null) {
                    send(p, "no-rank");
                } else {
                    send(p, "your-rank", "icon", r);
                }
            }
            default -> {
                if (plugin.icons().ids().isEmpty()) {
                    send(sender, "test-none");
                    return true;
                }
                send(sender, "header");
                for (String id : plugin.icons().ids()) {
                    send(sender, "line", "id", id, "icon", plugin.icons().icon(id));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("list", "me", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
