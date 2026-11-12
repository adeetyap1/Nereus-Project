package com.nereus.pets;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/** Data satu pet milik pemain (disimpan di plugins/NereusPets/data/UUID.yml). */
public final class PetData {
    public final UUID id;
    public final EntityType type;
    public final Rarity rarity;

    public String nickname;
    public int level = 1;
    public double xp;
    /** Bakat acak 0 sampai 15 (persen tambahan HP dan ATK). */
    public int ivHp;
    public int ivAtk;
    /** HP saat stamp. Nilai negatif berarti penuh. */
    public double hp = -1;
    public long stamp = System.currentTimeMillis();
    public long faintUntil;
    /** Bintang hasil fusion, 0 sampai fuse.max-stars. */
    public int stars;
    public int wins;
    public int losses;

    public PetData(UUID id, EntityType type, Rarity rarity) {
        this.id = id;
        this.type = type;
        this.rarity = rarity;
    }
}
