package com.nereus.pets;

import org.bukkit.entity.EntityType;

import java.util.List;

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
        int skillLevel,
        AttackStyle style,
        /** true = AI mob dimatikan dan digerakkan plugin (mob terbang atau berbasis brain). */
        boolean controlled,
        boolean flying,
        /** OVERWORLD, NETHER, END, atau ANY: tempat pet liar muncul. */
        String habitat,
        /** Boleh keluar dari telur gacha. */
        boolean egg,
        /** Nama partikel aura (hanya terlihat di pet liar dan pet Legendaris). */
        String aura,
        double wildScale,
        List<String> lore) {
}
