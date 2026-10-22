package io.github.chronicle.quietbelow.ecology;

import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SculkCatalystBlockEntity;
import net.minecraft.block.entity.SculkSensorBlockEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import io.github.chronicle.quietbelow.content.SculkAppearance;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in real-vanilla hook trial. It is unavailable outside the disposable loopback fixture. */
public final class EcologyNativeTrial {
    private static final String ENABLED = System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");
    private static final Pattern RUN_NAME = Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final BlockPos HEART = new BlockPos(0, 64, 1);
    private static final BlockPos SENSOR = new BlockPos(1, 65, 1);
    private static final BlockPos CATALYST = new BlockPos(2, 65, 1);
    private static final BlockPos DEATH = new BlockPos(3, 65, 1);

    private EcologyNativeTrial() {
    }

    public static void register() {
        EcologyWardenTrial.register();
        EcologyAppearanceTrial.register();
        EcologyItemTrial.register();
        EcologyCropTrial.register();
        EcologyRecoveryTrial.register();
        EcologyCostsTrial.register();
        EcologyCityTrial.register();
        EcologyHeartTrial.register();
        if (ENABLED == null || ENABLED.isBlank()) {
            return;
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> commands(dispatcher));
    }

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("quiet_below_native_trial")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("inactive").executes(context -> run(context.getSource(), false)))
                .then(literal("active").executes(context -> run(context.getSource(), true)))
                .then(literal("vitality_fading").executes(context->vitality(context.getSource(),3)))
                .then(literal("vitality_zero").executes(context->vitality(context.getSource(),0)))
                .then(literal("vitality_recovering").executes(context->vitality(context.getSource(),1)))
                .then(literal("vitality_ready").executes(context->vitality(context.getSource(),2))));
    }
    private static int vitality(ServerCommandSource source,int stage){
        try{
            requireFixture(source.getServer());var e=QuietBelowEcology.engineForTest(source.getServer());var w=source.getWorld();
            var end=new EcologyTerrain.Pos(4,64,0);var sensorPos=new BlockPos(4,65,0);var sensorCell=QuietBelowEcology.pos(sensorPos);
            require(source.getPlayer()!=null&&e.setup()!=null&&e.setup().city().equals("vitality_source"),"wrong vitality fixture");
            require(e.owns(end)&&e.owns(sensorCell)&&w.getBlockState(QuietBelowEcology.blockPos(end)).isOf(net.minecraft.block.Blocks.SCULK),"dormant terrain or ownership was lost");
            require(QuietBelowEcology.playing(source.getServer()),"vitality probe requires occupied ACTIVE play");
            if(stage==3){require(!e.rooted(end)&&e.vitality().value(end)>0&&e.vitality().value(end)<EcologyVitality.READY,"branch is not positive below 25 percent");}
            else if(stage==0){require(!e.rooted(end)&&e.vitality().value(end)==0,"detached branch has not reached zero vitality");}
            else if(stage==1){require(e.rooted(end)&&!e.living(end),"reconnected branch should be recovering below 25 percent");}
            else require(e.living(end)&&e.living(sensorCell),"reconnected branch has not recovered to 25 percent");
            require(w.getBlockState(QuietBelowEcology.blockPos(end)).get(SculkAppearance.VITALITY)
                    ==EcologyAppearance.stage(e.vitality().value(end),e.rooted(end),e.vitality().mode(end)),"sculk color disagrees with durable vitality");
            require(w.getBlockState(sensorPos).get(SculkAppearance.VITALITY)
                    ==EcologyAppearance.stage(e.vitality().value(sensorCell),e.rooted(sensorCell),e.vitality().mode(sensorCell)),"sensor color disagrees with durable vitality");
            var sensor=w.getBlockEntity(sensorPos,BlockEntityType.SCULK_SENSOR).orElseThrow();
            var before=w.getBlockState(sensorPos);sensor.getVibrationCallback().accept(w,new BlockPos(7,64,0),GameEvent.STEP,source.getPlayer(),source.getPlayer(),1.0F);
            if(stage!=2)require(w.getBlockState(sensorPos).equals(before),"low-vitality/detached sensor responded");
            else require(!SculkSensorBlock.isInactive(w.getBlockState(sensorPos)),"recovered sensor did not respond");
            source.sendFeedback(()->Text.literal("PASS vitality "+(stage==0?"zero":stage==1?"recovering":stage==3?"fading":"ready")+": retained terrain and native sensor gate verified."),false);return 1;
        }catch(Exception ex){source.sendError(Text.literal("FAIL vitality trial: "+detail(ex)));return 0;}
    }

    private static int run(ServerCommandSource source, boolean active) {
        try {
            requireFixture(source.getServer());
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            ServerWorld world = source.getWorld();
            require(engine.setup() != null, "ecology is not configured");
            require(engine.setup().dimension().equals(world.getRegistryKey().getValue().toString()),
                    "command source is not in the ecology dimension");
            require(engine.owns(QuietBelowEcology.pos(CATALYST)), "fixture catalyst is not ecology-owned");
            require(engine.owns(QuietBelowEcology.pos(SENSOR)), "fixture sensor is not ecology-owned");
            require(world.getBlockState(HEART).isOf(net.minecraft.block.Blocks.REINFORCED_DEEPSLATE),
                    "fixture Heart is missing");
            require(source.getPlayer() != null, "trial command requires a player source");
            require(QuietBelowEcology.playing(source.getServer()) == active,
                    active ? "active trial requires occupied ACTIVE play" : "inactive trial requires paused or unoccupied play");
            if (active) {
                return startActive(source, world, engine);
            }
            inactive(source, world);
            source.sendFeedback(() -> Text.literal(
                    "PASS native inactive hooks: catalyst intake/charge and sensor activation were blocked; no world mutation requested."), false);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("FAIL native ecology trial: " + detail(exception)));
            return 0;
        }
    }

    private static void inactive(ServerCommandSource source, ServerWorld world) {
        inactiveFeatures(source,world);
        SculkCatalystBlockEntity catalyst = catalyst(world);
        int cursors = catalyst.getEventListener().getSpreadManager().getCursors().size();
        int charge = charge(catalyst);
        TrialZombie victim = new TrialZombie(world);
        boolean accepted = catalyst.getEventListener().listen(
                world, GameEvent.ENTITY_DIE, GameEvent.Emitter.of(victim), Vec3d.ofBottomCenter(DEATH));
        require(!accepted, "inactive owned catalyst accepted ENTITY_DIE");
        require(!victim.isExperienceDroppingDisabled(), "inactive catalyst disabled victim XP");
        require(catalyst.getEventListener().getSpreadManager().getCursors().size() == cursors,
                "inactive catalyst added a spread cursor");
        require(charge(catalyst) == charge, "inactive catalyst changed queued charge");

        SculkSensorBlockEntity sensor = world.getBlockEntity(SENSOR, BlockEntityType.SCULK_SENSOR)
                .orElseThrow(() -> new IllegalStateException("fixture sensor block entity is missing"));
        var before = world.getBlockState(SENSOR);
        int frequency = sensor.getLastVibrationFrequency();
        require(SculkSensorBlock.isInactive(before), "fixture sensor must begin inactive");
        var player = source.getPlayer();
        require(player != null, "trial command requires a player source");
        sensor.getVibrationCallback().accept(
                world, DEATH, GameEvent.STEP, player, player, 1.0F);
        require(world.getBlockState(SENSOR).equals(before), "inactive owned sensor changed block state");
        require(sensor.getLastVibrationFrequency() == frequency, "inactive owned sensor accepted a vibration");
    }

    static void inactiveFeatures(ServerCommandSource source,ServerWorld world) {
        BlockPos sensorPos=new BlockPos(8,65,8),shriekPos=new BlockPos(10,65,8);
        var e=QuietBelowEcology.engineForTest(source.getServer());
        require(!e.owns(QuietBelowEcology.pos(sensorPos))&&!e.owns(QuietBelowEcology.pos(shriekPos)),"unowned probe unexpectedly owned");
        world.setBlockState(sensorPos,net.minecraft.block.Blocks.SCULK_SENSOR.getDefaultState());
        world.setBlockState(shriekPos,net.minecraft.block.Blocks.SCULK_SHRIEKER.getDefaultState());
        try {
            var sensor=world.getBlockEntity(sensorPos,BlockEntityType.SCULK_SENSOR).orElseThrow();
            var shrieker=world.getBlockEntity(shriekPos,BlockEntityType.SCULK_SHRIEKER).orElseThrow();
            var player=source.getPlayer();
            var foreign=new net.minecraft.world.event.Vibrations.Callback(){
                public int getRange(){return 8;}
                public net.minecraft.world.event.PositionSource getPositionSource(){return sensor.getVibrationCallback().getPositionSource();}
                public boolean accepts(ServerWorld w,BlockPos p,net.minecraft.registry.entry.RegistryEntry<GameEvent> event,GameEvent.Emitter emitter){return true;}
                public void accept(ServerWorld w,BlockPos p,net.minecraft.registry.entry.RegistryEntry<GameEvent> event,net.minecraft.entity.Entity entity,net.minecraft.entity.Entity owner,float distance){ }
            };
            require(!EcologyFeatureActivity.suppressTick(world,new net.minecraft.world.event.Vibrations.ListenerData(),foreign),"a foreign receiver at the sensor position was governed");
            sensor.getVibrationCallback().accept(world,sensorPos.up(),GameEvent.STEP,player,player,1);
            require(SculkSensorBlock.isInactive(world.getBlockState(sensorPos)),"unowned campaign sensor responded through vanilla delivery");
            ((SculkSensorBlock)net.minecraft.block.Blocks.SCULK_SENSOR).setActive(player,world,sensorPos,world.getBlockState(sensorPos),9,1);
            require(SculkSensorBlock.isInactive(world.getBlockState(sensorPos)),"unowned campaign sensor accepted direct activation");
            shrieker.shriek(world,player);
            require(!world.getBlockState(shriekPos).get(net.minecraft.block.SculkShriekerBlock.SHRIEKING),"unowned campaign shrieker responded to stepping");
            for(var receiver:java.util.List.<net.minecraft.world.event.Vibrations>of(sensor,shrieker)) {
                var data=receiver.getVibrationListenerData();
                var vibration=new net.minecraft.world.event.listener.Vibration(GameEvent.STEP,8,Vec3d.ofCenter(sensorPos.up()),player);
                data.setVibration(vibration);data.setDelay(8);data.setSpawnParticle(true);
                data.getSelector().tryAccept(vibration,world.getTime());
                net.minecraft.world.event.Vibrations.Ticker.tick(world,data,receiver.getVibrationCallback());
                require(data.getVibration()==null&&data.getDelay()==0&&!data.shouldSpawnParticle()
                        &&data.getSelector().getVibrationToTick(Long.MAX_VALUE).isEmpty(),"inactive feature retained a vibration/particle backlog");
            }
            world.setBlockState(sensorPos,world.getBlockState(sensorPos).with(SculkSensorBlock.POWER,9)
                    .with(SculkSensorBlock.SCULK_SENSOR_PHASE,net.minecraft.block.enums.SculkSensorPhase.ACTIVE)
                    .with(SculkSensorBlock.WATERLOGGED,true).with(SculkAppearance.VITALITY,EcologyAppearance.FADING));
            require(world.getBlockState(sensorPos).getWeakRedstonePower(world,sensorPos,net.minecraft.util.math.Direction.UP)==0,"inactive sensor emitted redstone");
            require(world.getBlockState(sensorPos).getComparatorOutput(world,sensorPos,net.minecraft.util.math.Direction.UP)==0,"inactive sensor emitted comparator output");
            world.setBlockState(shriekPos,world.getBlockState(shriekPos).with(net.minecraft.block.SculkShriekerBlock.SHRIEKING,true).with(net.minecraft.block.SculkShriekerBlock.CAN_SUMMON,true));
            world.scheduleBlockTick(sensorPos,net.minecraft.block.Blocks.SCULK_SENSOR,30);
            world.scheduleBlockTick(shriekPos,net.minecraft.block.Blocks.SCULK_SHRIEKER,90);
            net.minecraft.world.event.Vibrations.Ticker.tick(world,sensor.getVibrationListenerData(),sensor.getVibrationCallback());
            net.minecraft.world.event.Vibrations.Ticker.tick(world,shrieker.getVibrationListenerData(),shrieker.getVibrationCallback());
            require(SculkSensorBlock.isInactive(world.getBlockState(sensorPos))&&world.getBlockState(sensorPos).get(SculkSensorBlock.POWER)==0,"inactive sensor did not settle");
            require(!world.getBlockState(shriekPos).get(net.minecraft.block.SculkShriekerBlock.SHRIEKING),"inactive shrieker did not settle");
            require(world.getBlockEntity(sensorPos)==sensor&&world.getBlockEntity(shriekPos)==shrieker,"settling replaced a native block entity");
            require(world.getBlockState(sensorPos).get(SculkSensorBlock.WATERLOGGED)
                    &&world.getBlockState(sensorPos).get(SculkAppearance.VITALITY)==EcologyAppearance.FADING
                    &&world.getBlockState(shriekPos).get(net.minecraft.block.SculkShriekerBlock.CAN_SUMMON),"settling lost fluid, appearance or summon capability");
            require(!world.getBlockTickScheduler().isQueued(sensorPos,net.minecraft.block.Blocks.SCULK_SENSOR)
                    &&!world.getBlockTickScheduler().isQueued(shriekPos,net.minecraft.block.Blocks.SCULK_SHRIEKER),"inactive features retained delayed activation ticks");
            int settled=0;
            try{
                for(int x=0;x<20;x++){
                    BlockPos p=new BlockPos(x,65,12);
                    world.setBlockState(p,net.minecraft.block.Blocks.SCULK_SENSOR.getDefaultState().with(SculkSensorBlock.POWER,7)
                            .with(SculkSensorBlock.SCULK_SENSOR_PHASE,net.minecraft.block.enums.SculkSensorPhase.ACTIVE));
                    var be=world.getBlockEntity(p,BlockEntityType.SCULK_SENSOR).orElseThrow();
                    net.minecraft.world.event.Vibrations.Ticker.tick(world,be.getVibrationListenerData(),be.getVibrationCallback());
                    if(SculkSensorBlock.isInactive(world.getBlockState(p)))settled++;
                    require(world.getBlockState(p).getWeakRedstonePower(world,p,net.minecraft.util.math.Direction.UP)==0,"pending settling emitted power");
                }
                require(settled>0&&settled<=16&&settled<20,"native phase settling is not bounded");
                world.setBlockState(shriekPos,net.minecraft.block.Blocks.AIR.getDefaultState());
                shrieker.shriek(world,player);
                require(world.getBlockState(shriekPos).isAir(),"removed unowned shrieker responded through a stale receiver");
            }finally{for(int x=0;x<20;x++)world.setBlockState(new BlockPos(x,65,12),net.minecraft.block.Blocks.AIR.getDefaultState());}
        } finally {
            world.setBlockState(sensorPos,net.minecraft.block.Blocks.AIR.getDefaultState());
            world.setBlockState(shriekPos,net.minecraft.block.Blocks.AIR.getDefaultState());
        }
    }

    private static int startActive(ServerCommandSource source, ServerWorld world, EcologyEngine engine) {
        inactiveFeatures(source,world);
        require(engine.rooted(QuietBelowEcology.pos(CATALYST)), "fixture catalyst is not rooted yet; retry after topology resolves");
        SculkCatalystBlockEntity catalyst = catalyst(world);
        int beforeCharge = charge(catalyst);
        int cursors = catalyst.getEventListener().getSpreadManager().getCursors().size();
        TrialZombie victim = new TrialZombie(world);
        boolean accepted = catalyst.getEventListener().listen(
                world, GameEvent.ENTITY_DIE, GameEvent.Emitter.of(victim), Vec3d.ofBottomCenter(DEATH));
        require(!accepted, "active campaign catalyst accepted ENTITY_DIE");
        require(!victim.isExperienceDroppingDisabled(), "campaign catalyst consumed victim XP");
        require(charge(catalyst) == beforeCharge && catalyst.getEventListener().getSpreadManager().getCursors().size() == cursors,
                "campaign catalyst queued death/XP spread");
        // A legacy queue is retained but cannot tick, even during occupied active play.
        catalyst.getEventListener().getSpreadManager().spread(DEATH, 32);
        int queued = charge(catalyst);
        long revision = engine.view().journalBytes();
        SculkCatalystBlockEntity.tick(world, CATALYST, world.getBlockState(CATALYST), catalyst);
        require(charge(catalyst) == queued, "campaign catalyst advanced legacy queued charge");
        require(engine.view().journalBytes() == revision, "campaign catalyst journaled native growth");
        var unowned=new SculkCatalystBlockEntity(new BlockPos(12,65,1),net.minecraft.block.Blocks.SCULK_CATALYST.getDefaultState());
        TrialZombie second=new TrialZombie(world);
        require(!unowned.getEventListener().listen(world,GameEvent.ENTITY_DIE,GameEvent.Emitter.of(second),Vec3d.ofBottomCenter(DEATH)),"unowned catalyst bypassed the configured campaign growth policy");
        require(!second.isExperienceDroppingDisabled(),"unowned catalyst consumed XP in the campaign dimension");
        source.sendFeedback(() -> Text.literal("PASS native active hooks: deaths/XP ignored and legacy charge frozen; ecology is the only campaign growth system."), false);
        return 1;
    }

    private static SculkCatalystBlockEntity catalyst(ServerWorld world) {
        return world.getBlockEntity(CATALYST, BlockEntityType.SCULK_CATALYST)
                .orElseThrow(() -> new IllegalStateException("fixture catalyst block entity is missing"));
    }

    private static int charge(SculkCatalystBlockEntity catalyst) {
        return catalyst.getEventListener().getSpreadManager().getCursors().stream()
                .mapToInt(cursor -> cursor.getCharge()).sum();
    }

    private static void requireFixture(MinecraftServer server) throws IOException {
        Path run = server.getRunDirectory().toRealPath();
        Path parent = run.getParent();
        require(server.isDedicated(), "trial requires a dedicated server");
        require(!server.isOnlineMode(), "trial requires the offline disposable server");
        require("127.0.0.1".equals(server.getServerIp()) && server.getServerPort() == 25575,
                "trial requires loopback 127.0.0.1:25575");
        require(parent != null && parent.getFileName() != null && "test-runs".equals(parent.getFileName().toString())
                        && run.getFileName() != null && RUN_NAME.matcher(run.getFileName().toString()).matches(),
                "trial server root must be a timestamped direct child of test-runs");
    }

    private static String detail(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class TrialZombie extends ZombieEntity {
        private TrialZombie(World world) {
            super(world);
        }

        @Override
        public boolean shouldDropExperience() {
            return true;
        }

        @Override
        protected int getExperienceToDrop(ServerWorld world) {
            return 32;
        }
    }

}
