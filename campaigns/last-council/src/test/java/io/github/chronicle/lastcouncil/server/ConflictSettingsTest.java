package io.github.chronicle.lastcouncil.server;
import java.nio.file.*;import java.io.IOException;import java.nio.charset.StandardCharsets;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
class ConflictSettingsTest {
 @TempDir Path dir;
 @Test void settingsArePortableStrictAndMissingInitializedProfilesRefuse()throws Exception{ConflictSettings.initialize(dir,null,false);var s=new ConflictSettings(dir.resolve("last-council-conflicts.json"),4);s.save(6);assertEquals(6,ConflictSettings.decode(Files.readAllBytes(dir.resolve("last-council-conflicts.json"))));ConflictSettings.initialize(dir,null,true);Files.delete(dir.resolve("last-council-conflicts.json"));assertThrows(IOException.class,()->ConflictSettings.initialize(dir,null,true));}
 @Test void malformedAndOverflowValuesRefuse()throws Exception{for(String value:new String[]{"1","17","4294967300","4.0","\"4\""})assertThrows(IOException.class,()->ConflictSettings.decode(("{\"schema\":1,\"civilWarThreshold\":"+value+"}").getBytes(StandardCharsets.UTF_8)));assertEquals(4,ConflictSettings.decode(ConflictSettings.encode(4)));}
}
