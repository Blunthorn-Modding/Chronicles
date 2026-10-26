package io.github.chronicle.core.areas;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AreaCatalogServiceTest {
    @Test void failedCreateOrRecaptureSaveNeverPublishes() throws IOException {
        Memory memory=new Memory();AreaCatalogService service=new AreaCatalogService(memory);memory.fail=true;
        assertThrows(IOException.class,()->service.create("area","Area",region(3),AreaState.START,ref(),0));assertEquals(AreaCatalog.empty(),service.state());
        memory.fail=false;AreaChange area=service.create("area","Area",region(3),AreaState.START,ref(),0);memory.fail=true;
        assertThrows(IOException.class,()->service.replaceSnapshot("area",area.revision(),region(3),AreaState.END,ref()));assertTrue(service.state().areas().get("area").end().isEmpty());
    }

    @Test void staleRegionAndAreaRevisionAreRejected() throws IOException {
        AreaCatalogService service=new AreaCatalogService(new Memory());AreaChange area=service.create("area","Area",region(3),AreaState.START,ref(),0);
        assertThrows(IllegalStateException.class,()->service.replaceSnapshot("area",area.revision(),region(4),AreaState.END,ref()));
        AreaChange next=service.replaceSnapshot("area",area.revision(),region(3),AreaState.END,ref());
        assertThrows(IllegalStateException.class,()->service.replaceSnapshot("area",area.revision(),region(3),AreaState.END,ref()));assertEquals(2,next.revision());
    }

    @Test void retirementRequiresCompletedPinnedDeletionAndNeverReusesId() throws IOException {
        Memory memory=new Memory();AreaCatalogService service=new AreaCatalogService(memory);AreaChange area=service.create("area","Area",region(3),AreaState.START,ref(),0);
        assertThrows(IllegalStateException.class,()->area.requireSnapshot(AreaState.END));
        AreaOperation pending=delete(area,AreaOperation.Stage.PREPARED,0);assertThrows(IllegalStateException.class,()->service.retire("area",1,region(3),pending));
        AreaOperation complete=delete(area,AreaOperation.Stage.COMPLETE,area.bounds().cells());memory.fail=true;assertThrows(IOException.class,()->service.retire("area",1,region(3),complete));assertTrue(service.state().areas().containsKey("area"));memory.fail=false;service.retire("area",1,region(3),complete);
        assertTrue(service.state().tombstones().contains("area"));assertThrows(IllegalStateException.class,()->service.create("area","Again",region(3),AreaState.START,ref(),service.state().revision()));
    }

    @Test void unchangedRetirementAllowsMissingRegionButApplyingRetirementDoesNot() throws IOException {
        AreaCatalogService unchangedService=new AreaCatalogService(new Memory());AreaChange unchangedArea=unchangedService.create("area","Area",region(3),AreaState.START,ref(),0);
        AreaOperation unchanged=new AreaOperation(UUID.randomUUID(),AreaOperation.Intent.DELETE_UNCHANGED,unchangedArea.id(),unchangedArea.name(),unchangedArea.revision(),unchangedArea.bounds(),Optional.empty(),Optional.empty(),Optional.empty(),UUID.randomUUID(),0,AreaOperation.Stage.COMPLETE,true,"","1970-01-01T00:00:00Z");
        unchangedService.retire("area",unchangedArea.revision(),unchanged);assertTrue(unchangedService.state().tombstones().contains("area"));

        AreaCatalogService applyingService=new AreaCatalogService(new Memory());AreaChange applyingArea=applyingService.create("area","Area",region(3),AreaState.START,ref(),0);AreaOperation applying=delete(applyingArea,AreaOperation.Stage.COMPLETE,applyingArea.bounds().cells());
        assertThrows(IllegalStateException.class,()->applyingService.retire("area",applyingArea.revision(),null,applying));
        assertThrows(IllegalStateException.class,()->applyingService.retire("area",applyingArea.revision(),region(4),applying));
        assertTrue(applyingService.state().areas().containsKey("area"));
    }

    private static SnapshotRef ref(){return AreaSnapshotCodec.encode(AreaSnapshotStoreTest.snapshot()).ref();}
    private static AuthoringSite.Region region(long revision){return new AuthoringSite.Region("test","minecraft:overworld",revision,new BlockPosition(0,64,0),new BlockPosition(1,64,0));}
    private static AreaOperation delete(AreaChange area,AreaOperation.Stage stage,int cursor){SnapshotRef r=area.start().orElseThrow();return new AreaOperation(UUID.randomUUID(),AreaOperation.Intent.DELETE_APPLY,area.id(),area.name(),area.revision(),area.bounds(),Optional.of(AreaState.START),Optional.of(r),Optional.of(r),UUID.randomUUID(),cursor,stage,true,"","1970-01-01T00:00:00Z");}
    private static final class Memory implements AreaCatalogRepository{AreaCatalog state=AreaCatalog.empty();boolean fail;public AreaCatalog load(){return state;}public void save(AreaCatalog value)throws IOException{if(fail)throw new IOException("injected");state=value;}}
}
