package me.naook1.nereuscosmetics;

public record Cosmetic(String id, String name, Type type, int cmd, String permission, String desc) {

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
