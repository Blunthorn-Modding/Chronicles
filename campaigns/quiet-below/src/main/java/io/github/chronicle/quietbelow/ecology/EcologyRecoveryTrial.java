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
import net.minecraft.world.GameRules;
import static net.minecraft.server.command.CommandManager.literal;

/** Real player-health regression, available only on the explicitly opted-in disposable fixture. */
public final class EcologyRecoveryTrial {
    private static final BlockPos HEART=new BlockPos(-4,64,0), CONTACT=new BlockPos(-3,64,0), NEAR=new BlockPos(2,65,2), FAR=new BlockPos(14,64,2);
    private static float rememberedHealth;
    private EcologyRecoveryTrial() { }

    public static void register(){
        String enabled=System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");if(enabled==null||enabled.isBlank())return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var command=literal("quiet_below_recovery_trial").requires(s->s.hasPermissionLevel(2));
            for(String verb:List.of("prepare","seal","remember","healed","unchanged","far","creative","cut","reconnect","save","restart","review"))
                command.then(literal(verb).executes(c->run(c.getSource(),verb)));
            d.register(command);
        });
    }

    private static int run(ServerCommandSource source,String verb){
        try{
            requireFixture(source);var server=source.getServer();var world=source.getWorld();var player=source.getPlayerOrThrow();
            var engine=QuietBelowEcology.engineForTest(server);
            switch(verb){
                case "prepare" -> prepare(world,player);
                case "seal" -> {
                    // The unique contact cannot grow a second path around the Heart during the trial.
                    for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                        var p=HEART.add(x,y,z);if(!p.equals(HEART)&&!p.equals(CONTACT))put(world,p,Blocks.GLASS.getDefaultState());
                    }
                    player.teleport(world,NEAR.getX()+.5,NEAR.getY(),NEAR.getZ()+.5,java.util.Set.of(),0,0,false);
                }
                case "remember" -> remember(player);
                case "healed" -> healed(player,engine);
                case "unchanged" -> unchanged(player);
                case "far" -> { remember(player); require(player.getBlockPos().equals(FAR),"participant is not at the distant control"); }
                case "creative" -> { remember(player); player.interactionManager.changeGameMode(GameMode.CREATIVE); }
                case "cut" -> { remember(player); put(world,CONTACT,Blocks.AIR.getDefaultState()); }
                case "reconnect" -> { put(world,CONTACT,Blocks.SCULK.getDefaultState()); remember(player); }
                case "save" -> save(source,player,engine);
                case "restart" -> restart(source,player,engine);
                case "review" -> review(world,player,engine);
                default -> throw new IllegalArgumentException("Unknown recovery trial");
            }
            source.sendFeedback(()->Text.literal("PASS recovery "+verb+": native health and eligibility conditions verified."),false);return 1;
        }catch(Exception error){source.sendError(Text.literal("FAIL recovery "+verb+": "+detail(error)));return 0;}
    }

    private static void prepare(ServerWorld world,ServerPlayerEntity player){
        world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,world.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,world.getServer());
        for(int x=-6;x<=17;x++)for(int z=-5;z<=8;z++)put(world,new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.getDefaultState());
        put(world,HEART,Blocks.REINFORCED_DEEPSLATE.getDefaultState());
        for(int x=-3;x<=-1;x++)put(world,new BlockPos(x,64,0),Blocks.SCULK.getDefaultState());
        for(int x=0;x<=7;x++)for(int z=0;z<=7;z++)put(world,new BlockPos(x,64,z),Blocks.SCULK.getDefaultState());
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        player.removeStatusEffect(StatusEffects.REGENERATION);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,24_000,0,false,false));
        player.getHungerManager().setFoodLevel(20);player.setHealth(12F);player.teleport(world,NEAR.getX()+.5,NEAR.getY(),NEAR.getZ()+.5,java.util.Set.of(),0,0,false);
        require(!world.getGameRules().get(GameRules.NATURAL_REGENERATION).get(),"natural regeneration was not isolated");
        require(!player.hasStatusEffect(StatusEffects.REGENERATION),"fixture granted Regeneration");
        require(player.hasStatusEffect(StatusEffects.SPEED),"fixture could not establish external Speed control");
        require(player.getHealth()==12F&&player.getHungerManager().getFoodLevel()==20,"fixture health or hunger not prepared");
    }

    private static void healed(ServerPlayerEntity player,EcologyEngine engine){
        require(engine.recoveryRules().equals(new EcologyRecovery.Rules(true,2)),"wrong local-healing tuning");
        require(engine.recoveryAvailable(),"living network is not Established or Extensive");
        require(player.interactionManager.getGameMode()==GameMode.SURVIVAL||player.interactionManager.getGameMode()==GameMode.ADVENTURE,"healing participant is not survival or adventure");
        require(!player.hasStatusEffect(StatusEffects.REGENERATION),"local healing granted Regeneration");
        require(player.hasStatusEffect(StatusEffects.SPEED),"local healing changed external Speed");
        require(player.getHealth()==rememberedHealth+1F,"expected one half-heart after one full eligible interval; before="+rememberedHealth+", after="+player.getHealth());
        rememberedHealth=player.getHealth();
    }

    private static void unchanged(ServerPlayerEntity player){
        require(player.getHealth()==rememberedHealth,"health changed while recovery was ineligible; before="+rememberedHealth+", after="+player.getHealth());
        require(!player.hasStatusEffect(StatusEffects.REGENERATION),"fixture granted Regeneration");
        require(player.hasStatusEffect(StatusEffects.SPEED),"fixture changed external Speed");
    }

    private static void save(ServerCommandSource source,ServerPlayerEntity player,EcologyEngine engine)throws Exception{
        require(engine.recoveryRules().equals(new EcologyRecovery.Rules(true,2)),"fast healing tuning was not saved");
        JsonObject data=new JsonObject();data.addProperty("health",player.getHealth());data.addProperty("seconds",engine.recoveryRules().seconds());
        Path path=snapshot(source);Files.createDirectories(path.getParent());Files.writeString(path,data.toString());
    }
    private static void restart(ServerCommandSource source,ServerPlayerEntity player,EcologyEngine engine)throws Exception{
        JsonObject data=JsonParser.parseString(Files.readString(snapshot(source))).getAsJsonObject();
        require(engine.recoveryRules().equals(new EcologyRecovery.Rules(true,data.get("seconds").getAsInt())),"healing tuning did not survive restart");
        require(player.getHealth()==data.get("health").getAsFloat(),"offline time changed player health");
        require(!player.hasStatusEffect(StatusEffects.REGENERATION),"restart granted Regeneration");
        require(player.hasStatusEffect(StatusEffects.SPEED),"restart lost external Speed");remember(player);
    }
    private static void review(ServerWorld world,ServerPlayerEntity player,EcologyEngine engine)throws Exception{
        require(engine.recoveryRules().equals(EcologyRecovery.DEFAULT),"reset local healing before review");
        world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(true,world.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true,world.getServer());
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.removeStatusEffect(StatusEffects.REGENERATION);
        player.setHealth(12F);player.getHungerManager().setFoodLevel(17);player.teleport(world,NEAR.getX()+.5,NEAR.getY(),NEAR.getZ()+.5,java.util.Set.of(),0,0,false);
        sign(world,new BlockPos(2,64,7),"LOCAL HEALING","Near living sculk","10s half-heart","Food below 18");
        sign(world,new BlockPos(14,64,7),"DISTANT CONTROL","Same player health","No local effect","Watch both");
        sign(world,CONTACT.up(),"CUT MARKER","Break this sculk","Healing stops","Replace to restore");
    }
    private static void remember(ServerPlayerEntity player){rememberedHealth=player.getHealth();}
    private static void sign(ServerWorld world,BlockPos pos,String... lines){
        put(world,pos,Blocks.OAK_SIGN.getDefaultState());var sign=(SignBlockEntity)world.getBlockEntity(pos);require(sign!=null,"review sign missing");
        var text=sign.getFrontText();for(int line=0;line<4;line++)text=text.withMessage(line,Text.literal(lines[line]));sign.setText(text,true);
    }
    private static void put(ServerWorld world,BlockPos pos,BlockState state){require(world.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4),"fixture chunk not loaded");world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);}
    private static Path snapshot(ServerCommandSource source){return source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/recovery-trial.json");}
    private static void requireFixture(ServerCommandSource source)throws Exception{
        var server=source.getServer();Path root=server.getRunDirectory().toRealPath();
        require(server.isDedicated()&&!server.isOnlineMode()&&"127.0.0.1".equals(server.getServerIp())&&server.getServerPort()==25575,"wrong recovery fixture");
        require(root.getParent()!=null&&"test-runs".equals(root.getParent().getFileName().toString())&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"wrong recovery fixture");
    }
    private static String detail(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
