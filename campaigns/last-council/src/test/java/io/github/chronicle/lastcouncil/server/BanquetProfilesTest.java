package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BanquetProfilesTest {
    @TempDir Path directory;
    @Test void oldProfileGainsDisabledSettingsOnceAndLaterDeletionRefuses()throws Exception{
        BanquetProfiles.verify(directory);
        Path file=directory.resolve("last-council-banquet.json");
        assertEquals(BanquetConfig.defaults(),BanquetSettings.decode(Files.readAllBytes(file)));
        Files.delete(file);assertThrows(IOException.class,()->BanquetProfiles.verify(directory));assertFalse(Files.exists(file));
    }
    @Test void importedProfileKeepsValuesAndInvalidOldSettingsRemainUntouched()throws Exception{
        BanquetConfig imported=new BanquetConfig(true,3,11);
        BanquetProfiles.initialize(directory,new String(BanquetSettings.encode(imported),StandardCharsets.UTF_8),false);
        BanquetProfiles.verify(directory);
        assertEquals(imported,BanquetSettings.decode(Files.readAllBytes(directory.resolve("last-council-banquet.json"))));
        Path old=directory.resolve("old");Files.createDirectory(old);Path settings=old.resolve("last-council-banquet.json");Files.writeString(settings,"invalid");
        assertThrows(IOException.class,()->BanquetProfiles.verify(old));assertEquals("invalid",Files.readString(settings));assertFalse(Files.exists(old.resolve("last-council-banquet.initialized")));
    }
}
