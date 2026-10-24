package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.SessionGameplayPolicy;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.CandleCakeBlock;

/** Shared server-authoritative gameplay boundary; no gamemode or operator bypass for survival. */
public final class SessionGameplay {
    private SessionGameplay() { }
    public static boolean frozen(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Session gameplay requires the server thread.");
        var service = ChronicleRuntime.available(server);
        return SessionGameplayPolicy.frozen(service == null ? null : service.state().phase(), RuntimeHolds.reason(server) != null);
    }
    /** Map work is explicit, server-owned and rechecked against current operator authority. */
    public static boolean canBuild(ServerPlayerEntity player) {
        var server = player.getEntityWorld().getServer();
        if (!server.isOnThread()) throw new IllegalStateException("Session gameplay requires the server thread.");
        var service = ChronicleRuntime.available(server);
        return SessionGameplayPolicy.canBuild(service == null ? null : service.state().phase(),
                RuntimeHolds.reason(server) != null, player.getCommandSource().hasPermissionLevel(2),
                AuthoringRuntime.isAuthor(player));
    }
    public static boolean frozen(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity actor) return frozen(actor.getEntityWorld().getServer());
        return player.getEntityWorld().isClient() && player.getEntityWorld().getTickManager().isFrozen();
    }
    public static boolean canUseItem(ServerPlayerEntity player, ItemStack stack) {
        return canBuild(player) && (!frozen(player) || !stack.contains(DataComponentTypes.CONSUMABLE));
    }
    public static boolean canUseBlock(ServerPlayerEntity player, ItemStack stack, BlockPos pos) {
        if (!canUseItem(player, stack)) return false;
        if (!frozen(player)) return true;
        var chunk = player.getEntityWorld().getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        var block = chunk.getBlockState(pos).getBlock();
        return !(block instanceof CakeBlock) && !(block instanceof CandleCakeBlock);
    }
    public static void correctInventory(ServerPlayerEntity player) {
        player.playerScreenHandler.syncState();
        if (player.currentScreenHandler != player.playerScreenHandler) player.currentScreenHandler.syncState();
    }
    /** Packet targets are untrusted: never acquire a chunk to reject a prediction. */
    public static void correctBlock(ServerPlayerEntity player, BlockPos pos) {
        var world = player.getEntityWorld();
        var chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && !world.isOutOfHeightLimit(pos))
            player.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, chunk.getBlockState(pos)));
    }
}
