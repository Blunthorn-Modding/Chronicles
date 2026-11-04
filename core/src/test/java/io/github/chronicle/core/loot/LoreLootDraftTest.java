package io.github.chronicle.core.loot;

import io.github.chronicle.core.books.BookChance;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LoreLootDraftTest {
    @Test void namedReferencesRoundTripWithoutTouchingRichOrdinaryItems(){
        var item=new LootEntry("minecraft:written_book",40,1,1,"{\"minecraft:custom_name\":\"Unmanaged diary\"}");
        var table=new LootType("test:cache","Cache","A mixed cache",2,2.5,List.of(item),List.of(new BookChance("test:journal",12.5)));
        assertEquals(table,LootTableDraftJson.decode(LootTableDraftJson.encode(table)));
        String old="{\"id\":\"test:old\",\"label\":\"Old\",\"description\":\"Old table\",\"recommendedChests\":0,\"refillMinutes\":0,\"entries\":[]}";
        assertTrue(LootTableDraftJson.decode(old).books().isEmpty());
        assertThrows(IllegalArgumentException.class,()->LootTableDraftJson.decode(old.replace("\"entries\":[]","\"entries\":[],\"books\":[{\"bookId\":\"test:book\",\"chancePercent\":4,\"extra\":true}]")));
    }
    @Test void capacityIsCombinedAndDuplicateReferencesAreRejected(){
        var items=java.util.Collections.nCopies(LootType.MAX_ENTRIES,new LootEntry("minecraft:coal",10,1,3,"{}"));
        assertThrows(IllegalArgumentException.class,()->new LootType("test:cache","Cache","Cache",0,0,items,List.of(new BookChance("test:journal",4))));
        assertThrows(IllegalArgumentException.class,()->new LootType("test:cache","Cache","Cache",0,0,List.of(),List.of(new BookChance("test:journal",4),new BookChance("test:journal",8))));
        assertThrows(IllegalArgumentException.class,()->new BookChance("test:journal",Double.NaN));
    }
}
