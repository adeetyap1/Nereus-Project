package com.nereus.guild;

/** Data guild satu pemain. */
public final class PlayerData {
    /** Indeks rank (0 = rank pertama). */
    public int rank;
    public long cooldownUntil;
    public Exam exam;

    /** Ujian yang sedang berjalan. */
    public static final class Exam {
        public int targetRank;
        public long startedAt;
        /** 0 = tanpa batas waktu. */
        public long deadline;
        public int progress;
    }
}
