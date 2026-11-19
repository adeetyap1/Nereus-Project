package my.nereus.intro.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Deteksi pemain Bedrock. Floodgate bersifat opsional dan hanya dimuat bila ada. */
public final class PlatformManager {

    private final boolean floodgate;

    public PlatformManager() {
        this.floodgate = Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    public boolean hasFloodgate() { return floodgate; }

    public boolean isBedrock(Player p) {
        UUID id = p.getUniqueId();
        if (floodgate) {
            try {
                return FloodgateBridge.isBedrock(id);
            } catch (Throwable ignored) {
                // jatuh ke heuristik di bawah
            }
        }
        // UUID pemain Floodgate selalu diawali 00000000-0000-0000
        return id.getMostSignificantBits() == 0L;
    }

    /** Dipisah agar class Floodgate tidak dimuat bila plugin-nya tidak terpasang. */
    private static final class FloodgateBridge {
        static boolean isBedrock(UUID id) {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(id);
        }
    }
}
