package io.github.chronicle.core.lore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Bounded weighted sampling without replacement, independent of input ordering. */
public final class WeightedDraw {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_WEIGHT = 1_000_000;
    private WeightedDraw() {}

    public record Entry<T>(String id, int weight, T value) {
        public Entry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("entry ID is required");
            if (weight < 1 || weight > MAX_WEIGHT) throw new IllegalArgumentException("weight outside bounds");
            Objects.requireNonNull(value, "value");
        }
    }

    public static <T> List<Entry<T>> draw(List<Entry<T>> entries, int count, RandomGenerator random) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(random, "random");
        if (entries.size() > MAX_ENTRIES || count < 0 || count > MAX_ENTRIES || count > entries.size())
            throw new IllegalArgumentException("draw count or entries outside bounds");
        List<Entry<T>> remaining = new ArrayList<>(entries.size());
        var ids = new HashSet<String>();
        long total = 0;
        for (Entry<T> entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.id())) throw new IllegalArgumentException("duplicate entry ID");
            remaining.add(entry);
            total += entry.weight();
        }
        remaining.sort(Comparator.comparing(Entry::id));
        List<Entry<T>> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long choice = random.nextLong(total);
            for (int j = 0; j < remaining.size(); j++) {
                Entry<T> entry = remaining.get(j);
                if (choice < entry.weight()) {
                    result.add(entry);
                    remaining.remove(j);
                    total -= entry.weight();
                    break;
                }
                choice -= entry.weight();
            }
        }
        return List.copyOf(result);
    }
}
