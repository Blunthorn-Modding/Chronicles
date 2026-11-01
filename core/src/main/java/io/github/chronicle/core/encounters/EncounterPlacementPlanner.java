package io.github.chronicle.core.encounters;

import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure deterministic candidate anchors shared by validation and durable operation planning. */
public final class EncounterPlacementPlanner {
    public static final int MAX_COUNT=128;
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
}
