package io.github.chronicle.core.offering;

/** Snapshotted additive terms. Speed/exhaustion use percent of their native base values. */
public record OfferingTerms(int health, int food, int speedPercent, int exhaustionPercent) {
    public static final OfferingTerms ZERO = new OfferingTerms(0, 0, 0, 0);
    /** Legacy food/speed definitions retain exactly their old terms. */
    public OfferingTerms(int health,int food,int speedPercent) { this(health,food,speedPercent,0); }
    public OfferingTerms {
        if (health < -4096 || health > 4096 || food < -4096 || food > 4096
                || speedPercent < -16384 || speedPercent > 16384 || exhaustionPercent < 0 || exhaustionPercent > 16384)
            throw new IllegalArgumentException("offering totals exceed supported bounds");
    }
    public OfferingTerms plus(OfferingTerms other) {
        return new OfferingTerms(Math.addExact(health,other.health),Math.addExact(food,other.food),
                Math.addExact(speedPercent,other.speedPercent),Math.addExact(exhaustionPercent,other.exhaustionPercent));
    }
    public void validatePurchase() {
        if(Math.abs(health)>20 || Math.abs(food)>20 || Math.abs(speedPercent)>100 || exhaustionPercent>100 || equals(ZERO))
            throw new IllegalArgumentException("offering purchase terms must be nonzero and within health/food +/-20, speed +/-100%, exhaustion 0..100%");
    }
}
