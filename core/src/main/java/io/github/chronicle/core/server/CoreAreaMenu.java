package io.github.chronicle.core.server;

import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

/** Area presentation is derived from the Core Region pin, never a second editable geometry. */
final class CoreAreaMenu {
    private CoreAreaMenu(){}
    static List<Entry> entries(MinecraftServer server){return AreaChanges.catalog(server).areas().values().stream().map(a->new Entry(a.id(),a.name(),"Saved Start and End states of one Region.","AREA",a.bounds().regionId(),a.revision())).toList();}
    static List<String> consumers(MinecraftServer server,String id){var result=new ArrayList<>(Departures.references(server,id));result.add("Events: "+id+" / Start and End");return List.copyOf(result);}
    static Detail detail(ServerPlayerEntity player,String id){
        MinecraftServer server=player.getEntityWorld().getServer();boolean edit=AuthoringRuntime.isAuthor(player),free=RuntimeHolds.reason(server)==null&&!WorldAreaOperations.busy(server);List<Control> controls=new ArrayList<>();
        controls.add(new Control(Action.AREA_NEW,"Create new area",edit&&free,"Enable Editing and resolve pending operations first.",false,""));
        AreaChange area=AreaChanges.catalog(server).areas().get(id);if(area==null)return new Detail(id,"Area changes","Save two states of a selected Region, then choose when to replace it.",List.of(),controls,null,null,null);
        boolean stale=AreaChanges.stale(server,area);List<String> consumers=consumers(server,id);
        AreaOperation operation=AreaChanges.operations(server).state().operations().values().stream().filter(o->o.areaId().equals(id)&&(o.unresolved()||o.deletionObligation()&&o.stage()==AreaOperation.Stage.COMPLETE)).reduce((a,b)->b).orElseGet(()->WorldAreaOperations.pendingPrepared(server,id).orElse(null));
        String problem=stale?"The Region changed. These captures cannot be applied. Recreate the Area, or delete it without changing terrain.":!WorldAreaOperations.problem(server).isBlank()?WorldAreaOperations.problem(server):AreaChanges.problem(server).orElse("");
        boolean working=WorldAreaOperations.busy(server)||AreaChanges.busy(server);boolean operate=free&&!stale;
        controls.add(new Control(Action.AREA_CAPTURE_OPEN,"Save current area state",operate,"Load the Region, resolve its pin and finish pending operations first.",false,""));
        controls.add(new Control(Action.AREA_PREVIEW,"Preview saved state",!stale&&(area.start().isPresent()||area.end().isPresent()),"No current saved state is available.",false,""));
        controls.add(new Control(Action.AREA_APPLY_REVIEW,"Apply saved state",operate&&(area.start().isPresent()||area.end().isPresent()),"Finish pending operations and choose a current saved state.",false,""));
        controls.add(new Control(Action.AREA_DELETE_REVIEW,"Delete area",edit&&free,"Enable Editing and finish pending operations first.",false,""));
        if(operation!=null)controls.add(new Control(Action.AREA_RECOVER,"Recheck pending operation",!AreaChanges.busy(server)&&(!working||!WorldAreaOperations.problem(server).isBlank()),"Wait for the current operation to finish.",true,""));
        List<Fact> facts=List.of(new Fact("Region",area.bounds().regionId()),new Fact("Bounds",area.bounds().min()+" to "+area.bounds().max()),new Fact("States","Start: "+area.start().isPresent()+"; End: "+area.end().isPresent()),new Fact("Status",problem.isBlank()?"Ready":problem));
        AreaForm form=new AreaForm(area.id(),area.name(),area.revision(),area.bounds().dimension(),position(area.bounds().min()),position(area.bounds().max()),area.start().isPresent(),area.end().isPresent(),stale,working,consumers,problem,operation==null?"":operation.operationId().toString(),operation==null?"":operation.stage().name());
        return new Detail(id,area.name(),"Save or apply Start and End states for this Region. Applying a state replaces its blocks and container contents.",facts,controls,null,null,null,null,null,null,null,form,null);
    }
    private static String position(io.github.chronicle.core.authoring.BlockPosition p){return p.x()+" "+p.y()+" "+p.z();}
}
