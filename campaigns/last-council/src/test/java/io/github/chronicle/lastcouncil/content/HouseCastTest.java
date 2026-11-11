package io.github.chronicle.lastcouncil.content;

import static org.junit.jupiter.api.Assertions.*;
import io.github.chronicle.lastcouncil.house.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HouseCastTest {
    static final UUID A = new UUID(0,1), B = new UUID(0,2), C = new UUID(0,3);
    static HouseDefinition house(String id) { return new HouseDefinition("c:"+id,id,"","","",false); }
    static HouseRoster roster() { return new HouseRoster(7,Map.of(A,new HouseAssignment(A,"c:a",true,false),B,new HouseAssignment(B,"c:b",true,false),C,new HouseAssignment(C,"c:c",true,false))); }
    static CouncilContent content(int chance) {
        var houses=Map.of("c:a",house("a"),"c:b",house("b"),"c:c",house("c"));
        var scope=houses.keySet();
        return new CouncilContent(houses,List.of(new CouncilContent.SecretTemplate("one",1,"${house.title} holds ${fact.capacity}",Map.of("fact.capacity","three"),scope),new CouncilContent.SecretTemplate("two",1,"${house.title} holds four",Map.of(),scope),new CouncilContent.SecretTemplate("three",1,"${house.title} holds five",Map.of(),scope)),List.of(new CouncilContent.GoalTemplate("goal",1,"Help ${other.title}",scope)),1,1,chance);
    }
    @Test void deterministicAndKnowledgeMatchesTarget() {
        HouseCast cast=HouseCastGenerator.generate(content(100),roster(),44);
        assertEquals(cast,HouseCastGenerator.generate(content(100),roster(),44));
        assertNotEquals(cast,HouseCastGenerator.generate(content(100),roster(),45));
        for (var p:cast.papers().values()) {
            assertEquals(2,p.knownSecrets().size());
            for (var k:p.knownSecrets()) {
                assertNotEquals(p.houseId(),k.targetHouseId());
                var target=cast.papers().values().stream().filter(x->x.houseId().equals(k.targetHouseId())).findFirst().orElseThrow();
                assertTrue(target.secrets().contains(k.body()));
            }
        }
        assertTrue(HouseCastGenerator.generate(content(0),roster(),44).papers().values().stream().allMatch(p->p.knownSecrets().isEmpty()));
        HouseRoster pair = new HouseRoster(7, Map.of(A, roster().assignments().get(A), B, roster().assignments().get(B)));
        var paired = HouseCastGenerator.generate(content(100), pair, 44);
        assertTrue(paired.papers().values().stream().allMatch(p -> p.knownSecrets().size() == 1));
        assertTrue(paired.papers().values().stream().flatMap(p -> p.knownSecrets().stream()).noneMatch(k -> k.targetHouseId().equals("c:c")));
    }
    @Test void rejectsImpossibleAndUnknownBindings() {
        var c=content(0);
        var impossible=new CouncilContent(c.houses(),c.secrets(),c.goals(),3,1,0);
        assertThrows(IllegalArgumentException.class,()->HouseCastGenerator.generate(impossible,roster(),1));
        var bad=new CouncilContent(c.houses(),List.of(new CouncilContent.SecretTemplate("bad",1,"${fact.missing}",Map.of(),c.houses().keySet())),c.goals(),1,1,0);
        assertThrows(IllegalArgumentException.class,()->HouseCastGenerator.generate(bad,roster(),1));
    }
    @Test void restartAndCorruptPreservation(@TempDir Path dir) throws Exception {
        Path path=dir.resolve("cast.json"); HouseCastStore store=new HouseCastStore(path);
        assertNull(store.load()); HouseCast cast=HouseCastGenerator.generate(content(100),roster(),44); store.save(cast);
        assertEquals(cast,new HouseCastStore(path).load());
        Files.writeString(path,"{\"schema\":1,\"schema\":1}");
        HouseCastStore corrupt=new HouseCastStore(path); assertThrows(java.io.IOException.class,corrupt::load);
        assertThrows(java.io.IOException.class,()->corrupt.save(cast));
        assertEquals("{\"schema\":1,\"schema\":1}",Files.readString(path));
        assertThrows(java.io.IOException.class,()->store.save(cast));
    }
    @Test void sampleContentLoadsStrictly() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/last_council/chronicle/houses.json")) {
            assertNotNull(in);
            var sample = CouncilContentJson.decode(in.readAllBytes());
            assertEquals(8, sample.houses().size());
            assertEquals(16, sample.secrets().size());
            assertEquals(16, sample.goals().size());
        }
        assertThrows(java.io.IOException.class, () -> CouncilContentJson.decode("{\"note\":\"a\",\"note\":\"b\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(java.io.IOException.class, () -> CouncilContentJson.decode(new byte[]{(byte) 0xc3, 0x28}));
    }
    @Test void independentKnowledgeRollsCanExposeToNobodyOneOrSeveral() {
        var observed=new HashSet<Integer>();
        for(long seed=0;seed<100;seed++) {
            var cast=HouseCastGenerator.generate(content(50),roster(),seed);
            for(var target:cast.papers().values()) {
                long count=cast.papers().values().stream().flatMap(p->p.knownSecrets().stream()).filter(k->k.targetHouseId().equals(target.houseId())).count();
                observed.add((int)count);
            }
        }
        assertEquals(Set.of(0,1,2),observed);
    }
    @Test void houseGoalsCanUseTheirOwnAssignedFacts() {
        var base=content(0);
        List<CouncilContent.SecretTemplate> secrets=new ArrayList<>();for(String house:base.houses().keySet())secrets.add(new CouncilContent.SecretTemplate(house,1,"${house.title} holds ${fact.capacity}",Map.of("fact.capacity","three"),Set.of(house)));
        var c=new CouncilContent(base.houses(),secrets,List.of(new CouncilContent.GoalTemplate("goal",1,"Promise ${fact.capacity} places",base.houses().keySet())),1,1,0);
        var cast=HouseCastGenerator.generate(c,roster(),11);
        assertTrue(cast.papers().values().stream().allMatch(p->p.goals().equals(List.of("Promise three places"))));
    }
}
