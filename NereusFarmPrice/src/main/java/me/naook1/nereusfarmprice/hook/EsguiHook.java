package me.naook1.nereusfarmprice.hook;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.naook1.nereusfarmprice.FarmManager;
import me.naook1.nereusfarmprice.NereusFarmPrice;
import me.naook1.nereusfarmprice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Connects to EconomyShopGUI's PreTransactionEvent (changes the payout of farm sales) and
 * PostTransactionEvent (pays a Vault bonus only if the price could not be changed). Everything goes through reflection so
 * the plugin builds without the EconomyShopGUI jar and keeps working across its versions.
 */
public final class EsguiHook {

    private static final String PRE = "me.gypopo.economyshopgui.api.events.PreTransactionEvent";
    private static final String POST = "me.gypopo.economyshopgui.api.events.PostTransactionEvent";

    private final NereusFarmPrice plugin;
    private boolean preOk;
    private boolean postOk;
    private String status = "belum terhubung";
    private String lastSale = "-";
    private String lastError = "-";
    private Event lastPre;
    private Event lastPost;
    private int applied;
    private int mismatches;
    private int fallbacks;
    private final Map<UUID, double[]> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Material> pendingMat = new ConcurrentHashMap<>();

    public EsguiHook(NereusFarmPrice plugin) {
        this.plugin = plugin;
    }

    public boolean connected() {
        return preOk && postOk;
    }

    public String status() {
        return status;
    }

    public String lastSale() {
        return lastSale;
    }

    public String lastError() {
        return lastError;
    }

