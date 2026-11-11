package io.github.chronicle.core.stats;

/** Pure scaling policy shared by the server hunger hook and boundary tests. */
public final class OfferingExhaustion {
    private OfferingExhaustion() { }
    public static float scale(float amount,double multiplier) {
        if(!Float.isFinite(amount)||amount<=0||!Double.isFinite(multiplier)||multiplier<1)return amount;
        double result=amount*multiplier;
        return (float)Math.min(result,Float.MAX_VALUE);
    }
}
