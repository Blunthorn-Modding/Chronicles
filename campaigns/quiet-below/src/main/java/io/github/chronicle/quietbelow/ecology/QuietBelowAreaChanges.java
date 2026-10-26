package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaCell;
import io.github.chronicle.core.server.AreaChanges;
import io.github.chronicle.core.server.AreaWorldPolicies;
import io.github.chronicle.quietbelow.QuietBelow;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Quiet Below's explicit Area-change boundary: stations are inert snapshots; the unique Heart is never copied. */
public final class QuietBelowAreaChanges {
    private QuietBelowAreaChanges() { }

    public static void register() {
        AreaChanges.registerCaptureAdapter(QuietBelow.MOD_ID, QuietBelowAreaChanges::capture);
        for(String id:java.util.List.of("minecraft:sculk_catalyst","minecraft:sculk_sensor","minecraft:sculk_shrieker"))
            AreaChanges.registerBlockCaptureAdapter(id,(server,world,pos,state,entity)->{
                if(!(state.getBlock() instanceof net.minecraft.block.BlockEntityProvider provider))throw new IllegalStateException("Invalid sculk feature adapter.");
                BlockEntity fresh=provider.createBlockEntity(pos,EcologyFeatureActivity.quietState(state));
                if(fresh==null)throw new IllegalStateException("Cannot create an inert sculk feature snapshot.");
                return new AreaChanges.CaptureData(fresh.createNbtWithIdentifyingData(server.getRegistryManager()).toString());
            });
        AreaWorldPolicies.register(QuietBelow.MOD_ID, new AreaWorldPolicies.Policy() {
            @Override public void validate(MinecraftServer server, AreaBounds bounds) { QuietBelowEcology.requireAreaOperation(server); }
            @Override public void validateCell(ServerWorld world, BlockPos pos, AreaCell before, AreaCell after) {
                QuietBelowEcology.requireAreaCell(world,pos);
                QuietBelowAreaChanges.validateCell(before, after,QuietBelowEcology.areaNetworkEnded(world));
            }
            @Override public void durable(MinecraftServer server, AreaBounds bounds) { QuietBelowEcology.flushAreaOperation(server); }
            @Override public BlockState templateState(ServerWorld world,BlockPos pos,BlockState state){
                if(state.contains(io.github.chronicle.quietbelow.content.AncientForgeBlock.HEAT))state=state.with(io.github.chronicle.quietbelow.content.AncientForgeBlock.HEAT,0);
                if(state.contains(io.github.chronicle.quietbelow.content.SculkAppearance.VITALITY))state=state.with(io.github.chronicle.quietbelow.content.SculkAppearance.VITALITY,EcologyAppearance.EXHAUSTED);
                return EcologyFeatureActivity.quietState(state);
            }
        });
    }

    private static AreaChanges.CaptureData capture(MinecraftServer server, ServerWorld world, BlockPos pos, BlockState state, BlockEntity entity) {
        Identifier id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
        if (state.isOf(QuietBelow.RELIC_HEART))
            throw new IllegalStateException("The Relic Heart is a unique campaign root and cannot be captured.");
        if (!state.isOf(QuietBelow.ANCIENT_FORGE_BLOCK) && !state.isOf(QuietBelow.ANCIENT_ALTAR_BLOCK))
            throw new IllegalStateException("Quiet Below block " + id + " is not approved for Area capture.");
        String nbt = entity == null ? null : entity.createNbtWithIdentifyingData(server.getRegistryManager()).toString();
        return new AreaChanges.CaptureData(nbt);
    }

    static void validateCell(AreaCell before, AreaCell after) {
        validateCell(before,after,false);
    }
    static void validateCell(AreaCell before,AreaCell after,boolean ended){
        if ((isHeart(before) || isHeart(after)) && !before.equals(after))
            throw new IllegalStateException("The Relic Heart is a unique campaign root and cannot be copied, removed, or changed by an Area operation.");
        if(ended&&isNetwork(after))throw new IllegalStateException("An Area state cannot restore sculk after the network has ended.");
    }
    private static boolean isNetwork(AreaCell cell){var j=JsonParser.parseString(cell.blockStateJson()).getAsJsonObject();String name=j.has("Name")?j.get("Name").getAsString():"";return java.util.Set.of("minecraft:sculk","minecraft:sculk_vein","minecraft:sculk_sensor","minecraft:calibrated_sculk_sensor","minecraft:sculk_catalyst","minecraft:sculk_shrieker").contains(name);}
    static boolean isHeart(AreaCell cell) {
        try {
            JsonObject state = JsonParser.parseString(cell.blockStateJson()).getAsJsonObject();
            return "quiet_below:relic_heart".equals(state.has("Name") ? state.get("Name").getAsString() : "");
        } catch (RuntimeException badState) {
            throw new IllegalStateException("Area operation contains invalid block-state evidence.", badState);
        }
    }
}
