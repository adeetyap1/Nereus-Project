package me.naook1.nereusrealm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import me.naook1.nereusrealm.afk.AfkListener;
import me.naook1.nereusrealm.afk.AfkManager;
import me.naook1.nereusrealm.command.AfkCommand;
import me.naook1.nereusrealm.command.RedeemCommand;
import me.naook1.nereusrealm.command.NereusCommand;
import me.naook1.nereusrealm.daily.DailyManager;
import me.naook1.nereusrealm.data.DataStore;
import me.naook1.nereusrealm.feedback.FeedbackManager;
import me.naook1.nereusrealm.gadget.GadgetListener;
import me.naook1.nereusrealm.gadget.GadgetManager;
import me.naook1.nereusrealm.gui.MenuListener;
import me.naook1.nereusrealm.gui.MenuManager;
import me.naook1.nereusrealm.hook.PapiHook;
import me.naook1.nereusrealm.icon.IconChatListener;
import me.naook1.nereusrealm.icon.IconManager;
import me.naook1.nereusrealm.input.PromptListener;
import me.naook1.nereusrealm.input.PromptManager;
import me.naook1.nereusrealm.joinleave.CustomMessages;
import me.naook1.nereusrealm.joinleave.JoinLeaveListener;
import me.naook1.nereusrealm.joinleave.StyleManager;
import me.naook1.nereusrealm.point.PointManager;
import me.naook1.nereusrealm.rank.RankManager;
import me.naook1.nereusrealm.redeem.RedeemManager;
import me.naook1.nereusrealm.util.Messages;
import me.naook1.nereusrealm.util.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusRealm - one menu for everything: join/leave, rating, ranks, AFK, daily, redeem, teleports, hotbar gadget.
 * Developer: naook1/adityap
 */
public final class NereusRealm extends JavaPlugin {

    private Messages messages;
    private DataStore data;
    private CustomMessages custom;
    private StyleManager styles;
    private AfkManager afk;
    private DailyManager daily;
    private RankManager ranks;
    private FeedbackManager feedback;
    private PromptManager prompts;
    private RedeemManager redeem;
    private MenuManager menus;
    private GadgetManager gadgets;
    private PointManager points;
    private IconManager icons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Text.setPapi(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI"));

        messages = new Messages(this);
        data = new DataStore(this);
        custom = new CustomMessages(this);
        styles = new StyleManager(this);
        afk = new AfkManager(this);
        daily = new DailyManager(this);
        ranks = new RankManager(this);
        feedback = new FeedbackManager(this);
        prompts = new PromptManager(this);
        redeem = new RedeemManager(this);
        menus = new MenuManager(this);
        gadgets = new GadgetManager(this);
        points = new PointManager(this);
        points.load();
        icons = new IconManager(this);
        reloadAll();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new JoinLeaveListener(this), this);
        pm.registerEvents(new AfkListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new PromptListener(this), this);
        pm.registerEvents(new GadgetListener(this), this);
        pm.registerEvents(new IconChatListener(this), this);

        NereusCommand nereus = new NereusCommand(this);
        getCommand("nereus").setExecutor(nereus);
        getCommand("nereus").setTabCompleter(nereus);
        getCommand("afk").setExecutor(new AfkCommand(this));
        RedeemCommand redeemCmd = new RedeemCommand(this);
        getCommand("redeem").setExecutor(redeemCmd);
        getCommand("redeem").setTabCompleter(redeemCmd);

        afk.start();
        menus.start();
        gadgets.start();
        getServer().getScheduler().runTaskTimer(this, () -> {
            data.flush(true);
            feedback.flush(true);
            redeem.flush(true);
        }, 20L * 30, 20L * 30);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                PapiHook.registerExpansion(this);
            } catch (Throwable t) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        getLogger().info("NereusRealm v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (afk != null) {
            afk.stop();
        }
        if (menus != null) {
            menus.stop();
        }
        if (gadgets != null) {
            gadgets.stop();
        }
        if (data != null) {
            data.flush(false);
        }
        if (feedback != null) {
            feedback.flush(false);
        }
        if (redeem != null) {
            redeem.flush(false);
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        custom.reload();
        styles.reload();
        afk.reload();
        daily.reload();
        ranks.reload();
        feedback.reload();
        prompts.reload();
        redeem.reload();
        menus.reload();
        gadgets.reload();
        icons.reload();
        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
            gadgets.ensure(p, true);
        }
    }

    /**
     * Loads a bundled yml (copied to the plugin folder on first run, missing keys fall back to jar defaults).
     * If the file in the plugin folder has an older "file-version" than the jar, it is renamed to
     * "name.old-vN" and a fresh copy is written, so new menus/messages always appear.
     */
    public FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = null;
        try (InputStream in = getResource(name)) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // defaults are optional
        }
        if (defaults != null) {
            int have = cfg.getInt("file-version", 0);
            int want = defaults.getInt("file-version", 0);
            if (have < want) {
                File backup = new File(getDataFolder(), name + ".old-v" + have);
                try {
                    Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    saveResource(name, true);
                    cfg = YamlConfiguration.loadConfiguration(file);
                    getLogger().warning(name + " is outdated, replaced with the new version. Old copy: " + backup.getName());
                } catch (IOException e) {
                    getLogger().warning("Could not update " + name + ": " + e.getMessage());
                }
            }
            cfg.setDefaults(defaults);
        }
        return cfg;
    }

    public Messages messages() {
        return messages;
    }

    public DataStore data() {
        return data;
    }

    public CustomMessages custom() {
        return custom;
    }

    public StyleManager styles() {
        return styles;
    }

    public AfkManager afk() {
        return afk;
    }

    public DailyManager daily() {
        return daily;
    }

    public RankManager ranks() {
        return ranks;
    }

    public FeedbackManager feedback() {
        return feedback;
    }

    public PromptManager prompts() {
        return prompts;
    }

    public RedeemManager redeem() {
        return redeem;
    }

    public MenuManager menus() {
        return menus;
    }

    public GadgetManager gadgets() {
        return gadgets;
    }

    public PointManager points() {
        return points;
    }

    public IconManager icons() {
        return icons;
    }
}
