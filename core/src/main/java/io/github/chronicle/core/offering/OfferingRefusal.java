package io.github.chronicle.core.offering;

/** An expected gameplay refusal, distinct from stale state or unavailable authority. */
public final class OfferingRefusal extends IllegalStateException {
    public OfferingRefusal(String message) { super(message); }
}
