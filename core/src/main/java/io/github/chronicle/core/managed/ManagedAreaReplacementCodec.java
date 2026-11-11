package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic, bounded sidecar codec. Durable storage and fsync remain coordinator responsibilities. */
public final class ManagedAreaReplacementCodec {
    private static final int MAGIC = 0x434D4152; // CMAR
    private static final int SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ID_BYTES = 256;

    private ManagedAreaReplacementCodec() { }

    public static byte[] encode(ManagedAreaReplacementPlan plan) {
        Objects.requireNonNull(plan, "managed area replacement plan is required");
        try {
            int expectedSize = encodedSize(plan);
            if (expectedSize > MAX_ENCODED_BYTES)
                throw new IllegalArgumentException("managed area replacement plan exceeds encoded size limit");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(expectedSize);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC); out.writeInt(SCHEMA);
                uuid(out, plan.operationId()); text(out, plan.dimension(), MAX_ID_BYTES, "dimension");
                out.writeInt(plan.affected().size());
                for (BlockPosition position : plan.affected()) position(out, position);
                ledger(out, plan.before()); ledger(out, plan.after()); limits(out, plan.expectedLimits());
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_ENCODED_BYTES) throw new IllegalArgumentException("managed area replacement plan exceeds encoded size limit");
            return encoded;
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    public static ManagedAreaReplacementPlan decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "managed area replacement bytes are required");
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES)
            throw new IllegalArgumentException("managed area replacement encoded size is invalid");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (in.readInt() != MAGIC || in.readInt() != SCHEMA)
                throw new IllegalArgumentException("managed area replacement magic/schema is invalid");
            UUID operation = uuid(in); String dimension = text(in, MAX_ID_BYTES, "dimension");
            int count = count(in, 1, ManagedAreaReplacementPlan.MAX_AFFECTED_POSITIONS, "affected position");
            ArrayList<BlockPosition> affected = new ArrayList<>(count);
            for (int i = 0; i < count; i++) affected.add(position(in));
            ManagedBlockLedger before = ledger(in), after = ledger(in);
            ManagedBlockLimits limits = limits(in);
            if (in.read() != -1) throw new IllegalArgumentException("managed area replacement has trailing bytes");
            return new ManagedAreaReplacementPlan(operation, dimension, affected, before, after, limits);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("managed area replacement is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("managed area replacement cannot be decoded", exception);
        }
    }

    private static void ledger(DataOutputStream out, ManagedBlockLedger ledger) throws IOException {
        out.writeLong(ledger.revision()); out.writeInt(ledger.migrationVersion()); out.writeInt(ledger.instances().size());
        for (ManagedBlockInstance instance : ledger.instances()) {
            uuid(out, instance.id()); text(out, instance.typeId(), MAX_ID_BYTES, "type ID");
            text(out, instance.blockId(), MAX_ID_BYTES, "block ID"); text(out, instance.dimension(), MAX_ID_BYTES, "dimension");
            position(out, instance.position()); out.writeByte(instance.registration().ordinal()); out.writeByte(instance.origin().ordinal());
            out.writeLong(instance.revision());
        }
    }

    private static ManagedBlockLedger ledger(DataInputStream in) throws IOException {
        long revision = in.readLong(); int migration = in.readInt();
        int count = count(in, 0, ManagedBlockLedger.MAX_INSTANCES, "managed instance");
        ArrayList<ManagedBlockInstance> instances = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = uuid(in); String type = text(in, MAX_ID_BYTES, "type ID");
            String block = text(in, MAX_ID_BYTES, "block ID"), dimension = text(in, MAX_ID_BYTES, "dimension");
            BlockPosition position = position(in);
            int registration = ordinal(in, ManagedBlockInstance.Registration.values().length, "registration");
            int origin = ordinal(in, ManagedBlockInstance.Origin.values().length, "origin");
            instances.add(new ManagedBlockInstance(id, type, block, dimension, position,
                    ManagedBlockInstance.Registration.values()[registration], ManagedBlockInstance.Origin.values()[origin], in.readLong()));
        }
        return new ManagedBlockLedger(revision, migration, instances);
    }

    private static void limits(DataOutputStream out, ManagedBlockLimits limits) throws IOException {
        out.writeLong(limits.revision()); out.writeInt(limits.overrides().size());
        for (var entry : new TreeMap<>(limits.overrides()).entrySet()) {
            text(out, entry.getKey(), MAX_ID_BYTES, "limit type ID"); out.writeInt(entry.getValue());
        }
    }

    private static ManagedBlockLimits limits(DataInputStream in) throws IOException {
        long revision = in.readLong(); int count = count(in, 0, ManagedBlockCatalog.MAX_TYPES, "managed limit");
        TreeMap<String, Integer> overrides = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            String type = text(in, MAX_ID_BYTES, "limit type ID"); int value = in.readInt();
            if (overrides.putIfAbsent(type, value) != null) throw new IllegalArgumentException("duplicate managed limit type");
        }
        return new ManagedBlockLimits(revision, overrides);
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void position(DataOutputStream out, BlockPosition value) throws IOException { out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z()); }
    private static BlockPosition position(DataInputStream in) throws IOException { return new BlockPosition(in.readInt(), in.readInt(), in.readInt()); }
    private static int ordinal(DataInputStream in, int size, String name) throws IOException {
        int value = in.readUnsignedByte(); if (value >= size) throw new IllegalArgumentException("invalid managed " + name); return value;
    }
    private static int count(DataInputStream in, int minimum, int maximum, String name) throws IOException {
        int value = in.readInt(); if (value < minimum || value > maximum) throw new IllegalArgumentException(name + " count is outside limits"); return value;
    }
    private static void text(DataOutputStream out, String value, int maximum, String name) throws IOException {
        if (value == null || value.isEmpty() || value.length() > maximum)
            throw new IllegalArgumentException("managed " + name + " length is outside limits");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > maximum) throw new IllegalArgumentException("managed " + name + " length is outside limits");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static int encodedSize(ManagedAreaReplacementPlan plan) {
        long size = Integer.BYTES * 3L + Long.BYTES * 2L; // magic, schema, UUID, affected count
        size += Integer.BYTES + utf8Length(plan.dimension(), MAX_ID_BYTES, "dimension");
        size += (long) plan.affected().size() * Integer.BYTES * 3L;
        size += ledgerSize(plan.before()) + ledgerSize(plan.after());
        size += Long.BYTES + Integer.BYTES;
        for (var entry : plan.expectedLimits().overrides().entrySet())
            size += Integer.BYTES + utf8Length(entry.getKey(), MAX_ID_BYTES, "limit type ID") + Integer.BYTES;
        if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("managed area replacement plan exceeds encoded size limit");
        return (int) size;
    }
    private static long ledgerSize(ManagedBlockLedger ledger) {
        long size = Long.BYTES + Integer.BYTES * 2L;
        for (ManagedBlockInstance instance : ledger.instances()) {
            size += Long.BYTES * 2L;
            size += Integer.BYTES + utf8Length(instance.typeId(), MAX_ID_BYTES, "type ID");
            size += Integer.BYTES + utf8Length(instance.blockId(), MAX_ID_BYTES, "block ID");
            size += Integer.BYTES + utf8Length(instance.dimension(), MAX_ID_BYTES, "dimension");
            size += Integer.BYTES * 3L + 2L + Long.BYTES;
        }
        return size;
    }
    private static int utf8Length(String value, int maximum, String name) {
        if (value == null || value.isEmpty() || value.length() > maximum)
            throw new IllegalArgumentException("managed " + name + " length is outside limits");
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes == 0 || bytes > maximum)
            throw new IllegalArgumentException("managed " + name + " length is outside limits");
        return bytes;
    }
    private static String text(DataInputStream in, int maximum, String name) throws IOException {
        int length = count(in, 1, maximum, name + " byte");
        byte[] bytes = in.readNBytes(length); if (bytes.length != length) throw new EOFException();
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8)) || !AuthoringSite.NAMESPACED_ID.matcher(value).matches())
            throw new IllegalArgumentException("managed " + name + " is invalid");
        return value;
    }
}
