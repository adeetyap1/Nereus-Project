package my.nereus.intro.npc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import my.nereus.intro.config.IntroConfig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Map;
import java.util.UUID;

/**
 * Satu NPC FancyNpcs yang hanya dikirim (packet) ke satu pemain. NPC ini TIDAK didaftarkan ke NpcManager
 * FancyNpcs, jadi tidak tersimpan ke npcs.yml, tidak muncul untuk pemain lain, dan hilang bila server restart.
 */
public final class NerithNpc {

    private final Npc npc;
    private boolean removed;

    private NerithNpc(Npc npc) {
        this.npc = npc;
    }

    public static NerithNpc spawn(Player player, Location where, IntroConfig cfg, SkinData skin) {
        String name = "nereusintro-" + UUID.randomUUID().toString().substring(0, 8);
        NpcData data = new NpcData(name, player.getUniqueId(), where);
        data.setDisplayName(cfg.npcDisplayName);
        data.setShowInTab(false);
        data.setCollidable(false);
        data.setTurnToPlayer(false);
        data.setScale(cfg.npcScale);
        if (skin != null) data.setSkinData(skin);

        if (cfg.outfitEnabled) {
            Map<String, NpcEquipmentSlot> slots = Map.of(
                    "head", NpcEquipmentSlot.HEAD,
                    "chest", NpcEquipmentSlot.CHEST,
                    "legs", NpcEquipmentSlot.LEGS,
                    "feet", NpcEquipmentSlot.FEET);
            for (Map.Entry<String, NpcEquipmentSlot> e : slots.entrySet()) {
                ItemStack item = outfitItem(cfg.outfitSlots.get(e.getKey()), cfg.outfitColor);
                if (item != null) data.addEquipment(e.getValue(), item);
            }
        }

        Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
        npc.setSaveToFile(false);
        NerithNpc handle = new NerithNpc(npc);
        try {
            npc.create();
            npc.spawn(player);
        } catch (Throwable t) {
            handle.remove(player);
            throw t;
        }
        return handle;
    }

    private static ItemStack outfitItem(String material, String hex) {
        if (material == null || material.isBlank()) return null;
        Material m = Material.matchMaterial(material.trim());
        if (m == null || m.isAir()) return null;
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(parseColor(hex));
            item.setItemMeta(leather);
        }
        return item;
    }

    private static Color parseColor(String hex) {
        try {
            String h = hex == null ? "FFFFFF" : hex.trim().replace("#", "");
            return Color.fromRGB(Integer.parseInt(h, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }

    public void lookAt(Player player) {
        if (removed) return;
        try {
            npc.lookAt(player, player.getEyeLocation());
        } catch (Throwable ignored) {
            // kosmetik saja
        }
    }

    public void remove(Player player) {
        if (removed) return;
        removed = true;
        try {
            npc.remove(player);
        } catch (Throwable ignored) {
            // pemain mungkin sudah offline
        }
    }
}
