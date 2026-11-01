package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.encounters.*;
import io.github.chronicle.core.history.ImmutableHistoryArchive;
import io.github.chronicle.core.play.EventEncounterAction;
import io.github.chronicle.core.play.EventExecution;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Server-thread encounter authoring and at-most-once Event spawn coordinator. */
public final class Encounters {
    public static final String HOLD = "chronicle_core:encounters";
    public static final int MAX_EVENT_ENTITIES = 128;
    private static final int MAX_SPAWNS_PER_TICK = 4;
    private static final UUID AUTOMATIC_ACTOR = new UUID(0, 0);
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:skeleton", "minecraft:stray",
            "minecraft:bogged", "minecraft:creeper", "minecraft:spider", "minecraft:cave_spider", "minecraft:witch",
            "minecraft:slime", "minecraft:magma_cube", "minecraft:silverfish", "minecraft:endermite", "minecraft:phantom",
            "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:vex", "minecraft:ravager",
            "minecraft:guardian", "minecraft:blaze", "minecraft:ghast", "minecraft:hoglin", "minecraft:zoglin",
            "minecraft:piglin", "minecraft:piglin_brute", "minecraft:zombified_piglin", "minecraft:wither_skeleton",
            "minecraft:shulker", "minecraft:breeze",
            "minecraft:villager", "minecraft:wandering_trader", "minecraft:cow", "minecraft:pig", "minecraft:sheep",
            "minecraft:chicken", "minecraft:horse", "minecraft:donkey", "minecraft:rabbit", "minecraft:wolf",
            "minecraft:cat", "minecraft:fox", "minecraft:goat", "minecraft:iron_golem", "minecraft:snow_golem"
    );
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static final Map<String,EncounterGroup> DEFAULTS = new TreeMap<>();
    private Encounters() { }

    public enum Progress { IN_PROGRESS, COMPLETE }
    public record Snapshot(long groupRevision, long bindingRevision, Map<String, EncounterGroup> groups,
                           Map<String, EncounterBinding> bindings) { }
    public record Review(String eventId,UUID eventOperation,EncounterOperation child) {
        public Review{if(eventId==null||eventId.isBlank())throw new IllegalArgumentException("review Event ID is required");Objects.requireNonNull(eventOperation);Objects.requireNonNull(child);if(child.terminal()!=EncounterOperation.Terminal.UNCERTAIN)throw new IllegalArgumentException("review child must be uncertain");}
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(Encounters::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    /** Scenario mods register portable defaults during initialization, before a server starts. */
    public static synchronized void registerDefaults(Collection<EncounterGroup> groups){Objects.requireNonNull(groups,"encounter defaults are required");if(!WORLDS.isEmpty())throw new IllegalStateException("encounter defaults are frozen after server start");for(EncounterGroup group:groups){Objects.requireNonNull(group,"encounter default is required");EncounterGroup prior=DEFAULTS.putIfAbsent(group.id(),group);if(prior!=null&&!prior.equals(group))throw new IllegalArgumentException("conflicting encounter default: "+group.id());}}
    public static Map<String,EncounterGroup> groups(MinecraftServer server){return snapshot(server).groups();}

    public static Snapshot snapshot(MinecraftServer server) {
        Runtime runtime=runtime(server);
        return new Snapshot(runtime.groups.revision(), runtime.bindings.revision(), runtime.groups.groups(), runtime.bindings.bindings());
    }

    public static List<String> entityTypes() {
        return ALLOWED_TYPES.stream().filter(id -> Registries.ENTITY_TYPE.containsId(Identifier.of(id))).sorted().toList();
    }

    public static EncounterGroup save(ServerPlayerEntity player, EncounterGroup group, EncounterTarget target,
                                      EncounterGroup expectedGroup, EncounterBinding expectedBinding,
                                      long expectedGroupRevision, long expectedBindingRevision) throws IOException {
        requireEditing(player);Runtime runtime=runtime(player.getEntityWorld().getServer());
        if(runtime.groups.revision()!=expectedGroupRevision||runtime.bindings.revision()!=expectedBindingRevision)
            throw new IllegalStateException("Encounter groups or map bindings changed; reopen the editor");
        group.requireAllowed(EncounterTypeAllowlist.only(entityTypes()));validateTarget(player.getEntityWorld().getServer(),target);
        EncounterBinding binding=new EncounterBinding(group.id(),target);EncounterGroupRegistry nextGroups=runtime.groups.replace(group,expectedGroup);EncounterBindingRegistry nextBindings=runtime.bindings.replace(binding,expectedBinding);
        EncounterAuthoringCatalog saved=runtime.authoringStore.save(new EncounterAuthoringCatalog(runtime.groups,runtime.bindings),new EncounterAuthoringCatalog(nextGroups,nextBindings));runtime.groups=saved.groups();runtime.bindings=saved.bindings();
        return group;
    }

    public static void delete(ServerPlayerEntity player,String groupId,EncounterGroup expectedGroup,EncounterBinding expectedBinding,
                              long expectedGroupRevision,long expectedBindingRevision) throws IOException {
        requireEditing(player);MinecraftServer server=player.getEntityWorld().getServer();Runtime runtime=runtime(server);
        if(runtime.groups.revision()!=expectedGroupRevision||runtime.bindings.revision()!=expectedBindingRevision)
            throw new IllegalStateException("Encounter groups or map bindings changed; reopen the editor");
        if(EventRuntime.configurations(server).values().stream().flatMap(value->value.definition().encounterActions().stream()).anyMatch(action->action.groupId().equals(groupId)))
            throw new IllegalStateException("Remove this encounter group from every Event before deleting it");
        EncounterBindingRegistry nextBindings=runtime.bindings.remove(groupId,expectedBinding);EncounterGroupRegistry nextGroups=runtime.groups.remove(groupId,expectedGroup);
        EncounterAuthoringCatalog saved=runtime.authoringStore.save(new EncounterAuthoringCatalog(runtime.groups,runtime.bindings),new EncounterAuthoringCatalog(nextGroups,nextBindings));runtime.groups=saved.groups();runtime.bindings=saved.bindings();
    }

    /** Validates authoring pins and aggregate bounds without loading or spawning anything. */
    public static void validate(MinecraftServer server,List<EventEncounterAction> actions) {
        Runtime runtime=runtime(server);if(actions.size()>4)throw new IllegalArgumentException("An Event may spawn at most 4 encounter groups");
        int total=0;List<PlannedBox> candidates=new ArrayList<>();for(EventEncounterAction action:actions){EncounterGroup group=requireGroup(runtime,action.groupId());total=Math.addExact(total,group.entityCount());if(total>MAX_EVENT_ENTITIES)throw new IllegalArgumentException("Event encounter actions may spawn at most 128 entities");AuthoringSite site=requireSite(server,resolveTarget(server,runtime,action));double[] footprint=footprint(group);List<EncounterPlacementPlanner.Position> positions=EncounterPlacementPlanner.positions(site,group.entityCount(),footprint[0],footprint[1]);int index=0;for(EncounterMember member:group.members())for(int count=0;count<member.count();count++){var position=positions.get(index++);Box box=entityType(member.entityType()).getDimensions().getBoxAt(position.x(),position.y(),position.z());requireGeometry(world(server,site.dimension()),box);for(PlannedBox candidate:candidates)if(candidate.dimension.equals(site.dimension())&&candidate.box.intersects(box))throw new IllegalArgumentException("Encounter plan candidates overlap; choose a larger target or smaller composition");candidates.add(new PlannedBox(site.dimension(),box));}}
    }
    public static void requireCapacity(MinecraftServer server,int additionalOperations){Runtime runtime=runtime(server);if(additionalOperations<0||additionalOperations>4)throw new IllegalArgumentException("invalid encounter operation count");if(runtime.operations.state().operations().size()+additionalOperations>EncounterOperationJournal.MAX_LIVE_OPERATIONS)throw new IllegalStateException("Encounter operation history is full; archive completed Event history first");}

    /** Performs at most four external spawns. Every claimed spawn stays unreported until one confirmed world flush. */
    public static Progress progress(MinecraftServer server, EventExecution execution, BooleanSupplier eligible) throws IOException {
        Objects.requireNonNull(execution,"event execution is required");Objects.requireNonNull(eligible,"encounter eligibility is required");Runtime runtime=runtime(server);
        List<EventEncounterAction> actions=execution.configuration().definition().encounterActions();validate(server,actions);
        List<EncounterOperation> plans=new ArrayList<>();
        for(int index=0;index<actions.size();index++){EncounterOperation proposed=plan(server,execution,index,actions.get(index));plans.add(runtime.operations.ensure(proposed));}
        preflight(server,runtime,plans);
        int budget=MAX_SPAWNS_PER_TICK;
        for(int actionIndex=0;actionIndex<plans.size();actionIndex++){
            EncounterOperation current=current(runtime,plans.get(actionIndex).operationId());
            if(current.terminal()==EncounterOperation.Terminal.COMPLETE||current.terminal()==EncounterOperation.Terminal.REVIEWED)continue;
            if(current.terminal()==EncounterOperation.Terminal.UNCERTAIN)throw new IllegalStateException("Encounter spawn outcome is uncertain; retain the Event for GM review");
            EncounterOperation expected=plan(server,execution,actionIndex,actions.get(actionIndex));
            if(!current.planHash().equals(expected.planHash()))throw new IllegalStateException("Encounter composition or map binding changed after its operation was planned");
            List<EncounterSpawnEntry> confirmed=new ArrayList<>();
            for(EncounterSpawnEntry entry:current.entries()){
                if(entry.stage()==EncounterSpawnEntry.Stage.SPAWNED){try{requireExisting(server,entry);}catch(IllegalStateException failure){publishConfirmed(server,runtime,current.operationId(),confirmed);runtime.operations.uncertain(current.operationId());throw failure;}continue;}
                if(budget==0)break;
                if(!eligible.getAsBoolean())return Progress.IN_PROGRESS;
                Entity existing;
                try{requireLoaded(server,entry);existing=find(server,entry);}
                catch(RuntimeException failure){publishConfirmed(server,runtime,current.operationId(),confirmed);throw failure;}
                if(entry.stage()==EncounterSpawnEntry.Stage.CLAIMED){
                    if(existing==null){publishConfirmed(server,runtime,current.operationId(),confirmed);runtime.operations.uncertain(current.operationId());throw new IllegalStateException("A claimed encounter entity is missing after interruption; automatic respawn is forbidden");}
                    try{requireType(existing,entry);}catch(RuntimeException failure){publishConfirmed(server,runtime,current.operationId(),confirmed);runtime.operations.uncertain(current.operationId());throw failure;}
                    confirmed.add(entry);budget--;continue;
                }
                if(existing!=null){publishConfirmed(server,runtime,current.operationId(),confirmed);if(!confirmed.isEmpty())runtime.operations.uncertain(current.operationId());throw new IllegalStateException("A planned encounter UUID already exists; automatic replacement is forbidden");}
                requireSpawnSpace(server,entry);
                Entity entity;
                try{entity=create(server,entry);}catch(RuntimeException failure){publishConfirmed(server,runtime,current.operationId(),confirmed);throw failure;}
                runtime.operations.claim(current.operationId(),entry.entityId());
                if(!world(server,entry.dimension()).spawnEntity(entity)){publishConfirmed(server,runtime,current.operationId(),confirmed);runtime.operations.uncertain(current.operationId());throw new IllegalStateException("Minecraft refused a claimed encounter spawn; its outcome requires GM review");}
                confirmed.add(entry);budget--;
            }
            publishConfirmed(server,runtime,current.operationId(),confirmed);
            current=current(runtime,current.operationId());
            if(current.entries().stream().allMatch(entry->entry.stage()==EncounterSpawnEntry.Stage.SPAWNED))runtime.operations.complete(current.operationId());
            else return Progress.IN_PROGRESS;
            if(budget==0&&actionIndex+1<plans.size())return Progress.IN_PROGRESS;
        }
        return Progress.COMPLETE;
    }

    /** Checks the entire immutable Event plan before the first new claim or external spawn. */
    private static void preflight(MinecraftServer server,Runtime runtime,List<EncounterOperation> plans)throws IOException{
        List<PlannedBox> candidates=new ArrayList<>();
        for(EncounterOperation proposed:plans){EncounterOperation operation=current(runtime,proposed.operationId());if(operation.terminal()==EncounterOperation.Terminal.COMPLETE||operation.terminal()==EncounterOperation.Terminal.REVIEWED)continue;if(operation.terminal()==EncounterOperation.Terminal.UNCERTAIN)throw new IllegalStateException("Encounter spawn outcome is uncertain; retain the Event for GM review");
            for(EncounterSpawnEntry entry:operation.entries()){
                requireLoaded(server,entry);Entity existing=find(server,entry);
                if(entry.stage()==EncounterSpawnEntry.Stage.SPAWNED){try{if(existing==null)throw new IllegalStateException("A spawned encounter entity disappeared before its operation completed; GM review is required");requireType(existing,entry);}catch(IllegalStateException failure){runtime.operations.uncertain(operation.operationId());throw failure;}continue;}
                if(entry.stage()==EncounterSpawnEntry.Stage.CLAIMED){try{if(existing==null)throw new IllegalStateException("A claimed encounter entity is missing after interruption; automatic respawn is forbidden");requireType(existing,entry);}catch(IllegalStateException failure){runtime.operations.uncertain(operation.operationId());throw failure;}continue;}
                if(existing!=null)throw new IllegalStateException("A planned encounter UUID already exists; automatic replacement is forbidden");
                Box box=requireSpawnSpace(server,entry);for(PlannedBox candidate:candidates)if(candidate.dimension.equals(entry.dimension())&&candidate.box.intersects(box))throw new IllegalStateException("Encounter plan candidates overlap before spawning; no entity was claimed");candidates.add(new PlannedBox(entry.dimension(),box));
            }
        }
    }

    /** Publishes every earlier success before a later child failure can make the operation uncertain. */
    private static void publishConfirmed(MinecraftServer server,Runtime runtime,UUID operation,List<EncounterSpawnEntry> confirmed)throws IOException{
        if(confirmed.isEmpty())return;
        if(!server.saveAll(true,true,true))throw new IOException("Minecraft did not confirm the encounter entity world save");
        for(EncounterSpawnEntry entry:List.copyOf(confirmed))runtime.operations.markSpawned(operation,entry.entityId());
        confirmed.clear();
    }

    public static int maintainCompleted(MinecraftServer server) throws IOException {
        return runtime(server).operations.archiveAllComplete();
    }
    public static int liveOperationCount(MinecraftServer server){return runtime(server).operations.state().operations().size();}
    public static boolean hasUnresolved(MinecraftServer server){Runtime value=WORLDS.get(server);return value==null||value.failure!=null||value.operations.state().operations().values().stream().anyMatch(operation->operation.terminal()==EncounterOperation.Terminal.OPEN||operation.terminal()==EncounterOperation.Terminal.UNCERTAIN);}

    /** Exact operator-facing evidence for the first uncertain child of this parent Event. */
    public static Optional<Review> review(MinecraftServer server,String eventId){
        EventExecution execution=PlayRuntime.service(server).state().executions().get(eventId);if(execution==null||execution.phase()!=EventExecution.Phase.ENCOUNTER_EFFECTS)return Optional.empty();Runtime runtime=runtime(server);
        for(int i=0;i<execution.configuration().definition().encounterActions().size();i++){EncounterOperation operation=runtime.operations.liveOperation(childOperationId(execution.operationId(),i)).orElse(null);if(operation!=null&&operation.terminal()==EncounterOperation.Terminal.UNCERTAIN)return Optional.of(new Review(eventId,execution.operationId(),operation));}
        return Optional.empty();
    }

    /** Accepts the retained partial result only; no entity, plan, stage, refund, or world state is changed. */
    public static void acceptReview(ServerPlayerEntity player,Review expected)throws IOException{
        Objects.requireNonNull(expected,"encounter review is required");requireOperator(player);MinecraftServer server=player.getEntityWorld().getServer();
        if(RuntimeHolds.reason(server)==null||RuntimeHolds.heldByOther(server,EventRuntime.HOLD))throw new IllegalStateException("Encounter acceptance requires its parent Event hold and no unrelated Chronicle hold");
        Review current=review(server,expected.eventId()).orElseThrow(()->new IllegalStateException("The uncertain encounter result changed; review it again"));
        if(!current.equals(expected))throw new IllegalStateException("The uncertain encounter identity or stages changed; review it again");
        Runtime runtime=runtime(server);EncounterOperation reviewed=runtime.operations.reviewUncertain(expected.child().operationId());
        EventRuntime.encounterReviewAccepted(server,expected.eventId(),expected.eventOperation());
        ChronicleRuntime.service(server).recordOperation(new io.github.chronicle.core.campaign.Actor(player.getName().getString(),player.getUuidAsString()),"event.encounter.review.accepted",expected.eventId(),Map.of(
                "eventOperation",expected.eventOperation().toString(),"childOperation",reviewed.operationId().toString(),"groupFingerprint",reviewed.groupFingerprint(),"planHash",reviewed.planHash(),"stages",reviewed.entries().stream().map(entry->entry.entityId()+":"+entry.stage()).toList().toString()));
    }

    /** Disposable loopback fixture hook; production callers cannot satisfy its environment and world marker guards. */
    public static void testingSeedClaimedMissing(MinecraftServer server,EventExecution execution)throws IOException{
        if(!"true".equals(System.getenv("CHRONICLE_ENCOUNTERS_TRIAL"))||!server.isDedicated()||server.isOnlineMode()||server.getServerPort()!=25575||!"127.0.0.1".equals(server.getServerIp()))throw new IllegalStateException("encounter fault injection is disabled");
        Path worldRoot=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize(),fixture=Objects.requireNonNull(worldRoot.getParent()).resolve(".encounter-actions-fixture");if(!java.nio.file.Files.isRegularFile(fixture))throw new IllegalStateException("encounter fault injection requires a disposable fixture");
        if(execution.phase()!=EventExecution.Phase.ENCOUNTER_EFFECTS||execution.configuration().definition().encounterActions().isEmpty())throw new IllegalStateException("encounter fault injection requires a pending encounter Event");
        Runtime runtime=runtime(server);EncounterOperation operation=runtime.operations.ensure(plan(server,execution,0,execution.configuration().definition().encounterActions().getFirst()));EncounterSpawnEntry first=operation.entries().stream().filter(entry->entry.stage()==EncounterSpawnEntry.Stage.PLANNED).findFirst().orElseThrow(()->new IllegalStateException("fixture child has no planned entry"));runtime.operations.claim(operation.operationId(),first.entityId());runtime.operations.uncertain(operation.operationId());
    }

    static UUID childOperationId(UUID eventOperation,int actionIndex){return UUID.nameUUIDFromBytes(("chronicle:encounter:"+eventOperation+":"+actionIndex).getBytes(StandardCharsets.UTF_8));}
    static UUID entityId(UUID childOperation,int entryIndex){return UUID.nameUUIDFromBytes(("chronicle:encounter-entity:"+childOperation+":"+entryIndex).getBytes(StandardCharsets.UTF_8));}
    public static Optional<EncounterOperation> testingOperation(MinecraftServer server,UUID eventOperation,int actionIndex){Path worldRoot=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();if(!"true".equals(System.getenv("CHRONICLE_ENCOUNTERS_TRIAL"))||!server.isDedicated()||server.isOnlineMode()||server.getServerPort()!=25575||!"127.0.0.1".equals(server.getServerIp())||worldRoot.getParent()==null||!java.nio.file.Files.isRegularFile(worldRoot.getParent().resolve(".encounter-actions-fixture")))throw new IllegalStateException("encounter fixture inspection is disabled");return runtime(server).operations.liveOperation(childOperationId(eventOperation,actionIndex));}

    private static EncounterOperation plan(MinecraftServer server,EventExecution execution,int actionIndex,EventEncounterAction action){Runtime runtime=runtime(server);EncounterGroup group=requireGroup(runtime,action.groupId());EncounterTarget target=resolveTarget(server,runtime,action);AuthoringSite site=requireSite(server,target);UUID operation=childOperationId(execution.operationId(),actionIndex);UUID actor=execution.actor()==null?AUTOMATIC_ACTOR:execution.actor();double[] footprint=footprint(group);List<EncounterPlacementPlanner.Position> positions=EncounterPlacementPlanner.positions(site,group.entityCount(),footprint[0],footprint[1]);List<EncounterSpawnEntry> entries=new ArrayList<>();int index=0;for(EncounterMember member:group.members())for(int count=0;count<member.count();count++){var position=positions.get(index);entries.add(new EncounterSpawnEntry(entityId(operation,index),member.entityType(),site.dimension(),position.x(),position.y(),position.z(),EncounterSpawnEntry.Stage.PLANNED));index++;}String fingerprint=EncounterOperation.fingerprint(group);return new EncounterOperation(operation,actor,group.id(),fingerprint,EncounterOperation.planHash(operation,actor,fingerprint,entries),entries,EncounterOperation.Terminal.OPEN);}
    private static double[] footprint(EncounterGroup group){double width=0,height=0;for(EncounterMember member:group.members()){Box box=entityType(member.entityType()).getDimensions().getBoxAt(0,0,0);width=Math.max(width,box.maxX-box.minX);height=Math.max(height,box.maxY-box.minY);}return new double[]{width,height};}
    private static EncounterGroup requireGroup(Runtime runtime,String id){EncounterGroup group=runtime.groups.groups().get(id);if(group==null)throw new IllegalStateException("Encounter group is missing: "+id);group.requireAllowed(EncounterTypeAllowlist.only(entityTypes()));return group;}
    private static EncounterTarget resolveTarget(MinecraftServer server,Runtime runtime,EventEncounterAction action){EncounterTarget target=action.targetOverride().orElseGet(()->{EncounterBinding binding=runtime.bindings.bindings().get(action.groupId());if(binding==null)throw new IllegalStateException("Encounter group has no map binding: "+action.groupId());return binding.target();});validateTarget(server,target);return target;}
    private static void validateTarget(MinecraftServer server,EncounterTarget target){AuthoringSite site=requireSite(server,target);if(target.kind()==EncounterTarget.Kind.POINT&&!(site instanceof AuthoringSite.Location))throw new IllegalStateException("Encounter Point binding does not reference a Point");if(target.kind()==EncounterTarget.Kind.REGION&&!(site instanceof AuthoringSite.Region))throw new IllegalStateException("Encounter Region binding does not reference a Region");}
    private static AuthoringSite requireSite(MinecraftServer server,EncounterTarget target){AuthoringSite site=AuthoringRuntime.get(server).service().state().sites().get(target.siteId());if(site==null||site.revision()!=target.siteRevision())throw new IllegalStateException("Encounter map target is missing or changed: "+target.siteId());return site;}
    private static EncounterOperation current(Runtime runtime,UUID id){return runtime.operations.liveOperation(id).orElseGet(()->{try{return runtime.operations.archived(id).orElseThrow(()->new IllegalStateException("Encounter operation evidence is missing"));}catch(IOException failure){throw new IllegalStateException("Encounter operation archive is unreadable",failure);}});}
    private static void requireLoaded(MinecraftServer server,EncounterSpawnEntry entry){ServerWorld world=world(server,entry.dimension());requireLoaded(world,entityType(entry).getDimensions().getBoxAt(entry.x(),entry.y(),entry.z()));}
    private static void requireLoaded(ServerWorld world,Box box){int minChunkX=((int)Math.floor(box.minX))>>4,maxChunkX=((int)Math.floor(Math.nextDown(box.maxX)))>>4,minChunkZ=((int)Math.floor(box.minZ))>>4,maxChunkZ=((int)Math.floor(Math.nextDown(box.maxZ)))>>4;for(int x=minChunkX;x<=maxChunkX;x++)for(int z=minChunkZ;z<=maxChunkZ;z++)if(!world.getChunkManager().isChunkLoaded(x,z))throw new IllegalStateException("Encounter candidate footprint crosses an unloaded chunk; no chunk was forced");}
    private static Box requireSpawnSpace(MinecraftServer server,EncounterSpawnEntry entry){ServerWorld world=world(server,entry.dimension());EntityType<?> type=entityType(entry);Box box=type.getDimensions().getBoxAt(entry.x(),entry.y(),entry.z());requireLoaded(world,box);requireGeometry(world,box);if(!world.isSpaceEmpty(box))throw new IllegalStateException("Encounter candidate space is blocked; no entity was claimed");return box;}
    private static void requireGeometry(ServerWorld world,Box box){BlockPos low=new BlockPos((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ)),high=new BlockPos((int)Math.floor(Math.nextDown(box.maxX)),(int)Math.floor(Math.nextDown(box.maxY)),(int)Math.floor(Math.nextDown(box.maxZ)));if(!world.isInBuildLimit(low)||!world.isInBuildLimit(high))throw new IllegalArgumentException("Encounter candidate is outside dimension build limits");var border=world.getWorldBorder();for(BlockPos corner:List.of(new BlockPos(low.getX(),low.getY(),low.getZ()),new BlockPos(low.getX(),low.getY(),high.getZ()),new BlockPos(high.getX(),low.getY(),low.getZ()),new BlockPos(high.getX(),low.getY(),high.getZ())))if(!border.contains(corner))throw new IllegalArgumentException("Encounter candidate is outside the world border");}
    private static ServerWorld world(MinecraftServer server,String dimension){ServerWorld world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));if(world==null)throw new IllegalStateException("Encounter dimension is unavailable: "+dimension);return world;}
    private static Entity find(MinecraftServer server,EncounterSpawnEntry entry){return world(server,entry.dimension()).getEntity(entry.entityId());}
    private static Entity requireExisting(MinecraftServer server,EncounterSpawnEntry entry){Entity entity=find(server,entry);if(entity==null)throw new IllegalStateException("A spawned encounter entity disappeared before its operation completed; GM review is required");requireType(entity,entry);return entity;}
    private static void requireType(Entity entity,EncounterSpawnEntry entry){String actual=Registries.ENTITY_TYPE.getId(entity.getType()).toString();if(!actual.equals(entry.entityType()))throw new IllegalStateException("Encounter entity UUID exists with a different type");}
    private static EntityType<?> entityType(EncounterSpawnEntry entry){return entityType(entry.entityType());}
    private static EntityType<?> entityType(String entityType){Identifier id=Identifier.of(entityType);if(!ALLOWED_TYPES.contains(id.toString()))throw new IllegalStateException("Encounter entity type is not allowed: "+id);return Registries.ENTITY_TYPE.getOptionalValue(id).orElseThrow(()->new IllegalStateException("Encounter entity type is unavailable: "+id));}
    private static Entity create(MinecraftServer server,EncounterSpawnEntry entry){Identifier id=Identifier.of(entry.entityType());EntityType<?> type=entityType(entry);ServerWorld world=world(server,entry.dimension());Entity entity=type.create(world,SpawnReason.EVENT);if(!(entity instanceof MobEntity))throw new IllegalStateException("Encounter entity type is not a MobEntity: "+id);entity.setUuid(entry.entityId());entity.refreshPositionAndAngles(entry.x(),entry.y(),entry.z(),0,0);return entity;}
    private static void requireEditing(ServerPlayerEntity player){if(player==null||!player.getCommandSource().hasPermissionLevel(2)||!AuthoringRuntime.isAuthor(player)||!AuthoringRuntime.editable(player.getEntityWorld().getServer())||RuntimeHolds.reason(player.getEntityWorld().getServer())!=null)throw new IllegalStateException("Encounter edits require non-active operator Editing and no pending operation");}
    private static void requireOperator(ServerPlayerEntity player){if(player==null||!player.getCommandSource().hasPermissionLevel(2))throw new IllegalStateException("Encounter recovery requires operator permission");}
    private static Runtime runtime(MinecraftServer server){Runtime runtime=WORLDS.get(server);if(runtime==null||runtime.failure!=null)throw new IllegalStateException("Encounters are unavailable; inspect retained configuration and operation evidence");return runtime;}
    private static void start(MinecraftServer server){try{Path root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle");EncounterAuthoringStore authoringStore=new EncounterAuthoringStore(root.resolve("encounter-authoring.json"),root.resolve("encounter-authoring.initialized"),root.resolve("encounter-groups.json"),root.resolve("encounter-bindings.json"),EncounterTypeAllowlist.only(entityTypes()));EncounterOperationStore operations=new EncounterOperationStore(root.resolve("encounter-operations.bin"),new ImmutableHistoryArchive(root.resolve("encounter-operation-history"),"encounter-operation",64*1024));Runtime runtime=new Runtime(authoringStore,operations);Map<String,EncounterGroup> defaults; synchronized(Encounters.class){defaults=Map.copyOf(DEFAULTS);}EncounterAuthoringCatalog catalog=authoringStore.loadOrCreate(new EncounterGroupRegistry(0,defaults));runtime.groups=catalog.groups();runtime.bindings=catalog.bindings();operations.load();WORLDS.put(server,runtime);}catch(Exception failure){Runtime runtime=new Runtime(null,null);runtime.failure=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();WORLDS.put(server,runtime);RuntimeHolds.hold(server,HOLD,"Encounter evidence needs repair; no entity spawn was retried");ChronicleCore.LOGGER.error("Encounter runtime unavailable; retained evidence was not replaced",failure);}}
    private static final class Runtime{final EncounterAuthoringStore authoringStore;final EncounterOperationStore operations;EncounterGroupRegistry groups;EncounterBindingRegistry bindings;String failure;Runtime(EncounterAuthoringStore authoringStore,EncounterOperationStore operations){this.authoringStore=authoringStore;this.operations=operations;}}
    private record PlannedBox(String dimension,Box box){}
}