    /** Hooks the events. Safe to call again; returns true when both events are hooked. */
    public boolean register() {
        if (connected()) {
            return true;
        }
        ClassLoader loader = null;
        for (String name : new String[]{"EconomyShopGUI", "EconomyShopGUI-Premium"}) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                loader = p.getClass().getClassLoader();
                break;
            }
        }
        if (loader == null) {
            status = "EconomyShopGUI tidak ditemukan";
            return false;
        }
        if (!preOk) {
            preOk = hook(loader, PRE, EventPriority.HIGH, this::onPre);
        }
        if (!postOk) {
            postOk = hook(loader, POST, EventPriority.MONITOR, this::onPost);
        }
        status = connected() ? "terhubung (Pre + Post transaction)"
                : "sebagian: pre=" + preOk + ", post=" + postOk + " (API EconomyShopGUI tidak cocok?)";
        return connected();
    }

    @SuppressWarnings("unchecked")
    private boolean hook(ClassLoader loader, String className, EventPriority priority, Consumer<Event> handler) {
        try {
            Class<?> raw = Class.forName(className, true, loader);
            if (!Event.class.isAssignableFrom(raw)) {
                return false;
            }
            Class<? extends Event> type = (Class<? extends Event>) raw;
            Bukkit.getPluginManager().registerEvent(type, new Listener() {
            }, priority, (listener, event) -> {
                if (!type.isInstance(event)) {
                    return;
                }
                try {
                    handler.accept(event);
                } catch (Throwable t) {
                    lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
                    plugin.getLogger().warning("EconomyShopGUI hook error: " + lastError);
                }
            }, plugin, true);
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("EconomyShopGUI event not found: " + className);
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook " + className + ": " + t.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ handlers

    private void onPre(Event e) {
        if (e == lastPre || !isSell(e)) {
            return;                 // same event seen twice: never apply the boost twice
        }
        lastPre = e;
        FarmManager fm = plugin.farm();
        fm.checkRotation();
        Player player = call(e, "getPlayer") instanceof Player p ? p : null;
        Map<?, ?> items = call(e, "getItems") instanceof Map<?, ?> m ? m : null;
        Map<?, ?> prices = call(e, "getPrices") instanceof Map<?, ?> m2 ? m2 : null;

        if (items != null && !items.isEmpty() && prices != null && !prices.isEmpty()) {
            multi(e, fm, player, items, prices);
        } else {
            single(e, fm, player);
        }
    }

    private void single(Event e, FarmManager fm, Player player) {
        Material mat = materialOf(call(e, "getShopItem"));
        if (mat == null || !fm.sellBoosted(mat)) {
            return;
        }
        Object old = call(e, "getPrice");
        if (!(old instanceof Number n)) {
            return;
        }
        double before = n.doubleValue();
        double f = fm.sellFactor(mat);
        double after = before * f;
        try {
            e.getClass().getMethod("setPrice", double.class).invoke(e, after);
            applied++;
            lastSale = mat + " " + before + " -> " + after;
            note(player, mat, after - before);
        } catch (Exception ex) {
            lastError = "setPrice: " + ex.getMessage();
            fallback(player, mat, after - before);
        }
    }

    @SuppressWarnings("unchecked")
    private void multi(Event e, FarmManager fm, Player player, Map<?, ?> items, Map<?, ?> prices) {
        double delta = 0;
        Material shown = null;
        for (Map.Entry<?, ?> en : items.entrySet()) {
            Material mat = materialOf(en.getKey());
            if (mat == null || !fm.sellBoosted(mat) || !(en.getValue() instanceof Number qty)) {
                continue;
            }
            double base = fm.normalPrice(mat);
            if (base <= 0) {
                continue;
            }
            delta += qty.doubleValue() * base * (fm.sellFactor(mat) - 1.0);
            if (shown == null) {
                shown = mat;
            }
        }
        if (delta < 0.01) {
            return;
        }
        Object key = null;
        for (Object k : prices.keySet()) {
            if (key == null || String.valueOf(k).toUpperCase(Locale.ROOT).contains("VAULT")) {
                key = k;
            }
        }
        if (key == null || !(prices.get(key) instanceof Number old)) {
            fallback(player, shown, delta);
            return;
        }
        try {
            ((Map<Object, Object>) prices).put(key, old.doubleValue() + delta);
            applied++;
            lastSale = "bulk " + items.size() + " item, +" + String.format(Locale.ROOT, "%.2f", delta);
            note(player, shown, delta);
        } catch (Exception ex) {
            lastError = "prices map: " + ex.getMessage();
            fallback(player, shown, delta);
        }
    }

    /** The price could not be changed in the event: pay the bonus through Vault once the sale succeeds. */
    private void fallback(Player player, Material mat, double delta) {
        if (player == null || delta < 0.01 || !plugin.getConfig().getBoolean("integration.fallback-vault-bonus", true)) {
            return;
        }
        pending.merge(player.getUniqueId(), new double[]{delta}, (a, b) -> new double[]{a[0] + b[0]});
        pendingMat.put(player.getUniqueId(), mat);
    }

    private void onPost(Event e) {
        if (e == lastPost || !isSell(e)) {
            return;
        }
        lastPost = e;
        Player player = call(e, "getPlayer") instanceof Player p ? p : null;
        if (player == null) {
            return;
        }
        double[] bonus = pending.remove(player.getUniqueId());
        Material mat = pendingMat.remove(player.getUniqueId());
        if (bonus == null) {
            return;
        }
        Object result = call(e, "getTransactionResult");
        if (result != null && !result.toString().toUpperCase(Locale.ROOT).contains("SUCCESS")) {
            return;
        }
        if (plugin.economy().ready() && plugin.economy().depositExact(player, bonus[0])) {
            fallbacks++;
            lastSale = "vault bonus +" + String.format(Locale.ROOT, "%.2f", bonus[0]);
            note(player, mat, bonus[0]);
        } else {
            mismatches++;
            lastError = "bonus tidak bisa dibayar (Vault tidak siap)";
            plugin.getLogger().warning(lastError);
        }
    }

    private void note(Player player, Material mat, double delta) {
        FarmManager fm = plugin.farm();
        if (player == null || mat == null || !plugin.getConfig().getBoolean("integration.notify-sale", true)
                || delta < 0.01) {
            return;
        }
        FarmManager.Candidate c = fm.candidate(mat);
        String msg = plugin.messages().format("sale-note", "name", c == null ? mat.name() : c.name(),
                "delta", fm.fmt(delta), "percent", fm.percentText());
        player.sendActionBar(Text.parse(msg));
    }

    public String esguiVersion() {
        for (String name : new String[]{"EconomyShopGUI", "EconomyShopGUI-Premium"}) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null) {
                return name + " " + p.getDescription().getVersion() + (p.isEnabled() ? "" : " (disabled)");
            }
        }
        return "tidak ditemukan";
    }

    public int applied() {
        return applied;
    }

    public int mismatches() {
        return mismatches;
    }

    public int fallbacks() {
        return fallbacks;
    }

    // ------------------------------------------------------------------ reflection helpers

    private boolean isSell(Event e) {
        Object type = call(e, "getTransactionType");
        return type != null && type.toString().toUpperCase(Locale.ROOT).contains("SELL");
    }

    private static Object call(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }

    /** The Bukkit material of an EconomyShopGUI ShopItem (tries the usual getters, then scans). */
    private Material materialOf(Object shopItem) {
        if (shopItem == null) {
            return null;
        }
        for (String name : new String[]{"getItemToGive", "getItemStack", "getItem", "getMaterial", "getType"}) {
            Material m = asMaterial(call(shopItem, name));
            if (m != null) {
                return m;
            }
        }
        for (Method m : shopItem.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && (ItemStack.class.isAssignableFrom(m.getReturnType())
                    || m.getReturnType() == Material.class)) {
                try {
                    Material found = asMaterial(m.invoke(shopItem));
                    if (found != null) {
                        return found;
                    }
                } catch (Exception ignored) {
                    // try the next one
                }
            }
        }
        return null;
    }

    private static Material asMaterial(Object o) {
        if (o instanceof ItemStack is) {
            return is.getType();
        }
        if (o instanceof Material m) {
            return m;
        }
        return null;
    }
}
