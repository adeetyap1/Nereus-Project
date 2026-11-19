package my.nereus.intro.session;

public enum EndReason {
    /** Timeline selesai. */
    COMPLETED,
    /** Dilewati pemain/admin. */
    SKIPPED,
    /** Error (NPC, teleport, timeout). */
    FAILED,
    /** Pemain keluar saat intro. */
    QUIT,
    /** Plugin dimatikan / server berhenti. */
    DISABLE;

    /** Pemulihan harus sinkron karena pemain/plugin sedang berhenti. */
    public boolean syncRestore() {
        return this == QUIT || this == DISABLE;
    }

    public boolean finishesStory() {
        return this == COMPLETED || this == SKIPPED;
    }
}
