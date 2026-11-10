package io.github.chronicle.lastcouncil.client;

import com.google.gson.*;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import io.github.chronicle.lastcouncil.network.PaperCopyPayloads;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

/** Disposable, opt-in native personal-copy integration fixture. */
public final class PaperCopiesSmoke {
    private static PaperCopiesSmoke instance;
    private final Path root, output, shared; private final String role, phase;
    private final List<String> checks = new ArrayList<>(); private final long started = System.nanoTime();
    private boolean connecting, done; private int step, wait; private UUID pending, request; private AdminView admin; private Notice notice;
    private List<ItemStack> fullInventory;
    private static final UUID NONE = new UUID(0, 0);
    private PaperCopiesSmoke(String path) {
        root = Path.of(System.getenv("CHRONICLE_PAPERS_ROOT")).toAbsolutePath().normalize();
        output = Path.of(path).toAbsolutePath().normalize(); shared = output.getParent();
        role = System.getenv("CHRONICLE_PAPERS_ROLE"); phase = System.getenv("CHRONICLE_PAPERS_PHASE");
        if (root.getParent() == null || !root.getParent().getFileName().toString().equals("test-runs") ||
            !Files.isRegularFile(root.resolve(".council-papers-fixture")) || output.startsWith(root) ||
            !Set.of("A", "B", "C").contains(role) || !Set.of("initial", "restart").contains(phase)) throw new IllegalArgumentException("unsafe papers fixture");
    }
    public static void register() { String p = System.getenv("CHRONICLE_PAPERS_SMOKE"); if (p == null) return; instance = new PaperCopiesSmoke(p); ClientTickEvents.END_CLIENT_TICK.register(instance::tick); }
    public static void admin(AdminView v) { if (instance != null && v.request().equals(instance.pending)) instance.admin = v; }
    public static void personal(PersonalView v) { }
    public static void notice(Notice v) { if (instance != null && v.request().equals(instance.pending)) instance.notice = v; }
    private static UUID offline(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
    private void mark(String key, String value) throws Exception { Files.writeString(shared.resolve(phase + "-" + key), value, StandardCharsets.UTF_8); }
    private boolean has(String key) { return Files.isRegularFile(shared.resolve(phase + "-" + key)); }
    private void command(MinecraftClient c, String command) { c.player.networkHandler.sendChatCommand(command); }
    private void admin(Operation op, long revision, UUID target, String house) {
        long office = admin == null ? -1 : admin.officeRevision(); pending = UUID.randomUUID(); notice = null; admin = null;
        ClientPlayNetworking.send(new AdminRequest(pending, op, revision, target, house, false, NONE, office));
    }
    private void send(UUID id, Section section, int index) { pending = id; notice = null; ClientPlayNetworking.send(new PaperCopyPayloads.Request(id, section, index)); }
    private int books(MinecraftClient c) { int count=0; for (int i=0;i<36;i++) if(c.player.getInventory().getStack(i).isOf(Items.WRITTEN_BOOK)) count++; return count; }
    private void tick(MinecraftClient c) {
        if(done)return;
        try {
            if(System.nanoTime()-started>400_000_000_000L)throw new IllegalStateException("timeout " + role + " " + phase + " step " + step);
            if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
            if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Paper smoke","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}
            if(c.player==null)return; if(wait>0){wait--;return;}
            if(step==0){mark("ready-"+role,"ready");step=1;return;}
            if(phase.equals("restart")){restart(c);return;}
            if(role.equals("A"))host(c);else player(c);
        } catch(Throwable e){finish(c,e.toString());}
    }
    private void host(MinecraftClient c)throws Exception {
        switch(step){
            case 1->{if(!has("ready-B")||!has("ready-C"))return;command(c,"chronicle editing on");wait=30;step=2;}
            case 2->{admin(Operation.OPEN,0,offline("ChronicleFabricB"),"");step=3;}
            case 3->{if(notice!=null)throw new IllegalStateException(notice.message());if(admin==null)return;require(!admin.houses().isEmpty(),"no houses");admin(Operation.APPOINT_CHANCELLOR,admin.revision(),offline("ChronicleFabricA"),"");step=13;}
            case 13->{if(notice!=null)throw new IllegalStateException(notice.message());if(admin==null)return;admin(Operation.ASSIGN,admin.revision(),offline("ChronicleFabricB"),admin.houses().get(0).id());step=4;}
            case 4->{if(notice!=null)throw new IllegalStateException(notice.message());if(admin==null)return;command(c,"chronicle editing off");mark("join","ready");step=5;}
            case 5->{if(!has("joined-B"))return;command(c,"chronicle session start");wait=50;mark("active","ready");step=6;}
            case 6->{if(!has("done-B")||!has("done-C"))return;mark("done-A","ready");finish(c,null);}
        }
    }
    private void player(MinecraftClient c)throws Exception {
        if(role.equals("C")) { switch(step){case 1->{if(!has("active"))return;send(UUID.randomUUID(),Section.INFORMATION,0);step=2;}case 2->{if(notice==null)return;require(!notice.message().contains("in your inventory"),"nonparticipant received copy");require(books(c)==0,"nonparticipant inventory changed");checks.add("nonparticipant copy request denied");mark("done-C","ready");step=3;}case 3->{if(has("done-A"))finish(c,null);}}return; }
        switch(step){
            case 1->{if(!has("join"))return;command(c,"chronicle players join");wait=30;mark("joined-B","ready");step=2;}
            case 2->{if(!has("active"))return;require(books(c)==0,"books issued automatically");command(c,"chronicle council");step=3;}
            case 3->{if(!(c.currentScreen instanceof PersonalScreen))return;press(c.currentScreen,"My house");wait=40;step=4;}
            case 4->{require(books(c)==0,"opening personal menu issued book");press(c.currentScreen,"Copy paper...");step=5;}
            case 5->{if(c.currentScreen instanceof PersonalScreen)return;press(c.currentScreen,"House information");wait=60;step=6;}
            case 6->{require(books(c)>0,"explicit picker delivered no written book");var entries=JsonParser.parseString(Files.readString(root.resolve("world/chronicle/personal-copies.json"))).getAsJsonObject().getAsJsonArray("operations");require(entries.size()==1,"expected one copy receipt");var receipt=entries.get(0).getAsJsonObject();require(receipt.get("stage").getAsString().equals("COMPLETE"),"receipt incomplete");request=UUID.fromString(receipt.get("request").getAsString());mark("request",request.toString());mark("count",Integer.toString(books(c)));checks.add("personal menu did not auto-issue; selecting House information in native chooser delivered a written book");send(request,Section.INFORMATION,0);step=8;}
            case 8->{if(notice==null)return;require(notice.message().contains("already completed"),"retry was not recognized");require(books(c)==Integer.parseInt(Files.readString(shared.resolve("initial-count"))),"retry duplicated books");checks.add("same request retry did not duplicate inventory");wait=15;step=12;}
            case 12->{send(UUID.randomUUID(),Section.INFORMATION,1);step=9;}
            case 9->{if(notice==null)return;require(!notice.message().contains("in your inventory"),"invalid index accepted");require(books(c)==Integer.parseInt(Files.readString(shared.resolve("initial-count"))),"invalid index changed inventory");checks.add("invalid paper index refused without inventory change");wait=15;step=10;}
            case 10->{send(UUID.randomUUID(),Section.INFORMATION,0);step=11;}
            case 11->{if(notice==null)return;require(notice.message().contains("in your inventory"),"new explicit request failed");require(books(c)>Integer.parseInt(Files.readString(shared.resolve("initial-count"))),"new request did not deliver another copy");checks.add("new explicit request delivered another copy");press(c.currentScreen,"Secrets");wait=40;step=14;}
            case 14->{press(c.currentScreen,"Copy paper...");step=15;}
            case 15->{if(c.currentScreen instanceof PersonalScreen)return;pressPrefix(c.currentScreen,"Secrets 1 /");wait=60;step=16;}
            case 16->{inspectSecret(c);wait=20;step=17;}
            case 17->{require(c.currentScreen instanceof BookScreen,"vanilla book reader missing");ScreenshotRecorder.saveScreenshot(shared.toFile(),"personal-book.png",c.getFramebuffer(),1,ignored->{});c.setScreen(null);int empty=0;for(int i=0;i<36;i++){var stack=c.player.getInventory().getStack(i);if(stack.isEmpty())empty++;else require(stack.isOf(Items.WRITTEN_BOOK),"unexpected item in disposable fixture");}require(empty>0,"no empty slots for full-inventory setup");command(c,"give @s minecraft:stone "+(empty*64));wait=60;step=18;}
            case 18->{fullInventory=new ArrayList<>();for(int i=0;i<36;i++){var stack=c.player.getInventory().getStack(i);require(!stack.isEmpty(),"inventory was not filled");fullInventory.add(stack.copy());}send(UUID.randomUUID(),Section.INFORMATION,0);step=19;}
            case 19->{if(notice==null)return;require(notice.message().contains("Make room"),"full inventory refusal missing: "+notice.message());for(int i=0;i<36;i++)require(ItemStack.areEqual(fullInventory.get(i),c.player.getInventory().getStack(i)),"full inventory changed at slot "+i);checks.add("full inventory refused without changing any slot");command(c,"clear @s minecraft:stone");wait=30;step=20;}
            case 20->{mark("count",Integer.toString(books(c)));mark("done-B","ready");step=7;}
            case 7->{if(has("done-A"))finish(c,null);}
        }
    }
    private void restart(MinecraftClient c)throws Exception {
        if(!role.equals("B")){mark("done-"+role,"ready");finish(c,null);return;}
        switch(step){
            case 1->{int expected=Integer.parseInt(Files.readString(shared.resolve("initial-count")));require(books(c)==expected,"written book inventory did not survive restart");request=UUID.fromString(Files.readString(shared.resolve("initial-request")));var entries=JsonParser.parseString(Files.readString(root.resolve("world/chronicle/personal-copies.json"))).getAsJsonObject().getAsJsonArray("operations");require(entries.size()==3&&entries.get(0).getAsJsonObject().get("stage").getAsString().equals("COMPLETE"),"completed receipt missing after restart");send(request,Section.INFORMATION,0);step=2;}
            case 2->{if(notice==null)return;require(notice.message().contains("already completed"),"restart retry was not recognized");require(books(c)==Integer.parseInt(Files.readString(shared.resolve("initial-count"))),"restart retry duplicated inventory");checks.add("written books and completed receipt survived restart; retry did not duplicate inventory");mark("done-B","ready");finish(c,null);}
        }
    }
    private void inspectSecret(MinecraftClient c)throws Exception {
        JsonObject cast=JsonParser.parseString(Files.readString(root.resolve("world/chronicle/last-council/cast.json"))).getAsJsonObject();
        JsonObject papers=cast.getAsJsonObject("papers");JsonObject own=papers.getAsJsonObject(c.player.getUuid().toString());
        require(own!=null&&!own.getAsJsonArray("secrets").isEmpty(),"fixture has no issued secret");
        String secret=own.getAsJsonArray("secrets").get(0).getAsString();
        JsonObject definitions;try(var in=PaperCopiesSmoke.class.getResourceAsStream("/data/last_council/chronicle/houses.json")){require(in!=null,"house definitions missing");definitions=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
        String title=definitions.getAsJsonObject("houses").getAsJsonObject(own.get("houseId").getAsString()).get("title").getAsString();
        ItemStack book=ItemStack.EMPTY;String text="";List<Text> pages=List.of();
        for(int i=0;i<36;i++){var stack=c.player.getInventory().getStack(i);if(!stack.isOf(Items.WRITTEN_BOOK))continue;var content=stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);if(content==null)continue;var actual=content.pages().stream().map(p->p.raw()).toList();String joined=actual.stream().map(Text::getString).collect(java.util.stream.Collectors.joining());if(joined.equals(title+"\n\n"+secret)){book=stack;text=joined;pages=actual;break;}}
        require(!book.isEmpty(),"delivered literal secret pages differ from committed owner paper");
        var custom=book.get(DataComponentTypes.CUSTOM_DATA);require(custom!=null&&custom.copyNbt().getString("chronicle_personal_copy").isPresent()&&!custom.copyNbt().contains("chronicle_book"),"personal copy marker missing or common managed-book marker present");
        int foreign=0;for(var e:papers.entrySet())if(!e.getKey().equals(c.player.getUuid().toString()))for(var item:e.getValue().getAsJsonObject().getAsJsonArray("secrets")){String other=item.getAsString();if(!other.isBlank()&&!other.equals(secret)){require(!text.contains(other),"foreign House secret included in owner's book");foreign++;}}
        require(foreign>0,"fixture lacks distinct foreign secret");for(Text page:pages)require(c.textRenderer.wrapLines(page,114).size()<=14,"written-book page exceeds native reader height");
        checks.add("actual literal secret pages match committed owner paper, exclude foreign secrets, carry a personal marker, and fit native wrapping");
        c.getWindow().setWindowedSize(1280,720);c.setScreen(new BookScreen(new BookScreen.Contents(pages)));
    }
    private static void pressPrefix(Screen s,String label){var b=s.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(x->x.active&&x.getMessage().getString().startsWith(label)).findFirst().orElseThrow(()->new IllegalStateException("button unavailable: "+label));b.onPress(new MouseInput(0,0));}
    private static void press(Screen s,String label){var b=s.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(x->x.active&&x.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("button unavailable: "+label));b.onPress(new MouseInput(0,0));}
    private void finish(MinecraftClient c,String error){if(done)return;done=true;try{var o=new JsonObject();o.addProperty("result",error==null?"PASS":"FAIL");o.addProperty("detail",error==null?"Completed":error);o.add("checks",new Gson().toJsonTree(checks));Files.writeString(output,new Gson().toJson(o),StandardCharsets.UTF_8);}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
