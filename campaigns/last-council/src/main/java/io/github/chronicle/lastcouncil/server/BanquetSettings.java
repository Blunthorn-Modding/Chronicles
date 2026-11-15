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
    private boolean legacy;

    public BanquetSettings(Path path, BanquetConfig defaults) throws IOException {
        file = new AtomicDataFile(path, 16384);
        byte[] bytes = file.load();
        config = bytes == null ? java.util.Objects.requireNonNull(defaults) : decode(bytes);
        legacy = bytes != null && BookConfig.strictObject(new String(bytes, StandardCharsets.UTF_8)).get("schema").getAsInt() == 1;
        if (bytes == null) file.save(encode(config));
    }

    public BanquetConfig config() { return config; }
    public void verify() throws IOException { file.verifyUnchanged(); }
    public void save(BanquetConfig next) throws IOException { file.save(encode(next)); config = next; legacy = false; }
    public String migrationNotice() { return legacy ? "Poison settings upgraded. Review the stage timings." : ""; }

    public static byte[] encode(BanquetConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("enabled", config.enabled());
        root.addProperty("onsetMinutes", config.onsetMinutes());
        root.addProperty("symptomMinutes", config.symptomMinutes());
        root.addProperty("poisonMinutes", config.poisonMinutes());
        return (root + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static BanquetConfig decode(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);
            long schema = number(root.get("schema"));
            if (schema != 1 && schema != 2) throw new IllegalArgumentException("Invalid setup schema");
            if (!root.keySet().equals(schema == 1
                    ? Set.of("schema", "enabled", "onsetMinutes", "durationMinutes")
                    : Set.of("schema", "enabled", "onsetMinutes", "symptomMinutes", "poisonMinutes")))
                throw new IllegalArgumentException("Invalid setup schema");
            JsonElement enabled = root.get("enabled");
            if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Expected boolean");
            int onset = Math.toIntExact(number(root.get("onsetMinutes")));
            if (schema == 1) {
                long duration = number(root.get("durationMinutes"));
                if (duration < 1 || duration > 60) throw new IllegalArgumentException("Invalid legacy duration");
                return new BanquetConfig(enabled.getAsBoolean(), onset, Math.toIntExact(duration));
            }
            return new BanquetConfig(enabled.getAsBoolean(), onset,
                    Math.toIntExact(number(root.get("symptomMinutes"))),
                    Math.toIntExact(number(root.get("poisonMinutes"))));
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
