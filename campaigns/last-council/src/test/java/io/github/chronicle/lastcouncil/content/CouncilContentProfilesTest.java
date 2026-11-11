package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.lastcouncil.house.HouseDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CouncilContentProfilesTest {
    @TempDir Path config;
    private Path profile() { return config.resolve("profile-content"); }
    private static CouncilContent content() {
        var h=new HouseDefinition("test:house","House","Reputation","Description","Interests",true);
        var secrets=new ArrayList<CouncilContent.SecretTemplate>();
        for(int i=0;i<80;i++) secrets.add(new CouncilContent.SecretTemplate("secret"+i,1,"x".repeat(1000),Map.of("fact.z","value"),Set.of(h.id())));
        return new CouncilContent(Map.of(h.id(),h),secrets,List.of(new CouncilContent.GoalTemplate("goal",2,"Goal",Set.of(h.id()))),2,1,37);
    }
    private static void write(Path file, byte[] bytes) throws IOException { Files.createDirectories(file.getParent()); Files.write(file,bytes); }
    @Test void codecIsDeterministicAndRoundTripsLargeValidContent() throws Exception {
        var value=content(); byte[] encoded=CouncilContentJson.encode(value);
        assertTrue(encoded.length>65536); assertTrue(encoded.length<CouncilContentJson.MAX_BYTES);
        assertArrayEquals(encoded,CouncilContentJson.encode(value)); assertEquals(value,CouncilContentJson.decode(encoded));
    }
    @Test void encodingCannotSilentlyReplaceInvalidUnicodeOrExpandLiteralMarkup() throws Exception {
        var value=new CouncilContent(Map.of(),List.of(),List.of(),0,0,0);
        assertEquals(value,CouncilContentJson.decode(CouncilContentJson.encode(value)));
        var house=new HouseDefinition("test:house","House","","\uD800","",false);
        assertThrows(IOException.class,()->CouncilContentJson.encode(new CouncilContent(Map.of(house.id(),house),List.of(),List.of(),0,0,0)));
        var literal=new HouseDefinition("test:house","House","","<>&\uD83D\uDE00","",false);
        byte[] encoded=CouncilContentJson.encode(new CouncilContent(Map.of(literal.id(),literal),List.of(),List.of(),0,0,0));
        assertTrue(new String(encoded,StandardCharsets.UTF_8).contains("<>&"));
    }
    @Test void contentAtTheInputByteLimitStillExportsWithoutAddingMetadataOverhead() throws Exception {
        var house=new HouseDefinition("test:house","House","","","",false);
        var facts=new TreeMap<String,String>();
        for(int i=0;i<128;i++)facts.put("fact.k"+i,"a".repeat(4096));
        var entries=new ArrayList<CouncilContent.SecretTemplate>();
        for(int i=0;i<4;i++)entries.add(new CouncilContent.SecretTemplate("secret"+i,1,"Secret",facts,Set.of(house.id())));
        var model=new CouncilContent(Map.of(house.id(),house),entries,List.of(),0,0,0);
        var gson=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
        var raw=gson.toJsonTree(model).getAsJsonObject();raw.addProperty("schema",1);raw.addProperty("note","");
        for(var entry:raw.getAsJsonArray("secrets"))entry.getAsJsonObject().remove("numbers");
        int excess=gson.toJson(raw).getBytes(StandardCharsets.UTF_8).length-CouncilContentJson.MAX_BYTES;
        assertTrue(excess>0);
        for(var entry:raw.getAsJsonArray("secrets"))for(var key:new ArrayList<>(entry.getAsJsonObject().getAsJsonObject("facts").keySet())){
            var object=entry.getAsJsonObject().getAsJsonObject("facts");String value=object.get(key).getAsString();
            int trim=Math.min(excess,value.length());object.addProperty(key,value.substring(trim));excess-=trim;
        }
        byte[] source=gson.toJson(raw).getBytes(StandardCharsets.UTF_8);
        assertEquals(CouncilContentJson.MAX_BYTES,source.length);
        var decoded=CouncilContentJson.decode(source);byte[] exported=CouncilContentJson.encode(decoded);
        assertTrue(exported.length<=CouncilContentJson.MAX_BYTES);assertEquals(decoded,CouncilContentJson.decode(exported));
    }

    @Test void legacyAndNumberRangesRoundTripAndMalformedNumbersFail() throws Exception {
        var h=new HouseDefinition("test:house","H\u00e4user \uD83C\uDF3E","","","",false);
        var secret=new CouncilContent.SecretTemplate("s",1,"${number.capacity}",Map.of("fact.legacy","\u03b1"),Map.of("capacity",new io.github.chronicle.core.lore.NumberRange(Integer.MIN_VALUE,Integer.MAX_VALUE)),Set.of(h.id()));
        var value=new CouncilContent(Map.of(h.id(),h),List.of(secret),List.of(),1,0,0);
        byte[] encoded=CouncilContentJson.encode(value); assertEquals(value,CouncilContentJson.decode(encoded));
        var root=com.google.gson.JsonParser.parseString(new String(encoded,StandardCharsets.UTF_8)).getAsJsonObject();
        var range=root.getAsJsonArray("secrets").get(0).getAsJsonObject().getAsJsonObject("numbers").getAsJsonObject("capacity");
        for(String bad:List.of("{\"min\":2,\"max\":1}","{\"min\":1.5,\"max\":3}","{\"min\":\"1\",\"max\":3}","{\"min\":-2147483649,\"max\":3}")) {
            root.getAsJsonArray("secrets").get(0).getAsJsonObject().getAsJsonObject("numbers").add("capacity",com.google.gson.JsonParser.parseString(bad));
            assertThrows(IOException.class,()->CouncilContentJson.decode(root.toString().getBytes(StandardCharsets.UTF_8)));
        }
        root.getAsJsonArray("secrets").get(0).getAsJsonObject().getAsJsonObject("numbers").add("capacity",range);
        root.addProperty("schema",1);root.getAsJsonArray("secrets").get(0).getAsJsonObject().remove("numbers");
        var legacy=CouncilContentJson.decode(root.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals("\u03b1",legacy.secrets().getFirst().facts().get("fact.legacy"));assertTrue(legacy.secrets().getFirst().numbers().isEmpty());
        assertEquals(legacy,CouncilContentJson.decode(CouncilContentJson.encode(legacy)));
        assertThrows(IllegalArgumentException.class,()->new CouncilContent.SecretTemplate("s",1,"x",Map.of(),Map.of("x".repeat(65),new io.github.chronicle.core.lore.NumberRange(0,1)),Set.of(h.id())));
        var manyFacts=new HashMap<String,String>();for(int i=0;i<128;i++)manyFacts.put("fact.x"+i,"");
        assertDoesNotThrow(()->new CouncilContent.SecretTemplate("legacy",1,"x",manyFacts,Set.of(h.id())));
        assertThrows(IllegalArgumentException.class,()->new CouncilContent.SecretTemplate("s",1,"x",manyFacts,Map.of("n",new io.github.chronicle.core.lore.NumberRange(0,1)),Set.of(h.id())));
    }
    @Test void freshSuppliedAndEmptyIgnoreNormalDefaults() throws Exception {
        write(CouncilContentProfiles.file(config),CouncilContentJson.encode(content()));
        CouncilContentProfiles.initialize(profile(),null,false);
        assertEquals(CouncilContentProfiles.empty(),CouncilContentJson.decode(Files.readAllBytes(CouncilContentProfiles.file(profile()))));
        Path other=config.resolve("other/profile-content");
        CouncilContentProfiles.initialize(other,new String(CouncilContentJson.encode(content()),StandardCharsets.UTF_8),false);
        assertEquals(content(),CouncilContentJson.decode(Files.readAllBytes(CouncilContentProfiles.file(other))));
    }
    @Test void legacyPreservesTargetOrCopiesNormalExactly() throws Exception {
        byte[] normal=CouncilContentJson.encode(content()); write(CouncilContentProfiles.file(config),normal);
        byte[] existing=CouncilContentJson.encode(CouncilContentProfiles.empty()); write(CouncilContentProfiles.file(profile()),existing);
        CouncilContentProfiles.verify(profile()); assertArrayEquals(existing,Files.readAllBytes(CouncilContentProfiles.file(profile())));
        Path second=config.resolve("second/profile-content"); write(CouncilContentProfiles.file(second.getParent()),normal);
        CouncilContentProfiles.verify(second); assertArrayEquals(normal,Files.readAllBytes(CouncilContentProfiles.file(second)));
        assertArrayEquals(normal,Files.readAllBytes(CouncilContentProfiles.file(second.getParent())));
    }
    @Test void deletionAndCorruptMarkerRefuse() throws Exception {
        CouncilContentProfiles.initialize(profile(),null,false); Files.delete(CouncilContentProfiles.file(profile()));
        assertThrows(IOException.class,()->CouncilContentProfiles.verify(profile()));
        Path marker=profile().resolve("last-council/houses-profile-v1.marker"); Files.writeString(marker,"bad");
        assertThrows(IOException.class,()->CouncilContentProfiles.verify(profile()));
    }
    @Test void malformedAndInterruptedMigrationPreservesSource() throws Exception {
        byte[] invalid="{broken".getBytes(StandardCharsets.UTF_8); Path source=CouncilContentProfiles.file(config); write(source,invalid);
        assertThrows(IOException.class,()->CouncilContentProfiles.verify(profile())); assertArrayEquals(invalid,Files.readAllBytes(source));
        byte[] valid=CouncilContentJson.encode(content()); write(source,valid);
        write(CouncilContentProfiles.file(profile()),"{broken".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class,()->CouncilContentProfiles.verify(profile())); assertArrayEquals(valid,Files.readAllBytes(source));
        Files.delete(CouncilContentProfiles.file(profile())); CouncilContentProfiles.verify(profile());
        assertArrayEquals(valid,Files.readAllBytes(CouncilContentProfiles.file(profile())));
    }
}
