package me.naook1.nereusfarmprice.hook;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
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
 * PostTransactionEvent (records sales for the market saturation). Everything goes through reflection so
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
    private int unknownItems;

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

    public int unknownItems() {
        return unknownItems;
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
        if (!isSell(e)) {
            return;
        }
        FarmManager fm = plugin.farm();
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
        FarmManager.Crop crop = mat == null ? null : fm.cropOf(mat);
        if (crop == null) {
            return;
        }
        double f = fm.factor(crop);
        if (Math.abs(f - 1.0) < 0.001) {
            return;
        }
        Object old = call(e, "getPrice");
        if (!(old instanceof Number n)) {
            return;
        }
        double before = n.doubleValue();
        double after = before * f;
        try {
            e.getClass().getMethod("setPrice", double.class).invoke(e, after);
        } catch (Exception ex) {
            lastError = "setPrice: " + ex.getMessage();
            return;
        }
        lastSale = crop.id() + " x" + String.format(Locale.ROOT, "%.2f", f) + " (" + before + " -> " + after + ")";
        note(player, crop, f, after - before);
    }

    @SuppressWarnings("unchecked")
    private void multi(Event e, FarmManager fm, Player player, Map<?, ?> items, Map<?, ?> prices) {
        double delta = 0;
        FarmManager.Crop shown = null;
        double shownFactor = 1.0;
        for (Map.Entry<?, ?> en : items.entrySet()) {
            Material mat = materialOf(en.getKey());
            FarmManager.Crop crop = mat == null ? null : fm.cropOf(mat);
            if (crop == null || !(en.getValue() instanceof Number qty)) {
                continue;
            }
            double base = fm.basePrice(crop, mat);
            if (base <= 0) {
                unknownItems++;
                continue;
            }
            double f = fm.factor(crop);
            delta += qty.doubleValue() * base * (f - 1.0);
            if (shown == null || Math.abs(f - 1.0) > Math.abs(shownFactor - 1.0)) {
                shown = crop;
                shownFactor = f;
            }
        }
        if (Math.abs(delta) < 0.01) {
            return;
        }
        Object key = null;
        for (Object k : prices.keySet()) {
            if (key == null || String.valueOf(k).toUpperCase(Locale.ROOT).contains("VAULT")) {
                key = k;
            }
        }
        if (key == null || !(prices.get(key) instanceof Number old)) {
            return;
        }
        double after = Math.max(0, old.doubleValue() + delta);
        try {
            ((Map<Object, Object>) prices).put(key, after);
        } catch (Exception ex) {
            lastError = "prices map: " + ex.getMessage();
            return;
        }
        lastSale = "bulk " + items.size() + " item, delta " + String.format(Locale.ROOT, "%.2f", delta);
        note(player, shown, shownFactor, delta);
    }

    private void onPost(Event e) {
        if (!isSell(e)) {
            return;
        }
        Object result = call(e, "getTransactionResult");
        if (result != null && !result.toString().toUpperCase(Locale.ROOT).contains("SUCCESS")) {
            return;
        }
        FarmManager fm = plugin.farm();
        Map<?, ?> items = call(e, "getItems") instanceof Map<?, ?> m ? m : null;
        if (items != null && !items.isEmpty()) {
            for (Map.Entry<?, ?> en : items.entrySet()) {
                Material mat = materialOf(en.getKey());
                FarmManager.Crop crop = mat == null ? null : fm.cropOf(mat);
                if (crop != null && en.getValue() instanceof Number qty) {
                    fm.sold(crop, qty.intValue());
                }
            }
            return;
        }
        Material mat = materialOf(call(e, "getShopItem"));
        FarmManager.Crop crop = mat == null ? null : fm.cropOf(mat);
        if (crop != null && call(e, "getAmount") instanceof Number qty) {
            fm.sold(crop, qty.intValue());
        }
    }

    private void note(Player player, FarmManager.Crop crop, double factor, double delta) {
        if (player == null || !plugin.getConfig().getBoolean("notify-sale", true) || Math.abs(delta) < 0.01) {
            return;
        }
        FarmManager fm = plugin.farm();
        String msg = plugin.messages().format("sale-note", "crop", crop.name(),
                "sign", delta >= 0 ? "+" : "-", "delta", fm.fmt(Math.abs(delta)),
                "mult", String.format(Locale.ROOT, "%.2f", factor));
        player.sendActionBar(Text.parse(msg));
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
