package me.naook1.nereusarmorysets;

import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;

import java.util.List;
import java.util.Map;

/** Data model yang dibaca dari config.yml. */
public final class Model {
    private Model() {}

    public record Tier(
            int id,
            String name,
            String color,
            Material icon,
            double multiplier,            // pengali set bonus
            double armorMultiplier,       // pengali armor per piece
            double toughnessBonus,        // tambahan toughness per piece
            double durabilityMultiplier   // durability = base x ini
    ) {}

    /** Nilai pertahanan dasar satu kelas (robe / light / heavy). armor = [head, chest, legs, feet]. */
    public record ClassDef(String id, double[] armor, double toughness, double knockback) {}

    public record Bonus(String attribute, AttributeModifier.Operation operation, double amount) {}

    public record SetDef(
            String id,
            int index,                           // urutan di config, dipakai untuk CustomModelData
            String display,
            Tier tier,
            String armorClass,
            Integer color,                       // warna leather (RGB) atau null
            Map<String, String> pieces,          // head/chest/legs/feet -> nama item
            Map<Integer, List<Bonus>> bonuses    // jumlah piece -> bonus (nilai dasar, belum x multiplier)
    ) {}
}
