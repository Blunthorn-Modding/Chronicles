package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.lastcouncil.network.ForeignPayloads;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForeignProjectionBudgetTest {
    @Test void maximumCatalogueAndPagedEscapedHistoryFitThePublicFrame(){
        String name="<>&'".repeat(20),description="\u2028&".repeat(2048);
        var rows=new ArrayList<ForeignPayloads.Row>();
        for(int i=0;i<64;i++)rows.add(new ForeignPayloads.Row("last_council:realm_"+new UUID(0,i),name,false));
        var history=Collections.nCopies(32,"&".repeat(160)+": Council voted for peace.");
        var detail=new ForeignPayloads.Detail(rows.getFirst().id(),name,description,false,256,history,true,7,8);
        var view=new ForeignPayloads.View(UUID.randomUUID(),false,1,100,64,rows,detail,false,false,"",false);
        assertTrue(ForeignPayloads.fits(view));
        assertThrows(IllegalArgumentException.class,()->new ForeignPayloads.Detail(rows.getFirst().id(),name,description,false,256,history,true,8,8));
        assertThrows(IllegalArgumentException.class,()->new ForeignPayloads.Detail(rows.getFirst().id(),name,description,false,256,Collections.nCopies(33,"history"),true,0,1));
    }
}
