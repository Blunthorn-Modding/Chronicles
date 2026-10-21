package io.github.chronicle.core.offering;

/** A snapshotted, additive trade. Health/food use points; speed uses percent of base speed. */
public record OfferingTerms(int health, int food, int speedPercent) {
    public static final OfferingTerms ZERO = new OfferingTerms(0, 0, 0);

    public OfferingTerms {
        if (health < -4096 || health > 4096 || food < -4096 || food > 4096
                || speedPercent < -16384 || speedPercent > 16384) {
            throw new IllegalArgumentException("offering totals exceed supported bounds");
        }
    }

    public OfferingTerms plus(OfferingTerms other) {
        return new OfferingTerms(Math.addExact(health, other.health), Math.addExact(food, other.food),
                Math.addExact(speedPercent, other.speedPercent));
    }

    public void validatePurchase() {
        if (Math.abs(health) > 20 || Math.abs(food) > 20 || Math.abs(speedPercent) > 100 || equals(ZERO)) {
            throw new IllegalArgumentException("offering purchase terms must be nonzero and within health/food +/-20, speed +/-100%");
        }
    }
}
