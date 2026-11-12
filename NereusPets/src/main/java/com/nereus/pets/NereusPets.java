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
    private NamespacedKey wildKey;
    private NamespacedKey wildTimeKey;
    private NamespacedKey projKey;
    private NamespacedKey projDmgKey;
    private NamespacedKey itemKey;
    private Combat combat;
    private Items items;
    private Duels duels;
    private Wild wild;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        petKey = new NamespacedKey(this, "pet_owner");
        orbKey = new NamespacedKey(this, "orb_tier");
        wildKey = new NamespacedKey(this, "wild_level");
        wildTimeKey = new NamespacedKey(this, "wild_expire");
        projKey = new NamespacedKey(this, "proj_pet");
        projDmgKey = new NamespacedKey(this, "proj_dmg");
        itemKey = new NamespacedKey(this, "item_kind");
        orbs = new Orbs(this);
        manager = new PetManager(this);
        manager.reloadSpecies();
        combat = new Combat(this, manager);
        items = new Items(this, manager);
        duels = new Duels(this, manager);
        wild = new Wild(this, manager);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PetListener(this, manager), this);
        pm.registerEvents(new CaptureListener(this, manager, orbs), this);
        pm.registerEvents(new MenuListener(), this);
        ItemListener itemListener = new ItemListener(this, manager, items, combat);
        pm.registerEvents(itemListener, this);

        PetCommand command = new PetCommand(this, manager, orbs, itemListener);
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
        duels.start();
        wild.start();
    }

    @Override
    public void onDisable() {
        if (wild != null) {
            wild.stop();
        }
        if (manager != null) {
            manager.shutdown();
        }
        if (duels != null) {
            duels.stop();
        }
    }

    public void reloadAll() {
        reloadConfig();
        manager.reloadSpecies();
        wild.start();
    }

    public PetManager manager() {
        return manager;
    }

    public NamespacedKey petKey() {
        return petKey;
    }

    public Combat combat() {
        return combat;
    }

    public Items items() {
        return items;
    }

    public Duels duels() {
        return duels;
    }

    public Wild wild() {
        return wild;
    }

    public NamespacedKey wildKey() {
        return wildKey;
    }

    public NamespacedKey wildTimeKey() {
        return wildTimeKey;
    }

    public NamespacedKey projKey() {
        return projKey;
    }

    public NamespacedKey projDmgKey() {
        return projDmgKey;
    }

    public NamespacedKey itemKey() {
        return itemKey;
    }

    public void broadcast(String key, String... kv) {
        String prefix = getConfig().getString("messages.prefix", "");
        getServer().broadcast(LEGACY.deserialize(prefix + raw(key, kv)));
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
