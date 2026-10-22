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

    private static boolean eligible(Rules rules, Proof proof) {
        return proof.complete() && proof.rootValid() && proof.living() < rules.threshold(proof.established());
    }

    private static void validateElapsed(Rules rules, int elapsed) {
        if (elapsed < 0 || elapsed > rules.requiredTicks()) {
            throw new IllegalArgumentException("Heart elapsed ticks must be 0.." + rules.requiredTicks());
        }
    }
}
