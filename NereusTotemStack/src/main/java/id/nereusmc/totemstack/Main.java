package id.nereusmc.totemstack;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private ConfigManager configManager;
    private StatisticsManager statisticsManager;
    private TotemManager totemManager;
    private StackManager stackManager;
    private ActivationManager activationManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        statisticsManager = new StatisticsManager(this);
        statisticsManager.init();
        totemManager = new TotemManager(this);
        stackManager = new StackManager(this);
        activationManager = new ActivationManager(this);
        guiManager = new GUIManager(this);

        getServer().getPluginManager().registerEvents(stackManager, this);
        getServer().getPluginManager().registerEvents(activationManager, this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        CommandManager commandManager = new CommandManager(this);
        PluginCommand command = getCommand("nereustotem");
        if (command != null) {
            command.setExecutor(commandManager);
            command.setTabCompleter(commandManager);
        }

        stackManager.normalizeOnline();
        getLogger().info("NereusTotemStack " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (statisticsManager != null) {
            statisticsManager.shutdown();
        }
    }

    /** Reloads config.yml from disk and re-applies stacking to online players. */
    public void reloadAll() {
        configManager.reload();
        stackManager.normalizeOnline();
    }

    public ConfigManager config() {
        return configManager;
    }

    public StatisticsManager statistics() {
        return statisticsManager;
    }

    public TotemManager totems() {
        return totemManager;
    }

    public StackManager stacks() {
        return stackManager;
    }

    public ActivationManager activation() {
        return activationManager;
    }

    public GUIManager gui() {
        return guiManager;
    }
}
