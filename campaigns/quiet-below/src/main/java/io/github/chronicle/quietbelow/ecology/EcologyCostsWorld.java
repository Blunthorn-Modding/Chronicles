package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.core.server.AuthoringRuntime;
import net.minecraft.block.Blocks;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.EmptyBlockView;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Current native sole contact, cached-only reads and guarded transient effect ownership. */
final class EcologyCostsWorld implements EcologyCosts.World {
    private static final double LANDING_TOLERANCE=1.0/32;
    private final MinecraftServer server;
    private final ServerWorld dimension;
    private final EcologyEngine engine;
    private final EcologyCosts costs;
    private final BooleanSupplier permitted;
    private final Map<UUID,ServerPlayerEntity> eligible=new LinkedHashMap<>();
    EcologyCostsWorld(MinecraftServer server,ServerWorld dimension,EcologyEngine engine,EcologyCosts costs,BooleanSupplier permitted){
        this.server=server;this.dimension=dimension;this.engine=engine;this.costs=costs;this.permitted=permitted;
    }
    @Override public List<EcologyCosts.Player> eligiblePlayers(){
        eligible.clear();List<EcologyCosts.Player> result=new ArrayList<>();
        // Bound the shared eligibility enumeration and all subsequent native contact reads.
        if(server.getPlayerManager().getPlayerList().size()>EcologyCosts.MAX_PLAYERS)
            throw new IllegalStateException("Contact costs support at most 256 online players; no contact blocks were read.");
        for(var p:PlayRuntime.eligiblePlayers(server)){
            if(p.getEntityWorld()!=dimension||p.isCreative())continue;
            var contact=contact(p,engine);
            if(contact!=null){eligible.put(p.getUuid(),p);result.add(new EcologyCosts.Player(p.getUuid(),contact));}
            else if(airborneFooting(p)){eligible.put(p.getUuid(),p);result.add(EcologyCosts.Player.airborne(p.getUuid()));}
        }
        return result;
    }
    @Override public void synchronize(Map<UUID,Set<EcologyCosts.Kind>> active){
        EcologyCostEffects.synchronize(server,active,(id,kind)->{
            var p=eligible.get(id);var participation=PlayRuntime.available(server);var rule=kind==EcologyCosts.Kind.HUNGER?costs.rules().hunger():costs.rules().darkness();
            if(p==null||participation==null||!participation.participants().contains(id)||AuthoringRuntime.isAuthor(p)
                    ||server.getPlayerManager().getPlayer(id)!=p||!p.isAlive()||p.isCreative()||p.isSpectator()
                    ||p.getEntityWorld()!=dimension||!permitted.getAsBoolean()||!rule.enabled()||!costs.rules().equals(engine.costRules())
                    ||!engine.costsAvailable()||kind==EcologyCosts.Kind.DARKNESS&&!engine.extensive())return false;
            return contact(p,engine)!=null||airborneFooting(p);
        });
    }
    private boolean airborneFooting(ServerPlayerEntity player){
        var footing=costs.footing(player.getUuid());
        return footing!=null&&livingFooting(dimension,footing,engine)&&(!player.isOnGround()||landingTransition(player,footing,engine));
    }
    /** Native movement can set grounded before publishing its final collision-adjusted Y. */
    static boolean landingTransition(ServerPlayerEntity player,EcologyTerrain.Pos footing,EcologyEngine engine){
        var world=player.getEntityWorld();
        if(!livingFooting(world,footing,engine))return false;
        // An early grounded packet can already name another cell, or the air above a vein.
        // It is still airborne until a collision actually reaches the sole. Retain the previous
        // living footing irrespective of that provisional stepping position; a real clean
        // landing (including a newly placed pillar) must fail this collision-free exception.
        var body=player.getBoundingBox();int minX=(int)Math.floor(body.minX+.000001),maxX=(int)Math.floor(body.maxX-.000001);
        int minZ=(int)Math.floor(body.minZ+.000001),maxZ=(int)Math.floor(body.maxZ-.000001),topY=(int)Math.floor(player.getY()-.00001);
        if(maxX-minX>1||maxZ-minZ>1)return false; // Bounded native-size footprint; larger bodies fail closed.
        var sole=new Box(body.minX,player.getY()-LANDING_TOLERANCE,body.minZ,body.maxX,player.getY()+.000001,body.maxZ);
        // Check every intersecting foot column, including blocks supporting only an edge.
        // Two heights include fences/walls; shape evaluation cannot acquire world chunk tickets.
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=Math.max(topY-1,world.getBottomY());y<=Math.min(topY,world.getBottomY()+world.getHeight()-1);y++){
            var below=new BlockPos(x,y,z);if(EcologyLoadedBlocks.chunk(world,below)==null)return false;
            var shape=EcologyLoadedBlocks.state(world,below).getCollisionShape(EmptyBlockView.INSTANCE,below,ShapeContext.of(player));
            for(var part:shape.getBoundingBoxes())if(part.offset(x,y,z).intersects(sole))return false;
        }
        return true;
    }
    /** Airborne recheck of the saved exact footing: cached-only, no spatial search or tickets. */
    private static boolean livingFooting(ServerWorld world,EcologyTerrain.Pos footing,EcologyEngine engine){
        BlockPos pos=new BlockPos(footing.x(),footing.y(),footing.z());if(EcologyLoadedBlocks.chunk(world,pos)==null)return false;
        var state=EcologyLoadedBlocks.state(world,pos);return (state.isOf(Blocks.SCULK)||state.isOf(Blocks.SCULK_CATALYST)||state.isOf(Blocks.SCULK_SENSOR)||state.isOf(Blocks.SCULK_SHRIEKER)||state.isOf(Blocks.SCULK_VEIN)&&state.get(MultifaceGrowthBlock.getProperty(Direction.DOWN)))&&engine.owns(footing)&&engine.living(footing);
    }
    static EcologyTerrain.Pos contact(ServerPlayerEntity p,EcologyEngine engine){
        if(!p.isOnGround()||!(p.getEntityWorld() instanceof ServerWorld world))return null;
        var body=p.getBoundingBox();int minX=(int)Math.floor(body.minX+.000001),maxX=(int)Math.floor(body.maxX-.000001);
        int minZ=(int)Math.floor(body.minZ+.000001),maxZ=(int)Math.floor(body.maxZ-.000001),topY=(int)Math.floor(p.getY()-.00001);
        if(maxX-minX>1||maxZ-minZ>1)return null;
        var sole=new Box(body.minX,p.getY()-LANDING_TOLERANCE,body.minZ,body.maxX,p.getY()+.000001,body.maxZ);
        // A support cache picks just one block at seams. Inspect the actual complete sole instead:
        // at most four columns, two support heights and one floor coating per column (12 cells).
        // Missing evidence anywhere in that footprint cannot establish a clean or living landing.
        Map<BlockPos,net.minecraft.block.BlockState> states=new HashMap<>();
        // Beyond the dimension's vertical limits there is no supporting block; do not mistake
        // that known empty space for an unloaded chunk at a valid bottom/top surface.
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=Math.max(topY-1,world.getBottomY());y<=Math.min(topY+1,world.getBottomY()+world.getHeight()-1);y++){
            var at=new BlockPos(x,y,z);if(EcologyLoadedBlocks.chunk(world,at)==null)return null;
            states.put(at,EcologyLoadedBlocks.state(world,at));
        }
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=topY-1;y<=topY;y++){
            var support=new BlockPos(x,y,z);var state=states.get(support);if(state==null)continue;
            var shape=state.getCollisionShape(EmptyBlockView.INSTANCE,support,ShapeContext.of(p));
            boolean touching=false;
            for(var part:shape.getBoundingBoxes())if(part.offset(x,y,z).intersects(sole)
                    &&Math.abs(p.getY()-(y+part.maxY))<=LANDING_TOLERANCE){touching=true;break;}
            if(!touching)continue;
            var at=position(support);
            if((state.isOf(Blocks.SCULK)||state.isOf(Blocks.SCULK_CATALYST)||state.isOf(Blocks.SCULK_SENSOR)||state.isOf(Blocks.SCULK_SHRIEKER))&&engine.owns(at)&&engine.living(at))return at;
            // Only a floor-facing vein attached directly to this collision support coats the
            // surface underfoot. Side-only or nearby veins do not turn a clean pillar into sculk.
            if(Math.abs(p.getY()-(y+1))>LANDING_TOLERANCE)continue;
            var feet=support.up();var coating=states.get(feet);var coat=position(feet);
            if(coating!=null&&coating.isOf(Blocks.SCULK_VEIN)&&coating.get(MultifaceGrowthBlock.getProperty(Direction.DOWN))&&engine.owns(coat)&&engine.living(coat))return coat;
        }
        return null;
    }
    private static EcologyTerrain.Pos position(BlockPos p){return new EcologyTerrain.Pos(p.getX(),p.getY(),p.getZ());}
}
