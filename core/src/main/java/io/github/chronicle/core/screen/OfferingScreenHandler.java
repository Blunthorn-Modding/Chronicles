package io.github.chronicle.core.screen;

import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.server.LastingBargains;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Real player slots, no station storage, inputs, fake outputs or transaction-on-slot-click. */
public final class OfferingScreenHandler extends ScreenHandler {
    public static final int WIDTH=300, HEIGHT=222, INVENTORY_X=70, INVENTORY_Y=140, HOTBAR_Y=198;
    public static final ExtendedScreenHandlerType<OfferingScreenHandler, OfferingPayloads.View> TYPE = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of("chronicle_core", "offering"),
            new ExtendedScreenHandlerType<>(OfferingScreenHandler::new, OfferingPayloads.View.CODEC));
    private OfferingPayloads.View view;
    public static void register() { }
    public OfferingScreenHandler(int syncId, PlayerInventory inventory, OfferingPayloads.View view) {
        super(TYPE, syncId); view(view);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,INVENTORY_X+18*col,INVENTORY_Y+18*row));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,INVENTORY_X+18*col,HOTBAR_Y));
    }
    public OfferingPayloads.View view() { return view; }
    public void view(OfferingPayloads.View next) {
        if(next.syncId()!=syncId)throw new IllegalArgumentException("Offering screen identity changed");
        view=next;
    }
    @Override public boolean canUse(PlayerEntity player) {
        return !(player instanceof ServerPlayerEntity actor) || LastingBargains.canUse(actor,this);
    }
    @Override public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if(player instanceof ServerPlayerEntity actor)LastingBargains.closed(actor,this);
    }
    @Override public ItemStack quickMove(PlayerEntity player,int index) {
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),copy=stack.copy();
        if(index<27?!insertItem(stack,27,36,false):!insertItem(stack,0,27,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();
        slot.onTakeItem(player,stack);return copy;
    }
}
