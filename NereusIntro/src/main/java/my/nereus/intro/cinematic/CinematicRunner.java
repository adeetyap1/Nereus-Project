package my.nereus.intro.cinematic;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.session.IntroSession;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Menjalankan timeline secara berurutan: langkah berikutnya baru dijadwalkan setelah langkah sekarang
 * melapor selesai. Hanya ada satu task tertunda pada satu waktu, sehingga adegan tidak bisa bertabrakan.
 */
public final class CinematicRunner {

    private final NereusIntro plugin;
    private final IntroSession session;
    private final List<Step> steps;
    private final StepContext ctx;

    private int index;
    private int generation;
    private boolean stopped;
    private BukkitTask pending;

    public CinematicRunner(NereusIntro plugin, IntroSession session, List<Step> steps) {
        this.plugin = plugin;
        this.session = session;
        this.steps = steps;
        this.ctx = new StepContext(plugin, session);
    }

    public void start() {
        advance();
    }

    public int index() { return index; }
    public int size() { return steps.size(); }

    private void advance() {
        pending = null;
        if (stopped) return;
        if (index >= steps.size()) {
            session.onTimelineFinished();
            return;
        }
        Step step = steps.get(index++);
        final int gen = generation;
        session.debugStep(index, steps.size(), step.name());
        AtomicBoolean called = new AtomicBoolean(false);
        try {
            step.execute(ctx, delay -> {
                if (!called.compareAndSet(false, true) || stopped || gen != generation) return;
                schedule(delay);
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Langkah timeline #" + index + " (" + step.name() + ") error: " + t);
            session.fail("error pada langkah " + step.name());
        }
    }

    private void schedule(long delay) {
        long d = Math.max(plugin.config().minStepDelay, delay);
        try {
            pending = Bukkit.getScheduler().runTaskLater(plugin, this::advance, d);
        } catch (Throwable t) {
            // plugin sedang dimatikan
        }
    }

    public void stop() {
        stopped = true;
        generation++;
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }
}
