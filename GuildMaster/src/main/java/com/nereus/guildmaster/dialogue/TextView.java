package com.nereus.guildmaster.dialogue;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

/** Cadangan: teks bernomor di chat (Bedrock tanpa Floodgate). Pilih dengan /guildmaster choose N. */
final class TextView {
    private TextView() {
    }

    static void show(GuildMaster plugin, Player p, Screen screen) {
        p.sendMessage(Text.c("&8&m                              "));
        p.sendMessage(Text.c(screen.title()));
        for (String l : screen.lines()) {
            p.sendMessage(Text.c(l));
        }
        for (int i = 0; i < screen.buttons().size(); i++) {
            Screen.Button b = screen.buttons().get(i);
            Component line = Text.c("&e[" + (i + 1) + "] &r" + b.label())
                    .clickEvent(ClickEvent.runCommand("/guildmaster choose " + (i + 1)));
            p.sendMessage(line);
        }
        p.sendMessage(Text.c("&7Ketik &f/guildmaster choose <nomor> &7untuk memilih."));
    }
}
