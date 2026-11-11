package io.github.chronicle.core.encounters;

import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Pure deterministic candidate anchors shared by validation and durable operation planning. */
public final class EncounterPlacementPlanner {
    public static final int MAX_COUNT=128;
    public static final int MAX_CANDIDATE_CHECKS=4096;
    private static final double MAX_FOOTPRINT=64;
    private EncounterPlacementPlanner(){}
    public record Position(double x,double y,double z){public Position{if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("encounter position must be finite");}}

    public static List<Position> positions(AuthoringSite site,int count,double maxWidth,double maxHeight){
        Objects.requireNonNull(site,"encounter site is required");if(count<1||count>MAX_COUNT)throw new IllegalArgumentException("encounter placement count must be 1-128");if(!Double.isFinite(maxWidth)||maxWidth<=0||maxWidth>MAX_FOOTPRINT||!Double.isFinite(maxHeight)||maxHeight<=0||maxHeight>MAX_FOOTPRINT)throw new IllegalArgumentException("encounter footprint must be positive, finite, and at most 64 blocks");
        int horizontalStride=Math.max(1,(int)Math.ceil(maxWidth)),verticalStride=Math.max(1,(int)Math.ceil(maxHeight));ArrayList<Position> result=new ArrayList<>(count);
        if(site instanceof AuthoringSite.Location point){double[][] offsets={{0,0},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};for(int index=0;index<count;index++){int ring=index==0?0:(index-1)/8+1,slot=index==0?0:(index-1)%8;double[] offset=ring==0?offsets[0]:offsets[slot+1];result.add(new Position(point.x()+offset[0]*ring*horizontalStride,point.y(),point.z()+offset[1]*ring*horizontalStride));}return List.copyOf(result);}
        AuthoringSite.Region region=(AuthoringSite.Region)site;long width=(long)region.max().x()-region.min().x()+1,depth=(long)region.max().z()-region.min().z()+1,height=(long)region.max().y()-region.min().y()+1;long xSlots=Math.min(count,horizontalSlots(width,maxWidth,horizontalStride)),zSlots=Math.min(count,horizontalSlots(depth,maxWidth,horizontalStride)),ySlots=Math.min(count,(height-1)/verticalStride+1);long plane=xSlots*zSlots,capacity=plane*ySlots;if(xSlots==0||zSlots==0||capacity<count)throw new IllegalArgumentException("Encounter target does not have enough non-overlapping spaces for this composition");double inset=maxWidth<=1?.5:maxWidth/2;
        for(int index=0;index<count;index++){long y=index/plane,flat=index%plane,z=flat/xSlots,x=flat%xSlots;result.add(new Position(region.min().x()+inset+x*horizontalStride,region.min().y()+y*verticalStride,region.min().z()+inset+z*horizontalStride));}return List.copyOf(result);
    }

    private static long horizontalSlots(long extent,double footprint,int stride){if(extent<footprint)return 0;return (long)Math.floor((extent-footprint)/stride)+1;}

    /** Structural capacity only; unlike legacy positions, every full-height box fits inside the Region. */
    public static List<Position> structuralRegionPositions(AuthoringSite.Region region,int count,double maxWidth,double maxHeight){
        positions(region,1,maxWidth,maxHeight);if(count<1||count>MAX_COUNT)throw new IllegalArgumentException("encounter placement count must be 1-128");
        int horizontalStride=Math.max(1,(int)Math.ceil(maxWidth)),verticalStride=Math.max(1,(int)Math.ceil(maxHeight));
        long width=(long)region.max().x()-region.min().x()+1,depth=(long)region.max().z()-region.min().z()+1,height=(long)region.max().y()-region.min().y()+1;
        long xSlots=Math.min(count,horizontalSlots(width,maxWidth,horizontalStride)),zSlots=Math.min(count,horizontalSlots(depth,maxWidth,horizontalStride));
        long ySlots=Math.min(count,Math.max(0,(long)Math.floor((height-maxHeight)/verticalStride)+1)),plane=xSlots*zSlots;
        if(plane*ySlots<count)throw new IllegalArgumentException("Encounter Region does not have enough full-height non-overlapping spaces; enlarge the Region or use fewer entities");
        double inset=maxWidth<=1?.5:maxWidth/2;List<Position> result=new ArrayList<>();
        for(int index=0;index<count;index++){long y=index/plane,flat=index%plane;result.add(new Position(region.min().x()+inset+(flat%xSlots)*horizontalStride,region.min().y()+y*verticalStride,region.min().z()+inset+(flat/xSlots)*horizontalStride));}
        return List.copyOf(result);
    }

    /** New plans only: bounded first-clear selection, with no terrain edits or floor-support assumption. */
    public static List<Position> clearRegionPositions(AuthoringSite.Region region,int count,double maxWidth,double maxHeight,Predicate<Position> clear){
        Objects.requireNonNull(clear,"encounter clearance check is required");positions(region,1,maxWidth,maxHeight);
        if(count<1||count>MAX_COUNT)throw new IllegalArgumentException("encounter placement count must be 1-128");
        int stride=Math.max(1,(int)Math.ceil(maxWidth));
        long xSlots=Math.min(MAX_CANDIDATE_CHECKS,horizontalSlots((long)region.max().x()-region.min().x()+1,maxWidth,stride));
        long zSlots=Math.min(MAX_CANDIDATE_CHECKS,horizontalSlots((long)region.max().z()-region.min().z()+1,maxWidth,stride));
        double inset=maxWidth<=1?.5:maxWidth/2,maxY=region.max().y()+1.0-maxHeight;
        List<Position> selected=new ArrayList<>();int checks=0;
        // One lowest available position per column per pass distributes mobs over ordinary ground first.
        for(int pass=0;pass<count;pass++){
            int before=selected.size();
            for(long z=0;z<zSlots;z++)for(long x=0;x<xSlots;x++){
                for(long y=region.min().y();y<=maxY;y++){
                    if(checks++==MAX_CANDIDATE_CHECKS)throw new IllegalStateException("Encounter Region search reached 4096 candidate checks; choose a smaller clear loaded Region or fewer entities");
                    Position candidate=new Position(region.min().x()+inset+x*stride,y,region.min().z()+inset+z*stride);
                    boolean overlaps=selected.stream().anyMatch(old->Math.abs(old.x()-candidate.x())<maxWidth&&Math.abs(old.z()-candidate.z())<maxWidth&&Math.abs(old.y()-candidate.y())<maxHeight);
                    if(overlaps||!clear.test(candidate))continue;
                    selected.add(candidate);if(selected.size()==count)return List.copyOf(selected);break;
                }
            }
            if(before==selected.size())break;
        }
        throw new IllegalStateException("Encounter Region has fewer than "+count+" clear loaded spaces within its bounds; clear or enlarge the Region before retrying");
    }
}
