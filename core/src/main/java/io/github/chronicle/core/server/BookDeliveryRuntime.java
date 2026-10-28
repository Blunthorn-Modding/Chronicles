package io.github.chronicle.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.*;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.*;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Managed books share the chest journal, its inventory transactions and its existing fill clock. */
public final class BookDeliveryRuntime {
    private static final String SYSTEM="00000000-0000-0000-0000-000000000000";
    private static final Map<MinecraftServer,Map<String,String>> PROBLEMS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Integer> CURSORS=new IdentityHashMap<>();
    private BookDeliveryRuntime() { }
    public static void register(){
        ServerTickEvents.END_SERVER_TICK.register(BookDeliveryRuntime::discover);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->observeInventory(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{PROBLEMS.remove(s);CURSORS.remove(s);});
    }
    private static String now(){return Instant.now().toString();}
    public static String ruleFingerprint(BookDefinition book){return book.rule().name();}
    private static ChestContentService service(MinecraftServer s){return ChestAuthoring.service(s);}
    public static BookSummary summary(MinecraftServer s,BookDefinition b){var d=service(s).state().bookDelivery().deliveries().get(b.id());return new BookSummary(b.id(),b.title(),b.author(),b.rule().name(),d!=null&&d.issued(),d!=null&&d.firstDiscovery()!=null);}
    private record Tag(String campaign,String book,String token) { }
    private static Tag tag(ItemStack stack){
        if(!stack.isOf(net.minecraft.item.Items.WRITTEN_BOOK))return null;var data=stack.get(DataComponentTypes.CUSTOM_DATA);if(data==null)return null;
        var marker=data.copyNbt().getCompound("chronicle_book").orElse(null);if(marker==null)return null;
        return new Tag(marker.getString("campaign").orElse(""),marker.getString("book").orElse(""),marker.getString("token").orElse(""));
    }
    private static Tag authentic(MinecraftServer s,ItemStack stack){
        var t=tag(stack);if(t==null)return null;var ledger=service(s).state().bookDelivery();var d=ledger.deliveries().get(t.book);
        return ledger.campaignId().equals(t.campaign)&&d!=null&&d.deliveryToken().equals(t.token)&&(d.issued()||!d.pendingOperationIds().isEmpty())?t:null;
    }
    /** Authentic managed identity only; labels and client-supplied book IDs are never accepted. */
    public static String authenticBook(MinecraftServer server,ItemStack stack){var tag=authentic(server,stack);return tag==null?null:tag.book;}
    public static String prepareDiscovery(MinecraftServer server,String book,String token,java.util.UUID actor,boolean activeOrigin){
        return prepareDiscovery(server,book,token,actor,activeOrigin,"book:"+token);
    }
    static String prepareDiscovery(MinecraftServer server,String book,String token,java.util.UUID actor,boolean activeOrigin,String authority){
        var delivery=service(server).state().bookDelivery().deliveries().get(book);if(delivery!=null&&delivery.firstDiscovery()!=null)return null;
        if(authority.startsWith("book:")&&EventSources.pending(server).stream().anyMatch(receipt->receipt.target().equals(book)&&receipt.authority().equals(authority))) {
            var failure=new IllegalStateException("An earlier discovery attempt for this book is uncertain; review its exact source receipt before retrying");EventSources.fail(server,failure);throw new EventSources.Unavailable(failure.getMessage(),failure);
        }
        var source=new io.github.chronicle.core.play.EventSource("chronicle_core:book_found",book,"");
        String attempt=authority.startsWith("book:")?authority+"|attempt:"+java.util.UUID.randomUUID():authority;
        return EventSources.prepare(server,EventSources.id(attempt,source.type(),book,""),source,actor,activeOrigin,authority);
    }
    static io.github.chronicle.core.progression.BookSourceRecovery.Decision inspectRecovery(MinecraftServer server,io.github.chronicle.core.progression.SourceLedger.Receipt receipt) {
        var delivery=service(server).state().bookDelivery().deliveries().get(receipt.target());var player=receipt.actor()==null?null:server.getPlayerManager().getPlayer(receipt.actor());boolean authenticInventory=false;
        if(player!=null)for(int slot=0;slot<player.getInventory().size();slot++){var tag=authentic(server,player.getInventory().getStack(slot));if(tag!=null&&tag.book.equals(receipt.target())&&receipt.authority().equals("book:"+tag.token)){authenticInventory=true;break;}}
        return io.github.chronicle.core.progression.BookSourceRecovery.inspect(receipt,delivery,authenticInventory);
    }
    static void recoverDiscovery(MinecraftServer server,io.github.chronicle.core.progression.SourceLedger.Receipt receipt)throws IOException {
        if(inspectRecovery(server,receipt)==io.github.chronicle.core.progression.BookSourceRecovery.Decision.RECORD_AUTHENTIC_INVENTORY) {
            server.getPlayerManager().saveAllPlayerData();
            service(server).recordDiscovery(receipt.target(),receipt.authority().substring(5),receipt.actor().toString(),now());
        }
    }
    static String stackJson(MinecraftServer s,BookDefinition b){
        JsonObject content=new JsonObject();content.addProperty("title",b.title());content.addProperty("author",b.author());content.addProperty("generation",0);content.addProperty("resolved",true);
        JsonArray pages=new JsonArray();for(String page:b.pages()){JsonObject text=new JsonObject();text.addProperty("text",page);pages.add(text);}content.add("pages",pages);
        JsonObject marker=new JsonObject();marker.addProperty("campaign",service(s).state().bookDelivery().campaignId());marker.addProperty("book",b.id());marker.addProperty("token",service(s).deliveryToken(b.id()));
        JsonObject custom=new JsonObject();custom.add("chronicle_book",marker);JsonObject components=new JsonObject();components.add("minecraft:written_book_content",content);components.add("minecraft:custom_data",custom);
        JsonObject stack=new JsonObject();stack.addProperty("id","minecraft:written_book");stack.addProperty("count",1);stack.add("components",components);return stack.toString();
    }
    private static void discover(MinecraftServer server){
        if(server.getTicks()%5==0)for(var player:server.getPlayerManager().getPlayerList())observeInventory(player);
    }
    /** Called after authoritative vanilla inventory mutations; the cursor is deliberately excluded. */
    public static void observeInventory(net.minecraft.server.network.ServerPlayerEntity player){
        var server=player.getEntityWorld().getServer();
        if(!server.isOnThread()||!playing(server)||RuntimeHolds.reason(server)!=null||player.isSpectator()||player.isCreative()||AuthoringRuntime.isAuthor(player))return;
        try{
            if(service(server).state().bookDelivery().deliveries().isEmpty())return;
            for(int slot=0;slot<player.getInventory().size();slot++){
                var t=authentic(server,player.getInventory().getStack(slot));if(t==null)continue;
                var d=service(server).state().bookDelivery().deliveries().get(t.book);if(!d.issued()||d.firstDiscovery()!=null)continue;
                String receipt=prepareDiscovery(server,t.book,t.token,player.getUuid(),EventSources.active(player));
                service(server).recordDiscovery(t.book,t.token,player.getUuidAsString(),now());
                EventSources.complete(server,receipt,true);
            }
        }catch(IOException|RuntimeException e){if(!(e instanceof EventSources.Unavailable))RuntimeHolds.hold(server,"chronicle_core:book_delivery","Book discovery could not be saved; preserve inventories and inspect the server log.");io.github.chronicle.core.ChronicleCore.LOGGER.error("Book discovery held without discarding prior state",e);}
    }
    private static boolean playing(MinecraftServer s){var c=ChronicleRuntime.available(s);return c!=null&&c.state().phase()==SessionPhase.ACTIVE&&s.getPlayerManager().getCurrentPlayerCount()>0;}
    private static boolean maintained(MinecraftServer s,String book){var r=service(s).state().bookDelivery().keepRequests().get(book);return r!=null&&r.status()!=BookKeepRequest.Status.CANCELLED&&r.status()!=BookKeepRequest.Status.COMPLETED_FOUND;}
    private static boolean eligible(MinecraftServer s,BookDefinition b,Set<String> reserved){
        var d=service(s).state().bookDelivery().deliveries().get(b.id());
        return switch(b.rule()){case OFF->false;case REPEAT->true;case UNTIL_FOUND->d==null||d.firstDiscovery()==null;case ONCE->!reserved.contains(b.id())&&(d==null||!d.issued()&&d.pendingOperationIds().isEmpty());};
    }
    public record Fill(InventorySnapshot after,List<BookDeliveryClaim> claims) { }
    public static Fill planFill(MinecraftServer s,ChestBinding binding,LootType type,InventorySnapshot before,InventorySnapshot ordinary,UUID operation,Set<String> reserved){
        var old=ChestAuthoring.decode(s,before);var entries=new ArrayList<>(Collections.nCopies(before.size(),"{}"));var present=new HashSet<String>();
        for(int i=0;i<old.size();i++){var t=authentic(s,old.get(i));if(t!=null){entries.set(i,before.stacks().get(i));present.add(t.book);}}
        List<Integer> slots=new ArrayList<>();for(int i=0;i<entries.size();i++)if(entries.get(i).equals("{}"))slots.add(i);
        var random=new Random(operation.getMostSignificantBits()^operation.getLeastSignificantBits());Collections.shuffle(slots,random);
        var refs=new LinkedHashMap<String,Double>();if(binding.directBook()!=null)refs.put(binding.directBook().bookId(),binding.directBook().chancePercent());for(var r:type.books())refs.putIfAbsent(r.bookId(),r.chancePercent());
        var claims=new ArrayList<BookDeliveryClaim>();int used=0;
        for(var ref:refs.entrySet()){
            var b=BookRuntime.books(s).get(ref.getKey());if(b==null)throw new IllegalStateException("Missing registered book: "+ref.getKey());
            if(used>=slots.size()||present.contains(b.id())||maintained(s,b.id())||!eligible(s,b,reserved)||random.nextDouble()*100>=ref.getValue())continue;
            entries.set(slots.get(used++),stackJson(s,b));claims.add(new BookDeliveryClaim(b.id(),service(s).deliveryToken(b.id()),false));if(b.rule()==BookDefinition.Rule.ONCE)reserved.add(b.id());
        }
        for(String item:ordinary.stacks())if(!item.equals("{}")&&used<slots.size())entries.set(slots.get(used++),item);
        var normalized=ChestAuthoring.decode(s,new InventorySnapshot(entries.size(),entries));
        return new Fill(ChestAuthoring.snapshot(s,new net.minecraft.inventory.SimpleInventory(normalized.toArray(ItemStack[]::new))),List.copyOf(claims));
    }
    public static void setDirect(ServerCommandSource source,String id,long revision,BookChance direct)throws IOException{
        if(!source.hasPermissionLevel(2)||source.getPlayer()==null||!AuthoringRuntime.isAuthor(source.getPlayer())||!AuthoringRuntime.editable(source.getServer()))throw new IllegalStateException("Direct book settings require non-active operator Editing.");
        if(direct!=null&&!BookRuntime.books(source.getServer()).containsKey(direct.bookId()))throw new IllegalArgumentException("Choose a registered book.");
        service(source.getServer()).setDirectBook(id,revision,direct);
    }
    private static boolean needsReview(MinecraftServer s,BookKeepRequest r,BookDefinition b){
        var d=service(s).state().bookDelivery().deliveries().get(b.id());
        return r.status()==BookKeepRequest.Status.SUSPENDED_RULE||r.status()==BookKeepRequest.Status.SUSPENDED_ONCE||!r.ruleFingerprint().equals(ruleFingerprint(b))||b.rule()==BookDefinition.Rule.ONCE&&!r.ownIssuance()&&!r.overrideRule()&&d!=null&&(d.issued()||!d.pendingOperationIds().isEmpty());
    }
    public static KeepForm keepForm(MinecraftServer s,String bookId,String chosenTable){
        var b=BookRuntime.books(s).get(bookId);if(b==null)return null;
        var r=service(s).state().bookDelivery().keepRequests().get(bookId);boolean active=r!=null&&r.status()!=BookKeepRequest.Status.CANCELLED&&r.status()!=BookKeepRequest.Status.COMPLETED_FOUND;
        String tableId=active?r.lootTableId():chosenTable;var table=TypedChestLoot.types(s).get(tableId);var info=summary(s,b);
        boolean review=active&&needsReview(s,r,b);boolean override=b.rule()==BookDefinition.Rule.OFF||b.rule()==BookDefinition.Rule.ONCE&&info.issued()&&(!active||!r.ownIssuance()||r.approvedRule()!=b.rule());
        String status=table==null?"Waiting - selected loot table unavailable; restore it or Stop keeping":info.found()?"Found - keeping stopped":review?"Waiting - review updated spawning eligibility":active?PROBLEMS.getOrDefault(s,Map.of()).getOrDefault(bookId,r.location()==null?"Waiting for the next table refill":"Kept available until found"):"Not enabled";
        String loc=r==null||r.location()==null?"":r.location().target().dimension()+" "+r.location().target().x()+" "+r.location().target().y()+" "+r.location().target().z();
        boolean can=playing(s)&&RuntimeHolds.reason(s)==null;return new KeepForm(bookId,b.title(),tableId,table==null?tableId:table.label(),status,active,review,override,info.found(),can,can?"":"Requires occupied active play without an operation hold.",loc);
    }
    public static String keep(ServerCommandSource source,String bookId,String tableId,boolean override,String action)throws IOException{
        if(!source.hasPermissionLevel(2)||!playing(source.getServer())||RuntimeHolds.reason(source.getServer())!=null)throw new IllegalStateException("Keeping books requires active play and an operator.");
        var s=source.getServer();var book=BookRuntime.books(s).get(bookId);if(book==null)throw new IllegalArgumentException("Choose a registered book.");
        var r=service(s).state().bookDelivery().keepRequests().get(bookId);
        if(action.equals("stop")){if(r==null||!r.lootTableId().equals(tableId))throw new IllegalStateException("Review the current request.");service(s).stopKeep(bookId,now());return "Keeping stopped. Existing books and history retained.";}
        if(!TypedChestLoot.types(s).containsKey(tableId))throw new IllegalArgumentException("Selected loot table unavailable; restore it or Stop keeping.");
        if(action.equals("review")){if(r==null||!r.lootTableId().equals(tableId))throw new IllegalStateException("Review the current request.");suspendIfChanged(s,r,book);service(s).reviewKeep(bookId,ruleFingerprint(book),book.rule(),override,now());}
        else service(s).startKeep(bookId,tableId,ruleFingerprint(book),book.rule(),override,now());
        int session=ChronicleRuntime.service(s).state().sessionNumber();var schedule=service(s).state().schedule();var currentCycle=schedule.types().get(tableId);
        long generation=schedule.sessionNumber()==session&&currentCycle!=null?currentCycle.generation():-1;
        attemptKeep(s,service(s).state().bookDelivery().keepRequests().get(bookId),session,generation,false);
        if(generation>=0)service(s).saveKeepCycle(bookId,session,generation,now());
        return "Keep placed until found enabled. "+keepForm(s,bookId,tableId).status();
    }
    private static void suspendIfChanged(MinecraftServer s,BookKeepRequest r,BookDefinition b)throws IOException{
        if(r.active()&&needsReview(s,r,b))service(s).suspendKeep(b.id(),r.ruleFingerprint().equals(ruleFingerprint(b))?BookKeepRequest.Status.SUSPENDED_ONCE:BookKeepRequest.Status.SUSPENDED_RULE,now());
    }
    /** Called only from the existing typed-loot batch opportunity. At most one inventory transaction per batch. */
    public static boolean reconcile(MinecraftServer s,LootSchedule schedule)throws IOException{
        var requests=List.copyOf(service(s).state().bookDelivery().keepRequests().values());if(requests.isEmpty())return false;
        int cursor=CURSORS.getOrDefault(s,0);
        for(int checked=0;checked<Math.min(4,requests.size());checked++){
            var r=requests.get(Math.floorMod(cursor++,requests.size()));if(!r.active())continue;
            var cycle=schedule.types().get(r.lootTableId());if(cycle==null){problem(s,r,"Waiting - selected loot table unavailable; restore it or Stop keeping");continue;}
            if(r.lastSessionNumber()==schedule.sessionNumber()&&r.lastCycle()>=cycle.generation())continue;
            CURSORS.put(s,cursor);boolean changed=attemptKeep(s,r,schedule.sessionNumber(),cycle.generation(),true);
            var current=service(s).state().bookDelivery().keepRequests().get(r.bookId());
            if(current.active())service(s).saveKeepCycle(r.bookId(),schedule.sessionNumber(),cycle.generation(),now());
            if(changed)return true;
        }
        CURSORS.put(s,cursor);return false;
    }
    private static boolean attemptKeep(MinecraftServer s,BookKeepRequest request,int session,long cycle,boolean relocate)throws IOException{
        var b=BookRuntime.books(s).get(request.bookId());if(b==null){problem(s,request,"Waiting - book definition unavailable");return false;}
        suspendIfChanged(s,request,b);request=service(s).state().bookDelivery().keepRequests().get(b.id());if(!request.active())return false;
        var delivery=service(s).state().bookDelivery().deliveries().get(b.id());if(delivery!=null&&delivery.firstDiscovery()!=null)return false;
        String requestTable=request.lootTableId();var candidates=service(s).state().bindings().values().stream().filter(c->c.typed()&&c.lootType().equals(requestTable)).toList();
        ChestBinding source=null;ChestAuthoring.ResolvedTarget sourceTarget=null;InventorySnapshot sourceBefore=null;int sourceSlot=-1;
        try{
            if(request.location()!=null&&request.location().state()!=BookCopyLocation.State.ABSENT){
                var loc=request.location();source=service(s).state().bindings().get(loc.bindingId());
                if(source==null||!source.typed()||!source.lootType().equals(request.lootTableId())||!samePhysical(source.directTarget(),loc.target())){problem(s,request,"Waiting - known source binding changed; restore it or stop keeping");return false;}
                sourceTarget=ChestAuthoring.resolve(s,loc.target());sourceBefore=ChestAuthoring.snapshot(s,sourceTarget.inventory());sourceSlot=find(s,sourceTarget.inventory(),b.id());
                if(sourceSlot<0){problem(s,request,"Waiting - known copy is absent; awaiting inventory discovery or reconciliation");return false;}
                if(source.revision()!=loc.bindingRevision()||loc.state()!=BookCopyLocation.State.PRESENT)service(s).reconcileKeepLocation(b.id(),new BookCopyLocation(source.id(),source.revision(),source.directTarget(),BookCopyLocation.State.PRESENT),now());
            }else{
                boolean unresolved=false;
                for(var c:candidates){try{var t=ChestAuthoring.resolve(s,c.directTarget());int slot=find(s,t.inventory(),b.id());if(slot>=0){source=c;sourceTarget=t;sourceBefore=ChestAuthoring.snapshot(s,t.inventory());sourceSlot=slot;service(s).reconcileKeepLocation(b.id(),new BookCopyLocation(c.id(),c.revision(),c.directTarget(),BookCopyLocation.State.PRESENT),now());break;}}catch(IllegalArgumentException|IllegalStateException ignored){unresolved=true;}}
                if(source==null&&unresolved&&delivery!=null&&delivery.issued()){problem(s,request,"Waiting - unresolved table chests may hold the existing copy");return false;}
            }
            ChestBinding destination=null;ChestAuthoring.ResolvedTarget destTarget=null;
            int start=source==null?0:candidates.indexOf(source)+1;
            for(int i=0;i<candidates.size();i++){var c=candidates.get(Math.floorMod(start+i,candidates.size()));if(source!=null&&c.id().equals(source.id()))continue;
                try{var t=ChestAuthoring.resolve(s,c.directTarget());if(find(s,t.inventory(),b.id())<0&&emptySlot(t.inventory())>=0){destination=c;destTarget=t;break;}}catch(IllegalArgumentException|IllegalStateException ignored){}
            }
            if(source!=null&&(!relocate||destination==null)){
                if(cycle>=0)service(s).saveKeepCycle(b.id(),session,cycle,now());problem(s,request,"Kept available until found");return false;
            }
            if(destination==null){problem(s,request,"Waiting - no loaded bound chest with room uses this table");return false;}
            ChestAuthoring.requireSavingEnabled(destTarget);if(sourceTarget!=null)ChestAuthoring.requireSavingEnabled(sourceTarget);
            InventorySnapshot destBefore=ChestAuthoring.snapshot(s,destTarget.inventory());var after=new ArrayList<>(destBefore.stacks());
            after.set(emptySlot(destTarget.inventory()),source==null?stackJson(s,b):sourceBefore.stacks().get(sourceSlot));
            InventorySnapshot sourceAfter=null;if(source!=null){var entries=new ArrayList<>(sourceBefore.stacks());entries.set(sourceSlot,"{}");sourceAfter=new InventorySnapshot(sourceBefore.size(),entries);}
            var op=new PendingChestOperation(UUID.randomUUID().toString(),SYSTEM,"Chronicle kept book",destination.id(),destination.revision(),session,cycle,destination.directTarget(),destBefore,new InventorySnapshot(destBefore.size(),after),now(),source==null?PendingChestOperation.Purpose.INSERT:PendingChestOperation.Purpose.MOVE,source==null?null:source.id(),source==null?0:source.revision(),source==null?null:source.directTarget(),sourceBefore,sourceAfter,List.of(new BookDeliveryClaim(b.id(),service(s).deliveryToken(b.id()),true)));
            execute(s,op,destTarget,sourceTarget,b.rule());problem(s,request,"Kept available until found");return true;
        }catch(IllegalArgumentException|IllegalStateException e){if(!service(s).state().pending().isEmpty())throw e;problem(s,request,"Waiting - "+e.getMessage());return false;}
    }
    private static void execute(MinecraftServer s,PendingChestOperation op,ChestAuthoring.ResolvedTarget dest,ChestAuthoring.ResolvedTarget source,BookDefinition.Rule rule)throws IOException{
        var stacks=ChestAuthoring.validateForInventory(s,op.after(),dest.inventory());var sourceStacks=source==null?null:ChestAuthoring.validateForInventory(s,op.sourceAfter(),source.inventory());
        service(s).beginLore(op,Map.of(op.bookClaims().getFirst().bookId(),rule),true);ChestAuthoring.refreshHold(s);
        if(source!=null){ChestAuthoring.closeViewers(s,source.inventory());for(int i=0;i<sourceStacks.size();i++)source.inventory().setStack(i,sourceStacks.get(i).copy());source.inventory().markDirty();}
        ChestAuthoring.closeViewers(s,dest.inventory());for(int i=0;i<stacks.size();i++)dest.inventory().setStack(i,stacks.get(i).copy());dest.inventory().markDirty();
        if(!s.saveAll(true,true,true))throw new IOException("Minecraft did not confirm a kept-book transfer save");service(s).complete(op.operationId(),now());ChestAuthoring.refreshHold(s);
    }
    private static boolean samePhysical(ChestTarget a,ChestTarget b){return a.dimension().equals(b.dimension())&&a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z()&&a.expectedBlock().equals(b.expectedBlock());}
    private static int find(MinecraftServer s,net.minecraft.inventory.Inventory inv,String book){for(int i=0;i<inv.size();i++){var t=authentic(s,inv.getStack(i));if(t!=null&&t.book.equals(book))return i;}return -1;}
    private static int emptySlot(net.minecraft.inventory.Inventory inv){for(int i=0;i<inv.size();i++)if(inv.getStack(i).isEmpty())return i;return -1;}
    private static void problem(MinecraftServer s,BookKeepRequest r,String message){PROBLEMS.computeIfAbsent(s,k->new HashMap<>()).put(r.bookId(),message.length()>450?message.substring(0,450):message);}
}
