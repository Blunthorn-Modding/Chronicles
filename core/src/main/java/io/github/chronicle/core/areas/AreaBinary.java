package io.github.chronicle.core.areas;

import io.github.chronicle.core.authoring.BlockPosition;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class AreaBinary {
    private AreaBinary() {}
    static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    static String text(DataInputStream in,int max,String label)throws IOException{return text(in,1,max,label);}
    static String textAllowEmpty(DataInputStream in,int max,String label)throws IOException{return text(in,0,max,label);}
    private static String text(DataInputStream in,int min,int max,String label)throws IOException{int n=in.readInt();if(n<min||n>max)throw new IllegalArgumentException(label+" length is outside limits");byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();String v=new String(b,StandardCharsets.UTF_8);if(!java.util.Arrays.equals(b,v.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException(label+" is not valid UTF-8");return v;}
    static int count(DataInputStream in,int max,String label)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException(label+" count is outside limits");return n;}
    static void bounds(DataOutputStream out,AreaBounds b)throws IOException{text(out,b.regionId());text(out,b.dimension());out.writeLong(b.regionRevision());pos(out,b.min());pos(out,b.max());}
    static AreaBounds bounds(DataInputStream in)throws IOException{return new AreaBounds(text(in,129,"region ID"),text(in,256,"dimension"),in.readLong(),pos(in),pos(in));}
    static void ref(DataOutputStream out,SnapshotRef r)throws IOException{text(out,r.sha256());bounds(out,r.bounds());out.writeInt(r.cells());out.writeLong(r.encodedBytes());}
    static SnapshotRef ref(DataInputStream in)throws IOException{return new SnapshotRef(text(in,64,"snapshot hash"),bounds(in),in.readInt(),in.readLong());}
    static void optionalRef(DataOutputStream out,Optional<SnapshotRef> r)throws IOException{out.writeBoolean(r.isPresent());if(r.isPresent())ref(out,r.get());}
    static Optional<SnapshotRef> optionalRef(DataInputStream in)throws IOException{return in.readBoolean()?Optional.of(ref(in)):Optional.empty();}
    private static void pos(DataOutputStream out,BlockPosition p)throws IOException{out.writeInt(p.x());out.writeInt(p.y());out.writeInt(p.z());}
    private static BlockPosition pos(DataInputStream in)throws IOException{return new BlockPosition(in.readInt(),in.readInt(),in.readInt());}
}
