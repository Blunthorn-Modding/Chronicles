package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.eventpool.EventPoolDraw;
import io.github.chronicle.core.play.*;
import io.github.chronicle.lastcouncil.complications.ComplicationConfig;
import io.github.chronicle.lastcouncil.council.ProceedingsState.Phase;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ComplicationsProjectionTest {
    private EventPoolDraw draw(boolean privateRoom,UUID recipient) {
        var d=new EventDefinition("test:interruption",1,"A private letter","","The private contents","",privateRoom?ObservationCatalog.Audience.REGION:ObservationCatalog.Audience.PARTICIPANTS,
                privateRoom?Optional.of(new EventRegionReference("room",1)):Optional.empty(),List.of());
        var c=new EventConfiguration(d,PlayState.Mode.MANUAL,Optional.empty(),0,0,"",true);
        return new EventPoolDraw("slot","test:pool","a".repeat(64),UUID.randomUUID(),c,1,null,List.of(recipient));
    }
    @Test void privateOccurrenceDoesNotLeakEvenItsLabelToAnOutsideChancellor() {
        UUID inside=UUID.randomUUID(),outside=UUID.randomUUID();var privateDraw=draw(true,inside);
        assertTrue(ComplicationsRuntime.projectReveal(1,privateDraw,outside).isEmpty());
        assertEquals("The private contents",ComplicationsRuntime.projectReveal(1,privateDraw,inside).orElseThrow().message());
        assertEquals("The private contents",ComplicationsRuntime.projectReveal(1,draw(false,inside),outside).orElseThrow().message());
    }
    @Test void gatheringFreeRoleplayPausesAndHoldsAreNotCouncilTime() {
        assertFalse(ComplicationsRuntime.clockAdvances(Phase.BETWEEN,true,false));
        assertFalse(ComplicationsRuntime.clockAdvances(Phase.GATHERING,true,false));
        assertTrue(ComplicationsRuntime.clockAdvances(Phase.DISCUSSION,true,false));
        assertTrue(ComplicationsRuntime.clockAdvances(Phase.RECESS,true,false));
        assertFalse(ComplicationsRuntime.clockAdvances(Phase.DISCUSSION,false,false));
        assertFalse(ComplicationsRuntime.clockAdvances(Phase.RECESS,true,true));
    }
    @Test void settingsTransportAcceptsEveryLegalPoolLabel() {
        String label="\ud83c\udfdb".repeat(80);
        var pool=new io.github.chronicle.core.eventpool.EventPoolDefinition("test:pool",label,List.of());
        assertEquals(label,new io.github.chronicle.lastcouncil.network.ComplicationPayloads.PoolChoice(pool.id(),pool.label()).label());
    }
    @Test void reusableSettingsRejectMalformedNumbersAndOutsideChanges(@TempDir Path dir)throws Exception {
        var c=ComplicationConfig.defaults();assertEquals(c,ComplicationSettings.decode(ComplicationSettings.encode(c)));
        String json=new String(ComplicationSettings.encode(c),StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->ComplicationSettings.decode(json.replace("\"total\":2","\"total\":2.0").getBytes(StandardCharsets.UTF_8)));
        Path file=dir.resolve("setup.json");var settings=new ComplicationSettings(file,c);Files.writeString(file,"{}");
        assertThrows(IOException.class,()->settings.save(c));assertEquals("{}",Files.readString(file));
    }
    @Test void reusableSettingsEncodingContainsNoPrivatePlanFields() {
        String json=new String(ComplicationSettings.encode(ComplicationConfig.defaults()),StandardCharsets.UTF_8);
        for(String privateField:List.of("seed","slots","claims","recipients","clock","experience","schedule","selected")) assertFalse(json.contains(privateField));
        assertEquals(Set.of("schema","poolId","total","meetings","earliestTicks","latestTicks","spacingTicks"),com.google.gson.JsonParser.parseString(json).getAsJsonObject().keySet());
    }

}
