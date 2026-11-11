package io.github.chronicle.quietbelow.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AltarOfferingConfigTest {
    @Test void cooldownDefaultsAndLegacyConfigurationArePreserved(){
        String encoded=AltarOfferingConfig.encode(AltarOfferingConfig.DEFAULT);
        assertEquals(900,AltarOfferingConfig.decode(encoded).cooldownSeconds());
        String legacy=encoded.replace("\"schema\": 2","\"schema\": 1").replaceAll("(?m)\\s*\"cooldownSeconds\": 900,", "");
        assertEquals(900,AltarOfferingConfig.decode(legacy).cooldownSeconds());
        assertThrows(IllegalArgumentException.class,()->AltarOfferingConfig.decode(encoded.replace("\"cooldownSeconds\": 900","\"cooldownSeconds\": -1")));
    }
    @Test void defaultsRoundTripAndExposeAllFourIndependentSwitches(){String encoded=AltarOfferingConfig.encode(AltarOfferingConfig.DEFAULT);assertEquals(AltarOfferingConfig.DEFAULT,AltarOfferingConfig.decode(encoded));assertTrue(AltarOfferingConfig.DEFAULT.emergency().enabled());assertEquals(25,AltarOfferingConfig.DEFAULT.repair().percent());assertEquals(300,AltarOfferingConfig.DEFAULT.sight().seconds());assertEquals(150,AltarOfferingConfig.DEFAULT.harvest().growthPercent());}
    @Test void rejectsUnknownDuplicateTrailingWrongTypesAndUnsafeBounds(){String encoded=AltarOfferingConfig.encode(AltarOfferingConfig.DEFAULT);assertThrows(IllegalArgumentException.class,()->AltarOfferingConfig.decode(encoded.replaceFirst("\\{","{\"unknown\":true,")));assertThrows(IllegalArgumentException.class,()->AltarOfferingConfig.decode(encoded.replaceFirst("\"schema\": 2","\"schema\": 2, \"schema\": 2")));assertThrows(IllegalArgumentException.class,()->AltarOfferingConfig.decode(encoded+"{}"));assertThrows(IllegalArgumentException.class,()->AltarOfferingConfig.decode(encoded.replace("\"levels\": 3","\"levels\": false")));assertThrows(IllegalArgumentException.class,()->new AltarOfferingConfig.Harvest(true,16,300,8,200,201));}
    @Test void invalidReloadDisablesNewTradesInsteadOfKeepingPriorTerms(){String valid=AltarOfferingConfig.encode(AltarOfferingConfig.DEFAULT);try{AltarOfferingConfig.replaceForTest(valid);assertEquals(AltarOfferingConfig.DEFAULT,AltarOfferingConfig.get());assertThrows(IllegalArgumentException.class,()->AltarOfferingConfig.replaceForTest(valid.replace("\"levels\": 3","\"levels\": false")));assertThrows(IllegalStateException.class,AltarOfferingConfig::get);}finally{AltarOfferingConfig.replaceForTest(valid);}}
}
