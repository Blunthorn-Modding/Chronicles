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
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import static net.minecraft.server.command.CommandManager.literal;

/** Real effect regression, available only in the explicitly opted-in disposable fixture. */
public final class EcologyCostsTrial {
    private static final BlockPos HEART=new BlockPos(-4,64,0),CONTACT=new BlockPos(-3,64,0),NEAR=new BlockPos(2,65,2),FAR=new BlockPos(24,64,2);
    private static int hungerApplications,darknessApplications,hungerAmplifier,darknessAmplifier,hungerDuration,darknessDuration;
    private EcologyCostsTrial() { }

    public static void register(){
        String enabled=System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");if(enabled==null||enabled.isBlank())return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var command=literal("quiet_below_costs_trial").requires(s->s.hasPermissionLevel(2));
            for(String verb:List.of("prepare","seal","pulse","remember","unchanged","far","creative","cut","reconnect","foreign","save","restart","review"))
                command.then(literal(verb).executes(c->run(c.getSource(),verb)));
            d.register(command);
        });
    }

    private static int run(ServerCommandSource source,String verb){
        try{
            requireFixture(source);var server=source.getServer();var world=source.getWorld();var player=source.getPlayerOrThrow();var engine=QuietBelowEcology.engineForTest(server);
            switch(verb){
                case "prepare" -> prepare(world,player);
                case "seal" -> {seal(world);player.teleport(world,NEAR.getX()+.5,NEAR.getY(),NEAR.getZ()+.5,java.util.Set.of(),0,0,false);}
                case "pulse" -> pulse(player,engine);
                case "remember" -> remember(player);
                case "unchanged" -> unchanged(player);
                case "far" -> {remember(player);require(player.getBlockPos().equals(FAR),"participant is not at the distant control");}
                case "creative" -> {remember(player);player.interactionManager.changeGameMode(GameMode.CREATIVE);}
                case "cut" -> {remember(player);put(world,CONTACT,Blocks.AIR.getDefaultState());}
                case "reconnect" -> {put(world,CONTACT,Blocks.SCULK.getDefaultState());remember(player);}
                case "foreign" -> foreign(player);
                case "save" -> save(source,engine);
                case "restart" -> restart(source,player,engine);
                case "review" -> review(world,player,engine);
                default -> throw new IllegalArgumentException("Unknown local-cost trial");
            }
            source.sendFeedback(()->Text.literal("PASS costs "+verb+": native local effect conditions verified."),false);return 1;
        }catch(Exception error){source.sendError(Text.literal("FAIL costs "+verb+": "+detail(error)));return 0;}
    }

    private static void prepare(ServerWorld world,ServerPlayerEntity player){
        for(int x=-6;x<=27;x++)for(int z=-5;z<=42;z++)put(world,new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.getDefaultState());
        put(world,HEART,Blocks.REINFORCED_DEEPSLATE.getDefaultState());
        for(int x=-3;x<=-1;x++)put(world,new BlockPos(x,64,0),Blocks.SCULK.getDefaultState());
        // 640 connected cells exceeds the minimum-extent extensive threshold (512).
        for(int x=0;x<16;x++)for(int z=0;z<40;z++)put(world,new BlockPos(x,64,z),Blocks.SCULK.getDefaultState());
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.removeStatusEffect(StatusEffects.HUNGER);player.removeStatusEffect(StatusEffects.DARKNESS);
        player.teleport(world,NEAR.getX()+.5,NEAR.getY(),NEAR.getZ()+.5,java.util.Set.of(),0,0,false);hungerApplications=darknessApplications=0;
        require(player.getBlockPos().equals(NEAR),"near participant was not prepared");
    }
    private static void seal(ServerWorld world){
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            BlockPos p=HEART.add(x,y,z);if(!p.equals(HEART)&&!p.equals(CONTACT))put(world,p,Blocks.GLASS.getDefaultState());
        }
    }
    private static void pulse(ServerPlayerEntity player,EcologyEngine engine){
        require(engine.costRules().equals(new EcologyCosts.Rules(new EcologyCosts.Rule(true,10),new EcologyCosts.Rule(true,10))),"wrong local-cost tuning");
        require(engine.costsAvailable()&&engine.extensive(),"fixture is not resolved Extensive living sculk");
        StatusEffectInstance hunger=player.getStatusEffect(StatusEffects.HUNGER),darkness=player.getStatusEffect(StatusEffects.DARKNESS);
        require(hunger!=null&&hunger.getAmplifier()==0&&hunger.getDuration()<=100,"missing or invalid Hunger I pulse");
        require(darkness!=null&&darkness.getAmplifier()==0&&darkness.getDuration()<=80,"missing or invalid Darkness pulse");
        require(!player.hasStatusEffect(StatusEffects.REGENERATION)&&!player.hasStatusEffect(StatusEffects.SPEED),"local costs altered unrelated effects");
        hungerApplications++;darknessApplications++;
    }
    private static void remember(ServerPlayerEntity p){
        var hunger=p.getStatusEffect(StatusEffects.HUNGER);var darkness=p.getStatusEffect(StatusEffects.DARKNESS);
        hungerApplications=hunger==null?0:1;darknessApplications=darkness==null?0:1;
        hungerAmplifier=hunger==null?-1:hunger.getAmplifier();darknessAmplifier=darkness==null?-1:darkness.getAmplifier();
        hungerDuration=hunger==null?-1:hunger.getDuration();darknessDuration=darkness==null?-1:darkness.getDuration();
    }
    private static void unchanged(ServerPlayerEntity p){
        require((p.hasStatusEffect(StatusEffects.HUNGER)?1:0)==hungerApplications,"Hunger changed while locally ineligible");
        require((p.hasStatusEffect(StatusEffects.DARKNESS)?1:0)==darknessApplications,"Darkness changed while locally ineligible");
        if(hungerApplications!=0)require(p.getStatusEffect(StatusEffects.HUNGER).getAmplifier()==hungerAmplifier,"foreign Hunger was overwritten");
        if(darknessApplications!=0)require(p.getStatusEffect(StatusEffects.DARKNESS).getAmplifier()==darknessAmplifier,"foreign Darkness was overwritten");
        if(hungerApplications!=0)require(p.getStatusEffect(StatusEffects.HUNGER).getDuration()<=hungerDuration,"foreign Hunger duration was extended");
        if(darknessApplications!=0)require(p.getStatusEffect(StatusEffects.DARKNESS).getDuration()<=darknessDuration,"foreign Darkness duration was extended");
        require(!p.hasStatusEffect(StatusEffects.REGENERATION)&&!p.hasStatusEffect(StatusEffects.SPEED),"local costs altered unrelated effects");
    }
    private static void foreign(ServerPlayerEntity p){
        // Hunger is deliberately shorter than our own five-second pulse; Darkness is stronger.
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER,80,0,false,false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,100,2,false,false));remember(p);
    }
    private static void save(ServerCommandSource s,EcologyEngine engine)throws Exception{
        require(engine.costRules().equals(new EcologyCosts.Rules(new EcologyCosts.Rule(true,10),new EcologyCosts.Rule(true,10))),"fast cost tuning was not saved");
        JsonObject data=new JsonObject();data.addProperty("hunger",engine.costRules().hunger().seconds());data.addProperty("darkness",engine.costRules().darkness().seconds());
        Path path=snapshot(s);Files.createDirectories(path.getParent());Files.writeString(path,data.toString());
    }
    private static void restart(ServerCommandSource s,ServerPlayerEntity player,EcologyEngine engine)throws Exception{
        JsonObject data=JsonParser.parseString(Files.readString(snapshot(s))).getAsJsonObject();
        require(engine.costRules().equals(new EcologyCosts.Rules(new EcologyCosts.Rule(true,data.get("hunger").getAsInt()),new EcologyCosts.Rule(true,data.get("darkness").getAsInt()))),"cost tuning did not survive restart");
        player.removeStatusEffect(StatusEffects.HUNGER);player.removeStatusEffect(StatusEffects.DARKNESS);
    }
    private static void review(ServerWorld world,ServerPlayerEntity player,EcologyEngine engine){
        require(engine.costRules().equals(EcologyCosts.DEFAULT),"reset local costs before review");
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.removeStatusEffect(StatusEffects.HUNGER);player.removeStatusEffect(StatusEffects.DARKNESS);
        player.teleport(world,NEAR.getX()+.5,NEAR.getY(),NEAR.getZ()+.5,java.util.Set.of(),0,0,false);
        sign(world,new BlockPos(2,64,40),"LOCAL COSTS","Dense living sculk","Hunger + Darkness","40s default");
        sign(world,new BlockPos(24,64,40),"DISTANT CONTROL","Same participant","No local effect","Watch both");
        sign(world,CONTACT.up(),"CUT MARKER","Break this sculk","Both pulses stop","Replace to restore");
    }
    private static void sign(ServerWorld world,BlockPos pos,String... lines){put(world,pos,Blocks.OAK_SIGN.getDefaultState());var sign=(SignBlockEntity)world.getBlockEntity(pos);require(sign!=null,"review sign missing");var text=sign.getFrontText();for(int i=0;i<4;i++)text=text.withMessage(i,Text.literal(lines[i]));sign.setText(text,true);}
    private static void put(ServerWorld w,BlockPos p,BlockState state){require(w.getChunkManager().isChunkLoaded(p.getX()>>4,p.getZ()>>4),"fixture chunk not loaded");w.setBlockState(p,state,Block.NOTIFY_LISTENERS);}
    private static Path snapshot(ServerCommandSource s){return s.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/costs-trial.json");}
    private static void requireFixture(ServerCommandSource s)throws Exception{var server=s.getServer();Path root=server.getRunDirectory().toRealPath();require(server.isDedicated()&&!server.isOnlineMode()&&"127.0.0.1".equals(server.getServerIp())&&server.getServerPort()==25575,"wrong costs fixture");require(root.getParent()!=null&&"test-runs".equals(root.getParent().getFileName().toString())&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"wrong costs fixture");}
    private static String detail(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private static void require(boolean test,String text){if(!test)throw new IllegalStateException(text);}
}
