package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generic boolean campaign prerequisites; flags have one management home under Advanced. */
final class CoreFlagMenu {
    private CoreFlagMenu() { }

    static List<Entry> entries(net.minecraft.server.MinecraftServer server) {
        return ChronicleRuntime.service(server).state().flags().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(flag -> new Entry("flag:" + flag.getKey(), flag.getKey(),
                        "Current value: " + (flag.getValue() ? "On" : "Off"), "FLAG", flag.getKey(), 0))
                .toList();
    }

    static Detail detail(ServerPlayerEntity player, String selected) {
        var server = player.getEntityWorld().getServer();
        var state = ChronicleRuntime.service(server).state();
        boolean free = RuntimeHolds.reason(server) == null;
        boolean canCreate = player.getCommandSource().hasPermissionLevel(2) && free
                && AuthoringRuntime.isAuthor(player) && AuthoringRuntime.editable(server);
        boolean room = state.flags().size() < 256;
        String reason = !room ? "The campaign flag limit has been reached."
                : !free ? "Finish pending operations before creating flags."
                : state.phase() == SessionPhase.ACTIVE ? "Pause play to create flags."
                : "Enable editing to create flags.";
        var controls = new ArrayList<Control>();
        controls.add(new Control(Action.FLAG_CREATE, "Create flag", canCreate && room,
                canCreate && room ? "" : reason, false, ""));
        if (selected.isBlank()) return new Detail("", "Flags",
                "Create flags to use as Event prerequisites. New flags start Off.", List.of(), controls, null, null, null);
        String id = selected.startsWith("flag:") ? selected.substring(5) : selected;
        Boolean value = state.flags().get(id);
        if (value == null) throw new IllegalArgumentException("Choose an existing flag.");
        boolean live = state.phase() == SessionPhase.ACTIVE && (free||EventOccurrenceRuntime.onlyBackpressureHold(server));
        controls.add(new Control(Action.CONDITION_SET, value ? "Turn Off" : "Turn On", live,
                live ? "" : !free ? "Finish pending operations before changing flags." : "Start or resume play to change this flag.", true, value ? "FLAG_OFF" : "FLAG_ON"));
        return new Detail("flag:" + id, id,
                "Current value: " + (value ? "On" : "Off") + ". An Event requiring this flag can trigger when it is On.",
                List.of(new Fact("Flag ID", id), new Fact("Stored value", value.toString())), controls, null, null, null);
    }

    static void requireDefinitionAuthor(ServerCommandSource source) {
        var player = source.getPlayer();
        if (!source.hasPermissionLevel(2) || player == null || !AuthoringRuntime.isAuthor(player)
                || !AuthoringRuntime.editable(source.getServer()) || RuntimeHolds.reason(source.getServer()) != null)
            throw new IllegalStateException("Flag creation requires operator permission and non-active Editing with no operation hold.");
    }
}
