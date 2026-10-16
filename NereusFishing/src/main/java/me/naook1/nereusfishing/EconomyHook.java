package me.naook1.nereusfishing;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Talks to any Vault economy (EssentialsX etc.) through reflection, so no Vault jar is needed to build. */
public final class EconomyHook {

    private final NereusFishing plugin;
    private Object eco;
    private Method has;
    private Method withdraw;
    private Method deposit;
    private Method balance;
    private Method success;

    public EconomyHook(NereusFishing plugin) {
        this.plugin = plugin;
    }

    /** True when a Vault economy provider is available (looked up lazily, providers register late). */
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
            has = economy.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = economy.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = economy.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            balance = economy.getMethod("getBalance", OfflinePlayer.class);
            success = response.getMethod("transactionSuccess");
            eco = rsp.getProvider();
            plugin.getLogger().info("Economy hooked via Vault: " + eco.getClass().getSimpleName());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not hook Vault economy: " + e.getMessage());
            return false;
        }
    }

    public double balance(OfflinePlayer p) {
        if (!ready()) {
            return 0;
        }
        try {
            return ((Number) balance.invoke(eco, p)).doubleValue();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean has(OfflinePlayer p, long amount) {
        if (!ready()) {
            return false;
        }
        try {
            return (boolean) has.invoke(eco, p, (double) amount);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer p, long amount) {
        return transact(withdraw, p, amount);
    }

    public boolean deposit(OfflinePlayer p, long amount) {
        return transact(deposit, p, amount);
    }

    /** Deposits an exact (decimal) amount. */
    public boolean depositExact(OfflinePlayer p, double amount) {
        if (!ready() || amount <= 0) {
            return false;
        }
        try {
            Object response = deposit.invoke(eco, p, amount);
            return (boolean) success.invoke(response);
        } catch (Exception e) {
            plugin.getLogger().warning("Economy deposit failed: " + e.getMessage());
            return false;
        }
    }

    private boolean transact(Method m, OfflinePlayer p, long amount) {
        if (!ready()) {
            return false;
        }
        try {
            Object response = m.invoke(eco, p, (double) amount);
            return (boolean) success.invoke(response);
        } catch (Exception e) {
            plugin.getLogger().warning("Economy transaction failed: " + e.getMessage());
            return false;
        }
    }
}
