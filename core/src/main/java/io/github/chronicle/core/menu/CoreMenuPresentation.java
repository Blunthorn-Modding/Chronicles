package io.github.chronicle.core.menu;

import java.util.Set;

/**
 * Presentation-only classification for the operator menu. The server always sends its
 * bounded facts; a client may hide these specifically named diagnostics in Clean view.
 */
public final class CoreMenuPresentation {
    private static final Set<String> TECHNICAL_FACT_LABELS = Set.of(
            "Active play",
            "Authority",
            "Block",
            "Clock",
            "Entry/audience region",
            "Assessment",
            "Attempts",
            "Audit detail",
            "How to test",
            "Loaded inventory queue",
            "Loot",
            "Managed block revision",
            "Native mechanics",
            "Network",
            "Other references",
            "Persistence",
            "Purchases",
            "Registered block",
            "Session boundaries",
            "Since last reset",
            "Since reset",
            "Sound ID",
            "Speed controls",
            "Starting-position history",
            "Tool revision",
            "Work bounds",
            "Surface frontier"
    );

    private CoreMenuPresentation() { }

    /** Unknown labels stay visible deliberately; values are never pattern-matched. */
    public static boolean showFact(CoreMenuModel.Fact fact, boolean debug) {
        return debug || !TECHNICAL_FACT_LABELS.contains(fact.label());
    }

    public static boolean technicalFactLabel(String label) {
        return TECHNICAL_FACT_LABELS.contains(label);
    }

    /** Clean view reserves the footer for pending, errors, and meaningful action results. */
    public static boolean showFooterMessage(String notice, boolean pending, boolean debug) {
        return debug || pending || (!notice.isBlank() && !notice.equals("Server state updated."));
    }
}
