package io.github.chronicle.quietbelow.ecology;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.block.enums.SculkSensorPhase;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.Vibrations;

/** Reuses native feature ticks; plain sculk/veins gain no ticker or entity. */
public final class EcologyFeatureActivity {
    public static final int SETTLING_WRITES=16;
    private EcologyFeatureActivity() { }

    public static boolean blocked(ServerWorld world,BlockPos pos) {
        var handler=EcologyHooks.handler();
        if(handler==null||!handler.managesFeature(world,pos)||handler.active(world,pos))return false;
        // Calibrated sensors are outside the five campaign-supported sculk types.
        return !world.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)
                ||!world.getBlockState(pos).isOf(Blocks.CALIBRATED_SCULK_SENSOR);
    }

    /** Runs before vanilla vibration ticking can select or emit a pending particle. */
    public static boolean suppressTick(World world,Vibrations.ListenerData data,Vibrations.Callback callback) {
        if(!(world instanceof ServerWorld server))return false;
        var handler=EcologyHooks.handler();if(handler==null)return false;
        var source=callback.getPositionSource().getPos(world);if(source.isEmpty())return false;
        BlockPos pos=BlockPos.ofFloored(source.get());
        if(!blocked(server,pos)||!server.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4))return false;
        BlockState state=server.getBlockState(pos);
        if(!state.isOf(Blocks.SCULK_SENSOR)&&!state.isOf(Blocks.SCULK_SHRIEKER))return false;
        var entity=server.getBlockEntity(pos);
        // A Warden/Allay can occupy this position too; only this block's receiver is managed.
        if(!(entity instanceof Vibrations receiver)||receiver.getVibrationCallback()!=callback)return false;
        boolean pending=data.getVibration()!=null||data.getDelay()!=0||data.shouldSpawnParticle()
                ||data.getSelector().getVibrationToTick(Long.MAX_VALUE).isPresent();
        data.setVibration(null);data.setDelay(0);data.setSpawnParticle(false);data.getSelector().clear();
        if(pending)entity.markDirty();
        settle(server,pos,state);
        return true;
    }
    public static void settle(ServerWorld server,BlockPos pos,BlockState state) {
        BlockState quiet=quietState(state);
        if(quiet!=state)EcologyHooks.handler().settleInactive(server,pos,quiet);
    }
    public static BlockState quietState(BlockState state) {
        if(state.isOf(Blocks.SCULK_SENSOR))return state.with(SculkSensorBlock.POWER,0)
                .with(SculkSensorBlock.SCULK_SENSOR_PHASE,SculkSensorPhase.INACTIVE);
        return state.isOf(Blocks.SCULK_SHRIEKER)?state.with(SculkShriekerBlock.SHRIEKING,false):state;
    }
}
