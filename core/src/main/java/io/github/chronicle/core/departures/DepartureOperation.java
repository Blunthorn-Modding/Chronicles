package io.github.chronicle.core.departures;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable recovery evidence. Stages attest durable effects; they never perform native effects. */
public record DepartureOperation(UUID operationId, DepartureDefinition definition, UUID actor,
                                 List<UUID> onlineParticipants, boolean concludeCampaign,
                                 Optional<PreparedAreaEffect> preparedAreaEffect, Stage stage,
                                 Optional<DepartureReceipt> playerEffects,
                                 Optional<DepartureReceipt> areaEffects,
                                 Optional<DepartureReceipt> conclusion,
                                 String createdAt) {
    public enum Stage { COMMITTED, PLAYER_EFFECTS_SAVED, AREA_EFFECTS_SAVED, CONCLUSION_SAVED, COMPLETE }

    public DepartureOperation {
        Objects.requireNonNull(operationId, "departure operation ID is required");
        Objects.requireNonNull(definition, "pinned departure definition is required");
        Objects.requireNonNull(actor, "departure actor is required");
        onlineParticipants = new DepartureIntent(operationId, definition.id(), definition.revision(), actor,
                onlineParticipants, concludeCampaign, preparedAreaEffect, createdAt).onlineParticipants();
        preparedAreaEffect = preparedAreaEffect == null ? Optional.empty() : preparedAreaEffect;
        Objects.requireNonNull(stage, "departure stage is required");
        playerEffects = playerEffects == null ? Optional.empty() : playerEffects;
        areaEffects = areaEffects == null ? Optional.empty() : areaEffects;
        conclusion = conclusion == null ? Optional.empty() : conclusion;
        createdAt = DepartureValidation.instant(createdAt, "departure creation time");
        definition.requireExecutable();
        if (definition.areaLink().isPresent() != preparedAreaEffect.isPresent()) throw new IllegalArgumentException("prepared area evidence does not match the departure link");
        if (preparedAreaEffect.isPresent() && !preparedAreaEffect.get().matches(definition.areaLink().orElseThrow())) throw new IllegalArgumentException("prepared area evidence does not match the pinned link");
        int ordinal = stage.ordinal();
        if (playerEffects.isPresent() != (ordinal >= Stage.PLAYER_EFFECTS_SAVED.ordinal())) throw new IllegalArgumentException("player receipt does not match operation stage");
        if (areaEffects.isPresent() != (ordinal >= Stage.AREA_EFFECTS_SAVED.ordinal())) throw new IllegalArgumentException("area receipt does not match operation stage");
        if (conclusion.isPresent() != (ordinal >= Stage.CONCLUSION_SAVED.ordinal())) throw new IllegalArgumentException("conclusion receipt does not match operation stage");
        playerEffects.ifPresent(receipt -> { if (receipt.disposition() != DepartureReceipt.Disposition.SAVED) throw new IllegalArgumentException("player effects require saved native evidence"); });
        boolean hasPreparedAreaEffect = preparedAreaEffect.isPresent();
        areaEffects.ifPresent(receipt -> requireDisposition(receipt, hasPreparedAreaEffect, "area"));
        conclusion.ifPresent(receipt -> requireDisposition(receipt, concludeCampaign, "conclusion"));
    }

    public static DepartureOperation committed(DepartureDefinition definition, DepartureIntent intent) {
        return new DepartureOperation(intent.operationId(), definition, intent.actor(), intent.onlineParticipants(), intent.concludeCampaign(),
                intent.preparedAreaEffect(), Stage.COMMITTED, Optional.empty(), Optional.empty(), Optional.empty(), intent.createdAt());
    }

    public boolean matches(DepartureIntent intent) {
        return operationId.equals(intent.operationId()) && definition.id().equals(intent.definitionId())
                && definition.revision() == intent.expectedDefinitionRevision() && actor.equals(intent.actor())
                && onlineParticipants.equals(intent.onlineParticipants()) && concludeCampaign == intent.concludeCampaign()
                && preparedAreaEffect.equals(intent.preparedAreaEffect()) && createdAt.equals(intent.createdAt());
    }

    DepartureOperation withPlayer(DepartureReceipt receipt) { return new DepartureOperation(operationId,definition,actor,onlineParticipants,concludeCampaign,preparedAreaEffect,Stage.PLAYER_EFFECTS_SAVED,Optional.of(receipt),Optional.empty(),Optional.empty(),createdAt); }
    DepartureOperation withArea(DepartureReceipt receipt) { return new DepartureOperation(operationId,definition,actor,onlineParticipants,concludeCampaign,preparedAreaEffect,Stage.AREA_EFFECTS_SAVED,playerEffects,Optional.of(receipt),Optional.empty(),createdAt); }
    DepartureOperation withConclusion(DepartureReceipt receipt) { return new DepartureOperation(operationId,definition,actor,onlineParticipants,concludeCampaign,preparedAreaEffect,Stage.CONCLUSION_SAVED,playerEffects,areaEffects,Optional.of(receipt),createdAt); }
    DepartureOperation complete() { return new DepartureOperation(operationId,definition,actor,onlineParticipants,concludeCampaign,preparedAreaEffect,Stage.COMPLETE,playerEffects,areaEffects,conclusion,createdAt); }

    private static void requireDisposition(DepartureReceipt receipt, boolean required, String field) {
        DepartureReceipt.Disposition expected = required ? DepartureReceipt.Disposition.SAVED : DepartureReceipt.Disposition.NOT_REQUIRED;
        if (receipt.disposition() != expected) throw new IllegalArgumentException(field + " receipt disposition does not match the pinned obligation");
    }
}
