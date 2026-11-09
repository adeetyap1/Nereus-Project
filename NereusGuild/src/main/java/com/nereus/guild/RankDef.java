package com.nereus.guild;

import java.util.List;

/** Satu rank guild dari config.yml. */
public record RankDef(String key, String name, String color, int gp, ExamDef exam, boolean broadcast,
                      List<String> commands) {

    /** Nama berwarna, contoh "&9Rank C". */
    public String label() {
        return color + name;
    }
}
