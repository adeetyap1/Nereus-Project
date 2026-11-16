package com.nereus.guildmaster.config;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Memuat config.yml, messages.yml, dialogue.yml, dan gui/*.yml (default disalin dari jar). */
public final class ConfigManager {
    public static final String[] GUI_FILES = {"main", "profile", "quest-board", "active-quest", "quest-history", "info"};

    private final GuildMaster plugin;
    private YamlConfiguration messages;
    private YamlConfiguration dialogue;
    private final Map<String, YamlConfiguration> gui = new HashMap<>();

    public ConfigManager(GuildMaster plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        messages = file("messages.yml");
        dialogue = file("dialogue.yml");
        gui.clear();
        for (String g : GUI_FILES) {
            gui.put(g, file("gui/" + g + ".yml"));
        }
        File data = new File(plugin.getDataFolder(), "data");
        if (!data.exists() && !data.mkdirs()) {
            plugin.getLogger().warning("Tidak bisa membuat folder data/");
        }
    }

    private YamlConfiguration file(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    public FileConfiguration main() {
        return plugin.getConfig();
    }

    public YamlConfiguration dialogue() {
        return dialogue;
    }

    public YamlConfiguration gui(String name) {
        return gui.get(name);
    }

    // ------------------------------------------------------------------ pesan

    public String raw(String key, String... kv) {
        String s = messages.getString(key, "&c[pesan hilang: " + key + "]");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("%" + kv[i] + "%", kv[i + 1]);
        }
        return s;
    }

    public Component msg(String key, String... kv) {
        return Text.c(messages.getString("prefix", "") + raw(key, kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(msg(key, kv));
    }
}
