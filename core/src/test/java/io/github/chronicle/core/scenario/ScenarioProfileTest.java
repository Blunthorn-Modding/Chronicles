package io.github.chronicle.core.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioProfileTest {
    @TempDir Path directory;
    @Test void emptySetupHasSeparateIdentityAndNoContent(){
        var profile=ScenarioProfile.empty("custom:island","Our island",Set.of("quiet_below:mechanics"));
        assertEquals(profile,ScenarioProfileJson.decode(ScenarioProfileJson.encode(profile)));
        assertTrue(profile.content().roles().isEmpty());assertTrue(profile.content().books().isEmpty());assertTrue(profile.content().lootTables().isEmpty());assertTrue(profile.content().events().isEmpty());assertTrue(profile.content().encounterGroups().isEmpty());
        assertNotEquals(profile.identity(),ScenarioProfile.empty("custom:island","Our island",profile.mechanics()).identity());
        assertThrows(IllegalArgumentException.class,()->ScenarioContentJson.decode(ScenarioProfileJson.encode(profile)));
    }
    @Test void duplicateUnknownOrMalformedEnvelopeIsRefused(){
        String json=ScenarioProfileJson.encode(ScenarioProfile.empty("custom:test","Test",Set.of()));
        assertThrows(IllegalArgumentException.class,()->ScenarioProfileJson.decode(json.replace("\"schema\": 1","\"schema\": 1, \"schema\": 1")));
        assertThrows(IllegalArgumentException.class,()->ScenarioProfileJson.decode(json.replace("\"schema\": 1","\"schema\": 1, \"other\": true")));
        assertThrows(IllegalArgumentException.class,()->ScenarioProfileJson.decode(json.replace("\"schema\": 1","\"schema\": 1.0")));
    }
    @Test void createNeverOverwritesAndExportIsExactAndReusable()throws Exception{
        var store=new ScenarioProfileStore(directory);var profile=ScenarioProfile.empty("custom:a","A",Set.of());Path kit=store.create(profile);byte[] before=Files.readAllBytes(kit);
        assertThrows(java.io.IOException.class,()->store.create(ScenarioProfile.empty("custom:a","Changed",Set.of())));assertArrayEquals(before,Files.readAllBytes(kit));
        Path export=store.export(profile);assertEquals(export,store.export(profile));assertEquals(profile,ScenarioProfileStore.read(export));
        Files.writeString(export,"invalid");assertThrows(java.io.IOException.class,()->store.export(profile));assertEquals("invalid",Files.readString(export));
    }
}
