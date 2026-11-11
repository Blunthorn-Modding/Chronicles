package io.github.chronicle.quietbelow.ecology;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.MapSiteAssignments;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.lang.management.ManagementFactory;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

import static net.minecraft.server.command.CommandManager.literal;

/** Guarded fixture for an actual generated ancient city; never registered in a normal runtime. */
public final class EcologyCityTrial {
    private static final String ENABLED = System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");
    private static final Pattern RUN_NAME = Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final String CITY = "ancient_city_native";
    private static final String HEART = "ancient_city_heart";
    private static final int MAX_CHUNKS = 256;
    private static final Map<MinecraftServer, Probe> PROBES = new IdentityHashMap<>();

    private EcologyCityTrial() { }

    public static void register() {
        if (ENABLED == null || ENABLED.isBlank()) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> commands(dispatcher));
        ServerTickEvents.START_SERVER_TICK.register(server->{Probe p=PROBES.get(server);if(p!=null&&p.sampling&&p.samples<SOAK_TICKS)p.tickStarted=System.nanoTime();});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            Probe p=PROBES.get(server);if(p==null||!p.sampling||p.samples>=SOAK_TICKS||p.tickStarted==0)return;
            p.nativeNanos[p.samples]=System.nanoTime()-p.tickStarted;
            p.ecologyNanos[p.samples++]=QuietBelowEcology.tickNanosForTest(server);p.tickStarted=0;
            if(!QuietBelowEcology.engineForTest(server).cropsAvailable()||!QuietBelowEcology.playing(server))p.uncertifiedTicks++;
            p.peakHeap=Math.max(p.peakHeap,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        });
    }

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("quiet_below_city_trial").requires(source -> source.hasPermissionLevel(2))
                .then(literal("prepare").executes(context -> prepare(context.getSource())))
                .then(literal("visit").executes(context -> visit(context.getSource())))
                .then(literal("indexed").executes(context -> indexed(context.getSource())))
                .then(literal("converge").executes(context -> converge(context.getSource())))
                .then(literal("certified").executes(context -> certified(context.getSource())))
                .then(literal("soak").executes(context -> soak(context.getSource())))
                .then(literal("measured").executes(context -> measured(context.getSource())))
                .then(literal("unload").executes(context -> unload(context.getSource())))
                .then(literal("unloaded").executes(context -> unloaded(context.getSource())))
                .then(literal("stable").executes(context -> stable(context.getSource())))
                .then(literal("save").executes(context -> save(context.getSource())))
                .then(literal("restart").executes(context -> restart(context.getSource()))));
    }

    private static int prepare(ServerCommandSource source) {
        try {
            requireFixture(source.getServer());
            ServerWorld world = source.getWorld();
            ServerPlayerEntity player = requirePlayer(source);
            require(QuietBelowEcology.engineForTest(source.getServer()).setup() == null, "city fixture is already configured");
            Probe probe = locate(world, player.getBlockPos());
            int chunks = (probe.box.getMaxX() >> 4) - (probe.box.getMinX() >> 4) + 1;
            final int chunkCount = chunks * ((probe.box.getMaxZ() >> 4) - (probe.box.getMinZ() >> 4) + 1);
            require(chunkCount <= MAX_CHUNKS, "actual ancient city covers " + chunkCount + " chunks (runtime policy is 256; ask the lead before changing it)");
            BlockPos heart = findHeart(world, probe.box);
            AuthoringRuntime authoring = AuthoringRuntime.get(source.getServer());
            authoring.enable(player.getUuid());
            String dimension = world.getRegistryKey().getValue().toString();
            AuthoringSite.Region city = (AuthoringSite.Region) authoring.service().put(new AuthoringSite.Region(CITY, dimension, 1,
                    new BlockPosition(probe.box.getMinX(), probe.box.getMinY(), probe.box.getMinZ()),
                    new BlockPosition(probe.box.getMaxX(), probe.box.getMaxY(), probe.box.getMaxZ())), false);
            AuthoringSite.Anchor anchor = (AuthoringSite.Anchor) authoring.service().put(new AuthoringSite.Anchor(HEART, dimension, 1,
                    new BlockPosition(heart.getX(), heart.getY(), heart.getZ()), "minecraft:reinforced_deepslate"), false);
            MapSiteAssignments.assign(source, "quiet_below:source_city", city.id());
            MapSiteAssignments.assign(source, "quiet_below:relic_heart", anchor.id());
            Probe ready=probe.withHeart(heart);
            // Count this exact generated terrain before indexing, rather than assuming another
            // world's population count. Keep position/type identity for an exact completeness test.
            for(BlockPos at:BlockPos.iterate(probe.box.getMinX(),probe.box.getMinY(),probe.box.getMinZ(),probe.box.getMaxX(),probe.box.getMaxY(),probe.box.getMaxZ())){
                var state=world.getBlockState(at);
                if(state.isOf(Blocks.SCULK)||state.isOf(Blocks.SCULK_VEIN)||state.isOf(Blocks.SCULK_CATALYST)||state.isOf(Blocks.SCULK_SENSOR)||state.isOf(Blocks.SCULK_SHRIEKER))
                    ready.source.put(new EcologyTerrain.Pos(at.getX(),at.getY(),at.getZ()),Registries.BLOCK.getId(state.getBlock()).toString());
            }
            require(ready.source.size()>32_768,"complete native city must exceed the original owned-cell cap");
            JsonObject census=new JsonObject();census.addProperty("bounds",bounds(probe.box));census.addProperty("chunks",chunkCount);census.addProperty("cells",ready.source.size());
            Map<String,Integer> byType=new TreeMap<>();ready.source.values().forEach(id->byType.merge(id,1,Integer::sum));JsonObject types=new JsonObject();byType.forEach(types::addProperty);census.add("byType",types);
            Path censusFile=source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/city-source-census.json");Files.createDirectories(censusFile.getParent());Files.writeString(censusFile,census.toString());
            PROBES.put(source.getServer(), ready);
            source.getServer().getTickManager().setTickRate(100F); // Faster explicit preparation, same per-tick ecology budgets.
            source.sendFeedback(() -> Text.literal("PASS ancient city prepared: actual bounds " + bounds(probe.box)
                    + "; chunks=" + chunkCount + "; Heart=" + coordinates(heart) + ". Start normal source indexing and visit city chunks."), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static int visit(ServerCommandSource source) {
        try {
            requireFixture(source.getServer());
            Probe probe = probe(source.getServer(), source.getWorld());
            ServerPlayerEntity player = requirePlayer(source);
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            require(engine.view().hold() == null, "native city index failed: " + engine.view().hold());
            if (!source.getWorld().getChunkManager().isChunkLoaded(probe.heart.getX() >> 4, probe.heart.getZ() >> 4)) probe.heartUnloadedVisits++;
            if (engine.view().indexed()) {
                source.sendFeedback(() -> Text.literal("CITY_INDEX_READY ticks=" + (source.getServer().getTicks() - probe.indexStartTick)
                        + "; visits=" + probe.visits), false);
            }
            if (probe.indexStartTick < 0) probe.indexStartTick = source.getServer().getTicks();
            int width = (probe.box.getMaxZ() >> 4) - (probe.box.getMinZ() >> 4) + 1;
            int count = ((probe.box.getMaxX() >> 4) - (probe.box.getMinX() >> 4) + 1) * width;
            int n = probe.visits++ % count;
            int cx = (probe.box.getMinX() >> 4) + n / width;
            int cz = (probe.box.getMinZ() >> 4) + n % width;
            player.teleportTo(new net.minecraft.world.TeleportTarget(source.getWorld(), new Vec3d((cx << 4) + 8.5, probe.box.getCenter().getY() + 2.0, (cz << 4) + 8.5), Vec3d.ZERO, 0, 0, net.minecraft.world.TeleportTarget.NO_OP));
            if (probe.visits % 32 == 0) {
                EcologyEngine.View view = engine.view();
                source.sendFeedback(() -> Text.literal("CITY_VISIT " + cx + "," + cz
                        + "; completed=" + view.completedChunks() + "/" + view.totalChunks()
                        + "; owned=" + view.owned() + "; serverTicks=" + source.getServer().getTicks()), false);
            } else source.sendFeedback(() -> Text.literal("CITY_VISIT " + cx + "," + cz), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static int indexed(ServerCommandSource source) {
        try {
            requireFixture(source.getServer());
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            Probe probe = probe(source.getServer(), source.getWorld());
            require(engine.view().indexed(), "normal loaded-only index is not complete yet");
            require(engine.view().owned() > 0, "actual city index found no native sculk cells");
            require(!probe.source.isEmpty()&&engine.view().owned()==probe.source.size(),"full index differs from this world's pre-index native census: expected "+probe.source.size()+"; got "+engine.view().owned());
            for(var cell:engine.cells())require(cell.after().id().equals(probe.source.get(cell.position())),"index lost or changed a native source position/type: "+cell.position());
            source.sendFeedback(() -> Text.literal("PASS ancient city indexed: bounds=" + bounds(probe.box) + "; chunks="
                    + engine.view().completedChunks() + "/" + engine.view().totalChunks() + "; owned=" + engine.view().owned()
                    + "; visits=" + probe.visits + "; elapsedTicks=" + (source.getServer().getTicks() - probe.indexStartTick)
                    + "; heartUnloadedVisits=" + probe.heartUnloadedVisits + "; indexing remains 256 reads/tick."), false);
            require(probe.heartUnloadedVisits > 0, "index did not observe a normal Heart-chunk unload while visiting actual city chunks");
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static int certified(ServerCommandSource source) {
        try {
            requireFixture(source.getServer()); EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            require(QuietBelowEcology.playing(source.getServer()), "native-city session has no eligible non-author participant");
            if (engine.view().unresolved()) { source.sendFeedback(() -> Text.literal("CITY_PENDING topology is still resolving."), false); return 1; }
            require(engine.view().connected() > 0, "native city resolved with zero rooted cells");
            source.sendFeedback(() -> Text.literal("PASS ancient city certified: " + engine.view().connected() + " rooted cells in eligible native play."), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }
    private static int converge(ServerCommandSource source){
        try{
            requireFixture(source.getServer());Probe probe=probe(source.getServer(),source.getWorld());
            source.getServer().getTickManager().setTickRate(20F);
            source.getServer().getPlayerManager().setViewDistance(12);
            var center=probe.box.getCenter();requirePlayer(source).teleportTo(new net.minecraft.world.TeleportTarget(source.getWorld(),new Vec3d(center.getX()+.5,center.getY()+2,center.getZ()+.5),Vec3d.ZERO,0,0,net.minecraft.world.TeleportTarget.NO_OP));
            source.sendFeedback(()->Text.literal("CITY_CONVERGE native 20 TPS and normal client view 12; loading the complete city for graph certification."),false);return 1;
        }catch(Exception e){return fail(source,e);}
    }

    private static final int SOAK_TICKS=1200;
    private static int soak(ServerCommandSource source){
        try{
            requireFixture(source.getServer());var engine=QuietBelowEcology.engineForTest(source.getServer());var p=probe(source.getServer(),source.getWorld());
            require(engine.cropsAvailable()&&QuietBelowEcology.playing(source.getServer()),"soak requires certified occupied play");
            p.samples=0;p.uncertifiedTicks=0;p.sampling=true;p.peakHeap=0;p.started=System.nanoTime();p.startOwned=engine.view().owned();p.startBytes=engine.view().journalBytes();
            source.sendFeedback(()->Text.literal("CITY_SOAK measuring 1,200 normal 20 TPS server ticks, including ecology and native world work."),false);return 1;
        }catch(Exception e){return fail(source,e);}
    }
    private static int measured(ServerCommandSource source){
        try{
            requireFixture(source.getServer());var engine=QuietBelowEcology.engineForTest(source.getServer());var p=probe(source.getServer(),source.getWorld());
            require(engine.failure()==null,"soak held: "+engine.failure());
            if(p.samples<SOAK_TICKS){source.sendFeedback(()->Text.literal("CITY_SOAK_PENDING "+p.samples+"/"+SOAK_TICKS),false);return 1;}
            p.sampling=false;JsonObject metrics=new JsonObject();metrics.addProperty("ticks",p.samples);metrics.addProperty("targetTps",20);
            metrics.addProperty("uncertifiedTicks",p.uncertifiedTicks);
            metrics.addProperty("elapsedSeconds",(System.nanoTime()-p.started)/1_000_000_000d);metrics.addProperty("ownedBefore",p.startOwned);metrics.addProperty("ownedAfter",engine.view().owned());
            metrics.addProperty("frontier",engine.view().frontier());metrics.addProperty("journalBefore",p.startBytes);metrics.addProperty("journalAfter",engine.view().journalBytes());
            metrics.addProperty("peakHeapBytes",p.peakHeap);metrics.addProperty("maxHeapBytes",Runtime.getRuntime().maxMemory());
            metrics.add("nativeTickMillis",timings(p.nativeNanos));metrics.add("ecologyCallbackMillis",timings(p.ecologyNanos));
            Path file=source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/city-scale-"+p.phase+".json");Files.writeString(file,metrics.toString());
            require(p.uncertifiedTicks==0&&engine.cropsAvailable()&&QuietBelowEcology.playing(source.getServer()),"city lost certified occupied play during soak; inspect "+file);
            require(metrics.getAsJsonObject("nativeTickMillis").get("mean").getAsDouble()<50,"native work cannot sustain 20 TPS; inspect "+file);
            require(metrics.getAsJsonObject("nativeTickMillis").get("p99").getAsDouble()<50,"native p99 exceeds the 20 TPS tick budget; inspect "+file);
            require(metrics.getAsJsonObject("ecologyCallbackMillis").get("p99").getAsDouble()<25,"ecology p99 consumes over half the tick budget; inspect "+file);
            source.sendFeedback(()->Text.literal("PASS ancient city measured: "+metrics),false);return 1;
        }catch(Exception e){return fail(source,e);}
    }
    private static JsonObject timings(long[] samples){
        long[] sorted=samples.clone();Arrays.sort(sorted);JsonObject result=new JsonObject();
        result.addProperty("mean",Arrays.stream(sorted).average().orElseThrow()/1_000_000d);
        result.addProperty("p95",sorted[(int)Math.ceil(sorted.length*.95)-1]/1_000_000d);result.addProperty("p99",sorted[(int)Math.ceil(sorted.length*.99)-1]/1_000_000d);
        result.addProperty("max",sorted[sorted.length-1]/1_000_000d);return result;
    }

    private static int unload(ServerCommandSource source) {
        try {
            requireFixture(source.getServer());
            Probe probe = probe(source.getServer(), source.getWorld());
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            require(engine.view().indexed(), "unload check needs a completed city index");
            ServerPlayerEntity player = requirePlayer(source);
            player.teleportTo(new net.minecraft.world.TeleportTarget(source.getWorld(), new Vec3d(probe.box.getMaxX() + 1024.5, 128, probe.box.getMaxZ() + 1024.5), Vec3d.ZERO, 0, 0, net.minecraft.world.TeleportTarget.NO_OP));
            source.sendFeedback(() -> Text.literal("CITY_UNLOAD moving the native client beyond the city; no fixture chunk is force-loaded."), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static int unloaded(ServerCommandSource source) {
        try {
            requireFixture(source.getServer());
            Probe probe = probe(source.getServer(), source.getWorld());
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            if(source.getWorld().getChunkManager().isChunkLoaded(probe.heart.getX() >> 4, probe.heart.getZ() >> 4)){
                source.sendFeedback(()->Text.literal("CITY_UNLOAD_PENDING waiting for native chunk tickets to expire."),false);return 1;
            }
            engine.flushVitality();probe.vitalityAtUnload=engine.vitality().ticks();
            probe.journalBytes = engine.view().journalBytes();
            source.sendFeedback(() -> Text.literal("PASS ancient city unload: natural client distance unloaded the Heart; baseline captured after normal unload settling."), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static int stable(ServerCommandSource source) {
        try {
            requireFixture(source.getServer()); Probe probe = probe(source.getServer(), source.getWorld());
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            require(!source.getWorld().getChunkManager().isChunkLoaded(probe.heart.getX() >> 4, probe.heart.getZ() >> 4), "city Heart chunk reloaded during unloaded stability check");
            require(engine.view().journalBytes() == probe.journalBytes, "unresolved/unloaded city wrote ecology history after baseline");
            require(engine.vitality().ticks()==probe.vitalityAtUnload,"unloaded city accumulated vitality time");
            source.sendFeedback(() -> Text.literal("PASS ancient city unloaded stability: five eligible seconds made no writes or vitality decay."), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static int save(ServerCommandSource source) {
        try {
            requireFixture(source.getServer()); EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            require(!QuietBelowEcology.playing(source.getServer()), "city restart snapshot requires paused play");
            engine.flushVitality();
            JsonObject snapshot = new JsonObject(); snapshot.addProperty("owned", engine.view().owned());
            snapshot.addProperty("vitalityTicks", engine.vitality().ticks()); snapshot.addProperty("bounds", bounds(probe(source.getServer(), source.getWorld()).box));
            snapshot.addProperty("stateDigest",stateDigest(engine));
            Path file = source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/city-trial.json");
            Files.createDirectories(file.getParent()); Files.writeString(file, snapshot.toString());
            source.sendFeedback(() -> Text.literal("PASS ancient city saved: owned=" + engine.view().owned() + "; vitalityTicks=" + engine.vitality().ticks()), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static int restart(ServerCommandSource source) {
        try {
            requireFixture(source.getServer());
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            require(engine.view().indexed() && engine.view().owned() > 0, "city ownership did not survive restart");
            Probe probe = probe(source.getServer(), source.getWorld());
            require(engine.setup().city().equals(CITY) && engine.setup().heart().equals(HEART), "restart did not restore exact city pins");
            probe.phase="city_restart";
            require(engine.enabled(), "restart did not restore enabled native-city ecology");
            JsonObject snapshot = JsonParser.parseString(Files.readString(source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/city-trial.json"))).getAsJsonObject();
            require(snapshot.keySet().equals(java.util.Set.of("owned", "vitalityTicks", "bounds", "stateDigest")), "city restart snapshot has unexpected fields");
            require(snapshot.get("owned").getAsInt() == engine.view().owned(), "restart changed saved city owned count");
            require(snapshot.get("bounds").getAsString().equals(bounds(probe.box)),"restart changed saved city bounds");
            require(snapshot.get("vitalityTicks").getAsLong() == engine.vitality().ticks(), "restart changed saved city vitality ticks");
            require(snapshot.get("stateDigest").getAsString().equals(stateDigest(engine)),"restart changed exact ownership, baseline, shape, vitality or tuning");
            source.sendFeedback(() -> Text.literal("PASS ancient city restart: exact bounds/pins and " + engine.view().owned()
                    + " owned cells restored while the saved session remains paused; no restart catch-up was requested."), false);
            return 1;
        } catch (Exception exception) { return fail(source, exception); }
    }

    private static String stateDigest(EcologyEngine engine)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        hash.update((engine.enabled()+"/"+engine.cleanup()+"/"+engine.speedPercent()+"/"+engine.cropRules()+"/"+engine.recoveryRules()+"/"+engine.costRules()+"/"+engine.vitality().checkpointHeader()+"\n").getBytes(StandardCharsets.UTF_8));
        var ordered=engine.cells().stream().sorted(Comparator.comparingInt((EcologyEngine.Cell c)->c.position().x()).thenComparingInt(c->c.position().y()).thenComparingInt(c->c.position().z())).toList();
        for(var cell:ordered){
            String row=EcologyEngine.pos(cell.position())+"/"+cell.before().id()+new TreeMap<>(cell.before().properties())+"/"+cell.after().id()+new TreeMap<>(cell.after().properties())+"/"+cell.adopted()+"/"+engine.vitality().checkpointCell(cell.position())+"\n";
            hash.update(row.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    private static Probe locate(ServerWorld world, BlockPos origin) {
        RegistryEntry<Structure> city = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).getOrThrow(StructureKeys.ANCIENT_CITY);
        Pair<BlockPos, RegistryEntry<Structure>> found = world.getChunkManager().getChunkGenerator().locateStructure(world, RegistryEntryList.of(city), origin, 128, false);
        require(found != null, "no vanilla ancient city located within 128 chunks");
        var chunk=world.getChunkManager().getChunk(found.getFirst().getX() >> 4, found.getFirst().getZ() >> 4, ChunkStatus.FULL, true);
        // Locate returns a placement position, not necessarily a point inside the underground box.
        StructureStart start = chunk.getStructureStart(city.value());
        if(start==null||!start.hasChildren())start=world.getStructureAccessor().getStructureStarts(new ChunkPos(found.getFirst()),s->s==city.value()).stream().filter(StructureStart::hasChildren).findFirst().orElse(StructureStart.DEFAULT);
        require(start.hasChildren(), "located ancient city has no native structure pieces");
        return new Probe(start.getBoundingBox(), null, 0, 0);
    }

    private static BlockPos findHeart(ServerWorld world, BlockBox box) {
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++)
            world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, true);
        for (int y = box.getMinY(); y <= box.getMaxY(); y++) for (int x = box.getMinX(); x <= box.getMaxX(); x++) for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (world.getBlockState(candidate).isOf(Blocks.SCULK)) {
                for(var direction:net.minecraft.util.math.Direction.values()){
                    BlockPos marker=candidate.offset(direction);
                    if(box.contains(marker)&&world.getBlockState(marker).isAir()){
                        world.setBlockState(marker,Blocks.REINFORCED_DEEPSLATE.getDefaultState(),net.minecraft.block.Block.NOTIFY_LISTENERS);
                        return marker; // One explicit fixture Heart touches real generated sculk.
                    }
                }
            }
        }
        throw new IllegalStateException("actual ancient city has no exposed sculk for a fixture Heart");
    }

    private static Probe probe(MinecraftServer server, ServerWorld world) {
        Probe probe = PROBES.get(server);
        if (probe != null) return probe;
        AuthoringSite city = MapSiteAssignments.resolve(server, "quiet_below:source_city").orElseThrow(() -> new IllegalStateException("city source pin is missing"));
        AuthoringSite heart = MapSiteAssignments.resolve(server, "quiet_below:relic_heart").orElseThrow(() -> new IllegalStateException("city Heart pin is missing"));
        require(city instanceof AuthoringSite.Region && heart instanceof AuthoringSite.Anchor, "city pins changed type");
        AuthoringSite.Region area = (AuthoringSite.Region) city;
        AuthoringSite.Anchor anchor = (AuthoringSite.Anchor) heart;
        Probe restored = new Probe(new BlockBox(area.min().x(), area.min().y(), area.min().z(), area.max().x(), area.max().y(), area.max().z()),
                new BlockPos(anchor.position().x(), anchor.position().y(), anchor.position().z()), 0, 0, -1);
        PROBES.put(server, restored);
        return restored;
    }

    private static ServerPlayerEntity requirePlayer(ServerCommandSource source) { return source.getPlayer() == null ? failPlayer() : source.getPlayer(); }
    private static ServerPlayerEntity failPlayer() { throw new IllegalStateException("city trial requires a connected test player"); }
    private static int fail(ServerCommandSource source, Exception exception) { source.sendError(Text.literal("FAIL ancient city trial: " + exception.getMessage())); return 0; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static String coordinates(BlockPos p) { return p.getX() + "," + p.getY() + "," + p.getZ(); }
    private static String bounds(BlockBox b) { return b.getMinX() + "," + b.getMinY() + "," + b.getMinZ() + ".." + b.getMaxX() + "," + b.getMaxY() + "," + b.getMaxZ(); }
    private static void requireFixture(MinecraftServer server) throws IOException {
        Path run = server.getRunDirectory().toRealPath(); Path parent = run.getParent();
        require(server.isDedicated() && !server.isOnlineMode(), "trial requires the offline dedicated server");
        require("127.0.0.1".equals(server.getServerIp()) && server.getServerPort() == 25575, "trial requires loopback 127.0.0.1:25575");
        require(parent != null && "test-runs".equals(parent.getFileName().toString()) && RUN_NAME.matcher(run.getFileName().toString()).matches(), "trial server root must be a timestamped direct child of test-runs");
    }

    private static final class Probe {
        final BlockBox box; final BlockPos heart; int visits, heartUnloadedVisits; long journalBytes, indexStartTick;
        final Map<EcologyTerrain.Pos,String> source=new HashMap<>();
        final long[] nativeNanos=new long[SOAK_TICKS],ecologyNanos=new long[SOAK_TICKS];
        int samples,startOwned,uncertifiedTicks;boolean sampling;long tickStarted,started,startBytes,peakHeap,vitalityAtUnload;String phase="city";
        Probe(BlockBox box, BlockPos heart, int visits, long journalBytes) { this(box, heart, visits, journalBytes, -1); }
        Probe(BlockBox box, BlockPos heart, int visits, long journalBytes, long indexStartTick) { this.box = box; this.heart = heart; this.visits = visits; this.journalBytes = journalBytes; this.indexStartTick = indexStartTick; }
        Probe withHeart(BlockPos value) { return new Probe(box, value, visits, journalBytes, indexStartTick); }
    }
}
