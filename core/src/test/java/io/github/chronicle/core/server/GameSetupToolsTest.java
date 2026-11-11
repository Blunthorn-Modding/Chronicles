package io.github.chronicle.core.server;

import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameSetupToolsTest {
    @Test void presentationConsolidationKeepsRegisteredRecoveryTools(){
        String parent="test:altar_parent",child="test:altar_child";
        for(String id:List.of(parent,child))GameSetupTools.register(new GameSetupTools.Tool(id,id,"",List.of(),ignored->new GameSetupTools.Status(0,List.of(),Map.of()),(source,verb)->"done"));
        GameSetupTools.presentWithin(parent,List.of(child));
        org.junit.jupiter.api.Assertions.assertTrue(GameSetupTools.get(child).isPresent());
        org.junit.jupiter.api.Assertions.assertTrue(GameSetupTools.tools().stream().anyMatch(t->t.id().equals(child)));
        org.junit.jupiter.api.Assertions.assertFalse(GameSetupTools.menuTools().stream().anyMatch(t->t.id().equals(child)));
        org.junit.jupiter.api.Assertions.assertTrue(GameSetupTools.menuTools().stream().anyMatch(t->t.id().equals(parent)));
    }
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
