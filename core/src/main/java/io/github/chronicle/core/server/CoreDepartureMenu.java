package io.github.chronicle.core.server;

import com.google.gson.JsonObject;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.departures.*;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import java.io.IOException;
import java.util.*;

/** Server-owned departure editor drafts; shared locations remain the sole geometry registry. */
final class CoreDepartureMenu {
    private CoreDepartureMenu(){}
    static List<Entry> entries(MinecraftServer server){return Departures.state(server).definitions().values().stream().map(d->new Entry(d.id(),d.name(),"An exact block and boarding Region for a one-way group departure.","DEPARTURE",d.id(),d.revision())).toList();}
    static DepartureDefinition fresh(){return new DepartureDefinition("chronicle:departure_"+UUID.randomUUID().toString().replace("-",""),"New departure",1,Optional.empty(),Optional.empty(),"Everyone inside the boarding Region will leave together. Participants elsewhere will remain behind. Continue?","This is a point of no return. You and the listed passengers will permanently leave this campaign and become spectators. If no enrolled participants remain, the campaign will conclude.",Optional.empty(),true);}
    static DepartureDefinition decode(String json,long revision){
        JsonObject j=BookConfig.strictObject(json);if(!j.keySet().equals(Set.of("id","name","anchorId","anchorRevision","regionId","regionRevision","areaId","areaState","firstText","finalText")))throw new IllegalArgumentException("Invalid departure editor fields.");
        String anchor=text(j,"anchorId"),region=text(j,"regionId"),area=text(j,"areaId");long ar=number(j,"anchorRevision"),rr=number(j,"regionRevision");
        if(anchor.isBlank()&&ar!=0||region.isBlank()&&rr!=0)throw new IllegalArgumentException("Empty references cannot have a revision.");
        String state=text(j,"areaState");if(area.isBlank()&&!state.isBlank())throw new IllegalArgumentException("Choose an Area before its state.");
        return new DepartureDefinition(text(j,"id"),text(j,"name"),revision,anchor.isBlank()?Optional.empty():Optional.of(new SiteRef(anchor,ar)),region.isBlank()?Optional.empty():Optional.of(new SiteRef(region,rr)),text(j,"firstText"),text(j,"finalText"),area.isBlank()?Optional.empty():Optional.of(new AreaLink(area,switch(state){case "start"->AreaState.START;case "end"->AreaState.END;default->throw new IllegalArgumentException("Choose Start or End.");})),true);
    }
    static DepartureDefinition bindLook(ServerCommandSource source,DepartureDefinition draft)throws IOException{
        var pos=ChestAuthoring.target(source);var world=source.getWorld();String dimension=world.getRegistryKey().getValue().toString(),block=Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        if(world.getBlockState(pos).isAir())throw new IllegalStateException("Look at a solid departure control block.");
        var authoring=AuthoringRuntime.get(source.getServer()).service();var at=new io.github.chronicle.core.authoring.BlockPosition(pos.getX(),pos.getY(),pos.getZ());
        AuthoringSite.Anchor anchor=authoring.state().sites().values().stream().filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast).filter(a->a.dimension().equals(dimension)&&a.position().equals(at)&&a.expectedBlock().equals(block)).findFirst().orElse(null);
        if(anchor==null)anchor=(AuthoringSite.Anchor)authoring.put(new AuthoringSite.Anchor("chronicle:helm_"+UUID.randomUUID().toString().replace("-",""),dimension,1,at,block),false);
        return new DepartureDefinition(draft.id(),draft.name(),draft.revision(),Optional.of(new SiteRef(anchor.id(),anchor.revision())),draft.boardingRegion(),draft.firstText(),draft.finalText(),draft.areaLink(),true);
    }
    static Detail detail(ServerPlayerEntity player,String id,DepartureDefinition draft,boolean creating){
        var server=player.getEntityWorld().getServer();boolean edit=AuthoringRuntime.isAuthor(player)&&RuntimeHolds.reason(server)==null;var controls=new ArrayList<Control>();
        controls.add(new Control(Action.DEPARTURE_NEW,"Create new departure",edit,"Enable Editing and finish pending operations first.",false,""));
        DepartureDefinition def=draft!=null&&draft.id().equals(id)?draft:Departures.state(server).definitions().get(id);
        if(def==null)return new Detail(id,"Departures","Bind a departure control and the Region whose online participants leave together.",List.of(),controls,null,null,null);
        boolean used=Departures.state(server).usedByDefinition().containsKey(id);controls.add(new Control(Action.DEPARTURE_EDIT,"Edit departure",edit&&!used,"Used departures remain in history and cannot be edited.",false,""));controls.add(new Control(Action.DEPARTURE_DELETE,"Delete departure",edit&&!used,"Used departures remain in history and cannot be deleted.",true,""));
        var sites=AuthoringRuntime.get(server).service().state().sites();
        var anchors=sites.values().stream().filter(AuthoringSite.Anchor.class::isInstance).map(a->new Entry(a.id(),a.id(),"Saved Block site","BLOCK",a.id(),a.revision())).toList();
        var regions=sites.values().stream().filter(AuthoringSite.Region.class::isInstance).map(a->new Entry(a.id(),a.id(),"Saved boarding Region","REGION",a.id(),a.revision())).toList();
        var areas=AreaChanges.catalog(server).areas().values().stream().map(a->new Entry(a.id(),a.name(),AreaChanges.stale(server,a)?"Stale Region reference":"Paired Area states","AREA",a.id(),a.revision())).toList();
        SiteRef anchor=def.exactBlockAnchor().orElse(null),region=def.boardingRegion().orElse(null);AreaLink area=def.areaLink().orElse(null);
        DepartureForm form=new DepartureForm(def.id(),def.name(),def.revision(),anchor==null?"":anchor.id(),anchor==null?0:anchor.revision(),region==null?"":region.id(),region==null?0:region.revision(),area==null?"":area.areaId(),area==null?"":area.requestedState().name().toLowerCase(Locale.ROOT),def.firstText(),def.finalText(),creating,used,anchors,regions,areas);
        List<Fact> facts=List.of(new Fact("Block site",anchor==null?"Choose a Block site":anchor.id()),new Fact("Boarding Region",region==null?"Choose a Region":region.id()),new Fact("Area change",area==null?"None":area.areaId()+" / "+area.requestedState()),new Fact("Use",used?"Already departed":"Single use"));
        return new Detail(id,def.name(),"Interact with its control during active play to review the passengers, then confirm twice. Departed characters become spectators permanently.",facts,controls,null,null,null,null,null,null,null,null,form);
    }
    private static String text(JsonObject j,String key){if(!j.get(key).isJsonPrimitive()||!j.getAsJsonPrimitive(key).isString())throw new IllegalArgumentException(key+" must be text");return j.get(key).getAsString();}
    private static long number(JsonObject j,String key){if(!j.get(key).isJsonPrimitive()||!j.getAsJsonPrimitive(key).isNumber()||!j.get(key).getAsString().matches("[0-9]+"))throw new IllegalArgumentException(key+" must be an integer");return j.get(key).getAsLong();}
}
