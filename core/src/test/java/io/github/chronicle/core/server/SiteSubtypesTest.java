package io.github.chronicle.core.server;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SiteSubtypesTest {
    private static SiteSubtypes.Handler handler(){return new SiteSubtypes.Handler(){public boolean assigned(net.minecraft.server.MinecraftServer server,AuthoringSite site){return true;}public SiteSubtypes.Change change(net.minecraft.server.command.ServerCommandSource source,AuthoringSite site,boolean enabled){return SiteSubtypes.Change.APPLIED;}};}
    @Test void independentlyRegisteredRegionCapabilitiesCoexistAndFreeze(){
        var registry=new SiteSubtypes.Registry();
        registry.register(new SiteSubtypes.Definition("test:states","States","",Set.of(SiteSubtypes.Kind.REGION),handler()));
        registry.register(new SiteSubtypes.Definition("test:encounter","Encounter","",Set.of(SiteSubtypes.Kind.REGION),handler()));
        var region=new AuthoringSite.Region("test:r","minecraft:overworld",1,new BlockPosition(0,0,0),new BlockPosition(1,1,1));
        assertEquals(2,registry.applicable(region).size());assertTrue(registry.applicable(region).stream().allMatch(d->d.handler().assigned(null,region)));
        assertTrue(registry.applicable(new AuthoringSite.Location("test:p","minecraft:overworld",1,0,0,0,0,0)).isEmpty());
        assertThrows(IllegalArgumentException.class,()->registry.register(registry.definitions().getFirst()));registry.freeze();assertThrows(IllegalStateException.class,()->registry.register(new SiteSubtypes.Definition("test:new","New","",Set.of(SiteSubtypes.Kind.REGION),handler())));
    }
    @Test void unchangedGeometryIgnoresRevisionButNotBoundsOrIdentity(){var a=new AuthoringSite.Region("r","minecraft:overworld",1,new BlockPosition(0,0,0),new BlockPosition(1,1,1));assertTrue(AuthoringRuntime.sameGeometry(a,a.withRevision(4)));assertFalse(AuthoringRuntime.sameGeometry(a,new AuthoringSite.Region("r","minecraft:overworld",4,new BlockPosition(0,0,0),new BlockPosition(2,1,1))));assertFalse(AuthoringRuntime.sameGeometry(a,new AuthoringSite.Region("other","minecraft:overworld",1,a.min(),a.max())));}
    @Test void crossRegionDepartureStateNeedsExplicitRepair(){assertDoesNotThrow(()->Departures.requireSameRegionState("ship","ship"));assertThrows(IllegalStateException.class,()->Departures.requireSameRegionState("ship","other"));}
}
