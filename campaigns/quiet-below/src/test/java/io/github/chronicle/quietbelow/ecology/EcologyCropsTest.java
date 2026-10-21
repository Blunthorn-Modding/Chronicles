package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyCropsTest {
    @TempDir Path dir;
    private static final Pos SOURCE=new Pos(0,0,0),CROP=new Pos(2,1,0),CONTROL=new Pos(8,1,0);
    static class Network implements EcologyCrops.Network {
        Set<Pos> owned=new LinkedHashSet<>(List.of(SOURCE));boolean available=true,live=true;
        public List<Pos> cropSources(){return List.copyOf(owned);}
        public boolean owns(Pos p){return owned.contains(p);}
        public boolean living(Pos p){return live&&owns(p);}
        public boolean cropsAvailable(){return available;}
    }
    static class Field implements EcologyCrops.Terrain {
        Map<Pos,EcologyCrops.Crop> crops=new HashMap<>();boolean loaded=true,eligible=true,conflict;
        int reads,writes;final Set<Pos> changed=new HashSet<>();
        public boolean loaded(Pos p){return loaded;}
        public boolean mayContainCrops(EcologyCrops.Section s){return loaded&&crops.keySet().stream().anyMatch(p->EcologyCrops.Section.at(p).equals(s));}
        public EcologyCrops.Crop crop(Pos p){assertTrue(loaded);reads++;var c=crops.get(p);return c==null?null:new EcologyCrops.Crop(c.id(),c.age(),c.maximum(),eligible);}
        public boolean grow(Pos p,EcologyCrops.Crop expected){writes++;assertTrue(loaded);if(conflict)return false;assertEquals(expected,crop(p));crops.put(p,new EcologyCrops.Crop(expected.id(),expected.age()+1,expected.maximum(),eligible));changed.add(p);return true;}
        void plant(Pos p){crops.put(p,new EcologyCrops.Crop("minecraft:wheat",0,7,true));}
        int age(Pos p){return crops.get(p).age();}
    }
    static void ticks(EcologyCrops service,Field field,Network net,int count,boolean play){
        for(int i=0;i<count;i++){
            field.reads=field.writes=0;service.tick(field,net,play);
            assertTrue(service.reads()<=160,"native samples exceed budget");assertTrue(service.writes()<=2);assertTrue(field.writes<=2);
            assertTrue(field.reads<=164,"including adapter rechecks");
        }
    }
    @Test void discoversExistingCropsAndBoostsOneAgeWithoutMultiplyingSources(){
        var n=new Network();for(int x=-2;x<=2;x++)n.owned.add(new Pos(x,0,0));var f=new Field();f.plant(CROP);f.plant(CONTROL);
        var c=new EcologyCrops(EcologyCrops.DEFAULT);ticks(c,f,n,199,true);assertEquals(0,f.age(CROP));
        ticks(c,f,n,401,true);assertTrue(f.age(CROP)>=1&&f.age(CROP)<=2);assertEquals(0,f.age(CONTROL));assertNull(c.failure());
    }
    @Test void radiusUsesRealThreeDimensionalDistanceAndLivingOwnership(){
        var n=new Network();assertTrue(EcologyCrops.nearLiving(n,new Pos(4,0,0)));assertFalse(EcologyCrops.nearLiving(n,new Pos(4,1,0)));
        assertTrue(EcologyCrops.nearLiving(n,new Pos(0,-4,0)));assertFalse(EcologyCrops.nearLiving(n,new Pos(0,0,5)));
        n.live=false;assertFalse(EcologyCrops.nearLiving(n,SOURCE));n.live=true;n.owned.clear();assertFalse(EcologyCrops.nearLiving(n,SOURCE));
    }
    @Test void pauseUnknownDisableAndUnloadNeverChargeOrCatchUp(){
        var n=new Network();var f=new Field();f.plant(CROP);var c=new EcologyCrops(EcologyCrops.DEFAULT);
        c.source(SOURCE);c.notice(CROP);ticks(c,f,n,100,true);long time=c.ticks();
        ticks(c,f,n,1000,false);assertEquals(time,c.ticks());n.available=false;ticks(c,f,n,1000,true);assertEquals(time,c.ticks());
        n.available=true;f.loaded=false;ticks(c,f,n,1000,true);assertEquals(0,f.age(CROP));
        f.loaded=true;ticks(c,f,n,100,true);assertEquals(0,f.age(CROP));ticks(c,f,n,130,true);assertEquals(1,f.age(CROP));
        var disabled=new EcologyCrops(new EcologyCrops.Rules(false,2));ticks(disabled,f,n,1000,true);assertEquals(0,disabled.ticks());assertEquals(1,f.age(CROP));
    }
    @Test void cutWeaknessAndLightStopBonusAndReconnectionWaitsFullInterval(){
        var n=new Network();var f=new Field();f.plant(CROP);var c=new EcologyCrops(new EcologyCrops.Rules(true,2));
        c.source(SOURCE);c.notice(CROP);ticks(c,f,n,30,true);n.live=false;ticks(c,f,n,200,true);assertEquals(0,f.age(CROP));
        n.live=true;ticks(c,f,n,20,true);assertEquals(0,f.age(CROP));ticks(c,f,n,30,true);assertEquals(1,f.age(CROP));
        f.eligible=false;ticks(c,f,n,300,true);assertEquals(1,f.age(CROP));f.eligible=true;ticks(c,f,n,20,true);assertEquals(1,f.age(CROP));ticks(c,f,n,30,true);assertEquals(2,f.age(CROP));
    }
    @Test void restartMaturityRemovalAndWriteConflictRemainBounded(){
        var n=new Network();var f=new Field();f.plant(CROP);var c=new EcologyCrops(new EcologyCrops.Rules(true,2));c.source(SOURCE);c.notice(CROP);
        ticks(c,f,n,30,true);c=new EcologyCrops(c.rules());c.source(SOURCE);c.notice(CROP);ticks(c,f,n,30,true);assertEquals(0,f.age(CROP));
        f.conflict=true;ticks(c,f,n,100,true);assertEquals(0,f.age(CROP));f.conflict=false;ticks(c,f,n,350,true);assertEquals(7,f.age(CROP));ticks(c,f,n,100,true);assertEquals(7,f.age(CROP));
        f.crops.remove(CROP);ticks(c,f,n,100,true);f.plant(CROP);c.notice(CROP);ticks(c,f,n,30,true);assertEquals(0,f.age(CROP));ticks(c,f,n,20,true);assertEquals(1,f.age(CROP));
    }
    @Test void largeFieldHasHardWorkBoundsAndLateSectionsAreNotStarved(){
        var n=new Network();n.owned.clear();var f=new Field();
        for(int x=0;x<8;x++){Pos p=new Pos(x*16,0,0);n.owned.add(p);for(int z=0;z<4;z++)f.plant(p.add(1,1,z));}
        var c=new EcologyCrops(new EcologyCrops.Rules(true,2));ticks(c,f,n,3500,true);
        assertTrue(f.changed.contains(new Pos(113,1,0)),"source rebuild reset starved the final section");assertNull(c.failure());
    }
    @Test void fastReplantingMustWaitANewFullIntervalAndHoldsStopAllWrites(){
        var n=new Network();var f=new Field();f.plant(CROP);var c=new EcologyCrops(new EcologyCrops.Rules(true,2));
        c.source(SOURCE);c.notice(CROP);ticks(c,f,n,35,true);f.plant(CROP);c.planted(CROP);
        ticks(c,f,n,35,true);assertEquals(0,f.age(CROP));ticks(c,f,n,10,true);assertEquals(1,f.age(CROP));
        c.fail("test storage/world hold");long clock=c.ticks();ticks(c,f,n,1000,true);assertEquals(clock,c.ticks());assertEquals(1,f.age(CROP));
        n.owned=Set.of(new Pos(30_000_000,4096,30_000_000));assertDoesNotThrow(()->EcologyCrops.nearLiving(n,new Pos(30_000_000,4096,30_000_000)));
    }
    @Test void malformedRulesAndJournalTransitionsRejectWithoutMutation()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new EcologyCrops.Rules(true,0));
        assertThrows(IllegalArgumentException.class,()->new EcologyCrops.Crop("minecraft:torchflower_crop",0,7,true));
        var w=new EcologyVitalityTest.Branch();Path file=dir.resolve("crops.jsonl");
        try(var j=new EcologyJournal(file)){
            var fresh=new EcologyEngine(j);assertThrows(IllegalArgumentException.class,()->fresh.tuneCrops(new EcologyCrops.Rules(false,2)));assertEquals(0,j.revision());
            var e=EcologyVitalityTest.ready(j,w);long revision=j.revision();e.tuneCrops(new EcologyCrops.Rules(false,2));assertEquals(revision+1,j.revision());
            e.tuneCrops(new EcologyCrops.Rules(false,2));assertEquals(revision+1,j.revision());
        }
        try(var j=new EcologyJournal(file)){assertEquals(new EcologyCrops.Rules(false,2),new EcologyEngine(j).cropRules());}
    }
    @Test void cropCacheCapacityHoldsInsteadOfGrowingWithoutBounds(){
        var n=new Network();n.owned.clear();var f=new Field();f.eligible=false;
        for(int x=0;x<172;x++)for(int z=0;z<48;z++){
            f.plant(new Pos(x,1,z));if(x%4==0&&z%4==0)n.owned.add(new Pos(x,0,z));
        }
        var c=new EcologyCrops(new EcologyCrops.Rules(true,2));ticks(c,f,n,4000,true);
        assertNotNull(c.failure());assertTrue(c.failure().contains("cache capacity"));assertEquals(EcologyCrops.MAX_CROPS,c.cached());
        assertEquals(0,c.bonuses());long clock=c.ticks();ticks(c,f,n,400,true);assertEquals(clock,c.ticks());
    }
    @Test void malformedSavedCropRulesFailClosedAndPreserveEvidence()throws Exception {
        Path file=dir.resolve("invalid-crops.jsonl");
        try(var j=new EcologyJournal(file)){
            EcologyVitalityTest.ready(j,new EcologyVitalityTest.Branch());
            var invalid=new com.google.gson.JsonObject();invalid.addProperty("enabled","true");invalid.addProperty("seconds",10);j.append("CROP_RULES",invalid);
            assertThrows(IllegalArgumentException.class,()->new EcologyEngine(j));
        }
        byte[] evidence=Files.readAllBytes(file);
        try(var j=new EcologyJournal(file)){assertThrows(IllegalArgumentException.class,()->new EcologyEngine(j));}
        assertArrayEquals(evidence,Files.readAllBytes(file));
    }
    @Test void aCustomBorderThroughASectionDoesNotHideExistingCrops(){
        var n=new Network();n.owned=Set.of(new Pos(1,0,0));
        var f=new Field(){@Override public boolean loaded(Pos p){return p.x()>=1&&p.x()<=3;}};f.plant(CROP);
        var c=new EcologyCrops(EcologyCrops.DEFAULT);ticks(c,f,n,450,true);assertTrue(f.age(CROP)>0);
    }
}
