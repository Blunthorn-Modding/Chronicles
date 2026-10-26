package io.github.chronicle.core.departures;

import java.util.Optional;

/** Immutable author-facing definition. Runtime execution additionally requires both site references. */
public record DepartureDefinition(String id, String name, long revision,
                                  Optional<SiteRef> exactBlockAnchor, Optional<SiteRef> boardingRegion,
                                  String firstText, String finalText, Optional<AreaLink> areaLink,
                                  boolean singleUse) {
    public static final int MAX_NAME_BYTES = 96;
    public static final int MAX_TEXT_BYTES = 4096;

    public DepartureDefinition {
        id = DepartureValidation.id(id, "departure ID");
        name = DepartureValidation.text(name, 1, MAX_NAME_BYTES, "departure name");
        if (revision < 1) throw new IllegalArgumentException("departure revision must be positive");
        exactBlockAnchor = exactBlockAnchor == null ? Optional.empty() : exactBlockAnchor;
        boardingRegion = boardingRegion == null ? Optional.empty() : boardingRegion;
        firstText = DepartureValidation.text(firstText, 1, MAX_TEXT_BYTES, "first confirmation text");
        finalText = DepartureValidation.text(finalText, 1, MAX_TEXT_BYTES, "final confirmation text");
        areaLink = areaLink == null ? Optional.empty() : areaLink;
        if (!singleUse) throw new IllegalArgumentException("departure definitions are single-use in this version");
    }

    public DepartureDefinition withRevision(long nextRevision) {
        return new DepartureDefinition(id, name, nextRevision, exactBlockAnchor, boardingRegion, firstText, finalText, areaLink, singleUse);
    }

    public void requireExecutable() {
        if (exactBlockAnchor.isEmpty()) throw new IllegalStateException("departure has no exact block anchor");
        if (boardingRegion.isEmpty()) throw new IllegalStateException("departure has no boarding region");
    }
}
