package io.github.chronicle.quietbelow.content;

import io.github.chronicle.core.sound.BundledSoundCatalog;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuietBelowEventSoundsTest {
    @Test void allEightDeclaredRecordingsArePackagedCreditedAndMatchVorbisHashes()throws Exception{
        try(var stream=getClass().getResourceAsStream("/data/quiet_below/chronicle/event-sounds.json")){
            var catalog=BundledSoundCatalog.read(stream);assertEquals("quiet_below",catalog.namespace());assertEquals(8,catalog.sounds().size());catalog.verify(getClass()::getResourceAsStream);
            try(var credits=getClass().getResourceAsStream("/quiet_below-sound-credits.txt")){String attribution=new String(credits.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(attribution.contains("Arto Koivisto"));assertTrue(attribution.contains("TunePocket"));}assertTrue(catalog.sounds().stream().allMatch(sound->sound.downloadUrl().startsWith("https://cdn.freesound.org/previews/")));
        }
    }
}
