package io.github.chronicle.core.departures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Fully revalidated native intent presented at the irreversible boundary. */
public record DepartureIntent(UUID operationId, String definitionId, long expectedDefinitionRevision,
                              UUID actor, List<UUID> onlineParticipants, boolean concludeCampaign,
                              Optional<PreparedAreaEffect> preparedAreaEffect, String createdAt) {
    public static final int MAX_PARTICIPANTS = 512;
    public DepartureIntent {
        Objects.requireNonNull(operationId, "departure operation ID is required");
        definitionId = DepartureValidation.id(definitionId, "departure definition ID");
        if (expectedDefinitionRevision < 1) throw new IllegalArgumentException("expected definition revision must be positive");
        Objects.requireNonNull(actor, "departure actor is required");
        if (onlineParticipants == null || onlineParticipants.isEmpty() || onlineParticipants.size() > MAX_PARTICIPANTS) throw new IllegalArgumentException("departure roster must contain 1 to 512 online participants");
        HashSet<UUID> unique = new HashSet<>();
        ArrayList<UUID> normalized = new ArrayList<>(onlineParticipants.size());
        for (UUID player : onlineParticipants) {
            if (player == null || !unique.add(player)) throw new IllegalArgumentException("departure roster contains a null or duplicate player");
            normalized.add(player);
        }
        if (!unique.contains(actor)) throw new IllegalArgumentException("departure actor must be in the exact online roster");
        normalized.sort(Comparator.comparing(UUID::toString));
        onlineParticipants = List.copyOf(normalized);
        preparedAreaEffect = preparedAreaEffect == null ? Optional.empty() : preparedAreaEffect;
        createdAt = DepartureValidation.instant(createdAt, "departure creation time");
    }
}
