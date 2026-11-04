package io.github.chronicle.core.server;

import io.github.chronicle.core.play.*;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.util.*;

/** Relative timers observe exact completed once-only Events, rather than a second story clock. */
public final class CompletionEventSources {
    public static final String TYPE = "chronicle_core:event_completed";
    private CompletionEventSources() { }
    public static void register() {
        EventSources.registerProvider(TYPE, "Event completed", "A once-only Event completed. Its delay starts here and uses occupied active play.",
                server -> EventRuntime.configurations(server).values().stream()
                        .filter(c -> c.occurrencePolicy() == EventOccurrencePolicy.ONCE)
                        .map(c -> new EventSources.Choice(c.definition().id(), c.definition().label(), c.definition().description())).toList(), List.of());
        EventSources.registerAuthority("eventcomplete:", (server, receipt) -> {
            if(!receipt.type().equals(TYPE)||!receipt.activity().isEmpty())throw new IllegalStateException("Completed Event source identity changed");
            UUID operation = UUID.fromString(receipt.authority().substring("eventcomplete:".length()));
            EventExecution execution = PlayRuntime.service(server).state().executions().get(receipt.target());
            if (execution == null || !execution.operationId().equals(operation)) try {
                execution = PlayRuntime.service(server).archivedExecution(operation).orElse(null);
            } catch (IOException failure) { throw new IllegalStateException("Completed Event source archive is unavailable", failure); }
            return execution != null && execution.operationId().equals(operation) && execution.eventId().equals(receipt.target())
                    && Objects.equals(execution.actor(),receipt.actor())&&execution.configuration().occurrencePolicy() == EventOccurrencePolicy.ONCE
                    && execution.phase() == EventExecution.Phase.COMPLETE;
        });
    }
    static String prepare(MinecraftServer server, EventExecution execution) {
        if (execution.configuration().occurrencePolicy() != EventOccurrencePolicy.ONCE) return null;
        String authority = "eventcomplete:" + execution.operationId();
        return EventSources.prepare(server, EventSources.id(authority, TYPE, execution.eventId(), ""),
                new EventSource(TYPE, execution.eventId(), ""), execution.actor(), EventSources.activeOperation(server), authority);
    }
}
