package io.github.chronicle.core.menu;

/** Presentation-only rules for the operator menu's local Clean/Debug preference. */
public final class CoreMenuPresentation {
    private CoreMenuPresentation() { }

    /** Clean mode is deliberately sparse: facts are an all-or-nothing Debug surface. */
    public static boolean showFact(CoreMenuModel.Fact fact, boolean debug) {
        return debug;
    }

    /** Keep ordinary network round trips visually quiet; show a wait after 200 ms or in Debug. */
    public static boolean showPendingWait(boolean pending, int ticks, boolean debug) {
        return pending && (debug || ticks >= 4);
    }

    /** Clean view reserves the footer for pending, errors, and meaningful action results. */
    public static boolean showFooterMessage(String notice, boolean pending, boolean debug) {
        return debug || pending || (!notice.isBlank() && !notice.equals("Server state updated."));
    }
}
