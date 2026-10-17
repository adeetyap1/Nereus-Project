package me.naook1.nereusicons;

import me.naook1.nereusicons.command.IconCommand;
import me.naook1.nereusicons.hook.PapiHook;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusIcons - replaces :name: tokens with rank icon characters (same characters for Java and Bedrock).
 * Developer: naook1/adityap
 */
public final class NereusIcons extends JavaPlugin {

    private IconManager icons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        icons = new IconManager(this);
        icons.reload();
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        IconCommand cmd = new IconCommand(this);
        getCommand("icon").setExecutor(cmd);
        getCommand("icon").setTabCompleter(cmd);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                PapiHook.registerExpansion(this);
            } catch (Throwable t) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
        getLogger().info("NereusIcons v" + getDescription().getVersion() + " enabled | "
                + icons.ids().size() + " icons | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    public IconManager icons() {
        return icons;
    }
}
