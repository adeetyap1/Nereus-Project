package com.nereus.guildmaster.adventurer;

import java.util.UUID;

/** Catatan registrasi satu pemain. Hanya ini yang disimpan GuildMaster; progres ada di Nereus RPG. */
public record Adventurer(UUID uuid, String name, long registeredAt, boolean revoked) {
    public boolean active() {
        return !revoked;
    }
}
