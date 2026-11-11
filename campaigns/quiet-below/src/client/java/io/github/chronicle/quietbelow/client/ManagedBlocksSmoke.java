package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.ItemEnchantingScreen;
import io.github.chronicle.core.client.OfferingScreen;
import io.github.chronicle.core.network.CoreMenuPayloads;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Real vanilla placement packets and native limit controls in a disposable offline world. */
final class ManagedBlocksSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_MANAGED_SMOKE"),ROOT=System.getenv("CHRONICLE_MANAGED_SERVER_DIR"),PHASE=System.getenv("CHRONICLE_MANAGED_PHASE");
    private static final String ADDRESS="127.0.0.1:25575",ALTAR="quiet_below:ancient_altar",HEART="quiet_below:relic_heart";
    private static final BlockPos FIRST=new BlockPos(127,64,131),FORGE=new BlockPos(127,64,133),ROOT_A=new BlockPos(129,64,131),ROOT_B=new BlockPos(130,64,132);
    private static final BlockPos SECOND=new BlockPos(1023,64,1027),THIRD=new BlockPos(1025,64,1027),DISCOVERED=new BlockPos(1026,64,1026);
    private final Path output,root;private final String phase;
    private final List<Step> steps=new ArrayList<>();private final List<String> checks=new ArrayList<>(),messages=new ArrayList<>();
    private final long overall=System.nanoTime()+300_000_000_000L;
    private int index,baseline,beforeItems;private long deadline,settleUntil;private boolean connected,built,started,finished;
    private BlockPos mining;private String firstRootId;private long firstRootRevision;

    private ManagedBlocksSmoke(Path output,Path root,String phase)throws IOException{
        this.output=output;this.root=root.toAbsolutePath().normalize();this.phase=phase;
        require(List.of("initial","restart","final").contains(phase),"unknown phase");
        require(this.root.getParent().getFileName().toString().equals("test-runs")&&this.root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"not an owned disposable runtime");
        require(!output.toAbsolutePath().normalize().startsWith(this.root),"reports must be outside runtime");
        List<String> properties=Files.readAllLines(this.root.resolve("server.properties"));
        require(properties.contains("online-mode=false")&&properties.contains("server-ip=127.0.0.1")&&properties.contains("server-port=25575"),"fixture authentication/address guard failed");
        Files.createDirectories(output);
    }
    static void register(){
        if(OUTPUT==null)return;
        try{var smoke=new ManagedBlocksSmoke(Path.of(OUTPUT),Path.of(ROOT),PHASE);
            ClientReceiveMessageEvents.GAME.register((text,overlay)->{if(!overlay&&!smoke.finished)smoke.messages.add(text.getString());});
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        }catch(Exception error){setupFail(error);MinecraftClient.getInstance().scheduleStop();}
    }
    private void tick(MinecraftClient client){
        if(finished)return;
        try{
            require(System.nanoTime()<overall,"overall timeout");
            if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Disposable managed blocks",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
            if(client.player==null||client.world==null||client.player.age<40)return;
            require(client.getCurrentServerEntry()!=null&&ADDRESS.equals(client.getCurrentServerEntry().address),"wrong endpoint");
            if(mining!=null)client.interactionManager.updateBlockBreakingProgress(mining,Direction.UP);
            if(!built){build(client);built=true;}
            client.getToastManager().clear();
            if(index==steps.size()){finish(client,true,"managed placement, policy and persistence proved");return;}
            Step step=steps.get(index);
            if(!started){baseline=messages.size();deadline=System.nanoTime()+step.timeout*1_000_000L;step.start.run();started=true;}
            if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}else require(System.nanoTime()<deadline,"timed out: "+step.name);
        }catch(Throwable error){finish(client,false,error.getClass().getSimpleName()+": "+error.getMessage());}
    }
    private void build(MinecraftClient client){
        command(client,"enable explicit paused map editing","chronicle editing on","Editing enabled");
        command(client,"enable global paused map work","chronicle session building on","Building Mode enabled for everyone");
        if(phase.equals("initial")){
            step("fresh fixture player is in Creative",()->{},()->client.player.isCreative(),15_000);
            teleport(client,128.5,66,134.5);
            loaded(client,"initial",120,120,144,144);
            command(client,"build only the currently player-loaded floor","fill 120 63 120 144 63 144 minecraft:stone","filled");
            teleport(client,128.5,64,134.5);
            command(client,"use Survival map placement with explicit editing","gamemode survival","survival");
            give(client,ALTAR,2,QuietBelow.ANCIENT_ALTAR);
            place(client,"first Altar consumes exactly one item and auto-registers",FIRST,QuietBelow.ANCIENT_ALTAR,1,true);
            place(client,"second Altar is refused without consuming its item",new BlockPos(129,64,133),QuietBelow.ANCIENT_ALTAR,1,false);
            give(client,"quiet_below:ancient_forge",2,QuietBelow.ANCIENT_FORGE);
            place(client,"first Forge auto-registers without a manual anchor",FORGE,QuietBelow.ANCIENT_FORGE,1,true);
            place(client,"second Forge is refused without consuming its item",new BlockPos(129,64,133),QuietBelow.ANCIENT_FORGE,1,false);
            step("stations need no Altar or Forge authoring binding",()->{},()->!hasBinding(ALTAR)&&!hasBinding("quiet_below:ancient_forge"),10_000);
            openStation(client,FIRST,true);openStation(client,FORGE,false);
            give(client,HEART,2,QuietBelow.RELIC_HEART_ITEM);
            place(client,"first Heart automatically establishes one physical instance",ROOT_A,QuietBelow.RELIC_HEART_ITEM,1,true);
            step("capture the automatically registered Heart pin",()->{JsonObject pin=binding(HEART);firstRootId=pin.get("siteId").getAsString();firstRootRevision=pin.get("siteRevision").getAsLong();},()->firstRootId!=null,10_000);
            command(client,"allow mapmaker relocation before ecology setup","gamemode creative","creative");
            step("physically remove the unbound Heart",()->client.interactionManager.attackBlock(ROOT_A,Direction.UP),()->client.world.getBlockState(ROOT_A).isAir()&&count(HEART)==0,20_000);
            command(client,"return to Survival before relocating the Heart","gamemode survival","survival");
            place(client,"unbound Heart can be moved normally",ROOT_B,QuietBelow.RELIC_HEART_ITEM,1,true);
            step("relocation preserves alias identity and advances its unbound revision",()->{},()->{JsonObject pin=binding(HEART);return pin.get("siteId").getAsString().equals(firstRootId)&&pin.get("siteRevision").getAsLong()>firstRootRevision;},20_000);
            command(client,"move away before saved restart","gamemode creative","creative");
            teleport(client,1024.5,66,1030.5);
            loaded(client,"distant",1020,1020,1040,1040);
            command(client,"prepare a second naturally loaded floor","fill 1020 63 1020 1040 63 1040 minecraft:stone","filled");
            teleport(client,1024.5,64,1030.5);
            command(client,"save a Survival player away from the first stations","gamemode survival","survival");
        }else{
            command(client,"prove original station chunk is actually unloaded","execute unless loaded 127 64 131 run tellraw @s {\"text\":\"MBS_ORIGIN_UNLOADED\"}","MBS_ORIGIN_UNLOADED");
            if(phase.equals("restart")){
                step("restart retains all three managed types",()->{},()->count(ALTAR)==1&&count("quiet_below:ancient_forge")==1&&count(HEART)==1,20_000);
                place(client,"unloaded Altar still consumes its quota after restart",SECOND,QuietBelow.ANCIENT_ALTAR,1,false);
                limit(client,2);
                place(client,"raised server limit permits one additional Altar",SECOND,QuietBelow.ANCIENT_ALTAR,2,true);
                give(client,ALTAR,1,QuietBelow.ANCIENT_ALTAR);
                place(client,"raised limit also refuses the next extra Altar",THIRD,QuietBelow.ANCIENT_ALTAR,2,false);
                rejectLimitBelowPlaced(client,1,2);
                give(client,"minecraft:netherite_pickaxe",1,Items.NETHERITE_PICKAXE);
                step("mine the actual Altar in Survival",()->{select(client,Items.NETHERITE_PICKAXE);mining=SECOND;client.interactionManager.attackBlock(SECOND,Direction.UP);},()->client.world.getBlockState(SECOND).isAir()&&count(ALTAR)==1,25_000);
                step("stop mining after authoritative quota release",()->{mining=null;client.interactionManager.cancelBlockBreaking();},()->true,1_000);
                limit(client,1);
                place(client,"lowered policy refuses a replacement while its sole slot is occupied",THIRD,QuietBelow.ANCIENT_ALTAR,1,false);
                limit(client,2);
                place(client,"verified removal frees the raised slot for a replacement",THIRD,QuietBelow.ANCIENT_ALTAR,2,true);
                command(client,"adopt an operator-created extra block without deleting it","setblock 1026 64 1026 quiet_below:ancient_altar","changed");
                step("direct placement is counted even when over limit",()->{},()->count(ALTAR)==3&&client.world.getBlockState(DISCOVERED).isOf(QuietBelow.ANCIENT_ALTAR_BLOCK),25_000);
                rejectLimitBelowPlaced(client,1,3);
                step("rejected policy decrease never deletes discovered or placed blocks",()->{},()->limitValue()==2&&count(ALTAR)==3&&client.world.getBlockState(THIRD).isOf(QuietBelow.ANCIENT_ALTAR_BLOCK)&&client.world.getBlockState(DISCOVERED).isOf(QuietBelow.ANCIENT_ALTAR_BLOCK),10_000);
            }else{
                step("final restart keeps the policy and over-limit records",()->{},()->limitValue()==2&&count(ALTAR)==3,20_000);
                give(client,ALTAR,1,QuietBelow.ANCIENT_ALTAR);
                place(client,"persisted policy refuses additional placements after discovered overage",new BlockPos(1022,64,1027),QuietBelow.ANCIENT_ALTAR,3,false);
            }
        }
    }
    private void place(MinecraftClient client,String name,BlockPos at,Item item,int expectedCount,boolean accepted){
        step(name,()->{beforeItems=items(client,item);select(client,item);interact(client,at.down());settleUntil=System.nanoTime()+1_000_000_000L;},()->{
            if(System.nanoTime()<settleUntil)return false;
            if(accepted)return client.world.getBlockState(at).isOf(((BlockItem)item).getBlock())&&count(item==QuietBelow.RELIC_HEART_ITEM?HEART:item==QuietBelow.ANCIENT_ALTAR?ALTAR:"quiet_below:ancient_forge")==expectedCount&&items(client,item)==beforeItems-1;
            require(count(item==QuietBelow.RELIC_HEART_ITEM?HEART:item==QuietBelow.ANCIENT_ALTAR?ALTAR:"quiet_below:ancient_forge")==expectedCount,"denied placement changed ledger count");
            require(client.world.getBlockState(at).isAir(),"denied placement created a block");require(items(client,item)==beforeItems,"denied placement consumed an item");return true;
        },25_000);
    }
    private void openStation(MinecraftClient client,BlockPos at,boolean altar){
        step("open intrinsic "+(altar?"Altar":"Forge")+" without manual registration",()->{client.player.getInventory().setSelectedSlot(8);interact(client,at);},()->altar?client.currentScreen instanceof OfferingScreen:client.currentScreen instanceof ItemEnchantingScreen,15_000);
        step("close intrinsic station",()->client.currentScreen.close(),()->client.currentScreen==null,10_000);
    }
    private void limit(MinecraftClient client,int value){
        step("open management for limit "+value,()->send(client,"chronicle"),()->client.currentScreen instanceof CoreMenuScreen,15_000);
        step("enable server-authorized policy editing",()->{if(!menu(client).state().editing())press(menu(client),"Enable editing");},()->menu(client).state().editing(),15_000);
        step("open Game setup for placement policy",()->press(menu(client),"Game setup"),()->menu(client).state().page()==Page.SETUP,15_000);
        step("select the sole Block limits entry",()->menu(client).selectForTest("limits"),()->menu(client).state().selected().equals("limits"),15_000);
        step("open Block limits modal",()->press(menu(client),"Choose block type\u2026"),()->menu(client).limitDialogOpenForTest()&&menu(client).limitDialogStageForTest().equals("choose"),15_000);
        step("choose Altar type policy in modal",()->menu(client).selectForTest(ALTAR),()->menu(client).limitDialogStageForTest().equals("edit")&&menu(client).limitFieldEnabledForTest(),15_000);
        step("capture native limit detail "+value,()->ScreenshotRecorder.saveScreenshot(output.toFile(),"limit-"+phase+"-"+value+".png",client.getFramebuffer(),1,ignored->{}),()->pngReady(output.resolve("screenshots/limit-"+phase+"-"+value+".png")),15_000);
        step("save cap "+value+" through native control",()->{menu(client).setLimitValueForTest(Integer.toString(value));require(menu(client).triggerForTest("Save"),"cap Save unavailable");},()->limitValue()==value&&menu(client).state().success()&&menu(client).limitDialogStageForTest().equals("edit"),15_000);
        closeLimitManagement(client,"cap change "+value);
    }
    private void rejectLimitBelowPlaced(MinecraftClient client,int value,int placed){
        step("open management for rejected limit "+value,()->send(client,"chronicle"),()->client.currentScreen instanceof CoreMenuScreen,15_000);
        step("enable server-authorized policy editing for rejected limit",()->{if(!menu(client).state().editing())press(menu(client),"Enable editing");},()->menu(client).state().editing(),15_000);
        step("open Game setup for rejected limit",()->press(menu(client),"Game setup"),()->menu(client).state().page()==Page.SETUP,15_000);
        step("select Block limits for rejected limit",()->menu(client).selectForTest("limits"),()->menu(client).state().selected().equals("limits"),15_000);
        step("open Block limits modal for rejected limit",()->press(menu(client),"Choose block type\u2026"),()->menu(client).limitDialogOpenForTest()&&menu(client).limitDialogStageForTest().equals("choose"),15_000);
        step("select Altar for rejected limit",()->menu(client).selectForTest(ALTAR),()->menu(client).limitDialogStageForTest().equals("edit")&&menu(client).limitFieldEnabledForTest(),15_000);
        final int[] before={-1,-1};
        step("below-placed limit disables modal Save",()->{before[0]=limitValue();before[1]=count(ALTAR);menu(client).setLimitValueForTest(Integer.toString(value));},()->!menu(client).triggerForTest("Save")&&limitValue()==before[0]&&count(ALTAR)==before[1],10_000);
        step("forged below-placed limit is rejected by the authoritative service",()->{
            var screen=menu(client);
            ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.BLOCK_LIMIT_SET,Page.SETUP,"limit:"+ALTAR,Integer.toString(value),screen.state().token(),screen.state().index(),screen.state().filter(),""));
        },()->{
            var screen=menu(client);
            return !screen.state().success()&&screen.state().message().contains("Limit cannot be below "+placed+" placed")&&limitValue()==before[0]&&count(ALTAR)==before[1];
        },15_000);
        closeLimitManagement(client,"rejected limit "+value);
    }
    private void closeLimitManagement(MinecraftClient client,String reason){
        step("close Block limits modal after "+reason,()->require(menu(client).triggerForTest("Close"),"modal Close unavailable"),()->!menu(client).limitDialogOpenForTest()&&menu(client).state().page()==Page.SETUP&&menu(client).state().selected().equals("limits"),15_000);
        step("close management after "+reason,()->menu(client).close(),()->client.currentScreen==null,10_000);
    }
    private void loaded(MinecraftClient client,String name,int x1,int z1,int x2,int z2){
        step("wait for naturally player-loaded "+name+" chunks",()->settleUntil=0,()->{
            String marker="MBS_LOADED_"+name;
            if(messages.subList(baseline,messages.size()).contains(marker))return true;
            if(System.nanoTime()>=settleUntil){send(client,"execute if loaded "+x1+" 63 "+z1+" if loaded "+x2+" 63 "+z2+" run tellraw @s {\"text\":\""+marker+"\"}");settleUntil=System.nanoTime()+1_000_000_000L;}
            return false;
        },30_000);
    }
    private void teleport(MinecraftClient client,double x,double y,double z){step("move to loaded fixture "+x,()->send(client,"tp @s "+x+" "+y+" "+z),()->Math.abs(client.player.getX()-x)<.1&&Math.abs(client.player.getY()-y)<.1&&Math.abs(client.player.getZ()-z)<.1,20_000);}
    private void give(MinecraftClient client,String id,int amount,Item item){step("give fixture "+id,()->{beforeItems=items(client,item);send(client,"give @s "+id+" "+amount);},()->items(client,item)>=beforeItems+amount,15_000);}
    private void command(MinecraftClient client,String name,String command,String expected){step(name,()->send(client,command),()->messages.subList(baseline,messages.size()).stream().anyMatch(m->m.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))),20_000);}
    private void interact(MinecraftClient client,BlockPos block){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(block).add(0,.5,0),Direction.UP,block,false));}
    private static int items(MinecraftClient client,Item item){int count=0;for(int i=0;i<client.player.getInventory().size();i++){var stack=client.player.getInventory().getStack(i);if(stack.isOf(item))count+=stack.getCount();}return count;}
    private static void select(MinecraftClient client,Item item){for(int i=0;i<9;i++)if(client.player.getInventory().getStack(i).isOf(item)){client.player.getInventory().setSelectedSlot(i);return;}throw new IllegalStateException("fixture item absent from hotbar");}
    private int count(String type){int count=0;for(var entry:json(root.resolve("world/chronicle/managed-blocks.json")).getAsJsonArray("instances"))if(entry.getAsJsonObject().get("type").getAsString().equals(type))count++;return count;}
    private int limitValue(){Path file=root.resolve("config/chronicle/managed-block-limits.json");if(!Files.exists(file))return 1;JsonObject overrides=json(file).getAsJsonObject("overrides");return overrides.has(ALTAR)?overrides.get(ALTAR).getAsInt():1;}
    private JsonObject binding(String role){for(var entry:json(root.resolve("world/chronicle/map-assignments.json")).getAsJsonArray("bindings"))if(entry.getAsJsonObject().get("requirementId").getAsString().equals(role))return entry.getAsJsonObject();throw new IllegalStateException("missing fixture binding: "+role);}
    private boolean hasBinding(String role){return json(root.resolve("world/chronicle/map-assignments.json")).getAsJsonArray("bindings").asList().stream().anyMatch(e->e.getAsJsonObject().get("requirementId").getAsString().equals(role));}
    private static JsonObject json(Path file){try{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}catch(IOException error){throw new IllegalStateException("fixture state unavailable: "+file.getFileName(),error);}}
    private static CoreMenuScreen menu(MinecraftClient client){if(client.currentScreen instanceof CoreMenuScreen screen)return screen;throw new IllegalStateException("management screen unavailable");}
    private static void press(CoreMenuScreen screen,String label){if(screen.triggerForTest(label)){screen.triggerForTest("Confirm: "+label);return;}for(int i=0;i<60;i++)screen.scrollDetailForTest(1);for(int i=0;i<30;i++){if(screen.triggerForTest(label)){screen.triggerForTest("Confirm: "+label);return;}screen.scrollDetailForTest(-1);}throw new IllegalStateException("native control absent: "+label);}
    private void send(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private void step(String name,Runnable start,BooleanSupplier done,long timeout){steps.add(new Step(name,start,done,timeout));}
    private void finish(MinecraftClient client,boolean pass,String detail){if(finished)return;finished=true;try{Files.writeString(output.resolve("managed-"+phase+".json"),new Gson().toJson(Map.of("result",pass?"PASS":"FAIL","phase",phase,"detail",detail,"checks",checks,"messages",messages)));}catch(IOException ignored){}client.scheduleStop();}
    private static boolean pngReady(Path path){try{if(!Files.isRegularFile(path))return false;byte[] bytes=Files.readAllBytes(path);int n=bytes.length;return n>24&&bytes[n-8]==73&&bytes[n-7]==69&&bytes[n-6]==78&&bytes[n-5]==68;}catch(IOException ignored){return false;}}
    private static void require(boolean value,String detail){if(!value)throw new IllegalStateException(detail);}
    private record Step(String name,Runnable start,BooleanSupplier done,long timeout){}
    private static void setupFail(Exception error){try{if(OUTPUT!=null){Files.createDirectories(Path.of(OUTPUT));Files.writeString(Path.of(OUTPUT).resolve("managed-"+String.valueOf(PHASE)+".json"),new Gson().toJson(Map.of("result","FAIL","detail","setup: "+error.getMessage())));}}catch(Exception ignored){}}
}
