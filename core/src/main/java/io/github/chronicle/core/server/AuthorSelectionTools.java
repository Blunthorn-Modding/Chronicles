package io.github.chronicle.core.server;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.UUID;

/** Temporary selection tools never overwrite items, drop items, or authorize themselves. */
public final class AuthorSelectionTools {
    private static final java.util.Map<ServerPlayerEntity,java.util.Set<Lease>> ACTIVE=new java.util.IdentityHashMap<>();
    static final class Lease {
        final UUID token; final String tag; final int previousSelected,toolSlot,displacedSlot; final ItemStack original;
        Lease(UUID token,String tag,int previous,int slot,int displaced,ItemStack original){this.token=token;this.tag=tag;previousSelected=previous;toolSlot=slot;displacedSlot=displaced;this.original=original;}
    }
    private AuthorSelectionTools(){}
    public static void register(){
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->{for(var lease:java.util.List.copyOf(ACTIVE.getOrDefault(oldPlayer,java.util.Set.of()))){release(newPlayer,lease);release(oldPlayer,lease);}RegionAuthoringWorkflow.resumeTool(newPlayer);AreaChanges.resumeTool(newPlayer);});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var player:server.getPlayerManager().getPlayerList())for(var lease:java.util.List.copyOf(ACTIVE.getOrDefault(player,java.util.Set.of())))release(player,lease);});
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)->{if(entity instanceof ServerPlayerEntity player)for(var lease:java.util.List.copyOf(ACTIVE.getOrDefault(player,java.util.Set.of())))release(player,lease);return true;});
    }
    static Lease issue(ServerPlayerEntity player,UUID token,String tag,String name){
        int previous=player.getInventory().getSelectedSlot(),slot=-1,displaced=-1;ItemStack original=null;
        for(int i=0;i<9;i++)if(player.getInventory().getStack(i).isEmpty()){slot=i;break;}
        if(slot<0){for(int i=9;i<36;i++)if(player.getInventory().getStack(i).isEmpty()){displaced=i;break;}if(displaced<0)return null;original=player.getInventory().getStack(previous).copy();player.getInventory().setStack(displaced,original.copy());player.getInventory().setStack(previous,ItemStack.EMPTY);slot=previous;}
        ItemStack tool=new ItemStack(Items.WOODEN_SHOVEL);NbtCompound data=new NbtCompound();data.putString(tag,token.toString());tool.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));tool.set(DataComponentTypes.CUSTOM_NAME,Text.literal(name));player.getInventory().setStack(slot,tool);player.getInventory().setSelectedSlot(slot);player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();Lease lease=new Lease(token,tag,previous,slot,displaced,original);ACTIVE.computeIfAbsent(player,p->new java.util.HashSet<>()).add(lease);return lease;
    }
    static boolean live(ServerPlayerEntity player,Lease lease){return lease!=null&&ACTIVE.getOrDefault(player,java.util.Set.of()).contains(lease);}
    static String token(ItemStack stack,String tag){NbtComponent data=stack.get(DataComponentTypes.CUSTOM_DATA);return data==null?null:data.copyNbt().getString(tag).orElse(null);}
    static void release(ServerPlayerEntity player,Lease lease){
        if(lease==null)return;var active=ACTIVE.get(player);if(active!=null){active.remove(lease);if(active.isEmpty())ACTIVE.remove(player);}
        for(int i=0;i<player.getInventory().size();i++)if(matches(player.getInventory().getStack(i),lease))player.getInventory().setStack(i,ItemStack.EMPTY);
        if(matches(player.currentScreenHandler.getCursorStack(),lease))player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        if(lease.original!=null&&lease.displacedSlot>=0&&player.getInventory().getStack(lease.toolSlot).isEmpty()&&ItemStack.areEqual(player.getInventory().getStack(lease.displacedSlot),lease.original)){player.getInventory().setStack(lease.toolSlot,lease.original.copy());player.getInventory().setStack(lease.displacedSlot,ItemStack.EMPTY);}
        if(player.getInventory().getSelectedSlot()==lease.toolSlot)player.getInventory().setSelectedSlot(lease.previousSelected);player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
    }
    private static boolean matches(ItemStack stack,Lease lease){return lease.token.toString().equals(token(stack,lease.tag));}
    public static boolean active(ServerPlayerEntity player,ItemStack stack){return ACTIVE.getOrDefault(player,java.util.Set.of()).stream().anyMatch(l->matches(stack,l));}
    public static boolean activeHand(ServerPlayerEntity player){return active(player,player.getMainHandStack())||active(player,player.getOffHandStack());}
    public static boolean rejectClick(ServerPlayerEntity player,net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet){
        var screen=player.currentScreenHandler;int slot=packet.slot();if(active(player,screen.getCursorStack())||slot>=0&&slot<screen.slots.size()&&active(player,screen.getSlot(slot).getStack()))return true;
        return packet.actionType()==net.minecraft.screen.slot.SlotActionType.SWAP&&(packet.button()>=0&&packet.button()<9||packet.button()==40)&&active(player,player.getInventory().getStack(packet.button()));
    }
    public static boolean rejectCreative(ServerPlayerEntity player,net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket packet){int slot=packet.slot();return active(player,packet.stack())||slot>=0&&slot<player.playerScreenHandler.slots.size()&&active(player,player.playerScreenHandler.getSlot(slot).getStack());}
    public static boolean rejectAction(ServerPlayerEntity player,net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket packet){return switch(packet.getAction()){case DROP_ITEM,DROP_ALL_ITEMS->active(player,player.getMainHandStack());case SWAP_ITEM_WITH_OFFHAND->activeHand(player);default->false;};}
}
