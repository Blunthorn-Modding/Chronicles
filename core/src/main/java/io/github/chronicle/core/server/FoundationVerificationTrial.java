package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.play.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.*;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

/** Explicitly opt-in disposable proof of automatic archival and bundled private media. */
public final class FoundationVerificationTrial {
    public static final String EVENT="test:foundation",MARK="Foundation private audio proof",FIRST="Foundation first actor cue",REPEAT="Foundation repeat cue";
    private static final String ACTOR_EVENT="test:foundation_actor",REPEAT_EVENT="test:foundation_repeat",UNCERTAIN="test:foundation_uncertain",PRESSURE="test:foundation_pressure";
    private FoundationVerificationTrial(){}
    public static void register(){if(!"true".equals(System.getenv("CHRONICLE_FOUNDATION_TRIAL")))return;
        Encounters.registerDefaults(List.of(new io.github.chronicle.core.encounters.EncounterGroup("test:foundation_pig","Trial pig","Disposable fresh-spawn source proof",List.of(new io.github.chronicle.core.encounters.EncounterMember("minecraft:pig",1)))));
        EventSources.registerProvider("test:foundation_activity","Foundation activity","Disposable recurrence proof",server->List.of(new EventSources.Choice("*","Any trial activity","")),List.of());
        for(String type:List.of("test:foundation_uncertain_source","test:foundation_pressure_source"))EventSources.registerProvider(type,"Foundation edge case","Disposable recurrence proof",server->List.of(new EventSources.Choice("*","Any trial activity","")),List.of());
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
        var root=literal("chronicle_foundation").requires(source->source.hasPermissionLevel(2));for(String verb:List.of("prepare","ready","repeat","repeated","uncertain","uncertain_ready","acknowledge","uncertain_clear","source_recovery_holds","pressure","pressure_release","pressure_clear","pause","online_guard","release","verify_auto"))root.then(literal(verb).executes(context->run(context.getSource(),verb)));dispatcher.register(root);
    });}
    private static int run(ServerCommandSource source,String verb){try{
        fixture(source);var server=source.getServer();var campaign=ChronicleRuntime.service(server);var play=PlayRuntime.service(server);
        switch(verb){
            case "prepare"->{
                require(RuntimeHolds.reason(server)==null,"unexpected startup hold: "+RuntimeHolds.reason(server));
                require(PlayerRoles.definitions(server).size()==6&&BookRuntime.books(server).size()==9,"fresh final defaults missing");
                for(var role:PlayerRoles.definitions(server).values())for(var item:role.items()){
                    var stack=ChestAuthoring.decode(server,new io.github.chronicle.core.content.InventorySnapshot(1,List.of(item.stackJson()))).getFirst();
                    require(!stack.isEmpty()&&stack.getCount()<=stack.getMaxCount(),"invalid default starting stack");
                }
                var a=source.getPlayerOrThrow();var b=server.getPlayerManager().getPlayer("ChronicleFabricB");require(b!=null,"observer not connected");PlayRuntime.setSelfParticipant(a,true);PlayRuntime.setSelfParticipant(b,true);
                campaign.defineFlag(new Actor("foundation",null),"test:foundation_seen");String book=BookRuntime.books(server).keySet().stream().findFirst().orElseThrow();var descriptor=new EventSource("chronicle_core:book_opened",book,"");
                var definition=new EventDefinition(EVENT,play.nextEventRevision(),"Foundation media","Disposable proof",MARK,"quiet_below:event.altar_offering",ObservationCatalog.Audience.PARTICIPANTS,Optional.empty(),List.of(),Map.of("test:foundation_seen",true));
                var config=new EventConfiguration(definition,PlayState.Mode.SYSTEM,Optional.empty(),play.elapsedTicks(),0,List.of(),true,descriptor);play.putEvent(config,null,true);EventProgressRuntime.arm(server,config);
                recurring(source,ACTOR_EVENT,FIRST,EventOccurrencePolicy.ONCE_PER_ACTOR);recurring(source,REPEAT_EVENT,REPEAT,EventOccurrencePolicy.PER_SOURCE_OCCURRENCE);
                once(source,"test:foundation_relative","test:relative_done",new EventSource(CompletionEventSources.TYPE,EVENT,""),20);
                once(source,"test:foundation_spawn","test:spawn_done",new EventSource(EntitySpawnEventSources.TYPE,"minecraft:warden",""),0);
                once(source,"test:foundation_encounter_cue","test:encounter_spawn_done",new EventSource(EntitySpawnEventSources.TYPE,"minecraft:pig",""),0);
                once(source,"test:foundation_source_review","test:source_review_seen",new EventSource(EntitySpawnEventSources.TYPE,"minecraft:cow",""),0);
                server.getCommandManager().getDispatcher().execute("gamerule doMobSpawning false",source);
                var point=(io.github.chronicle.core.authoring.AuthoringSite.Location)AuthoringRuntime.get(server).service().put(new io.github.chronicle.core.authoring.AuthoringSite.Location("test:foundation_pig_point",source.getWorld().getRegistryKey().getValue().toString(),1,a.getX()+8,a.getY(),a.getZ(),0,0),false);
                var action=new EventEncounterAction("test:foundation_pig",Optional.of(new io.github.chronicle.core.encounters.EncounterTarget(io.github.chronicle.core.encounters.EncounterTarget.Kind.POINT,point.id(),point.revision())));
                var parentDefinition=new EventDefinition("test:foundation_pig_parent",play.nextEventRevision(),"Pig parent","Disposable encounter source proof","","",ObservationCatalog.Audience.PARTICIPANTS,Optional.empty(),List.of(),Map.of(),List.of(action));
                var parent=new EventConfiguration(parentDefinition,PlayState.Mode.MANUAL,Optional.empty(),play.elapsedTicks(),0,List.of(),true,EventSource.empty());play.putEvent(parent,null,true);EventProgressRuntime.arm(server,parent);
                campaign.defineFlag(new Actor("foundation",null),"test:pressure_ready");
                campaign.start(new Actor("foundation",null),null);String fact=EventSources.prepare(server,"foundation-first-book",descriptor,a.getUuid(),true,"foundation-proof");require(fact!=null,"source not captured");EventSources.complete(server,fact,true);
                activity(source,a.getUuid(),"one");
                var warden=net.minecraft.entity.EntityType.WARDEN.create(source.getWorld(),net.minecraft.entity.SpawnReason.COMMAND);require(warden!=null,"Warden creation failed");warden.setPosition(a.getX()+3,a.getY(),a.getZ());require(source.getWorld().spawnEntity(warden),"actual fresh Warden spawn refused");
                EventRuntime.run(a,parentDefinition.id(),EventRuntime.review(server,parentDefinition.id()));
            }
            case "ready"->{if(!campaign.state().flag("test:foundation_seen")||!campaign.state().flag("test:relative_done")||!campaign.state().flag("test:spawn_done")||!campaign.state().flag("test:encounter_spawn_done")||EventRuntime.hasPending(server)||EventOccurrenceRuntime.hasPending(server)||EventOccurrenceRuntime.liveActors(server)!=1)return 0;require(!EventSources.committed(server).isEmpty(),"source missing before archival");}
            case "repeat"->{activity(source,source.getPlayerOrThrow().getUuid(),"two");activity(source,server.getPlayerManager().getPlayer("ChronicleFabricB").getUuid(),"three");}
            case "repeated"->{if(EventRuntime.hasPending(server)||EventOccurrenceRuntime.hasPending(server)||EventOccurrenceRuntime.liveActors(server)!=2)return 0;}
            case "uncertain"->{
                recurring(source,UNCERTAIN,"Uncertain cue must not replay",EventOccurrencePolicy.PER_SOURCE_OCCURRENCE,"test:foundation_uncertain_source",List.of());
                activity(source,source.getPlayerOrThrow().getUuid(),"uncertain","test:foundation_uncertain_source");
            }
            case "uncertain_ready"->{
                var e=play.state().executions().get(UNCERTAIN);if(e==null||e.phase()!=EventExecution.Phase.MEDIA_UNCERTAIN||RuntimeHolds.reason(server)==null)return 0;
                require(EventOccurrenceRuntime.hasPending(server),"uncertain claim was silently retired");
                require(RuntimeHolds.reason(server).contains("media is uncertain"),"uncertain claim does not hold new activity");
            }
            case "acknowledge"->EventRuntime.acknowledge(source.getPlayerOrThrow(),UNCERTAIN);
            case "uncertain_clear"->{if(EventOccurrenceRuntime.hasPending(server)||RuntimeHolds.reason(server)!=null)return 0;require(!play.state().executions().containsKey(UNCERTAIN),"acknowledged recurring execution was not retired");}
            case "source_recovery_holds"->{
                String id=EventSources.prepare(server,"foundation-interrupted-entity",new EventSource(EntitySpawnEventSources.TYPE,"minecraft:cow",""),null,true,"entityspawn:foundation-interrupted");
                require(id!=null,"entity preparation missing");
                var receipt=EventSources.pending(server).stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
                campaign.pause(new Actor("foundation",null));
                RuntimeHolds.hold(server,EventRuntime.HOLD,"Foundation retained parent Event");
                EventSources.fail(server,new java.io.IOException("Disposable uncertain entity source failure injection"));
                RuntimeHolds.hold(server,"test:unrelated","Foundation unrelated operation");
                boolean refused=false;try{EventSources.reload(source);}catch(IllegalStateException expected){refused=true;}
                require(refused&&!EventSources.reloadAvailable(server),"unrelated hold allowed recovery");
                RuntimeHolds.release(server,"test:unrelated");
                require(EventSources.reloadAvailable(server),"parent hold deadlocked source reload");
                EventSources.reload(source);EventSources.recover(source,receipt,false);
                require("Foundation retained parent Event".equals(RuntimeHolds.reason(server)),"source recovery released or changed its parent hold");
                require(EventSources.pending(server).isEmpty()&&!campaign.state().flag("test:source_review_seen"),"recovery fabricated entity activity");
                RuntimeHolds.release(server,EventRuntime.HOLD);campaign.resume(new Actor("foundation",null));
            }
            case "pressure"->{
                campaign.setFlag(new Actor("foundation",null),"test:pressure_ready",true);
                recurring(source,PRESSURE,"Capacity drain cue",EventOccurrencePolicy.PER_SOURCE_OCCURRENCE,"test:foundation_pressure_source",List.of("test:pressure_ready"));
                for(int i=0;i<EventOccurrenceState.MAX_LIVE_CLAIMS;i++)activity(source,source.getPlayerOrThrow().getUuid(),"pressure_"+i,"test:foundation_pressure_source");
                EventOccurrenceRuntime.collectForTrial(server);
                campaign.setFlag(new Actor("foundation",null),"test:pressure_ready",false);
                require(EventOccurrenceRuntime.onlyBackpressureHold(server)&&EventOccurrenceRuntime.liveClaims(server)==256,"full queue did not stop intake with recoverable pressure");
                campaign.pause(new Actor("foundation",null));AuthoringRuntime.get(server).enable(source.getPlayerOrThrow().getUuid());EventOccurrenceRuntime.reload(source);
                require(EventOccurrenceRuntime.onlyBackpressureHold(server)&&EventOccurrenceRuntime.liveClaims(server)==256,"reload lost exact-bound pressure");
                AuthoringRuntime.get(server).disable(source.getPlayerOrThrow().getUuid());campaign.resume(new Actor("foundation",null));
                require(CoreFlagMenu.detail(source.getPlayerOrThrow(),"flag:test:pressure_ready").controls().stream().anyMatch(c->c.label().equals("Turn On")&&c.enabled()),"capacity hold blocks prerequisite recovery UI");
            }
            case "pressure_release"->{
                require(EventOccurrenceRuntime.onlyBackpressureHold(server)&&EventOccurrenceRuntime.liveClaims(server)==256,"blocked flag queue lost its pressure hold or claims");
                server.getCommandManager().getDispatcher().execute("chronicle flags set test:pressure_ready true",source);
                require(campaign.state().flag("test:pressure_ready"),"real GM flag command could not unblock capacity");
            }
            case "pressure_clear"->{if(EventOccurrenceRuntime.hasPending(server)||RuntimeHolds.reason(server)!=null)return 0;require(!play.state().executions().containsKey(PRESSURE),"full queue did not drain to exact archives");}
            case "pause"->{
                campaign.pause(new Actor("foundation",null));
                // Editing display text must not reset per-character history.
                var old=EventRuntime.configuration(server,ACTOR_EVENT);var d=old.definition();
                var updated=new EventDefinition(d.id(),play.nextEventRevision(),d.label(),"Edited after purchase",d.text(),d.sound(),d.audience(),d.audienceRegion(),List.of(),Map.of(),List.of(),4);
                var next=new EventConfiguration(updated,old.mode(),Optional.empty(),play.elapsedTicks(),0,List.of(),true,old.source(),old.occurrencePolicy());
                EventOccurrenceRuntime.requireNoPending(server,d.id());play.putEvent(next,old,false);EventProgressRuntime.arm(server,next);
                require(!EventOccurrenceRuntime.interested(server,next,source.getPlayerOrThrow().getUuid()),"editing reset actor consumption");
            }
            case "online_guard"->{require(server.getPlayerManager().getPlayerList().size()==2,"observer/player left early");require(!EventSources.committed(server).isEmpty()&&!campaign.state().eventFlagOutcomes().isEmpty(),"automatic cleanup ran with players online");require(EventHistoryScheduler.status(server).enabled(),"automatic scheduler disabled");}
            case "release"->server.getPlayerManager().broadcast(Text.literal("Foundation trial RELEASE"),false);
            case "verify_auto"->{require(server.getPlayerManager().getPlayerList().isEmpty(),"players remain online");if(!EventSources.committed(server).isEmpty()||!campaign.state().eventFlagOutcomes().isEmpty()||EventOccurrenceRuntime.liveActors(server)!=0)return 0;require(campaign.state().flag("test:foundation_seen"),"cleanup removed campaign progress");require(EventHistoryScheduler.status(server).enabled(),"scheduler failed");require(campaign.state().history().stream().anyMatch(h->h.action().equals("event.history.archive.automatic")),"automatic audit missing");for(var participant:play.state().participants())require(!EventOccurrenceRuntime.interested(server,EventRuntime.configuration(server,ACTOR_EVENT),participant),"archival/restart reset first-player cue");}
            default->throw new IllegalArgumentException("unknown trial");
        }
        source.sendFeedback(()->Text.literal("Foundation trial PASS "+verb),false);return 1;
    }catch(Exception error){source.sendError(Text.literal("Foundation trial FAIL "+verb+": "+error));return 0;}}
    private static void recurring(ServerCommandSource source,String id,String text,EventOccurrencePolicy policy)throws Exception{
        recurring(source,id,text,policy,"test:foundation_activity",List.of());
    }
    private static void once(ServerCommandSource source,String id,String flag,EventSource descriptor,long delay)throws Exception{
        var server=source.getServer();var play=PlayRuntime.service(server);ChronicleRuntime.service(server).defineFlag(new Actor("foundation",null),flag);
        var d=new EventDefinition(id,play.nextEventRevision(),id,"Disposable exact source proof","","",ObservationCatalog.Audience.PARTICIPANTS,Optional.empty(),List.of(),Map.of(flag,true));
        var c=new EventConfiguration(d,PlayState.Mode.SYSTEM,Optional.empty(),play.elapsedTicks(),delay,List.of(),true,descriptor);
        play.putEvent(c,null,true);EventProgressRuntime.arm(server,c);
    }
    private static void recurring(ServerCommandSource source,String id,String text,EventOccurrencePolicy policy,String type,List<String> flags)throws Exception{
        var server=source.getServer();var play=PlayRuntime.service(server);var audience=policy==EventOccurrencePolicy.ONCE_PER_ACTOR?ObservationCatalog.Audience.SOURCE_ACTOR:ObservationCatalog.Audience.PARTICIPANTS;
        var d=new EventDefinition(id,play.nextEventRevision(),text,"Disposable proof",text,"",audience,Optional.empty(),List.of(),Map.of(),List.of(),4);
        var c=new EventConfiguration(d,PlayState.Mode.SYSTEM,Optional.empty(),play.elapsedTicks(),0,flags,true,new EventSource(type,"*",""),policy);
        play.putEvent(c,null,true);EventProgressRuntime.arm(server,c);
    }
    private static void activity(ServerCommandSource source,UUID actor,String id){activity(source,actor,id,"test:foundation_activity");}
    private static void activity(ServerCommandSource source,UUID actor,String id,String kind){var server=source.getServer();var type=new EventSource(kind,"*","");String receipt=EventSources.prepare(server,"foundation-activity-"+id,type,actor,true,"foundation:"+id);require(receipt!=null,"trial activity not captured");EventSources.complete(server,receipt,true);}
    static boolean failMediaForTrial(EventExecution execution){return "true".equals(System.getenv("CHRONICLE_FOUNDATION_TRIAL"))&&execution.eventId().equals(UNCERTAIN);}
    private static void fixture(ServerCommandSource source){var server=source.getServer();Path root=server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();require(root!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&Files.isRegularFile(root.resolve(".foundation-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp()),"unsafe fixture");}
    private static void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
}
