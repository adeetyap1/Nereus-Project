package id.nereusmc.totemstack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed access to config.yml. All GUI changes go through here and are persisted with save(). */
public final class ConfigManager {

    public static final int MIN_STACK = 2;
    public static final int MAX_STACK = 99;
    public static final int[] STACK_CHOICES = {16, 32, 64, 99};
    public static final List<String> EFFECTS = List.of("regeneration", "absorption", "fire-resistance");
    public static final int MIN_DURATION = 20;
    public static final int MAX_DURATION = 72000;
    public static final int MAX_AMPLIFIER = 9;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            "activation", "&aTotem activated! &7Remaining: &e%amount%",
            "no-totem", "&cYou don't have any totems!",
            "no-permission", "&cYou don't have permission.",
            "reloaded", "&aConfiguration reloaded."
    );

    private final Main plugin;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        validate();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        validate();
    }

    public void save() {
        plugin.saveConfig();
    }

    /** Overwrites config.yml with the bundled defaults. */
    public void resetToDefaults() {
        plugin.saveResource("config.yml", true);
        plugin.reloadConfig();
        validate();
    }

    private void validate() {
        FileConfiguration c = cfg();
        boolean changed = false;

        int max = c.getInt("stacking.max-stack-size", 64);
        if (max < MIN_STACK || max > MAX_STACK) {
            plugin.getLogger().warning("Invalid stacking.max-stack-size (" + max + "), reset to 64.");
            c.set("stacking.max-stack-size", 64);
            changed = true;
        }

        for (String key : EFFECTS) {
            String path = "effects." + key;
            int duration = c.getInt(path + ".duration", 100);
            int clampedDuration = Math.max(MIN_DURATION, Math.min(MAX_DURATION, duration));
            if (clampedDuration != duration || !c.isSet(path + ".duration")) {
                c.set(path + ".duration", clampedDuration);
                changed = true;
            }
            int amplifier = c.getInt(path + ".amplifier", 0);
            int clampedAmp = Math.max(0, Math.min(MAX_AMPLIFIER, amplifier));
            if (clampedAmp != amplifier || !c.isSet(path + ".amplifier")) {
                c.set(path + ".amplifier", clampedAmp);
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    // ---------------------------------------------------------------- text

    public Component color(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /** Component for item names / lore: legacy colors, italics off. */
    public Component itemText(String text) {
        return color(text).decoration(TextDecoration.ITALIC, false);
    }

    public Component prefixed(String raw) {
        return color(cfg().getString("messages.prefix", "&6&lNEREUS &8» ") + raw);
    }

    /** Prefixed message from config; replacements are (placeholder, value) pairs. */
    public Component message(String key, String... replacements) {
        String raw = cfg().getString("messages." + key, DEFAULT_MESSAGES.getOrDefault(key, "&c" + key));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return prefixed(raw);
    }

    // ------------------------------------------------------------- general

    public boolean isPluginEnabled() {
        return cfg().getBoolean("settings.enabled", true);
    }

    public void setPluginEnabled(boolean value) {
        cfg().set("settings.enabled", value);
    }

    // ------------------------------------------------------------ stacking

    public boolean isStackingEnabled() {
        return cfg().getBoolean("stacking.enabled", true);
    }

    public void setStackingEnabled(boolean value) {
        cfg().set("stacking.enabled", value);
    }

    public int getMaxStackSize() {
        return cfg().getInt("stacking.max-stack-size", 64);
    }

    /** @return false if the size is outside the allowed range and was not applied. */
    public boolean setMaxStackSize(int size) {
        if (size < MIN_STACK || size > MAX_STACK) {
            return false;
        }
        cfg().set("stacking.max-stack-size", size);
        return true;
    }

    // ---------------------------------------------------------- activation

    public boolean isActivationEnabled() {
        return cfg().getBoolean("activation.enabled", true);
    }

    public void setActivationEnabled(boolean value) {
        cfg().set("activation.enabled", value);
    }

    public boolean isInventoryActivation() {
        return cfg().getBoolean("activation.inventory-activation", true);
    }

    public void setInventoryActivation(boolean value) {
        cfg().set("activation.inventory-activation", value);
    }

    public boolean isHotbarEnabled() {
        return cfg().getBoolean("activation.hotbar", true);
    }

    public void setHotbarEnabled(boolean value) {
        cfg().set("activation.hotbar", value);
    }

    public boolean isMainInventoryEnabled() {
        return cfg().getBoolean("activation.main-inventory", true);
    }

    public void setMainInventoryEnabled(boolean value) {
        cfg().set("activation.main-inventory", value);
    }

    /** Search order for the non-hand slots: always contains HOTBAR and INVENTORY exactly once. */
    public List<String> getSearchOrder() {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (String entry : cfg().getStringList("activation.priority")) {
            String upper = entry.trim().toUpperCase(Locale.ROOT);
            if (upper.equals("HOTBAR") || upper.equals("INVENTORY")) {
                order.add(upper);
            }
        }
        order.add("HOTBAR");
        order.add("INVENTORY");
        return new ArrayList<>(order);
    }

    public boolean isHotbarFirst() {
        return getSearchOrder().get(0).equals("HOTBAR");
    }

    public void setHotbarFirst(boolean hotbarFirst) {
        cfg().set("activation.priority", hotbarFirst
                ? List.of("MAIN_HAND", "OFF_HAND", "HOTBAR", "INVENTORY")
                : List.of("MAIN_HAND", "OFF_HAND", "INVENTORY", "HOTBAR"));
    }

    // ------------------------------------------------------------- effects

    public boolean isEffectEnabled(String key) {
        return cfg().getBoolean("effects." + key + ".enabled", true);
    }

    public void setEffectEnabled(String key, boolean value) {
        cfg().set("effects." + key + ".enabled", value);
    }

    public int getEffectDuration(String key) {
        return cfg().getInt("effects." + key + ".duration", 100);
    }

    public void setEffectDuration(String key, int ticks) {
        cfg().set("effects." + key + ".duration", Math.max(MIN_DURATION, Math.min(MAX_DURATION, ticks)));
    }

    public int getEffectAmplifier(String key) {
        return cfg().getInt("effects." + key + ".amplifier", 0);
    }

    public void setEffectAmplifier(String key, int amplifier) {
        cfg().set("effects." + key + ".amplifier", Math.max(0, Math.min(MAX_AMPLIFIER, amplifier)));
    }

    // ----------------------------------------------------------------- gui

    public boolean isGuiEnabled() {
        return cfg().getBoolean("gui.enabled", true);
    }

    public String getGuiTitle() {
        return cfg().getString("gui.title", "&8NereusTotemStack");
    }

    // ---------------------------------------------------------- statistics

    public boolean isStatisticsEnabled() {
        return cfg().getBoolean("statistics.enabled", true);
    }

    public int getSaveIntervalSeconds() {
        return Math.max(1, cfg().getInt("statistics.save-interval", 300));
    }
}
