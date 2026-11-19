package my.nereus.intro.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Satu sesi aktif per pemain. */
public final class SessionManager {

    private final Map<UUID, IntroSession> sessions = new ConcurrentHashMap<>();

    public boolean isActive(UUID id) { return sessions.containsKey(id); }

    public IntroSession get(UUID id) { return sessions.get(id); }

    /** @return false bila pemain sudah punya sesi (mencegah dobel). */
    public boolean register(IntroSession s) {
        return sessions.putIfAbsent(s.uuid(), s) == null;
    }

    public void remove(UUID id) { sessions.remove(id); }

    public int count() { return sessions.size(); }

    public void stopAll(EndReason reason) {
        for (IntroSession s : new ArrayList<>(sessions.values())) {
            try {
                s.finish(reason);
            } catch (Throwable t) {
                s.logError(t);
            }
        }
        sessions.clear();
    }

    public List<IntroSession> all() { return new ArrayList<>(sessions.values()); }
}
