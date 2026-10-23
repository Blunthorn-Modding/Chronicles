package io.github.chronicle.core.server;

import io.github.chronicle.core.network.ItemEnchantingPayloads.*;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ItemEnchantingWorkstationPayloadTest {
    private static final UUID SESSION=UUID.randomUUID(),TOKEN=UUID.randomUUID();
    private static final Artwork ART=new Artwork(Identifier.of("quiet_below","forge/off"),Identifier.of("quiet_below","forge/idle"),Identifier.of("quiet_below","forge/working"));
    private static View view(Phase phase,boolean available,boolean offerable){return new View(7,SESSION,TOKEN,3,"Ancient Forge",phase,available,offerable,ART,false);}

    @Test void viewCarriesOnlyBoundedAuthorityAndCoarsePresentation(){
        var view=view(Phase.OFFERED,true,true);
        assertEquals(SESSION,view.sessionId());assertEquals(TOKEN,view.actionToken());assertEquals(Phase.OFFERED,view.phase());
        assertEquals(Identifier.of("quiet_below","forge/idle"),view.artwork().idleSprite());
        assertThrows(IllegalArgumentException.class,()->new View(-1,SESSION,TOKEN,0,"Forge",Phase.EMPTY,true,false,ART,false));
        assertThrows(IllegalArgumentException.class,()->new View(1,null,TOKEN,0,"Forge",Phase.EMPTY,true,false,ART,false));
        assertThrows(IllegalArgumentException.class,()->new View(1,SESSION,null,0,"Forge",Phase.EMPTY,true,false,ART,false));
        assertThrows(IllegalArgumentException.class,()->new View(1,SESSION,TOKEN,-1,"Forge",Phase.EMPTY,true,false,ART,false));
        assertThrows(IllegalArgumentException.class,()->new View(1,SESSION,TOKEN,0," ",Phase.EMPTY,true,false,ART,false));
        assertThrows(IllegalArgumentException.class,()->new View(1,SESSION,TOKEN,0,"x".repeat(81),Phase.EMPTY,true,false,ART,false));
        assertThrows(IllegalArgumentException.class,()->new View(1,SESSION,TOKEN,0,"Forge",Phase.EMPTY,false,true,ART,false));
        assertThrows(IllegalArgumentException.class,()->new View(1,SESSION,TOKEN,0,"Forge",Phase.WORKING,true,true,ART,false));
        assertThrows(IllegalArgumentException.class,()->new Artwork(null,ART.idleSprite(),ART.workingSprite()));
    }

    @Test void offerHasFiniteIdentityOnly(){
        assertDoesNotThrow(()->new Offer(1,SESSION,TOKEN));
        assertThrows(IllegalArgumentException.class,()->new Offer(-1,SESSION,TOKEN));
        assertThrows(IllegalArgumentException.class,()->new Offer(1,null,TOKEN));
        assertThrows(IllegalArgumentException.class,()->new Offer(1,SESSION,null));
        assertEquals("chronicle_core:workshop_ritual_offer_v1",Offer.ID.id().toString());
        assertEquals("chronicle_core:workshop_ritual_view_v1",View.ID.id().toString());
    }

    @Test void geometryExposesExactlyTwoWorkshopSlots(){
        assertEquals(0,ItemEnchantingScreenHandler.ENTRY_SLOT);assertEquals(1,ItemEnchantingScreenHandler.OUTPUT_SLOT);
        assertEquals(2,ItemEnchantingScreenHandler.PLAYER_START);assertEquals(38,ItemEnchantingScreenHandler.PLAYER_END);
        assertEquals(224,ItemEnchantingScreenHandler.WIDTH);assertEquals(220,ItemEnchantingScreenHandler.HEIGHT);
        assertEquals(105,ItemEnchantingScreenHandler.ENTRY_X);assertEquals(29,ItemEnchantingScreenHandler.ENTRY_Y);
        assertEquals(105,ItemEnchantingScreenHandler.OUTPUT_X);assertEquals(108,ItemEnchantingScreenHandler.OUTPUT_Y);
        assertEquals(32,ItemEnchantingScreenHandler.INVENTORY_X);assertEquals(136,ItemEnchantingScreenHandler.INVENTORY_Y);
    }

    @Test void unorderedMatcherAcceptsOneOrientationAndCoalescesExactDuplicates(){
        assertEquals(ItemEnchantingRuntime.Selection.FIRST,ItemEnchantingRuntime.select(true,1,2,false,0,0,false));
        assertEquals(ItemEnchantingRuntime.Selection.SECOND,ItemEnchantingRuntime.select(false,0,0,true,1,2,false));
        assertEquals(ItemEnchantingRuntime.Selection.NONE,ItemEnchantingRuntime.select(false,0,0,false,0,0,false));
        assertEquals(ItemEnchantingRuntime.Selection.FIRST,ItemEnchantingRuntime.select(true,1,2,true,1,2,true));
    }

    @Test void unorderedMatcherRejectsDifferentConsumptionOrComponents(){
        assertEquals(ItemEnchantingRuntime.Selection.AMBIGUOUS,ItemEnchantingRuntime.select(true,1,1,true,1,1,false),
                "same item with different components must be ambiguous");
        assertEquals(ItemEnchantingRuntime.Selection.AMBIGUOUS,ItemEnchantingRuntime.select(true,1,1,true,2,1,true),
                "same output with different consumption must be ambiguous");
    }
}
