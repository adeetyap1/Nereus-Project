package com.nereus.guild;

import org.bukkit.entity.EntityType;

import java.util.List;

/** Ujian promosi satu rank, dari config.yml. */
public record ExamDef(Type type, EntityType entity, int amount, int minutes, String description, List<String> worlds) {

    public enum Type {
        /** Tanpa ujian: naik langsung kalau GP cukup. */
        NONE,
        /** Bunuh sejumlah mob. */
        KILL,
        /** Diselesaikan dari luar: /guild exam complete. */
        MANUAL
    }
}
