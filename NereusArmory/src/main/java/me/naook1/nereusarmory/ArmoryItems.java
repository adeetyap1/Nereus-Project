package me.naook1.nereusarmory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** All Armory items. Java and Bedrock look comes from custom_model_data (resource pack). */
public final class ArmoryItems {

    public record Def(String id, Material material, int cmd, String name, boolean weapon, List<String> lore) {
    }

    private final NereusArmory plugin;
    private final NamespacedKey idKey;
    private final Map<String, Def> defs = new LinkedHashMap<>();

    public ArmoryItems(NereusArmory plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item");
        add("tidecaller", Material.NETHERITE_SWORD, 9001, "&#2fd0c8&lTidecaller", true,
                "&7Trident of the deep tide.", "",
                "&b&lSkill: Tidal Surge &8(right click)",
                "&7Sends a crushing wave forward that",
                "&7damages, throws back and slows enemies.",
                "&7Passive: &f+25% damage &7while wet or in rain.");
        add("frostpearl", Material.NETHERITE_SWORD, 9002, "&#9fe8ff&lFrostpearl Reaver", true,
                "&7A blade carved from a frozen pearl.", "",
                "&b&lSkill: Frost Nova &8(right click)",
                "&7Bursts a freezing shockwave around you.",
                "&7Damages and freezes everything nearby.",
                "&7Passive: &fhits chill the target.");
        add("stormpiercer", Material.NETHERITE_SWORD, 9003, "&#ffd23f&lStormpiercer", true,
                "&7A spear that drinks the storm.", "",
                "&e&lSkill: Thunder Judgment &8(right click)",
                "&7Calls lightning on your target and",
                "&7chains it to enemies nearby.",
                "&7Passive: &f15% chance of a shock hit.");
        add("helm", Material.CHAINMAIL_HELMET, 9011, "&#2fd0c8&lAbyssal Crown", false,
                "&7Part of the &bAbyssal Throne &7set.");
        add("chest", Material.CHAINMAIL_CHESTPLATE, 9012, "&#2fd0c8&lAbyssal Cuirass", false,
                "&7Part of the &bAbyssal Throne &7set.");
        add("legs", Material.CHAINMAIL_LEGGINGS, 9013, "&#2fd0c8&lAbyssal Greaves", false,
                "&7Part of the &bAbyssal Throne &7set.");
        add("boots", Material.CHAINMAIL_BOOTS, 9014, "&#2fd0c8&lAbyssal Striders", false,
                "&7Part of the &bAbyssal Throne &7set.");
    }

    private void add(String id, Material m, int cmd, String name, boolean weapon, String... lore) {
        defs.put(id, new Def(id, m, cmd, name, weapon, List.of(lore)));
    }

    public Map<String, Def> all() {
        return defs;
    }

    public Def def(String id) {
        return defs.get(id);
    }

    public ItemStack create(String id) {
        Def d = defs.get(id);
        if (d == null) {
            return null;
        }
        FileConfiguration c = plugin.getConfig();
        ItemStack it = new ItemStack(d.material());
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(d.cmd());
        meta.displayName(Text.item(d.name()));
        List<Component> lore = new ArrayList<>();
        for (String l : d.lore()) {
            lore.add(Text.item(l));
        }
        if (!d.weapon()) {
            lore.add(Component.empty());
            lore.add(Text.item("&6Full set bonus:"));
            lore.add(Text.item("&72 pcs &fWater Breathing"));
            lore.add(Text.item("&73 pcs &fDolphin's Grace"));
            lore.add(Text.item("&74 pcs &fConduit Power, regen in water,"));
            lore.add(Text.item("&7      water aura + &b&lTSUNAMI &7(sneak twice)"));
        }
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        if (d.weapon()) {
            double dmg = c.getDouble("weapons." + id + ".damage", 8.0);
            double spd = c.getDouble("weapons." + id + ".speed", 1.6);
            mod(meta, Attribute.GENERIC_ATTACK_DAMAGE, "dmg", dmg - 1.0, EquipmentSlotGroup.MAINHAND);
            mod(meta, Attribute.GENERIC_ATTACK_SPEED, "spd", spd - 4.0, EquipmentSlotGroup.MAINHAND);
        } else {
            EquipmentSlotGroup g = switch (id) {
                case "helm" -> EquipmentSlotGroup.HEAD;
                case "chest" -> EquipmentSlotGroup.CHEST;
                case "legs" -> EquipmentSlotGroup.LEGS;
                default -> EquipmentSlotGroup.FEET;
            };
            String p = "armor." + id + ".";
            mod(meta, Attribute.GENERIC_ARMOR, "armor", c.getDouble(p + "armor", 3), g);
            mod(meta, Attribute.GENERIC_ARMOR_TOUGHNESS, "tough", c.getDouble(p + "toughness", 3), g);
            mod(meta, Attribute.GENERIC_KNOCKBACK_RESISTANCE, "kb", c.getDouble(p + "knockback", 0.05), g);
            mod(meta, Attribute.GENERIC_MAX_HEALTH, "hp", c.getDouble(p + "health", 2), g);
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private void mod(ItemMeta meta, Attribute a, String key, double amount, EquipmentSlotGroup g) {
        if (amount == 0) {
            return;
        }
        meta.addAttributeModifier(a, new AttributeModifier(new NamespacedKey(plugin, key), amount,
                AttributeModifier.Operation.ADD_NUMBER, g));
    }

    /** Armory id of an item, or null. */
    public String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }
}
