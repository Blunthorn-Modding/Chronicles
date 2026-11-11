package io.github.chronicle.core.departures;

/** Caller attestation. SAVED evidence must name durable native proof; it is not proof by itself. */
public record DepartureReceipt(Disposition disposition, String evidence) {
    public enum Disposition { SAVED, NOT_REQUIRED }
    public DepartureReceipt {
        if (disposition == null) throw new IllegalArgumentException("receipt disposition is required");
        evidence = evidence == null ? "" : evidence;
        DepartureValidation.text(evidence, disposition == Disposition.SAVED ? 1 : 0, 512, "receipt evidence");
        if (disposition == Disposition.NOT_REQUIRED && !evidence.isEmpty()) throw new IllegalArgumentException("a not-required receipt cannot contain evidence");
    }
    public static DepartureReceipt saved(String evidence) { return new DepartureReceipt(Disposition.SAVED, evidence); }
    public static DepartureReceipt notRequired() { return new DepartureReceipt(Disposition.NOT_REQUIRED, ""); }
}
