package io.github.chronicle.core.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.CampaignEvent;
import io.github.chronicle.core.content.ChestBinding;
import io.github.chronicle.core.loot.*;
import io.github.chronicle.core.menu.CoreMenuModel.*;
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
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var map=WORLDS.get(server);if(map!=null)map.remove(handler.player.getUuid());});
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
        try{String result=act(player,new Request(action,page,c.selected,argument,Math.max(0,c.token),0,c.filter,""),c);c.token=0;send(player,c,result,true,false,false);source.sendFeedback(()->Text.literal(result),false);return 1;}
        catch(Exception e){source.sendError(Text.literal(Objects.requireNonNullElse(e.getMessage(),"Action failed; inspect the server log.")));return 0;}
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
            if(request.action()==Action.OPEN){context.token=0;context.visible=true;}
            context.page=gm?request.page():Page.PLAYERS;
            context.filter=gm&&"BLOCK".equals(request.filter())?"BLOCK":"POINT";
            context.index=request.index();
            context.selected=gm?request.id():player.getUuidAsString();
            if(request.action()==Action.OPEN&&context.page==Page.LOOT_TABLES)context.expectedLoot=null;
            context.picker=null;
            String message="";boolean close=false;
            if(request.action()==Action.OPTIONS){
                if(!gm)throw new IllegalStateException("Management selectors require operator permission.");
                context.picker=picker(player,request.argument(),request.query(),request.index());
                context.index=0;
            } else if(!read || request.action()==Action.EVENT_PREVIEW || request.action()==Action.EVENT_AUDIO || request.action()==Action.EVENT_CHECK) {
                message=act(player,request,context);
                close=request.action()==Action.REGION_CONTINUE;
                if(close)context.visible=false;
                if(!read)context.token=0;
            }
            send(player,context,message,true,request.action()==Action.OPEN,close);
        } catch(Exception exception) {
            if(exception instanceof IOException)ChronicleCore.LOGGER.error("Menu save failed; do not retry uncertain operations blindly",exception);
            send(player,context,shortText(exception.getMessage()==null?"Action failed; inspect the server log.":exception.getMessage(),2048),false,false,false);
        }
    }

    private static boolean publicAction(Action action){return switch(action){case OPEN,REFRESH,CARD_OPEN,PARTICIPANT_ADD,PARTICIPANT_REMOVE,LEAVE->true;default->false;};}
    private static void invalidate(Context c){c.token=0;c.observation=null;c.reviewId="";c.reviewFrame=null;}

    private static String act(ServerPlayerEntity player,Request request,Context c) throws Exception {
        var source=player.getCommandSource();var server=source.getServer();String id=c.selected,arg=request.argument();
        switch(request.action()) {
            case EDIT_ON -> {return command(source,"chronicle author mode on");}
            case EDIT_OFF -> {return command(source,"chronicle author mode off");}
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
            case STARTING_RESET -> {AutomaticPlayerSpawns.reset(source,UUID.fromString(id));return "Starting-position claim reset deliberately; the next eligible entry into play may place this player again.";}
            case PLAYER_MOVE_REVIEW -> {String result=MenuPlayerDestinations.review(source,UUID.fromString(id),arg);c.reviewId=id;c.reviewFrame=frame(player);c.reviewTime=System.nanoTime();return result;}
            case PLAYER_MOVE -> {requireReview(player,c,id);c.reviewId="";return MenuPlayerDestinations.move(source,UUID.fromString(id));}
            case EVENT_PREVIEW -> {
                if(id.startsWith("cue:")){var d=cue(id);return d.text()+"\nSound: "+(d.sound().isBlank()?"none":d.sound())+"\nPrivate content preview only. No event is delivered or completed.";}
                var event=gameEvent(id);return event.description()+"\nState action only. Required conditions: "+event.requiredFlags()+". This preview does not invoke its handler.";
            }
            case EVENT_AUDIO -> {
                var d=cue(id);if(d.sound().isBlank())throw new IllegalStateException("This event has no sound.");
                var sound=Registries.SOUND_EVENT.getOptionalValue(Identifier.of(d.sound())).orElseThrow(()->new IllegalStateException("Sound is unavailable: "+d.sound()));
                player.playSoundToPlayer(sound,SoundCategory.AMBIENT,1F,1F);return "Preview played only to you. No event completion, timer or world state changed.";
            }
            case EVENT_CHECK,EVENT_REVIEW -> {
                String result;
                if(id.startsWith("cue:")){var preview=PlayRuntime.preview(server,raw(id));result=preview.description();if(request.action()==Action.EVENT_REVIEW)c.observation=preview;}
                else {result=ChronicleRuntime.service(server).preview(raw(id));if(!result.equals("Ready."))throw new IllegalStateException(result);if(RuntimeHolds.reason(server)!=null)throw new IllegalStateException("Resolve the active operation hold first.");}
                if(request.action()==Action.EVENT_REVIEW){c.reviewId=id;c.reviewFrame=frame(player);c.reviewTime=System.nanoTime();}return result;
            }
            case EVENT_RUN -> {
                requireReview(player,c,id);c.reviewId="";
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
            case LOOT_RELOAD -> {String result=command(source,"chronicle author chest reload");c.expectedLoot=null;return result;}
            case REGION_NEW -> {AuthoringRuntime.get(server).beginRegionDraft(player,arg);c.selected=arg;return "Draft created. Continue in world activates the outline; use pos1 and pos2, then return to save.";}
            case REGION_EDIT -> {AuthoringRuntime.get(server).resumeRegionDraft(player,id);return "Both saved bounds loaded. Change either position independently; the region ID stays the same.";}
            case REGION_POSITION -> {
                editor(player);requireSelectedDraft(player,id);int colon=arg.indexOf(':');if(colon!=1)throw new IllegalArgumentException("Choose position 1 or 2 and three integer coordinates.");
                AuthoringRuntime.get(server).setRegionDraftCorner(player,Integer.parseInt(arg.substring(0,1)),position(arg.substring(2)));return "Draft position updated; the other position is retained. Save to apply the bounds.";
            }
            case REGION_CONTINUE -> {requireSelectedDraft(player,id);AuthoringRuntime.get(server).resumeRegionDraft(player);AuthoringRuntime.get(server).previewRegionDraft(player);player.sendMessage(Text.literal("Stand at the first corner: /chronicles author region pos1"),false);player.sendMessage(Text.literal("Stand at the other corner: /chronicles author region pos2. Reopen Regions to save."),false);return "Region editing is active and the private outline is on. Select positions, then reopen Regions.";}
            case REGION_CREATE,REGION_SAVE -> {requireSelectedDraft(player,id);var saved=AuthoringRuntime.get(server).saveRegionDraft(player);c.selected=saved.id();return "Region saved under the same ID. Existing revision-pinned consumers must be deliberately revalidated after bounds changes.";}
            case POINT_CREATE,POINT_UPDATE,BLOCK_CREATE,BLOCK_UPDATE -> {
                boolean point=request.action()==Action.POINT_CREATE||request.action()==Action.POINT_UPDATE;
                boolean update=request.action()==Action.POINT_UPDATE||request.action()==Action.BLOCK_UPDATE;
                String siteId=update?id:arg;
                String result=command(source,"chronicle author "+(point?"location ":"block ")+(update?"replace ":point?"save ":"bind ")+quote(siteId)+(point?" here":" target"));c.selected=siteId;return result;
            }
            case SITE_DELETE -> {return command(source,"chronicle author remove "+quote(id));}
            case SETUP_ASSIGN -> {MapSiteAssignments.assign(source,id,arg);return "Requirement assigned to the existing saved site. No coordinates copied, growth enabled or player moved.";}
            case SETUP_CLEAR -> {MapSiteAssignments.clear(source,id);return "Assignment cleared; the saved site itself is retained.";}
            case SHOW -> {
                if(c.page==Page.CHESTS){var t=chest(server,id).directTarget();AuthoringRuntime.get(server).preview(player,new AuthoringSite.Anchor("preview",t.dimension(),1,new BlockPosition(t.x(),t.y(),t.z()),t.expectedBlock()));return "Private chest outline shown.";}
                if(c.page==Page.SETUP){var site=MapSiteAssignments.resolve(server,id).orElseThrow(()->new IllegalStateException("Choose a saved site first."));AuthoringRuntime.get(server).preview(player,site);return "Private assigned-site outline shown.";}
                return command(source,"chronicle author show "+quote(id));
            }
            default -> throw new IllegalArgumentException("Unsupported menu action.");
        }
    }

    private static void requireReview(ServerPlayerEntity player,Context c,String id){if(!c.reviewId.equals(id)||!Objects.equals(c.reviewFrame,frame(player))||System.nanoTime()-c.reviewTime>30_000_000_000L)throw new IllegalStateException("Review this exact action and its current targets first; reviews expire after 30 seconds.");}
    private static void requireSelectedDraft(ServerPlayerEntity player,String id){var draft=AuthoringRuntime.get(player.getEntityWorld().getServer()).regionDraft(player.getUuid()).orElseThrow(()->new IllegalStateException("Open a region draft first."));if(!draft.id().equals(id))throw new IllegalStateException("Select the current region draft before changing or saving it.");}
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
        if(c.page==Page.LOCATIONS&&!c.selected.isBlank()&&all.stream().noneMatch(e->e.id().equals(c.selected)))c.selected="";
        if(c.selected.isBlank()&&!all.isEmpty())c.selected=all.get(c.index*12).id();
        Detail detail;
        try{detail=detail(player,c);}catch(RuntimeException exception){detail=new Detail(c.selected,"Service unavailable",shortText(exception.getMessage(),2048),List.of(),List.of(),null,null,null);}
        var campaign=ChronicleRuntime.service(server).state();
        String clock=gm?"Active play: "+PlayRuntime.elapsedTicks(server)/20+"s":"";
        var view=new View(c.token,c.page,c.index,pages,c.filter,c.selected,campaign.phase().name(),campaign.sessionNumber(),gm&&AuthoringRuntime.isAuthor(player),gm&&AuthoringRuntime.editable(server),gm,gm?Objects.requireNonNullElse(RuntimeHolds.reason(server),""):"",clock,shortText(message,4096),success,all.subList(c.index*12,Math.min((c.index+1)*12,all.size())),detail,c.picker,close);
        ServerPlayNetworking.send(player,new Response(view,open));
    }

    private static List<Entry> entries(ServerPlayerEntity player,Page page,String filter) {
        var server=player.getEntityWorld().getServer();
        if(!player.getCommandSource().hasPermissionLevel(2))return List.of(entry(player.getUuidAsString(),player.getName().getString(),"Your participation and public character card","PLAYER","",0));
        return switch(page) {
            case SESSION -> List.of(entry("play","Play controls","Run or freeze the continuing world","SESSION","",0),entry("activity","Activity","Session, event, player and loot records","HISTORY","",0));
            case PLAYERS -> {
                var players=new TreeMap<String,String>();var play=PlayRuntime.available(server);if(play!=null)play.participants().forEach(id->players.put(id.toString(),id.toString()));
                var automatic=AutomaticPlayerSpawns.available(server);if(automatic!=null){automatic.policies().keySet().forEach(id->players.put(id.toString(),id.toString()));automatic.arrivals().keySet().forEach(id->players.put(id.toString(),id.toString()));}
                server.getPlayerManager().getPlayerList().forEach(p->players.put(p.getUuidAsString(),p.getName().getString()));
                yield players.entrySet().stream().map(e->entry(e.getKey(),e.getValue(),server.getPlayerManager().getPlayer(UUID.fromString(e.getKey()))==null?"Offline saved participant":"Online","PLAYER","",0)).toList();
            }
            case EVENTS -> {
                var result=new ArrayList<Entry>();ChronicleCore.observations().definitions().forEach(d->result.add(entry("cue:"+d.id(),d.label(),d.text(),"CUE",d.id(),0)));
                ChronicleCore.events().events().forEach(e->result.add(entry("action:"+e.id(),e.id(),e.description(),"ACTION",e.id(),0)));
                ChronicleRuntime.service(server).state().flags().forEach((id,value)->result.add(entry("flag:"+id,id,"Game condition: "+value,"FLAG",id,0)));yield result;
            }
            case CHESTS -> ChestAuthoring.service(server).state().bindings().values().stream().filter(ChestBinding::typed).map(b->{var t=b.directTarget();var table=TypedChestLoot.types(server).get(b.lootType());return entry(b.id(),(table==null?b.lootType():table.label())+" @ "+t.x()+" "+t.y()+" "+t.z(),"Bound container · "+t.dimension(),"CHEST",b.lootType(),b.revision());}).toList();
            case LOOT_TABLES -> TypedChestLoot.types(server).values().stream().map(t->entry(t.id(),t.label(),t.description(),"LOOT",t.id(),0)).toList();
            case REGIONS -> sites(server).values().stream().filter(AuthoringSite.Region.class::isInstance).map(CoreMenuServer::siteEntry).toList();
            case LOCATIONS -> sites(server).values().stream().filter(s->"BLOCK".equals(filter)?s instanceof AuthoringSite.Anchor:s instanceof AuthoringSite.Location).map(CoreMenuServer::siteEntry).toList();
            case SETUP -> ChronicleCore.authoring().roles().stream().filter(r->!r.id().startsWith("chronicle_core:")).map(r->entry(r.id(),r.label(),r.description(),r.kind().name(),r.id(),0)).toList();
        };
    }

    private static Detail detail(ServerPlayerEntity player,Context c) {
        var server=player.getEntityWorld().getServer();var source=player.getCommandSource();String id=c.selected;
        boolean gm=source.hasPermissionLevel(2),editing=gm&&AuthoringRuntime.isAuthor(player),active=ChronicleRuntime.service(server).state().phase().name().equals("ACTIVE");
        boolean canEdit=editing&&AuthoringRuntime.editable(server);String editReason=active?"Pause play before changing setup.":"Enable editing to change saved setup.";
        var facts=new ArrayList<Fact>();var controls=new ArrayList<Control>();String title="Choose an item",description="Select an item in the middle to inspect its details.";
        LootType loot=null;EventForm event=null;RegionForm region=null;
        switch(c.page) {
            case SESSION -> {
                title=id.equals("activity")?"Activity":"Play controls";
                description="A session is a play window. This world and story continue across meetings; ending play does not reset game progress.";
                var campaign=ChronicleRuntime.service(server).state();
                if(id.equals("activity")) {
                    campaign.history().stream().skip(Math.max(0,campaign.history().size()-12)).forEach(a->facts.add(new Fact(a.timestamp()+" · "+a.action(),a.actor().name()+" · "+a.target()+" · "+a.parameters())));
                    var play=PlayRuntime.available(server);if(play!=null)play.deliveries().values().stream().limit(8).forEach(d->facts.add(new Fact("Event · "+d.id(),d.status()+" · "+d.recipients().size()+" recipients · session "+d.session())));
                    var autos=AutomaticPlayerSpawns.available(server);if(autos!=null)autos.arrivals().entrySet().stream().limit(8).forEach(a->facts.add(new Fact("Starting position · "+a.getKey(),a.getValue().status()+" · "+a.getValue().detail())));
                    facts.add(new Fact("Loot","Scheduled fills and interrupted-operation evidence remain in the chest journal. Inspection never refills a chest."));
                } else {
                    facts.add(new Fact("World",active&&RuntimeHolds.reason(server)==null?"Simulation running":"Frozen; players stay mobile and may edit their cards"));
                    facts.add(new Fact("Active play","The footer shows occupied active time. Paused, empty and held time does not count."));
                    facts.add(new Fact("Session boundaries","Conditions, event completion and starting-position history continue. Configured new-session loot fills are the explicit exception."));
                    switch(campaign.phase()) {
                        case INACTIVE,ENDED -> controls.add(control(Action.SESSION_START,"Start play",true,"",true,""));
                        case PAUSED -> controls.add(control(Action.SESSION_RESUME,"Resume play",true,"",false,""));
                        case ACTIVE -> controls.add(control(Action.SESSION_PAUSE,"Pause play",true,"",false,""));
                    }
                    if(active||campaign.phase().name().equals("PAUSED"))controls.add(control(Action.SESSION_END,"Finish for now",true,"",true,""));
                }
            }
            case PLAYERS -> {
                if(id.isBlank())break;UUID uuid=UUID.fromString(id);var target=server.getPlayerManager().getPlayer(uuid);
                title=target==null?id:target.getName().getString();description="Participation, public character card and this player's destinations. Starting position places them once when they first enter play, not at every meeting.";
                var play=PlayRuntime.available(server);boolean member=play!=null&&play.participants().contains(uuid);
                facts.add(new Fact("Connection",target==null?"Offline":"Online"));facts.add(new Fact("Participation",member?"Participating":"Not participating"));
                facts.add(new Fact("Eligible now",PlayRuntime.eligibleIds(server).contains(uuid)?"Yes":"No: must be participating, online, alive, not spectating or editing"));
                controls.add(control(member?Action.PARTICIPANT_REMOVE:Action.PARTICIPANT_ADD,member?"Remove participation":"Add participation",!gm||canEdit,editReason,member,""));
                controls.add(control(Action.CARD_OPEN,uuid.equals(player.getUuid())?"Edit my character card":"Read character card",target!=null,"Select an online account for the card screen.",false,""));
                if(uuid.equals(player.getUuid()))controls.add(control(Action.LEAVE,"Leave Chronicle",true,"",true,""));
                if(gm) {
                    var state=AutomaticPlayerSpawns.available(server);var policy=state==null?null:state.policies().get(uuid);var attempt=state==null?null:state.arrivals().get(uuid);
                    facts.add(new Fact("Starting position",policy==null||policy.arrival()==null?"Off":destination(server,policy.arrival().purpose())));
                    facts.add(new Fact("After death",policy==null||policy.respawn()==null?"Vanilla bed / anchor / world spawn":destination(server,policy.respawn().purpose())));
                    facts.add(new Fact("Starting-position history",attempt==null?"Never attempted":attempt.status()+" · "+attempt.detail()));
                    facts.add(new Fact("Saving settings","Moves nobody now. Independent per-player opt-ins preserve beds/anchors and previous once-only claims."));
                    controls.add(control(Action.PLAYER_MOVE_REVIEW,"Move player…",active&&target!=null&&!held(server),"Requires active play, online target and no unresolved operation.",false,"DESTINATION"));
                    if(c.reviewId.equals(id))controls.add(control(Action.PLAYER_MOVE,"Confirm player move",active&&target!=null&&!held(server),"Review a current safe destination first.",true,""));
                    controls.add(control(Action.STARTING_SET,"Set starting position…",canEdit&&state!=null,editReason,false,"DESTINATION"));
                    controls.add(control(Action.RESPAWN_SET,"Set after-death destination…",canEdit&&state!=null,editReason,false,"DESTINATION"));
                    if(policy!=null&&policy.arrival()!=null)controls.add(control(Action.STARTING_OFF,"Disable starting position",canEdit,editReason,true,""));
                    if(policy!=null&&policy.respawn()!=null)controls.add(control(Action.RESPAWN_OFF,"Use vanilla respawn",canEdit,editReason,true,""));
                    if(attempt!=null)controls.add(control(Action.STARTING_RESET,"Allow another placement attempt",canEdit,editReason,true,""));
                }
            }
            case EVENTS -> {
                if(id.isBlank())break;
                if(id.startsWith("flag:")) {
                    title=raw(id);description="A true/false game condition. Changing a live value can satisfy event prerequisites.";
                    facts.add(new Fact("Value",Boolean.toString(ChronicleRuntime.service(server).state().flag(raw(id)))));
                    controls.add(control(Action.CONDITION_SET,"Set condition…",active,"Live condition changes require active play.",true,"TEXT"));break;
                }
                if(id.startsWith("cue:")) {
                    var d=cue(id);title=d.label();description="Message and optional sound · "+raw(id).split(":")[0];
                    var state=PlayRuntime.available(server);var binding=state==null?null:state.bindings().get(raw(id));var delivery=state==null?null:state.deliveries().get(raw(id));
                    facts.add(new Fact("Content",d.text()));facts.add(new Fact("Sound",d.sound().isBlank()?"None":d.sound()));
                    facts.add(new Fact("Audience",d.audience()==ObservationCatalog.Audience.REGION?"Eligible participants in the selected region":"All eligible participants"));
                    facts.add(new Fact("Completion",delivery==null?"Not delivered":delivery.status()+" · "+delivery.recipients().size()+" recipients"));
                    facts.add(new Fact("Configuration",binding==null?"Unconfigured; preview works before setup":binding.mode()+" · region="+binding.regionId()+" · condition="+binding.requiredFlag()));
                    facts.add(new Fact("Entry/audience region","This cue service currently uses one region for both entry detection and a region audience."));
                    event=new EventForm(binding==null?"MANUAL":binding.mode().name(),binding==null?"":binding.regionId(),binding==null?"":binding.requiredFlag(),binding==null?60:binding.delayTicks()/20,binding!=null,true,!d.sound().isBlank());
                    controls.add(control(Action.EVENT_PREVIEW,"Preview event",true,"",false,""));
                    if(!d.sound().isBlank())controls.add(control(Action.EVENT_AUDIO,"Play preview — only me",true,"",false,""));
                    controls.add(control(Action.EVENT_CHECK,"Check live audience",true,"",false,""));
                    controls.add(control(Action.EVENT_REVIEW,"Run event…",active&&!held(server)&&binding!=null&&delivery==null,"Requires ACTIVE, a valid saved audience/configuration, no prior claim and no operation hold.",false,""));
                    if(c.reviewId.equals(id))controls.add(control(Action.EVENT_RUN,"Run this event",true,"",true,""));
                    controls.add(control(Action.EVENT_SAVE,"Save event configuration",canEdit,editReason,false,"EVENT_DRAFT"));
                    if(binding!=null){controls.add(control(Action.EVENT_DISABLE_AUTO,"Disable automatic trigger",canEdit,editReason,true,""));controls.add(control(Action.EVENT_REMOVE,"Remove event configuration",canEdit,editReason,true,""));}
                    if(delivery!=null)controls.add(control(Action.EVENT_RESET,"Allow event again",canEdit,editReason,true,""));
                } else {
                    var d=gameEvent(id);title=d.id();description="Game-state action · "+d.description();
                    facts.add(new Fact("Required conditions",d.requiredFlags().toString()));facts.add(new Fact("Completion",ChronicleRuntime.service(server).state().completedEvents().contains(raw(id))?"Completed":"Not completed"));
                    facts.add(new Fact("Readiness",ChronicleRuntime.service(server).preview(raw(id))));
                    controls.add(control(Action.EVENT_PREVIEW,"Preview event",true,"",false,""));controls.add(control(Action.EVENT_CHECK,"Check readiness",true,"",false,""));
                    controls.add(control(Action.EVENT_REVIEW,"Run event…",active&&!held(server)&&ChronicleRuntime.service(server).preview(raw(id)).equals("Ready."),"Requires active play and satisfied conditions.",false,""));
                    if(c.reviewId.equals(id))controls.add(control(Action.EVENT_RUN,"Run this event",true,"",true,""));
                }
            }
            case CHESTS -> {
                title="Bound chests";description="Physical loot containers and their table bindings. Item probabilities and refill settings live separately in Loot Tables.";
                boolean supported=false;
                try{var target=ChestAuthoring.target(source);ChestAuthoring.supported(source.getWorld(),target);supported=true;facts.add(new Fact("Looked-at container",source.getWorld().getRegistryKey().getValue()+" · "+target.toShortString()+" · "+Registries.BLOCK.getId(source.getWorld().getBlockState(target).getBlock())));}
                catch(IllegalArgumentException|IllegalStateException exception){facts.add(new Fact("Looked-at container","No supported container in reach"));}
                controls.add(control(Action.CHEST_BIND,"Bind looked-at chest…",canEdit&&supported,canEdit?"Look at a supported chest, barrel or shulker box within reach.":editReason,true,"LOOT_TYPE"));
                if(!id.isBlank()) {
                    var b=chest(server,id);var t=b.directTarget();title="Chest at "+t.x()+" "+t.y()+" "+t.z();
                    facts.add(new Fact("Loot table",b.lootType()));facts.add(new Fact("Container",t.dimension()+" · "+t.expectedBlock()));facts.add(new Fact("Fill behavior","Scheduled fills REPLACE ALL contents; binding/inspection does not fill now."));
                    controls.add(control(Action.SHOW,"Show chest",true,"",false,""));controls.add(control(Action.TABLE_OPEN,"Open loot table",true,"",false,""));controls.add(control(Action.CHEST_RETYPE,"Change loot table…",canEdit,editReason,true,"LOOT_TYPE"));controls.add(control(Action.CHEST_UNBIND,"Unbind chest",canEdit,editReason,true,""));
                }
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
                title="Regions";description="Select positions in the world, then review and save. Continue in world activates this draft and private bounding box automatically.";
                var draft=canEdit?AuthoringRuntime.get(server).regionDraft(player.getUuid()).orElse(null):null;
                controls.add(control(Action.REGION_NEW,"Create region…",canEdit,editReason,draft!=null,"ID"));
                if(!id.isBlank()&&sites(server).get(id) instanceof AuthoringSite.Region saved) {
                    title=saved.id();facts.add(new Fact("Saved bounds",saved.min()+" to "+saved.max()));facts.add(new Fact("Used by",references(server,id)));
                    controls.add(control(Action.SHOW,"Show region",true,"",false,""));controls.add(control(Action.REGION_EDIT,"Edit region",canEdit,editReason,draft!=null&&!draft.id().equals(id),""));controls.add(control(Action.SITE_DELETE,"Delete region",canEdit,editReason,true,""));
                }
                if(draft!=null&&draft.id().equals(id)) {
                    title="Region draft · "+draft.id();region=new RegionForm(draft.id(),draft.dimension(),draft.baseRevision(),coordinates(draft.first()),coordinates(draft.second()));
                    facts.add(new Fact("Step 1","Continue in world; stand at a corner and run /chronicles author region pos1 or pos2. Each changes only its own position."));
                    facts.add(new Fact("Position 1",coordinates(draft.first())));facts.add(new Fact("Position 2",coordinates(draft.second())));facts.add(new Fact("Step 2","Return here to save; existing pinned consumers become stale after changed bounds."));
                    controls.add(control(Action.REGION_CONTINUE,"Continue in world",canEdit,editReason,false,""));
                    controls.add(control(Action.REGION_POSITION,"Change position 1…",canEdit,editReason,false,"POSITION1"));controls.add(control(Action.REGION_POSITION,"Change position 2…",canEdit,editReason,false,"POSITION2"));
                    controls.add(control(draft.existing()?Action.REGION_SAVE:Action.REGION_CREATE,draft.existing()?"Save region changes":"Create selected region",canEdit&&draft.complete(),"Select both positions in the same dimension first.",draft.existing(),""));
                }
            }
            case LOCATIONS -> {
                boolean block="BLOCK".equals(c.filter);title=block?"Block sites":"Points";description=block?"A reference to one exact block instance and its expected type. Used for stations, landmarks or interactions.":"A saved position and facing for player destinations. No physical block is required.";
                controls.add(control(block?Action.BLOCK_CREATE:Action.POINT_CREATE,block?"Bind looked-at block…":"Save my position…",canEdit,editReason,false,"ID"));
                var site=sites(server).get(id);if(site!=null){title=site.id();facts.add(new Fact("Location",describe(site)));facts.add(new Fact("Used by",references(server,id)));controls.add(control(Action.SHOW,block?"Show block site":"Show point",true,"",false,""));controls.add(control(block?Action.BLOCK_UPDATE:Action.POINT_UPDATE,block?"Replace block site":"Update to my position",canEdit,editReason,true,""));controls.add(control(Action.SITE_DELETE,"Delete location",canEdit,editReason,true,""));}
                facts.add(new Fact("Behavior",block?"Mod blocks own their gameplay; this site is only a reference. Actual campaign stations arrive in their own milestone.":"Saving moves nobody. Coordinates, dimension and facing are retained; destinations are opt-in under Players."));
            }
            case SETUP -> {
                title="Game setup";description="Assign this game's key requirements to existing saved geometry. This registers references; it enables no ecology, event or player move.";
                var role=ChronicleCore.authoring().get(id).orElse(null);
                if(role!=null){title=role.label();facts.add(new Fact("Purpose",role.description()));facts.add(new Fact("Geometry",role.kind().name()));var binding=MapSiteAssignments.bindings(server).get(id);facts.add(new Fact("Assigned site",binding==null?"Unassigned":binding.siteId()+" · revision "+binding.siteRevision()));if(binding!=null){try{MapSiteAssignments.resolve(server,id);facts.add(new Fact("Reference","Current"));}catch(RuntimeException e){facts.add(new Fact("Reference",shortText(e.getMessage(),512)));}controls.add(control(Action.SHOW,"Show assigned site",true,"",false,""));controls.add(control(Action.SETUP_CLEAR,"Clear assignment",canEdit,editReason,true,""));}
                    String kind=switch(role.kind()){case REGION->"REGION";case LOCATION->"POINT";case BLOCK->"BLOCK";};controls.add(control(Action.SETUP_ASSIGN,"Choose existing site…",canEdit,editReason,true,kind));}
                facts.add(new Fact("Scope","Only installed game requirements appear. Hidden-faction voting, Journey chapters and future campaign tools are not required here."));
            }
        }
        return new Detail(id,shortText(title,160),shortText(description,4096),facts,controls,loot,event,region);
    }

    private static Picker picker(ServerPlayerEntity player,String kind,String query,int index) {
        var server=player.getEntityWorld().getServer();String needle=query.toLowerCase(Locale.ROOT);var choices=new ArrayList<Entry>();
        switch(kind) {
            case "ITEMS" -> Registries.ITEM.getIds().stream().sorted().forEach(id->{var item=Registries.ITEM.get(id);String label=item.getName().getString();if((id+" "+label).toLowerCase(Locale.ROOT).contains(needle))choices.add(entry(id.toString(),shortText(label,160),id.toString(),"ITEM",Integer.toString(item.getMaxCount()),0));});
            case "LOOT_TYPE" -> TypedChestLoot.types(server).values().forEach(t->choices.add(entry(t.id(),t.label(),t.description(),"LOOT",t.id(),0)));
            case "DESTINATION","POINT","BLOCK","REGION" -> sites(server).values().stream().filter(site->switch(kind){case "DESTINATION","POINT"->site instanceof AuthoringSite.Location;case "BLOCK"->site instanceof AuthoringSite.Anchor;default->site instanceof AuthoringSite.Region;}).forEach(s->choices.add(siteEntry(s)));
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
        return new Frame(gm,campaign,optional(()->AuthoringRuntime.get(server).service().state()),optional(()->ChestAuthoring.service(server).state().bindings()),optional(()->SpawnAuthoring.service(server).state()),optional(()->TypedChestLoot.types(server)),AutomaticPlayerSpawns.available(server),controls,optional(()->MapSiteAssignments.revision(server)),player.getEntityWorld().getRegistryKey().getValue().toString(),author,Objects.requireNonNullElse(RuntimeHolds.reason(server),""),server.getPlayerManager().getPlayerList().stream().map(ServerPlayerEntity::getUuid).toList(),PlayRuntime.eligibleIds(server),author?AuthoringRuntime.get(server).selection(player.getUuid()):null,author?AuthoringRuntime.get(server).regionDraft(player.getUuid()).orElse(null):null,targetKey(player));
    }
    private static Object optional(Supplier<?> operation){try{return operation.get();}catch(IllegalArgumentException|IllegalStateException e){return "unavailable:"+e.getMessage();}}
    private static String targetKey(ServerPlayerEntity player) {
        try{var pos=ChestAuthoring.target(player.getCommandSource());return pos.toShortString()+":"+player.getEntityWorld().getBlockState(pos);}
        catch(IllegalArgumentException|IllegalStateException exception){return "";}
    }
    private static Map<String,AuthoringSite> sites(MinecraftServer server){return AuthoringRuntime.get(server).service().state().sites();}
    private static String references(MinecraftServer server,String id) {
        var refs=new ArrayList<String>();MapSiteAssignments.bindings(server).values().stream().filter(b->b.siteId().equals(id)).forEach(b->refs.add("Setup: "+b.requirementId()));
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
    private static void editor(ServerPlayerEntity player){if(!player.getCommandSource().hasPermissionLevel(2)||!AuthoringRuntime.isAuthor(player)||!AuthoringRuntime.editable(player.getEntityWorld().getServer()))throw new IllegalStateException("Requires operator permission and non-active server editing mode.");}
    private static boolean held(MinecraftServer server){return RuntimeHolds.reason(server)!=null;}
    private static String quote(String value){return StringArgumentType.escapeIfRequired(value);}
    private static String shortText(String value,int max){return value==null?"":value.substring(0,Math.min(value.length(),max));}
    private static String command(ServerCommandSource source,String command) throws Exception {
        var feedback=new ArrayList<String>();
        var output=new CommandOutput(){@Override public void sendMessage(Text text){if(feedback.size()<8)feedback.add(shortText(text.getString(),512));}@Override public boolean shouldReceiveFeedback(){return true;}@Override public boolean shouldTrackOutput(){return true;}@Override public boolean shouldBroadcastConsoleToOps(){return false;}};
        int result=source.getServer().getCommandManager().getDispatcher().execute(command,source.withOutput(output));String message=String.join("\n",feedback);if(result<=0)throw new IllegalStateException(message.isBlank()?"The action was refused; check its requirements.":message);return message.isBlank()?"Action completed.":message;
    }
    private record PlayControls(Set<UUID> participants,Map<String,PlayState.Binding> bindings,Map<String,PlayState.Delivery> deliveries) { }
    private record Frame(boolean gm,Object campaign,Object sites,Object chests,Object spawns,Object loot,Object automatic,Object play,Object assignmentRevision,String dimension,boolean editing,String hold,List<UUID> online,List<UUID> eligible,Object selection,Object draft,String target) { }
    private static final class Context {
        long token,window,reviewTime;int requests,index;Page page=Page.SESSION;String filter="POINT",selected="",reviewId="";Frame frame,reviewFrame;
        PlayRuntime.Preview observation;LootType expectedLoot;Picker picker;
        boolean visible;
    }
}
