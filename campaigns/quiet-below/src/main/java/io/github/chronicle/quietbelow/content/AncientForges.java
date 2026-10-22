package io.github.chronicle.quietbelow.content;

import io.github.chronicle.core.enchanting.IngredientEnchantmentRecipe;
import io.github.chronicle.core.server.ItemEnchantingRuntime;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.ecology.QuietBelowEcology;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Campaign power/models. Core owns recipe configuration and inventory transactions. */
public final class AncientForges {
    public static final String GROUP="quiet_below:forge";
    private static final int MAX_LOADED=1024,MODEL_CHECKS=8;
    private static final Map<MinecraftServer,LinkedHashSet<AncientForgeBlockEntity>> LOADED=new IdentityHashMap<>();
    private AncientForges() { }
    public static void register() {
        var recipes=ItemEnchantingRuntime.catalog();
        for(var mapping:List.of(new String[]{"echo_shard","sharpness"},new String[]{"amethyst_shard","unbreaking"},new String[]{"prismarine_shard","protection"},new String[]{"flint","power"},new String[]{"feather","feather_falling"}))
            recipes.register(new IngredientEnchantmentRecipe("quiet_below:"+mapping[0],GROUP,"minecraft:"+mapping[0],"minecraft:"+mapping[1],1,1,true));
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((block,world)->{if(block instanceof AncientForgeBlockEntity forge)admit(world.getServer(),forge);});
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((block,world)->{var loaded=LOADED.get(world.getServer());if(loaded!=null)loaded.remove(block);});
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
            // Transient art must not survive a save/reload, even beyond tracker capacity.
            // Use the callback's actual chunk; World.getChunk/markDirty may self-wait here.
            for(var block:chunk.getBlockEntities().values())if(block instanceof AncientForgeBlockEntity forge){
                var state=chunk.getBlockState(forge.getPos());
                if(state.isOf(QuietBelow.ANCIENT_FORGE_BLOCK)&&state.get(AncientForgeBlock.HEAT)!=0){
                    chunk.setBlockState(forge.getPos(),state.with(AncientForgeBlock.HEAT,0),0);chunk.markNeedsSaving();
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(LOADED::remove);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var loaded=LOADED.get(server);if(loaded==null)return;
            for(int i=0,n=Math.min(MODEL_CHECKS,loaded.size());i<n;i++){
                var forge=loaded.iterator().next();loaded.remove(forge);
                if(!forge.isRemoved()&&forge.getWorld() instanceof ServerWorld world&&physical(world,forge.getPos())==forge){loaded.add(forge);update(world,forge);}
            }
        });
    }
    private static boolean admit(MinecraftServer server,AncientForgeBlockEntity forge){
        var loaded=LOADED.computeIfAbsent(server,s->new LinkedHashSet<>());
        if(loaded.contains(forge))return true;
        return loaded.size()<MAX_LOADED&&loaded.add(forge);
    }
    private static AncientForgeBlockEntity physical(ServerWorld world,BlockPos pos){
        if(!world.isInBuildLimit(pos)||!world.getWorldBorder().contains(pos))return null;
        var chunk=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!chunk.getBlockState(pos).isOf(QuietBelow.ANCIENT_FORGE_BLOCK))return null;
        return chunk.getBlockEntities().get(pos) instanceof AncientForgeBlockEntity forge?forge:null;
    }
    private static void update(ServerWorld world,AncientForgeBlockEntity forge){
        String blocked=QuietBelowEcology.stationPower(world,forge.getPos());
        int heat=blocked!=null?0:world.getServer().getTicks()<forge.workingUntil?2:1;
        var state=forge.getCachedState();
        if(state.isOf(QuietBelow.ANCIENT_FORGE_BLOCK)&&state.get(AncientForgeBlock.HEAT)!=heat)
            world.setBlockState(forge.getPos(),state.with(AncientForgeBlock.HEAT,heat),Block.NOTIFY_LISTENERS);
    }
    public static void open(ServerPlayerEntity player,BlockPos position){
        ServerWorld world=player.getEntityWorld();BlockPos pos=position.toImmutable();var forge=physical(world,pos);
        if(forge==null)return;
        if(!admit(world.getServer(),forge)){player.sendMessage(Text.literal("Too many loaded Ancient Forges; unload some before using this one."),false);return;}
        update(world,forge);
        ItemEnchantingRuntime.open(player,GROUP,"Ancient Forge",new ItemEnchantingRuntime.Gate(){
            @Override public boolean stillPresent(ServerPlayerEntity actor){
                return actor.getEntityWorld()==world&&actor.isAlive()&&!actor.isSpectator()&&physical(world,pos)==forge&&!forge.isRemoved();
            }
            @Override public io.github.chronicle.core.network.ItemEnchantingPayloads.State state(ServerPlayerEntity actor){
                var unavailable=io.github.chronicle.core.network.ItemEnchantingPayloads.State.UNAVAILABLE;
                var quiet=io.github.chronicle.core.network.ItemEnchantingPayloads.State.QUIET;
                if(!stillPresent(actor)||actor.squaredDistanceTo(pos.toCenterPos())>36)return unavailable;
                if(actor.isCreative()||!PlayRuntime.eligiblePlayers(world.getServer()).contains(actor)||!QuietBelowEcology.playing(world.getServer()))return quiet;
                return QuietBelowEcology.stationPower(world,pos)==null?io.github.chronicle.core.network.ItemEnchantingPayloads.State.READY:
                        io.github.chronicle.core.network.ItemEnchantingPayloads.State.UNPOWERED;
            }
            @Override public void completed(ServerPlayerEntity actor){
                if(physical(world,pos)!=forge)return;
                forge.workingUntil=(long)world.getServer().getTicks()+20;update(world,forge);
                world.playSound(null,pos,SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,SoundCategory.BLOCKS,0.8f,0.8f);
            }
        });
    }
}
