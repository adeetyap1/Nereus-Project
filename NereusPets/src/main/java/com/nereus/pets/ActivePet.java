package com.nereus.pets;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.UUID;

/** Pet yang sedang dipanggil ke dunia. */
public final class ActivePet {
    public final UUID owner;
    public final PetData data;
    public final Mob entity;

    public LivingEntity target;
    public long nextAttack;
    public long nextSkill;
    public long nextRegen;
    public long nextScan;
    public long lastHurt;

    public ActivePet(UUID owner, PetData data, Mob entity) {
        this.owner = owner;
        this.data = data;
        this.entity = entity;
    }
}
