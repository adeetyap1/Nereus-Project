package my.nereus.intro.cinematic;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.config.IntroConfig;
import my.nereus.intro.session.IntroSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Akses terbatas yang dibutuhkan sebuah langkah. */
public final class StepContext {

    private final NereusIntro plugin;
    private final IntroSession session;

    public StepContext(NereusIntro plugin, IntroSession session) {
        this.plugin = plugin;
        this.session = session;
    }

    public NereusIntro plugin() { return plugin; }
    public IntroSession session() { return session; }
    public Player player() { return session.player(); }
    public IntroConfig cfg() { return session.config(); }
    public boolean bedrock() { return session.isBedrock(); }

    public void track(BukkitTask task) { session.track(task); }
    public void fail(String reason) { session.fail(reason); }

    /** Jeda otomatis (tick) untuk sebaris teks sesuai panjangnya. */
    public long pauseFor(Component c) {
        IntroConfig cfg = cfg();
        int len = PlainTextComponentSerializer.plainText().serialize(c).length();
        long p = Math.round(cfg.pauseBase + len * cfg.pausePerChar);
        return Math.max(cfg.pauseMin, Math.min(cfg.pauseMax, p));
    }
}
