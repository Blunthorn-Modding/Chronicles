package io.github.chronicle.quietbelow.content;

import io.github.chronicle.core.offering.OfferingDefinition;
import io.github.chronicle.core.offering.OfferingTerms;
import io.github.chronicle.core.server.LastingBargains;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.ecology.QuietBelowEcology;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Campaign defaults and physical power only. Core owns consent, durable receipts and native stats. */
public final class AncientAltars {
    public static final String SOURCE = "quiet_below:network", GROUP = "quiet_below:altar";
    public static final OfferingDefinition EXTRA_HEART = new OfferingDefinition("quiet_below:extra_heart", new OfferingTerms(2, 2, 0), 3);
    public static final OfferingDefinition MOVEMENT = new OfferingDefinition("quiet_below:movement", new OfferingTerms(-2, 0, 10), 3);
    private AncientAltars() { }
    public static void register() {
        LastingBargains.registerGroup(GROUP, List.of(
                new LastingBargains.Choice(EXTRA_HEART, "Extra heart",
                        "Bonus: one maximum heart. Sacrifice: one more food icon to fill; natural regeneration requires the larger food threshold. No immediate healing or food refill."),
                new LastingBargains.Choice(MOVEMENT, "Faster movement",
                        "Bonus: +10% base movement speed per purchase. Sacrifice: one maximum heart per purchase. Existing health is clamped to the resulting maximum.")));
    }
    private static AncientAltarBlockEntity physical(ServerWorld world, BlockPos pos) {
        if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) return null;
        var chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getBlockState(pos).isOf(QuietBelow.ANCIENT_ALTAR_BLOCK)) return null;
        return chunk.getBlockEntities().get(pos) instanceof AncientAltarBlockEntity altar ? altar : null;
    }
    public static void open(ServerPlayerEntity player, BlockPos position) {
        ServerWorld world = player.getEntityWorld(); BlockPos pos = position.toImmutable();
        AncientAltarBlockEntity altar = physical(world, pos); if (altar == null) return;
        LastingBargains.open(player, SOURCE, GROUP, "Ancient Altar", new LastingBargains.Gate() {
            @Override public String unavailable(ServerPlayerEntity actor) {
                if (actor.getEntityWorld() != world || !actor.isAlive() || actor.isSpectator() || actor.squaredDistanceTo(pos.toCenterPos()) > 36) return "The Altar is out of reach.";
                if (physical(world, pos) != altar || altar.isRemoved()) return "The original Altar is no longer here.";
                if (actor.isCreative()) return "Creative inspection only; use Survival or Adventure to accept a bargain.";
                if (!PlayRuntime.eligiblePlayers(world.getServer()).contains(actor)) return "Join participation and leave editing mode before accepting a bargain.";
                return QuietBelowEcology.stationPower(world, pos);
            }
            @Override public void completed(ServerPlayerEntity actor) {
                world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.8f, 0.65f);
            }
        });
    }
}
