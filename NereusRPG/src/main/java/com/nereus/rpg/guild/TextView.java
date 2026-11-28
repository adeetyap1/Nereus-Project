package com.nereus.rpg.guild;

import com.nereus.rpg.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

/** Cadangan: teks bernomor di chat (Bedrock tanpa Floodgate). Pilih dengan /gm choose N. */
final class TextView {
    private TextView() {
    }

    static void show(Player p, Screen screen) {
        p.sendMessage(Text.c("&8&m                              "));
        p.sendMessage(Text.c(screen.title()));
        for (String l : screen.lines()) {
            p.sendMessage(Text.c(l));
        }
        for (int i = 0; i < screen.buttons().size(); i++) {
            Screen.Button b = screen.buttons().get(i);
            Component line = Text.c("&e[" + (i + 1) + "] &r" + b.label())
                    .clickEvent(ClickEvent.runCommand("/nereusrpg choose " + (i + 1)));
            p.sendMessage(line);
        }
        p.sendMessage(Text.c("&7Ketik &f/gm choose <nomor> &7untuk memilih."));
    }
}
