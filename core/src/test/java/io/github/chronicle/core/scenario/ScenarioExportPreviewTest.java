package io.github.chronicle.core.scenario;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ScenarioExportPreviewTest {
    @Test void boundsOperatorSummaryAndRejectsUnboundedOrNegativeValues() {
        var preview=new ScenarioExportPreview("example:quiet","Quiet Below",1,2,3,4,2,"a".repeat(64),true,"");
        assertEquals(4,preview.events());
        assertThrows(IllegalArgumentException.class,()->new ScenarioExportPreview("x".repeat(161),"Quiet",0,0,0,0,0,"",true,""));
        assertThrows(IllegalArgumentException.class,()->new ScenarioExportPreview("example:quiet","Quiet",-1,0,0,0,0,"",true,""));
    }
}
