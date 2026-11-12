package io.github.chronicle.lastcouncil.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

/** Reusable setup, separate from the world's private banquet ledger. */
public final class BanquetSettings {
    private final AtomicDataFile file;
    private BanquetConfig config;

    public BanquetSettings(Path path, BanquetConfig defaults) throws IOException {
        file = new AtomicDataFile(path, 16384);
        byte[] bytes = file.load();
        config = bytes == null ? java.util.Objects.requireNonNull(defaults) : decode(bytes);
        if (bytes == null) file.save(encode(config));
    }

    public BanquetConfig config() { return config; }
    public void verify() throws IOException { file.verifyUnchanged(); }
    public void save(BanquetConfig next) throws IOException { file.save(encode(next)); config = next; }

    public static byte[] encode(BanquetConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("enabled", config.enabled());
        root.addProperty("onsetMinutes", config.onsetMinutes());
        root.addProperty("durationMinutes", config.durationMinutes());
        return (root + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static BanquetConfig decode(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);
            if (!root.keySet().equals(Set.of("schema", "enabled", "onsetMinutes", "durationMinutes"))
                    || number(root.get("schema")) != 1)
                throw new IllegalArgumentException("Invalid setup schema");
            JsonElement enabled = root.get("enabled");
            if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Expected boolean");
            return new BanquetConfig(enabled.getAsBoolean(), Math.toIntExact(number(root.get("onsetMinutes"))),
                    Math.toIntExact(number(root.get("durationMinutes"))));
        } catch (RuntimeException | CharacterCodingException invalid) {
            throw new IOException("Invalid banquet settings; file preserved", invalid);
        }
    }

    private static long number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Expected integer");
        return Long.parseLong(value.getAsString());
    }
}
