package com.nereus.guild;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/** Membaca GP dari PlayerPoints lewat refleksi, supaya tidak perlu dependensi saat build. */
public final class PointsHook {
    private Object api;
    private Method look;
    private boolean tried;

    private void init() {
        if (tried && look != null) {
            return;
        }
        tried = true;
        Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pp == null || !pp.isEnabled()) {
            return;
        }
        try {
            Object a = pp.getClass().getMethod("getAPI").invoke(pp);
            Method m = a.getClass().getMethod("look", UUID.class);
            api = a;
            look = m;
        } catch (ReflectiveOperationException ex) {
            look = null;
        }
    }

    public boolean available() {
        init();
        return look != null;
    }

    /** GP pemain, atau 0 kalau PlayerPoints tidak ada. */
    public int look(UUID id) {
        init();
        if (look == null) {
            return 0;
        }
        try {
            Object r = look.invoke(api, id);
            return r instanceof Number n ? n.intValue() : 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }
}
