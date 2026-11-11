package io.github.chronicle.core.server;

import com.google.gson.JsonObject;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreDepartureMenuTest {
    private static String draft(){
        JsonObject j=new JsonObject();
        j.addProperty("id","test:ship");j.addProperty("name","Ship");
        j.addProperty("anchorId","test:helm");j.addProperty("anchorRevision",2);
        j.addProperty("regionId","test:aboard");j.addProperty("regionRevision",3);
        j.addProperty("areaId","");j.addProperty("areaState","");
        j.addProperty("firstText","First warning");j.addProperty("finalText","Final warning");
        return j.toString();
    }
    @Test void editingAdvancesExactlyOneRevisionWhileBindingRetainsTheBase(){
        var edited=CoreDepartureMenu.decodeAction(draft(),7,Action.DEPARTURE_SAVE);
        assertEquals(8,edited.revision());assertEquals("test:helm",edited.exactBlockAnchor().orElseThrow().id());
        assertEquals(7,CoreDepartureMenu.decodeAction(draft(),7,Action.DEPARTURE_BIND_LOOK).revision());
        assertEquals(1,CoreDepartureMenu.decodeAction(draft(),1,Action.DEPARTURE_CREATE).revision());
        assertThrows(ArithmeticException.class,()->CoreDepartureMenu.decodeAction(draft(),Long.MAX_VALUE,Action.DEPARTURE_SAVE));
    }
}
