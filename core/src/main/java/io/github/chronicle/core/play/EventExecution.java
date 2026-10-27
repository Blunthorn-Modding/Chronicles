package io.github.chronicle.core.play;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable durable authority for one in-flight or terminal unified Event execution. */
public record EventExecution(String eventId, UUID operationId, EventConfiguration configuration,
                             Phase phase, PlayState.DeliverySource source, UUID actor,
                             int session, long elapsedTicks, List<UUID> recipients,
                             String worldPlanHash, String detail) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public enum Phase {
        PREPARED,
        APPLYING,
        TERRAIN_SAVED,
        WORLD_COMMITTED,
        EVENT_OUTCOME,
        MEDIA_CLAIMED,
        MEDIA_UNCERTAIN,
        COMPLETE,
        CONFLICT
    }

    public EventExecution {
        ObservationCatalog.requireIdentifier(eventId, "event execution ID");
        Objects.requireNonNull(operationId, "event execution operation ID is required");
        Objects.requireNonNull(configuration, "event execution configuration is required");
        if (!eventId.equals(configuration.definition().id())) {
            throw new IllegalArgumentException("event execution ID must match its definition ID");
        }
        Objects.requireNonNull(phase, "event execution phase is required");
        Objects.requireNonNull(source, "event execution source is required");
        if ((source == PlayState.DeliverySource.MANUAL || source == PlayState.DeliverySource.ENTRY)
                && actor == null) {
            throw new IllegalArgumentException(source.name().toLowerCase(java.util.Locale.ROOT)
                    + " event execution requires an actor");
        }
        if (session < 1) throw new IllegalArgumentException("event execution session must be positive");
        if (elapsedTicks < 0) throw new IllegalArgumentException("event execution time must not be negative");
        if (recipients == null || recipients.isEmpty() || recipients.size() > PlayState.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("event execution must contain 1-" + PlayState.MAX_PARTICIPANTS + " recipients");
        }
        Set<UUID> distinct = new HashSet<>();
        for (UUID recipient : recipients) {
            if (recipient == null || !distinct.add(recipient)) {
                throw new IllegalArgumentException("event execution recipients must be distinct UUIDs");
            }
        }
        recipients = List.copyOf(recipients);
        if (worldPlanHash == null) {
            throw new IllegalArgumentException("event execution world-plan hash is required; use an empty string for no Region changes");
        }
        boolean hasWorldChanges = !configuration.definition().changes().isEmpty();
        if (hasWorldChanges) {
            if (!SHA_256.matcher(worldPlanHash).matches()) {
                throw new IllegalArgumentException("a world-changing event execution requires a lowercase SHA-256 plan hash");
            }
        } else if (!worldPlanHash.isEmpty()) {
            throw new IllegalArgumentException("a media-only event execution must not have a world-plan hash");
        }
        ObservationCatalog.requirePlain(detail, 2_048, true, "event execution detail");
        if (!hasWorldChanges && (phase == Phase.APPLYING || phase == Phase.TERRAIN_SAVED || phase == Phase.CONFLICT)) {
            throw new IllegalArgumentException("a media-only event execution cannot enter a terrain phase");
        }
    }

    public EventExecution with(Phase next, String detail) {
        return new EventExecution(eventId, operationId, configuration, next, source, actor, session,
                elapsedTicks, recipients, worldPlanHash, detail);
    }

    public boolean terminal() {
        return phase == Phase.COMPLETE || phase == Phase.MEDIA_UNCERTAIN;
    }

    /** Stable digest of the complete trigger/configuration pin used by this execution. */
    public String configurationFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "chronicle:event-configuration:v1");
            update(digest, configuration.definition().fingerprint());
            update(digest, configuration.mode().name());
            update(digest, configuration.triggerRegion().isPresent());
            configuration.triggerRegion().ifPresent(reference -> {
                update(digest, reference.id());
                update(digest, reference.revision());
            });
            update(digest, configuration.armedAtTicks());
            update(digest, configuration.delayTicks());
            update(digest, configuration.requiredFlag());
            update(digest, configuration.enabled());
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

    private static void update(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }
}
