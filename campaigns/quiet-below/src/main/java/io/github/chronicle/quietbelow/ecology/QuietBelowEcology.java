package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.server.*;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import java.io.IOException;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;

/** Campaign lifecycle and the sole Minecraft adapter for ecology. */
public final class QuietBelowEcology {
    public static final String TOOL="quiet_below:ecology";
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private QuietBelowEcology() { }

    public static void register() {
        GameSetupTools.register(new GameSetupTools.Tool(TOOL,"Sculk ecology",
                "Quiet Below growth. Assign Source city and Relic Heart first, then index existing city sculk. " +
                "Enable only after reviewing a disposable map. Growth follows exposed surfaces during occupied active play; buildings have no protection.",
                List.of(new GameSetupTools.Verb("index","Index source city",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("enable","Enable growth",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("disable","Disable growth",GameSetupTools.Guard.OPERATOR,true),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::status,QuietBelowEcology::execute));
        ServerLifecycleEvents.SERVER_STARTED.register(QuietBelowEcology::start);
        ServerTickEvents.START_SERVER_TICK.register(server->{Runtime r=WORLDS.get(server);if(r!=null)r.engine.beginTick();});
        ServerTickEvents.END_SERVER_TICK.register(QuietBelowEcology::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{
            Runtime r=WORLDS.remove(server);if(r!=null)try {r.journal.close();}catch(IOException e){QuietBelow.LOGGER.error("Ecology journal close failed",e);}
        });
        EcologyHooks.register(new NativeHooks());
        EcologyNativeTrial.register();
    }
    private static void start(MinecraftServer server) {
        EcologyJournal journal=null;
        try {
            journal=new EcologyJournal(server.getSavePath(WorldSavePath.ROOT).resolve("quiet-below/ecology.jsonl"));
            EcologyEngine engine=new EcologyEngine(journal);Runtime runtime=new Runtime(engine,journal);WORLDS.put(server,runtime);
            if(engine.setup()!=null) attach(server,runtime);
            QuietBelow.LOGGER.info("Quiet Below ecology ready; configured={}, enabled={}",engine.setup()!=null,engine.enabled());
        } catch(Exception e) {
            Runtime r=WORLDS.get(server);
            if(r!=null)r.engine.fail("Ecology setup cannot be resolved: "+e.getMessage());
            else if(journal!=null)try{journal.close();}catch(IOException close){e.addSuppressed(close);}
            QuietBelow.LOGGER.error("Quiet Below ecology unavailable; existing journal preserved",e);
        }
    }
    private static void tick(MinecraftServer server) {
        Runtime r=WORLDS.get(server);if(r==null||r.engine.failure()!=null||r.engine.setup()==null)return;
        try {
            attach(server,r);
            Adapter world=adapter(server,r.engine.setup().dimension());
            // Indexing is explicit preparation, never starts from simply registering a role.
            if(!r.engine.view().indexed()) {
                if(ChronicleRuntime.service(server).state().phase()!=SessionPhase.ACTIVE)r.engine.index(world);
                return;
            }
            r.engine.tick(world,playing(server));
        } catch(Exception e) { hold(r,e); }
    }
    public static boolean playing(MinecraftServer server) {
        try {
            return ChronicleRuntime.service(server).state().phase()==SessionPhase.ACTIVE && RuntimeHolds.reason(server)==null
                    && !PlayRuntime.eligiblePlayers(server).isEmpty();
        } catch(IllegalStateException e) {return false;}
    }
    private static GameSetupTools.Status status(ServerCommandSource source) {
        Runtime r=runtime(source.getServer());EcologyEngine.View v=r.engine.view();
        List<Fact> facts=List.of(new Fact("Growth",v.hold()!=null?"HELD: "+v.hold():v.enabled()?"Enabled; "+(playing(source.getServer())?"occupied play":"waiting for occupied active play"):"Disabled"),
                new Fact("Source index",!v.configured()?"Not prepared":v.indexed()?"Complete":v.completedChunks()+" / "+v.totalChunks()+" chunks; visit remaining source chunks"),
                new Fact("Network",v.owned()+" owned; "+v.connected()+" connected; "+v.extent()),
                new Fact("Connectivity",v.unresolved()?"Unresolved / rebuilding; expansion waits":"Resolved"),
                new Fact("Surface frontier",v.frontier()+" discovered air cells"),
                new Fact("Persistence",v.journalBytes()+" bytes; append-only history"),
                new Fact("Review scope","Surface growth and physical containment. Crop benefits, stations and Heart ending follow later."));
        Map<String,String> disabled=new HashMap<>();
        if(v.configured())disabled.put("index","Already indexed or indexing; source ownership is not reset by this menu.");
        else try{pins(source.getServer());}catch(Exception e){disabled.put("index",e.getMessage());}
        if(!v.indexed())disabled.put("enable","Complete the loaded source index first.");
        if(v.enabled())disabled.put("enable","Growth is already enabled.");
        if(!v.configured()||!v.enabled())disabled.put("disable","Growth is already disabled.");
        if(v.hold()!=null){disabled.put("enable",v.hold());disabled.put("index",v.hold());}
        return new GameSetupTools.Status(v.revision()+r.menuRevision,facts,disabled);
    }
    private static String execute(ServerCommandSource source,String verb) throws IOException {
        Runtime r=runtime(source.getServer());
        switch(verb) {
            case "index" -> { Pins p=pins(source.getServer());r.engine.configure(p.setup,p.area,p.heart); }
            case "enable" -> { attach(source.getServer(),r);r.engine.mode(true); }
            case "disable" -> r.engine.mode(false);
            case "refresh" -> r.menuRevision++;
            default -> throw new IllegalArgumentException("Unknown ecology action");
        }
        QuietBelow.LOGGER.info("Ecology {} requested by {}",verb,source.getName());
        return switch(verb){case "index"->"Source indexing started. Keep source chunks loaded; it changes no terrain.";
            case "enable"->"Growth enabled. It advances only during occupied active play.";
            case "disable"->"Growth disabled; owned terrain and history retained.";default->"Ecology status refreshed.";};
    }
    private record Pins(EcologyEngine.Setup setup,EcologyEngine.Area area,Pos heart,String heartBlock) { }
    private static Pins pins(MinecraftServer server) {
        AuthoringSite city=MapSiteAssignments.resolve(server,"quiet_below:source_city").orElseThrow(()->new IllegalStateException("Assign Source city in Game setup first."));
        AuthoringSite heart=MapSiteAssignments.resolve(server,"quiet_below:relic_heart").orElseThrow(()->new IllegalStateException("Assign a Relic Heart block site first; use a clearly labelled temporary anchor in review maps."));
        if(!(city instanceof AuthoringSite.Region region)||!(heart instanceof AuthoringSite.Anchor anchor)||!city.dimension().equals(heart.dimension()))
            throw new IllegalStateException("Source city must be a region and Heart a block site in the same dimension.");
        var area=new EcologyEngine.Area(new Pos(region.min().x(),region.min().y(),region.min().z()),new Pos(region.max().x(),region.max().y(),region.max().z()));
        Pos hp=new Pos(anchor.position().x(),anchor.position().y(),anchor.position().z());
        return new Pins(new EcologyEngine.Setup(city.id(),city.revision(),heart.id(),heart.revision(),city.dimension()),area,hp,anchor.expectedBlock());
    }
    private static void attach(MinecraftServer server,Runtime r) {
        Pins p=pins(server);r.engine.attach(p.setup,p.area,p.heart);
        Adapter w=adapter(server,p.setup.dimension());
        if(w.loaded(p.heart)&&!w.get(p.heart).id().equals(p.heartBlock))throw new IllegalStateException("Relic Heart anchor block changed; ending mechanics are not implemented yet.");
    }
    private static Runtime runtime(MinecraftServer server) {
        if(!server.isOnThread())throw new IllegalStateException("Ecology requires the server thread");
        Runtime r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Ecology unavailable; inspect quiet-below/ecology.jsonl and server log.");return r;
    }
    static EcologyEngine engineForTest(MinecraftServer server){return runtime(server).engine;}
    private static Adapter adapter(MinecraftServer server,String dimension) {
        ServerWorld w=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));
        if(w==null)throw new IllegalStateException("Ecology dimension unavailable");return new Adapter(w);
    }
    private static void hold(Runtime r,Exception e) {
        if(r.engine.failure()==null){r.engine.fail(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());QuietBelow.LOGGER.error("Quiet Below ecology held; no automatic retry",e);}
    }
    private static final class Runtime {
        final EcologyEngine engine;final EcologyJournal journal;long menuRevision;
        Runtime(EcologyEngine e,EcologyJournal j){engine=e;journal=j;}
    }
    private static final class NativeHooks implements EcologyHooks.Handler {
        private Runtime find(ServerWorld w) {
            Runtime r=WORLDS.get(w.getServer());
            return r!=null&&r.engine.setup()!=null&&r.engine.setup().dimension().equals(w.getRegistryKey().getValue().toString())?r:null;
        }
        @Override public boolean owns(ServerWorld w,BlockPos p){Runtime r=find(w);return r!=null&&r.engine.owns(pos(p));}
        @Override public boolean active(ServerWorld w,BlockPos p){Runtime r=find(w);return r!=null&&r.engine.enabled()&&r.engine.rooted(pos(p))&&playing(w.getServer());}
        @Override public BlockState beforeWrite(ServerWorld w,BlockPos p,BlockState before,BlockState proposed,int flags) {
            Runtime r=find(w);if(r==null)return proposed;
            var origin=EcologyHooks.catalystOrigin();
            if(origin==null||origin.world()!=w||!r.engine.owns(pos(origin.pos())))return proposed;
            // Native transient changes remain native; only descendants/static shape changes need ownership intents.
            State previous=encode(before),next=encode(proposed);
            if(r.engine.owns(pos(p))&&previous.sameOwnedShape(next))return proposed;
            if(!next.network()) {
                if(r.engine.owns(pos(p)))return active(w,origin.pos())&&r.engine.reserveNativeLoss()?proposed:null;
                return previous.network()?null:proposed; // Never prune a neighbouring unrelated network.
            }
            if(!active(w,origin.pos()))return null;
            if(next.id().equals("minecraft:sculk_shrieker")){proposed=proposed.with(SculkShriekerBlock.CAN_SUMMON,true);next=encode(proposed);}
            try{return r.engine.nativeChange(new Adapter(w),pos(origin.pos()),pos(p),previous,next)?proposed:null;}
            catch(Exception e){hold(r,e);return null;}
        }
        @Override public void afterWrite(ServerWorld w,BlockPos p,BlockState before,BlockState proposed,boolean success) {
            Runtime r=find(w);if(r==null||!r.engine.owns(pos(p)))return;
            try {
                // Neighbor updates can change the same position before the outer write returns.
                if(w.getChunkManager().isChunkLoaded(p.getX()>>4,p.getZ()>>4))r.engine.observed(pos(p),encode(w.getBlockState(p)));
            }catch(Exception e){hold(r,e);}
        }
    }
    public static Pos pos(BlockPos p){return new Pos(p.getX(),p.getY(),p.getZ());}
    public static BlockPos blockPos(Pos p){return new BlockPos(p.x(),p.y(),p.z());}
    public static State encode(BlockState state){Map<String,String> properties=new TreeMap<>();state.getEntries().forEach((p,v)->properties.put(p.getName(),propertyName(p,v)));return new State(Registries.BLOCK.getId(state.getBlock()).toString(),properties);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static String propertyName(Property p,Comparable v){return p.name(v);}
    public static BlockState decode(State encoded){
        Block block=Registries.BLOCK.getOptionalValue(Identifier.of(encoded.id())).orElseThrow(()->new IllegalStateException("Unknown recorded block: "+encoded.id()));
        BlockState state=block.getDefaultState();
        for(var e:encoded.properties().entrySet()){
            Property<?> p=block.getStateManager().getProperty(e.getKey());if(p==null)throw new IllegalStateException("Unknown recorded property");state=with(state,p,e.getValue());
        }
        if(state.getProperties().size()!=encoded.properties().size())throw new IllegalStateException("Recorded state omits block properties: "+encoded.id());
        return state;
    }
    private static <T extends Comparable<T>> BlockState with(BlockState s,Property<T> p,String v){return s.with(p,p.parse(v).orElseThrow(()->new IllegalStateException("Invalid recorded property")));}
    public static final class Adapter implements EcologyTerrain {
        private final ServerWorld world;
        public Adapter(ServerWorld w){world=w;}
        @Override public boolean loaded(Pos p){return world.isInBuildLimit(blockPos(p))&&world.getWorldBorder().contains(blockPos(p))&&world.getChunkManager().isChunkLoaded(p.x()>>4,p.z()>>4);}
        private BlockState checked(Pos p){if(!loaded(p))throw new IllegalStateException("Unloaded ecology read");return world.getBlockState(blockPos(p));}
        @Override public State get(Pos p){return encode(checked(p));}
        @Override public boolean solidFace(Pos p,int face){return checked(p).isSideSolidFullSquare(world,blockPos(p),Direction.byId(FACES[face]));}
        @Override public boolean substrate(Pos p){return checked(p).isIn(BlockTags.SCULK_REPLACEABLE)&&world.getBlockEntity(blockPos(p))==null;}
        @Override public int light(Pos p){checked(p);return world.getLightLevel(blockPos(p));}
        @Override public boolean set(Pos p,State state){
            checked(p);
            try(var ignored=EcologyHooks.ownWrite()){return world.setBlockState(blockPos(p),decode(state),Block.NOTIFY_ALL);}
        }
    }
}
