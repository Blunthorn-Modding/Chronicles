package io.github.chronicle.core.screen;

import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.server.ItemEnchantingRuntime;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** One visible entry, one real owned output and the native player inventory. */
public final class ItemEnchantingScreenHandler extends ScreenHandler {
    public static final int WIDTH=224,HEIGHT=220;
    public static final int ENTRY_SLOT=0,OUTPUT_SLOT=1,PLAYER_START=2,PLAYER_END=38;
    public static final int ENTRY_X=105,ENTRY_Y=29,OUTPUT_X=105,OUTPUT_Y=108;
    public static final int INVENTORY_X=32,INVENTORY_Y=136;
    public static final ExtendedScreenHandlerType<ItemEnchantingScreenHandler,ItemEnchantingPayloads.View> TYPE=Registry.register(
            Registries.SCREEN_HANDLER,Identifier.of("chronicle_core","item_workshop"),new ExtendedScreenHandlerType<>(ItemEnchantingScreenHandler::new,ItemEnchantingPayloads.View.CODEC));

    private final SimpleInventory entry=new SimpleInventory(1){@Override public void markDirty(){super.markDirty();changed();}};
    private final SimpleInventory output=new SimpleInventory(1){@Override public void markDirty(){super.markDirty();changed();}};
    private final PlayerInventory playerInventory;
    private final ServerPlayerEntity owner;
    private ItemEnchantingPayloads.View view;
    private boolean mutating,settled,clientPending;

    public static void register(){ }
    public ItemEnchantingScreenHandler(int syncId,PlayerInventory inventory,ItemEnchantingPayloads.View opening){
        super(TYPE,syncId);playerInventory=inventory;owner=inventory.player instanceof ServerPlayerEntity actor?actor:null;view=opening;
        if(opening.syncId()!=syncId)throw new IllegalArgumentException("Workshop opening identity mismatch");
        addSlot(new Slot(entry,0,ENTRY_X,ENTRY_Y){
            @Override public boolean canInsert(ItemStack stack){return canStage();}
            @Override public boolean canTakeItems(PlayerEntity player){return phase()!=ItemEnchantingPayloads.Phase.WORKING;}
        });
        addSlot(new Slot(output,0,OUTPUT_X,OUTPUT_Y){
            @Override public boolean canInsert(ItemStack stack){return false;}
            @Override public boolean canTakeItems(PlayerEntity player){return phase()==ItemEnchantingPayloads.Phase.RESULT;}
        });
        addPlayerSlots(inventory,INVENTORY_X,INVENTORY_Y);
    }

    public ServerPlayerEntity owner(){return owner;}
    public boolean settled(){return settled;}
    public ItemEnchantingPayloads.View view(){return view;}
    public ItemEnchantingPayloads.Phase phase(){return view.phase();}
    public void view(ItemEnchantingPayloads.View next){
        if(next.syncId()!=syncId||!next.sessionId().equals(view.sessionId()))throw new IllegalArgumentException("Workshop view identity mismatch");
        view=next;
    }
    public ItemStack entry(){return entry.getStack(0);}
    public ItemStack output(){return output.getStack(0);}
    public ItemStack takeEntry(){return entry.removeStack(0);}
    public void output(ItemStack stack){output.setStack(0,stack);}
    public void mutate(Runnable action){boolean previous=mutating;mutating=true;try{action.run();}finally{mutating=previous;}}
    public void clientPending(boolean pending){clientPending=pending;}

    private boolean canStage(){return !settled&&!clientPending&&view.available()&&(phase()==ItemEnchantingPayloads.Phase.EMPTY||phase()==ItemEnchantingPayloads.Phase.OFFERED)&&output().isEmpty();}
    private void changed(){if(owner!=null&&!mutating&&!settled)ItemEnchantingRuntime.changed(owner,this);}
    @Override public boolean canUse(PlayerEntity player){return !settled&&(owner==null||player==owner&&ItemEnchantingRuntime.canUse(owner,this));}

    @Override public void onSlotClick(int index,int button,SlotActionType type,PlayerEntity player){
        if(settled||owner!=null&&(player!=owner||!ItemEnchantingRuntime.canUse(owner,this)))return;
        if(index==ENTRY_SLOT&&(clientPending||phase()==ItemEnchantingPayloads.Phase.WORKING))return;
        if(index==OUTPUT_SLOT&&phase()!=ItemEnchantingPayloads.Phase.RESULT)return;
        mutate(()->super.onSlotClick(index,button,type,player));changed();
    }
    @Override public boolean canInsertIntoSlot(ItemStack stack,Slot slot){
        if(slot.id==OUTPUT_SLOT||slot.id==ENTRY_SLOT&&!canStage())return false;
        return super.canInsertIntoSlot(stack,slot);
    }
    @Override public boolean canInsertIntoSlot(Slot slot){
        if(slot.id==OUTPUT_SLOT||slot.id==ENTRY_SLOT&&!canStage())return false;
        return super.canInsertIntoSlot(slot);
    }
    @Override public ItemStack quickMove(PlayerEntity player,int index){
        if(owner!=null&&(player!=owner||!ItemEnchantingRuntime.canUse(owner,this)))return ItemStack.EMPTY;
        if(index==ENTRY_SLOT&&(clientPending||phase()==ItemEnchantingPayloads.Phase.WORKING)
                ||index==OUTPUT_SLOT&&phase()!=ItemEnchantingPayloads.Phase.RESULT)return ItemStack.EMPTY;
        ItemStack[] moved={ItemStack.EMPTY};mutate(()->moved[0]=move(player,index));changed();return moved[0];
    }
    private ItemStack move(PlayerEntity player,int index){
        if(settled||index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),copy=stack.copy();
        if(index==ENTRY_SLOT||index==OUTPUT_SLOT){if(!insertItem(stack,PLAYER_START,PLAYER_END,true))return ItemStack.EMPTY;}
        else if(canStage()&&!getSlot(ENTRY_SLOT).hasStack()&&insertItem(stack,ENTRY_SLOT,ENTRY_SLOT+1,false)){ }
        else if(index<29){if(!insertItem(stack,29,PLAYER_END,false))return ItemStack.EMPTY;}
        else if(!insertItem(stack,PLAYER_START,29,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();slot.onTakeItem(player,stack);return copy;
    }

    @Override public void onClosed(PlayerEntity player){
        if(settled||owner!=null&&player!=owner)return;settled=true;
        if(owner!=null){
            ItemEnchantingRuntime.closed(owner,this);
            mutate(()->{super.onClosed(player);dropInventory(player,entry);dropInventory(player,output);});
            playerInventory.markDirty();owner.playerScreenHandler.syncState();
        }else super.onClosed(player);
    }
}
