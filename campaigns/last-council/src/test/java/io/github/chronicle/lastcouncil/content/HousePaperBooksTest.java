package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.lastcouncil.house.HouseDefinition;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.Section;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HousePaperBooksTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),id=UUID.randomUUID();
    private final HouseDefinition first=new HouseDefinition("test:first","First House","Reputation","Description","Interests",true);
    private final HouseDefinition second=new HouseDefinition("test:second","Second House","","","",true);
    private CouncilContent content(){return new CouncilContent(Map.of(first.id(),first,second.id(),second),List.of(new CouncilContent.SecretTemplate("unused",1,"UNASSIGNED TEMPLATE",Map.of(),Set.of(first.id()))),List.of(),0,0,0);}
    private HouseCast cast(String own,String other,boolean exposed){
        return new HouseCast(id,1,123456,Map.of(
                a,new HouseCast.HousePapers(first.id(),List.of(own),List.of("My goal"),List.of(new HouseCast.KnownSecret(second.id(),other))),
                b,new HouseCast.HousePapers(second.id(),List.of(other,"Hidden second secret"),List.of("Their goal"),exposed?List.of(new HouseCast.KnownSecret(first.id(),own)):List.of())));
    }
    private static String text(List<BookDefinition> books){return books.stream().flatMap(book->book.pages().stream()).collect(java.util.stream.Collectors.joining());}
    @Test void copiesOnlyTheRequestedRecipientPaperWithoutExposureMetadata(){
        var unseen=cast("My secret","Known other secret",false);var seen=cast("My secret","Known other secret",true);
        var own=HousePaperBooks.build(content(),unseen,a,Section.SECRETS,0);
        assertEquals(own,HousePaperBooks.build(content(),seen,a,Section.SECRETS,0));
        assertEquals(first.title()+"\n\nMy secret",text(own));
        var known=HousePaperBooks.build(content(),seen,a,Section.KNOWN_SECRETS,0);
        assertEquals(first.title()+"\n\n"+second.title()+"\nKnown other secret",text(known));
        assertFalse(text(known).contains("Hidden second secret"));assertFalse(text(known).contains("UNASSIGNED"));
        assertThrows(IllegalArgumentException.class,()->HousePaperBooks.build(content(),seen,a,Section.SECRETS,1));
        assertThrows(IllegalStateException.class,()->HousePaperBooks.build(content(),seen,UUID.randomUUID(),Section.SECRETS,0));
    }
    @Test void maximumUnicodeAndNewlineHeavyPapersRetainEveryCharacterAcrossVolumes(){
        String unicode="\uD83D\uDE00".repeat(4096);var value=cast(unicode,"Other",false);
        assertEquals(first.title()+"\n\n"+unicode,text(HousePaperBooks.build(content(),value,a,Section.SECRETS,0)));
        String lines="\n".repeat(4096);var books=HousePaperBooks.build(content(),cast(lines,"Other",false),a,Section.SECRETS,0);
        assertTrue(books.size()>1);assertTrue(books.size()<=36);assertEquals(first.title()+"\n\n"+lines,text(books));
        for(var book:books){assertTrue(book.pages().size()<=64);assertTrue(book.title().codePointCount(0,book.title().length())<=32);assertEquals(BookDefinition.Rule.OFF,book.rule());}
    }
    @Test void houseInformationIsOnePaperAndCannotSelectAnotherFieldOrUnissuedCast(){
        var books=HousePaperBooks.build(content(),cast("Secret","Other",false),a,Section.INFORMATION,0);
        assertEquals("First House\n\nReputation\n\nDescription\n\nInterests",text(books));
        assertThrows(IllegalArgumentException.class,()->HousePaperBooks.build(content(),cast("Secret","Other",false),a,Section.INFORMATION,1));
        assertThrows(IllegalStateException.class,()->HousePaperBooks.build(content(),null,a,Section.INFORMATION,0));
    }
}
