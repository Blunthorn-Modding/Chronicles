package io.github.chronicle.quietbelow.ecology;

/** Ephemeral diagnostic labels for the work actually reached in one engine tick. */
enum EcologyTickWork {
    NOT_TICKED(false), NOT_PLAYING(false), DISABLED(false), UNINDEXED(false), UNCONFIGURED(false),
    TOPOLOGY_UNKNOWN(false), TOPOLOGY_REBUILD(false), EXTENSION_WAIT(false), DISCONNECTED(false),
    MAINTENANCE(false), FAILURE(false),
    CONNECTION_ADOPTED(true), WRITE_BUDGET(true), COUNTDOWN(true), GROWTH_ATTEMPT(true), HEART_DEFENCE(true);

    private final boolean active;
    EcologyTickWork(boolean active){this.active=active;}
    boolean active(){return active;}
    static EcologyTickWork observed(boolean maintenanceAtStart,boolean maintenanceNow,boolean failed,EcologyTickWork actual){
        if(maintenanceAtStart||maintenanceNow)return MAINTENANCE;
        return failed?FAILURE:actual;
    }
}
