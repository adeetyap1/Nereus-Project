package com.nereus.rpg.guild;

import com.nereus.rpg.Text;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.function.IntConsumer;

/** Form Bedrock lewat Floodgate. Hanya dimuat jika Floodgate aktif (semua akses lewat Platform.floodgate()). */
public final class BedrockForms {
    private BedrockForms() {
    }

    public static boolean isFloodgate(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    public static boolean send(Player p, Screen screen, IntConsumer choose) {
        try {
            if (!FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId())) {
                return false;
            }
            SimpleForm.Builder form = SimpleForm.builder()
                    .title(Text.section(screen.title()))
                    .content(Text.section(String.join("\n", screen.lines())));
            for (Screen.Button b : screen.buttons()) {
                form.button(Text.section(b.label()));
            }
            form.validResultHandler(response -> choose.accept(response.clickedButtonId()));
            return FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }
}
