package io.github.chronicle.core.areas;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import io.github.chronicle.core.authoring.BlockPosition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic, bounded binary encoding used as the snapshot content address. */
public final class AreaSnapshotCodec {
    private static final int MAGIC = 0x43484153; // CHAS
    private static final int SCHEMA = 1;
    private static final int MAX_TEXT_DEPTH = 64;
    private AreaSnapshotCodec() {}

    public static Encoded encode(AreaSnapshot snapshot) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(1 << 20, snapshot.cells() * 4));
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC); out.writeInt(SCHEMA);
                AreaBounds b = snapshot.bounds();
                text(out, b.regionId()); text(out, b.dimension()); out.writeLong(b.regionRevision());
                position(out, b.min()); position(out, b.max());
                out.writeInt(snapshot.palette().size());
                for (String state : snapshot.palette()) text(out, state);
                out.writeInt(snapshot.cells());
                for (int value : snapshot.paletteIndices()) out.writeInt(value);
                out.writeInt(snapshot.blockEntities().size());
                for (Map.Entry<Integer, String> entry : snapshot.blockEntities().entrySet()) { out.writeInt(entry.getKey()); text(out, entry.getValue()); }
            }
            byte[] result = bytes.toByteArray();
            if (result.length > SnapshotRef.MAX_ENCODED_BYTES) throw new IllegalArgumentException("snapshot payload exceeds 64 MiB");
            String sha = hex(digest(result));
            return new Encoded(new SnapshotRef(sha, snapshot.bounds(), snapshot.cells(), result.length), result);
        } catch (IOException impossible) { throw new IllegalStateException("in-memory snapshot encoding failed", impossible); }
    }

    public static AreaSnapshot decode(byte[] bytes, SnapshotRef expected) {
        if (bytes == null || bytes.length == 0 || bytes.length > SnapshotRef.MAX_ENCODED_BYTES) throw new IllegalArgumentException("snapshot payload size is outside limits");
        if (expected != null) {
            if (expected.encodedBytes() != bytes.length) throw new IllegalArgumentException("snapshot encoded length does not match reference");
            if (!MessageDigest.isEqual(digest(bytes), fromHex(expected.sha256()))) throw new IllegalArgumentException("snapshot content hash does not match reference");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("snapshot magic is invalid");
            if (in.readInt() != SCHEMA) throw new IllegalArgumentException("unsupported snapshot schema");
            String regionId = text(in, 129, "region ID");
            String dimension = text(in, 256, "dimension");
            long revision = in.readLong();
            AreaBounds bounds = new AreaBounds(regionId, dimension, revision, position(in), position(in));
            if (expected != null && !expected.bounds().equals(bounds)) throw new IllegalArgumentException("snapshot bounds do not match reference");
            int paletteSize = count(in, 1, AreaSnapshot.MAX_PALETTE, "palette size");
            List<String> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                String state = text(in, AreaCell.MAX_STATE_BYTES, "block state JSON");
                requireCanonicalJsonObject(state, AreaCell.MAX_STATE_BYTES, "block state JSON");
                palette.add(state);
            }
            int cells = count(in, 1, AreaBounds.MAX_CELLS, "cell count");
            if (cells != bounds.cells() || expected != null && cells != expected.cells()) throw new IllegalArgumentException("snapshot cell count does not match bounds/reference");
            int[] indices = new int[cells];
            for (int i = 0; i < cells; i++) { indices[i] = in.readInt(); if (indices[i] < 0 || indices[i] >= paletteSize) throw new IllegalArgumentException("palette index is outside palette"); }
            int entities = count(in, 0, Math.min(AreaSnapshot.MAX_BLOCK_ENTITIES, cells), "block entity count");
            Map<Integer, String> blockEntities = new LinkedHashMap<>();
            for (int i = 0; i < entities; i++) {
                int cell = in.readInt();
                if (cell < 0 || cell >= cells || blockEntities.containsKey(cell)) throw new IllegalArgumentException("block entity cell is invalid or duplicated");
                blockEntities.put(cell, text(in, AreaCell.MAX_BLOCK_ENTITY_BYTES, "block entity NBT"));
            }
            if (in.read() != -1) throw new IllegalArgumentException("snapshot payload contains trailing bytes");
            return new AreaSnapshot(bounds, palette, indices, blockEntities);
        } catch (EOFException exception) { throw new IllegalArgumentException("snapshot payload is truncated", exception); }
        catch (IOException exception) { throw new IllegalArgumentException("snapshot payload cannot be decoded", exception); }
    }

    static void requireCanonicalJsonObject(String source, int maxBytes, String label) {
        requireUtf8(source, 2, maxBytes, label);
        requireBoundedNesting(source, label);
        try {
            JsonReader reader = new JsonReader(new StringReader(source)); reader.setStrictness(Strictness.STRICT);
            JsonElement value = JsonParser.parseReader(reader);
            if (!value.isJsonObject() || !value.toString().equals(source)) throw new IllegalArgumentException(label + " must be a canonical JSON object");
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException(label + " is invalid", exception);
        }
    }

    static int requireUtf8(String value, int min, int max, String label) {
        if (value == null) throw new IllegalArgumentException(label + " is required");
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < min || bytes > max) throw new IllegalArgumentException(label + " byte length is outside limits");
        return bytes;
    }

    private static void requireBoundedNesting(String source, String label) {
        int depth = 0; boolean quoted = false; boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) { if (escaped) escaped=false; else if (c=='\\') escaped=true; else if (c=='\"') quoted=false; continue; }
            if (c=='\"') quoted=true;
            else if (c=='{' || c=='[') { if (++depth > MAX_TEXT_DEPTH) throw new IllegalArgumentException(label + " nesting exceeds " + MAX_TEXT_DEPTH); }
            else if (c=='}' || c==']') { if (--depth < 0) throw new IllegalArgumentException(label + " has unbalanced delimiters"); }
        }
        if (quoted || escaped || depth != 0) throw new IllegalArgumentException(label + " is structurally incomplete");
    }

    private static int count(DataInputStream in, int min, int max, String label) throws IOException { int value=in.readInt(); if(value<min||value>max)throw new IllegalArgumentException(label+" is outside limits"); return value; }
    private static void position(DataOutputStream out, BlockPosition p) throws IOException { out.writeInt(p.x());out.writeInt(p.y());out.writeInt(p.z()); }
    private static BlockPosition position(DataInputStream in) throws IOException { return new BlockPosition(in.readInt(),in.readInt(),in.readInt()); }
    private static void text(DataOutputStream out,String text)throws IOException{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static String text(DataInputStream in,int max,String label)throws IOException{int length=in.readInt();if(length<1||length>max)throw new IllegalArgumentException(label+" byte length is outside limits");byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException();String value=new String(bytes,StandardCharsets.UTF_8);if(!java.util.Arrays.equals(bytes,value.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException(label+" is not valid UTF-8");return value;}
    private static byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}}
    private static String hex(byte[] bytes){StringBuilder value=new StringBuilder(bytes.length*2);for(byte b:bytes)value.append(String.format("%02x",b&255));return value.toString();}
    private static byte[] fromHex(String text){byte[] bytes=new byte[text.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return bytes;}
    public record Encoded(SnapshotRef ref, byte[] bytes) { public Encoded { bytes=bytes.clone(); } @Override public byte[] bytes(){return bytes.clone();} }
}
