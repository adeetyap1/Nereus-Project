package com.nereus.guildmaster.command;

import com.nereus.guildmaster.GuildMaster;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /guildmaster: admin (reload, info, register, unregister, reset) dan pembuka dialog/menu. */
public final class GuildCommand implements CommandExecutor, TabCompleter {
    private final GuildMaster plugin;

    public GuildCommand(GuildMaster plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                talk(p);
            } else {
                help(sender);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("guildmaster.admin.reload")) {
                    plugin.configs().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.configs().send(sender, "reloaded");
            }
            case "info" -> {
                if (!sender.hasPermission("guildmaster.admin.reload")) {
                    plugin.configs().send(sender, "no-permission");
                    return true;
                }
                info(sender);
            }
            case "register", "unregister", "reset" -> admin(sender, args);
            case "talk" -> {
                if (sender instanceof Player p) {
                    talk(p);
                } else {
                    plugin.configs().send(sender, "players-only");
                }
            }
            case "menu" -> {
                if (!(sender instanceof Player p)) {
                    plugin.configs().send(sender, "players-only");
                } else if (!canDirect(p)) {
                    plugin.configs().send(sender, "use-npc");
                } else {
                    plugin.adventurers().ready(p, () -> plugin.gui().openMain(p));
                }
            }
            case "choose" -> {
                if (sender instanceof Player p && args.length > 1) {
                    try {
                        plugin.dialogues().chooseNumber(p, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ex) {
                        plugin.configs().send(sender, "bad-number");
                    }
                }
            }
            default -> help(sender);
        }
        return true;
    }

    /** Admin selalu boleh; pemain biasa hanya jika config mengizinkan akses tanpa NPC. */
    private boolean canDirect(Player p) {
        return p.hasPermission("guildmaster.admin") || plugin.getConfig().getBoolean("commands.player-direct-access", false);
    }

    private void talk(Player p) {
        if (!p.hasPermission("guildmaster.use")) {
            plugin.configs().send(p, "no-permission");
        } else if (!canDirect(p)) {
            plugin.configs().send(p, "use-npc");
        } else {
            plugin.dialogues().open(p);
        }
    }

    private void admin(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        String perm = sub.equals("reset") ? "guildmaster.admin.reset" : "guildmaster.admin.register";
        if (!sender.hasPermission(perm)) {
            plugin.configs().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.configs().send(sender, "usage-player", "cmd", sub);
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (target == null) {
            plugin.configs().send(sender, "unknown-player", "player", args[1]);
            return;
        }
        UUID id = target.getUniqueId();
        String name = target.getName() == null ? args[1] : target.getName();
        switch (sub) {
            case "register" -> plugin.adventurers().adminRegister(id, name,
                    ok -> plugin.configs().send(sender, ok ? "admin-registered" : "admin-failed", "player", name));
            case "unregister" -> {
                boolean block = plugin.getConfig().getBoolean("registration.unregister-blocks-reregister", false);
                if (block) {
                    plugin.adventurers().adminUnregister(id,
                            ok -> plugin.configs().send(sender, ok ? "admin-unregistered-blocked" : "admin-not-found", "player", name));
                } else {
                    plugin.dialogues().end(id);
                    plugin.adventurers().adminReset(id,
                            ok -> plugin.configs().send(sender, ok ? "admin-unregistered" : "admin-not-found", "player", name));
                }
            }
            default -> {
                plugin.dialogues().end(id);
                plugin.adventurers().adminReset(id,
                        ok -> plugin.configs().send(sender, ok ? "admin-reset" : "admin-not-found", "player", name));
            }
        }
    }

    private void info(CommandSender sender) {
        plugin.adventurers().count(n -> {
            sender.sendMessage(com.nereus.guildmaster.Text.c("&8&m---&r &6GuildMaster " + plugin.getPluginMeta().getVersion() + " &8&m---"));
            sender.sendMessage(com.nereus.guildmaster.Text.c("&7Database: &f" + plugin.database().type()
                    + " &7| Adventurer aktif: &f" + (n < 0 ? "?" : String.valueOf(n))));
            sender.sendMessage(com.nereus.guildmaster.Text.c("&7Nereus RPG: "
                    + (plugin.rpg().available() ? "&aterhubung" : "&cTIDAK ada")));
            sender.sendMessage(com.nereus.guildmaster.Text.c("&7FancyNpcs: "
                    + (plugin.npc().hooked() ? "&aterhubung" : "&cTIDAK terhubung")
                    + " &7| NPC: &f" + plugin.getConfig().getStringList("npc.ids")));
            sender.sendMessage(com.nereus.guildmaster.Text.c("&7Floodgate (form Bedrock): "
                    + (com.nereus.guildmaster.hook.Platform.floodgate() ? "&aada" : "&eTidak ada (Bedrock pakai teks)")));
        });
    }

    private void help(CommandSender sender) {
        String[] lines = {
                "&8&m---&r &6GuildMaster &8&m---",
                "&e/guildmaster reload &7- muat ulang config",
                "&e/guildmaster info &7- status plugin dan integrasi",
                "&e/guildmaster register <pemain> &7- daftarkan paksa atau pulihkan",
                "&e/guildmaster unregister <pemain> &7- cabut akses guild",
                "&e/guildmaster reset <pemain> &7- hapus catatan registrasi",
                "&e/guildmaster talk &7- buka dialog (admin / jika diizinkan)",
        };
        for (String l : lines) {
            sender.sendMessage(com.nereus.guildmaster.Text.c(l));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("guildmaster.admin.reload")) {
                out.add("reload");
                out.add("info");
            }
            if (sender.hasPermission("guildmaster.admin.register")) {
                out.add("register");
                out.add("unregister");
            }
            if (sender.hasPermission("guildmaster.admin.reset")) {
                out.add("reset");
            }
            out.add("talk");
            out.add("menu");
        } else if (args.length == 2 && List.of("register", "unregister", "reset").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
