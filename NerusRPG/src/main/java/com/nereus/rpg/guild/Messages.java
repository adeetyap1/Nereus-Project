package com.nereus.rpg.guild;

import com.nereus.rpg.NerusRPG;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** messages.yml: semua teks Guild yang bisa diubah. Kunci yang hilang memakai bawaan di dalam jar. */
public final class Messages {
    private final NerusRPG plugin;
    private YamlConfiguration yml = new YamlConfiguration();

    public Messages(NerusRPG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        yml = YamlConfiguration.loadConfiguration(f);
        try (Reader r = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            yml.setDefaults(YamlConfiguration.loadConfiguration(r));
        } catch (Exception ignored) {
            // tanpa bawaan jar
        }
    }

    public String get(String key) {
        return yml.getString(key, "&c[" + key + "]");
    }

    public String get(String key, Map<String, String> vars) {
        String s = get(key);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        }
        return s;
    }

    public List<String> list(String key) {
        List<String> l = yml.getStringList(key);
        return l == null ? new ArrayList<>() : l;
    }
}
