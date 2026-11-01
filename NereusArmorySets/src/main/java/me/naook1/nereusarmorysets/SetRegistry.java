package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.Bonus;
import me.naook1.nereusarmorysets.Model.ClassDef;
import me.naook1.nereusarmorysets.Model.SetDef;
import me.naook1.nereusarmorysets.Model.Tier;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Memuat tier, kelas pertahanan, dan set dari config.yml. */
public final class SetRegistry {
    public static final String[] SLOTS = {"head", "chest", "legs", "feet"};

    private final Map<Integer, Tier> tiers = new LinkedHashMap<>();
    private final Map<String, ClassDef> classes = new LinkedHashMap<>();
    private final Map<String, SetDef> sets = new LinkedHashMap<>();
    private double[] durabilityBase = {11, 16, 15, 13};

    public static int slotIndex(String slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i].equals(slot)) return i;
        }
        return -1;
    }

    public void load(FileConfiguration cfg, Logger log) {
        tiers.clear();
        classes.clear();
        sets.clear();

        // pertahanan
        List<Double> dur = cfg.getDoubleList("defense.durability-base");
        if (dur.size() == 4) {
            durabilityBase = new double[] {dur.get(0), dur.get(1), dur.get(2), dur.get(3)};
        }
        ConfigurationSection cs = cfg.getConfigurationSection("defense.classes");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(key);
                if (c == null) continue;
                List<Double> a = c.getDoubleList("armor");
                if (a.size() != 4) {
                    log.warning("Kelas '" + key + "' dilewati: armor harus 4 angka [head, chest, legs, feet].");
                    continue;
                }
                classes.put(key.toLowerCase(Locale.ROOT), new ClassDef(key.toLowerCase(Locale.ROOT),
                        new double[] {a.get(0), a.get(1), a.get(2), a.get(3)},
                        c.getDouble("toughness", 0.0), c.getDouble("knockback", 0.0)));
            }
        }

        // tier
        ConfigurationSection tierSec = cfg.getConfigurationSection("tiers");
        if (tierSec != null) {
            for (String key : tierSec.getKeys(false)) {
                ConfigurationSection t = tierSec.getConfigurationSection(key);
                if (t == null) continue;
                int id;
                try {
                    id = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    log.warning("Tier '" + key + "' dilewati: id harus angka.");
                    continue;
                }
                Material icon = Material.matchMaterial(t.getString("icon", "IRON_CHESTPLATE"));
                if (icon == null) icon = Material.IRON_CHESTPLATE;
                tiers.put(id, new Tier(id, t.getString("name", "Tier " + id), t.getString("color", "&f"), icon,
                        t.getDouble("multiplier", 1.0), t.getDouble("armor-multiplier", 1.0),
                        t.getDouble("toughness-bonus", 0.0), t.getDouble("durability-multiplier", 40.0)));
            }
        }

        // set
        ConfigurationSection setSec = cfg.getConfigurationSection("sets");
        if (setSec == null) {
            log.warning("config.yml tidak punya bagian 'sets'.");
            return;
        }
        int index = 0;
        for (String rawId : setSec.getKeys(false)) {
            ConfigurationSection s = setSec.getConfigurationSection(rawId);
            if (s == null) continue;
            String id = rawId.toLowerCase(Locale.ROOT);

            Tier tier = tiers.get(s.getInt("tier", 1));
            if (tier == null) {
                log.warning("Set '" + id + "' dilewati: tier " + s.getInt("tier", 1) + " tidak ada.");
                continue;
            }
            String cls = s.getString("class", "heavy").toLowerCase(Locale.ROOT);
            if (!classes.containsKey(cls)) {
                log.warning("Set '" + id + "' dilewati: kelas '" + cls + "' tidak ada di defense.classes.");
                continue;
            }

            Integer color = null;
            String colorStr = s.getString("color");
            if (colorStr != null) {
                try {
                    color = Integer.parseInt(colorStr.replace("#", ""), 16);
                } catch (NumberFormatException e) {
                    log.warning("Set '" + id + "': warna '" + colorStr + "' tidak valid.");
                }
            }

            Map<String, String> pieces = new LinkedHashMap<>();
            ConfigurationSection pSec = s.getConfigurationSection("pieces");
            for (String slot : SLOTS) {
                pieces.put(slot, pSec != null ? pSec.getString(slot, id + " " + slot) : id + " " + slot);
            }

            Map<Integer, List<Bonus>> bonuses = new TreeMap<>();
            ConfigurationSection bSec = s.getConfigurationSection("bonuses");
            if (bSec != null) {
                for (String countKey : bSec.getKeys(false)) {
                    int count;
                    try {
                        count = Integer.parseInt(countKey);
                    } catch (NumberFormatException e) {
                        log.warning("Set '" + id + "': jumlah piece '" + countKey + "' tidak valid.");
                        continue;
                    }
                    List<Bonus> list = new ArrayList<>();
                    for (Map<?, ?> m : bSec.getMapList(countKey)) {
                        Object attr = m.get("attribute");
                        Object op = m.get("operation");
                        Object amt = m.get("amount");
                        if (attr == null || !(amt instanceof Number)) {
                            log.warning("Set '" + id + "': bonus tidak lengkap, dilewati.");
                            continue;
                        }
                        AttributeModifier.Operation operation = parseOp(op == null ? "add" : op.toString());
                        if (operation == null) {
                            log.warning("Set '" + id + "': operation '" + op + "' tidak dikenal.");
                            continue;
                        }
                        list.add(new Bonus(attr.toString().toLowerCase(Locale.ROOT), operation,
                                ((Number) amt).doubleValue()));
                    }
                    bonuses.put(count, list);
                }
            }

            sets.put(id, new SetDef(id, index++, s.getString("display", id), tier, cls, color, pieces, bonuses));
        }
        log.info("Dimuat " + sets.size() + " set dalam " + tiers.size() + " tier.");
    }

    public static AttributeModifier.Operation parseOp(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "add", "add_number" -> AttributeModifier.Operation.ADD_NUMBER;
            case "multiply_base", "add_scalar" -> AttributeModifier.Operation.ADD_SCALAR;
            case "multiply_total", "multiply_scalar_1" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> null;
        };
    }

    public SetDef get(String id) {
        return id == null ? null : sets.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, SetDef> sets() {
        return sets;
    }

    public Map<Integer, Tier> tiers() {
        return tiers;
    }

    public ClassDef classOf(SetDef set) {
        return classes.get(set.armorClass());
    }

    public double durabilityBase(int slotIndex) {
        return durabilityBase[slotIndex];
    }

    public List<SetDef> setsOfTier(int tierId) {
        List<SetDef> out = new ArrayList<>();
        for (SetDef s : sets.values()) {
            if (s.tier().id() == tierId) out.add(s);
        }
        return out;
    }
}
