package io.github.chronicle.core.managed;

/** Cheap menu-frame invalidation token; statusRevision covers readiness, problem and observed status. */
public record ManagedBlockRevision(long instanceRevision, long policyRevision, long statusRevision, boolean ready) { }
