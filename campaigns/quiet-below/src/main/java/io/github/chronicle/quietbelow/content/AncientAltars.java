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
import net.minecraft.util.Hand;
import java.util.List;

/** Campaign defaults and physical power only. Core owns consent, durable receipts and native stats. */
public final class AncientAltars {
    public static final String SOURCE = "quiet_below:network", GROUP = "quiet_below:altar";
    public static final OfferingDefinition EXTRA_HEART = new OfferingDefinition("quiet_below:extra_heart", new OfferingTerms(2, 0, 0, 25), 3);
    public static final OfferingDefinition MOVEMENT = new OfferingDefinition("quiet_below:movement", new OfferingTerms(-2, 0, 10), 3);
    private AncientAltars() { }
    public static void register() {
        TemporaryAltarOfferings.register();
        LastingBargains.registerGroup(GROUP,List.of(
                new LastingBargains.Choice(EXTRA_HEART,"Pray for more life","The altar listens."),
                new LastingBargains.Choice(MOVEMENT,"Pray for quicker feet","The altar listens."),
                new LastingBargains.Choice(TemporaryAltarOfferings.REPAIR,"Pray for swift mending","The altar listens."),
                new LastingBargains.Choice(TemporaryAltarOfferings.SIGHT,"Pray for clear sight","The altar listens."),
                new LastingBargains.Choice(TemporaryAltarOfferings.HARVEST,"Pray for a quiet harvest","The altar listens."),
                new LastingBargains.Choice(TemporaryAltarOfferings.EMERGENCY,"Pray for sheltering grace","The altar listens.")),
                new io.github.chronicle.core.network.OfferingPayloads.Presentation("The altar listens...","The altar is quiet.","The altar listens.","One answer has faded.","No answer comes.","The altar falls silent.","The altar remembers."),
                io.github.chronicle.core.network.OfferingPayloads.Layout.ALTAR_TWO_SHEET);
    }
    private static AncientAltarBlockEntity physical(ServerWorld world, BlockPos pos) {
        if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) return null;
        var chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getBlockState(pos).isOf(QuietBelow.ANCIENT_ALTAR_BLOCK)) return null;
        return chunk.getBlockEntities().get(pos) instanceof AncientAltarBlockEntity altar ? altar : null;
    }
    public static void open(ServerPlayerEntity player, BlockPos position) { open(player,position,Hand.MAIN_HAND); }
    public static void open(ServerPlayerEntity player, BlockPos position,Hand activationHand) {
        ServerWorld world = player.getEntityWorld(); BlockPos pos = position.toImmutable();
        AncientAltarBlockEntity altar = physical(world, pos); if (altar == null) return;
        LastingBargains.open(player, SOURCE, GROUP, "Ancient Altar", new LastingBargains.Gate() {
            @Override public boolean stillPresent(ServerPlayerEntity actor) {
                return actor.getEntityWorld()==world&&actor.isAlive()&&!actor.isSpectator()
                        &&physical(world,pos)==altar&&!altar.isRemoved();
            }
            @Override public io.github.chronicle.core.server.TemporaryOfferings.Station station(ServerPlayerEntity actor) { return TemporaryAltarOfferings.station(world,pos,activationHand); }
            @Override public String unavailable(ServerPlayerEntity actor) {
                if (actor.getEntityWorld() != world || !actor.isAlive() || actor.isSpectator() || actor.squaredDistanceTo(pos.toCenterPos()) > 36) return "The Altar is out of reach.";
                if (physical(world, pos) != altar || altar.isRemoved()) return "The original Altar is no longer here.";
                if (actor.isCreative()) return "Creative inspection only; use Survival or Adventure to accept a bargain.";
                if (!PlayRuntime.eligiblePlayers(world.getServer()).contains(actor)) return "Join participation and leave editing mode before accepting a bargain.";
                return QuietBelowEcology.stationPower(world, pos);
            }
            @Override public void committed(ServerPlayerEntity actor,OfferingDefinition definition,java.util.UUID transaction) {
                if(definition.id().equals(EXTRA_HEART.id())) {
                    actor.setHealth(1);
                    actor.getHungerManager().setFoodLevel(0);
                    actor.getHungerManager().setSaturationLevel(0);
                    actor.sendMessage(net.minecraft.text.Text.literal("You prayed, and the sculk answered. A lasting boon for a little life force... The sculk around you pulses hungrily for more."),false);
                }else if(definition.id().equals(MOVEMENT.id())) {
                    actor.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS,9600,0));
                    actor.sendMessage(net.minecraft.text.Text.literal("You prayed, and the sculk answered. Your feet feel light, then leaden. The sculk lingers in your steps."),false);
                }
                world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.8f, 0.65f);
            }
        });
    }
    static boolean isStation(ServerWorld world,BlockPos position,String stationId){AncientAltarBlockEntity altar=physical(world,position);return altar!=null&&altar.stationId().toString().equals(stationId);}
}
