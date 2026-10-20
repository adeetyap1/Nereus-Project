package me.naook1.nereusbank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import me.naook1.nereusbank.command.BankCommand;
import me.naook1.nereusbank.command.CardCommand;
import me.naook1.nereusbank.util.Fmt;
import me.naook1.nereusbank.util.Messages;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Nereus Bank - accounts, transfers, interest, fixed deposits, loans and cards.
 * Developer: naook1/adityap
 */
public final class NereusBank extends JavaPlugin {

    private Messages messages;
    private EconomyHook economy;
    private BankManager bank;
    private CardManager cards;
    private BankGui gui;
    private final Map<UUID, Consumer<String>> prompts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Fmt.symbol = getConfig().getString("currency-symbol", "$");
        messages = new Messages(this);
        economy = new EconomyHook(this);
        bank = new BankManager(this);
        cards = new CardManager(this);
        gui = new BankGui(this);
        messages.reload();
        bank.reloadConfig();
        bank.load();

        getServer().getPluginManager().registerEvents(new BankListener(this), this);
        BankCommand bc = new BankCommand(this);
        getCommand("bank").setExecutor(bc);
        getCommand("bank").setTabCompleter(bc);
        CardCommand cc = new CardCommand(this);
        getCommand("card").setExecutor(cc);
        getCommand("card").setTabCompleter(cc);

        getServer().getScheduler().runTaskTimer(this, () -> bank.tick(), 1200L, 6000L);
        getServer().getScheduler().runTaskTimer(this, () -> bank.save(), 6000L, 6000L);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new me.naook1.nereusbank.hook.BankExpansion(this).register();
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found - deposit/withdraw need a Vault economy.");
        }
        getLogger().info("NereusBank v" + getDescription().getVersion() + " enabled | accounts: "
                + bank.all().size() + " | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (bank != null) {
            bank.markDirty();
            bank.save();
        }
    }

    public void reloadAll() {
        reloadConfig();
        Fmt.symbol = getConfig().getString("currency-symbol", "$");
        messages.reload();
        bank.reloadConfig();
    }

    public Messages messages() {
        return messages;
    }

    public EconomyHook economy() {
        return economy;
    }

    public BankManager bank() {
        return bank;
    }

    public CardManager cards() {
        return cards;
    }

    public BankGui gui() {
        return gui;
    }

    // ---------------- chat prompts ----------------

    public void ask(Player p, String messageKey, Consumer<String> callback) {
        prompts.put(p.getUniqueId(), callback);
        messages.send(p, messageKey);
        UUID id = p.getUniqueId();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (prompts.get(id) == callback) {
                prompts.remove(id);
            }
        }, 20L * 60);
    }

    public Consumer<String> takePrompt(UUID id) {
        return prompts.remove(id);
    }

    public boolean hasPrompt(UUID id) {
        return prompts.containsKey(id);
    }

    // ---------------- ATMs ----------------

    private static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    public boolean isAtm(Block b) {
        return getConfig().getStringList("atm.blocks").contains(key(b.getLocation()));
    }

    public boolean addAtm(Block b) {
        List<String> l = new ArrayList<>(getConfig().getStringList("atm.blocks"));
        String k = key(b.getLocation());
        if (l.contains(k)) {
            return false;
        }
        l.add(k);
        getConfig().set("atm.blocks", l);
        saveConfig();
        return true;
    }

    public boolean removeAtm(Block b) {
        List<String> l = new ArrayList<>(getConfig().getStringList("atm.blocks"));
        if (!l.remove(key(b.getLocation()))) {
            return false;
        }
        getConfig().set("atm.blocks", l);
        saveConfig();
        return true;
    }

    public List<String> atms() {
        return getConfig().getStringList("atm.blocks");
    }
}
