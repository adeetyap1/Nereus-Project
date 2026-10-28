package me.naook1.nereuscosmetics;

public record Cosmetic(String id, String name, Type type, int cmd, String permission, String desc, org.bukkit.Material mat) {

    public Cosmetic(String id, String name, Type type, int cmd, String permission, String desc) {
        this(id, name, type, cmd, permission, desc, org.bukkit.Material.COAL);
    }

    public enum Type {
        HAT, BACK, HAND;

        public static Type of(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
