package com.nereus.pets;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusPets extends JavaPlugin {
    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private PetManager manager;
    private Orbs orbs;
    private NamespacedKey petKey;
    private NamespacedKey orbKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        petKey = new NamespacedKey(this, "pet_owner");
        orbKey = new NamespacedKey(this, "orb_tier");
        orbs = new Orbs(this);
        manager = new PetManager(this);
        manager.reloadSpecies();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PetListener(this, manager), this);
        pm.registerEvents(new CaptureListener(this, manager, orbs), this);
        pm.registerEvents(new MenuListener(), this);

        PetCommand command = new PetCommand(this, manager, orbs);
        PluginCommand pets = getCommand("pets");
        if (pets != null) {
            pets.setExecutor(command);
            pets.setTabCompleter(command);
        }

        for (Player p : getServer().getOnlinePlayers()) {
            manager.load(p);
        }
        manager.sweepStrays();
        manager.start();
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        manager.reloadSpecies();
    }

    public PetManager manager() {
        return manager;
    }

    public NamespacedKey petKey() {
        return petKey;
    }

    public NamespacedKey orbKey() {
        return orbKey;
    }

    /** Teks dari messages.<key> dengan penggantian pasangan kunci-nilai. */
    public String raw(String key, String... kv) {
        String s = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace(kv[i], kv[i + 1]);
        }
        return s;
    }

    public void send(CommandSender to, String key, String... kv) {
        String prefix = getConfig().getString("messages.prefix", "");
        to.sendMessage(LEGACY.deserialize(prefix + raw(key, kv)));
    }
}
