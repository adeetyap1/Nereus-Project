package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.Bonus;
import me.naook1.nereusarmorysets.Model.SetDef;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Menghitung piece yang dipakai dan memasang/mencabut modifier atribut. */
public final class BonusManager {
    private final NereusArmorySets plugin;
    /** Signature terakhir per pemain, supaya modifier tidak dipasang ulang tiap tick. */
    private final Map<UUID, String> lastSignature = new HashMap<>();

    public BonusManager(NereusArmorySets plugin) {
        this.plugin = plugin;
    }

    public void refreshAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            refresh(p);
        }
    }

    public void forget(UUID id) {
        lastSignature.remove(id);
    }

    public void clearCache() {
        lastSignature.clear();
    }

    /** Cek armor pemain, pasang bonus jika berubah. */
    public void refresh(Player p) {
        Map<String, Integer> counts = countPieces(p);
        String signature = signature(counts);
        String previous = lastSignature.get(p.getUniqueId());
        if (signature.equals(previous)) {
            return;
        }
        lastSignature.put(p.getUniqueId(), signature);

        removeAll(p);
        List<String> summary = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            SetDef set = plugin.registry().get(e.getKey());
            if (set == null) continue;
            apply(p, set, e.getValue());
            if (e.getValue() >= 2) {
                summary.add(set.tier().color() + set.display() + " &7(" + e.getValue() + "/4)");
            }
        }

        if (plugin.getConfig().getBoolean("actionbar-on-change", true)
                && !(previous == null && summary.isEmpty())) {
            if (summary.isEmpty()) {
                p.sendActionBar(ItemFactory.text("&7Set bonus nonaktif"));
            } else {
                p.sendActionBar(ItemFactory.text("&aSet Bonus: " + String.join("&7, ", summary)));
            }
        }
    }

    /** Hapus semua modifier milik plugin ini dari pemain. */
    public void removeAll(Player p) {
        String ns = plugin.keyNamespace();
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance inst = p.getAttribute(attribute);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                // hanya modifier bonus set; atribut pertahanan (def_*) milik item tidak boleh disentuh
                if (m.getKey().getNamespace().equals(ns) && m.getKey().getKey().startsWith("bonus_")) {
                    inst.removeModifier(m);
                }
            }
        }
    }

    private Map<String, Integer> countPieces(Player p) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            String id = plugin.items().setIdOf(piece);
            if (id != null) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String signature(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    private void apply(Player p, SetDef set, int count) {
        double mult = set.tier().multiplier();
        for (Map.Entry<Integer, List<Bonus>> e : set.bonuses().entrySet()) {
            if (count < e.getKey()) continue;
            int index = 0;
            for (Bonus b : e.getValue()) {
                Attribute attribute = Attrs.resolve(b.attribute());
                if (attribute == null) {
                    plugin.getLogger().warning("Atribut tidak dikenal di set '" + set.id() + "': " + b.attribute());
                    continue;
                }
                AttributeInstance inst = p.getAttribute(attribute);
                if (inst == null) continue;
                NamespacedKey key = new NamespacedKey(plugin, "bonus_" + set.id() + "_" + e.getKey() + "_" + index++);
                inst.addTransientModifier(new AttributeModifier(
                        key, b.amount() * mult, b.operation(), EquipmentSlotGroup.ANY));
            }
        }
    }
}
