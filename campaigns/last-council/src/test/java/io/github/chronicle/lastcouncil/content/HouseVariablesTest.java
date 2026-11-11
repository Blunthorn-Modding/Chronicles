package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.core.lore.NumberRange;
import io.github.chronicle.lastcouncil.house.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HouseVariablesTest {
    private static final UUID A=HouseCastTest.A,B=HouseCastTest.B,C=HouseCastTest.C;
    private static HouseRoster pair(){return new HouseRoster(7,Map.of(A,new HouseAssignment(A,"c:a",true,false),B,new HouseAssignment(B,"c:b",true,false)));}
    private static CouncilContent.SecretTemplate secret(String id,String text,Set<String> scope,Map<String,NumberRange> numbers){return new CouncilContent.SecretTemplate(id,1,text,Map.of(),numbers,scope);}
    @Test void everySecretIsAssignedOnceAndRestrictedHousesStillGetTheirOnlyOption(){
        var houses=HouseCastTest.content(0).houses();
        var shared=new CouncilContent.SecretTemplate("shared",1_000_000,"Shared ${house.own}",Map.of(),Set.of("c:a","c:b"));
        var onlyA=secret("only-a","Only A",Set.of("c:a"),Map.of());
        var content=new CouncilContent(houses,List.of(shared,onlyA),List.of(),1,0,0);
        for(int seed=0;seed<50;seed++){var cast=HouseCastGenerator.generate(content,pair(),seed);assertEquals(List.of("Only A"),cast.papers().get(A).secrets());assertEquals(List.of("Shared b"),cast.papers().get(B).secrets());assertEquals(Set.of("shared","only-a"),cast.usedSecretIds());}
        var impossible=new CouncilContent(houses,List.of(shared),List.of(),1,0,0);
        var failure=assertThrows(IllegalArgumentException.class,()->HouseCastGenerator.validateAssignments(impossible,pair()));assertTrue(failure.getMessage().contains("unique"));
    }
    @Test void numericAndHouseVariablesResolveOnceAndEveryKnowledgeCopyMatches(){
        var houses=HouseCastTest.content(0).houses();var scope=houses.keySet();
        var wheat=secret("wheat","WHEAT:${house.own}|${house.own}|${house.other}|${house.other}|${number.capacity}|${number.capacity}",scope,Map.of("capacity",new NumberRange(2,5)));
        var content=new CouncilContent(houses,List.of(wheat,secret("second","Second ${house.own}",scope,Map.of()),secret("third","Third ${house.own}",scope,Map.of())),List.of(),1,0,100);
        Set<Integer> observed=new HashSet<>();
        for(int seed=0;seed<100;seed++){
            var cast=HouseCastGenerator.generate(content,HouseCastTest.roster(),seed);assertEquals(cast,HouseCastGenerator.generate(content,HouseCastTest.roster(),seed));
            var owner=cast.papers().entrySet().stream().filter(e->e.getValue().secrets().getFirst().startsWith("WHEAT:")).findFirst().orElseThrow();String body=owner.getValue().secrets().getFirst();var fields=body.substring(6).split("\\|");
            assertEquals(fields[0],fields[1]);assertEquals(houses.get(owner.getValue().houseId()).title(),fields[0]);assertEquals(fields[2],fields[3]);assertNotEquals(fields[0],fields[2]);assertEquals(fields[4],fields[5]);int number=Integer.parseInt(fields[4]);assertTrue(number>=2&&number<=5);observed.add(number);
            for(var entry:cast.papers().entrySet())if(!entry.getKey().equals(owner.getKey()))assertTrue(entry.getValue().knownSecrets().contains(new HouseCast.KnownSecret(owner.getValue().houseId(),body)));
            assertEquals(3,cast.usedSecretIds().size());
        }
        assertEquals(Set.of(2,3,4,5),observed);
    }
    @Test void equallyNamedNumbersAreLocalToTheirSecret(){
        var content=new CouncilContent(HouseCastTest.content(0).houses(),List.of(secret("a","${number.capacity}",Set.of("c:a"),Map.of("capacity",new NumberRange(1,1))),secret("b","${number.capacity}",Set.of("c:b"),Map.of("capacity",new NumberRange(7,7)))),List.of(),1,0,0);
        var cast=HouseCastGenerator.generate(content,pair(),12);assertEquals(List.of("1"),cast.papers().get(A).secrets());assertEquals(List.of("7"),cast.papers().get(B).secrets());
    }
    @Test void legacyFixedValuesRemainLocalWhenDifferentSecretsReusedTheSameKey(){
        var house=HouseCastTest.house("a");var scope=Set.of(house.id());var definitions=List.of(new CouncilContent.SecretTemplate("one",1,"One ${fact.value1}",Map.of("fact.value1","A"),scope),new CouncilContent.SecretTemplate("two",1,"Two ${fact.value1}",Map.of("fact.value1","B"),scope));
        var content=new CouncilContent(Map.of(house.id(),house),definitions,List.of(),2,0,0);var roster=new HouseRoster(1,Map.of(A,new HouseAssignment(A,house.id(),true,false)));
        assertDoesNotThrow(()->HouseCastGenerator.validateAssignments(content,roster));assertEquals(List.of("One A","Two B"),HouseCastGenerator.generate(content,roster,1).papers().get(A).secrets());
    }
    @Test void legacyGoalDependenciesFindTheFeasibleProviderRegardlessOfItsWeight(){
        var house=HouseCastTest.house("a");var scope=Set.of(house.id());var provider=new CouncilContent.SecretTemplate("provider",1,"Provider ${fact.x}",Map.of("fact.x","A"),scope);var empty=new CouncilContent.SecretTemplate("empty",1_000_000,"Empty",Map.of(),scope);
        var content=new CouncilContent(Map.of(house.id(),house),List.of(provider,empty),List.of(new CouncilContent.GoalTemplate("goal",1,"Use ${fact.x}",scope)),1,1,0);var roster=new HouseRoster(1,Map.of(A,new HouseAssignment(A,house.id(),true,false)));
        assertDoesNotThrow(()->HouseCastGenerator.validateAssignments(content,roster));for(int seed=0;seed<30;seed++){var cast=HouseCastGenerator.generate(content,roster,seed);assertEquals(Set.of("provider"),cast.usedSecretIds());assertEquals(List.of("Use A"),cast.papers().get(A).goals());}
        var invalid=new CouncilContent(content.houses(),List.of(empty),content.goals(),1,1,0);assertTrue(assertThrows(IllegalArgumentException.class,()->HouseCastGenerator.validateAssignments(invalid,roster)).getMessage().contains("resolve"));
    }
    @Test void lateLegacyGoalDependenciesUseTheAvailableProviderWithoutChangingOldPapers(){
        var a=HouseCastTest.house("a");var b=HouseCastTest.house("b");var scope=Set.of(a.id());
        var provider=new CouncilContent.SecretTemplate("provider",1,"Provider ${fact.x}",Map.of("fact.x","A"),scope);var empty=new CouncilContent.SecretTemplate("empty",1_000_000,"Empty",Map.of(),scope);
        var content=new CouncilContent(Map.of(a.id(),a,b.id(),b),List.of(provider,empty,secret("old","Old",Set.of(b.id()),Map.of())),List.of(new CouncilContent.GoalTemplate("goal",1,"Use ${fact.x}",scope),new CouncilContent.GoalTemplate("old-goal",1,"Old goal",Set.of(b.id()))),1,1,0);
        var before=new HouseRoster(1,Map.of(B,new HouseAssignment(B,b.id(),true,false)));var old=HouseCastGenerator.generate(content,before,1);var next=new HouseRoster(2,Map.of(B,before.assignments().get(B),A,new HouseAssignment(A,a.id(),true,false)));
        var extended=HouseCastGenerator.append(content,before,old,next,A,3);assertEquals(old.papers().get(B),extended.papers().get(B));assertEquals(List.of("Use A"),extended.papers().get(A).goals());assertEquals(Set.of("old","provider"),extended.usedSecretIds());
    }
    @Test void compatibilitySearchPrunesLargeLegacyPoolsWithRequiredProviders(){
        var house=HouseCastTest.house("a");var scope=Set.of(house.id());List<CouncilContent.SecretTemplate> secrets=new ArrayList<>();
        for(int i=0;i<34;i++)secrets.add(new CouncilContent.SecretTemplate("junk"+i,1_000_000,"Junk "+i,Map.of(),scope));
        for(String key:List.of("x","y","z"))secrets.add(new CouncilContent.SecretTemplate("provider"+key,1,"Provider "+key,Map.of("fact."+key,key),scope));
        var content=new CouncilContent(Map.of(house.id(),house),secrets,List.of(new CouncilContent.GoalTemplate("goal",1,"${fact.x}/${fact.y}/${fact.z}",scope)),4,1,0);var roster=new HouseRoster(1,Map.of(A,new HouseAssignment(A,house.id(),true,false)));
        assertDoesNotThrow(()->HouseCastGenerator.validateAssignments(content,roster));for(int seed=0;seed<3;seed++){var cast=HouseCastGenerator.generate(content,roster,seed);assertTrue(cast.usedSecretIds().containsAll(Set.of("providerx","providery","providerz")));assertEquals(List.of("x/y/z"),cast.papers().get(A).goals());}
    }
    @Test void lateEnrollmentAndRestartPreserveConsumedIds(@TempDir Path directory)throws Exception{
        var content=new CouncilContent(HouseCastTest.content(0).houses(),List.of(secret("shared","Shared ${house.own}",Set.of("c:a","c:b","c:c"),Map.of()),secret("ab","AB ${house.own}",Set.of("c:a","c:b"),Map.of()),secret("c","New ${number.n}",Set.of("c:c"),Map.of("n",new NumberRange(2,5)))),List.of(),1,0,100);
        var initial=HouseCastGenerator.generate(content,pair(),12);var next=new HouseRoster(8,HouseCastTest.roster().assignments());var extended=HouseCastGenerator.append(content,pair(),initial,next,C,44);
        assertEquals(3,extended.usedSecretIds().size());assertEquals(initial.papers().get(A),extended.papers().get(A));assertEquals(initial.papers().get(B),extended.papers().get(B));assertTrue(extended.papers().get(C).secrets().getFirst().startsWith("New "));
        var store=new HouseCastStore(directory.resolve("cast.json"));assertNull(store.load());store.save(extended);assertEquals(extended,new HouseCastStore(directory.resolve("cast.json")).load());
    }
    @Test void legacyCastEncodingStaysUnchangedAndLateDrawReservesPossibleOldSecrets()throws Exception{
        var houses=HouseCastTest.content(0).houses();var content=new CouncilContent(houses,List.of(secret("shared","Shared",houses.keySet(),Map.of()),secret("ab","AB",Set.of("c:a","c:b"),Map.of()),secret("c","C",Set.of("c:c"),Map.of())),List.of(),1,0,0);
        var generated=HouseCastGenerator.generate(content,pair(),2);var legacy=new HouseCast(generated.castId(),generated.rosterRevision(),generated.seed(),generated.papers());byte[] bytes=HouseCastStore.encode(legacy);assertEquals(legacy,HouseCastStore.decode(bytes));assertTrue(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).contains("\"schema\":1"));
        var next=new HouseRoster(8,HouseCastTest.roster().assignments());var extended=HouseCastGenerator.append(content,pair(),legacy,next,C,3);assertEquals(Set.of("shared","ab","c"),extended.usedSecretIds());assertEquals(legacy.papers().get(A),extended.papers().get(A));assertEquals(List.of("C"),extended.papers().get(C).secrets());
    }
}
