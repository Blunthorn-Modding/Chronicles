package io.github.chronicle.core.lore;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Inclusive signed integer range for reusable randomized content. */
public record NumberRange(int min, int max) {
    public NumberRange {
        if (min > max) throw new IllegalArgumentException("invalid number range");
    }

    public int draw(RandomGenerator random) {
        Objects.requireNonNull(random);
        return (int) (min + random.nextLong((long) max - min + 1));
    }
}
