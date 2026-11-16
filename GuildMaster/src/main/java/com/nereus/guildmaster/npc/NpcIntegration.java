package com.nereus.guildmaster.npc;

import com.nereus.guildmaster.GuildMaster;
import org.bukkit.Bukkit;

import java.util.UUID;

/** Pemasangan listener FancyNpcs (soft-dependency). Tidak pernah membuat NPC sendiri. */
public final class NpcIntegration {
    private final GuildMaster plugin;
    private FancyNpcsListener listener;

    public NpcIntegration(GuildMaster plugin) {
        this.plugin = plugin;
    }

    public boolean hook() {
        if (listener != null) {
            return true;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            plugin.getLogger().warning("FancyNpcs tidak ditemukan: klik NPC dinonaktifkan. "
                    + "Pasang FancyNpcs, atau admin bisa memakai /guildmaster talk untuk menguji dialog.");
            return false;
        }
        try {
            listener = new FancyNpcsListener(plugin);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            plugin.getLogger().info("FancyNpcs terhubung. NPC Guild Master: " + plugin.getConfig().getStringList("npc.ids"));
            if (plugin.getConfig().getStringList("npc.ids").isEmpty()) {
                plugin.getLogger().warning("config.yml npc.ids kosong: isi dengan ID/nama NPC FancyNpcs.");
            }
            return true;
        } catch (LinkageError e) {
            listener = null;
            plugin.getLogger().warning("Versi FancyNpcs tidak cocok dengan GuildMaster: " + e.getMessage());
            return false;
        }
    }

    public boolean hooked() {
        return listener != null;
    }

    public void forget(UUID id) {
        if (listener != null) {
            listener.forget(id);
        }
    }
}
