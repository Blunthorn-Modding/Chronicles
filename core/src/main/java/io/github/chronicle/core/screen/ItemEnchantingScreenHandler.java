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

/** Two real temporary inputs, a non-owning derived result and the native player inventory. */
public final class ItemEnchantingScreenHandler extends ScreenHandler {
    public static final int WIDTH=220;
    public static final int HEIGHT=212;
    public static final int TARGET_X=44;
    public static final int MATERIAL_X=88;
    public static final int RESULT_X=160;
    public static final int INPUT_Y=42;
    public static final int INVENTORY_X=29;
    public static final int INVENTORY_Y=130;
    public static final int PLAYER_START=3;
    public static final int PLAYER_END=39;
    public static final ExtendedScreenHandlerType<ItemEnchantingScreenHandler,ItemEnchantingPayloads.View> TYPE=Registry.register(
            Registries.SCREEN_HANDLER,Identifier.of("chronicle_core","item_workshop"),new ExtendedScreenHandlerType<>(ItemEnchantingScreenHandler::new,ItemEnchantingPayloads.View.CODEC));
    private final SimpleInventory inputs=new SimpleInventory(2){@Override public void markDirty(){super.markDirty();changed();}};
    private final SimpleInventory result=new SimpleInventory(1);
    private final PlayerInventory playerInventory;
    private final ServerPlayerEntity owner;
    private ItemEnchantingPayloads.View view;
    private boolean mutating,settled,clientPending;
    public static void register(){ }
    public ItemEnchantingScreenHandler(int syncId,PlayerInventory inventory,ItemEnchantingPayloads.View opening){
        super(TYPE,syncId);playerInventory=inventory;owner=inventory.player instanceof ServerPlayerEntity actor?actor:null;view=opening;
        if(opening.syncId()!=syncId)throw new IllegalArgumentException("Workshop opening identity mismatch");
        addSlot(new Slot(inputs,0,TARGET_X,INPUT_Y){@Override public int getMaxItemCount(){return 1;}});
        addSlot(new Slot(inputs,1,MATERIAL_X,INPUT_Y){@Override public boolean canInsert(ItemStack stack){return ingredient(stack);}});
        addSlot(new Slot(result,0,RESULT_X,INPUT_Y){
            @Override public boolean canInsert(ItemStack stack){return false;}
            @Override public boolean canTakeItems(PlayerEntity player){return owner==null&&view.state()==ItemEnchantingPayloads.State.READY&&!clientPending;}
        });
        addPlayerSlots(inventory,INVENTORY_X,INVENTORY_Y);
    }
    public ServerPlayerEntity owner(){return owner;}
    public boolean settled(){return settled;}
    public ItemEnchantingPayloads.View view(){return view;}
    public void view(ItemEnchantingPayloads.View next){
        if(next.syncId()!=syncId||!next.session().equals(view.session()))throw new IllegalArgumentException("Workshop view identity mismatch");
        view=next.recipes()==null?next.withRecipes(view.recipes()):next;
    }
    public ItemStack target(){return inputs.getStack(0);}
    public ItemStack material(){return inputs.getStack(1);}
    public void result(ItemStack stack){result.setStack(0,stack);}
    public void mutate(Runnable action){boolean previous=mutating;mutating=true;try{action.run();}finally{mutating=previous;}}
    public void consume(int count){inputs.setStack(0,ItemStack.EMPTY);ItemStack remainder=material().copy();remainder.decrement(count);inputs.setStack(1,remainder);}
    public void clientPending(boolean pending){clientPending=pending;}
    private void changed(){if(owner!=null&&!mutating&&!settled)ItemEnchantingRuntime.changed(owner,this);}
    private boolean ingredient(ItemStack stack){
        if(stack.isEmpty())return false;
        if(owner!=null)return ItemEnchantingRuntime.ingredient(owner,this,stack);
        String id=Registries.ITEM.getId(stack.getItem()).toString();return view.recipes()!=null&&view.recipes().stream().anyMatch(r->r.ingredient().equals(id));
    }
    @Override public boolean canUse(PlayerEntity player){return !settled&&(owner==null||player==owner&&ItemEnchantingRuntime.canUse(owner,this));}
    @Override public void onSlotClick(int index,int button,SlotActionType type,PlayerEntity player){
        if(settled||index==2||owner==null&&clientPending)return;
        if(owner!=null&&(player!=owner||!ItemEnchantingRuntime.canUse(owner,this)))return;
        mutate(()->super.onSlotClick(index,button,type,player));changed();
    }
    @Override public boolean canInsertIntoSlot(ItemStack stack,Slot slot){return slot.id!=2&&super.canInsertIntoSlot(stack,slot);}
    @Override public boolean canInsertIntoSlot(Slot slot){return slot.id!=2&&super.canInsertIntoSlot(slot);}
    @Override public ItemStack quickMove(PlayerEntity player,int index){
        if(owner!=null&&(player!=owner||!ItemEnchantingRuntime.canUse(owner,this)))return ItemStack.EMPTY;
        ItemStack[] moved={ItemStack.EMPTY};mutate(()->moved[0]=move(player,index));changed();return moved[0];
    }
    private ItemStack move(PlayerEntity player,int index){
        if(settled||index<0||index>=slots.size()||index==2)return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),copy=stack.copy();
        if(index<2){if(!insertItem(stack,PLAYER_START,PLAYER_END,true))return ItemStack.EMPTY;}
        else if(ingredient(stack)&&insertItem(stack,1,2,false)){ }
        else if(!getSlot(0).hasStack()&&insertItem(stack,0,1,false)){ }
        else if(index<30){if(!insertItem(stack,30,39,false))return ItemStack.EMPTY;}
        else if(!insertItem(stack,3,30,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();slot.onTakeItem(player,stack);return copy;
    }
    @Override public void onClosed(PlayerEntity player){
        if(settled||owner!=null&&player!=owner)return;settled=true;
        if(owner!=null){
            ItemEnchantingRuntime.closed(owner,this);
            mutate(()->{result.clear();super.onClosed(player);dropInventory(player,inputs);});
            playerInventory.markDirty();owner.playerScreenHandler.syncState();
        }else super.onClosed(player);
    }
}
