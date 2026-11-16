package com.nereus.guildmaster;

import com.nereus.guildmaster.adventurer.AdventurerManager;
import com.nereus.guildmaster.command.GuildCommand;
import com.nereus.guildmaster.config.ConfigManager;
import com.nereus.guildmaster.db.AdventurerRepository;
import com.nereus.guildmaster.db.Database;
import com.nereus.guildmaster.dialogue.DialogueManager;
import com.nereus.guildmaster.gui.GuiManager;
import com.nereus.guildmaster.hook.GuildExpansion;
import com.nereus.guildmaster.npc.NpcIntegration;
import com.nereus.guildmaster.rpg.NereusRpgAdapter;
import com.nereus.guildmaster.rpg.NoRpgAdapter;
import com.nereus.guildmaster.rpg.OdqAdapter;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin utama: hanya merangkai modul. */
public final class GuildMaster extends JavaPlugin implements Listener {
    private ConfigManager configs;
    private Database database;
    private AdventurerManager adventurers;
    private DialogueManager dialogues;
    private GuiManager gui;
    private NpcIntegration npc;
    private RpgAdapter rpg = new NoRpgAdapter();

    @Override
    public void onEnable() {
        configs = new ConfigManager(this);
        configs.load();

        database = new Database(this);
        if (!database.open()) {
            getLogger().severe("Database gagal dibuka. Perbaiki bagian database di config.yml lalu /guildmaster reload.");
        }
        adventurers = new AdventurerManager(this, new AdventurerRepository(database));
        dialogues = new DialogueManager(this);
        gui = new GuiManager(this);
        npc = new NpcIntegration(this);

        connectRpg();
        npc.hook();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);

        GuildCommand command = new GuildCommand(this);
        PluginCommand pc = getCommand("guildmaster");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new GuildExpansion(this).register();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            adventurers.load(p, null);
        }
    }

    @Override
    public void onDisable() {
        if (dialogues != null) {
            dialogues.clear();
        }
        if (database != null) {
            database.close();
        }
    }

    private void connectRpg() {
        RpgAdapter a = null;
        if (Bukkit.getPluginManager().isPluginEnabled("NereusRpg")) {
            try {
                a = NereusRpgAdapter.tryCreate();
            } catch (LinkageError e) {
                getLogger().warning("NereusRpg ada tapi versinya tidak cocok: " + e.getMessage());
            }
        }
        RpgAdapter base = a == null ? new NoRpgAdapter() : a;
        if (a == null) {
            getLogger().warning("Nereus RPG tidak ditemukan: rank tidak bisa dibaca, registrasi mandiri ditolak.");
        } else {
            getLogger().info("Terhubung ke Nereus RPG (rank dan profil).");
        }
        String source = getConfig().getString("quests.source", "auto").toLowerCase();
        RpgAdapter chosen = base;
        if (!source.equals("nereusrpg") && Bukkit.getPluginManager().isPluginEnabled("ODailyQuests")) {
            OdqAdapter odq = OdqAdapter.tryCreate(this, base);
            if (odq != null) {
                chosen = odq;
                getLogger().info("Quest dibaca dari ODailyQuests.");
            }
        }
        if (!chosen.available()) {
            getLogger().warning("Tidak ada sumber quest (ODailyQuests atau Nereus RPG). Menu quest dinonaktifkan.");
        }
        rpg = chosen;
    }

    public void reloadAll() {
        configs.load();
        if (database.open()) {
            adventurers.clearCache();
            for (Player p : Bukkit.getOnlinePlayers()) {
                adventurers.load(p, null);
            }
        }
        connectRpg();
        npc.hook();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        adventurers.load(e.getPlayer(), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var id = e.getPlayer().getUniqueId();
        adventurers.unload(id);
        dialogues.end(id);
        gui.forget(id);
        npc.forget(id);
    }

    public ConfigManager configs() {
        return configs;
    }

    public Database database() {
        return database;
    }

    public AdventurerManager adventurers() {
        return adventurers;
    }

    public DialogueManager dialogues() {
        return dialogues;
    }

    public GuiManager gui() {
        return gui;
    }

    public NpcIntegration npc() {
        return npc;
    }

    public RpgAdapter rpg() {
        return rpg;
    }
}
