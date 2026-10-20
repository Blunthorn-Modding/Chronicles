package io.github.chronicle.core.server;

import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameSetupToolsTest {
    @Test
    void toolDefinitionKeepsFiniteVerbsAndStatusDataBounded() {
        assertDoesNotThrow(() -> new GameSetupTools.Tool("quiet_below:ecology", "Ecology", "Campaign state.",
                List.of(new GameSetupTools.Verb("dry_run", "Dry run", GameSetupTools.Guard.EDIT, true)),
                ignored -> new GameSetupTools.Status(3, List.of(new Fact("State", "Ready")), Map.of("dry_run", "Not configured")),
                (source, verb) -> "done"));
    }

    @Test
    void rejectsUnboundedTransportVerbAndDisabledReasonKey() {
        assertThrows(IllegalArgumentException.class, () -> new GameSetupTools.Verb("x".repeat(28), "Too long", GameSetupTools.Guard.READ_ONLY, false));
        assertThrows(IllegalArgumentException.class, () -> new GameSetupTools.Status(0, List.of(), Map.of("x".repeat(28), "disabled")));
    }
}
