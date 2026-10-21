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
        var world=player.getEntityWorld();var source=new BlockPos(footing.x(),footing.y(),footing.z());
        if(!livingFooting(world,footing,engine))return false;
        var sourceState=EcologyLoadedBlocks.state(world,source);var step=player.getSteppingPos();
        boolean same=step.equals(source)||(sourceState.isOf(Blocks.SCULK_VEIN)&&step.equals(source.down()));
        if(!same)return false;
        var body=player.getBoundingBox();int minX=(int)Math.floor(body.minX+.000001),maxX=(int)Math.floor(body.maxX-.000001);
        int minZ=(int)Math.floor(body.minZ+.000001),maxZ=(int)Math.floor(body.maxZ-.000001),topY=(int)Math.floor(player.getY()-.00001);
        if(maxX-minX>1||maxZ-minZ>1)return false; // Bounded native-size footprint; larger bodies fail closed.
        var sole=new Box(body.minX,player.getY()-LANDING_TOLERANCE,body.minZ,body.maxX,player.getY()+.000001,body.maxZ);
        // Check every intersecting foot column, including blocks supporting only an edge.
        // Two heights include fences/walls; shape evaluation cannot acquire world chunk tickets.
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=topY-1;y<=topY;y++){
            var below=new BlockPos(x,y,z);if(EcologyLoadedBlocks.chunk(world,below)==null)return false;
            var shape=EcologyLoadedBlocks.state(world,below).getCollisionShape(EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent());
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
        BlockPos support=p.getSteppingPos();var box=p.getBoundingBox();
        if(support.getX()+1<=box.minX||support.getX()>=box.maxX||support.getZ()+1<=box.minZ||support.getZ()>=box.maxZ)return null;
        if(EcologyLoadedBlocks.chunk(world,support)==null)return null;
        var state=EcologyLoadedBlocks.state(world,support);var at=position(support);
        if((state.isOf(Blocks.SCULK)||state.isOf(Blocks.SCULK_CATALYST)||state.isOf(Blocks.SCULK_SENSOR)||state.isOf(Blocks.SCULK_SHRIEKER))&&engine.owns(at)&&engine.living(at)){
            var shape=state.getCollisionShape(world,support,ShapeContext.of(p));
            // Native movement can report grounded feet ~0.022 blocks above the surface.
            // Stay below carpet height (1/16), so a real intervening platform remains safe.
            if(!shape.isEmpty()&&Math.abs(p.getY()-(support.getY()+shape.getMax(Direction.Axis.Y)))<=LANDING_TOLERANCE)return at;
        }
        // A DOWN-facing vein at floor height coats the native supporting block.
        BlockPos feet=new BlockPos(p.getBlockX(),(int)Math.round(p.getY()),p.getBlockZ());if(Math.abs(p.getY()-feet.getY())>LANDING_TOLERANCE||EcologyLoadedBlocks.chunk(world,feet)==null)return null;
        var coating=EcologyLoadedBlocks.state(world,feet);var coat=position(feet);
        return coating.isOf(Blocks.SCULK_VEIN)&&coating.get(MultifaceGrowthBlock.getProperty(Direction.DOWN))&&engine.owns(coat)&&engine.living(coat)?coat:null;
    }
    private static EcologyTerrain.Pos position(BlockPos p){return new EcologyTerrain.Pos(p.getX(),p.getY(),p.getZ());}
}
