package io.github.chronicle.core.campaign;

/** Shared session boundary; player movement and presentation are separate concerns. */
public final class SessionGameplayPolicy {
    private SessionGameplayPolicy() { }
    public static boolean frozen(SessionPhase phase, boolean held) {
        return phase != SessionPhase.ACTIVE || held;
    }
    public static boolean canBuild(SessionPhase phase, boolean held, boolean building) {
        if (phase == null || held) return false;
        return phase == SessionPhase.ACTIVE || building;
    }
}
