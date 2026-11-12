package io.github.chronicle.lastcouncil.network;

import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.lastcouncil.network.BanquetPayloads.*;

class BanquetPayloadsTest {
    @Test void privateLogRequiresBothLogSceneAndChancellorProjection(){
        UUID id=UUID.randomUUID();var entry=new LogEntry("Private provenance");
        assertThrows(IllegalArgumentException.class,()->new View(id,false,1,BanquetConfig.defaults(),false,true,true,List.of(),List.of(entry),"",false,false,id));
        assertThrows(IllegalArgumentException.class,()->new View(id,false,1,BanquetConfig.defaults(),false,true,false,List.of(),List.of(entry),"",true,false,id));
        assertEquals(1,new View(id,false,1,BanquetConfig.defaults(),false,false,true,List.of(),List.of(entry),"",true,false,id).log().size());
    }
    @Test void giftQuantitiesAndProjectionSizesAreBounded(){
        UUID id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new Request(id,Action.GIFT,0,null,id,-1,0,0));
        assertThrows(IllegalArgumentException.class,()->new Request(id,Action.GIFT,0,null,id,17,0,0));
        assertThrows(IllegalArgumentException.class,()->new Request(id,Action.GIFT,0,null,id,16,16,16));
        assertThrows(IllegalArgumentException.class,()->new View(id,false,1,BanquetConfig.defaults(),false,false,true,List.of(),Collections.nCopies(65,new LogEntry("x")),"",true,false,id));
        assertThrows(IllegalArgumentException.class,()->new PlayerChoice(id,"x".repeat(161)));
        assertThrows(IllegalArgumentException.class,()->new LogEntry("x".repeat(513)));
    }
}
