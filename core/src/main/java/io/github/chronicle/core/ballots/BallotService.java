package io.github.chronicle.core.ballots;

import java.io.IOException;
import java.time.Clock;
import java.util.*;

/** Pure ballot authority. Callers must enforce operator, session and location permissions. */
public final class BallotService {
    private final BallotRepository repository;
    private final Clock clock;
    private BallotState state;
    public BallotService(BallotRepository repository, Clock clock) throws IOException {
        this.repository = Objects.requireNonNull(repository); this.clock = Objects.requireNonNull(clock); state = repository.load();
    }
    public synchronized BallotState state() { return state; }
    public synchronized void verifyUnchanged() throws IOException { repository.verifyUnchanged(); }
    public synchronized Ballot open(BallotDefinition definition, UUID operator) throws IOException {
        repository.verifyUnchanged();
        Objects.requireNonNull(operator); Ballot old = state.ballots().get(definition.id());
        if (old != null) {
            if (old.status() == BallotStatus.OPEN && old.definition().equals(definition) && old.openedBy().equals(operator)) return old;
            throw new IllegalStateException("Ballot ID already used");
        }
        if (state.ballots().size() >= 256 || state.ballots().values().stream().anyMatch(b -> b.status() == BallotStatus.OPEN)) throw new IllegalStateException("Ballot already open or history full");
        return save(new Ballot(definition, operator, now(), BallotStatus.OPEN, Map.of(), null, "", ""));
    }
    public synchronized Ballot cast(UUID id, UUID actor, String optionId) throws IOException {
        repository.verifyUnchanged();
        Ballot b = get(id); if (b.status() != BallotStatus.OPEN) throw new IllegalStateException("Ballot is not open");
        String elector = b.definition().electorate().entrySet().stream().filter(e -> e.getValue().actor().equals(actor)).map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalArgumentException("Actor is not an elector"));
        if (b.definition().options().stream().noneMatch(o -> o.id().equals(optionId))) throw new IllegalArgumentException("Unknown option");
        String old = b.votes().get(elector); if (old != null) { if (old.equals(optionId)) return b; throw new IllegalStateException("Vote is final"); }
        Map<String,String> votes = new LinkedHashMap<>(b.votes()); votes.put(elector, optionId);
        return save(new Ballot(b.definition(), b.openedBy(), b.openedAt(), b.status(), votes, null, "", ""));
    }
    public synchronized Ballot close(UUID id, UUID operator) throws IOException {
        repository.verifyUnchanged();
        Objects.requireNonNull(operator); Ballot b = get(id); if (b.status() == BallotStatus.CLOSED) return b;
        if (b.status() != BallotStatus.OPEN) throw new IllegalStateException("Ballot is void");
        return save(new Ballot(b.definition(), b.openedBy(), b.openedAt(), BallotStatus.CLOSED, b.votes(), operator, now(), ""));
    }
    public synchronized Ballot voidBallot(UUID id, UUID operator, String reason) throws IOException {
        repository.verifyUnchanged();
        Objects.requireNonNull(operator); BallotValidation.text(reason, 512, true); Ballot b = get(id);
        if (b.status() == BallotStatus.VOID && b.reason().equals(reason)) return b;
        if (b.status() != BallotStatus.OPEN) throw new IllegalStateException("Ballot is terminal");
        return save(new Ballot(b.definition(), b.openedBy(), b.openedAt(), BallotStatus.VOID, b.votes(), operator, now(), reason));
    }
    public synchronized BallotView view(UUID id, UUID viewer) {
        Ballot b = get(id); BallotDefinition d = b.definition(); Map<String,String> labels = new LinkedHashMap<>();
        d.electorate().forEach((key, e) -> labels.put(key, e.label()));
        String own = d.electorate().entrySet().stream().filter(e -> e.getValue().actor().equals(viewer)).map(e -> b.votes().getOrDefault(e.getKey(), "")).findFirst().orElse("");
        Map<String,String> named = new LinkedHashMap<>(); Map<String,Integer> totals = new LinkedHashMap<>(); List<String> passed = new ArrayList<>();
        boolean visible = d.visibility() == BallotVisibility.PUBLIC || b.status() == BallotStatus.CLOSED;
        if (visible) {
            d.options().forEach(o -> totals.put(o.id(), 0)); b.votes().forEach((elector, option) -> totals.compute(option, (k, count) -> count + 1));
            if (d.visibility() == BallotVisibility.PUBLIC) named.putAll(b.votes());
            if (b.status() == BallotStatus.CLOSED) d.options().stream().filter(o -> o.canPass() && totals.get(o.id()) >= d.threshold()).forEach(o -> passed.add(o.id()));
        }
        return new BallotView(d.id(), d.purpose(), d.title(), d.options(), labels, d.threshold(), d.visibility(), b.status(), own, named, totals, passed, b.reason());
    }
    private Ballot get(UUID id) { Ballot b = state.ballots().get(id); if (b == null) throw new IllegalArgumentException("Unknown ballot"); return b; }
    private String now() { return clock.instant().toString(); }
    private Ballot save(Ballot b) throws IOException { Map<UUID,Ballot> all = new LinkedHashMap<>(state.ballots()); all.put(b.definition().id(), b); BallotState next = new BallotState(Math.addExact(state.revision(), 1), all); repository.save(next); state = next; return b; }
}
