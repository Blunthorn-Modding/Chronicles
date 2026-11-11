package io.github.chronicle.quietbelow.stage;

public enum StageOperationStatus {
    APPLYING,
    APPLIED,
    UNDOING,
    UNDONE,
    INTERRUPTED,
    CONFLICT,
    RECOVERY_REQUIRED
}
