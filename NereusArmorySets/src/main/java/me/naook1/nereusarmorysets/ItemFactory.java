package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.Bonus;
import me.naook1.nereusarmorysets.Model.SetDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Membuat item armor set (dengan pertahanan nyata) dan membaca tag set dari item. */
public final class ItemFactory {
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    /** Item pembawa model 3D untuk ItemDisplay (tidak pernah dipegang pemain). */
    public static final Material CARRIER = Material.ARMADILLO_SCUTE;

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

    private static EquipmentSlotGroup groupOf(String slot) {
        return switch (slot) {
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            default -> EquipmentSlotGroup.FEET;
        };
    }

    // ---- nilai pertahanan ----

    /** Armor satu piece: nilai kelas x pengali tier, dibulatkan ke 0.5. */
    public double armorOf(SetDef set, String slot) {
        double base = plugin.registry().classOf(set).armor()[SetRegistry.slotIndex(slot)];
        return Math.round(base * set.tier().armorMultiplier() * 2.0) / 2.0;
    }

    public double toughnessOf(SetDef set) {
        return plugin.registry().classOf(set).toughness() + set.tier().toughnessBonus();
    }

    public double knockbackOf(SetDef set) {
        return plugin.registry().classOf(set).knockback();
    }

    public int durabilityOf(SetDef set, String slot) {
        return (int) Math.round(plugin.registry().durabilityBase(SetRegistry.slotIndex(slot))
                * set.tier().durabilityMultiplier());
    }

    /** Total armor satu set penuh. */
    public double totalArmor(SetDef set) {
        double sum = 0;
        for (String slot : SetRegistry.SLOTS) sum += armorOf(set, slot);
        return sum;
    }

    // ---- CustomModelData ----

    private int base() {
        return plugin.getConfig().getInt("custom-model-data-base", 9000);
    }

    /** CMD ikon item (2D). */
    public int iconCmd(SetDef set, String slot) {
        return base() + set.index() * 4 + SetRegistry.slotIndex(slot);
    }

    /** CMD model 3D untuk ItemDisplay. */
    public int displayCmd(SetDef set, String slot) {
        return iconCmd(set, slot) + 100;
    }

    public ItemStack carrier(SetDef set, String slot) {
        ItemStack item = new ItemStack(CARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(displayCmd(set, slot));
        item.setItemMeta(meta);
        return item;
    }

    // ---- item ----

    public ItemStack create(SetDef set, String slot) {
        Material mat = Material.matchMaterial("LEATHER_" + vanillaSuffix(slot));
        if (mat == null) return null;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String tc = set.tier().color();

        meta.displayName(text(tc + set.pieces().get(slot)));

        double armor = armorOf(set, slot);
        double tough = toughnessOf(set);
        double kb = knockbackOf(set);

        List<Component> lore = new ArrayList<>();
        lore.add(text("&7Set: " + tc + set.display() + " &8(" + set.tier().name() + ")"));
        lore.add(text("&7Armor &f" + trim(armor) + "&7  Toughness &f" + trim(tough)
                + (kb > 0 ? "&7  Knockback Res. &f" + trim(kb) : "")));
        for (Map.Entry<Integer, List<Bonus>> e : set.bonuses().entrySet()) {
            lore.add(Component.empty());
            lore.add(text("&7Set Bonus (" + e.getKey() + "/4):"));
            for (Bonus b : e.getValue()) {
                lore.add(text("&a " + describe(b, set.tier().multiplier())));
            }
        }
        meta.lore(lore);

        // pertahanan sungguhan: atribut ada di item, jadi dihitung oleh sistem armor vanilla.
        // (atribut kustom MENGGANTIKAN nilai bawaan leather, jadi angka di sini yang berlaku)
        EquipmentSlotGroup group = groupOf(slot);
        addAttr(meta, "armor", armor, group, slot);
        if (tough > 0) addAttr(meta, "armor_toughness", tough, group, slot);
        if (kb > 0) addAttr(meta, "knockback_resistance", kb, group, slot);

        if (meta instanceof Damageable dmg) {
            dmg.setMaxDamage(durabilityOf(set, slot));
        }
        if (meta instanceof LeatherArmorMeta leather && set.color() != null) {
            leather.setColor(Color.fromRGB(set.color()));
        }
        meta.addItemFlags(ItemFlag.HIDE_DYE);
        meta.setCustomModelData(iconCmd(set, slot));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(setKey, PersistentDataType.STRING, set.id());
        pdc.set(slotKey, PersistentDataType.STRING, slot);

        item.setItemMeta(meta);
        return item;
    }

    private void addAttr(ItemMeta meta, String attrName, double value, EquipmentSlotGroup group, String slot) {
        Attribute attribute = Attrs.resolve(attrName);
        if (attribute == null) {
            plugin.getLogger().warning("Atribut tidak ditemukan: " + attrName);
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "def_" + slot + "_" + attrName);
        meta.addAttributeModifier(attribute,
                new AttributeModifier(key, value, AttributeModifier.Operation.ADD_NUMBER, group));
    }

    /** @return id set dari item, atau null jika bukan item NereusArmorySets. */
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

    public static String trim(double v) {
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
