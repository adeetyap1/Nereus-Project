package my.nereus.intro.npc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.skins.SkinData;
import my.nereus.intro.NereusIntro;
import my.nereus.intro.config.IntroConfig;
import org.bukkit.Bukkit;

import java.util.Locale;

/**
 * Menyiapkan skin Nerith di thread async (username/url bisa memerlukan request jaringan) dan menyimpannya,
 * sehingga saat intro berjalan tidak ada blocking di main thread.
 */
public final class SkinResolver {

    private final NereusIntro plugin;
    private volatile SkinData cached;

    public SkinResolver(NereusIntro plugin) {
        this.plugin = plugin;
    }

    public SkinData get() {
        return cached;
    }

    public void refresh(IntroConfig cfg) {
        final String mode = cfg.skinMode;
        if (mode.equals("default") || cfg.skinValue.isBlank()) {
            cached = null;
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                cached = load(cfg);
                if (cached == null || !cached.hasTexture()) {
                    plugin.getLogger().warning("Skin NPC (" + mode + ") tidak memiliki tekstur; memakai skin bawaan.");
                    cached = null;
                } else {
                    plugin.getLogger().info("Skin NPC Nerith siap (" + mode + ").");
                }
            } catch (Throwable t) {
                cached = null;
                plugin.getLogger().warning("Gagal memuat skin NPC: " + t + ". Memakai skin bawaan.");
            }
        });
    }

    private SkinData load(IntroConfig cfg) {
        SkinData.SkinVariant variant;
        try {
            variant = SkinData.SkinVariant.valueOf(cfg.skinVariant.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            variant = SkinData.SkinVariant.AUTO;
        }
        if (cfg.skinMode.equals("texture")) {
            if (cfg.skinSignature.isBlank()) return null;
            return new SkinData("nereusintro", variant, cfg.skinValue, cfg.skinSignature);
        }
        SkinData data = FancyNpcsPlugin.get().getSkinManager().getByIdentifier(cfg.skinValue, variant);
        if (data != null) data.setIdentifier(cfg.skinValue);
        return data;
    }
}
