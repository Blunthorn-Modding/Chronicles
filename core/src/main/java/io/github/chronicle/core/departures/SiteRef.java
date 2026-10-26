package io.github.chronicle.core.departures;

/** A reference to geometry owned by the authoring registry; no live coordinates are copied here. */
public record SiteRef(String id, long revision) {
    public SiteRef {
        id = DepartureValidation.id(id, "site reference ID");
        if (revision < 1) throw new IllegalArgumentException("site reference revision must be positive");
    }
}
