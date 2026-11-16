package com.nereus.rpg;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Jembatan ke ItemsAdder lewat refleksi (tidak perlu dependensi saat kompilasi).
 * Semua metode aman: jika ItemsAdder tidak ada atau gagal, hasilnya null/false dan
 * pemanggil memakai tampilan biasa.
 */
public final class IA {
    private static final Map<String, String> CACHE = new HashMap<>();
    private static final String FIW = "dev.lone.itemsadder.api.FontImages.FontImageWrapper";
    private static final String TIW = "dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper";

    private IA() {
    }

    public static boolean present() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    /** Pemain Bedrock (Floodgate) tidak melihat font resource pack Java. */
    public static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || p.getName().startsWith(".");
    }

    /** Karakter font image, mis. "nereus_ui:hud_7_4". Null jika tidak ada. */
    public static String image(String id) {
        String cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        try {
            Object w = Class.forName(FIW).getConstructor(String.class).newInstance(id);
            Object s = w.getClass().getMethod("getString").invoke(w);
            if (s instanceof String str && !str.isEmpty()) {
                CACHE.put(id, str);
                return str;
            }
        } catch (Throwable ignored) {
            // ItemsAdder belum siap atau gambar tidak ada
        }
        return null;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Buat TexturedInventoryWrapper. Mengembalikan null bila gagal. */
    public static Object textured(InventoryHolder holder, int size, String imageId, int titleOffset, int textureOffset) {
        try {
            Class<?> fiw = Class.forName(FIW);
            Class<?> tiw = Class.forName(TIW);
            Object img = fiw.getConstructor(String.class).newInstance(imageId);
            Constructor<?> c = tiw.getConstructor(InventoryHolder.class, int.class, String.class, fiw, int.class, int.class);
            return c.newInstance(holder, size, " ", img, titleOffset, textureOffset);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Inventory internal(Object wrapper) {
        try {
            return (Inventory) wrapper.getClass().getMethod("getInternal").invoke(wrapper);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean show(Object wrapper, Player p) {
        try {
            wrapper.getClass().getMethod("showInventory", Player.class).invoke(wrapper, p);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
