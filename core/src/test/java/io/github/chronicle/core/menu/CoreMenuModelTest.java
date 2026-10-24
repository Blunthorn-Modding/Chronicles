package io.github.chronicle.core.menu;

import com.google.gson.Gson;
import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class CoreMenuModelTest {
    private final Gson gson=new Gson();

    @Test void clockAndFeedbackDoNotRebuildControlsButAuthorityAndContentDo() {
        var a=view(true,"","10s","",List.of(),null);
        var b=view(true,"","11s","Saved",List.of(),null);
        assertTrue(a.sameControls(b));
        var changed=view(true,"Operation pending","11s","",List.of(),null);
        assertFalse(a.sameControls(changed));
        var entries=List.of(new Entry("test:site","Site","Region","REGION","",1));
        assertFalse(a.sameControls(view(true,"","10s","",entries,null)));
    }

    @Test void ordinaryPlayerEnvelopeRejectsPrivateStateAndControls() {
        var player=new Entry("player","My account","Own account","PLAYER","",0);
        var own=new Detail("player","My account","Participation",List.of(),List.of(new Control(Action.CARD_OPEN,"My card",true,"",false,"")),null,null,null);
        assertDoesNotThrow(()->publicView("","",List.of(player),own));
        assertThrows(IllegalArgumentException.class,()->publicView("Inspect config/chronicle/recovery.json","",List.of(player),own));
        assertThrows(IllegalArgumentException.class,()->publicView("","123s",List.of(player),own));
        assertThrows(IllegalArgumentException.class,()->publicView("","",List.of(player,new Entry("someone_else","Other account","","PLAYER","",0)),own));
        var management=new Detail("player","Account","",List.of(),List.of(new Control(Action.EDIT_ON,"Edit",true,"",false,"")),null,null,null);
        assertThrows(IllegalArgumentException.class,()->publicView("","",List.of(player),management));
    }

    @Test void boundedSnapshotRoundtripRetainsWholeLootAndComponents() {
        var entries=IntStream.range(0,LootType.MAX_ENTRIES).mapToObj(i->new LootEntry("minecraft:bread",i+1,1,2,"{\"minecraft:custom_name\":\"Keepsake\"}")).toList();
        var type=new LootType("test:cache","Cache","Server table",3,2.5,entries);
        var detail=new Detail(type.id(),type.label(),type.description(),List.of(),List.of(),type,null,null);
        var original=view(true,"","","",List.of(),detail);
        var decoded=gson.fromJson(gson.toJson(original),View.class);
        assertEquals(original,decoded);assertEquals(27,decoded.detail().loot().entries().size());
        assertTrue(decoded.detail().loot().entries().getLast().componentsJson().contains("Keepsake"));
    }

    @Test void invalidAndOversizedIncomingViewsFailAtConstruction() {
        assertThrows(IllegalArgumentException.class,()->new Entry("x".repeat(161),"x","","POINT","",0));
        var tooMany=IntStream.range(0,13).mapToObj(i->new Entry("p"+i,"p","","POINT","",0)).toList();
        assertThrows(IllegalArgumentException.class,()->view(true,"","","",tooMany,null));
        assertThrows(RuntimeException.class,()->gson.fromJson("{\"token\":0}",View.class));
    }

    @Test void typedBlockPolicyIsBoundedPrivateAndPartOfControlIdentity() {
        var type = new BlockLimitType("test:forge","test:forge","Forge","Station",1,2,false);
        var form = new BlockLimitsForm(4,true,"",true,"","test:forge",List.of(type));
        var original = new View(1,Page.SETUP,0,1,"POINT","limit:test:forge","PAUSED",1,true,true,true,"","","",true,List.of(),null,null,false,form);
        assertEquals(original,gson.fromJson(gson.toJson(original),View.class));
        assertEquals(type,form.selected());
        var without = new View(1,Page.SETUP,0,1,"POINT","limit:test:forge","PAUSED",1,true,true,true,"","","",true,List.of(),null,null,false);
        assertFalse(original.sameControls(without));
        assertThrows(IllegalArgumentException.class,()->new View(1,Page.PLAYERS,0,1,"POINT","player","PAUSED",1,false,false,false,"","","",true,List.of(),null,null,false,form));
        assertThrows(IllegalArgumentException.class,()->new BlockLimitType("test:forge","test:forge","Forge","",-1,2,false));
        assertThrows(IllegalArgumentException.class,()->new BlockLimitsForm(4,true,"",true,"","missing:type",List.of(type)));
        assertThrows(IllegalArgumentException.class,()->new BlockLimitsForm(4,true,"",true,"","",List.of(type,type)));
        var tooMany = IntStream.range(0,13).mapToObj(i->new BlockLimitType("test:t"+i,"test:t"+i,"Type "+i,"",0,1,false)).toList();
        assertThrows(IllegalArgumentException.class,()->new BlockLimitsForm(4,true,"",false,"off","",tooMany));
        assertThrows(IllegalArgumentException.class,()->new BlockLimitsForm(4,false,"Unavailable",true,"","",List.of()));
    }

    private static View view(boolean op,String hold,String clock,String message,List<Entry> entries,Detail detail) {
        return new View(1,Page.LOOT_TABLES,0,1,"POINT","test:cache","PAUSED",2,false,true,op,hold,clock,message,true,entries,detail,null,false);
    }
    private static View publicView(String hold,String clock,List<Entry> entries,Detail detail) {
        return new View(1,Page.PLAYERS,0,1,"POINT","player","PAUSED",2,false,false,false,hold,clock,"",true,entries,detail,null,false);
    }
}
