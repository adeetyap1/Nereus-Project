package my.nereus.duel;

import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class Match {
    enum Phase { COUNTDOWN, ACTIVE, BREAK, ENDED }

    final UUID a;
    final UUID b;
    final String mode;
    final Arena arena;
    final Map<UUID, Snapshot> snaps = new HashMap<>();
    volatile Phase phase = Phase.COUNTDOWN;
    int scoreA;
    int scoreB;
    int round;
    long roundStart;
    BukkitTask task;

    Match(UUID a, UUID b, String mode, Arena arena) {
        this.a = a;
        this.b = b;
        this.mode = mode;
        this.arena = arena;
    }

    UUID opponent(UUID u) {
        return u.equals(a) ? b : a;
    }

    int score(UUID u) {
        return u.equals(a) ? scoreA : scoreB;
    }
}
