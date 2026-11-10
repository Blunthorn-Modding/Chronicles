package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.lastcouncil.house.HouseDefinition;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CouncilContentEditorTest {
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static CouncilContent sample(){var h=new HouseDefinition("test:a","A","","","",false);return new CouncilContent(Map.of(h.id(),h),List.of(new CouncilContent.SecretTemplate("s",1,"Secret",Map.of("fact.x","value"),Set.of(h.id()))),List.of(),1,0,5);}
    @Test void houseDraftsGenerateUniqueValidIdsAndKeepThemWhenRenamed() throws Exception {
        var c=sample();var draft=CouncilContentEditor.blank(Category.HOUSES);var other=CouncilContentEditor.blank(Category.HOUSES);
        String id=draft.get("id").getAsString();assertTrue(id.startsWith("last_council:house/"));assertNotEquals(id,other.get("id").getAsString());
        draft.addProperty("title","House Birch");var saved=CouncilContentEditor.save(c,Category.HOUSES,null,bytes(draft.toString()));
        assertEquals("House Birch",saved.houses().get(id).title());draft.addProperty("title","House Silver Birch");
        assertEquals("House Silver Birch",CouncilContentEditor.save(saved,Category.HOUSES,id,bytes(draft.toString())).houses().get(id).title());
        draft.remove("id");draft.addProperty("title","House Willow");assertEquals(2,CouncilContentEditor.save(c,Category.HOUSES,null,bytes(draft.toString())).houses().size());
    }
    @Test void aPlainSecretRequiresNoFactsOrPlaceholders() throws Exception {
        var c=sample();var draft=CouncilContentEditor.blank(Category.SECRETS);draft.addProperty("id","A hidden promise");draft.addProperty("text","The House promised the same harvest twice.");draft.getAsJsonArray("applicableHouseIds").add("test:a");
        var saved=CouncilContentEditor.save(c,Category.SECRETS,null,bytes(draft.toString()));
        var secret=saved.secrets().stream().filter(s->s.id().equals("A hidden promise")).findFirst().orElseThrow();
        assertTrue(secret.facts().isEmpty());assertEquals("The House promised the same harvest twice.",secret.text());
    }
    @Test void optionalValuesGenerateReferencesWithoutOverwritingExistingFacts(){
        assertEquals("fact.value1",CouncilContentEditor.nextFactKey(Set.of()));
        assertEquals("fact.value2",CouncilContentEditor.nextFactKey(Set.of("fact.value1","fact.wheat")));
        assertEquals("fact.value1",CouncilContentEditor.nextFactKey(Set.of("fact.value2")));
    }
    @Test void createsAndEditsWithoutChangingIds() throws Exception {var c=sample();var d=CouncilContentEditor.document(c,Category.SECRETS,"s");d.addProperty("text","Changed");var saved=CouncilContentEditor.save(c,Category.SECRETS,"s",bytes(d.toString()));assertEquals("Changed",saved.secrets().getFirst().text());d.addProperty("id","other");assertThrows(IllegalArgumentException.class,()->CouncilContentEditor.save(c,Category.SECRETS,"s",bytes(d.toString())));assertThrows(IllegalArgumentException.class,()->CouncilContentEditor.save(c,Category.SECRETS,null,bytes(CouncilContentEditor.document(c,Category.SECRETS,"s").toString())));}
    @Test void refusesReferencedHouseAndInvalidScopes() throws Exception {var c=sample();assertThrows(IllegalArgumentException.class,()->CouncilContentEditor.delete(c,Category.HOUSES,"test:a"));var d=CouncilContentEditor.document(c,Category.SECRETS,"s");d.add("applicableHouseIds",new com.google.gson.JsonArray());assertThrows(Exception.class,()->CouncilContentEditor.save(c,Category.SECRETS,"s",bytes(d.toString())));}
    @Test void rejectsMalformedOrUnsupportedPlaceholders() throws Exception {var c=sample();var d=CouncilContentEditor.document(c,Category.SECRETS,"s");for(String text:List.of("${missing}","${house.title", "${fact.bad-key}")){d.addProperty("text",text);assertThrows(IllegalArgumentException.class,()->CouncilContentEditor.save(c,Category.SECRETS,"s",bytes(d.toString())));}d.addProperty("text","${house.title} ${fact.x} ${other.title}");assertDoesNotThrow(()->CouncilContentEditor.save(c,Category.SECRETS,"s",bytes(d.toString())));}
    @Test void rulesRoundTripAndInvalidFactsFail() throws Exception {var c=sample();var d=CouncilContentEditor.document(c,Category.KNOWLEDGE,"rules");d.addProperty("knowledgeChancePercent",40);assertEquals(40,CouncilContentEditor.save(c,Category.KNOWLEDGE,"rules",bytes(d.toString())).knowledgeChancePercent());var secret=CouncilContentEditor.document(c,Category.SECRETS,"s");secret.getAsJsonObject("facts").addProperty("bad","x");assertThrows(Exception.class,()->CouncilContentEditor.save(c,Category.SECRETS,"s",bytes(secret.toString())));}
}
