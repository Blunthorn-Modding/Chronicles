package io.github.chronicle.lastcouncil.banquet;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.*;

/** Save-before-publication wrapper. Does not authorize or transact Minecraft inventory changes. */
public final class BanquetService {
    private final BanquetRepository repository;
    private BanquetState state;

    public BanquetService(BanquetRepository repository) throws IOException {
        this.repository = java.util.Objects.requireNonNull(repository);
        state = repository.load();
    }

    public synchronized BanquetState snapshot() { return state; }

    public synchronized BanquetState issue(long revision, UUID request, UUID actor, UUID token, SupplyKind kind) throws IOException {
        repository.verifyUnchanged();
        return publish(BanquetTransitions.issue(state, revision, request, actor, token, kind));
    }
    public synchronized BanquetState contaminate(long revision, UUID request, UUID actor, UUID poison, UUID drink) throws IOException {
        repository.verifyUnchanged();
        return publish(BanquetTransitions.contaminate(state, revision, request, actor, poison, drink));
    }
    public synchronized BanquetState drink(long revision, UUID request, UUID actor, UUID token, Timing timing, RepeatPolicy repeat) throws IOException {
        repository.verifyUnchanged();
        return publish(BanquetTransitions.drink(state, revision, request, actor, token, timing, repeat));
    }
    public synchronized BanquetState antidote(long revision, UUID request, UUID actor, UUID token) throws IOException {
        repository.verifyUnchanged();
        return publish(BanquetTransitions.antidote(state, revision, request, actor, token));
    }
    public synchronized BanquetState advance(long ticks, Set<UUID> eligible) throws IOException {
        repository.verifyUnchanged();
        return publish(BanquetTransitions.advance(state, ticks, eligible));
    }

    private BanquetState publish(BanquetState next) throws IOException {
        if (next != state) {
            repository.save(next);
            state = next;
        }
        return state;
    }
}
