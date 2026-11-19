package my.nereus.intro.manager;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.config.IntroConfig;
import my.nereus.intro.recovery.PlayerSnapshot;
import my.nereus.intro.session.EndReason;
import my.nereus.intro.session.IntroSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Pintu masuk tunggal untuk memulai, melewati, dan mengatur sesi intro. */
public final class IntroService {

    public enum Status { OK, DISABLED, NOT_CONFIGURED, EMPTY_TIMELINE, ACTIVE, ALREADY_COMPLETED, RECOVERY_PENDING, DEAD, NO_TEST }

    public record StartResult(Status status, String detail) {
        public boolean ok() { return status == Status.OK; }
    }

    public enum SkipStatus { NONE_ACTIVE, DISABLED, TOO_EARLY, CONFIRM, OK }

    private final NereusIntro plugin;

    public IntroService(NereusIntro plugin) {
        this.plugin = plugin;
    }

    public StartResult start(Player target, boolean test, boolean requesterIsAdmin, Boolean bedrockOverride) {
        IntroConfig cfg = plugin.config();
        UUID id = target.getUniqueId();

        if (test) {
            if (!cfg.adminAllowTest) return new StartResult(Status.NO_TEST, "");
            if (!cfg.enabled && !cfg.adminTestWhenDisabled) return new StartResult(Status.DISABLED, "");
        } else {
            if (!cfg.enabled) return new StartResult(Status.DISABLED, "");
            boolean unlimited = requesterIsAdmin && cfg.adminUnlimitedReplay;
            if (!unlimited && !cfg.replayAllowPlayers && plugin.progress().isCompleted(id)) {
                return new StartResult(Status.ALREADY_COMPLETED, "");
            }
        }
        List<String> problems = cfg.locationProblems();
        if (!problems.isEmpty()) return new StartResult(Status.NOT_CONFIGURED, String.join("; ", problems));
        if (cfg.timeline.isEmpty()) return new StartResult(Status.EMPTY_TIMELINE, "");
        if (target.isDead()) return new StartResult(Status.DEAD, "");
        if (plugin.sessions().isActive(id)) return new StartResult(Status.ACTIVE, "");
        if (plugin.recovery().store().exists(id)) return new StartResult(Status.RECOVERY_PENDING, "");

        IntroSession session = new IntroSession(plugin, target, test, cfg, PlayerSnapshot.capture(target), bedrockOverride);
        if (!plugin.sessions().register(session)) return new StartResult(Status.ACTIVE, "");
        try {
            session.start();
        } catch (Throwable t) {
            session.logError(t);
            session.finish(EndReason.FAILED);
        }
        return new StartResult(Status.OK, "");
    }

    public SkipStatus skip(Player target, boolean byAdmin) {
        IntroSession s = plugin.sessions().get(target.getUniqueId());
        if (s == null) return SkipStatus.NONE_ACTIVE;
        IntroConfig cfg = s.config();
        if (!byAdmin && !s.isTest()) {
            if (!cfg.skipEnabled || !cfg.skipAllowPlayers) return SkipStatus.DISABLED;
            long elapsed = System.currentTimeMillis() - s.startedAt();
            if (elapsed < cfg.skipMinSeconds * 1000L) return SkipStatus.TOO_EARLY;
            if (cfg.skipConfirm) {
                long now = System.currentTimeMillis();
                if (now > s.skipConfirmUntil()) {
                    s.skipConfirmUntil(now + cfg.skipConfirmWindow * 1000L);
                    return SkipStatus.CONFIRM;
                }
            }
        }
        s.finish(EndReason.SKIPPED);
        return SkipStatus.OK;
    }

    /** Dipanggil dari listener login setelah pemulihan selesai. */
    public void maybeAutoStart(Player p, boolean firstJoin) {
        IntroConfig cfg = plugin.config();
        if (!cfg.enabled || !cfg.autoStartEnabled || !p.hasPermission("nereusintro.use")) return;
        UUID id = p.getUniqueId();
        if (plugin.progress().isCompleted(id)) return;

        int interrupted = plugin.progress().interrupted(id);
        boolean restart = cfg.recoveryRestart && interrupted > 0;
        if (restart && interrupted > cfg.recoveryMaxRestarts) {
            plugin.getLogger().info(p.getName() + " melewati batas pengulangan intro (" + interrupted + "); tidak dimulai lagi otomatis.");
            return;
        }
        boolean eligible = firstJoin || restart || !cfg.autoStartNewOnly;
        if (!eligible) return;

        long delay = restart && !firstJoin ? cfg.recoveryRestartDelay : cfg.autoStartDelay;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || plugin.sessions().isActive(id)) return;
            StartResult r = start(p, false, false, null);
            if (!r.ok() && r.status() != Status.ACTIVE) {
                plugin.getLogger().warning("Auto-start intro untuk " + p.getName() + " gagal: " + r.status()
                        + (r.detail().isEmpty() ? "" : " (" + r.detail() + ")"));
            }
        }, delay);
    }
}
