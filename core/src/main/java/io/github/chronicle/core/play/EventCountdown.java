package io.github.chronicle.core.play;

import java.util.Map;

/** Pure countdown reducer. Durable Off epochs catch even an Off/On between dispatch ticks. */
public final class EventCountdown {
    private EventCountdown() { }

    public static EventProgressState.Entry observe(EventProgressState.Entry entry,
            Map<String, Boolean> flags, Map<String, Long> offVersions, long clock, boolean eligible) {
        if (clock < 0) throw new IllegalArgumentException("Invalid play clock");
        if (entry.startedAt() > clock) throw new IllegalStateException("Event countdown is ahead of the saved play clock; review its recovery evidence");
        boolean ready = offVersions.keySet().stream().allMatch(id -> Boolean.TRUE.equals(flags.get(id)));
        boolean reset = !offVersions.equals(entry.offVersions()) || !ready;
        long started = reset ? -1 : entry.startedAt();
        if (eligible && ready && entry.triggered() && !entry.skipped() && started == -1) started = clock;
        if (started > clock) throw new IllegalStateException("Event countdown is ahead of the saved play clock; review its recovery evidence");
        return new EventProgressState.Entry(entry.eventId(),entry.fingerprint(),started,entry.triggered(),entry.actor(),
                entry.source(),entry.occurrence(),entry.activeOrigin(),entry.seenFacts(),offVersions,entry.skipped());
    }

    public static boolean due(EventProgressState.Entry entry, long delay, long clock) {
        return !entry.skipped() && entry.triggered() && entry.startedAt() >= 0
                && clock >= entry.startedAt() && clock - entry.startedAt() >= delay;
    }
}
