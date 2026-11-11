package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.WorldSavePath;
import static net.minecraft.server.command.CommandManager.literal;

/** Native crop regression, unavailable outside an explicitly opted-in disposable server. */
public final class EcologyCropTrial {
    private static final List<BlockPos> NEAR=List.of(new BlockPos(1,65,0),new BlockPos(1,65,1),new BlockPos(1,65,2),new BlockPos(1,65,3));
    private static final List<Block> TYPES=List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,Blocks.BEETROOTS);
    private static final BlockPos DARK=new BlockPos(1,65,-2),MATURE=new BlockPos(2,65,1),OTHER=new BlockPos(2,65,2),CUT=new BlockPos(-3,64,0);
    private static final Map<BlockPos,Integer> BEFORE=new LinkedHashMap<>();
    private EcologyCropTrial() { }
    public static void register(){
        String enabled=System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");if(enabled==null||enabled.isBlank())return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var command=literal("quiet_below_crop_trial").requires(s->s.hasPermissionLevel(2));
            for(String verb:List.of("prepare","grown","remember","cut","unchanged","recovered","save","restart","review"))
                command.then(literal(verb).executes(c->run(c.getSource(),verb)));
            d.register(command);
        });
    }
    private static int run(ServerCommandSource s,String verb){
        try{
            Path root=s.getServer().getRunDirectory().toRealPath();
            require(s.getServer().isDedicated()&&!s.getServer().isOnlineMode()&&s.getServer().getServerPort()==25575
                    &&s.getServer().getServerIp().equals("127.0.0.1")&&root.getParent().getFileName().toString().equals("test-runs")
                    &&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"wrong crop fixture");
            var world=s.getWorld();var engine=QuietBelowEcology.engineForTest(s.getServer());
            switch(verb){
                case "prepare" -> {
                    put(world,new BlockPos(-4,64,0),Blocks.REINFORCED_DEEPSLATE.getDefaultState());
                    for(int x=-3;x<=-1;x++)put(world,new BlockPos(x,64,0),Blocks.SCULK.getDefaultState());
                    adapterBounds(world);field(world);
                }
                case "grown" -> {
                    require(engine.cropRules().equals(new EcologyCrops.Rules(true,2)),"wrong crop tuning");
                    for(BlockPos p:NEAR)require(age(world,p)>0,"living sculk did not boost "+p.toShortString());
                    controls(world);
                }
                case "remember" -> remember(world);
                case "cut" -> {remember(world);put(world,CUT,Blocks.AIR.getDefaultState());}
                case "unchanged" -> {for(var e:BEFORE.entrySet())require(age(world,e.getKey())==e.getValue(),"crop grew while ineligible: "+e.getKey());controls(world);}
                case "recovered" -> {require(age(world,NEAR.getFirst())>BEFORE.get(NEAR.getFirst()),"reconnected crop did not resume");controls(world);}
                case "save" -> {
                    remember(world);JsonObject data=new JsonObject();for(var e:BEFORE.entrySet())data.addProperty(key(e.getKey()),e.getValue());
                    Files.writeString(snapshot(s),data.toString());
                }
                case "restart" -> {
                    require(engine.cropRules().equals(new EcologyCrops.Rules(true,2)),"crop tuning did not survive restart");
                    BEFORE.clear();JsonObject data=JsonParser.parseString(Files.readString(snapshot(s))).getAsJsonObject();
                    for(BlockPos p:NEAR)BEFORE.put(p,data.get(key(p)).getAsInt());
                    for(var e:BEFORE.entrySet())require(age(world,e.getKey())==e.getValue(),"native crop state did not survive restart");controls(world);
                }
                case "review" -> {
                    require(engine.cropRules().equals(EcologyCrops.DEFAULT),"reset crop tuning before review");
                    for(int x=-6;x<=17;x++)for(int z=-5;z<=7;z++)put(world,new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.getDefaultState());
                    field(world);
                    // The human bench contains only the two comparisons, not invalid/dark negative fixtures.
                    for(int x=0;x<=2;x++)for(int y=64;y<=66;y++)for(int z=-3;z<=-1;z++)put(world,new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
                    for(int x=0;x<=2;x++)for(int z=-3;z<=-1;z++)put(world,new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.getDefaultState());
                    for(BlockPos p:List.of(MATURE,OTHER)){put(world,p,Blocks.AIR.getDefaultState());put(world,p.down(),Blocks.AIR.getDefaultState());}
                    sign(world,new BlockPos(1,64,5),"NEAR SCULK","4 crop types","Bonus + vanilla","4-block reach");
                    sign(world,new BlockPos(13,64,5),"CONTROL","Same crop types","Vanilla growth","Watch both");
                    sign(world,new BlockPos(7,64,4),"CROP REVIEW","Join + resume","O > Game setup","10s default");
                    world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(-6,60,-5,18,80,8),item->true)
                            .forEach(net.minecraft.entity.Entity::discard);
                }
                default -> throw new IllegalArgumentException("Unknown crop trial");
            }
            s.sendFeedback(()->Text.literal("PASS crop "+verb+": native conditions, ages and campaign gating verified."),false);return 1;
        }catch(Exception e){s.sendError(Text.literal("FAIL crop "+verb+": "+e.getMessage()));return 0;}
    }
    private static void adapterBounds(ServerWorld w){
        var adapter=new EcologyCropWorld(w);var crop=new BlockPos(5,65,5);var bottom=new BlockPos(5,w.getBottomY(),5);
        var originalCrop=w.getBlockState(crop);var originalSoil=w.getBlockState(crop.down());var originalBottom=w.getBlockState(bottom);
        var border=w.getWorldBorder();double centerX=border.getCenterX(),centerZ=border.getCenterZ(),size=border.getSize();
        try{
            put(w,crop.down(),Blocks.FARMLAND.getDefaultState());put(w,crop,Blocks.WHEAT.getDefaultState());
            border.setCenter(8,8);border.setSize(14); // origin0 outside, crop5 inside the same section
            var section=new EcologyCrops.Section(0,4,0);
            require(!border.contains(new BlockPos(0,64,0))&&border.contains(crop),"border-straddling fixture invalid");
            require(adapter.mayContainCrops(section),"cached palette missed border-straddling crops");
            require(adapter.crop(new EcologyTerrain.Pos(5,65,5))!=null,"in-border native crop unreadable");
            put(w,bottom,Blocks.WHEAT.getDefaultState());
            var minimum=adapter.crop(new EcologyTerrain.Pos(5,w.getBottomY(),5));
            require(minimum!=null&&!minimum.eligible(),"minimum-Y crop must have no valid soil");
        }finally{
            border.setCenter(centerX,centerZ);border.setSize(size);
            put(w,crop,originalCrop);put(w,crop.down(),originalSoil);put(w,bottom,originalBottom);
        }
    }
    private static void field(ServerWorld w){
        for(int i=0;i<NEAR.size();i++){
            for(BlockPos p:List.of(NEAR.get(i),NEAR.get(i).add(12,0,0))){
                put(w,p.down(),Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));put(w,p,TYPES.get(i).getDefaultState());
            }
        }
        put(w,MATURE.down(),Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));put(w,MATURE,Blocks.WHEAT.getDefaultState().with(CropBlock.AGE,7));
        put(w,OTHER.down(),Blocks.SOUL_SAND.getDefaultState());put(w,OTHER,Blocks.NETHER_WART.getDefaultState());
        for(int dx=-1;dx<=1;dx++)for(int dy=-2;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)
            if(dx!=0||dy!=0||dz!=0)put(w,DARK.add(dx,dy,dz),Blocks.BEDROCK.getDefaultState());
        put(w,DARK.down(),Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));put(w,DARK,Blocks.WHEAT.getDefaultState());
    }
    private static void controls(ServerWorld w){
        for(BlockPos p:NEAR)require(age(w,p.add(12,0,0))==0,"control plot received a bonus");
        require(age(w,DARK)==0&&w.getBaseLightLevel(DARK,0)<9,"unlit crop age="+age(w,DARK)+", light="+w.getBaseLightLevel(DARK,0));
        require(age(w,MATURE)==7,"mature crop changed");require(w.getBlockState(OTHER).equals(Blocks.NETHER_WART.getDefaultState()),"unsupported crop changed");
    }
    private static void remember(ServerWorld w){BEFORE.clear();for(BlockPos p:NEAR)BEFORE.put(p,age(w,p));}
    private static void sign(ServerWorld w,BlockPos p,String... lines){
        put(w,p,Blocks.OAK_SIGN.getDefaultState());
        var sign=(net.minecraft.block.entity.SignBlockEntity)w.getBlockEntity(p);require(sign!=null,"review sign missing");
        var text=sign.getFrontText();for(int n=0;n<4;n++)text=text.withMessage(n,Text.literal(lines[n]));sign.setText(text,true);
    }
    private static int age(ServerWorld w,BlockPos p){var state=w.getBlockState(p);require(state.getBlock() instanceof CropBlock,"crop missing at "+p);return ((CropBlock)state.getBlock()).getAge(state);}
    private static void put(ServerWorld w,BlockPos p,BlockState state){require(w.getChunkManager().isChunkLoaded(p.getX()>>4,p.getZ()>>4),"fixture chunk not loaded");w.setBlockState(p,state,Block.NOTIFY_LISTENERS);}
    private static Path snapshot(ServerCommandSource s){return s.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/crop-trial.json");}
    private static String key(BlockPos p){return p.getX()+","+p.getY()+","+p.getZ();}
    private static void require(boolean test,String text){if(!test)throw new IllegalStateException(text);}
}
