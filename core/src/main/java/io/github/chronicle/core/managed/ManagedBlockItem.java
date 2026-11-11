package io.github.chronicle.core.managed;

import io.github.chronicle.core.server.ManagedBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** BlockItem seam that durably reserves managed capacity before vanilla writes or consumes. */
public final class ManagedBlockItem extends BlockItem {
    private final String managedTypeId;

    public ManagedBlockItem(Block block, Item.Settings settings, String managedTypeId) {
        super(block, settings);
        this.managedTypeId = managedTypeId;
    }

    @Override
    protected boolean place(ItemPlacementContext context, BlockState state) {
        if (!(context.getWorld() instanceof ServerWorld world)) return super.place(context, state);
        ManagedBlocks.ReservationTicket ticket;
        try { ticket = ManagedBlocks.reservePlacement(world, context.getBlockPos(), managedTypeId); }
        catch (IllegalArgumentException | IllegalStateException exception) {
            if (context.getPlayer() instanceof ServerPlayerEntity player) {
                player.sendMessage(Text.literal(exception.getMessage() == null ? "Managed block placement was denied." : exception.getMessage()), true);
                // Vanilla predicted a successful item use on the client. The server's
                // unchanged stack would otherwise evade ordinary dirty-slot syncing.
                player.playerScreenHandler.syncState();
            }
            return false;
        }
        boolean placed;
        try { placed = super.place(context, state); }
        catch (RuntimeException exception) { ManagedBlocks.placementUncertain(ticket); throw exception; }
        if (placed) ManagedBlocks.placementSucceeded(ticket); else {
            ManagedBlocks.placementFailed(ticket);
            if (context.getPlayer() instanceof ServerPlayerEntity player) player.playerScreenHandler.syncState();
        }
        return placed;
    }
}
