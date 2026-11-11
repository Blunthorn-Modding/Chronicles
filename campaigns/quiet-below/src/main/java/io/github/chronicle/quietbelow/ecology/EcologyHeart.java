package io.github.chronicle.quietbelow.ecology;

import java.util.List;
import java.util.Objects;

/** Pure vulnerability clock for a weakened, completely resolved living sculk network. */
public final class EcologyHeart {
    public static final List<Integer> THRESHOLD_CHOICES = List.of(1, 32, 64, 128, 256, 512, 1024, 2048, 4096,8192,16384,32768,65536,EcologyEngine.MAX_CELLS);
    public static final List<Integer> DELAY_CHOICES = List.of(1,5,10,15,30,60,120,300,600,1200,1800,3600);

    public record Rules(int threshold, int seconds) {
        public Rules {
            if (threshold < 0 || threshold > EcologyEngine.MAX_CELLS) {
                throw new IllegalArgumentException("Heart threshold must be zero or 1.." + EcologyEngine.MAX_CELLS);
            }
            if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("Heart vulnerability delay must be 1..3600 seconds");
        }

        /** Zero follows the current Established threshold; a positive value is fixed. */
        public int threshold(int established) {
            if (established < 1 || established > EcologyEngine.MAX_CELLS) {
                throw new IllegalArgumentException("Established threshold must be 1.." + EcologyEngine.MAX_CELLS);
            }
            return threshold == 0 ? established : threshold;
        }

        public int requiredTicks() { return seconds * 20; }
    }

    public static final Rules DEFAULT = new Rules(0, 60);

    /** A pure description of one weakness-clock step. */
    public record Transition(int previous, int current, boolean pulse, boolean lastEffort) {
        public Transition {
            if (previous < 0 || current < previous) throw new IllegalArgumentException("Invalid Heart clock transition");
        }
    }

    /** Complete means every relevant branch is resolved and loaded for this proof. */
    public record Proof(boolean complete, boolean rootValid, int living, int established) {
        public Proof {
            if (living < 0 || living > EcologyEngine.MAX_CELLS) {
                throw new IllegalArgumentException("Living sculk count must be 0.." + EcologyEngine.MAX_CELLS);
            }
            if (established < 1 || established > EcologyEngine.MAX_CELLS) {
                throw new IllegalArgumentException("Established threshold must be 1.." + EcologyEngine.MAX_CELLS);
            }
        }
    }

    private EcologyHeart() { }

    /**
     * Advances only during eligible ACTIVE occupied, unheld ticks. A known eligible pause keeps
     * progress; uncertainty, a broken root, or a network at/above the threshold clears it.
     */
    public static int nextElapsed(Rules rules, int elapsed, Proof proof, boolean advancing, int ticks) {
        Objects.requireNonNull(rules, "Heart rules");
        Objects.requireNonNull(proof, "Heart proof");
        validateElapsed(rules, elapsed);
        if (ticks < 0 || ticks > 20) throw new IllegalArgumentException("Heart clock step must be 0..20 ticks");
        if (!eligible(rules, proof)) return 0;
        if (!advancing) return elapsed;
        return Math.min(rules.requiredTicks(), elapsed + ticks);
    }

    public static boolean vulnerable(Rules rules, int elapsed, Proof proof) {
        Objects.requireNonNull(rules, "Heart rules");
        Objects.requireNonNull(proof, "Heart proof");
        validateElapsed(rules, elapsed);
        return eligible(rules, proof) && elapsed >= rules.requiredTicks();
    }

    /** Crossings are driven by occupied ACTIVE time, so pauses never replay a beat. */
    public static Transition transition(Rules rules, int previous, int current) {
        Objects.requireNonNull(rules, "Heart rules");
        validateElapsed(rules, previous);
        validateElapsed(rules, current);
        if (current < previous) throw new IllegalArgumentException("Heart clock cannot move backwards within a transition");
        boolean pulse = heartbeatTicks(rules).stream().anyMatch(tick -> previous < tick && current >= tick);
        int effort = lastEffortTick(rules);
        return new Transition(previous, current, pulse, previous < effort && current >= effort);
    }

    /** Eight accelerating beats before the final effort; default gaps are10,9,8,7,6,5,3,1seconds.
     * Very short configured waits omit beats rather than emitting more than one per active second. */
    public static List<Integer> heartbeatTicks(Rules rules) {
        Objects.requireNonNull(rules, "Heart rules");
        int effort = lastEffortTick(rules), last = 0;
        var ticks = new java.util.ArrayList<Integer>();
        for (int percent : new int[]{20,38,54,68,80,90,96,98}) {
            int tick = Math.max(20, effort * percent / 100);
            if (tick < effort && tick - last >= 20) { ticks.add(tick); last = tick; }
        }
        return List.copyOf(ticks);
    }

    /** Five sixths of the configured active-play wait: 50 seconds for the default rules. */
    public static int lastEffortTick(Rules rules) {
        Objects.requireNonNull(rules, "Heart rules");
        return Math.max(1, rules.requiredTicks() * 5 / 6);
    }

    static boolean eligible(Rules rules, Proof proof) {
        return proof.complete() && proof.rootValid() && proof.living() < rules.threshold(proof.established());
    }

    private static void validateElapsed(Rules rules, int elapsed) {
        if (elapsed < 0 || elapsed > rules.requiredTicks()) {
            throw new IllegalArgumentException("Heart elapsed ticks must be 0.." + rules.requiredTicks());
        }
    }
}
