package com.nereus.rpg.guild;

import com.nereus.rpg.Core;
import com.nereus.rpg.NerusRPG;
import org.bukkit.Bukkit;

import java.util.UUID;

/** Pemasangan listener FancyNpcs (soft-dependency). Tidak pernah membuat NPC sendiri. */
public final class NpcIntegration {
    private final NerusRPG plugin;
    private final Core core;
    private FancyNpcsListener listener;

    public NpcIntegration(NerusRPG plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
    }

    public boolean hook() {
        if (listener != null) {
            return true;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            plugin.getLogger().warning("FancyNpcs tidak ditemukan: klik NPC Caylin dinonaktifkan. "
                    + "Admin bisa menguji dialog dengan /gm talk.");
            return false;
        }
        try {
            listener = new FancyNpcsListener(plugin, core);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            plugin.getLogger().info("FancyNpcs terhubung. NPC Caylin: " + plugin.getConfig().getStringList("npc.ids"));
            if (plugin.getConfig().getStringList("npc.ids").isEmpty()) {
                plugin.getLogger().warning("config.yml npc.ids kosong: isi dengan ID/nama NPC FancyNpcs.");
            }
            return true;
        } catch (LinkageError e) {
            listener = null;
            plugin.getLogger().warning("Versi FancyNpcs tidak cocok dengan NerusRPG: " + e.getMessage());
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
