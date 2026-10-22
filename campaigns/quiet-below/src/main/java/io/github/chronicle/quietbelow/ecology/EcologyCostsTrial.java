package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import static net.minecraft.server.command.CommandManager.literal;

/** Disposable native fixture for continuous contact-cost ownership and persistence boundaries. */
public final class EcologyCostsTrial {
    private static final BlockPos HEART=new BlockPos(-4,64,0),CONTACT=new BlockPos(-3,64,0),NEAR=new BlockPos(2,65,2);
    private static StatusEffectInstance rememberedHunger,rememberedDarkness;
    private EcologyCostsTrial() { }

    public static void register(){
        String enabled=System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");if(enabled==null||enabled.isBlank())return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var command=literal("quiet_below_costs_trial").requires(s->s.hasPermissionLevel(2));
            for(String verb:List.of("prepare","seal","active","remember","sustained","clear","pillar","down","cut","reconnect","creative","replace","foreign","foreign_off","serialize_owned","serialize_foreign","copy","precision","air","airborne","landed","lane","lane_end","save","restart","review"))
                command.then(literal(verb).executes(c->run(c.getSource(),verb)));
            d.register(command);
        });
    }
    private static int run(ServerCommandSource source,String verb){
        try{
            requireFixture(source);var server=source.getServer();var world=source.getWorld();var player=source.getPlayerOrThrow();var engine=QuietBelowEcology.engineForTest(server);
            switch(verb){
                case "prepare" -> prepare(world,player);
                case "seal" -> {seal(world);near(world,player);}
                case "active" -> active(player,engine);
                case "remember" -> remember(player);
                case "sustained" -> sustained(player);
                case "clear" -> clear(player);
                case "pillar" -> {put(world,NEAR.up(),Blocks.GLASS.getDefaultState());player.teleport(world,NEAR.getX()+.5,NEAR.getY()+2,NEAR.getZ()+.5,java.util.Set.of(),0,0,false);player.setOnGround(true);require(EcologyCostsWorld.contact(player,engine)==null&&!EcologyCostsWorld.landingTransition(player,QuietBelowEcology.pos(NEAR),engine),"new glass pillar over living sculk retained exposure");}
                case "down" -> {put(world,NEAR.up(),Blocks.AIR.getDefaultState());near(world,player);clear(player);}
                case "cut" -> put(world,CONTACT,Blocks.AIR.getDefaultState());
                case "reconnect" -> {put(world,CONTACT,Blocks.SCULK.getDefaultState());clear(player);}
                case "creative" -> player.interactionManager.changeGameMode(GameMode.CREATIVE);
                case "replace" -> replace(player);
                case "foreign" -> foreign(player);
                case "foreign_off" -> foreignOff(player);
                case "serialize_owned" -> serializeOwned(player);
                case "serialize_foreign" -> serializeForeign(player);
                case "copy" -> copy(player);
                case "precision" -> precision(world,player,engine);
                case "air" -> {remember(player);player.teleport(world,player.getX(),player.getY()+4,player.getZ(),java.util.Set.of(),0,0,false);player.setOnGround(false);}
                case "airborne" -> {require(!player.isOnGround(),"airborne trial already landed");sustained(player);}
                case "landed" -> {require(player.isOnGround(),"airborne trial did not land");sustained(player);}
                case "lane" -> {sustained(player);player.teleport(world,2.5,66,2.5,java.util.Set.of(),-90,0,false);}
                case "lane_end" -> {require(player.getX()>8&&player.getX()<15&&player.isOnGround(),"movement did not cross the floor-vein lane");require(EcologyCostsWorld.contact(player,engine)!=null,"lane endpoint is not living footing");sustained(player);}
                case "save" -> save(source,engine);
                case "restart" -> restart(source,player,engine);
                case "review" -> review(world,player,engine);
                default -> throw new IllegalArgumentException("Unknown local-cost trial");
            }
            source.sendFeedback(()->Text.literal("PASS costs "+verb+": continuous contact conditions verified."),false);return 1;
        }catch(Exception error){source.sendError(Text.literal("FAIL costs "+verb+": "+detail(error)));return 0;}
    }
    private static void prepare(ServerWorld world,ServerPlayerEntity player){
        for(int x=-6;x<=27;x++)for(int z=-5;z<=42;z++)put(world,new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.getDefaultState());
        put(world,HEART,Blocks.REINFORCED_DEEPSLATE.getDefaultState());
        for(int x=-3;x<=-1;x++)put(world,new BlockPos(x,64,0),Blocks.SCULK.getDefaultState());
        for(int x=0;x<16;x++)for(int z=0;z<40;z++)put(world,new BlockPos(x,64,z),Blocks.SCULK.getDefaultState());
        put(world,NEAR,Blocks.SCULK.getDefaultState());
        // Raised, indexed lane: continuous real floor veins over alternating sculk/stone.
        // Walk/jump across it using actual client input, not server teleports between samples.
        for(int x=2;x<=14;x++){
            put(world,new BlockPos(x,65,2),x%2==0&&x>2?Blocks.STONE.getDefaultState():Blocks.SCULK.getDefaultState());
            put(world,new BlockPos(x,66,2),Blocks.SCULK_VEIN.getDefaultState().with(MultifaceGrowthBlock.getProperty(Direction.DOWN),true));
        }
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.removeStatusEffect(StatusEffects.HUNGER);player.removeStatusEffect(StatusEffects.DARKNESS);
        near(world,player);rememberedHunger=rememberedDarkness=null;
    }
    private static void near(ServerWorld world,ServerPlayerEntity player){player.teleport(world,NEAR.getX()+.5,NEAR.getY()+1,NEAR.getZ()+.5,java.util.Set.of(),0,0,false);}
    private static void seal(ServerWorld world){for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){BlockPos p=HEART.add(x,y,z);if(!p.equals(HEART)&&!p.equals(CONTACT))put(world,p,Blocks.GLASS.getDefaultState());}}
    private static void active(ServerPlayerEntity player,EcologyEngine engine){
        require(engine.costRules().equals(new EcologyCosts.Rules(new EcologyCosts.Rule(true,10),new EcologyCosts.Rule(true,10))),"wrong local-cost tuning");require(engine.costsAvailable()&&engine.extensive(),"fixture is not resolved Extensive living sculk");
        owned(player.getStatusEffect(StatusEffects.HUNGER),"Hunger I");owned(player.getStatusEffect(StatusEffects.DARKNESS),"Darkness");require(!player.hasStatusEffect(StatusEffects.REGENERATION)&&!player.hasStatusEffect(StatusEffects.SPEED),"local costs altered unrelated effects");
    }
    private static void owned(StatusEffectInstance effect,String label){require(effect!=null&&effect.getAmplifier()==0&&effect.getDuration()==StatusEffectInstance.INFINITE&&EcologyCostEffects.owned(effect),"missing marked infinite "+label);}
    private static void remember(ServerPlayerEntity player){rememberedHunger=player.getStatusEffect(StatusEffects.HUNGER);rememberedDarkness=player.getStatusEffect(StatusEffects.DARKNESS);}
    private static void sustained(ServerPlayerEntity player){owned(rememberedHunger,"remembered Hunger I");owned(rememberedDarkness,"remembered Darkness");require(player.getStatusEffect(StatusEffects.HUNGER)==rememberedHunger,"Hunger instance was replaced during continuous contact");require(player.getStatusEffect(StatusEffects.DARKNESS)==rememberedDarkness,"Darkness instance was replaced during continuous contact");}
    private static void clear(ServerPlayerEntity player){require(!player.hasStatusEffect(StatusEffects.HUNGER)&&!player.hasStatusEffect(StatusEffects.DARKNESS),"contact effect survived lost eligibility");}
    private static void foreign(ServerPlayerEntity player){
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER,80,0,false,false));player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,100,2,false,false));
        var hunger=player.getStatusEffect(StatusEffects.HUNGER);var darkness=player.getStatusEffect(StatusEffects.DARKNESS);require(hunger!=null&&!EcologyCostEffects.owned(hunger)&&hunger.getAmplifier()==0&&hunger.getDuration()<=80,"foreign Hunger did not replace campaign instance");require(darkness!=null&&!EcologyCostEffects.owned(darkness)&&darkness.getAmplifier()==2&&darkness.getDuration()<=100,"foreign Darkness did not replace campaign instance");
    }
    private static void replace(ServerPlayerEntity player){
        owned(player.getStatusEffect(StatusEffects.HUNGER),"Hunger I");var replacement=new StatusEffectInstance(StatusEffects.HUNGER,80,0,false,false);player.setStatusEffect(replacement,null);
        require(player.getStatusEffect(StatusEffects.HUNGER)==replacement&&!EcologyCostEffects.owned(replacement),"direct foreign replacement did not displace the marked Hunger instance");
    }
    private static void foreignOff(ServerPlayerEntity player){var hunger=player.getStatusEffect(StatusEffects.HUNGER);var darkness=player.getStatusEffect(StatusEffects.DARKNESS);require(hunger!=null&&!EcologyCostEffects.owned(hunger)&&hunger.getAmplifier()==0,"foreign Hunger did not survive leaving sculk");require(darkness!=null&&!EcologyCostEffects.owned(darkness)&&darkness.getAmplifier()==2,"foreign Darkness did not survive leaving sculk");}
    private static void serializeOwned(ServerPlayerEntity player){
        var hunger=player.getStatusEffect(StatusEffects.HUNGER);var darkness=player.getStatusEffect(StatusEffects.DARKNESS);
        owned(hunger,"Hunger I");owned(darkness,"Darkness");
        var speed=new StatusEffectInstance(StatusEffects.SPEED,80,0,false,false);player.addStatusEffect(speed);
        var view=NbtWriteView.create(ErrorReporter.EMPTY,player.getRegistryManager());player.writeData(view);
        Object saved=view.getNbt().get("active_effects");String text=String.valueOf(saved);
        require(text.contains("minecraft:speed")&&!text.contains("minecraft:hunger")&&!text.contains("minecraft:darkness"),"transient/foreign save boundary failed: "+saved);
        require(player.getStatusEffect(StatusEffects.HUNGER)==hunger&&player.getStatusEffect(StatusEffects.DARKNESS)==darkness&&player.getStatusEffect(StatusEffects.SPEED)==speed,"writing player data mutated live effects");
        player.removeStatusEffect(StatusEffects.SPEED);
    }

    private static void serializeForeign(ServerPlayerEntity player){var view=NbtWriteView.create(ErrorReporter.EMPTY,player.getRegistryManager());player.writeData(view);Object saved=view.getNbt().get("active_effects");require(saved!=null&&saved.toString().contains("minecraft:hunger")&&saved.toString().contains("minecraft:darkness"),"foreign effects were removed from player save: "+saved);foreignOff(player);}
    private static void copy(ServerPlayerEntity player){var hunger=player.getStatusEffect(StatusEffects.HUNGER);owned(hunger,"Hunger I");var clone=new StatusEffectInstance(hunger);require(EcologyCostEffects.owned(clone),"owned marker was lost copying a status effect");((EcologyCostEffect)(Object)clone).quietBelow$yieldContactCost();require(!EcologyCostEffects.owned(clone),"withdrawal did not clear copied contact marker");
        var stronger=new StatusEffectInstance(StatusEffects.HUNGER,StatusEffectInstance.INFINITE,1,false,false);
        ((EcologyCostEffect)(Object)stronger).quietBelow$markContactCost();
        stronger.upgrade(new StatusEffectInstance(StatusEffects.HUNGER,80,0,false,false));
        require(!EcologyCostEffects.owned(stronger),"direct foreign upgrade kept campaign ownership");
        stronger.update(player.getEntityWorld(),player,()->{});
        require(stronger.getAmplifier()==0&&stronger.getDuration()>0&&stronger.getDuration()<=80,"direct upgrade lost the weaker foreign hidden effect");
    }
    private static void precision(ServerWorld world,ServerPlayerEntity player,EcologyEngine engine){
        double x=player.getX(),y=player.getY(),z=player.getZ();
        require(EcologyCostsWorld.contact(player,engine)!=null,"precision fixture needs actual living footing");
        BlockPos edge=NEAR.up().east();BlockState edgeBefore=world.getBlockState(edge);
        try{
            player.setOnGround(true);player.setPosition(x,y+.0216,z);
            require(EcologyCostsWorld.contact(player,engine)!=null,"native grounded landing residue reset living contact");
            player.setPosition(x,y,z);player.setOnGround(true);player.setPosition(x,y+.6537296175886,z);
            require(EcologyCostsWorld.landingTransition(player,QuietBelowEcology.pos(NEAR),engine),"early grounded packet reset the saved living footing");
            player.setPosition(x,y,z);player.setOnGround(true);player.setPosition(x,y+1.0/16,z);
            require(EcologyCostsWorld.contact(player,engine)==null,"contact tolerance swallowed a carpet-height separation");
            player.setPosition(x,y,z);player.setOnGround(true);fixtureState(world,edge,Blocks.GLASS.getDefaultState());
            player.setPosition(x+.3,y+1,z);
            require(!EcologyCostsWorld.landingTransition(player,QuietBelowEcology.pos(NEAR),engine),"edge-supported glass pillar retained exposure");
        }finally{fixtureState(world,edge,edgeBefore);player.setPosition(x,y,z);player.setOnGround(true);}
        // Isolate the native footing adapter: restore both exact source states before any ecology tick.
        BlockState original=world.getBlockState(NEAR),below=world.getBlockState(NEAR.down());
        try{
            fixtureState(world,NEAR.down(),Blocks.STONE.getDefaultState());
            fixtureState(world,NEAR,Blocks.SCULK_VEIN.getDefaultState().with(MultifaceGrowthBlock.getProperty(Direction.DOWN),true));
            require(engine.costsAvailable()&&engine.living(QuietBelowEcology.pos(NEAR)),"precision fixture invalidated the living network");
            player.setPosition(x,NEAR.getY(),z);player.setOnGround(true);player.setPosition(x,NEAR.getY()+.0216,z);
            require(EcologyCostsWorld.contact(player,engine)!=null,"floor vein rejected native landing residue");
            player.setPosition(x+1,NEAR.getY()+.65,z+1);player.setOnGround(true);
            require(EcologyCostsWorld.landingTransition(player,QuietBelowEcology.pos(NEAR),engine),"early grounded lateral vein transition discarded the previous footing");
            player.setPosition(x,NEAR.getY()+.0216,z);player.setOnGround(true);
            fixtureState(world,NEAR.down(),Blocks.AIR.getDefaultState());
            require(EcologyCostsWorld.contact(player,engine)==null,"unsupported vein established a physical landing");
            fixtureState(world,NEAR.down(),Blocks.STONE.getDefaultState());
            fixtureState(world,NEAR,Blocks.SCULK_VEIN.getDefaultState().with(MultifaceGrowthBlock.getProperty(Direction.NORTH),true));
            require(EcologyCostsWorld.contact(player,engine)==null,"side vein incorrectly counted as floor contact");
        }finally{
            fixtureState(world,NEAR.down(),below);fixtureState(world,NEAR,original);
            player.setPosition(x,y,z);player.setOnGround(true);
        }
        BlockPos adjacent=NEAR.east(),coat=adjacent.up();var oldSupport=world.getBlockState(adjacent);var oldCoat=world.getBlockState(coat);
        try{
            fixtureState(world,adjacent,Blocks.STONE.getDefaultState());fixtureState(world,coat,Blocks.AIR.getDefaultState());
            player.setPosition(NEAR.getX()+1.05,y,z);player.setOnGround(true);
            require(EcologyCostsWorld.contact(player,engine)!=null,"mixed clean/sculk sole seam discarded infested support");
            player.setPosition(NEAR.getX()+1.4,y,z);player.setOnGround(true);
            require(EcologyCostsWorld.contact(player,engine)==null,"completely clean sole retained nearby sculk");
        }finally{
            fixtureState(world,adjacent,oldSupport);fixtureState(world,coat,oldCoat);player.setPosition(x,y,z);player.setOnGround(true);
        }
    }
    private static void fixtureState(ServerWorld world,BlockPos pos,BlockState state){
        // These synchronous geometry probes restore exact states before the next ecology tick.
        // Suppress neighbor shape cascades as well as ownership notices: otherwise replacing a
        // lane support drops its real vein and invalidates the network instead of testing contact.
        try(var ignored=EcologyHooks.ownWrite()){world.setBlockState(pos,state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);}
    }
    private static void save(ServerCommandSource s,EcologyEngine engine)throws Exception{require(engine.costRules().equals(new EcologyCosts.Rules(new EcologyCosts.Rule(true,10),new EcologyCosts.Rule(true,10))),"fast cost tuning was not saved");require(engine.spreadRules().mode()==EcologyEngine.GrowthMode.SCULK_COUNT&&engine.spreadRules().amplificationPercent()==25,"non-default spread tuning was not saved");require(engine.vitalitySummary().ready()>=512&&engine.effectiveSpeedPercent()>engine.speedPercent(),"living sculk count did not amplify spread");JsonObject data=new JsonObject();data.addProperty("hunger",engine.costRules().hunger().seconds());data.addProperty("darkness",engine.costRules().darkness().seconds());data.addProperty("mode",engine.spreadRules().mode().name());data.addProperty("amplification",engine.spreadRules().amplificationPercent());Path path=snapshot(s);Files.createDirectories(path.getParent());Files.writeString(path,data.toString());}
    private static void restart(ServerCommandSource s,ServerPlayerEntity player,EcologyEngine engine)throws Exception{JsonObject data=JsonParser.parseString(Files.readString(snapshot(s))).getAsJsonObject();require(engine.costRules().equals(new EcologyCosts.Rules(new EcologyCosts.Rule(true,data.get("hunger").getAsInt()),new EcologyCosts.Rule(true,data.get("darkness").getAsInt()))),"cost tuning did not survive restart");require(engine.spreadRules().equals(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.valueOf(data.get("mode").getAsString()),data.get("amplification").getAsInt())),"spread mode/amplification did not survive restart");clear(player);}
    private static void review(ServerWorld world,ServerPlayerEntity player,EcologyEngine engine){require(engine.costRules().equals(EcologyCosts.DEFAULT),"reset local costs before review");player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.removeStatusEffect(StatusEffects.HUNGER);player.removeStatusEffect(StatusEffects.DARKNESS);near(world,player);sign(world,new BlockPos(2,64,40),"LOCAL COSTS","Living footing only","Hunger + Darkness","40s initial delay");sign(world,new BlockPos(24,64,40),"SAFE CONTROL","Nearby is not enough","Use a glass pillar","Effects stay off");sign(world,CONTACT.up(),"CUT MARKER","Break this sculk","Contact effects clear","Replace, then wait");}
    private static void sign(ServerWorld world,BlockPos pos,String... lines){put(world,pos,Blocks.OAK_SIGN.getDefaultState());var sign=(SignBlockEntity)world.getBlockEntity(pos);require(sign!=null,"review sign missing");var text=sign.getFrontText();for(int i=0;i<4;i++)text=text.withMessage(i,Text.literal(lines[i]));sign.setText(text,true);}
    private static void put(ServerWorld w,BlockPos p,BlockState state){require(w.getChunkManager().isChunkLoaded(p.getX()>>4,p.getZ()>>4),"fixture chunk not loaded");w.setBlockState(p,state,Block.NOTIFY_LISTENERS);}
    private static Path snapshot(ServerCommandSource s){return s.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/costs-trial.json");}
    private static void requireFixture(ServerCommandSource s)throws Exception{var server=s.getServer();Path root=server.getRunDirectory().toRealPath();require(server.isDedicated()&&!server.isOnlineMode()&&"127.0.0.1".equals(server.getServerIp())&&server.getServerPort()==25575,"wrong costs fixture");require(root.getParent()!=null&&"test-runs".equals(root.getParent().getFileName().toString())&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"wrong costs fixture");}
    private static String detail(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private static void require(boolean test,String text){if(!test)throw new IllegalStateException(text);}
}
