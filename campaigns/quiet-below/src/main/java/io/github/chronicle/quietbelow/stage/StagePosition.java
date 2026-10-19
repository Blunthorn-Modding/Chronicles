package io.github.chronicle.quietbelow.stage;

/** Integer world coordinate stored without any runtime registry dependency. */
public record StagePosition(int x, int y, int z) {
    public String display() {
        return x + " " + y + " " + z;
    }
}
