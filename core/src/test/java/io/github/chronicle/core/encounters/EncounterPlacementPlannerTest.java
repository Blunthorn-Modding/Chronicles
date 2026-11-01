package io.github.chronicle.core.encounters;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncounterPlacementPlannerTest {
    @Test void ordinaryPointAndSingleLayerRegionKeepLegacyOrder(){AuthoringSite.Location point=new AuthoringSite.Location("point","minecraft:overworld",1,10.5,64,10.5,0,0);assertEquals(List.of(new EncounterPlacementPlanner.Position(10.5,64,10.5),new EncounterPlacementPlanner.Position(11.5,64,10.5),new EncounterPlacementPlanner.Position(11.5,64,11.5)),EncounterPlacementPlanner.positions(point,3,.6,1.95));AuthoringSite.Region region=new AuthoringSite.Region("region","minecraft:overworld",1,new BlockPosition(20,64,20),new BlockPosition(22,64,21));assertEquals(List.of(new EncounterPlacementPlanner.Position(20.5,64,20.5),new EncounterPlacementPlanner.Position(21.5,64,20.5),new EncounterPlacementPlanner.Position(22.5,64,20.5)),EncounterPlacementPlanner.positions(region,3,.6,1.95));}
    @Test void wideMobsUseDimensionAwarePointRingsAndRegionSlots(){AuthoringSite.Location point=new AuthoringSite.Location("point","minecraft:overworld",1,10.5,64,10.5,0,0);assertEquals(2.0,EncounterPlacementPlanner.positions(point,2,1.95,2.2).get(1).x()-10.5);AuthoringSite.Region region=new AuthoringSite.Region("region","minecraft:overworld",1,new BlockPosition(0,64,0),new BlockPosition(3,64,1));var values=EncounterPlacementPlanner.positions(region,2,1.95,2.2);assertEquals(2,values.size());assertEquals(2.0,values.get(1).x()-values.get(0).x());}
    @Test void insufficientRegionRefusesInsteadOfWrappingDuplicateAnchors(){AuthoringSite.Region region=new AuthoringSite.Region("region","minecraft:overworld",1,new BlockPosition(0,64,0),new BlockPosition(0,64,0));IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->EncounterPlacementPlanner.positions(region,2,.6,1.95));assertTrue(failure.getMessage().contains("non-overlapping"));assertThrows(IllegalArgumentException.class,()->EncounterPlacementPlanner.positions(region,129,.6,1));assertThrows(IllegalArgumentException.class,()->EncounterPlacementPlanner.positions(region,1,65,1));}
}
