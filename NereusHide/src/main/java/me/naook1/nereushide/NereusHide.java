package me.naook1.nereushide;

import me.naook1.nereushide.command.HideCommand;
import me.naook1.nereushide.gui.MenuListener;
import me.naook1.nereushide.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusHide - hide and seek with a random seeker draw and a points system.
 * Developer: naook1/adityap
 */
public final class NereusHide extends JavaPlugin {

    private Messages messages;
    private Settings settings;
    private Arena arena;
    private PlayerStates states;
    private DataStore data;
    private GameManager game;
    private int saveCounter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        settings = new Settings(this);
        arena = new Arena(this);
        states = new PlayerStates(this);
        data = new DataStore(this);
        game = new GameManager(this);
        messages.reload();
        arena.load();
        data.load();

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        HideCommand cmd = new HideCommand(this);
        getCommand("hide").setExecutor(cmd);
        getCommand("hide").setTabCompleter(cmd);

        getServer().getScheduler().runTaskTimer(this, this::second, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, game::boundaryTick, 10L, 5L);

        getLogger().info("NereusHide v" + getDescription().getVersion() + " enabled | arena: "
                + (arena.complete() ? "ready" : "belum lengkap (" + arena.missing() + ")")
                + " | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stop();
        }
        if (data != null) {
            data.flush(false);
        }
    }

    private void second() {
        game.secondTick();
        if (++saveCounter >= 60) {
            saveCounter = 0;
            data.flush(true);
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        arena.load();
    }

    public Messages messages() {
        return messages;
    }

    public Settings settings() {
        return settings;
    }

    public Arena arena() {
        return arena;
    }

    public PlayerStates states() {
        return states;
    }

    public DataStore data() {
        return data;
    }

    public GameManager game() {
        return game;
    }
}
