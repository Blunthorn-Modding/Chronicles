package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static io.github.chronicle.quietbelow.ecology.EcologyVitality.Mode.*;
import static io.github.chronicle.quietbelow.ecology.EcologyAppearance.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyAppearanceTest {
    @TempDir Path dir;

    @Test void colorsDistinguishZeroReadinessHealthyFeedingAndUnknown() {
        assertEquals(EXHAUSTED,stage(0,false,DORMANT));
        assertEquals(FADING,stage(1,true,LIT));
        assertEquals(FADING,stage(EcologyVitality.READY-1,true,LIT));
        assertEquals(DULL,stage(EcologyVitality.READY,true,LIT));
        assertEquals(DULL,stage(EcologyVitality.FULL*3/4-1,true,SHADED));
        assertEquals(HEALTHY,stage(EcologyVitality.FULL*3/4,true,SHADED));
        assertEquals(DULL,stage(EcologyVitality.FULL,false,DORMANT));
        assertEquals(DULL,stage(EcologyVitality.FULL,true,SEALED));
        assertEquals(-1,stage(EcologyVitality.FULL,true,UNKNOWN));
        assertEquals(-1,stage(0,false,null));
        assertThrows(IllegalArgumentException.class,()->stage(-1,true,LIT));
        assertThrows(IllegalArgumentException.class,()->stage(EcologyVitality.FULL+1,true,LIT));
    }

    @Test void cutFadeExhaustionAndRecoveryOnlyProjectVisualState()throws Exception {
        var w=new VisualWorld();Path file=dir.resolve("colors.jsonl");
        try(var j=new EcologyJournal(file)) {
            var e=EcologyVitalityTest.ready(j,w);e.tuneVitality(new EcologyVitality.Rules(1,1,2));ticks(e,w,20,true);
            assertEquals(0,w.visualWrites);assertEquals(HEALTHY,w.color(VisualWorld.END));
            w.blocks.put(VisualWorld.BRIDGE,AIR);e.observed(VisualWorld.BRIDGE,AIR);ticks(e,w,10,true);
            assertEquals(DULL,w.color(VisualWorld.END));assertTrue(e.owns(VisualWorld.END));
            ticks(e,w,1400,true);assertEquals(EXHAUSTED,w.color(VisualWorld.END));assertTrue(w.seen.contains(FADING));
            var original=e.cells().stream().filter(c->c.position().equals(VisualWorld.END)).findFirst().orElseThrow();
            e.flushVitality();int writes=w.visualWrites;long clock=e.vitality().ticks();ticks(e,w,2000,false);
            assertEquals(writes,w.visualWrites);assertEquals(clock,e.vitality().ticks());
            assertEquals(SCULK,w.get(VisualWorld.END));assertEquals(0,w.writes);
            w.blocks.put(VisualWorld.BRIDGE,SCULK);e.noticeContact(VisualWorld.BRIDGE);ticks(e,w,10,true);
            assertTrue(e.rooted(VisualWorld.END));assertEquals(EXHAUSTED,w.color(VisualWorld.END));
            ticks(e,w,500,true);assertEquals(DULL,w.color(VisualWorld.END));assertTrue(e.living(VisualWorld.END));
            ticks(e,w,800,true);assertEquals(HEALTHY,w.color(VisualWorld.END));
            assertEquals(original.before(),e.cells().stream().filter(c->c.position().equals(VisualWorld.END)).findFirst().orElseThrow().before());
            e.flushVitality();assertNull(e.failure());assertEquals(0,w.writes);
        }
        assertFalse(Files.readString(file).contains(PROPERTY),"derived appearance must not rewrite ownership history");
        try(var j=new EcologyJournal(file)) {
            var e=new EcologyEngine(j);e.attach(VisualWorld.PINS,VisualWorld.AREA,VisualWorld.HEART);
            ticks(e,w,20,true);assertEquals(HEALTHY,w.color(VisualWorld.END));assertTrue(e.owns(VisualWorld.END));
        }
    }

    @Test void recoloringIsBoundedSkipsUnchangedCellsAndNeverLoadsTerrain()throws Exception {
        var w=new VisualWorld(){
            @Override public State get(Pos p){reads++;return p.equals(HEART)?HEART_BLOCK:
                p.x()>=0&&p.x()<8&&p.y()>=0&&p.y()<8&&p.z()>=0&&p.z()<8?SCULK:HEART_BLOCK;}
        };
        try(var j=new EcologyJournal(dir.resolve("bounded.jsonl"))) {
            var e=new EcologyEngine(j);e.configure(VisualWorld.PINS,new EcologyEngine.Area(VisualWorld.HEART,new Pos(7,7,7)),VisualWorld.HEART);
            for(int n=0;n<100&&!e.view().indexed();n++)e.index(w);e.mode(true);
            ticks(e,w,200,true);assertEquals(511,w.visualWrites);assertEquals(511,w.colors.size());
            int before=w.visualWrites;ticks(e,w,200,true);assertEquals(before,w.visualWrites,"unchanged colors cause no block updates");
            w.hidden=true;ticks(e,w,100,true);assertEquals(before,w.visualWrites);assertEquals(0,w.writes);assertNull(e.failure());
        }
    }

    static void ticks(EcologyEngine e,VisualWorld w,int n,boolean playing)throws Exception {
        for(int tick=0;tick<n;tick++) {
            w.visualTickWrites=0;w.visualReads=0;e.beginTick();e.tick(w,playing);
            assertTrue(w.visualTickWrites<=WRITES);assertTrue(w.visualReads<=READS);
        }
    }
    static class VisualWorld extends EcologyVitalityTest.Branch {
        final Map<Pos,Integer> colors=new HashMap<>();final Set<Integer> seen=new HashSet<>();
        int visualWrites,visualTickWrites,visualReads;
        int color(Pos p){return colors.getOrDefault(p,HEALTHY);}
        @Override public boolean appearance(Pos p,State expected,int stage) {
            assertTrue(loaded(p));visualReads++;
            if(!get(p).sameOwnedShape(expected)||color(p)==stage)return false;
            colors.put(p,stage);seen.add(stage);visualWrites++;visualTickWrites++;return true;
        }
    }
}
