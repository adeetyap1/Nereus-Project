package me.naook1.nereusarmory;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusArmory extends JavaPlugin implements Listener, TabExecutor {

    private ArmoryItems items;
    private ArmoryGui gui;
    private Skills skills;
    private Overlay overlay;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new ArmoryItems(this);
        gui = new ArmoryGui(this);
        skills = new Skills(this);
        overlay = new Overlay(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(skills, this);
        getCommand("armory").setExecutor(this);
        getCommand("armory").setTabCompleter(this);
        Bukkit.getScheduler().runTaskTimer(this, skills::tickSet, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, skills::aura, 20L, 4L);
        Bukkit.getScheduler().runTaskTimer(this, overlay::tick, 20L, 2L);
        getLogger().info("NereusArmory v" + getDescription().getVersion() + " enabled | developer: naook1/adityap");
    }

    @Override
    public void onDisable() {
        if (overlay != null) {
            overlay.removeAll();
        }
    }

    public ArmoryItems items() {
        return items;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        overlay.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        String sub = a.length == 0 ? "gui" : a[0].toLowerCase();
        switch (sub) {
            case "gui" -> {
                if (s instanceof Player p) {
                    gui.open(p);
                } else {
                    s.sendMessage("Players only.");
                }
            }
            case "list" -> s.sendMessage(Text.parse("&bItems: &f" + String.join(", ", items.all().keySet())));
            case "give" -> {
                if (!s.hasPermission("nereusarmory.admin")) {
                    s.sendMessage(Text.parse("&cNo permission."));
                    return true;
                }
                if (a.length < 3) {
                    s.sendMessage(Text.parse("&7/armory give <player> <id|all>"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    s.sendMessage(Text.parse("&cPlayer not online."));
                    return true;
                }
                List<String> ids = a[2].equalsIgnoreCase("all") ? new ArrayList<>(items.all().keySet()) : List.of(a[2].toLowerCase());
                for (String id : ids) {
                    var it = items.create(id);
                    if (it == null) {
                        s.sendMessage(Text.parse("&cUnknown item: " + id));
                        return true;
                    }
                    t.getInventory().addItem(it).values().forEach(x -> t.getWorld().dropItemNaturally(t.getLocation(), x));
                }
                s.sendMessage(Text.parse("&aGiven to " + t.getName()));
            }
            case "reload" -> {
                if (s.hasPermission("nereusarmory.admin")) {
                    reloadConfig();
                    s.sendMessage(Text.parse("&aReloaded."));
                }
            }
            default -> s.sendMessage(Text.parse("&7/armory [gui|list|give <player> <id|all>|reload]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("gui", "list", "give", "reload"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            out.add("all");
            out.addAll(items.all().keySet());
        }
        String pre = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(pre));
        return out;
    }
}
