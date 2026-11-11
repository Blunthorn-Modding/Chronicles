package io.github.chronicle.core.areas;

/** Pure per-cell comparison. Third-state evidence is a conflict and is never blindly rolled back. */
public final class AreaRecovery{
    public enum CellStatus{BEFORE,AFTER,CONFLICT}
    private AreaRecovery(){}
    public static Plan plan(AreaOperation operation,AreaSnapshot before,AreaSnapshot after){return new Plan(operation,before,after);}
    public static CellStatus classify(AreaOperation operation,int cell,AreaCell live,AreaSnapshot before,AreaSnapshot after){return plan(operation,before,after).classify(cell,live);}
    public static final class Plan{
        private final AreaOperation operation;private final AreaSnapshot before,after;
        private Plan(AreaOperation operation,AreaSnapshot before,AreaSnapshot after){if(operation==null||operation.intent()==AreaOperation.Intent.DELETE_UNCHANGED)throw new IllegalArgumentException("an apply operation is required");if(!AreaSnapshotCodec.encode(before).ref().equals(operation.before().orElseThrow())||!AreaSnapshotCodec.encode(after).ref().equals(operation.after().orElseThrow()))throw new IllegalArgumentException("recovery snapshots do not match pinned operation evidence");this.operation=operation;this.before=before;this.after=after;}
        public int cells(){return operation.bounds().cells();}
        public CellStatus classify(int cell,AreaCell live){if(cell<0||cell>=cells())throw new IndexOutOfBoundsException(cell);AreaCell expectedAfter=after.cell(cell);if(expectedAfter.equals(live))return CellStatus.AFTER;return before.cell(cell).equals(live)?CellStatus.BEFORE:CellStatus.CONFLICT;}
    }
}
