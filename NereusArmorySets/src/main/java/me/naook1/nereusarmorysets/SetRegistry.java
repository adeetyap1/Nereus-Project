package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.Bonus;
import me.naook1.nereusarmorysets.Model.SetDef;
import me.naook1.nereusarmorysets.Model.Tier;
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

/** Memuat tier dan set dari config.yml. */
public final class SetRegistry {
    public static final String[] SLOTS = {"head", "chest", "legs", "feet"};

    private final Map<Integer, Tier> tiers = new LinkedHashMap<>();
    private final Map<String, SetDef> sets = new LinkedHashMap<>();

    public void load(FileConfiguration cfg, Logger log) {
        tiers.clear();
        sets.clear();

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
                tiers.put(id, new Tier(id, t.getString("name", "Tier " + id),
                        t.getString("color", "&f"), t.getDouble("multiplier", 1.0)));
            }
        }

        ConfigurationSection setSec = cfg.getConfigurationSection("sets");
        if (setSec == null) {
            log.warning("config.yml tidak punya bagian 'sets'.");
            return;
        }
        for (String rawId : setSec.getKeys(false)) {
            ConfigurationSection s = setSec.getConfigurationSection(rawId);
            if (s == null) continue;
            String id = rawId.toLowerCase(Locale.ROOT);

            Tier tier = tiers.get(s.getInt("tier", 1));
            if (tier == null) {
                log.warning("Set '" + id + "' dilewati: tier " + s.getInt("tier", 1) + " tidak ada.");
                continue;
            }

            String material = s.getString("material", "IRON").toUpperCase(Locale.ROOT);
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

            sets.put(id, new SetDef(id, s.getString("display", id), tier, material, color, pieces, bonuses));
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
}
