package com.nereus.rpg;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Format chat dan nametag: [PREFIX DONATUR] [NAMA] [RANK]. Diatur di config bagian display. */
public final class Display implements Listener {
    private final Core core;

    public Display(Core core) {
        this.core = core;
        Bukkit.getScheduler().runTaskTimer(core.plugin, this::refreshAll, 40L, 100L);
    }

    private FileConfiguration cfg() {
        return core.plugin.getConfig();
    }

    /** Ganti placeholder PlaceholderAPI (jika ada) lalu %rank%. */
    String fill(String s, Player p) {
        Defs.Rank r = core.activeRank(core.profile(p));
        s = s.replace("%rank%", core.tag(r, null)).replace("%player%", p.getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
        }
        return s;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!cfg().getBoolean("display.chat.enabled", true)) {
            return;
        }
        Player p = e.getPlayer();
        String fmt = cfg().getString("display.chat.format", "%luckperms_prefix%&f%player% %rank%&8 » &f");
        Component head = Text.L.deserialize(fill(fmt, p));
        e.renderer((source, name, msg, viewer) -> head.append(msg));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(core.plugin, () -> refresh(e.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Team t = Bukkit.getScheduler() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(e.getPlayer()));
        if (t != null) {
            t.unregister();
        }
    }

    private String teamName(Player p) {
        return "nrpg_" + p.getUniqueId().toString().substring(0, 8);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    public void refresh(Player p) {
        FileConfiguration c = cfg();
        if (!p.isOnline() || !c.getBoolean("display.builtin-nametag", false)) {
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(teamName(p));
        if (t == null) {
            t = sb.registerNewTeam(teamName(p));
        }
        if (!t.hasEntry(p.getName())) {
            t.addEntry(p.getName());
        }
        Component prefix = Text.L.deserialize(fill(c.getString("display.nametag-format", "%luckperms_prefix%"), p));
        Component suffix = Text.L.deserialize(fill(c.getString("display.nametag-suffix", " %rank%"), p));
        t.prefix(prefix);
        t.suffix(suffix);
        if (c.getBoolean("display.tab-list-name", false)) {
            p.playerListName(prefix.append(Component.text(p.getName())).append(suffix));
        }
        if (c.getBoolean("display.health-below-name", true)) {
            Objective o = sb.getObjective("nrpg_hp");
            if (o == null) {
                o = sb.registerNewObjective("nrpg_hp", Criteria.HEALTH, Text.L.deserialize("&c❤"));
                o.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
        }
    }
}
