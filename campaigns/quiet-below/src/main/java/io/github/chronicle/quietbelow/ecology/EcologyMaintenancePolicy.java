package io.github.chronicle.quietbelow.ecology;

/** Internal storage headroom policy; no gameplay clock or user setting is changed. */
record EcologyMaintenancePolicy(long quietBytes,long quietRecords,long hardBytes,long hardRecords,
                                long retryTicks,long growthBytes,long growthRecords) {
    static final EcologyMaintenancePolicy DEFAULT=new EcologyMaintenancePolicy(16L<<20,37_500,32L<<20,75_000,6_000,8L<<20,12_500);
    EcologyMaintenancePolicy {
        if(quietBytes<1||quietRecords<1||hardBytes<quietBytes||hardRecords<quietRecords||hardBytes>=EcologyJournal.MAX_FILE_BYTES
                ||hardRecords>=EcologyJournal.MAX_ENTRIES||retryTicks<0||growthBytes<1||growthRecords<1)
            throw new IllegalArgumentException("Invalid automatic journal maintenance policy");
    }
    boolean due(boolean quiet,long bytes,long records,long now,long lastBytes,long lastRecords,long lastTick) {
        boolean urgent=bytes>=hardBytes||records>=hardRecords;
        if(!urgent&&!(quiet&&(bytes>=quietBytes||records>=quietRecords)))return false;
        if(lastTick<0)return true;
        boolean progress=bytes-lastBytes>=growthBytes||records-lastRecords>=growthRecords;
        // A busy server must not hit its hard file cap while waiting out a quiet-period cooldown.
        return progress&&(urgent||now-lastTick>=retryTicks);
    }
}
