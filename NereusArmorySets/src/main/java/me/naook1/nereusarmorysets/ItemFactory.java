package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.Bonus;
import me.naook1.nereusarmorysets.Model.SetDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Membuat item armor set dan membaca tag set dari item. */
public final class ItemFactory {
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private final NereusArmorySets plugin;
    private final NamespacedKey setKey;
    private final NamespacedKey slotKey;

    public ItemFactory(NereusArmorySets plugin) {
        this.plugin = plugin;
        this.setKey = new NamespacedKey(plugin, "set");
        this.slotKey = new NamespacedKey(plugin, "slot");
    }

    public static Component text(String legacyAmpersand) {
        return AMP.deserialize(legacyAmpersand).decoration(TextDecoration.ITALIC, false);
    }

    private static String vanillaSuffix(String slot) {
        return switch (slot) {
            case "head" -> "HELMET";
            case "chest" -> "CHESTPLATE";
            case "legs" -> "LEGGINGS";
            default -> "BOOTS";
        };
    }

    /** @return item atau null jika material tidak valid. */
    public ItemStack create(SetDef set, String slot) {
        Material mat = Material.matchMaterial(set.material() + "_" + vanillaSuffix(slot));
        if (mat == null) {
            return null;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String tc = set.tier().color();

        meta.displayName(text(tc + set.pieces().get(slot)));

        List<Component> lore = new ArrayList<>();
        lore.add(text("&7Set: " + tc + set.display() + " &8(" + set.tier().name() + ")"));
        for (Map.Entry<Integer, List<Bonus>> e : set.bonuses().entrySet()) {
            lore.add(Component.empty());
            lore.add(text("&7Set Bonus (" + e.getKey() + "/4):"));
            for (Bonus b : e.getValue()) {
                lore.add(text("&a " + describe(b, set.tier().multiplier())));
            }
        }
        meta.lore(lore);

        if (meta instanceof LeatherArmorMeta leather && set.leatherColor() != null) {
            leather.setColor(Color.fromRGB(set.leatherColor()));
        }
        meta.addItemFlags(ItemFlag.HIDE_DYE);

        int cmdBase = plugin.getConfig().getInt("custom-model-data-base", 0);
        if (cmdBase > 0) {
            int setIndex = new ArrayList<>(plugin.registry().sets().keySet()).indexOf(set.id());
            int slotIndex = List.of(SetRegistry.SLOTS).indexOf(slot);
            meta.setCustomModelData(cmdBase + setIndex * 4 + slotIndex);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(setKey, PersistentDataType.STRING, set.id());
        pdc.set(slotKey, PersistentDataType.STRING, slot);

        item.setItemMeta(meta);
        return item;
    }

    /** @return id set dari item, atau null jika bukan item ArmorySets. */
    public String setIdOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
    }

    /** Teks bonus yang sudah dikali multiplier tier, contoh "+9% Attack Damage". */
    public static String describe(Bonus b, double multiplier) {
        double v = b.amount() * multiplier;
        String name = prettyName(b.attribute());
        if (b.operation() == AttributeModifier.Operation.ADD_NUMBER) {
            return "+" + trim(v) + " " + name;
        }
        return "+" + trim(v * 100.0) + "% " + name;
    }

    private static String trim(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private static String prettyName(String attr) {
        StringBuilder sb = new StringBuilder();
        for (String part : attr.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return sb.toString();
    }
}
