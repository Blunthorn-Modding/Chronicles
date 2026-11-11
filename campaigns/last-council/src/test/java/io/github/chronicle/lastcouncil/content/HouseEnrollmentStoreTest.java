package io.github.chronicle.lastcouncil.content;

import com.google.gson.JsonParser;
import io.github.chronicle.lastcouncil.house.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HouseEnrollmentStoreTest {
    private static class Memory implements HouseEnrollmentStore.Storage {
        byte[] bytes;boolean failBefore,failAfter;
        public byte[] load(){return bytes==null?null:bytes.clone();}
        public void save(byte[] value)throws IOException{if(failBefore)throw new IOException("before");bytes=value.clone();if(failAfter)throw new IOException("after");}
        public void verifyUnchanged(){ }
    }
    private static HouseRoster base(){var all=HouseCastTest.roster();return new HouseRoster(7,Map.of(HouseCastTest.A,all.assignments().get(HouseCastTest.A),HouseCastTest.B,all.assignments().get(HouseCastTest.B)));}
    private static HouseRoster next(){return new HouseRoster(8,HouseCastTest.roster().assignments());}
    @Test void atomicallyPublishesRosterAndPapersAcrossRestart()throws Exception{
        var content=HouseCastTest.content(100);var before=base();var cast=HouseCastGenerator.generate(content,before,44);
        var after=HouseCastGenerator.append(content,before,cast,next(),HouseCastTest.C,99);
        Memory memory=new Memory();var store=new HouseEnrollmentStore(memory,before,cast);
        store.append(next(),after,99);
        var restored=new HouseEnrollmentStore(memory,before,cast).snapshot();
        assertEquals(next(),restored.roster());assertEquals(after,restored.cast());
        assertNotNull(restored.cast().usedSecretIds());assertTrue(restored.cast().usedSecretIds().containsAll(cast.usedSecretIds()));
        assertEquals(cast.papers().get(HouseCastTest.A),restored.cast().papers().get(HouseCastTest.A));
        assertThrows(IllegalArgumentException.class,()->store.append(next(),after,100));
    }
    @Test void legacyEntriesRetainUnknownIdsAndFingerprintWhileMetadataSurvivesAppend()throws Exception{
        var before=base();var original=HouseCastTest.content(100);var secrets=new ArrayList<CouncilContent.SecretTemplate>();for(int i=0;i<original.secrets().size();i++){var v=original.secrets().get(i);secrets.add(new CouncilContent.SecretTemplate(v.id(),v.weight(),v.text(),v.facts(),i==2?Set.of("c:c"):Set.of("c:a","c:b")));}var content=new CouncilContent(original.houses(),secrets,original.goals(),1,1,100);var generated=HouseCastGenerator.generate(content,before,44);
        var legacy=new HouseCast(generated.castId(),generated.rosterRevision(),generated.seed(),generated.papers());
        var after=HouseCastGenerator.append(content,before,legacy,next(),HouseCastTest.C,99);
        Memory memory=new Memory();var store=new HouseEnrollmentStore(memory,before,legacy);store.append(next(),after,99);
        var root=JsonParser.parseString(new String(memory.bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        root.addProperty("schema",1);root.getAsJsonArray("entries").get(0).getAsJsonObject().remove("usedSecretIds");
        memory.bytes=root.toString().getBytes(StandardCharsets.UTF_8);
        var restored=new HouseEnrollmentStore(memory,before,legacy).snapshot();
        assertEquals(after.papers(),restored.cast().papers());assertNull(restored.cast().usedSecretIds());
    }
    @Test void rejectsMetadataRegressionAndPreservesCorruptFile()throws Exception{
        var before=base();var content=HouseCastTest.content(100);var cast=HouseCastGenerator.generate(content,before,44);
        var after=HouseCastGenerator.append(content,before,cast,next(),HouseCastTest.C,99);
        Memory memory=new Memory();var store=new HouseEnrollmentStore(memory,before,cast);store.append(next(),after,99);
        var root=JsonParser.parseString(new String(memory.bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        root.getAsJsonArray("entries").get(0).getAsJsonObject().add("usedSecretIds",new com.google.gson.JsonArray());
        memory.bytes=root.toString().getBytes(StandardCharsets.UTF_8);byte[] corrupt=memory.bytes.clone();
        assertThrows(IOException.class,()->new HouseEnrollmentStore(memory,before,cast));assertArrayEquals(corrupt,memory.bytes);
        var bad=new HouseCast(after.castId(),after.rosterRevision(),after.seed(),after.papers(),Set.of());
        Memory clean=new Memory();var fresh=new HouseEnrollmentStore(clean,before,cast);
        assertThrows(IllegalArgumentException.class,()->fresh.append(next(),bad,99));assertNull(clean.bytes);
    }
    @Test void failedWritesNeverExposeHalfAnEnrollmentAndRestartUsesPublishedPapers()throws Exception{
        var before=base();var content=HouseCastTest.content(100);var cast=HouseCastGenerator.generate(content,before,44);
        var after=HouseCastGenerator.append(content,before,cast,next(),HouseCastTest.C,99);
        Memory memory=new Memory();var store=new HouseEnrollmentStore(memory,before,cast);
        memory.failBefore=true;assertThrows(IOException.class,()->store.append(next(),after,99));
        assertEquals(before,store.snapshot().roster());assertEquals(cast,store.snapshot().cast());assertNull(memory.bytes);
        memory.failBefore=false;memory.failAfter=true;assertThrows(IOException.class,()->store.append(next(),after,99));
        assertEquals(before,store.snapshot().roster());assertEquals(cast,store.snapshot().cast());
        var restarted=new HouseEnrollmentStore(memory,before,cast);
        assertEquals(next(),restarted.snapshot().roster());assertEquals(after,restarted.snapshot().cast());
    }
    @Test void rejectsRewrittenPapersAndBaselineChanges()throws Exception{
        var before=base();var content=HouseCastTest.content(0);var cast=HouseCastGenerator.generate(content,before,44);
        var after=HouseCastGenerator.append(content,before,cast,next(),HouseCastTest.C,99);
        Memory memory=new Memory();var store=new HouseEnrollmentStore(memory,before,cast);
        var papers=new HashMap<>(after.papers());var old=papers.get(HouseCastTest.A);
        papers.put(HouseCastTest.A,new HouseCast.HousePapers(old.houseId(),List.of("changed"),old.goals(),old.knownSecrets()));
        var bad=new HouseCast(cast.castId(),8,cast.seed(),papers);
        assertThrows(IllegalArgumentException.class,()->store.append(next(),bad,99));assertNull(memory.bytes);
        store.append(next(),after,99);
        assertThrows(IOException.class,()->new HouseEnrollmentStore(memory,new HouseRoster(9,before.assignments()),new HouseCast(cast.castId(),9,cast.seed(),cast.papers())));
        assertThrows(IOException.class,()->store.verifyBaseline(before,new HouseCast(cast.castId(),7,45,cast.papers())));
    }
    @Test void strictFileRoundTripAndExternalChangesArePreserved(@TempDir Path directory)throws Exception{
        var before=base();var content=HouseCastTest.content(0);var cast=HouseCastGenerator.generate(content,before,44);
        var after=HouseCastGenerator.append(content,before,cast,next(),HouseCastTest.C,99);
        var store=new HouseEnrollmentStore(directory,before,cast);store.append(next(),after,99);
        assertEquals(after,new HouseEnrollmentStore(directory,before,cast).snapshot().cast());
        Path path=directory.resolve("house-enrollments.json");byte[] valid=Files.readAllBytes(path);
        Files.writeString(path,"invalid",StandardCharsets.UTF_8);
        assertThrows(IOException.class,store::verifyUnchanged);
        assertThrows(IOException.class,()->new HouseEnrollmentStore(directory,before,cast));
        assertEquals("invalid",Files.readString(path));
        Files.writeString(path,new String(valid,StandardCharsets.UTF_8).replace("\"candidate\":false","\"candidate\":\"false\""),StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->new HouseEnrollmentStore(directory,before,cast));
    }
}
