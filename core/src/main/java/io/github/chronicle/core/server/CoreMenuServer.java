package io.github.chronicle.core.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.CampaignEvent;
import io.github.chronicle.core.content.ChestBinding;
import io.github.chronicle.core.loot.*;
import io.github.chronicle.core.books.*;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import io.github.chronicle.core.managed.*;
import io.github.chronicle.core.network.CoreMenuPayloads.*;
import io.github.chronicle.core.play.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Finite private menu actions, live authority checks and immutable, revision-pinned views. */
public final class CoreMenuServer {
    private static final Map<MinecraftServer,Map<UUID,Context>> WORLDS=new IdentityHashMap<>();
    private CoreMenuServer() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(request,context)->handle(context.player(),request));
        io.github.chronicle.core.server.ServerDisconnects.register((handler,server)->{var map=WORLDS.get(server);if(map!=null)map.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            // Relevant server changes are pushed; the elapsed clock is deliberately outside Frame.
            if(server.getTicks()%5!=0)return;
            var contexts=WORLDS.get(server);if(contexts==null)return;
            for(var entry:List.copyOf(contexts.entrySet())) {
                var player=server.getPlayerManager().getPlayer(entry.getKey());var context=entry.getValue();
                if(player==null||!context.visible)continue;
                try {if(!Objects.equals(context.frame,frame(player))){invalidate(context);send(player,context,"Server state updated.",true,false,false);}}
                catch(RuntimeException exception){ChronicleCore.LOGGER.debug("Menu update unavailable",exception);}
            }
        });
    }

    public static int open(ServerCommandSource source){return open(source,Page.SESSION);}
    public static int open(ServerCommandSource source,Page page) {
        var player=source.getPlayer();
        if(player==null){source.sendError(Text.literal("The menu requires a player. Explicit Chronicle commands remain available."));return 0;}
        if(!ServerPlayNetworking.canSend(player,Response.ID)){source.sendError(Text.literal("Install the matching Chronicle client pack for the redesigned menu."));return 0;}
        handle(player,new Request(Action.OPEN,page,"","",0,0,"",""));return 1;
    }

    /** Explicit command facade uses the same typed actions and live services as the screen. */
    public static int executeCommand(ServerCommandSource source,Action action,Page page,String id,String argument) {
        var player=source.getPlayer();if(player==null){source.sendError(Text.literal("This action requires a player; explicit legacy server commands remain available."));return 0;}
        if(!source.hasPermissionLevel(2)&&!publicAction(action)){source.sendError(Text.literal("Operator permission level 2 is required."));return 0;}
        var c=WORLDS.computeIfAbsent(source.getServer(),s->new HashMap<>()).computeIfAbsent(player.getUuid(),u->new Context());
        c.page=page;c.selected=id;c.filter="POINT";
        if(!source.hasPermissionLevel(2))c.selected=player.getUuidAsString();
        try{String result=page==Page.SETUP&&(action==Action.SETUP_ASSIGN||action==Action.SETUP_CLEAR||action==Action.SHOW)
                    ?legacySetupCommand(source,action,id,argument)
                    :act(player,new Request(action,page,c.selected,argument,Math.max(0,c.token),0,c.filter,""),c);
            c.token=0;send(player,c,result,true,false,false);source.sendFeedback(()->Text.literal(result),false);return 1;}
        catch(Exception e){source.sendError(Text.literal(Objects.requireNonNullElse(e.getMessage(),"Action failed; inspect the server log.")));return 0;}
    }

    /** Compatibility for explicit setup commands only; network requests use their geometry page. */
    private static String legacySetupCommand(ServerCommandSource source,Action action,String requirement,String site)throws Exception {
        if(ChronicleCore.authoring().get(requirement).isEmpty())throw new IllegalArgumentException("Choose a registered game requirement.");
        if(action==Action.SETUP_ASSIGN){MapSiteAssignments.assign(source,requirement,site);return "Requirement assigned to the existing saved site. No coordinates copied, growth enabled or player moved.";}
        if(action==Action.SETUP_CLEAR){MapSiteAssignments.clear(source,requirement);return "Assignment cleared; the saved site itself is retained.";}
        var saved=MapSiteAssignments.resolve(source.getServer(),requirement).orElseThrow(()->new IllegalStateException("Choose a saved site first."));
        AuthoringRuntime.get(source.getServer()).preview(source.getPlayerOrThrow(),saved);return "Private assigned-site outline shown.";
    }

    private static void handle(ServerPlayerEntity player,Request request) {
        var server=player.getEntityWorld().getServer();
        var context=WORLDS.computeIfAbsent(server,s->new HashMap<>()).computeIfAbsent(player.getUuid(),id->new Context());
        if(request.action()==Action.CLOSE){context.visible=false;invalidate(context);return;}
        long now=System.nanoTime();if(now-context.window>1_000_000_000L){context.window=now;context.requests=0;}
        if(++context.requests>20)return;
        boolean gm=player.getCommandSource().hasPermissionLevel(2);
        boolean read=switch(request.action()){case OPEN,REFRESH,OPTIONS,EVENT_PREVIEW,EVENT_AUDIO,EVENT_CHECK->true;default->false;};
        try {
            if(!gm&&!publicAction(request.action()))throw new IllegalStateException("Operator permission level 2 is required for management.");
            Frame current=frame(player);
            if(!read&&(request.token()!=context.token||!Objects.equals(current,context.frame))){invalidate(context);send(player,context,"Server state changed. Review the refreshed selection before retrying.",false,false,false);return;}
            if(!Objects.equals(current,context.frame))invalidate(context);
            if(request.action()==Action.OPEN){context.token=0;context.visible=true;context.keepBook="";context.keepTable="";clearGive(context);}
            context.page=gm?request.page():Page.PLAYERS;
            context.filter=gm&&"BLOCK".equals(request.filter())?"BLOCK":"POINT";
            context.index=request.index();
            context.selected=gm?request.id():player.getUuidAsString();
            if(gm&&request.action()==Action.OPEN&&context.page==Page.AREA_CHANGES){var legacy=AreaChanges.catalog(server).areas().get(context.selected);context.page=Page.REGIONS;context.selected=legacy==null?"":legacy.bounds().regionId();}
            if(gm&&request.action()==Action.OPEN&&context.page==Page.DEPARTURES){var legacy=Departures.state(server).definitions().get(context.selected);context.page=Page.LOCATIONS;context.filter="BLOCK";context.selected=legacy==null?"":legacy.exactBlockAnchor().filter(r->sites(server).get(r.id()) instanceof AuthoringSite.Anchor).map(io.github.chronicle.core.departures.SiteRef::id).orElse(CoreDepartureMenu.pendingKey(legacy.id()));}

            if(request.action()==Action.OPEN&&context.page==Page.LOOT_TABLES)context.expectedLoot=null;
            if(request.action()==Action.OPEN&&context.page==Page.BOOKS_LORE)context.expectedBook=null;
            if(request.action()==Action.OPEN){context.departureDraft=null;context.departureCreating=false;context.roleDraft=null;context.roleCreating=false;context.pendingRole="";}
            context.picker=null;
            String message="";boolean close=false;
            if(request.action()==Action.OPTIONS){
                if(!gm)throw new IllegalStateException("Management selectors require operator permission.");
                if(request.argument().equals("BLOCK_LIMIT_TYPE")&&context.page!=Page.SETUP)throw new IllegalArgumentException("Block limits are under Game setup.");
                context.picker=picker(player,request.argument(),request.query(),request.index());
                context.index=0;
            } else if(!read || request.action()==Action.EVENT_PREVIEW || request.action()==Action.EVENT_AUDIO || request.action()==Action.EVENT_CHECK) {
                message=act(player,request,context);
                close=request.action()==Action.REGION_CONTINUE||request.action()==Action.AREA_BEGIN||request.action()==Action.REGION_BEGIN;
                if(close)context.visible=false;
                if(!read)context.token=0;
            }
            send(player,context,message,true,request.action()==Action.OPEN,close);
        } catch(Exception exception) {
            if(exception instanceof IOException)ChronicleCore.LOGGER.error("Menu save failed; do not retry uncertain operations blindly",exception);
            send(player,context,shortText(exception.getMessage()==null?"Action failed; inspect the server log.":exception.getMessage(),2048),false,false,false);
        }
    }

    private static io.github.chronicle.core.areas.AreaChange selectedArea(MinecraftServer server,Context c){if(c.page==Page.REGIONS)return AreaChanges.forRegion(server,c.selected).orElseThrow(()->new IllegalStateException("This Region has no Saved states subtype."));if(c.page==Page.AREA_CHANGES)return area(server,c.selected);throw new IllegalArgumentException("Choose a Region with Saved states.");}
    private static List<Entry> regionEntries(MinecraftServer server){List<Entry> entries=new ArrayList<>();Set<String> live=new HashSet<>();for(var site:sites(server).values())if(site instanceof AuthoringSite.Region r){live.add(r.id());var states=AreaChanges.forRegion(server,r.id());entries.add(new Entry(r.id(),states.map(io.github.chronicle.core.areas.AreaChange::name).orElse(r.id()),"A bounded Region with independent optional subtypes.","REGION",r.id(),r.revision()));}for(var states:AreaChanges.catalog(server).areas().values())if(!live.contains(states.bounds().regionId()))entries.add(new Entry(states.bounds().regionId(),shortText(states.name(),138)+" (Region missing)","Saved states whose Core Region is missing. Remove the state capability without changing terrain, or repair its geometry explicitly.","MISSING_REGION",states.id(),states.revision()));return List.copyOf(entries);}
    private static List<Entry> locationBlockEntries(MinecraftServer server){var entries=new ArrayList<Entry>(blockEntries(server));entries.addAll(CoreDepartureMenu.pendingEntries(server));return List.copyOf(entries);}
    private static io.github.chronicle.core.departures.DepartureDefinition locationDeparture(MinecraftServer server,String selection){if(selection.startsWith("managed:")&&manualAliases(server,managedInstance(server,selection)).size()!=1)return null;return CoreDepartureMenu.selected(server,manualSiteId(server,selection));}
    private static DepartureForm locationDepartureForm(MinecraftServer server,String selection,DepartureForm form){if(form==null)return null;List<Entry> anchors=List.of();var selected=sites(server).get(manualSiteId(server,selection));if(selected instanceof AuthoringSite.Anchor a)anchors=List.of(siteEntry(a));return new DepartureForm(form.id(),form.name(),form.revision(),form.anchorId(),form.anchorRevision(),form.regionId(),form.regionRevision(),form.areaId(),form.areaState(),form.firstText(),form.finalText(),form.creating(),form.used(),anchors,form.regions(),form.areas());}
    private static io.github.chronicle.core.areas.AreaState areaState(String value){return switch(value.toLowerCase(Locale.ROOT)){case "start"->io.github.chronicle.core.areas.AreaState.START;case "end"->io.github.chronicle.core.areas.AreaState.END;default->throw new IllegalArgumentException("Choose Start or End.");};}
    private static io.github.chronicle.core.areas.AreaChange area(MinecraftServer server,String id){return Optional.ofNullable(AreaChanges.catalog(server).areas().get(id)).orElseThrow(()->new IllegalStateException("Choose an existing Area change."));}
    private static void requireAreaOperator(ServerPlayerEntity player){if(!player.getCommandSource().hasPermissionLevel(2)||(!ChronicleRuntime.service(player.getEntityWorld().getServer()).state().phase().name().equals("ACTIVE")&&!AuthoringRuntime.isAuthor(player)))throw new IllegalStateException("Enable Editing, or apply during active play.");}
    private static boolean publicAction(Action action){return switch(action){case OPEN,REFRESH,CARD_OPEN,PARTICIPANT_ADD,PARTICIPANT_REMOVE,LEAVE,ROLE_CHOICE_OPEN,ROLE_CHOOSE->true;default->false;};}
    private static void invalidate(Context c){c.token=0;c.observation=null;c.reviewId="";c.reviewFrame=null;clearGive(c);c.pendingRole="";c.roleReview=null;}
    private static void clearGive(Context c){c.giveBook="";c.giveTarget="";c.giveDefinition=null;c.giveAt=0;}

    private static String act(ServerPlayerEntity player,Request request,Context c) throws Exception {
        var source=player.getCommandSource();var server=source.getServer();String id=c.selected,arg=request.argument();
        switch(request.action()) {
            case SITE_SUBTYPES_OPEN -> {
                if(c.page!=Page.REGIONS&&c.page!=Page.LOCATIONS)throw new IllegalArgumentException("Choose a Region or Location first.");
                var site=sites(server).get(manualSiteId(server,id));if(site==null)throw new IllegalStateException("Choose an existing geometry item first.");
                c.picker=new Picker("SITE_SUBTYPES","",0,1,SiteSubtypes.choices(server,site,AuthoringRuntime.isAuthor(player)&&AuthoringRuntime.editable(server)&&!held(server)));return "";
            }
            case SITE_SUBTYPE_SET -> {
                editor(player);if(c.page!=Page.REGIONS&&c.page!=Page.LOCATIONS)throw new IllegalArgumentException("Choose a Region or Location first.");
                var site=sites(server).get(manualSiteId(server,id));if(site==null)throw new IllegalStateException("The selected geometry item is missing.");
                var json=BookConfig.strictObject(arg);if(!json.keySet().equals(Set.of("selected"))||!json.get("selected").isJsonArray()||json.getAsJsonArray("selected").size()>12)throw new IllegalArgumentException("Invalid subtype selection.");
                Set<String> selected=new LinkedHashSet<>();for(var value:json.getAsJsonArray("selected")){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!selected.add(value.getAsString()))throw new IllegalArgumentException("Invalid duplicate subtype selection.");}
                var results=SiteSubtypes.set(source,site,selected);if(results.contains(SiteSubtypes.Change.CONFIGURE_DEPARTURE)){if(!(site instanceof AuthoringSite.Anchor anchor))throw new IllegalStateException("Departure requires a bound Block site.");c.departureDraft=CoreDepartureMenu.bindSite(CoreDepartureMenu.fresh(),anchor);c.departureCreating=true;return "Configure this departure before saving its subtype.";}
                c.departureDraft=null;return "Subtypes updated.";
            }
            case REGION_BEGIN -> {
                editor(player);if(c.page!=Page.REGIONS||held(server))throw new IllegalStateException("Finish pending operations first.");var json=BookConfig.strictObject(arg);if(!json.keySet().equals(Set.of("name","savedStates","state"))||!json.get("name").isJsonPrimitive()||!json.getAsJsonPrimitive("name").isString()||!json.get("savedStates").isJsonPrimitive()||!json.getAsJsonPrimitive("savedStates").isBoolean()||!json.get("state").isJsonPrimitive()||!json.getAsJsonPrimitive("state").isString())throw new IllegalArgumentException("Invalid Region creation draft.");
                String state=json.get("state").getAsString();boolean saved=json.get("savedStates").getAsBoolean();if(!saved&&!state.isBlank())throw new IllegalArgumentException("Ordinary Regions have no initial saved state.");c.selected=RegionAuthoringWorkflow.begin(source,json.get("name").getAsString(),saved?areaState(state):null);return "Select both corners with the Debug shovel, then /chronicle region save.";
            }
            case DEPARTURE_NEW,DEPARTURE_EDIT -> {
                editor(player);if(c.page!=Page.DEPARTURES&&c.page!=Page.LOCATIONS||held(server))throw new IllegalStateException("Manage departures under Locations after finishing pending operations.");c.departureCreating=request.action()==Action.DEPARTURE_NEW;
                c.departureDraft=c.departureCreating?CoreDepartureMenu.fresh():c.page==Page.DEPARTURES?Departures.state(server).definitions().get(id):locationDeparture(server,id);if(c.departureDraft==null)throw new IllegalStateException("Choose an existing departure.");if(c.page==Page.DEPARTURES)c.selected=c.departureDraft.id();return "";
            }
            case DEPARTURE_CREATE,DEPARTURE_SAVE,DEPARTURE_BIND_LOOK -> {
                editor(player);if(c.page!=Page.DEPARTURES&&c.page!=Page.LOCATIONS||held(server)||c.departureDraft==null)throw new IllegalStateException("Open the Departure editor first.");
                var draft=CoreDepartureMenu.decodeAction(arg,c.departureDraft.revision(),request.action());if(!draft.id().equals(c.departureDraft.id()))throw new IllegalStateException("Departure draft identity changed.");
                if(request.action()==Action.DEPARTURE_BIND_LOOK){c.departureDraft=CoreDepartureMenu.bindLook(source,draft);if(c.page==Page.LOCATIONS){c.selected=c.departureDraft.exactBlockAnchor().orElseThrow().id();c.filter="BLOCK";}return "Block site selected. Your other draft settings are retained.";}
                if(c.departureCreating!=(request.action()==Action.DEPARTURE_CREATE))throw new IllegalStateException("Departure editor changed; reopen it.");
                if(c.page==Page.LOCATIONS){var selectedSite=sites(server).get(manualSiteId(server,id));if(!(selectedSite instanceof AuthoringSite.Anchor a)||draft.exactBlockAnchor().filter(r->r.id().equals(a.id())&&r.revision()==a.revision()).isEmpty())throw new IllegalStateException("Explicitly choose the current selected Block site before saving.");}
                if(c.departureCreating)Departures.create(source,draft,Departures.state(server).revision());else Departures.save(source,draft,c.departureDraft.revision(),Departures.state(server).revision());c.departureDraft=null;c.departureCreating=false;return "Departure saved.";
            }
            case DEPARTURE_DELETE -> {
                editor(player);var def=c.page==Page.LOCATIONS?locationDeparture(server,id):Departures.state(server).definitions().get(id);if(def==null)throw new IllegalStateException("Departure missing.");Departures.delete(source,def.id(),def.revision(),Departures.state(server).revision());c.departureDraft=null;return "Unused departure deleted. History retained.";
            }
            case AREA_NEW,AREA_CAPTURE_OPEN -> {editor(player);if(c.page!=Page.AREA_CHANGES&&c.page!=Page.REGIONS)throw new IllegalArgumentException("Use Regions.");return "";}
            case AREA_BEGIN -> {
                editor(player);if(c.page!=Page.AREA_CHANGES&&c.page!=Page.REGIONS||held(server)||WorldAreaOperations.busy(server))throw new IllegalStateException("Finish the current operation before creating an Area.");
                var json=BookConfig.strictObject(arg);if(!json.keySet().equals(Set.of("name","state")))throw new IllegalArgumentException("Invalid Area draft.");
                var draft=AreaChanges.begin(source,json.get("name").getAsString(),areaState(json.get("state").getAsString()));c.selected=draft.id();return "Select both corners, then use /chronicle area change save.";
            }
            case AREA_CAPTURE_REVIEW -> {
                editor(player);if(held(server))throw new IllegalStateException("Finish pending operations first.");var area=selectedArea(server,c);areaState(arg);c.reviewId="capture:"+area.id()+":"+arg;c.reviewFrame=frame(player);c.reviewTime=System.nanoTime();return "The Region pin changed. Capturing "+arg+" will adopt its current bounds and invalidate the other saved state. That state must be recaptured before use. No blocks change. Linked uses: "+String.join(", ",CoreAreaMenu.consumers(server,area.id()));
            }
            case AREA_CAPTURE -> {
                editor(player);if(c.page!=Page.AREA_CHANGES&&c.page!=Page.REGIONS||held(server)||WorldAreaOperations.busy(server))throw new IllegalStateException("Finish the current operation first.");
                var area=selectedArea(server,c);boolean invalidating=AreaChanges.invalidatesOtherState(server,area,areaState(arg));if(invalidating)requireReview(player,c,"capture:"+area.id()+":"+arg);AreaChanges.captureCurrent(source,area.id(),areaState(arg),area.revision(),invalidating);c.reviewId="";return "Saving the current Region as "+arg+". You will receive a message when verification finishes.";
            }
            case AREA_PREVIEW -> {
                var area=selectedArea(server,c);var site=sites(server).get(area.bounds().regionId());if(!(site instanceof AuthoringSite.Region region))throw new IllegalStateException("The Core Region is missing.");AuthoringRuntime.get(server).preview(player,region);return "Private bounding box shown. No blocks changed.";
            }
            case AREA_APPLY_REVIEW,AREA_DELETE_REVIEW -> {
                if(c.page!=Page.AREA_CHANGES&&c.page!=Page.REGIONS||held(server)||WorldAreaOperations.busy(server))throw new IllegalStateException("Finish the current operation first.");
                boolean deleting=request.action()==Action.AREA_DELETE_REVIEW;if(deleting)editor(player);else requireAreaOperator(player);
                var area=selectedArea(server,c);if(!deleting||!arg.equals("unchanged")){AreaChanges.currentRegion(server,area);area.requireSnapshot(areaState(arg));}
                c.reviewId="area:"+area.id()+":"+arg+":"+deleting;c.reviewFrame=frame(player);c.reviewTime=System.nanoTime();
                String uses=String.join(", ",CoreAreaMenu.consumers(server,area.id()));return (deleting?"Delete this Area definition after ":"Apply ")+arg+" for '"+area.name()+"'. "+(arg.equals("unchanged")?"Terrain stays unchanged.":"This replaces every block and container content inside the Region; replaced contents are discarded. Move non-spectator players clear first.")+(uses.isBlank()?"":" Linked uses will become unavailable: "+uses);
            }
            case AREA_APPLY,AREA_DELETE -> {
                var area=selectedArea(server,c);boolean deleting=request.action()==Action.AREA_DELETE;requireReview(player,c,"area:"+area.id()+":"+arg+":"+deleting);if(deleting)editor(player);else requireAreaOperator(player);
                c.reviewId="";if(deleting&&arg.equals("unchanged")){WorldAreaOperations.deleteUnchanged(source,area.id(),area.revision());if(c.page==Page.AREA_CHANGES)c.selected="";return "Area definition deleted. Terrain and the Core Region were retained.";}
                UUID operation=WorldAreaOperations.apply(source,area.id(),areaState(arg),area.revision(),deleting);return "Area operation "+operation+" is preparing. The world stays held until its saved result verifies.";
            }
            case AREA_RECOVER -> {
                if(c.page!=Page.AREA_CHANGES&&c.page!=Page.REGIONS)throw new IllegalArgumentException("Use Regions.");var area=selectedArea(server,c);var op=AreaChanges.operations(server).state().operations().values().stream().filter(o->o.areaId().equals(area.id())&&(o.unresolved()||o.deletionObligation())).reduce((a,b)->b).orElseGet(()->WorldAreaOperations.pendingPrepared(server,area.id()).orElseThrow(()->new IllegalStateException("No pending Area operation.")));WorldAreaOperations.recover(source,op.operationId());return "Rechecking original before/after evidence. Conflicting cells will remain held.";
            }
            case ROLE_NEW,ROLE_EDIT -> {
                editor(player);if(c.page!=Page.PLAYER_ROLES)throw new IllegalArgumentException("Manage definitions under Player Roles.");
                c.roleCreating=request.action()==Action.ROLE_NEW;
                c.roleDraft=c.roleCreating?new io.github.chronicle.core.roles.PlayerRole(io.github.chronicle.core.roles.PlayerRole.generatedId(UUID.randomUUID()),"New role","",true,1,null,null,List.of(),List.of()):PlayerRoles.definitions(server).get(id);
                if(c.roleDraft==null)throw new IllegalArgumentException("Choose an existing role.");c.expectedRole=c.roleCreating?null:c.roleDraft;c.selected=c.roleDraft.id();return "";
            }
            case ROLE_CREATE,ROLE_SAVE -> {
                editor(player);if(c.page!=Page.PLAYER_ROLES||c.roleDraft==null)throw new IllegalStateException("Open the role editor first.");
                var role=PlayerRoles.decode(arg);
                if(!role.id().equals(c.roleDraft.id())||c.roleCreating!=(request.action()==Action.ROLE_CREATE))throw new IllegalStateException("Role editor identity changed; reopen it.");
                if(c.roleCreating)PlayerRoles.create(source,role);else PlayerRoles.save(source,role,c.expectedRole);
                c.roleDraft=null;c.roleCreating=false;c.expectedRole=role;c.selected=role.id();return "Role saved. Existing players were not moved or resupplied.";
            }
            case ROLE_DELETE -> {
                editor(player);if(c.page!=Page.PLAYER_ROLES)throw new IllegalStateException("Delete definitions under Player Roles.");
                PlayerRoles.delete(source,id,c.expectedRole);c.selected="";c.roleDraft=null;c.expectedRole=null;return "Unassigned role removed. Starting history is retained.";
            }
            case ROLE_RELOAD -> {PlayerRoles.reload(source);c.roleDraft=null;c.expectedRole=null;return "Player-role YAML reloaded. No player was moved or resupplied.";}
            case ROLE_CHOICE_OPEN -> {
                if(c.page!=Page.PLAYERS)throw new IllegalArgumentException("Choose roles under Players.");
                var target=UUID.fromString(id);if(!source.hasPermissionLevel(2)&&!target.equals(player.getUuid()))throw new IllegalStateException("Role choice is private.");
                c.pendingRole="";return "";
            }
            case ROLE_CHOOSE -> {
                if(c.page!=Page.PLAYERS||!id.equals(player.getUuidAsString()))throw new IllegalStateException("Choose your own role under Players.");
                PlayerRoles.choose(player,arg);c.pendingRole="";return "Role selected. Your first entry will use its starting policy once.";
            }
            case ROLE_ASSIGN_REVIEW -> {
                if(!source.hasPermissionLevel(2)||c.page!=Page.PLAYERS||held(server))throw new IllegalStateException("Operator role assignment requires Players and no operation hold.");
                var target=UUID.fromString(id);var selected=PlayerRoles.definitions(server).get(arg);
                if(selected==null&&!arg.equals("-"))throw new IllegalArgumentException("Choose an existing role or clear the assignment.");
                var form=PlayerRoles.form(player,target,arg);if(form.choices().stream().noneMatch(r->r.id().equals(arg)&&r.available()))throw new IllegalStateException("Role capacity is full.");
                c.pendingRole=arg;c.roleReview=frame(player);c.roleReviewAt=System.nanoTime();c.roleTarget=id;return "";
            }
            case ROLE_ASSIGN -> {
                if(c.page!=Page.PLAYERS||!id.equals(c.roleTarget)||!arg.equals(c.pendingRole)||!Objects.equals(c.roleReview,frame(player))||System.nanoTime()-c.roleReviewAt>30_000_000_000L)throw new IllegalStateException("Review this role and player again; the confirmation changed or expired.");
                PlayerRoles.assign(source,UUID.fromString(id),arg);c.pendingRole="";c.roleReview=null;return "Role assigned; no teleport or starting-kit replay.";
            }
            case EDIT_ON -> {return command(source,"chronicle author mode on");}
            case EDIT_OFF -> {return command(source,"chronicle author mode off");}
            case SESSION_BUILD_ON,SESSION_BUILD_OFF -> {
                if(c.page!=Page.SESSION||!id.equals("play"))throw new IllegalArgumentException("Building Mode is under Session > Play controls.");
                return SessionBuilding.set(source,request.action()==Action.SESSION_BUILD_ON);
            }
            case SESSION_START,SESSION_PAUSE,SESSION_RESUME,SESSION_END -> {
                String verb=switch(request.action()){case SESSION_START->"start";case SESSION_PAUSE->"pause";case SESSION_RESUME->"resume";default->"end";};
                return command(source,"chronicle session "+verb)+" Continuing world, game conditions and completion history are retained.";
            }
            case PARTICIPANT_ADD,PARTICIPANT_REMOVE -> {
                UUID target=UUID.fromString(id);boolean add=request.action()==Action.PARTICIPANT_ADD;
                if(source.hasPermissionLevel(2))PlayRuntime.setParticipant(source,target,add);else {if(!target.equals(player.getUuid()))throw new IllegalArgumentException("Only your own participation may be changed.");PlayRuntime.setSelfParticipant(player,add);}
                return add?"Participation enabled. Spectating and editing do not count as active play.":"Participation removed; character card and automatic destinations are unchanged.";
            }
            case LEAVE -> {return command(source,"chronicle leave");}
            case CARD_OPEN -> {
                UUID owner=UUID.fromString(id);if(!source.hasPermissionLevel(2)&&!owner.equals(player.getUuid()))throw new IllegalArgumentException("Choose your own card.");
                var target=server.getPlayerManager().getPlayer(owner);if(target==null)throw new IllegalStateException("This account is offline; its saved public card remains available through the profile service.");
                return command(source,"chronicle profile open "+target.getName().getString());
            }
            case STARTING_SET,RESPAWN_SET -> {MenuPlayerDestinations.set(source,UUID.fromString(id),arg,request.action()==Action.STARTING_SET);return "Destination saved for this player. No player moved now; existing claim history is retained.";}
            case STARTING_OFF,RESPAWN_OFF -> {AutomaticPlayerSpawns.disable(source,UUID.fromString(id),request.action()==Action.STARTING_OFF);return "Automatic destination disabled. Existing placement history and vanilla respawn are retained.";}
            case STARTING_DEFAULT,RESPAWN_DEFAULT -> {AutomaticPlayerSpawns.useRoleDefault(source,UUID.fromString(id),request.action()==Action.STARTING_DEFAULT);return "Role destination policy selected. No player moved and no supplies repeated.";}
            case STARTING_RESET -> {AutomaticPlayerSpawns.reset(source,UUID.fromString(id));return "Starting-position claim reset deliberately; the next eligible entry into play may place this player again.";}
            case PLAYER_MOVE_REVIEW -> {String result=MenuPlayerDestinations.review(source,UUID.fromString(id),arg);c.reviewId=id;c.reviewFrame=frame(player);c.reviewTime=System.nanoTime();return result;}
            case PLAYER_MOVE -> {requireReview(player,c,id);c.reviewId="";return MenuPlayerDestinations.move(source,UUID.fromString(id));}
            case EVENT_PREVIEW -> {
                if(id.startsWith("world:"))return AreaWorldEvents.preview(player,id);
                if(id.startsWith("cue:")){var d=cue(id);previewCue(player,d);return d.text()+"\nSound: "+(d.sound().isBlank()?"none":d.sound())+"\nPreview sent only to you. No event completion or trigger changed.";}
                var event=gameEvent(id);return event.description()+"\nState action only. Required conditions: "+event.requiredFlags()+". This preview does not invoke its handler.";
            }
            case EVENT_AUDIO -> {
                var d=cue(id);if(d.sound().isBlank())throw new IllegalStateException("This event has no sound.");
                previewCue(player,d);return "Message and sound previewed only to you. No event completion, timer or world state changed.";
            }
            case EVENT_CHECK,EVENT_REVIEW -> {
                if(id.startsWith("world:")){requireAreaOperator(player);String result=AreaWorldEvents.review(player,id);if(request.action()==Action.EVENT_REVIEW){c.reviewId=id;c.reviewFrame=frame(player);c.reviewTime=System.nanoTime();}return result;}
                String result;
                if(id.startsWith("cue:")){var preview=PlayRuntime.preview(server,raw(id));result=preview.description();if(request.action()==Action.EVENT_REVIEW)c.observation=preview;}
                else {result=ChronicleRuntime.service(server).preview(raw(id));if(!result.equals("Ready."))throw new IllegalStateException(result);if(RuntimeHolds.reason(server)!=null)throw new IllegalStateException("Resolve the active operation hold first.");}
                if(request.action()==Action.EVENT_REVIEW){c.reviewId=id;c.reviewFrame=frame(player);c.reviewTime=System.nanoTime();}return result;
            }
            case EVENT_RUN -> {
                requireReview(player,c,id);c.reviewId="";
                if(id.startsWith("world:"))return AreaWorldEvents.run(source,id);
                if(id.startsWith("cue:")){var preview=c.observation;c.observation=null;PlayRuntime.fire(server,raw(id),preview,player.getUuid());return "Event delivered once. Manual and automatic runs share completion history.";}
                return command(source,"chronicle event trigger "+quote(raw(id)));
            }
            case EVENT_SAVE -> {var config=ObservationConfiguration.decode(arg);var old=PlayRuntime.service(server).state().bindings().get(raw(id));PlayRuntime.bind(source,raw(id),config,old!=null);return "Event configuration saved atomically. Existing completion is retained; no event ran now.";}
            case EVENT_DISABLE_AUTO -> {var old=PlayRuntime.service(server).state().bindings().get(raw(id));if(old==null)throw new IllegalStateException("Configure the event first.");PlayRuntime.bind(source,raw(id),new ObservationConfiguration(PlayState.Mode.MANUAL,old.regionId(),old.requiredFlag(),0),true);return "Automatic triggering disabled. Audience, prerequisites and completion remain; manual control is available.";}
            case EVENT_REMOVE -> {PlayRuntime.off(source,raw(id));return "Event configuration removed. Its registered definition and completion history remain.";}
            case EVENT_RESET -> {PlayRuntime.reset(source,raw(id));return "Another run is now permitted; configured timer delay starts again. No event ran now.";}
            case CONDITION_SET -> {return command(source,"chronicle flag set "+quote(raw(id))+" "+parseBoolean(arg));}
            case CHEST_BIND -> {return command(source,"chronicle author chest bind "+quote(arg));}
            case TABLE_OPEN -> {c.page=Page.LOOT_TABLES;c.selected=chest(server,id).lootType();c.expectedLoot=null;return "Opened the chest's assigned loot table.";}
            case CHEST_RETYPE,CHEST_UNBIND -> {
                editor(player);var binding=chest(server,id);
                if(request.action()==Action.CHEST_UNBIND){ChestAuthoring.service(server).removeTypedBinding(id);return "Chest unbound. Contents and fill history are unchanged.";}
                if(!TypedChestLoot.types(server).containsKey(arg))throw new IllegalArgumentException("Unknown loot table.");
                ChestAuthoring.resolve(server,binding.directTarget());ChestAuthoring.service(server).putTypedBinding(arg,binding.directTarget(),true);return "Chest table changed. Existing contents and fill history are unchanged.";
            }
            case LOOT_SAVE -> {var type=LootTableDraftJson.decode(arg);if(!type.id().equals(id)||c.expectedLoot==null||!id.equals(c.expectedLoot.id()))throw new IllegalArgumentException("Review this loot table before saving.");TypedChestLoot.saveType(source,type,c.expectedLoot);c.expectedLoot=type;return "Loot table saved. Future fills use these independent entries; no chest filled now.";}
            case BOOK_OPEN -> {if(!BookRuntime.books(server).containsKey(arg))throw new IllegalArgumentException("Choose a registered book.");c.page=Page.BOOKS_LORE;c.selected=arg;c.expectedBook=null;return "";}
            case CHEST_BOOK_SET -> {
                if(c.page!=Page.CHESTS)throw new IllegalArgumentException("Choose a bound chest.");
                BookChance direct=null;if(!arg.equals("clear")){var json=BookConfig.strictObject(arg);if(!json.keySet().equals(Set.of("bookId","chancePercent"))||!json.get("bookId").isJsonPrimitive()||!json.get("bookId").getAsJsonPrimitive().isString()||!json.get("chancePercent").isJsonPrimitive()||!json.get("chancePercent").getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Choose one book and a finite chance.");direct=new BookChance(json.get("bookId").getAsString(),json.get("chancePercent").getAsDouble());}
                BookDeliveryRuntime.setDirect(source,id,chest(server,id).revision(),direct);return "Direct book setting saved. No chest filled and no history reset.";
            }
            case BOOK_KEEP -> {
                if(!source.hasPermissionLevel(2)||ChronicleRuntime.service(server).state().phase()!=io.github.chronicle.core.campaign.SessionPhase.ACTIVE)throw new IllegalStateException("Keep placed until found requires active play and an operator.");
                if(c.page==Page.BOOKS_LORE){c.keepBook=id;c.keepTable=arg;}
                else if(c.page==Page.LOOT_TABLES){c.keepBook=arg;c.keepTable=id;}
                else if(c.page==Page.CHESTS){c.keepBook=arg;c.keepTable=chest(server,id).lootType();}
                else throw new IllegalArgumentException("Choose a book, loot table or bound chest.");
                if(!BookRuntime.books(server).containsKey(c.keepBook))throw new IllegalArgumentException("Choose a registered book.");
                var existingKeep=ChestAuthoring.service(server).state().bookDelivery().keepRequests().get(c.keepBook);
                if(!TypedChestLoot.types(server).containsKey(c.keepTable)&&(existingKeep==null||existingKeep.status()==BookKeepRequest.Status.CANCELLED))throw new IllegalArgumentException("Choose a registered loot table.");
                var keep=BookDeliveryRuntime.keepForm(server,c.keepBook,c.keepTable);c.keepTable=keep.tableId();return "Review keeping this book available across the selected table's chests.";
            }
            case BOOK_KEEP_START,BOOK_KEEP_STOP,BOOK_KEEP_REVIEW -> {
                var json=BookConfig.strictObject(arg);if(!json.keySet().equals(Set.of("bookId","tableId","override"))||!json.get("bookId").isJsonPrimitive()||!json.get("bookId").getAsJsonPrimitive().isString()||!json.get("tableId").isJsonPrimitive()||!json.get("tableId").getAsJsonPrimitive().isString()||!json.get("override").isJsonPrimitive()||!json.get("override").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Review the selected keep request.");
                if(!json.get("bookId").getAsString().equals(c.keepBook)||!json.get("tableId").getAsString().equals(c.keepTable))throw new IllegalStateException("Keep request selection changed; review it again.");
                return BookDeliveryRuntime.keep(source,c.keepBook,c.keepTable,json.get("override").getAsBoolean(),request.action()==Action.BOOK_KEEP_STOP?"stop":request.action()==Action.BOOK_KEEP_REVIEW?"review":"start");
            }
            case BOOK_GIVE_OPEN -> {
                if(c.page!=Page.BOOKS_LORE)throw new IllegalStateException("Give books under Books & Lore.");
                BookGifts.requireActive(source);var definition=BookRuntime.books(server).get(id);if(definition==null)throw new IllegalArgumentException("Choose a registered book.");
                c.giveBook=id;c.giveTarget="";c.giveDefinition=definition;c.giveAt=0;return "Choose an active participant; no copy has been given.";
            }
            case BOOK_GIVE_REVIEW -> {
                if(c.page!=Page.BOOKS_LORE||!id.equals(c.giveBook)||c.giveDefinition==null||!c.giveDefinition.equals(BookRuntime.books(server).get(id)))throw new IllegalStateException("Reopen Give book to player for the selected book.");
                BookGifts.requireActive(source);var target=BookGifts.recipient(server,arg);
                c.giveTarget=target.getUuidAsString();c.giveAt=System.nanoTime();return "Confirm the book and selected player. No copy has been given.";
            }
            case BOOK_GIVE -> {
                if(c.page!=Page.BOOKS_LORE||!id.equals(c.giveBook)||!arg.equals(c.giveTarget)||c.giveDefinition==null||c.giveAt==0||System.nanoTime()-c.giveAt>30_000_000_000L)throw new IllegalStateException("Review the selected player again; confirmation expires after 30 seconds.");
                var expected=c.giveDefinition;var target=c.giveTarget;clearGive(c); // Consume review even on failure; no packet replay.
                return BookGifts.give(source,expected,target);
            }
            case BOOK_CREATE -> {
                if(c.page!=Page.BOOKS_LORE)throw new IllegalArgumentException("Books are managed under Books & Lore.");
                var created=BookRuntime.create(source,BookDraftJson.decode(arg));c.selected=created.id();c.expectedBook=created;
                return "Book created with distribution off. Edit its pages and rule when ready.";
            }
            case BOOK_SAVE -> {
                if(c.page!=Page.BOOKS_LORE)throw new IllegalArgumentException("Books are managed under Books & Lore.");
                var replacement=BookDraftJson.decode(arg);
                if(!replacement.id().equals(id)||c.expectedBook==null)throw new IllegalArgumentException("Review this book before saving.");
                BookRuntime.save(source,replacement,c.expectedBook);c.expectedBook=replacement;
                return "Book saved. Existing physical books and discovery history are unchanged.";
            }
            case BOOK_RELOAD -> {BookRuntime.reload(source);c.expectedBook=null;return "Book configuration reloaded. Existing books and discovery history retained.";}
            case LOOT_RELOAD -> {String result=command(source,"chronicle author chest reload");c.expectedLoot=null;return result;}
            case REGION_NEW -> {AuthoringRuntime.get(server).beginRegionDraft(player,arg);c.selected=arg;return "Region selection is active. Use the Debug shovel or pos1 and pos2, then /chronicle region save.";}
            case REGION_EDIT -> {AuthoringRuntime.get(server).resumeRegionDraft(player,id);return "Both saved bounds loaded. Change either position independently; the region ID stays the same.";}
            case REGION_POSITION -> {
                editor(player);requireSelectedDraft(player,id);int colon=arg.indexOf(':');if(colon!=1)throw new IllegalArgumentException("Choose position 1 or 2 and three integer coordinates.");
                AuthoringRuntime.get(server).setRegionDraftCorner(player,Integer.parseInt(arg.substring(0,1)),position(arg.substring(2)));return "Draft position updated; the other position is retained. Save to apply the bounds.";
            }
            case REGION_CONTINUE -> {requireSelectedDraft(player,id);AuthoringRuntime.get(server).resumeRegionDraft(player);RegionAuthoringWorkflow.instructions(player);return "Region selection is active. Select corners, then /chronicle region save.";}
            case REGION_SAVE_REVIEW -> {editor(player);requireSelectedDraft(player,id);return AuthoringRuntime.get(server).reviewRegionDraft(player);}
            case REGION_CREATE,REGION_SAVE -> {requireSelectedDraft(player,id);var saved=AuthoringRuntime.get(server).saveRegionDraft(player);c.selected=saved.id();return "Region saved under the same ID. Existing revision-pinned consumers must be deliberately revalidated after bounds changes.";}
            case REGION_ASSIGN -> {String purpose=regionPurpose(c,arg);requireSelectedRegion(server,id);MapSiteAssignments.assign(source,purpose,id);return "Region use saved at its current revision. Its bounds were not copied or changed.";}
            case REGION_UNASSIGN -> {String purpose=regionPurpose(c,arg);requireSelectedRegion(server,id);var binding=MapSiteAssignments.bindings(server).get(purpose);if(binding==null||!binding.siteId().equals(id))throw new IllegalStateException("This purpose is not assigned to the selected region.");MapSiteAssignments.clear(source,purpose);return "Region use cleared; the saved region is retained.";}
            case POINT_ASSIGN -> {String purpose=pointPurpose(c,arg);requireSelectedPoint(server,id);MapSiteAssignments.assign(source,purpose,id);return "Point use saved at its current revision. Its position was not copied or changed.";}
            case POINT_UNASSIGN -> {String purpose=pointPurpose(c,arg);requireSelectedPoint(server,id);var binding=MapSiteAssignments.bindings(server).get(purpose);if(binding==null||!binding.siteId().equals(id))throw new IllegalStateException("This purpose is not assigned to the selected point.");MapSiteAssignments.clear(source,purpose);return "Point use cleared; the saved point is retained.";}
            case POINT_CREATE,POINT_UPDATE,BLOCK_CREATE,BLOCK_UPDATE -> {
                boolean point=request.action()==Action.POINT_CREATE||request.action()==Action.POINT_UPDATE;
                boolean update=request.action()==Action.POINT_UPDATE||request.action()==Action.BLOCK_UPDATE;
                String siteId=manualSiteId(server,update?id:arg);
                if(!point&&lookedAtManagedBlock(player))throw new IllegalStateException("Supplied managed blocks register automatically and cannot be bound as manual block sites.");
                String result=command(source,"chronicle author "+(point?"location ":"block ")+(update?"replace ":point?"save ":"bind ")+quote(siteId)+(point?" here":" target"));c.selected=siteId;return result;
            }
            case BLOCK_LIMITS -> {if(c.page!=Page.SETUP)throw new IllegalArgumentException("Block limits are under Game setup.");if(ManagedBlocks.snapshot(server).types().stream().noneMatch(type->type.typeId().equals(arg)))throw new IllegalArgumentException("Choose a registered managed block type.");c.selected="limit:"+arg;return "Opened managed block limit.";}
            case BLOCK_LIMIT_SET -> {editor(player);if(c.page!=Page.SETUP)throw new IllegalArgumentException("Block limits are under Game setup.");if(!id.startsWith("limit:"))throw new IllegalArgumentException("Choose a managed block type.");int value=Integer.parseInt(arg);ManagedBlocks.setLimit(source,id.substring("limit:".length()),value,c.managedPolicyRevision);return "Managed block limit saved. Existing blocks remain in place.";}
            case MANAGED_SHOW -> {if(!id.startsWith("managed:"))throw new IllegalArgumentException("Choose a managed block site.");ManagedBlocks.preview(player,UUID.fromString(id.substring("managed:".length())));return "Private managed block-site outline shown without loading chunks.";}
            case MANUAL_REFERENCES -> {if(!id.startsWith("managed:")||!arg.startsWith("manual:"))throw new IllegalArgumentException("Choose a manual reference from this managed block site.");var instance=managedInstance(server,id);String alias=arg.substring("manual:".length());if(manualAliases(server,instance).stream().noneMatch(anchor->anchor.id().equals(alias)))throw new IllegalArgumentException("That manual reference is no longer at this managed block site.");c.selected=arg;return "Opened manual reference.";}
            case SITE_DELETE -> {var key=manualSiteId(server,id);if(sites(server).get(key) instanceof AuthoringSite.Region&&AreaChanges.forRegion(server,key).isPresent())throw new IllegalStateException("Remove Saved states before deleting this Region.");return command(source,"chronicle author remove "+quote(key));}
            case SETUP_ASSIGN,SETUP_CLEAR -> throw new IllegalStateException("Geometry uses are managed under Regions or Locations.");
            case TOOL_ACTION -> {return GameSetupTools.execute(source, toolId(id), arg);}
            case SHOW -> {
                if(c.page==Page.CHESTS){var t=chest(server,id).directTarget();AuthoringRuntime.get(server).preview(player,new AuthoringSite.Anchor("preview",t.dimension(),1,new BlockPosition(t.x(),t.y(),t.z()),t.expectedBlock()));return "Private chest outline shown.";}
                if(c.page==Page.SETUP)throw new IllegalStateException("Game setup contains global tools only.");
                return command(source,"chronicle author show "+quote(manualSiteId(server,id)));
            }
            default -> throw new IllegalArgumentException("Unsupported menu action.");
        }
    }

    /** Samples the registered media directly to the caller, without entering live delivery/claims. */
    private static void previewCue(ServerPlayerEntity player,ObservationCatalog.Definition definition) {
        var sound=definition.sound().isBlank()?null:Registries.SOUND_EVENT.getOptionalValue(Identifier.of(definition.sound())).orElseThrow(()->new IllegalStateException("Sound is unavailable: "+definition.sound()));
        player.sendMessage(Text.literal(definition.text()),false);
        if(sound!=null)player.playSoundToPlayer(sound,SoundCategory.AMBIENT,1F,1F);
    }

    private static void requireReview(ServerPlayerEntity player,Context c,String id){if(!c.reviewId.equals(id)||!Objects.equals(c.reviewFrame,frame(player))||System.nanoTime()-c.reviewTime>30_000_000_000L)throw new IllegalStateException("Review this exact action and its current targets first; reviews expire after 30 seconds.");}
    private static void requireSelectedDraft(ServerPlayerEntity player,String id){var draft=AuthoringRuntime.get(player.getEntityWorld().getServer()).regionDraft(player.getUuid()).orElseThrow(()->new IllegalStateException("Open a region draft first."));if(!draft.id().equals(id))throw new IllegalStateException("Select the current region draft before changing or saving it.");}
    private static String eventLabel(String id) {
        String path=id.substring(id.indexOf(':')+1).replace('_',' ').replace('.',' ').replace('/',' ').toLowerCase(Locale.ROOT);
        return path.isBlank()?id:Character.toUpperCase(path.charAt(0))+path.substring(1);
    }
    private static String parseBoolean(String value){if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Choose true or false.");return value;}
    private static BlockPosition position(String value){var parts=value.trim().split("\\s+");if(parts.length!=3)throw new IllegalArgumentException("Enter X Y Z as three integer coordinates.");return new BlockPosition(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));}

    private static void send(ServerPlayerEntity player,Context c,String message,boolean success,boolean open,boolean close) {
        if(!ServerPlayNetworking.canSend(player,Response.ID))return;
        var server=player.getEntityWorld().getServer();boolean gm=player.getCommandSource().hasPermissionLevel(2);
        if(!gm){c.page=Page.PLAYERS;c.selected=player.getUuidAsString();c.picker=null;}
        Frame frame=frame(player);if(c.token==0||!Objects.equals(frame,c.frame))c.token=ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE);c.frame=frame;
        List<Entry> all;
        try {all=entries(player,c.page,c.filter);}catch(RuntimeException exception){all=List.of();message="Service unavailable: "+exception.getMessage();success=false;}
        int pages=Math.max(1,(all.size()+11)/12);c.index=Math.min(c.index,pages-1);
        if(c.page==Page.REGIONS&&c.selected.isBlank()&&gm&&AuthoringRuntime.isAuthor(player))AuthoringRuntime.get(server).regionDraft(player.getUuid()).ifPresent(d->c.selected=d.id());
        boolean selectedManualAlias=c.page==Page.LOCATIONS&&"BLOCK".equals(c.filter)&&c.selected.startsWith("manual:")&&sites(server).get(c.selected.substring("manual:".length())) instanceof AuthoringSite.Anchor;

        if(c.page==Page.LOCATIONS&&!c.selected.isBlank()&&!selectedManualAlias&&all.stream().noneMatch(e->e.id().equals(c.selected)))c.selected="";
        if(c.selected.isBlank()&&!all.isEmpty())c.selected=all.get(c.index*12).id();
        Detail detail;
        try{detail=detail(player,c);}catch(RuntimeException exception){ChronicleCore.LOGGER.warn("Chronicle menu detail unavailable for page {} selection {}",c.page,c.selected,exception);detail=new Detail(c.selected,"Service unavailable",shortText(Objects.requireNonNullElse(exception.getMessage(),"The selected editor is unavailable. Inspect the server log."),2048),List.of(),List.of(),null,null,null);}
        var campaign=ChronicleRuntime.service(server).state();
        String clock=gm?"Active play: "+PlayRuntime.elapsedTicks(server)/20+"s":"";
        var view=new View(c.token,c.page,c.index,pages,c.filter,c.selected,campaign.phase().name(),campaign.sessionNumber(),gm&&AuthoringRuntime.isAuthor(player),gm&&AuthoringRuntime.editable(server),gm,gm?Objects.requireNonNullElse(RuntimeHolds.reason(server),""):"",clock,shortText(message,4096),success,all.subList(c.index*12,Math.min((c.index+1)*12,all.size())),detail,c.picker,close,blockLimitsForm(player,c));
        ServerPlayNetworking.send(player,new Response(view,open));
    }

    private static BlockLimitsForm blockLimitsForm(ServerPlayerEntity player,Context c) {
        if(!player.getCommandSource().hasPermissionLevel(2)||c.page!=Page.SETUP
                ||!(c.selected.equals("limits")||c.selected.startsWith("limit:")
                ||c.picker!=null&&c.picker.kind().equals("BLOCK_LIMIT_TYPE")))return null;
        var server=player.getEntityWorld().getServer();var snapshot=ManagedBlocks.snapshot(server);
        String selected=c.selected.startsWith("limit:")?c.selected.substring(6):"";
        var rows=new ArrayList<BlockLimitType>();
        if(c.picker!=null&&c.picker.kind().equals("BLOCK_LIMIT_TYPE")) {
            for(var entry:c.picker.entries())snapshot.types().stream().filter(type->type.typeId().equals(entry.id())).findFirst()
                    .ifPresent(type->rows.add(new BlockLimitType(type.typeId(),type.blockId(),type.label(),type.description(),type.count(),type.limit(),type.fixed())));
            selected="";
        } else if(!selected.isBlank()) {
            String wanted=selected;
            snapshot.types().stream().filter(type->type.typeId().equals(wanted)).findFirst()
                    .ifPresent(type->rows.add(new BlockLimitType(type.typeId(),type.blockId(),type.label(),type.description(),type.count(),type.limit(),type.fixed())));
            if(rows.isEmpty())selected="";
        }
        boolean editable=snapshot.ready()&&AuthoringRuntime.isAuthor(player)&&AuthoringRuntime.editable(server);
        String reason=!snapshot.ready()?snapshot.problem():!AuthoringRuntime.editable(server)?"Pause play and resolve any operation hold to edit limits.":"Enable editing to change this limit.";
        return new BlockLimitsForm(snapshot.policyRevision(),snapshot.ready(),shortText(snapshot.problem(),512),editable,
                editable?"":shortText(reason,512),selected,rows);
    }

    private static List<Entry> entries(ServerPlayerEntity player,Page page,String filter) {
        var server=player.getEntityWorld().getServer();
        if(!player.getCommandSource().hasPermissionLevel(2))return List.of(entry(player.getUuidAsString(),player.getName().getString(),"Your participation and public character card","PLAYER","",0));
        return switch(page) {
            case SESSION -> List.of(entry("play","Play controls","Run or freeze the continuing world","SESSION","",0),entry("activity","Activity","Recent Chronicle activity","HISTORY","",0));
            case PLAYERS -> {
                var players=new TreeMap<String,String>();var play=PlayRuntime.available(server);if(play!=null)play.participants().forEach(id->players.put(id.toString(),id.toString()));
                var automatic=AutomaticPlayerSpawns.available(server);if(automatic!=null){automatic.policies().keySet().forEach(id->players.put(id.toString(),id.toString()));automatic.arrivals().keySet().forEach(id->players.put(id.toString(),id.toString()));}
                if(PlayerRoles.ready(server))PlayerRoles.assignments(server).assigned().keySet().forEach(id->players.put(id.toString(),server.getApiServices().nameToIdCache().getByUuid(id).map(p->p.name()).orElse(id.toString())));
                server.getPlayerManager().getPlayerList().forEach(p->players.put(p.getUuidAsString(),p.getName().getString()));
                yield players.entrySet().stream().map(e->entry(e.getKey(),e.getValue(),server.getPlayerManager().getPlayer(UUID.fromString(e.getKey()))==null?"Offline saved participant":"Online","PLAYER","",0)).toList();
            }
            case EVENTS -> {
                var result=new ArrayList<Entry>();result.addAll(AreaWorldEvents.entries(server));ChronicleCore.observations().definitions().forEach(d->result.add(entry("cue:"+d.id(),d.label(),d.text(),"CUE",d.id(),0)));
                ChronicleCore.events().events().forEach(e->result.add(entry("action:"+e.id(),eventLabel(e.id()),e.description(),"ACTION",e.id(),0)));
                ChronicleRuntime.service(server).state().flags().forEach((id,value)->result.add(entry("flag:"+id,id,"Game condition: "+value,"FLAG",id,0)));yield result;
            }
            case CHESTS -> ChestAuthoring.service(server).state().bindings().values().stream().filter(ChestBinding::typed).map(b->{var t=b.directTarget();var table=TypedChestLoot.types(server).get(b.lootType());return entry(b.id(),(table==null?b.lootType():table.label())+" @ "+t.x()+" "+t.y()+" "+t.z(),"Bound container · "+t.dimension(),"CHEST",b.lootType(),b.revision());}).toList();
            case PLAYER_ROLES -> !PlayerRoles.ready(server)?List.of():PlayerRoles.definitions(server).values().stream().map(r->entry(r.id(),r.name(),r.description(),"PLAYER_ROLE",r.id(),0)).toList();
            case AREA_CHANGES -> CoreAreaMenu.entries(server);
            case DEPARTURES -> CoreDepartureMenu.entries(server);
            case BOOKS_LORE -> BookRuntime.books(server).values().stream().map(b->entry(b.id(),b.title(),b.description(),"BOOK",b.id(),0)).toList();
            case LOOT_TABLES -> TypedChestLoot.types(server).values().stream().map(t->entry(t.id(),t.label(),t.description(),"LOOT",t.id(),0)).toList();
            case REGIONS -> regionEntries(server);
            case LOCATIONS -> "BLOCK".equals(filter)?locationBlockEntries(server):sites(server).values().stream().filter(AuthoringSite.Location.class::isInstance).map(CoreMenuServer::siteEntry).toList();
            case SETUP -> {
                var tools=new ArrayList<Entry>();
                tools.add(entry("limits","Block limits","Set how many supplied blocks may be placed.","LIMITS","",0));
                GameSetupTools.tools().forEach(tool->tools.add(entry("tool:"+tool.id(),tool.label(),tool.description(),"TOOL",tool.id(),0)));
                yield tools;
            }
        };
    }

    private static Detail detail(ServerPlayerEntity player,Context c) {
        var server=player.getEntityWorld().getServer();var source=player.getCommandSource();String id=c.selected;
        boolean gm=source.hasPermissionLevel(2),editing=gm&&AuthoringRuntime.isAuthor(player),active=ChronicleRuntime.service(server).state().phase().name().equals("ACTIVE");
        boolean canEdit=editing&&AuthoringRuntime.editable(server);String editReason=active?"Pause play to edit these settings.":"Enable editing to change these settings.";
        var facts=new ArrayList<Fact>();var controls=new ArrayList<Control>();String title="Choose an item",description="Select an item in the middle to inspect its details.";
        LootType loot=null;EventForm event=null;RegionForm region=null;BookDefinition book=null;LoreForm lore=null;RoleForm roleForm=null;RoleAssignmentForm roleAssignment=null;AreaForm areaForm=null;DepartureForm departureForm=null;
        switch(c.page) {
            case PLAYER_ROLES -> {
                title="Player Roles";description="Define characters or shared backgrounds, their starting supplies and their destinations.";
                controls.add(control(Action.ROLE_RELOAD,"Reload role YAML",canEdit&&!RuntimeHolds.heldByOther(server,"chronicle_core:player_roles"),editReason,true,""));
                if(!PlayerRoles.ready(server)){description="Role configuration is unavailable. Correct player-roles.yaml or restore the role ledger, then reload.";break;}
                controls.add(control(Action.ROLE_NEW,"Create new role",canEdit&&!held(server),editReason,false,""));
                var r=c.roleDraft!=null&&c.roleDraft.id().equals(id)?c.roleDraft:PlayerRoles.definitions(server).get(id);
                if(r!=null){
                    title=r.name();description=r.description();roleForm=PlayerRoles.editorForm(server,r,c.roleCreating);
                    if(c.roleDraft==null)c.expectedRole=r;
                    facts.add(new Fact("Role ID",r.id()));facts.add(new Fact("Assigned accounts",PlayerRoles.assigned(server,r.id())+" / "+r.capacity()));
                    facts.add(new Fact("Selection",r.selectable()?"Players may choose this role before first entry":"GM assignment only"));
                    controls.add(control(Action.ROLE_EDIT,"Edit role",canEdit,editReason,false,""));
                    controls.add(control(Action.ROLE_DELETE,"Delete role",canEdit&&PlayerRoles.assigned(server,r.id())==0,canEdit?"Reassign its accounts before deleting this role.":editReason,true,""));
                }
            }
            case AREA_CHANGES -> {return CoreAreaMenu.detail(player,id);}
            case DEPARTURES -> {return CoreDepartureMenu.detail(player,id,c.departureDraft,c.departureCreating);}
            case SESSION -> {
                title=id.equals("activity")?"Activity":"Play controls";
                description="Start, pause or finish a play window. The world and story carry on between meetings.";
                var campaign=ChronicleRuntime.service(server).state();
                if(id.equals("activity")) {
                    campaign.history().stream().skip(Math.max(0,campaign.history().size()-12)).forEach(a->{
                        facts.add(new Fact("Recent activity",a.timestamp()+" - "+a.actor().name()+": "+eventLabel(a.action())));
                        facts.add(new Fact("Audit detail",shortText(a.target()+" - "+a.parameters(),4096)));
                    });
                    var play=PlayRuntime.available(server);if(play!=null)play.deliveries().values().stream().limit(8).forEach(d->facts.add(new Fact("Cue delivery",eventLabel(d.id())+" - "+d.status()+" - "+d.recipients().size()+" players")));
                    var autos=AutomaticPlayerSpawns.available(server);if(autos!=null)autos.arrivals().entrySet().stream().limit(8).forEach(a->facts.add(new Fact("Starting-position history",a.getKey()+" - "+a.getValue().status()+" - "+a.getValue().detail())));
                    facts.add(new Fact("Loot","Scheduled fills and interrupted-operation evidence remain in the chest journal. Inspection never refills a chest."));
                } else {
                    facts.add(new Fact("World",active&&RuntimeHolds.reason(server)==null?"Simulation running":"Frozen; players stay mobile and may edit their cards"));
                    facts.add(new Fact("Active play","Debug view shows the play clock. Paused, empty and held time does not count."));
                    facts.add(new Fact("Session boundaries","Discoveries and game progress are kept. Configured session-start loot refills run when a new play window starts."));
                    switch(campaign.phase()) {
                        case INACTIVE,ENDED -> controls.add(control(Action.SESSION_START,"Start play",true,"",true,""));
                        case PAUSED -> controls.add(control(Action.SESSION_RESUME,"Resume play",true,"",false,""));
                        case ACTIVE -> controls.add(control(Action.SESSION_PAUSE,"Pause play",true,"",false,""));
                    }
                    if(active||campaign.phase().name().equals("PAUSED"))controls.add(control(Action.SESSION_END,"Finish for now",true,"",true,""));
                    boolean building=SessionBuilding.enabled(server);
                    facts.add(new Fact("Building Mode",building?"On - anyone may change the map; survival remains frozen.":"Off - map interactions are frozen while play is paused or stopped."));
                    String buildReason=RuntimeHolds.reason(server)!=null?"Resolve the active Chronicle operation hold first.":active?"Building Mode requires a non-active Chronicle play state.":"";
                    controls.add(control(building?Action.SESSION_BUILD_OFF:Action.SESSION_BUILD_ON,
                            building?"Disable Building Mode":"Enable Building Mode",buildReason.isBlank(),buildReason,!building,""));
                }
            }
            case PLAYERS -> {
                if(id.isBlank())break;UUID uuid=UUID.fromString(id);var target=server.getPlayerManager().getPlayer(uuid);
                title=target==null?id:target.getName().getString();description="Manage participation, character cards and travel. A starting position is used once on first eligible entry.";
                var play=PlayRuntime.available(server);boolean member=play!=null&&play.participants().contains(uuid);
                facts.add(new Fact("Connection",target==null?"Offline":"Online"));facts.add(new Fact("Participation",member?"Participating":"Not participating"));
                facts.add(new Fact("Eligible now",PlayRuntime.eligibleIds(server).contains(uuid)?"Yes":"No: must be participating, online, alive, not spectating or editing"));
                controls.add(control(member?Action.PARTICIPANT_REMOVE:Action.PARTICIPANT_ADD,member?"Remove participation":"Add participation",!gm||canEdit,editReason,member,""));
                controls.add(control(Action.CARD_OPEN,uuid.equals(player.getUuid())?"Edit my character card":"Read character card",target!=null,"Select an online account for the card screen.",false,""));
                if(uuid.equals(player.getUuid()))controls.add(control(Action.LEAVE,"Leave Chronicle",true,"",true,""));
                if(PlayerRoles.ready(server)){
                    roleAssignment=PlayerRoles.form(player,uuid,c.pendingRole);
                    facts.add(new Fact("Player role",roleAssignment.roleName()));
                    controls.add(control(Action.ROLE_CHOICE_OPEN,gm?"Assign player role…":"Choose my role…",gm?roleAssignment.canAssign():roleAssignment.canChoose(),"Role selection is available before first entry; GM reassignment requires no operation hold.",false,"ROLE_CHOICE"));
                }
                if(gm) {
                    var state=AutomaticPlayerSpawns.available(server);var policy=state==null?null:state.policies().get(uuid);var attempt=state==null?null:state.arrivals().get(uuid);
                    var assignedRole=PlayerRoles.roleFor(server,uuid);boolean startOff=PlayerRoles.ready(server)&&PlayerRoles.assignments(server).startingDisabled().contains(uuid),respawnOff=PlayerRoles.ready(server)&&PlayerRoles.assignments(server).respawnDisabled().contains(uuid);
                    facts.add(new Fact("Starting position",policy!=null&&policy.arrival()!=null?"Manual: "+destination(server,policy.arrival().purpose()):!startOff&&assignedRole!=null&&assignedRole.starting()!=null?"Role default: "+assignedRole.starting().locationId():"Off / vanilla starting position"));
                    facts.add(new Fact("After death",policy!=null&&policy.respawn()!=null?"Manual: "+destination(server,policy.respawn().purpose()):!respawnOff&&assignedRole!=null&&assignedRole.respawn()!=null?"Role default: "+assignedRole.respawn().locationId():"Vanilla bed / anchor / world spawn"));
                    facts.add(new Fact("Starting-position history",attempt==null?"Never attempted":attempt.status()+" · "+attempt.detail()));
                    facts.add(new Fact("Saving settings","Saving does not move this player. Without an after-death destination, vanilla respawn still applies."));
                    controls.add(control(Action.PLAYER_MOVE_REVIEW,"Move player…",active&&target!=null&&!held(server),"Requires active play, online target and no unresolved operation.",false,"DESTINATION"));
                    if(c.reviewId.equals(id))controls.add(control(Action.PLAYER_MOVE,"Confirm player move",active&&target!=null&&!held(server),"Review a current safe destination first.",true,""));
                    controls.add(control(Action.STARTING_SET,"Set starting position…",canEdit&&state!=null,editReason,false,"DESTINATION"));
                    controls.add(control(Action.RESPAWN_SET,"Set after-death destination…",canEdit&&state!=null,editReason,false,"DESTINATION"));
                    if(policy!=null&&policy.arrival()!=null||assignedRole!=null&&assignedRole.starting()!=null&&!startOff)controls.add(control(Action.STARTING_OFF,"Disable starting position",canEdit,editReason,true,""));
                    if(policy!=null&&policy.respawn()!=null||assignedRole!=null&&assignedRole.respawn()!=null&&!respawnOff)controls.add(control(Action.RESPAWN_OFF,"Use vanilla respawn",canEdit,editReason,true,""));
                    if(assignedRole!=null&&(startOff||policy!=null&&policy.arrival()!=null))controls.add(control(Action.STARTING_DEFAULT,"Use role starting position",canEdit,editReason,true,""));
                    if(assignedRole!=null&&(respawnOff||policy!=null&&policy.respawn()!=null))controls.add(control(Action.RESPAWN_DEFAULT,"Use role after-death destination",canEdit,editReason,true,""));
                    if(attempt!=null&&policy!=null&&policy.arrival()!=null)controls.add(control(Action.STARTING_RESET,"Allow another placement attempt",canEdit,editReason,true,""));
                }
            }
            case EVENTS -> {
                if(id.isBlank())break;
                if(id.startsWith("world:"))return AreaWorldEvents.detail(player,id,c.reviewId.equals(id));
                if(id.startsWith("flag:")) {
                    title=raw(id);description="A game condition used by event triggers. Changing it can make an event ready to run.";
                    facts.add(new Fact("Value",Boolean.toString(ChronicleRuntime.service(server).state().flag(raw(id)))));
                    controls.add(control(Action.CONDITION_SET,"Set condition…",active,"Live condition changes require active play.",true,"TEXT"));break;
                }
                if(id.startsWith("cue:")) {
                    var d=cue(id);title=d.label();description="A message and optional sound for players. Preview is private and works before a trigger is configured.";
                    var state=PlayRuntime.available(server);var binding=state==null?null:state.bindings().get(raw(id));var delivery=state==null?null:state.deliveries().get(raw(id));
                    facts.add(new Fact("Content",d.text()));facts.add(new Fact("Sound",d.sound().isBlank()?"None":"Included"));if(!d.sound().isBlank())facts.add(new Fact("Sound ID",d.sound()));
                    facts.add(new Fact("Audience",d.audience()==ObservationCatalog.Audience.REGION?"Participants currently inside the selected region":"All participating players who can receive events"));
                    facts.add(new Fact("Completion",delivery==null?"Not delivered":delivery.status()+" · "+delivery.recipients().size()+" recipients"));
                    facts.add(new Fact("Configuration",binding==null?"Not configured; private preview is available":binding.mode()+" · region="+binding.regionId()+" · condition="+binding.requiredFlag()));
                    facts.add(new Fact("Entry/audience region","For an entry trigger, the same region also defines who receives a regional cue."));
                    event=new EventForm(binding==null?"MANUAL":binding.mode().name(),binding==null?"":binding.regionId(),binding==null?"":binding.requiredFlag(),binding==null?60:binding.delayTicks()/20,binding!=null,true,!d.sound().isBlank());
                    controls.add(control(Action.EVENT_PREVIEW,"Preview cue",true,"",false,""));
                    if(!d.sound().isBlank())controls.add(control(Action.EVENT_AUDIO,"Preview sound",true,"",false,""));
                    controls.add(control(Action.EVENT_CHECK,"Check audience",true,"",false,""));
                    controls.add(control(Action.EVENT_REVIEW,"Review run",active&&!held(server)&&binding!=null&&delivery==null,"Start play and save this cue's trigger first. A cue already run must be reset before it can run again.",false,""));
                    if(c.reviewId.equals(id))controls.add(control(Action.EVENT_RUN,"Run now",true,"",true,""));
                    controls.add(control(Action.EVENT_SAVE,"Save event configuration",canEdit,editReason,false,"EVENT_DRAFT"));
                    if(binding!=null){controls.add(control(Action.EVENT_DISABLE_AUTO,"Disable automatic trigger",canEdit,editReason,true,""));controls.add(control(Action.EVENT_REMOVE,"Remove event configuration",canEdit,editReason,true,""));}
                    if(delivery!=null)controls.add(control(Action.EVENT_RESET,"Allow event again",canEdit,editReason,true,""));
                } else {
                    var d=gameEvent(id);title=eventLabel(d.id());description="Game action. "+d.description();
                    facts.add(new Fact("Required conditions",d.requiredFlags().toString()));facts.add(new Fact("Completion",ChronicleRuntime.service(server).state().completedEvents().contains(raw(id))?"Completed":"Not completed"));
                    facts.add(new Fact("Readiness",ChronicleRuntime.service(server).preview(raw(id))));
                    controls.add(control(Action.EVENT_PREVIEW,"Preview action",true,"",false,""));controls.add(control(Action.EVENT_CHECK,"Check readiness",true,"",false,""));
                    controls.add(control(Action.EVENT_REVIEW,"Review run",active&&!held(server)&&ChronicleRuntime.service(server).preview(raw(id)).equals("Ready."),"Requires active play and satisfied conditions.",false,""));
                    if(c.reviewId.equals(id))controls.add(control(Action.EVENT_RUN,"Run now",true,"",true,""));
                }
            }
            case CHESTS -> {
                title="Bound chests";description="Choose a loot table for a physical chest. Edit possible items and refill settings in Loot Tables.";
                boolean supported=false;
                try{var target=ChestAuthoring.target(source);ChestAuthoring.supported(source.getWorld(),target);supported=true;facts.add(new Fact("Looked-at container",source.getWorld().getRegistryKey().getValue()+" · "+target.toShortString()+" · "+Registries.BLOCK.getId(source.getWorld().getBlockState(target).getBlock())));}
                catch(IllegalArgumentException|IllegalStateException exception){facts.add(new Fact("Looked-at container","No supported container in reach"));}
                controls.add(control(Action.CHEST_BIND,"Bind looked-at chest…",canEdit&&supported,canEdit?"Look at a supported chest, barrel or shulker box within reach.":editReason,true,"LOOT_TYPE"));
                if(!id.isBlank()) {
                    var b=chest(server,id);var t=b.directTarget();title="Chest at "+t.x()+" "+t.y()+" "+t.z();
                    facts.add(new Fact("Loot table",b.lootType()));facts.add(new Fact("Container",t.dimension()+" · "+t.expectedBlock()));facts.add(new Fact("Fill behavior","Session-start and timed refills replace all contents. Binding or viewing this chest does not fill it."));
                    controls.add(control(Action.SHOW,"Show chest",true,"",false,""));controls.add(control(Action.TABLE_OPEN,"Open loot table",true,"",false,""));controls.add(control(Action.CHEST_RETYPE,"Change loot table…",canEdit,editReason,true,"LOOT_TYPE"));controls.add(control(Action.CHEST_UNBIND,"Unbind chest",canEdit,editReason,true,""));
                }
            }
            case BOOKS_LORE -> {
                title="Books & Lore";description="Create named books, edit their pages and choose how they appear in the world.";
                controls.add(control(Action.BOOK_NEW,"New book...",canEdit,editReason,false,""));
                if(!id.isBlank()){
                    book=BookRuntime.books(server).get(id);
                    if(book!=null){
                        title=book.title();description=book.description();
                        facts.add(new Fact("Book ID",book.id()));facts.add(new Fact("Author",book.author()));facts.add(new Fact("Pages",Integer.toString(book.pages().size())));facts.add(new Fact("Distribution",book.rule().name()));
                        c.expectedBook=book;
                        controls.add(control(Action.BOOK_EDIT_DETAILS,"Edit details",canEdit,editReason,false,""));
                        controls.add(control(Action.BOOK_EDIT_PAGES,"Edit pages",canEdit,editReason,false,""));
                        controls.add(control(Action.BOOK_RULE,"Distribution...",canEdit,editReason,false,""));
                    }
                }
                controls.add(control(Action.BOOK_RELOAD,"Reload book configuration",canEdit,editReason,true,""));
            }
            case LOOT_TABLES -> {
                title="Loot Tables";description="Reusable independent item rolls. Scheduled fills replace all managed chest contents; changes do not fill immediately.";
                if(!id.isBlank()) {
                    var t=TypedChestLoot.types(server).get(id);if(t==null)break;title=t.label();
                    facts.add(new Fact("Description",t.description()));facts.add(new Fact("Entries",t.entries().size()+" / "+LootType.MAX_ENTRIES));
                    facts.add(new Fact("Recommended chests",Integer.toString(t.recommendedChests())));facts.add(new Fact("Bound chests",Long.toString(ChestAuthoring.service(server).state().bindings().values().stream().filter(b->t.id().equals(b.lootType())).count())));
                    if(LootTableDraftJson.encode(t).length()<=48000){loot=t;if(c.expectedLoot==null||!c.expectedLoot.id().equals(id))c.expectedLoot=t;controls.add(control(Action.LOOT_SAVE,"Save loot table",canEdit,editReason,true,"LOOT_DRAFT"));}
                    else facts.add(new Fact("Advanced components","This existing table exceeds the native editor payload budget. Its JSON remains supported and unchanged."));
                }
                controls.add(control(Action.LOOT_RELOAD,"Reload loot configuration",canEdit,editReason,true,""));
            }
            case REGIONS -> {
                title="Regions";description="Create a Region, select its two corners with the Debug shovel, then /chronicle region save.";
                var draft=canEdit?AuthoringRuntime.get(server).regionDraft(player.getUuid()).orElse(null):null;
                controls.add(control(Action.REGION_NEW,"Create region…",canEdit,editReason,draft!=null,"ID"));
                var unassigned=ChronicleCore.authoring().roles().stream().filter(role->role.kind()==AuthoringCatalog.Kind.REGION).filter(role->!MapSiteAssignments.bindings(server).containsKey(role.id())).map(AuthoringCatalog.Role::label).toList();
                if(!id.isBlank()&&sites(server).get(id) instanceof AuthoringSite.Region saved) {
                    title=saved.id();description="A named part of the world. Edit its bounds with the Debug shovel; optional subtypes add behavior.";facts.add(new Fact("Saved bounds",saved.min()+" to "+saved.max()));facts.add(regionUsage(server,id));facts.add(new Fact("Other references",nonRegionReferences(server,id)));
                    controls.add(control(Action.SHOW,"Preview bounding box",true,"",false,""));controls.add(control(Action.SITE_SUBTYPES_OPEN,"Choose subtypes",true,"",false,""));controls.add(control(Action.REGION_EDIT,"Edit region",canEdit,editReason,draft!=null&&!draft.id().equals(id),""));controls.add(control(Action.SITE_DELETE,"Delete region",canEdit,editReason,true,""));
                }
                var linked=id.isBlank()?java.util.Optional.<io.github.chronicle.core.areas.AreaChange>empty():AreaChanges.forRegion(server,id);if(linked.isPresent()){var capability=CoreAreaMenu.detail(player,linked.orElseThrow().id());areaForm=capability.area();controls.addAll(capability.controls().stream().filter(control->control.action()!=Action.AREA_PREVIEW).toList());facts.addAll(capability.facts());description="This Region can capture and apply saved Start and End states, including container contents.";}
                if(draft!=null&&draft.id().equals(id)) {
                    title="Region draft · "+draft.id();region=new RegionForm(draft.id(),draft.dimension(),draft.baseRevision(),coordinates(draft.first()),coordinates(draft.second()));
                    facts.add(new Fact("Step 1","Continue in world; stand at a corner and run /chronicles author region pos1 or pos2. Each changes only its own position."));
                    facts.add(new Fact("Position 1",coordinates(draft.first())));facts.add(new Fact("Position 2",coordinates(draft.second())));facts.add(new Fact("Step 2","Return here to save. If a game uses the old bounds, review that use again after saving."));
                    controls.add(control(Action.REGION_CONTINUE,"Continue in world",canEdit,editReason,false,""));
                    controls.add(control(Action.REGION_POSITION,"Change position 1…",canEdit,editReason,false,"POSITION1"));controls.add(control(Action.REGION_POSITION,"Change position 2…",canEdit,editReason,false,"POSITION2"));
                    controls.add(control(draft.existing()?Action.REGION_SAVE:Action.REGION_CREATE,draft.existing()?"Save region changes":"Create selected region",canEdit&&draft.complete(),"Select both positions in the same dimension first.",draft.existing(),""));
                }
            }
            case LOCATIONS -> {
                boolean block="BLOCK".equals(c.filter);title=block?"Block sites":"Points";description=block?"Placed game blocks register themselves. You can also save a reference to a vanilla or other block.":"A saved position and facing for game systems. No block is required.";
                if(block){
                    facts.add(new Fact("Looked-at block",player.getEntityWorld().getRegistryKey().getValue()+" / "+targetKey(player)));
                    var snapshot=ManagedBlocks.snapshot(server);
                    if(!snapshot.ready())facts.add(new Fact("Managed blocks",snapshot.problem().isBlank()?"Unavailable":snapshot.problem()));
                    controls.add(control(Action.BLOCK_CREATE,"Bind looked-at block…",canEdit&&!lookedAtManagedBlock(player),!canEdit?editReason:"Supplied managed blocks register automatically.",false,"ID"));
                }
                else controls.add(control(Action.POINT_CREATE,"Save my position…",canEdit,editReason,false,"ID"));
                var selectedDeparture=locationDeparture(server,id);
                if(selectedDeparture!=null||c.departureDraft!=null){var def=c.departureDraft!=null?c.departureDraft:selectedDeparture;var departure=CoreDepartureMenu.detail(player,def.id(),c.departureDraft,c.departureCreating);departureForm=locationDepartureForm(server,id,departure.departure());facts.addAll(departure.facts());controls.add(control(Action.DEPARTURE_EDIT,"Configure departure",canEdit&&!Departures.state(server).usedByDefinition().containsKey(def.id()),editReason,false,""));if(id.startsWith("unbound:")){title=def.name();description="Departure settings waiting for a physical control block. Configure and bind the control to finish this Location.";break;}}
                if(block&&id.startsWith("managed:")){title=managedInstance(server,id).label();managedDetail(server,c,id,facts,controls,canEdit,editReason);break;}
                var site=sites(server).get(manualSiteId(server,id));if(site!=null){title=site.id();controls.add(control(Action.SITE_SUBTYPES_OPEN,"Choose subtypes",true,"",false,""));facts.add(new Fact("Location",describe(site)));facts.add(new Fact("Used by",references(server,site.id())));controls.add(control(Action.SHOW,block?"Show block site":"Show point",true,"",false,""));controls.add(control(block?Action.BLOCK_UPDATE:Action.POINT_UPDATE,block?"Replace block site":"Update to my position",canEdit,editReason,true,""));controls.add(control(Action.SITE_DELETE,"Delete location",canEdit,editReason,true,""));if(!block&&!pointRoles().isEmpty()){controls.add(control(Action.POINT_ASSIGN,"Use for…",canEdit,editReason,true,"POINT_PURPOSE"));if(pointPurposeCount(server,site.id())>0)controls.add(control(Action.POINT_UNASSIGN,"Clear use…",canEdit,editReason,true,"POINT_BOUND"));}}
                facts.add(new Fact("Behavior",block?"Game blocks register when placed. A manual reference only marks a location.":"Saving a point moves nobody. Choose player travel destinations under Players."));
            }
            case SETUP -> {
                title="Game setup";description="Global game settings. Configure regions under Regions, and saved points or blocks under Locations.";
                if(id.equals("limits")) {
                    title="Block limits";description="Choose how many supplied blocks may be placed. Existing blocks stay in the world.";
                    var snapshot=ManagedBlocks.snapshot(server);
                    if(!snapshot.ready())facts.add(new Fact("Block policy",snapshot.problem().isBlank()?"Unavailable":snapshot.problem()));
                    facts.add(new Fact("Registered block types",Integer.toString(snapshot.types().size())));
                    controls.add(control(Action.BLOCK_LIMITS,"Choose block type…",!snapshot.types().isEmpty(),"No supplied block types are registered.",false,"BLOCK_LIMIT_TYPE"));
                } else if(id.startsWith("limit:")) {
                    title=managedTypeLabel(server,id);description="Existing blocks stay in place. The limit controls new placements.";
                    managedLimitDetail(server,c,id,facts,controls,canEdit,editReason);
                } else if(id.startsWith("tool:")) {
                    var tool=GameSetupTools.get(toolId(id)).orElse(null);
                    if(tool!=null) {
                        title=tool.label();description=tool.description();
                        try {
                            var status=GameSetupTools.status(source,tool.id());facts.addAll(status.facts());
                            for(var verb:tool.verbs()) {
                                String denied=GameSetupTools.guardReason(source,verb.guard());
                                String unavailable=status.disabledReasons().get(verb.id());
                                String reason=denied!=null?denied:unavailable;
                                controls.add(control(Action.TOOL_ACTION,verb.label(),reason==null||reason.isBlank(),reason==null?"":reason,verb.confirm(),"TOOL:"+verb.id()));
                            }
                        } catch(RuntimeException exception) {
                            facts.add(new Fact("Service","Unavailable: "+shortText(exception.getMessage(),512)));
                        }
                    }
                } else facts.add(new Fact("Scope","Only installed global tools appear here. Hidden-faction voting, Journey chapters and future campaign tools are not required here."));
            }
        }
        if(SessionBuilding.enabled(server)&&facts.stream().noneMatch(f->f.label().equals("Building Mode")))
            facts.add(new Fact("Building Mode","On - anyone may change the map; survival remains frozen."));
        if(gm&&(c.page==Page.BOOKS_LORE||c.page==Page.LOOT_TABLES||c.page==Page.CHESTS)){
            var refs=new ArrayList<BookSummary>();var tableUses=new ArrayList<Entry>();var chestUses=new ArrayList<Entry>();BookChance direct=null;
            if(c.page==Page.LOOT_TABLES&&loot!=null)for(var ref:loot.books()){var b=BookRuntime.books(server).get(ref.bookId());if(b!=null)refs.add(BookDeliveryRuntime.summary(server,b));}
            if(c.page==Page.CHESTS&&!id.isBlank()){
                direct=chest(server,id).directBook();if(direct!=null){var b=BookRuntime.books(server).get(direct.bookId());if(b!=null)refs.add(BookDeliveryRuntime.summary(server,b));}
                controls.add(control(Action.CHEST_BOOK_SET,"Specific lore book...",canEdit,editReason,false,"BOOKS"));
                controls.add(control(Action.BOOK_KEEP,"Keep book placed until found...",active&&!held(server),"Requires active play without a hold.",false,"BOOKS"));
            }
            if(c.page==Page.BOOKS_LORE&&book!=null){
                String bookId=book.id();TypedChestLoot.types(server).values().stream().filter(t->t.books().stream().anyMatch(r->r.bookId().equals(bookId))).limit(12).forEach(t->tableUses.add(entry(t.id(),t.label(),t.description(),"LOOT",t.id(),0)));
                ChestAuthoring.service(server).state().bindings().values().stream().filter(b->b.typed()&&b.directBook()!=null&&b.directBook().bookId().equals(bookId)).limit(12).forEach(b->chestUses.add(entry(b.id(),"Chest at "+b.directTarget().x()+" "+b.directTarget().y()+" "+b.directTarget().z(),b.directTarget().dimension(),"CHEST",b.id(),b.revision())));
                controls.add(control(Action.BOOK_KEEP,"Keep placed until found...",active&&!held(server),"Requires active play without a hold.",false,"LOOT_TYPE"));
                controls.add(control(Action.BOOK_GIVE_OPEN,"Give book to player...",active&&!held(server),"Requires active play without a hold.",false,""));
            }
            KeepForm keep=null;if(!c.keepBook.isBlank())keep=BookDeliveryRuntime.keepForm(server,c.keepBook,c.keepTable);
            else if(book!=null){var existing=ChestAuthoring.service(server).state().bookDelivery().keepRequests().get(book.id());if(existing!=null)keep=BookDeliveryRuntime.keepForm(server,book.id(),existing.lootTableId());}
            var status=book==null?null:BookGifts.status(server,book.id());
            BookGiveForm give=null;
            if(book!=null&&c.giveBook.equals(book.id()))give=BookGifts.form(player.getCommandSource(),book,c.giveTarget);
            lore=new LoreForm(refs,tableUses,chestUses,direct,keep,status,give);
        }
        return new Detail(id,shortText(title,160),shortText(description,4096),facts,controls,loot,event,region,book,lore,roleForm,roleAssignment,areaForm,departureForm);
    }

    private static Picker picker(ServerPlayerEntity player,String kind,String query,int index) {
        var server=player.getEntityWorld().getServer();String needle=query.toLowerCase(Locale.ROOT);var choices=new ArrayList<Entry>();
        switch(kind) {
            case "BOOKS" -> BookRuntime.books(server).values().forEach(b->choices.add(entry(b.id(),b.title(),b.author(),"BOOK",b.id(),0)));
            case "ITEMS" -> Registries.ITEM.getIds().stream().sorted().forEach(id->{var item=Registries.ITEM.get(id);String label=item.getName().getString();if((id+" "+label).toLowerCase(Locale.ROOT).contains(needle))choices.add(entry(id.toString(),shortText(label,160),id.toString(),"ITEM",Integer.toString(item.getMaxCount()),0));});
            case "LOOT_TYPE" -> TypedChestLoot.types(server).values().forEach(t->choices.add(entry(t.id(),t.label(),t.description(),"LOOT",t.id(),0)));
            case "DESTINATION","POINT","BLOCK","REGION" -> sites(server).values().stream().filter(site->switch(kind){case "DESTINATION","POINT"->site instanceof AuthoringSite.Location;case "BLOCK"->site instanceof AuthoringSite.Anchor;default->site instanceof AuthoringSite.Region;}).forEach(s->choices.add(siteEntry(s)));
            case "REGION_PURPOSE" -> ChronicleCore.authoring().roles().stream().filter(role->role.kind()==AuthoringCatalog.Kind.REGION).forEach(role->{var binding=MapSiteAssignments.bindings(server).get(role.id());String status=binding==null?"Unassigned":binding.siteId().equals(playerContextSelection(player))?bindingState(server,role.id())+" here":bindingState(server,role.id())+" elsewhere: "+binding.siteId();choices.add(entry(role.id(),role.label(),role.description()+" · "+status,"PURPOSE",role.id(),0));});
            case "REGION_BOUND" -> ChronicleCore.authoring().roles().stream().filter(role->role.kind()==AuthoringCatalog.Kind.REGION).filter(role->{var binding=MapSiteAssignments.bindings(server).get(role.id());return binding!=null&&binding.siteId().equals(playerContextSelection(player));}).forEach(role->choices.add(entry(role.id(),role.label(),role.description()+" · "+bindingState(server,role.id()),"PURPOSE",role.id(),0)));
            case "BLOCK_LIMIT_TYPE" -> ManagedBlocks.snapshot(server).types().forEach(type->choices.add(entry(type.typeId(),type.label(),type.description()+" · "+type.count()+" / "+type.limit(),"MANAGED_TYPE",type.blockId(),0)));
            case "MANUAL_REFERENCE" -> {
                var instance=managedInstance(server,playerContextSelection(player));
                manualAliases(server,instance).forEach(anchor->choices.add(entry("manual:"+anchor.id(),anchor.id(),describe(anchor),"MANUAL",anchor.id(),anchor.revision())));
            }
            case "POINT_PURPOSE" -> pointRoles().forEach(role->{var binding=MapSiteAssignments.bindings(server).get(role.id());String status=binding==null?"Unassigned":binding.siteId().equals(playerContextSelection(player))?bindingState(server,role.id())+" here":bindingState(server,role.id())+" elsewhere: "+binding.siteId();choices.add(entry(role.id(),role.label(),role.description()+" · "+status,"PURPOSE",role.id(),0));});
            case "POINT_BOUND" -> pointRoles().stream().filter(role->{var binding=MapSiteAssignments.bindings(server).get(role.id());return binding!=null&&binding.siteId().equals(playerContextSelection(player));}).forEach(role->choices.add(entry(role.id(),role.label(),role.description()+" · "+bindingState(server,role.id()),"PURPOSE",role.id(),0)));
            case "FLAG" -> {choices.add(entry("none","No prerequisite","No required game condition","FLAG","",0));ChronicleRuntime.service(server).state().flags().forEach((id,value)->choices.add(entry(id,id,"Current value: "+value,"FLAG","",0)));}
            default -> throw new IllegalArgumentException("Unsupported selector: "+kind);
        }
        if(!kind.equals("ITEMS"))choices.removeIf(e->!(e.id()+" "+e.label()+" "+e.description()).toLowerCase(Locale.ROOT).contains(needle));
        int pages=Math.max(1,(choices.size()+11)/12),page=Math.min(index,pages-1);return new Picker(kind,query,page,pages,choices.subList(page*12,Math.min((page+1)*12,choices.size())));
    }

    private static Frame frame(ServerPlayerEntity player) {
        var server=player.getEntityWorld().getServer();var campaign=ChronicleRuntime.service(server).state();
        boolean gm=player.getCommandSource().hasPermissionLevel(2);boolean author=gm&&AuthoringRuntime.isAuthor(player);
        var play=PlayRuntime.available(server);Object controls=play==null?null:new PlayControls(play.participants(),play.bindings(),play.deliveries());
        return new Frame(gm,campaign,optional(()->AuthoringRuntime.get(server).service().state()),optional(()->ChestAuthoring.service(server).state().bindings()),optional(()->SpawnAuthoring.service(server).state()),optional(()->TypedChestLoot.types(server)),optional(()->BookRuntime.books(server)),optional(()->ChestAuthoring.service(server).state().bookDelivery()),AutomaticPlayerSpawns.available(server),PlayerRoles.revision(server),optional(()->WorldAreaOperations.revision(server)),optional(()->Departures.state(server)),controls,optional(()->MapSiteAssignments.revision(server)),optional(()->ManagedBlocks.revision(server)),toolRevisions(player),player.getEntityWorld().getRegistryKey().getValue().toString(),author,SessionBuilding.enabled(server),Objects.requireNonNullElse(RuntimeHolds.reason(server),""),server.getPlayerManager().getPlayerList().stream().map(ServerPlayerEntity::getUuid).toList(),PlayRuntime.eligibleIds(server),author?AuthoringRuntime.get(server).selection(player.getUuid()):null,author?AuthoringRuntime.get(server).regionDraft(player.getUuid()).orElse(null):null,targetKey(player));
    }
    private static Object optional(Supplier<?> operation){try{return operation.get();}catch(IllegalArgumentException|IllegalStateException e){return "unavailable:"+e.getMessage();}}
    private static Map<String,Object> toolRevisions(ServerPlayerEntity player) {
        var revisions=new TreeMap<String,Object>();var source=player.getCommandSource();
        for(var tool:GameSetupTools.tools()) {
            try {revisions.put(tool.id(),GameSetupTools.status(source,tool.id()).controlRevision());}
            catch(RuntimeException exception) {revisions.put(tool.id(),"unavailable:"+shortText(exception.getMessage(),512));}
        }
        return revisions;
    }
    private static String toolId(String selected) {
        if(!selected.startsWith("tool:")) throw new IllegalArgumentException("Choose a registered game setup tool.");
        return selected.substring("tool:".length());
    }
    private static String requirementId(Context context,String selected) {
        if(context.page==Page.SETUP) {
            if(selected.startsWith("binding:")||ChronicleCore.authoring().get(selected).filter(role->role.kind()==AuthoringCatalog.Kind.LOCATION).isEmpty())throw new IllegalArgumentException("Choose a registered point requirement. Region uses are managed under Regions.");
            return selected;
        }
        throw new IllegalArgumentException("Explicit legacy setup commands remain available; geometry uses are managed under Regions or Locations.");
    }
    private static String regionPurpose(Context context,String selected){
        if(context.page!=Page.REGIONS)throw new IllegalArgumentException("Region uses are managed under Regions.");
        if(selected==null||!AuthoringSite.ID.matcher(selected).matches())throw new IllegalArgumentException("Choose a registered region purpose.");
        return ChronicleCore.authoring().get(selected).filter(role->role.kind()==AuthoringCatalog.Kind.REGION).map(AuthoringCatalog.Role::id).orElseThrow(()->new IllegalArgumentException("Choose a registered region purpose."));
    }
    private static String pointPurpose(Context context,String selected){if(context.page!=Page.LOCATIONS||!"POINT".equals(context.filter))throw new IllegalArgumentException("Point uses are managed under Locations → Points.");if(selected==null||!AuthoringSite.ID.matcher(selected).matches()||pointRoles().stream().noneMatch(role->role.id().equals(selected)))throw new IllegalArgumentException("Choose a registered point purpose.");return selected;}
    private static void requireSelectedPoint(MinecraftServer server,String id){if(!(sites(server).get(id) instanceof AuthoringSite.Location))throw new IllegalArgumentException("Choose a saved point.");}
    private static void requireSelectedRegion(MinecraftServer server,String id){if(!(sites(server).get(id) instanceof AuthoringSite.Region))throw new IllegalArgumentException("Choose a saved region.");}
    private static Fact regionUsage(MinecraftServer server,String id){var usage=ChronicleCore.authoring().roles().stream().filter(role->role.kind()==AuthoringCatalog.Kind.REGION).filter(role->{var binding=MapSiteAssignments.bindings(server).get(role.id());return binding!=null&&binding.siteId().equals(id);}).map(role->role.label()+" · "+bindingState(server,role.id())).toList();return new Fact("Used by",usage.isEmpty()?"None":summary(usage,4096));}
    private static long regionUsageCount(MinecraftServer server,String id){return ChronicleCore.authoring().roles().stream().filter(role->role.kind()==AuthoringCatalog.Kind.REGION).filter(role->{var binding=MapSiteAssignments.bindings(server).get(role.id());return binding!=null&&binding.siteId().equals(id);}).count();}
    private static String bindingState(MinecraftServer server,String requirement){try{MapSiteAssignments.resolve(server,requirement);return "Current";}catch(RuntimeException error){return "Stale";}}
    private static String playerContextSelection(ServerPlayerEntity player){var context=WORLDS.getOrDefault(player.getEntityWorld().getServer(),Map.of()).get(player.getUuid());return context==null?"":context.selected;}
    private static List<AuthoringCatalog.Role> pointRoles(){return ChronicleCore.authoring().roles().stream().filter(role->role.kind()==AuthoringCatalog.Kind.LOCATION&&!role.id().equals("chronicle_core:arrival")&&!role.id().equals("chronicle_core:respawn")).toList();}
    private static long pointPurposeCount(MinecraftServer server,String id){return pointRoles().stream().filter(role->{var binding=MapSiteAssignments.bindings(server).get(role.id());return binding!=null&&binding.siteId().equals(id);}).count();}
    private static List<Entry> blockEntries(MinecraftServer server) {
        var snapshot=ManagedBlocks.snapshot(server);
        var anchors=sites(server).values().stream().filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast).toList();
        var managedPositions=new HashSet<String>();var result=new ArrayList<Entry>();
        for(var instance:snapshot.instances()) {
            String position=sitePosition(instance.dimension(),instance.position());managedPositions.add(position);
            var aliases=manualAliases(anchors,instance);String alias=aliases.size()==1?aliases.getFirst().id():"";
            String description=instance.dimension()+" · "+coordinates(instance.position())+" · "+instance.status()+" · "+instance.actualBlock()+(aliases.isEmpty()?"":aliases.size()==1?" · Manual reference: "+alias:" · Manual references: "+aliases.size());
            result.add(entry("managed:"+instance.id(),instance.label(),description,"MANAGED",alias,instance.revision()));
        }
        for(var anchor:anchors)if(!managedPositions.contains(sitePosition(anchor.dimension(),anchor.position())))result.add(entry("manual:"+anchor.id(),anchor.id(),describe(anchor)+" · Manual reference","MANUAL",anchor.id(),anchor.revision()));
        result.sort(Comparator.comparing(Entry::label).thenComparing(Entry::id));return result;
    }
    private static String sitePosition(String dimension,BlockPosition position){return dimension+":"+position.x()+":"+position.y()+":"+position.z();}
    private static ManagedBlockInstanceView managedInstance(MinecraftServer server,String id) {
        if(!id.startsWith("managed:"))throw new IllegalArgumentException("Choose a managed block site.");
        UUID uuid;try{uuid=UUID.fromString(id.substring("managed:".length()));}catch(IllegalArgumentException e){throw new IllegalArgumentException("Choose a managed block site.");}
        return ManagedBlocks.snapshot(server).instances().stream().filter(instance->instance.id().equals(uuid)).findFirst().orElseThrow(()->new IllegalArgumentException("That managed block site is no longer available."));
    }
    private static String managedTypeLabel(MinecraftServer server,String id) {
        if(!id.startsWith("limit:"))throw new IllegalArgumentException("Choose a managed block type.");
        String typeId=id.substring("limit:".length());
        return ManagedBlocks.snapshot(server).types().stream().filter(type->type.typeId().equals(typeId)).map(ManagedBlockTypeView::label).findFirst().orElseThrow(()->new IllegalArgumentException("That managed block type is no longer available."));
    }
    private static List<AuthoringSite.Anchor> manualAliases(MinecraftServer server,ManagedBlockInstanceView instance){return manualAliases(sites(server).values().stream().filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast).toList(),instance);}
    private static List<AuthoringSite.Anchor> manualAliases(List<AuthoringSite.Anchor> anchors,ManagedBlockInstanceView instance){String position=sitePosition(instance.dimension(),instance.position());return anchors.stream().filter(anchor->sitePosition(anchor.dimension(),anchor.position()).equals(position)).sorted(Comparator.comparing(AuthoringSite.Anchor::id)).toList();}
    private static String manualSiteId(MinecraftServer server,String id) {
        if(id.startsWith("manual:"))return id.substring("manual:".length());
        if(!id.startsWith("managed:"))return id;
        var aliases=manualAliases(server,managedInstance(server,id));
        if(aliases.isEmpty())return id;
        if(aliases.size()>1)throw new IllegalStateException("Choose a specific manual reference first.");
        return aliases.getFirst().id();
    }
    private static void managedDetail(MinecraftServer server,Context context,String id,List<Fact> facts,List<Control> controls,boolean canEdit,String editReason) {
        var instance=managedInstance(server,id);var type=ManagedBlocks.snapshot(server).types().stream().filter(candidate->candidate.typeId().equals(instance.typeId())).findFirst().orElse(null);
        facts.add(new Fact("Status",instance.status().name()));facts.add(new Fact("Block",instance.actualBlock()));facts.add(new Fact("Location",instance.dimension()+" · "+coordinates(instance.position())));
        if(type!=null)facts.add(new Fact("Capacity",type.count()+" / "+type.limit()+" - "+limitStatus(type)));
        var aliases=manualAliases(server,instance);
        controls.add(control(Action.MANAGED_SHOW,"Show block site",true,"",false,""));
        if(aliases.size()==1) {
            facts.add(new Fact("Manual reference",aliases.getFirst().id()));
            controls.add(control(Action.BLOCK_UPDATE,"Replace manual reference",canEdit,editReason,true,""));
            controls.add(control(Action.SITE_DELETE,"Delete manual reference",canEdit,editReason,true,""));
        } else if(aliases.size()>1) {
            facts.add(new Fact("Manual references",Integer.toString(aliases.size())));
            controls.add(control(Action.MANUAL_REFERENCES,"Manual references…",true,"",false,"MANUAL_REFERENCE"));
        }
    }
    private static void managedLimitDetail(MinecraftServer server,Context context,String id,List<Fact> facts,List<Control> controls,boolean canEdit,String editReason) {
        String typeId=id.substring("limit:".length());var snapshot=ManagedBlocks.snapshot(server);var type=snapshot.types().stream().filter(candidate->candidate.typeId().equals(typeId)).findFirst().orElseThrow(()->new IllegalArgumentException("That managed block type is no longer available."));
        context.managedPolicyRevision=snapshot.policyRevision();
        facts.add(new Fact("Registered block",type.blockId()));facts.add(new Fact("Current blocks",Integer.toString(type.count())));facts.add(new Fact("Limit",type.fixed()?"Fixed at "+type.limit():Integer.toString(type.limit())));facts.add(new Fact("Capacity",limitStatus(type)));
        if(!type.fixed())controls.add(control(Action.BLOCK_LIMIT_SET,"Set limit…",canEdit&&snapshot.ready(),snapshot.ready()?editReason:shortText(snapshot.problem(),512),true,"INTEGER"));
        controls.add(control(Action.BLOCK_LIMITS,"Choose block type…",true,"",false,"BLOCK_LIMIT_TYPE"));
        controls.add(control(Action.REFRESH,"Back to game setup",true,"",false,"GAME_SETUP"));
    }
    private static String limitStatus(ManagedBlockTypeView type) { return type.overLimit()?"Over limit":type.count()>=type.limit()?"Limit reached":"Available"; }
    private static boolean lookedAtManagedBlock(ServerPlayerEntity player) {
        var world=player.getEntityWorld();var start=player.getEyePos();var end=start.add(player.getRotationVec(1).multiply(6));
        for(int sample=0;sample<=24;sample++){BlockPos position=BlockPos.ofFloored(start.lerp(end,sample/24.0));if(!world.getChunkManager().isChunkLoaded(position.getX()>>4,position.getZ()>>4))return false;}
        HitResult hit=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)return false;
        return ManagedBlocks.isManagedBlock(Registries.BLOCK.getId(world.getBlockState(block.getBlockPos()).getBlock()).toString());
    }
    private static String nonRegionReferences(MinecraftServer server,String id){var refs=new ArrayList<String>();refs.addAll(newReferences(server,id));SpawnAuthoring.service(server).state().bindings().values().stream().filter(binding->binding.locationId().equals(id)).forEach(binding->refs.add("Player destination: "+binding.purpose()));var play=PlayRuntime.available(server);if(play!=null)play.bindings().values().stream().filter(binding->binding.regionId().equals(id)).forEach(binding->refs.add("Event: "+binding.id()));return refs.isEmpty()?"None":summary(refs,4096);}
    private static String summary(List<String> values,int max){StringBuilder result=new StringBuilder();for(int index=0;index<values.size();index++){String next=result.isEmpty()?values.get(index):"; "+values.get(index);if(result.length()+next.length()>max){String suffix="; and "+(values.size()-index)+" more";return shortText(result.toString(),Math.max(0,max-suffix.length()))+suffix;}result.append(next);}return result.toString();}
    private static String targetKey(ServerPlayerEntity player) {
        try{var pos=ChestAuthoring.target(player.getCommandSource());return pos.toShortString()+":"+player.getEntityWorld().getBlockState(pos);}
        catch(IllegalArgumentException|IllegalStateException exception){return "";}
    }
    private static Map<String,AuthoringSite> sites(MinecraftServer server){return AuthoringRuntime.get(server).service().state().sites();}
    private static List<String> newReferences(MinecraftServer server,String id){List<String> refs=new ArrayList<>();AreaChanges.catalog(server).areas().values().stream().filter(a->a.bounds().regionId().equals(id)).forEach(a->refs.add("Area change: "+a.name()+(AreaChanges.stale(server,a)?" (stale)":"")));Departures.state(server).definitions().values().stream().filter(d->d.boardingRegion().filter(r->r.id().equals(id)).isPresent()||d.exactBlockAnchor().filter(r->r.id().equals(id)).isPresent()).forEach(d->refs.add("Departure: "+d.name()));PlayerRoles.definitions(server).values().stream().filter(r->r.starting()!=null&&r.starting().locationId().equals(id)||r.respawn()!=null&&r.respawn().locationId().equals(id)).forEach(r->refs.add("Player role: "+r.name()));return refs;}
    private static String references(MinecraftServer server,String id) {
        var refs=new ArrayList<String>();refs.addAll(newReferences(server,id));MapSiteAssignments.bindings(server).values().stream().filter(b->b.siteId().equals(id)).forEach(b->refs.add("Setup: "+b.requirementId()));
        SpawnAuthoring.service(server).state().bindings().values().stream().filter(b->b.locationId().equals(id)).forEach(b->refs.add("Player destination: "+b.purpose()));
        var play=PlayRuntime.available(server);if(play!=null)play.bindings().values().stream().filter(b->b.regionId().equals(id)).forEach(b->refs.add("Event: "+b.id()));
        return shortText(refs.isEmpty()?"No current references":String.join("; ",refs),2048);
    }
    private static String destination(MinecraftServer server,String purpose){var binding=SpawnAuthoring.service(server).state().bindings().get(purpose);return binding==null?"Unavailable destination (configure again)":binding.locationId()+" · saved revision "+binding.locationRevision();}
    private static ChestBinding chest(MinecraftServer server,String id){var b=ChestAuthoring.service(server).state().bindings().get(id);if(b==null||!b.typed())throw new IllegalArgumentException("Choose a saved managed chest.");return b;}
    private static ObservationCatalog.Definition cue(String id){if(!id.startsWith("cue:"))throw new IllegalArgumentException("Choose a message/sound event.");return ChronicleCore.observations().get(raw(id)).orElseThrow(()->new IllegalArgumentException("The event definition is unavailable."));}
    private static CampaignEvent gameEvent(String id){if(!id.startsWith("action:"))throw new IllegalArgumentException("Choose a game-state action.");return ChronicleCore.events().get(raw(id)).orElseThrow(()->new IllegalArgumentException("The game action is unavailable."));}
    private static String raw(String id){int colon=id.indexOf(':');if(colon<0)throw new IllegalArgumentException("Choose a registered event or condition.");return id.substring(colon+1);}
    private static Entry siteEntry(AuthoringSite s){String kind=s instanceof AuthoringSite.Region?"REGION":s instanceof AuthoringSite.Location?"POINT":"BLOCK";return entry(s.id(),s.id(),describe(s),kind,s.id(),s.revision());}
    private static String describe(AuthoringSite site) {
        if(site instanceof AuthoringSite.Region r)return r.dimension()+" · "+coordinates(r.min())+" to "+coordinates(r.max());
        if(site instanceof AuthoringSite.Location p)return String.format(Locale.ROOT,"%s · %.2f %.2f %.2f · facing %.1f / %.1f",p.dimension(),p.x(),p.y(),p.z(),p.yaw(),p.pitch());
        var b=(AuthoringSite.Anchor)site;return b.dimension()+" · "+coordinates(b.position())+" · "+b.expectedBlock();
    }
    private static String coordinates(BlockPosition p){return p==null?"Not selected":p.x()+" "+p.y()+" "+p.z();}
    private static Entry entry(String id,String label,String description,String kind,String reference,long revision){return new Entry(id,shortText(label,160),shortText(description,2048),kind,reference,revision);}
    private static Control control(Action action,String label,boolean enabled,String reason,boolean confirm,String input){return new Control(action,label,enabled,enabled?"":reason,confirm,input);}
    private static void editor(ServerPlayerEntity player){if(RuntimeHolds.reason(player.getEntityWorld().getServer())!=null)throw new IllegalStateException("Finish pending operations first.");if(!player.getCommandSource().hasPermissionLevel(2)||!AuthoringRuntime.isAuthor(player)||!AuthoringRuntime.editable(player.getEntityWorld().getServer()))throw new IllegalStateException("Requires operator permission and non-active server editing mode.");}
    private static boolean held(MinecraftServer server){return RuntimeHolds.reason(server)!=null;}
    private static String quote(String value){return StringArgumentType.escapeIfRequired(value);}
    private static String shortText(String value,int max){return value==null?"":value.substring(0,Math.min(value.length(),max));}
    private static String command(ServerCommandSource source,String command) throws Exception {
        var feedback=new ArrayList<String>();
        var output=new CommandOutput(){@Override public void sendMessage(Text text){if(feedback.size()<8)feedback.add(shortText(text.getString(),512));}@Override public boolean shouldReceiveFeedback(){return true;}@Override public boolean shouldTrackOutput(){return true;}@Override public boolean shouldBroadcastConsoleToOps(){return false;}};
        int result=source.getServer().getCommandManager().getDispatcher().execute(command,source.withOutput(output));String message=String.join("\n",feedback);if(result<=0)throw new IllegalStateException(message.isBlank()?"The action was refused; check its requirements.":message);return message.isBlank()?"Action completed.":message;
    }
    private record PlayControls(Set<UUID> participants,Map<String,PlayState.Binding> bindings,Map<String,PlayState.Delivery> deliveries) { }
    private record Frame(boolean gm,Object campaign,Object sites,Object chests,Object spawns,Object loot,Object books,Object bookDelivery,Object automatic,Object roles,Object areas,Object departures,Object play,Object assignmentRevision,Object managedRevision,Object toolRevisions,String dimension,boolean editing,boolean building,String hold,List<UUID> online,List<UUID> eligible,Object selection,Object draft,String target) { }
    private static final class Context {
        long token,window,reviewTime,managedPolicyRevision;int requests,index;Page page=Page.SESSION;String filter="POINT",selected="",reviewId="",keepBook="",keepTable="";Frame frame,reviewFrame;
        PlayRuntime.Preview observation;LootType expectedLoot;BookDefinition expectedBook,giveDefinition;Picker picker;
        String giveBook="",giveTarget="";long giveAt;
        boolean visible,roleCreating,departureCreating;io.github.chronicle.core.departures.DepartureDefinition departureDraft;io.github.chronicle.core.roles.PlayerRole roleDraft,expectedRole;String pendingRole="",roleTarget="";Frame roleReview;long roleReviewAt;
    }
}
