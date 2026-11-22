package my.nereus.relics.offering;

import my.nereus.relics.util.ItemCodec;
import my.nereus.relics.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Satu syarat persembahan. Dua cara identifikasi item (tidak dibatasi item vanilla):
 * <ul>
 *   <li>MATERIAL: item vanilla "polos" (tanpa nama/lore/custom model data/PDC) bertipe material tertentu.</li>
 *   <li>EXACT: item persis seperti template (nama, lore, custom model data, PDC/NBT), cocok untuk custom item
 *       dari plugin apa pun (MMOItems, ItemsAdder, Nexo, dan lain-lain) karena semuanya ItemStack biasa.</li>
 * </ul>
 */
public final class Requirement {

    public enum Mode { MATERIAL, EXACT }

    private final Mode mode;
    private final Material material;
    private final ItemStack template; // EXACT saja, amount = 1
    private volatile int amount;
    private final String label;

    private Requirement(Mode mode, Material material, ItemStack template, int amount, String label) {
        this.mode = mode;
        this.material = material;
        this.template = template;
        this.amount = Math.max(1, amount);
        this.label = label;
    }

    public static Requirement fromHeld(ItemStack held, int amount, Mode forced) {
        Mode m = forced != null ? forced : (isPlain(held) ? Mode.MATERIAL : Mode.EXACT);
        if (m == Mode.MATERIAL) {
            return new Requirement(Mode.MATERIAL, held.getType(), null, amount, Text.pretty(held.getType()));
        }
        ItemStack t = held.clone();
        t.setAmount(1);
        return new Requirement(Mode.EXACT, t.getType(), t, amount, Text.itemLabel(t));
    }

    public static Requirement restore(Mode mode, Material material, String itemB64, int amount, String label) {
        if (mode == Mode.EXACT && itemB64 != null && !itemB64.isEmpty()) {
            ItemStack t = ItemCodec.decode(itemB64);
            return new Requirement(Mode.EXACT, t.getType(), t, amount, label);
        }
        return new Requirement(Mode.MATERIAL, material, null, amount, label);
    }

    /** Item tanpa atribut khusus. */
    public static boolean isPlain(ItemStack item) {
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && !meta.hasCustomModelData()
                && meta.getPersistentDataContainer().getKeys().isEmpty()
                && !meta.hasEnchants() && !(meta instanceof org.bukkit.inventory.meta.Damageable d && d.hasDamage());
    }

    public boolean matches(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (mode == Mode.MATERIAL) return item.getType() == material && isPlain(item);
        return template.isSimilar(item);
    }

    /** Item yang dikembalikan ke pemain pada refund. */
    public ItemStack newStack(int n) {
        ItemStack s = mode == Mode.EXACT ? template.clone() : new ItemStack(material);
        s.setAmount(Math.max(1, n));
        return s;
    }

    public Mode mode() { return mode; }
    public Material material() { return material; }
    public ItemStack template() { return template == null ? null : template.clone(); }
    public int amount() { return amount; }
    public void amount(int a) { this.amount = Math.max(1, a); }
    public String label() { return label; }

    public String itemB64() {
        return template == null ? "" : ItemCodec.encode(template);
    }
}
