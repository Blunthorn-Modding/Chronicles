package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.lastcouncil.house.HouseRoster;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Persists the complete draw before publishing it, so retries never reroll it. */
public final class HouseCastIssuer {
    public interface Storage {
        HouseCast load() throws IOException;
        void save(HouseCast cast) throws IOException;
    }

    private final Storage pending;
    private final Storage published;
    private boolean hasPending;

    public HouseCastIssuer(Path root) throws IOException {
        this(storage(root.resolve("automatic-cast.json")), storage(root.resolve("cast.json")));
    }

    public HouseCastIssuer(Storage pending, Storage published) throws IOException {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.published = Objects.requireNonNull(published, "published");
        hasPending = pending.load() != null;
    }

    public synchronized boolean hasPending() { return hasPending; }

    public synchronized HouseCast ensure(CouncilContent content, HouseRoster roster, LongSupplier seed) throws IOException {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(seed, "seed");
        HouseCast plan = pending.load();
        hasPending = plan != null;
        HouseCast current = published.load();
        if (plan != null) validate(plan, content, roster);
        if (current != null) {
            validate(current, content, roster);
            if (plan != null && !plan.equals(current)) throw new IOException("Published cast differs from automatic cast receipt");
            return current;
        }
        if (plan == null) {
            validateRoster(content, roster);
            plan = HouseCastGenerator.generate(content, roster, seed.getAsLong());
            // A failed save may have published before throwing. Block roster changes until reloaded.
            hasPending = true;
            pending.save(plan);
        }
        published.save(plan);
        return plan;
    }

    private static void validateRoster(CouncilContent content, HouseRoster roster) {
        if (roster.assignments().isEmpty()) throw new IllegalArgumentException("Cannot issue papers without Houses");
        roster.assignments().values().forEach(a -> {
            if (!content.houses().containsKey(a.houseId())) throw new IllegalArgumentException("Unknown assigned House");
        });
    }

    private static void validate(HouseCast cast, CouncilContent content, HouseRoster roster) throws IOException {
        if (cast.rosterRevision() != roster.revision() || !cast.papers().keySet().equals(roster.assignments().keySet()))
            throw new IOException("Cast does not match House roster");
        for (var entry : roster.assignments().entrySet()) {
            String house = entry.getValue().houseId();
            if (!content.houses().containsKey(house) || !house.equals(cast.papers().get(entry.getKey()).houseId()))
                throw new IOException("Cast does not match assigned Houses");
        }
    }

    private static Storage storage(Path path) {
        HouseCastStore store = new HouseCastStore(path);
        return new Storage() {
            public HouseCast load() throws IOException { return store.load(); }
            public void save(HouseCast cast) throws IOException { store.save(cast); }
        };
    }
}
