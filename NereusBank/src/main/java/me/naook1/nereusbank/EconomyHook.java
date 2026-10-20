package me.naook1.nereusbank;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault economy through reflection (no Vault jar needed to build). This is the player's wallet. */
public final class EconomyHook {

    private final NereusBank plugin;
    private Object eco;
    private Method balance;
    private Method withdraw;
    private Method deposit;
    private Method success;

    public EconomyHook(NereusBank plugin) {
        this.plugin = plugin;
    }

    public boolean ready() {
        if (eco != null) {
            return true;
        }
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economy);
            if (rsp == null) {
                return false;
            }
            Class<?> response = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            balance = economy.getMethod("getBalance", OfflinePlayer.class);
            withdraw = economy.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = economy.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            success = response.getMethod("transactionSuccess");
            eco = rsp.getProvider();
            plugin.getLogger().info("Wallet economy hooked via Vault: " + eco.getClass().getSimpleName());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not hook Vault economy: " + e.getMessage());
            return false;
        }
    }

    public long wallet(OfflinePlayer p) {
        if (!ready()) {
            return 0;
        }
        try {
            return (long) Math.floor(((Number) balance.invoke(eco, p)).doubleValue());
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean take(OfflinePlayer p, long amount) {
        return run(withdraw, p, amount);
    }

    public boolean give(OfflinePlayer p, long amount) {
        return run(deposit, p, amount);
    }

    private boolean run(Method m, OfflinePlayer p, long amount) {
        if (!ready() || amount <= 0) {
            return false;
        }
        try {
            Object response = m.invoke(eco, p, (double) amount);
            return (boolean) success.invoke(response);
        } catch (Exception e) {
            plugin.getLogger().warning("Wallet transaction failed: " + e.getMessage());
            return false;
        }
    }
}
