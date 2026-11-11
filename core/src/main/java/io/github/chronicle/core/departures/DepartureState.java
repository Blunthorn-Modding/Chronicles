package io.github.chronicle.core.departures;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Complete world-scoped departure authority. History is bounded by refusal, never truncation. */
public record DepartureState(long revision,
                             Map<String, DepartureDefinition> definitions,
                             Set<String> tombstones,
                             Map<String, UUID> usedByDefinition,
                             Map<UUID, DepartureOperation> operations,
                             Map<UUID, UUID> departedByPlayer,
                             Optional<CampaignConclusion> concluded) {
    public static final int MAX_DEFINITIONS = 128;
    public static final int MAX_HISTORY = 4096;
    public static final int MAX_DEPARTED_PLAYERS = 512;

    public DepartureState {
        if (revision < 0) throw new IllegalArgumentException("departure state revision must be non-negative");
        if (definitions == null || definitions.size() > MAX_DEFINITIONS) throw new IllegalArgumentException("too many departure definitions");
        if (tombstones == null || tombstones.size() > MAX_HISTORY) throw new IllegalArgumentException("too many departure tombstones");
        if (usedByDefinition == null || usedByDefinition.size() > MAX_DEFINITIONS) throw new IllegalArgumentException("too many used departure definitions");
        if (operations == null || operations.size() > MAX_HISTORY) throw new IllegalArgumentException("departure operation history is full");
        if (departedByPlayer == null || departedByPlayer.size() > MAX_DEPARTED_PLAYERS) throw new IllegalArgumentException("too many departed players");
        concluded = concluded == null ? Optional.empty() : concluded;

        LinkedHashSet<String> checkedTombstones = new LinkedHashSet<>();
        tombstones.stream().sorted().forEach(id -> {
            DepartureValidation.id(id, "departure tombstone ID");
            if (!checkedTombstones.add(id)) throw new IllegalArgumentException("duplicate departure tombstone");
        });
        LinkedHashMap<String, DepartureDefinition> checkedDefinitions = new LinkedHashMap<>();
        definitions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getValue() == null || !entry.getKey().equals(entry.getValue().id()) || checkedTombstones.contains(entry.getKey())) throw new IllegalArgumentException("invalid departure definition entry");
            checkedDefinitions.put(entry.getKey(), entry.getValue());
        });
        LinkedHashMap<UUID, DepartureOperation> checkedOperations = new LinkedHashMap<>();
        operations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getValue() == null || !entry.getKey().equals(entry.getValue().operationId())) throw new IllegalArgumentException("invalid departure operation entry");
            checkedOperations.put(entry.getKey(), entry.getValue());
        });
        LinkedHashMap<String, UUID> checkedUsed = new LinkedHashMap<>();
        usedByDefinition.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            DepartureValidation.id(entry.getKey(), "used departure ID");
            DepartureOperation operation = checkedOperations.get(entry.getValue());
            if (operation == null || !operation.definition().id().equals(entry.getKey())) throw new IllegalArgumentException("used departure does not point to its operation");
            DepartureDefinition retained = checkedDefinitions.get(entry.getKey());
            if (retained == null || !retained.equals(operation.definition())) throw new IllegalArgumentException("used departure definition history is not retained exactly");
            checkedUsed.put(entry.getKey(), entry.getValue());
        });
        LinkedHashMap<UUID, UUID> checkedDeparted = new LinkedHashMap<>();
        departedByPlayer.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey() == null || entry.getValue() == null) throw new IllegalArgumentException("departed player entry is invalid");
            DepartureOperation operation = checkedOperations.get(entry.getValue());
            if (operation == null || !operation.onlineParticipants().contains(entry.getKey())) throw new IllegalArgumentException("departed player does not point to a matching operation");
            checkedDeparted.put(entry.getKey(), entry.getValue());
        });
        for (DepartureOperation operation : checkedOperations.values()) {
            if (!operation.operationId().equals(checkedUsed.get(operation.definition().id()))) throw new IllegalArgumentException("operation has no exact used-definition reservation");
            for (UUID player : operation.onlineParticipants()) if (!operation.operationId().equals(checkedDeparted.get(player))) throw new IllegalArgumentException("operation passenger has no permanent departed flag");
        }
        concluded.ifPresent(outcome -> {
            DepartureOperation operation = checkedOperations.get(outcome.operationId());
            if (operation == null || !operation.concludeCampaign() || operation.stage().ordinal() < DepartureOperation.Stage.CONCLUSION_SAVED.ordinal()
                    || !operation.conclusion().orElseThrow().equals(outcome.receipt())) throw new IllegalArgumentException("campaign conclusion does not match its operation evidence");
        });
        for (DepartureOperation operation : checkedOperations.values()) {
            if (operation.stage().ordinal() >= DepartureOperation.Stage.CONCLUSION_SAVED.ordinal() && operation.concludeCampaign()
                    && (concluded.isEmpty() || !concluded.get().operationId().equals(operation.operationId()))) throw new IllegalArgumentException("concluding operation has no permanent outcome");
        }

        definitions = Collections.unmodifiableMap(checkedDefinitions);
        tombstones = Collections.unmodifiableSet(checkedTombstones);
        usedByDefinition = Collections.unmodifiableMap(checkedUsed);
        operations = Collections.unmodifiableMap(checkedOperations);
        departedByPlayer = Collections.unmodifiableMap(checkedDeparted);
    }

    public static DepartureState empty() { return new DepartureState(0, Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), Optional.empty()); }
    public boolean isDeparted(UUID player) { return departedByPlayer.containsKey(player); }
}
