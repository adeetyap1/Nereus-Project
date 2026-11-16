package com.nereus.guildmaster.dialogue;

import java.util.List;

/** Satu layar percakapan siap tampil (teks sudah diisi placeholder, masih berkode &amp;). */
public record Screen(String title, List<String> lines, List<Button> buttons) {
    public record Button(String label, String tooltip, String action) {
    }
}
