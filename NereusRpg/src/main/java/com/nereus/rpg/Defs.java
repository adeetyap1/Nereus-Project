package com.nereus.rpg;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** Definisi data dari file konfigurasi. */
public final class Defs {
    private Defs() {
    }

    public record Rank(int index, String key, String name, String color, int gp, Objective exam,
                       String desc, int minutes, List<String> commands, boolean broadcast) {
        public String label() {
            return color + name;
        }

        public String tag() {
            return color + key;
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
