package com.nereus.guild;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Sesi pemain dan penghitung progres ujian. */
public final class GuildListener implements Listener {
    private final NereusGuild plugin;
    private final GuildManager mgr;

    public GuildListener(NereusGuild plugin, GuildManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        mgr.handleJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mgr.handleQuit(e.getPlayer());
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer != null) {
            mgr.onKill(killer, victim.getType());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (plugin.getConfig().getBoolean("exam.fail-on-death", false)) {
            mgr.failExam(e.getEntity(), "exam-death");
        }
    }
}
