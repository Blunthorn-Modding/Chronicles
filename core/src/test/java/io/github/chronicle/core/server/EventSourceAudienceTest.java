package io.github.chronicle.core.server;

import io.github.chronicle.core.play.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EventSourceAudienceTest {
    @Test void actorlessProviderRejectsPrivatePlayerAudience() {
        String id="test:actorless_validation";
        EventSources.registerProvider(id,"Actorless","No player actor",server->List.of(),List.of(),false);
        var source=new EventSource(id,"test:target","");
        assertThrows(IllegalArgumentException.class,()->EventSources.validateAudience(source,ObservationCatalog.Audience.SOURCE_ACTOR));
        assertDoesNotThrow(()->EventSources.validateAudience(source,ObservationCatalog.Audience.PARTICIPANTS));
    }
}
