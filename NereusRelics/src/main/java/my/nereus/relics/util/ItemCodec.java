package my.nereus.relics.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/** Serialisasi ItemStack ke teks (aman untuk custom item plugin lain: NBT/PDC ikut tersimpan). */
public final class ItemCodec {

    private ItemCodec() {}

    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String b64) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
    }
}
