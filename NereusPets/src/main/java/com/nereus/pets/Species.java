package com.nereus.pets;

import org.bukkit.entity.EntityType;

/** Definisi satu jenis pet dari config.yml. */
public record Species(
        EntityType type,
        String name,
        Rarity rarity,
        double hp,
        double attack,
        double hpPerLevel,
        double attackPerLevel,
        double captureRate,
        double range,
        double scale,
        Skill skill,
        int skillLevel) {
}
