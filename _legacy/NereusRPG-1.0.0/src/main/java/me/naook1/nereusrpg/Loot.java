package me.naook1.nereusrpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Random gear with rarity and bonus stats, stored in the item itself (no textures needed). */
public final class Loot {

    public enum Rarity {
        COMMON("§f", "Common", 1.0, 1),
        UNCOMMON("§a", "Uncommon", 1.5, 2),
        RARE("§9", "Rare", 2.2, 2),
        EPIC("§5", "Epic", 3.2, 3),
        LEGENDARY("§6", "Legendary", 4.5, 4);

        final String color;
        final String label;
        final double mult;
        final int affixes;

        Rarity(String color, String label, double mult, int affixes) {
            this.color = color;
            this.label = label;
            this.mult = mult;
            this.affixes = affixes;
        }
    }

    enum Affix {
        DMG("dmg", "Damage", "%", 3.0),
        HP("hp", "HP", "", 2.0),
        MANA("mana", "Mana", "", 8.0),
        CRIT("crit", "Crit", "%", 2.0),
        LUCK("luck", "Luck", "%", 2.0),
        DEF("def", "Defense", "%", 2.0),
        LIFESTEAL("ls", "Lifesteal", "%", 1.0);

        final String key;
        final String label;
        final String unit;
        final double base;

        Affix(String key, String label, String unit, double base) {
            this.key = key;
            this.label = label;
            this.unit = unit;
            this.base = base;
        }
    }

    private static final Affix[] WEAPON = {Affix.DMG, Affix.CRIT, Affix.LIFESTEAL, Affix.MANA, Affix.LUCK};
    private static final Affix[] ARMOR = {Affix.HP, Affix.DEF, Affix.MANA, Affix.LUCK, Affix.DMG};

    private static final String[][] WEAPONS = {
            {"WOODEN_SWORD", "STONE_SWORD", "BOW", "WOODEN_AXE"},
            {"IRON_SWORD", "IRON_AXE", "BOW", "CROSSBOW"},
            {"DIAMOND_SWORD", "DIAMOND_AXE", "CROSSBOW", "BOW"},
            {"NETHERITE_SWORD", "NETHERITE_AXE", "CROSSBOW", "TRIDENT"}};
    private static final String[][] ARMORS = {
            {"LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"},
            {"IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS"},
            {"DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS"},
            {"NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS"}};
    private static final String[] PREFIX = {"Savage", "Swift", "Arcane", "Ancient", "Abyssal", "Gilded", "Stormbound", "Radiant", "Feral", "Mystic"};

    private final NamespacedKey statsKey;
    private final NamespacedKey reqKey;
    private final Random random = new Random();

    public Loot(NereusRPG plugin) {
        this.statsKey = new NamespacedKey(plugin, "gear");
        this.reqKey = new NamespacedKey(plugin, "req");
    }

    public static int tierOf(double maxHealth) {
        return maxHealth < 15 ? 0 : maxHealth < 40 ? 1 : maxHealth < 100 ? 2 : 3;
    }

    public Rarity rollRarity(ConfigurationSection weights, double luck) {
        double[] w = new double[Rarity.values().length];
        double total = 0;
        for (Rarity r : Rarity.values()) {
            double base = weights == null ? 1 : weights.getDouble(r.name(), 1);
            // luck makes rarer tiers more likely (and common less)
            double shift = 1 + luck * 0.02 * r.ordinal() - luck * 0.004 * (r == Rarity.COMMON ? 1 : 0);
            w[r.ordinal()] = Math.max(0.01, base * shift);
            total += w[r.ordinal()];
        }
        double pick = random.nextDouble() * total;
        for (Rarity r : Rarity.values()) {
            pick -= w[r.ordinal()];
            if (pick <= 0) {
                return r;
            }
        }
        return Rarity.COMMON;
    }

    public ItemStack generate(int tier, Rarity rarity) {
        tier = Math.max(0, Math.min(3, tier));
        boolean weapon = random.nextInt(100) < 45;
        String[] pool = weapon ? WEAPONS[tier] : ARMORS[tier];
        Material mat = Material.valueOf(pool[random.nextInt(pool.length)]);
        Affix[] affixPool = weapon ? WEAPON : ARMOR;
        List<Affix> picks = new ArrayList<>(List.of(affixPool));
        java.util.Collections.shuffle(picks, random);
        double tierMult = 1 + tier * 0.5;
        StringBuilder data = new StringBuilder();
        List<String> lore = new ArrayList<>();
        lore.add(rarity.color + "§l" + rarity.label.toUpperCase());
        Affix first = null;
        for (int i = 0; i < Math.min(rarity.affixes, picks.size()); i++) {
            Affix a = picks.get(i);
            if (first == null) {
                first = a;
            }
            double v = a.base * rarity.mult * tierMult * (0.75 + random.nextDouble() * 0.5);
            v = Math.round(v * 10.0) / 10.0;
            data.append(a.key).append('=').append(v).append(';');
            lore.add("§a+" + fmt(v) + a.unit + " " + a.label);
        }
        int req = Math.max(1, tier * 15 + rarity.ordinal() * 3);
        lore.add("§7Butuh Level §e" + req);
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        String prefix = first == null ? PREFIX[random.nextInt(PREFIX.length)] : switch (first) {
            case DMG -> "Savage";
            case CRIT -> "Deadly";
            case LIFESTEAL -> "Vampiric";
            case MANA -> "Arcane";
            case LUCK -> "Fortunate";
            case HP -> "Sturdy";
            case DEF -> "Warded";
        };
        meta.setDisplayName(rarity.color + prefix + " " + nice(mat));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(statsKey, PersistentDataType.STRING, data.toString());
        meta.getPersistentDataContainer().set(reqKey, PersistentDataType.INTEGER, req);
        it.setItemMeta(meta);
        return it;
    }

    private static String nice(Material m) {
        String[] p = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : p) {
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Bonus stats of an item (zero if it is not RPG gear, or the player's level is too low). */
    public PlayerData.Gear read(ItemStack it, int level) {
        if (it == null || !it.hasItemMeta()) {
            return PlayerData.Gear.NONE;
        }
        var pdc = it.getItemMeta().getPersistentDataContainer();
        String s = pdc.get(statsKey, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) {
            return PlayerData.Gear.NONE;
        }
        Integer req = pdc.get(reqKey, PersistentDataType.INTEGER);
        if (req != null && level < req) {
            return PlayerData.Gear.NONE;
        }
        double dmg = 0, hp = 0, mana = 0, crit = 0, luck = 0, def = 0, ls = 0;
        for (String part : s.split(";")) {
            String[] kv = part.split("=");
            if (kv.length != 2) {
                continue;
            }
            double v;
            try {
                v = Double.parseDouble(kv[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            switch (kv[0]) {
                case "dmg" -> dmg += v;
                case "hp" -> hp += v;
                case "mana" -> mana += v;
                case "crit" -> crit += v;
                case "luck" -> luck += v;
                case "def" -> def += v;
                case "ls" -> ls += v;
                default -> {
                }
            }
        }
        return new PlayerData.Gear(dmg, hp, mana, crit, luck, def, ls);
    }

}
