package io.github.chronicle.core.server;

import io.github.chronicle.core.entry.RoleEntryOperation;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.roles.RoleSourceEvidence;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Role-owned source receipts. These observe completed operations and never grant or assign roles. */
public final class RoleEventSources {
    public static final String ASSIGNED = "chronicle_core:role_assigned";
    public static final String ENTERED = "chronicle_core:role_entry_completed";
    private RoleEventSources() { }

    public static void register() {
        EventSources.registerProvider(ASSIGNED, "Role assigned",
                "An actual role assignment during active play; opening a role menu or selecting the same role does not count.",
                RoleEventSources::choices, List.of());
        EventSources.registerProvider(ENTERED, "Role first entry completed",
                "The starting gear, lore and destination operation completed for the selected role; reconnecting does not count again.",
                RoleEventSources::choices, List.of());
        EventSources.registerAuthority("roleassign:", (server, receipt) -> {
            if (!receipt.type().equals(ASSIGNED)) throw new IllegalStateException("Role assignment source type changed");
            var decision = RoleSourceEvidence.inspect(receipt.authority(), receipt.actor(), receipt.target(), PlayerRoles.assignments(server));
            if (decision == RoleSourceEvidence.Decision.NOT_COMMITTED) EventSources.cancelAuthority(server, receipt.authority());
            return decision == RoleSourceEvidence.Decision.COMMITTED;
        }, true);
        EventSources.registerAuthority("roleentry:", (server, receipt) -> {
            String value = receipt.authority().substring("roleentry:".length());
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value) || !receipt.type().equals(ENTERED))
                throw new IllegalStateException("Role first-entry source identity changed");
            var operation = RoleFirstEntry.sourceOperation(server, id);
            if (operation != null && operation.stage() == RoleEntryOperation.Stage.ABORTED) {
                EventSources.cancelAuthority(server, receipt.authority());
                return false;
            }
            return operation != null && operation.stage() == RoleEntryOperation.Stage.COMPLETE
                    && operation.playerId().equals(receipt.actor()) && operation.roleId().equals(receipt.target());
        }, true);
    }

    private static List<EventSources.Choice> choices(MinecraftServer server) {
        var result = new ArrayList<EventSources.Choice>();
        result.add(new EventSources.Choice("*", "Any role", "Any available player role"));
        PlayerRoles.definitions(server).values().forEach(role -> result.add(new EventSources.Choice(role.id(), role.name(), role.description())));
        return List.copyOf(result);
    }

    public static String preparingAssignment(MinecraftServer server, UUID player, String role) {
        if (role.equals(PlayerRoles.assignments(server).assigned().get(player))) return null;
        var source = new EventSource(ASSIGNED, role, "");
        String authority = RoleSourceEvidence.authority(player, Math.incrementExact(PlayerRoles.assignments(server).revision()));
        return EventSources.prepare(server, EventSources.id(authority, ASSIGNED, role, ""), source,
                player, EventSources.activeOperation(server), authority);
    }

    public static void preparingEntry(MinecraftServer server, RoleEntryOperation operation, boolean activeOrigin) {
        var source = new EventSource(ENTERED, operation.roleId(), "");
        String authority = "roleentry:" + operation.operationId();
        EventSources.prepare(server, EventSources.id(authority, ENTERED, operation.roleId(), ""), source,
                operation.playerId(), activeOrigin, authority);
    }

    public static void completedEntry(MinecraftServer server, RoleEntryOperation operation) {
        String authority = "roleentry:" + operation.operationId();
        for (var receipt : EventSources.pending(server))
            if (receipt.authority().equals(authority)) EventSources.complete(server, receipt.id(), true);
    }
}
