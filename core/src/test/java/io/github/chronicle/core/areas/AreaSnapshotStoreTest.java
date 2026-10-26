package io.github.chronicle.core.areas;

import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AreaSnapshotStoreTest {
    @TempDir Path temp;

    @Test void contentAddressRoundTripsAndDuplicatePutDoesNotOverwrite() throws IOException {
        AreaSnapshotStore store=new AreaSnapshotStore(temp.resolve("payloads"));AreaSnapshot source=snapshot();
        SnapshotRef first=store.put(source),second=store.put(source);
        assertEquals(first,second);assertEquals(source,store.load(first));assertEquals(first.sha256(),AreaSnapshotCodec.encode(source).ref().sha256());
        assertEquals(1,Files.list(temp.resolve("payloads")).filter(p->p.toString().endsWith(".bin")).count());
    }

    @Test void corruptionTruncationAndWrongReferenceAreRejected() throws IOException {
        AreaSnapshotStore store=new AreaSnapshotStore(temp);SnapshotRef ref=store.put(snapshot());Path file=store.path(ref);byte[] bytes=Files.readAllBytes(file);
        bytes[bytes.length-1]^=1;Files.write(file,bytes);assertThrows(IOException.class,()->store.load(ref));assertThrows(IOException.class,()->store.put(snapshot()));assertArrayEquals(bytes,Files.readAllBytes(file));
        SnapshotRef wrong=new SnapshotRef("0".repeat(64),ref.bounds(),ref.cells(),ref.encodedBytes());assertThrows(IOException.class,()->store.load(wrong));
        assertThrows(IllegalArgumentException.class,()->AreaSnapshotCodec.decode(new byte[]{1,2,3},null));
    }

    @Test void geometryAndAllocationLimitsRejectBeforeDenseAllocation() {
        assertThrows(IllegalArgumentException.class,()->new AreaBounds("large","minecraft:overworld",1,new BlockPosition(0,0,0),new BlockPosition(512,0,0)));
        assertThrows(IllegalArgumentException.class,()->new AreaBounds("chunks","minecraft:overworld",1,new BlockPosition(0,0,0),new BlockPosition(128,0,128)));
        AreaBounds one=bounds();assertThrows(IllegalArgumentException.class,()->new AreaSnapshot(one,List.of("not-json"),new int[2],Map.of()));
        assertThrows(IllegalArgumentException.class,()->new AreaSnapshot(one,List.of("{\"Name\":\"minecraft:stone\"}"),new int[1],Map.of()));
    }

    @Test void malformedHugeCountsAndTrailingBytesAreRejectedBeforeAllocation() {
        byte[] bytes=AreaSnapshotCodec.encode(snapshot()).bytes();byte[] trailing=java.util.Arrays.copyOf(bytes,bytes.length+1);
        assertThrows(IllegalArgumentException.class,()->AreaSnapshotCodec.decode(trailing,null));
        // Header + region/dimension/revision/min/max ends immediately before palette count. Locate the known state text then corrupt preceding count.
        byte[] marker="{\"Name\":\"minecraft:stone\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);int at=indexOf(bytes,marker)-8;
        bytes[at]=(byte)0x7f;bytes[at+1]=(byte)0xff;bytes[at+2]=(byte)0xff;bytes[at+3]=(byte)0xff;
        assertThrows(IllegalArgumentException.class,()->AreaSnapshotCodec.decode(bytes,null));
    }

    @Test void maximumCellSnapshotStaysCompactAndPrimitiveArraysAreDefensive() {
        AreaBounds bounds=new AreaBounds("maximum","minecraft:overworld",1,new BlockPosition(0,0,0),new BlockPosition(63,63,63));
        int[] source=new int[bounds.cells()];AreaSnapshot snapshot=new AreaSnapshot(bounds,List.of("{\"Name\":\"minecraft:air\"}"),source,Map.of());
        source[0]=99;int[] exposed=snapshot.paletteIndices();exposed[1]=99;
        assertEquals("{\"Name\":\"minecraft:air\"}",snapshot.cell(0).blockStateJson());assertEquals("{\"Name\":\"minecraft:air\"}",snapshot.cell(1).blockStateJson());
        AreaSnapshotCodec.Encoded encoded=AreaSnapshotCodec.encode(snapshot);assertEquals(AreaBounds.MAX_CELLS,encoded.ref().cells());assertTrue(encoded.bytes().length<2L*1024*1024);
    }

    static AreaBounds bounds(){return new AreaBounds("test","minecraft:overworld",3,new BlockPosition(0,64,0),new BlockPosition(1,64,0));}
    static AreaSnapshot snapshot(){return new AreaSnapshot(bounds(),List.of("{\"Name\":\"minecraft:stone\"}","{\"Name\":\"minecraft:chest\"}"),new int[]{0,1},Map.of(1,"{id:\"minecraft:chest\",Items:[]}"));}
    private static int indexOf(byte[] value,byte[] needle){outer:for(int i=0;i<=value.length-needle.length;i++){for(int j=0;j<needle.length;j++)if(value[i+j]!=needle[j])continue outer;return i;}throw new AssertionError("marker absent");}
}
