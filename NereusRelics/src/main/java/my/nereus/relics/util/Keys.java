package my.nereus.relics.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Kunci PersistentDataContainer plugin ini. */
public final class Keys {

    public final NamespacedKey entityAltar;
    public final NamespacedKey entityRole;
    public final NamespacedKey offerThrower;
    public final NamespacedKey weaponAltar;
    public final NamespacedKey weaponSerial;

    public Keys(Plugin plugin) {
        entityAltar = new NamespacedKey(plugin, "altar_id");
        entityRole = new NamespacedKey(plugin, "role");
        offerThrower = new NamespacedKey(plugin, "offer_thrower");
        weaponAltar = new NamespacedKey(plugin, "relic_altar");
        weaponSerial = new NamespacedKey(plugin, "relic_serial");
    }
}
