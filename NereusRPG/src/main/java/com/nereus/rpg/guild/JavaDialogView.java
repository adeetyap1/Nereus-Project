package com.nereus.rpg.guild;

import com.nereus.rpg.NereusRPG;
import com.nereus.rpg.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Popup native Paper (Dialog API) untuk pemain Java; dipilih dengan dialogue.java-view: DIALOG. */
final class JavaDialogView {
    private JavaDialogView() {
    }

    static void show(NereusRPG plugin, Player p, Screen screen, IntConsumer choose) {
        UUID owner = p.getUniqueId();
        List<Component> lines = new ArrayList<>();
        for (String l : screen.lines()) {
            lines.add(Text.c(l.isEmpty() ? " " : l));
        }
        Component body = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);

        int width = Math.max(120, Math.min(300, plugin.getConfig().getInt("dialogue.button-width", 220)));
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < screen.buttons().size(); i++) {
            final int index = i;
            Screen.Button b = screen.buttons().get(i);
            Component tooltip = b.tooltip().isEmpty() ? null : Text.c(b.tooltip());
            buttons.add(ActionButton.create(Text.c(b.label()), tooltip, width,
                    DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker && clicker.getUniqueId().equals(owner)) {
                            choose.accept(index);
                        }
                    }, ClickCallback.Options.builder()
                            .uses(1)
                            .lifetime(ClickCallback.DEFAULT_LIFETIME)
                            .build())));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.c(screen.title()))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        p.showDialog(dialog);
    }
}
