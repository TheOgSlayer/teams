package smp.jalebi.scoreboard;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Native Minecraft dialog screen (Paper Dialog API, 1.21.7+). */
public class ScoreboardGui {
    private final JalebiScoreboard plugin;

    public ScoreboardGui(JalebiScoreboard plugin) {
        this.plugin = plugin;
    }

    private static Component label(Line l) {
        return Component.text(l.symbol + " " + SmallCaps.of(l.label), TextColor.fromHexString(l.hex));
    }

    public void open(Player p) {
        UUID id = p.getUniqueId();

        List<DialogInput> inputs = new ArrayList<>();
        for (Line l : Line.values()) {
            inputs.add(DialogInput.bool(l.name(), label(l))
                    .initial(plugin.data().isEnabled(id, l))
                    .build());
        }

        ActionButton save = ActionButton.create(
                Component.text(SmallCaps.of("Save"), NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text(SmallCaps.of("Apply your changes")),
                120,
                DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player player)) return;
                    // read values now, apply on the main thread
                    java.util.Map<Line, Boolean> result = new java.util.EnumMap<>(Line.class);
                    for (Line l : Line.values()) {
                        Boolean b = view.getBoolean(l.name());
                        if (b != null) result.put(l, b);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        result.forEach((l, on) -> plugin.data().setEnabled(player.getUniqueId(), l, on));
                        plugin.boards().update(player);
                    });
                }, ClickCallback.Options.builder().build()));

        ActionButton cancel = ActionButton.create(
                Component.text(SmallCaps.of("Cancel"), NamedTextColor.RED),
                null, 120, null);

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(
                                Component.text(SmallCaps.of("Scoreboard Settings"),
                                        TextColor.fromHexString("#FFA500"), TextDecoration.BOLD))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text(SmallCaps.of("Choose what to show on your scoreboard"),
                                        NamedTextColor.GRAY))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(save, cancel)));

        p.showDialog(dialog);
    }
}
