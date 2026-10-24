package me.naook1.nereuscosmetics;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

public record Cosmetic(String id, String name, Type type, String effect, Particle particle, Color c1, Color c2, float size,
                       Material icon, String permission, String desc) {

    public enum Type {
        HAT, BACK, AURA, TRAIL;

        public static Type of(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
