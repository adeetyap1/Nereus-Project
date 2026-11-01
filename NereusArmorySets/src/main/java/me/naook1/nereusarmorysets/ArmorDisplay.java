package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.SetDef;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Model 3D armor untuk pemain Java: tiap piece set yang dipakai dimunculkan sebagai ItemDisplay yang
 * menunggangi pemain (ikut bergerak tanpa lag). Pemain Bedrock tidak melihat display ini; mereka melihat
 * armor lewat attachable di resource pack Bedrock.
 *
 * Catatan: model bersifat statis (tidak ikut ayunan tangan/kaki) karena itu batas dari ItemDisplay.
 */
public final class ArmorDisplay {
    /** Skala vanilla untuk model humanoid. */
    private static final float VANILLA_SCALE = 0.9375f;
    /** 1 satuan model geo = 0.5 satuan model Java, jadi display dikali 2 untuk kembali ke ukuran asli. */
    private static final float MODEL_UNIT = 2.0f;

    private final NereusArmorySets plugin;
    private final Map<UUID, Map<String, ItemDisplay>> displays = new HashMap<>();
    private final Map<UUID, Map<String, Float>> lastYaw = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> shownCmd = new HashMap<>();
    private int counter;

    public ArmorDisplay(NereusArmorySets plugin) {
        this.plugin = plugin;
    }

    private static boolean isBedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    /** Dipanggil berkala oleh scheduler. */
    public void tick() {
        if (!plugin.getConfig().getBoolean("display.enabled", true)) {
            clearAll();
            return;
        }
        counter++;
        boolean recheckVisibility = counter % 10 == 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p, recheckVisibility);
        }
        // bersihkan data pemain yang sudah offline
        displays.keySet().removeIf(id -> {
            if (Bukkit.getPlayer(id) == null) {
                removeAllOf(id);
                return true;
            }
            return false;
        });
    }

    private void update(Player p, boolean recheckVisibility) {
        boolean hidden = p.isDead() || p.isInvisible() || p.isSwimming() || p.isGliding()
                || p.isSleeping() || p.getGameMode() == GameMode.SPECTATOR;

        SetDef[] worn = new SetDef[4];
        ItemStack[] armor = p.getInventory().getArmorContents(); // [boots, legs, chest, head]
        String[] byIndex = {"feet", "legs", "chest", "head"};
        for (int i = 0; i < 4; i++) {
            SetDef set = plugin.registry().get(plugin.items().setIdOf(armor[i]));
            worn[SetRegistry.slotIndex(byIndex[i])] = set;
        }

        Map<String, ItemDisplay> mine = displays.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        for (int i = 0; i < SetRegistry.SLOTS.length; i++) {
            String slot = SetRegistry.SLOTS[i];
            SetDef set = worn[i];
            if (set == null || hidden) {
                removeOne(p.getUniqueId(), slot);
                continue;
            }
            ensure(p, slot, set, mine, recheckVisibility);
        }
    }

    private void ensure(Player p, String slot, SetDef set, Map<String, ItemDisplay> mine, boolean recheckVisibility) {
        UUID id = p.getUniqueId();
        ItemDisplay d = mine.get(slot);
        boolean fresh = false;

        if (d == null || !d.isValid() || d.getWorld() != p.getWorld()) {
            if (d != null) d.remove();
            Location at = p.getLocation().clone();
            at.setYaw(0f);
            at.setPitch(0f);
            final float range = (float) plugin.getConfig().getDouble("display.view-range", 1.5);
            d = p.getWorld().spawn(at, ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTeleportDuration(0);
                e.setInterpolationDelay(0);
                e.setInterpolationDuration(2);
                e.setViewRange(range);
            });
            mine.put(slot, d);
            lastYaw.computeIfAbsent(id, k -> new HashMap<>()).put(slot, Float.NaN);
            shownCmd.computeIfAbsent(id, k -> new HashMap<>()).remove(slot);
            fresh = true;
        }

        int cmd = plugin.items().displayCmd(set, slot);
        Map<String, Integer> cmds = shownCmd.computeIfAbsent(id, k -> new HashMap<>());
        Integer shown = cmds.get(slot);
        if (shown == null || shown != cmd) {
            d.setItemStack(plugin.items().carrier(set, slot));
            cmds.put(slot, cmd);
        }

        // display menunggangi pemain: klien menggerakkannya bersama badan
        if (d.getVehicle() != p) {
            d.leaveVehicle();
            p.addPassenger(d);
            fresh = true;
        }

        float yaw = p.getBodyYaw();
        Float last = lastYaw.computeIfAbsent(id, k -> new HashMap<>()).get(slot);
        float delta = last == null || last.isNaN() ? 999f : Math.abs(((yaw - last + 540f) % 360f) - 180f);
        if (fresh || delta > 1.0f) {
            lastYaw.get(id).put(slot, yaw);
            float offsetY = (float) plugin.getConfig().getDouble("display.offset-y", 0.0);
            float scale = MODEL_UNIT * VANILLA_SCALE * (float) plugin.getConfig().getDouble("display.scale", 1.0);
            // penumpang duduk di y pemain + 1.8; model geo berpusat di pinggang (y geo = 16 -> 0.9375 blok)
            float ty = VANILLA_SCALE - 1.8f + offsetY;
            // sisi depan model (-Z) harus menghadap arah pandang badan
            float rad = (float) (Math.PI - Math.toRadians(yaw));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(fresh ? 0 : 2);
            d.setTransformation(new Transformation(new Vector3f(0f, ty, 0f),
                    new AxisAngle4f(rad, 0, 1, 0), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        }

        if (fresh || recheckVisibility) {
            boolean showOwn = plugin.getConfig().getBoolean("display.show-own", true);
            for (Player v : Bukkit.getOnlinePlayers()) {
                boolean hide = isBedrock(v) || (v.equals(p) && !showOwn);
                if (hide) {
                    v.hideEntity(plugin, d);
                } else {
                    v.showEntity(plugin, d);
                }
            }
        }
    }

    private void removeOne(UUID id, String slot) {
        Map<String, ItemDisplay> mine = displays.get(id);
        if (mine == null) return;
        ItemDisplay d = mine.remove(slot);
        if (d != null) d.remove();
        Map<String, Float> y = lastYaw.get(id);
        if (y != null) y.remove(slot);
        Map<String, Integer> c = shownCmd.get(id);
        if (c != null) c.remove(slot);
    }

    private void removeAllOf(UUID id) {
        Map<String, ItemDisplay> mine = displays.get(id);
        if (mine != null) {
            mine.values().forEach(ItemDisplay::remove);
            mine.clear();
        }
        lastYaw.remove(id);
        shownCmd.remove(id);
    }

    public void forget(UUID id) {
        removeAllOf(id);
        displays.remove(id);
    }

    public void clearAll() {
        for (UUID id : new java.util.ArrayList<>(displays.keySet())) {
            removeAllOf(id);
        }
        displays.clear();
        lastYaw.clear();
        shownCmd.clear();
    }
}
