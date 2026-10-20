package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyOrganicTest {
    @TempDir Path dir;
    @Test void floorAndWallGrowthBranchInsteadOfPaintingLongAxisLines()throws Exception {
        for(int variant=0;variant<8;variant++)checkPatch(variant);
    }
    private void checkPatch(int variant)throws Exception {
        boolean wall=variant%2==1;
        Pos heart=new Pos(0,0,-1),source=new Pos(0,0,0);
        var w=new EcologySurfaceTest.Cube(){
            {blocks.clear();blocks.put(heart,new State("minecraft:reinforced_deepslate",Map.of()));blocks.put(source,SCULK);}
            @Override public State get(Pos p){return blocks.getOrDefault(p,(wall?p.x():p.y())<=0?EcologySurfaceTest.STONE:AIR);}
        };
        try(var j=new EcologyJournal(dir.resolve("organic-"+variant+".jsonl"))){
            var pins=variant==0?EcologySurfaceTest.PINS:new EcologyEngine.Setup("city_"+variant,1,"heart",1,"minecraft:overworld");
            var e=new EcologyEngine(j);e.configure(pins,new EcologyEngine.Area(heart,source),heart);
            for(int n=0;n<10&&!e.view().indexed();n++)e.index(w);e.mode(true);e.speed(1600);
            for(int tick=0;tick<4000&&e.view().owned()<49;tick++){e.beginTick();e.tick(w,true);}
            var surface=e.cells().stream().filter(c->(wall?c.position().x():c.position().y())==0).map(EcologyEngine.Cell::position).toList();
            int maxX=surface.stream().mapToInt(p->Math.abs(wall?p.y():p.x())).max().orElseThrow();
            int maxZ=surface.stream().mapToInt(p->Math.abs(p.z())).max().orElseThrow();
            System.out.println("setup="+variant+", wall="+wall+", early surface extent="+maxX+","+maxZ);
            assertTrue(surface.size()>=45,"enough early surface growth to judge its shape");
            assertTrue(Math.max(maxX,maxZ)<=10,"early growth must branch locally rather than complete a long straight row");
            assertTrue(Math.min(maxX,maxZ)>=3,"early growth should expand across both surface axes");
        }
    }
}
