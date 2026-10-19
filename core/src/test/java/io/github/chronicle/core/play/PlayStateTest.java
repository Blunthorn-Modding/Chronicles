package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayStateTest {
    private static final String FINGERPRINT = "0".repeat(64);

    @Test
    void stateDefensivelyOrdersAndCopiesCollections() {
        UUID later = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID earlier = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var participants = new java.util.LinkedHashSet<>(List.of(later, earlier));
        PlayState state = new PlayState(0, 0, participants, Map.of(), Map.of());
        participants.clear();
        assertEquals(List.of(earlier, later), List.copyOf(state.participants()));
        assertThrows(UnsupportedOperationException.class, () -> state.participants().clear());
    }

    @Test
    void rejectsBoundsInvalidPinsAndInvalidUncertainReceipts() {
        Map<String, PlayState.Binding> tooMany = new HashMap<>();
        for (int index = 0; index <= PlayState.MAX_BINDINGS; index++) {
            String id = "test:item" + index;
            tooMany.put(id, new PlayState.Binding(id, 1, FINGERPRINT, PlayState.Mode.MANUAL, "", 0, 0, 0, ""));
        }
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 0, Set.of(), tooMany, Map.of()));
        PlayState.Binding future = new PlayState.Binding("test:future", 2, FINGERPRINT, PlayState.Mode.MANUAL, "", 0, 0, 0, "");
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 0, Set.of(), Map.of(future.id(), future), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlayState.Binding("test:entry", 1, FINGERPRINT, PlayState.Mode.ENTRY, "", 0, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new PlayState.Delivery("test:manual", PlayState.Status.CLAIMED,
                PlayState.DeliverySource.MANUAL, null, 1, 0, List.of(UUID.randomUUID())));
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new PlayState.Delivery("test:duplicate", PlayState.Status.CLAIMED,
                PlayState.DeliverySource.TIMER, null, 1, 0, List.of(player, player)));
    }
}
