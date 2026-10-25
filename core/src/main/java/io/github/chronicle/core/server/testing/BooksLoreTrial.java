package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.books.BookKeepRequest;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.content.ChestTarget;
import io.github.chronicle.core.server.BookDeliveryRuntime;
import io.github.chronicle.core.server.ChestAuthoring;
import io.github.chronicle.core.server.ChronicleRuntime;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in, loopback-only CF18 receipt rehearsal; absent from production servers. */
public final class BooksLoreTrial {
    private static final BlockPos FIRST = new BlockPos(0, 64, 0), SECOND = new BlockPos(4, 64, 0), OTHER = new BlockPos(8, 64, 0);
    private static final String TABLE = "test:books", KEPT = "test:kept", ONCE = "test:once";
    private static final Map<net.minecraft.server.MinecraftServer, Set<String>> VISITED = new IdentityHashMap<>();
    private BooksLoreTrial() { }
    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_BOOKS_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("chronicle_books_trial").requires(s -> s.hasPermissionLevel(2));
            for (String verb : List.of("prepare", "keep", "cycles", "cursor", "full", "failed", "found", "uncertain", "occupied", "occupied_check", "gift_prepare", "gift_checks", "gift_empty", "gift_first", "gift_repeat", "restart"))
                root.then(literal(verb).executes(c -> run(c.getSource(), verb)));
            dispatcher.register(root);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                var status=io.github.chronicle.core.server.BookGifts.status(server,"test:gift");
                if(!status.found())return;
                var root=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
                String result=!status.finderOnline()&&status.finderName().equals("ChronicleFabricA")?"PASS":"FAIL: offline first-finder status was not retained";
                Files.writeString(root.resolve(".books-gift-offline"),result);
            } catch(Exception error){io.github.chronicle.core.ChronicleCore.LOGGER.error("Offline gift-status probe failed",error);}
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { var request = ChestAuthoring.service(server).state().bookDelivery().keepRequests().get(KEPT);
                if (request != null && request.location() != null) {
                    VISITED.computeIfAbsent(server, ignored -> new LinkedHashSet<>()).add(request.location().bindingId());
                    var root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
                    var temporary=root.resolve(".books-kept-position.tmp");
                    Files.writeString(temporary, request.location().target().x()+","+request.location().target().y()+","+request.location().target().z(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    Files.move(temporary,root.resolve(".books-kept-position"),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) { }
        });
    }
    private static int run(ServerCommandSource source, String verb) {
        try {
            fixture(source); ServerPlayerEntity player = source.getPlayerOrThrow();
            switch (verb) {
                case "prepare" -> prepare(source, player);
                case "keep" -> keep(source);
                case "cycles" -> cycles(source);
                case "cursor" -> cursor(source, player);
                case "full" -> full(player);
                case "failed" -> failed(source, player);
                case "found" -> found(source);
                case "uncertain" -> uncertain(source);
                case "occupied" -> occupied(source);
                case "occupied_check" -> occupiedCheck(source);
                case "gift_prepare" -> {player.closeHandledScreen();interval(source,10);for(int i=0;i<36;i++)player.getInventory().setStack(i,ItemStack.EMPTY);io.github.chronicle.core.server.PlayRuntime.setSelfParticipant(player,false);}
                case "gift_checks" -> giftChecks(source,player);
                case "gift_empty" -> {require(managedPlayer(player,"test:gift")==0,"cancel issued a book");require(!ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().containsKey("test:gift"),"cancel changed issuance");}
                case "gift_first" -> giftVerify(source,player,false);
                case "gift_repeat" -> giftVerify(source,player,true);
                case "restart" -> restart(source);
                default -> throw new IllegalArgumentException("unknown Books trial action");
            }
            source.sendFeedback(() -> Text.literal("Books trial PASS " + verb), false); return 1;
        } catch (Exception error) { source.sendError(Text.literal("Books trial FAIL " + verb + ": " + error.getMessage())); return 0; }
    }
    private static void prepare(ServerCommandSource source, ServerPlayerEntity player) throws Exception {
        var world = source.getWorld();
        for(int x=-4;x<=12;x++)for(int z=-4;z<=6;z++)world.setBlockState(new BlockPos(x,63,z),Blocks.STONE.getDefaultState(),3);
        for (BlockPos pos : List.of(FIRST, SECOND, OTHER)) { world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 3); world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 3); }
        var service = ChestAuthoring.service(source.getServer());
        service.putTypedBinding(TABLE, target(FIRST), false); service.putTypedBinding(TABLE, target(SECOND), false);
        service.putTypedBinding("test:other", target(OTHER), false);
        player.changeGameMode(GameMode.SURVIVAL);
        player.teleport(world, 2.5, 64, 2.5, java.util.Set.of(), 0, 0, false);player.setVelocity(0,0,0);player.onLanding();
        ChronicleRuntime.service(source.getServer()).start(new Actor("books-trial", null), null);
        require(!ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().containsKey(KEPT), "0% unkept book was issued before the keep request");
    }
    private static void keep(ServerCommandSource source) throws Exception {
        require(managed(source,FIRST,ONCE)+managed(source,SECOND,ONCE)==1,"Spawn once batch did not issue exactly one managed copy");
        var chest=inventory(source,FIRST);int first=-1,second=-1;for(int i=0;i<chest.size();i++)if(chest.getStack(i).isEmpty()){if(first<0)first=i;else{second=i;break;}}
        require(second>=0,"fixture chest has no space for ordinary preservation proof");
        chest.setStack(first,new ItemStack(Items.DIAMOND));chest.setStack(second,new ItemStack(Items.WRITTEN_BOOK));
        var before=new java.util.ArrayList<ItemStack>();for(int i=0;i<chest.size();i++)before.add(chest.getStack(i).copy());
        BookDeliveryRuntime.keep(source,KEPT,TABLE,false,"start");
        var request=ChestAuthoring.service(source.getServer()).state().bookDelivery().keepRequests().get(KEPT);
        require(request!=null&&request.active()&&request.location()!=null,"book-only keeping did not publish a location");
        for(int i=0;i<before.size();i++)if(!before.get(i).isEmpty())require(ItemStack.areEqual(before.get(i),chest.getStack(i)),"book-only insertion overwrote ordinary or raw book contents");
    }
    private static void cycles(ServerCommandSource source) throws Exception {
        var state = ChestAuthoring.service(source.getServer()).state();
        var request = state.bookDelivery().keepRequests().get(KEPT);
        require(request != null && request.active() && request.location() != null, "kept request did not survive scheduled opportunities");
        require(managed(source, FIRST, KEPT) + managed(source, SECOND, KEPT) == 1, "kept book is not exactly once in its two-chest table group");
        require(managed(source, OTHER, KEPT) == 0, "kept book reached a different-table chest");
        require(state.bookDelivery().deliveries().get(ONCE) != null && state.bookDelivery().deliveries().get(ONCE).issued(), "100% Spawn once was not issued");
        require(managed(source,FIRST,ONCE)+managed(source,SECOND,ONCE)==1,"scheduled refills lost or duplicated the Spawn once copy");
        require(request.lastCycle() >= 1 && VISITED.getOrDefault(source.getServer(), Set.of()).size() >= 2, "normal refill cycles did not relocate the maintained book across both same-table chests");
        interval(source,10); // Keep the inventory-interaction assertions clear of real scheduled refills.
    }
    private static void cursor(ServerCommandSource source, ServerPlayerEntity player) {
        require(marker(player.currentScreenHandler.getCursorStack(), KEPT), "client did not leave the kept book on the cursor: handler="+player.currentScreenHandler.getClass().getSimpleName()+", cursor="+player.currentScreenHandler.getCursorStack()+", phase="+ChronicleRuntime.service(source.getServer()).state().phase()+", hold="+io.github.chronicle.core.server.RuntimeHolds.reason(source.getServer()));
        var d = ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get(KEPT);
        require(d != null && d.firstDiscovery() == null, "cursor-only book falsely created a discovery receipt");
    }
    private static void full(ServerPlayerEntity player) { for (int i=0;i<36;i++) player.getInventory().setStack(i,new ItemStack(Items.COBBLESTONE,64)); player.currentScreenHandler.sendContentUpdates(); }
    private static void failed(ServerCommandSource source, ServerPlayerEntity player) {
        var d=ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get(KEPT);
        require(d!=null&&d.firstDiscovery()==null,"full-inventory transfer falsely found the book"); require(managed(source,FIRST,KEPT)+managed(source,SECOND,KEPT)==1,"failed quick move lost or duplicated kept book");
        for(int i=0;i<36;i++)player.getInventory().setStack(i,ItemStack.EMPTY);player.currentScreenHandler.sendContentUpdates();
    }
    private static void found(ServerCommandSource source) {
        var request = ChestAuthoring.service(source.getServer()).state().bookDelivery().keepRequests().get(KEPT);
        var delivery = ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get(KEPT);
        require(delivery != null && delivery.firstDiscovery() != null, "actual player inventory transfer did not persist a receipt");
        require(request != null && request.status() == BookKeepRequest.Status.COMPLETED_FOUND, "discovery did not complete keep request");
        var player=source.getPlayer();for(int slot=0;slot<player.getInventory().size();slot++)require(!marker(player.getInventory().getStack(slot),KEPT),"fleeting receipt test did not return the book out of the player inventory");
        require(managed(source,FIRST,KEPT)+managed(source,SECOND,KEPT)==1,"fleeting transfer did not return exactly one managed copy to its chest group");
    }
    private static void uncertain(ServerCommandSource source) throws Exception {
        source.getPlayerOrThrow().closeHandledScreen();var service=ChestAuthoring.service(source.getServer());
        BlockPos position=managed(source,FIRST,ONCE)>0?FIRST:SECOND;
        service.putTypedBinding(TABLE,new ChestTarget("fixture",1,"minecraft:overworld",position.getX(),position.getY(),position.getZ(),"minecraft:barrel"),true);
        try{
            BookDeliveryRuntime.keep(source,ONCE,TABLE,true,"start");
            var request=service.state().bookDelivery().keepRequests().get(ONCE);
            require(request.location()==null,"uncertain existing copy caused a duplicate insertion");
            require(service.state().pending().isEmpty(),"uncertain keep operation published an inventory intent");
            require(managed(source,FIRST,ONCE)+managed(source,SECOND,ONCE)==1,"uncertain keep duplicated the authentic existing copy");
            require(BookDeliveryRuntime.keepForm(source.getServer(),ONCE,TABLE).status().contains("unresolved"),"uncertain copy did not surface a waiting reason");
            BookDeliveryRuntime.keep(source,ONCE,TABLE,false,"stop");
        } finally {service.putTypedBinding(TABLE,target(position),true);}
    }
    private static void occupied(ServerCommandSource source) throws Exception {
        BlockPos origin=managed(source,FIRST,ONCE)>0?FIRST:SECOND,other=origin.equals(FIRST)?SECOND:FIRST;
        ItemStack copy=ItemStack.EMPTY;for(int i=0;i<inventory(source,origin).size();i++)if(marker(inventory(source,origin).getStack(i),ONCE))copy=inventory(source,origin).getStack(i).copy();
        require(!copy.isEmpty(),"missing occupied-destination source copy");boolean inserted=false;
        for(int i=0;i<inventory(source,other).size();i++)if(inventory(source,other).getStack(i).isEmpty()){inventory(source,other).setStack(i,copy);inventory(source,other).markDirty();inserted=true;break;}
        require(inserted,"no room for fixture's pre-existing second copy");interval(source,.05);BookDeliveryRuntime.keep(source,ONCE,TABLE,true,"start");
    }
    private static void occupiedCheck(ServerCommandSource source) throws Exception {
        require(managed(source,FIRST,ONCE)==1&&managed(source,SECOND,ONCE)==1,"keep moved onto a chest already holding an authentic copy");
        BookDeliveryRuntime.keep(source,ONCE,TABLE,false,"stop");
    }
    private static void interval(ServerCommandSource source,double minutes) throws Exception {
        var server=source.getServer();var actor=new Actor("books-trial",null);var player=source.getPlayerOrThrow();
        ChronicleRuntime.service(server).pause(actor);var author=io.github.chronicle.core.server.AuthoringRuntime.get(server);author.enable(player.getUuid());
        try {var old=io.github.chronicle.core.server.TypedChestLoot.types(server).get(TABLE);
            io.github.chronicle.core.server.TypedChestLoot.saveType(source,new io.github.chronicle.core.loot.LootType(old.id(),old.label(),old.description(),old.recommendedChests(),minutes,old.entries(),old.books()),old);
        } finally {author.disable(player.getUuid());ChronicleRuntime.service(server).resume(actor);}
    }
    private static void giftChecks(ServerCommandSource source,ServerPlayerEntity player) throws Exception {
        var server=source.getServer();var book=io.github.chronicle.core.server.BookRuntime.books(server).get("test:gift");
        io.github.chronicle.core.server.PlayRuntime.setSelfParticipant(player,true);
        full(player);var before=ChestAuthoring.service(server).state();var audit=ChronicleRuntime.service(server).state();
        try{io.github.chronicle.core.server.BookGifts.give(source,book,player.getUuidAsString());throw new IllegalStateException("full inventory accepted a book");}catch(IllegalStateException e){require(e.getMessage().contains("empty inventory slot"),e.getMessage());}
        require(before.equals(ChestAuthoring.service(server).state())&&audit.equals(ChronicleRuntime.service(server).state()),"full rejection changed delivery or audit");
        for(int i=0;i<36;i++)player.getInventory().setStack(i,ItemStack.EMPTY);
        var actor=new Actor("books-trial",null);ChronicleRuntime.service(server).pause(actor);
        try{io.github.chronicle.core.server.BookGifts.give(source,book,player.getUuidAsString());throw new IllegalStateException("paused give accepted");}catch(IllegalStateException e){require(e.getMessage().contains("active play"),e.getMessage());}finally{ChronicleRuntime.service(server).resume(actor);}
        io.github.chronicle.core.server.PlayRuntime.setSelfParticipant(player,false);
        try{io.github.chronicle.core.server.BookGifts.give(source,book,player.getUuidAsString());throw new IllegalStateException("nonparticipant accepted");}catch(IllegalStateException e){require(e.getMessage().contains("active participant"),e.getMessage());}
        io.github.chronicle.core.server.PlayRuntime.setSelfParticipant(player,true);player.currentScreenHandler.sendContentUpdates();
    }
    private static int managedPlayer(ServerPlayerEntity player,String book){int count=0;for(int i=0;i<36;i++)if(marker(player.getInventory().getStack(i),book))count+=player.getInventory().getStack(i).getCount();return count;}
    private static void giftVerify(ServerCommandSource source,ServerPlayerEntity player,boolean repeat) throws Exception {
        require(managedPlayer(player,"test:gift")==(repeat?2:1),"GM give did not insert exactly one copy");
        var d=ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get("test:gift");
        require(d!=null&&d.firstDiscovery()!=null&&d.firstDiscovery().playerUuid().equals(player.getUuidAsString()),"GM give did not record actual recipient as finder");
        var file=source.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent().resolve(".books-gift-receipt");
        if(repeat)require(Files.readString(file).equals(d.firstDiscovery().discoveredAt()),"replacement overwrote first discovery");else Files.writeString(file,d.firstDiscovery().discoveredAt());
    }
    private static void restart(ServerCommandSource source) {
        var delivery = ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get(KEPT);
        require(delivery != null && delivery.firstDiscovery() != null && delivery.issued(), "discovery/provenance was not retained after restart");
        var once = ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get(ONCE);
        require(once != null && once.issued(), "Spawn once issuance reset after restart");
        var gift=ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get("test:gift");
        require(gift!=null&&gift.firstDiscovery()!=null,"GM discovery did not survive restart");
        var receipt=source.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent().resolve(".books-gift-receipt");
        try{require(Files.readString(receipt).equals(gift.firstDiscovery().discoveredAt()),"GM discovery timestamp changed after restart");}catch(java.io.IOException e){throw new IllegalStateException(e);}

    }
    private static net.minecraft.inventory.Inventory inventory(ServerCommandSource source, BlockPos pos) {
        require(source.getWorld().getBlockEntity(pos) instanceof net.minecraft.inventory.Inventory, "missing fixture chest " + pos);
        return (net.minecraft.inventory.Inventory) source.getWorld().getBlockEntity(pos);
    }
    private static int managed(ServerCommandSource source, BlockPos pos, String bookId) {
        int count = 0; for (int i = 0; i < inventory(source, pos).size(); i++) {
            var data = inventory(source, pos).getStack(i).get(DataComponentTypes.CUSTOM_DATA);
            if (marker(inventory(source, pos).getStack(i), bookId)) count++;
        } return count;
    }
    private static boolean marker(ItemStack stack,String bookId) { var data=stack.get(DataComponentTypes.CUSTOM_DATA); return data!=null&&data.copyNbt().getCompound("chronicle_book").map(marker -> marker.getString("book").orElse("")).filter(bookId::equals).isPresent(); }
    private static ChestTarget target(BlockPos pos) { return new ChestTarget("books", 1, "minecraft:overworld", pos.getX(), pos.getY(), pos.getZ(), "minecraft:chest"); }
    private static void fixture(ServerCommandSource source) throws Exception {
        var server = source.getServer(); var root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root != null && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z") && Files.isRegularFile(root.resolve(".books-lore-fixture"))
                && server.isDedicated() && !server.isOnlineMode() && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp())
                && source.getWorld().getRegistryKey() == World.OVERWORLD, "unsafe Books trial fixture");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
