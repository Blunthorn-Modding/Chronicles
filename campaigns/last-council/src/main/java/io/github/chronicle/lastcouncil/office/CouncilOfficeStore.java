package io.github.chronicle.lastcouncil.office;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict schema-1 storage with observed-byte conflict detection. */
public final class CouncilOfficeStore implements CouncilOfficeRepository {
    private static final long MAX_BYTES = 64 * 1024;
    private final AtomicDataFile file;
    private boolean loaded;

    public CouncilOfficeStore(Path path) { file = new AtomicDataFile(path, MAX_BYTES); }

    @Override public synchronized CouncilOffice load() throws IOException {
        loaded = false;
        byte[] bytes = file.load();
        CouncilOffice result = bytes == null ? CouncilOffice.empty() : decode(bytes);
        loaded = true;
        return result;
    }

    @Override public synchronized void verifyUnchanged() throws IOException {
        if (!loaded) throw new IOException("Load office before verifying");
        file.verifyUnchanged();
    }

    @Override public synchronized void save(CouncilOffice office) throws IOException {
        if (!loaded) throw new IOException("Load office before saving");
        Objects.requireNonNull(office, "office");
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("revision", office.revision());
        if (office.chancellor() == null) root.add("chancellor", JsonNull.INSTANCE);
        else root.addProperty("chancellor", office.chancellor().toString());
        byte[] bytes = (root.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (!decode(bytes).equals(office)) throw new IOException("Office serialization failed validation");
        file.save(bytes);
    }

    private static CouncilOffice decode(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);
            if (!root.keySet().equals(Set.of("schema", "revision", "chancellor"))) throw new IllegalArgumentException("incorrect fields");
            if (integer(root.get("schema")) != 1) throw new IllegalArgumentException("unsupported schema");
            long revision = integer(root.get("revision"));
            JsonElement value = root.get("chancellor");
            UUID holder = null;
            if (!value.isJsonNull()) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("expected UUID string or null");
                String textUuid = value.getAsString();
                holder = UUID.fromString(textUuid);
                if (!holder.toString().equals(textUuid)) throw new IllegalArgumentException("UUID must be canonical");
            }
            return new CouncilOffice(revision, holder);
        } catch (CharacterCodingException | RuntimeException failure) {
            throw new IOException("Invalid council office; file preserved", failure);
        }
    }

    private static long integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !value.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("expected nonnegative integer");
        return Long.parseLong(value.getAsString());
    }
}
