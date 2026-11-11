package io.github.chronicle.core.menu;

import com.google.gson.Gson;
import io.github.chronicle.core.roles.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerRoleDraftJsonTest {
    @Test void rejectsDeeplyNestedEscapedItemPayloadBeforeCanonicalizing(){String inner="0";for(int n=0;n<2000;n++)inner="["+inner+"]";String payload="{\"components\":{"+"\"test:x\":"+inner+"}}";var outer=io.github.chronicle.core.books.BookConfig.strictObject(valid());outer.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("stackJson",payload);assertThrows(IllegalArgumentException.class,()->PlayerRoleDraftJson.decode(outer.toString()));}
    private String valid(){return new Gson().toJson(new PlayerRole("test:role","New role","Prose ... & words --- retained",true,1,null,null,List.of(new StartingItem("{\"id\":\"minecraft:bread\",\"count\":1}")),List.of()));}
    @Test void acceptsClientNullOmissionAndCanonicalItems(){var role=PlayerRoleDraftJson.decode(valid());assertNull(role.starting());assertEquals(1,role.items().size());assertTrue(role.description().contains("..."));}
    @Test void refusesCoercedScalarsAndUnknownFields(){for(var text:List.of(valid().replace("\"capacity\":1","\"capacity\":1.5"),valid().replace("\"selectable\":true","\"selectable\":\"true\""),valid().replace("\"name\":\"New role\"","\"name\":7"),valid().replace("\"stackJson\":","\"extra\":true,\"stackJson\":"))){assertThrows(IllegalArgumentException.class,()->PlayerRoleDraftJson.decode(text));}}
    @Test void refusesDuplicateFields(){assertThrows(IllegalArgumentException.class,()->PlayerRoleDraftJson.decode(valid().replace("\"capacity\":1","\"capacity\":1,\"capacity\":2")));}
    @Test void generatedRoleIdIsValidForActualNewEditorDraft(){String id=PlayerRole.generatedId(java.util.UUID.randomUUID());var role=new PlayerRole(id,"New role","",true,1,null,null,List.of(),List.of());assertEquals(role,PlayerRoleDraftJson.decode(new Gson().toJson(role)));assertTrue(io.github.chronicle.core.authoring.AuthoringSite.ID.matcher(id).matches());}
}
