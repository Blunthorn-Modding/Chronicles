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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Slot-free native opening/close identity for the compact offering screen. */
public final class OfferingScreenHandler extends ScreenHandler {
    public static final int WIDTH=224, HEIGHT=132;
    public static final ExtendedScreenHandlerType<OfferingScreenHandler, OfferingPayloads.View> TYPE = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of("chronicle_core", "offering"),
            new ExtendedScreenHandlerType<>(OfferingScreenHandler::new, OfferingPayloads.View.CODEC));
    private OfferingPayloads.View view;
    public static void register() { }
    public OfferingScreenHandler(int syncId, PlayerInventory inventory, OfferingPayloads.View view) {
        super(TYPE, syncId); view(view);
        // This native handler binds authority only; offerings have no inventory or slots.
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
    @Override public ItemStack quickMove(PlayerEntity player,int index) { return ItemStack.EMPTY; }
}
