package io.github.chronicle.core.play;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Startup-only registry of bounded, campaign-supplied public observations. */
public final class ObservationCatalog {
    public static final int MAX_DEFINITIONS = 128;
    public static final int MAX_ID_LENGTH = 128;
    public static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum Audience { PARTICIPANTS, REGION }

    public record Definition(String id, String label, String text, String sound, Audience audience) {
        public Definition {
            requireIdentifier(id, "observation ID");
            requirePlain(label, 80, false, "observation label");
            requirePlain(text, 512, false, "observation text");
            if (sound == null) throw new IllegalArgumentException("observation sound is required; use an empty string for no sound");
            if (!sound.isEmpty()) requireIdentifier(sound, "observation sound");
            if (audience == null) throw new IllegalArgumentException("observation audience is required");
        }

        /** Stable digest of every campaign-controlled definition field used by a saved binding. */
        public String fingerprint() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                update(digest, id);
                update(digest, label);
                update(digest, text);
                update(digest, sound);
                update(digest, audience.name());
                return HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        private static void update(MessageDigest digest, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }

    private final TreeMap<String, Definition> definitions = new TreeMap<>();
    private boolean frozen;

    public synchronized void register(Definition definition) {
        if (frozen) throw new IllegalStateException("observation catalog is frozen");
        if (definition == null) throw new IllegalArgumentException("observation definition is required");
        if (definitions.containsKey(definition.id())) throw new IllegalArgumentException("duplicate observation definition: " + definition.id());
        if (definitions.size() >= MAX_DEFINITIONS) throw new IllegalArgumentException("observation catalog accepts at most " + MAX_DEFINITIONS + " definitions");
        definitions.put(definition.id(), definition);
    }

    public synchronized Optional<Definition> get(String id) {
        requireIdentifier(id, "observation ID");
        return Optional.ofNullable(definitions.get(id));
    }

    public synchronized List<Definition> definitions() { return List.copyOf(definitions.values()); }

    public synchronized void freeze() { frozen = true; }

    public synchronized boolean frozen() { return frozen; }

    static void requireIdentifier(String value, String name) {
        if (value == null || value.length() > MAX_ID_LENGTH || !NAMESPACED_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a namespaced ID of at most " + MAX_ID_LENGTH + " characters");
        }
    }

    static void requirePlain(String value, int maximum, boolean emptyAllowed, String name) {
        if (value == null || value.length() > maximum || (!emptyAllowed && value.isBlank())) {
            throw new IllegalArgumentException(name + " must be " + (emptyAllowed ? "at most " : "1-") + maximum + " plain-text characters");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) throw new IllegalArgumentException(name + " must be plain text");
        }
    }
}
