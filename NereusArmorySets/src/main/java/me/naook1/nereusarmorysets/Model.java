package me.naook1.nereusarmorysets;

import org.bukkit.attribute.AttributeModifier;

import java.util.List;
import java.util.Map;

/** Data model yang dibaca dari config.yml. */
public final class Model {
    private Model() {}

    public record Tier(int id, String name, String color, double multiplier) {}

    public record Bonus(String attribute, AttributeModifier.Operation operation, double amount) {}

    public record SetDef(
            String id,
            String display,
            Tier tier,
            String material,
            Integer leatherColor,
            Map<String, String> pieces,          // head/chest/legs/feet -> nama item
            Map<Integer, List<Bonus>> bonuses    // jumlah piece -> bonus (nilai dasar, belum x multiplier)
    ) {}
}
