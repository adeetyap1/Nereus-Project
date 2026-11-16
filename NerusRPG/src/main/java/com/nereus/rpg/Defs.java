package com.nereus.rpg;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** Definisi data dari file konfigurasi. */
public final class Defs {
    private Defs() {
    }

    /**
     * Satu tingkat RPG Rank (F sampai S). color/secondary = hex "#RRGGBB"; legacy = kode & cadangan (Bedrock/TAB lama).
     * Syarat naik rank (ranks.yml): minLevel, minExp (total EXP seumur hidup), minQuests, storyMissions,
     * questIds, minKills, gp, permission, plus ujian opsional.
     */
    public record Rank(int index, String key, String name, String color, String secondary, String legacy, String rarity,
                       boolean bold, int gp, Objective exam, String desc, int minutes, List<String> commands,
                       boolean broadcast, int minLevel, long minExp, int minQuests, int storyMissions,
                       List<String> questIds, int minKills, String permission) {
        /** Huruf rank berwarna (gradasi jika ada warna kedua). */
        public String tag() {
            String hex = color.startsWith("#") ? color : "#FFFFFF";
            if (secondary != null && !secondary.isEmpty()) {
                return Text.gradient(key, hex, secondary, bold);
            }
            return "&" + hex + (bold ? "&l" : "") + key;
        }

        public String label() {
            String hex = color.startsWith("#") ? color : "#FFFFFF";
            if (secondary != null && !secondary.isEmpty()) {
                return Text.gradient(name, hex, secondary, bold);
            }
            return "&" + hex + (bold ? "&l" : "") + name;
        }

        /** Warna utama saja, untuk awalan teks (hex). */
        public String colorCode() {
            return "&" + (color.startsWith("#") ? color : "#FFFFFF");
        }

        public String legacyTag() {
            return legacy + (bold ? "&l" : "") + key;
        }

        public String legacyLabel() {
            return legacy + (bold ? "&l" : "") + name;
        }
    }

    public record Quest(String id, int rank, boolean challenge, String name, Material icon,
                        Objective obj, int gp, List<String> commands, List<String> lore) {
    }

    public record Mission(String id, String title, List<String> story, int minRank, Objective obj,
                          int xp, int gp, List<String> commands) {
    }

    public record Skill(String type, String name, int level, int mana, double cooldown, double power, String desc) {
    }

    public record Attr(String name, boolean scalar, double amount) {
    }

    public record Clazz(String key, String name, Material icon, List<String> desc, List<Attr> attrs,
                        Map<String, Double> passives, List<String> buffLore, List<Skill> skills) {
        public double passive(String k) {
            return passives.getOrDefault(k, 0.0);
        }
    }
}
