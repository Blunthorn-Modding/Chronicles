package io.github.chronicle.quietbelow.ecology;

/** Pure, four-band projection. Unknown evidence retains the saved appearance. */
public final class EcologyAppearance {
    public static final String PROPERTY = "quiet_below_vitality";
    public static final int HEALTHY=0, DULL=1, FADING=2, EXHAUSTED=3;
    public static final int READS=128, WRITES=16;

    private EcologyAppearance() { }

    public static int stage(int value, boolean rooted, EcologyVitality.Mode feeding) {
        if(value<0||value>EcologyVitality.FULL)throw new IllegalArgumentException("Invalid vitality");
        if(feeding==null||feeding==EcologyVitality.Mode.UNKNOWN)return -1;
        if(value==0)return EXHAUSTED;
        if(value<EcologyVitality.READY)return FADING;
        return rooted && value>=EcologyVitality.FULL*3/4
                && (feeding==EcologyVitality.Mode.SHADED||feeding==EcologyVitality.Mode.LIT) ? HEALTHY:DULL;
    }
}
