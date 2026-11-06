package io.github.chronicle.lastcouncil.office;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Domain state only; callers enforce permissions and editing and ballot gates. */
public final class CouncilOfficeService {
    private final CouncilOfficeRepository repository;
    private CouncilOffice state;

    public CouncilOfficeService(CouncilOfficeRepository repository) throws IOException {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.state = Objects.requireNonNull(repository.load(), "office");
    }
    public synchronized CouncilOffice snapshot() { return state; }
    public synchronized void verifyUnchanged() throws IOException { repository.verifyUnchanged(); }
    public synchronized CouncilOffice appoint(UUID target, Set<UUID> houseOwners) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(houseOwners, "houseOwners");
        if (houseOwners.contains(target)) throw new IllegalArgumentException("Chancellor cannot own a House");
        if (target.equals(state.chancellor())) { repository.verifyUnchanged(); return state; }
        return commit(target);
    }
    public synchronized CouncilOffice clear() throws IOException {
        if (state.chancellor() == null) { repository.verifyUnchanged(); return state; }
        return commit(null);
    }
    private CouncilOffice commit(UUID target) throws IOException {
        if (state.revision() == Long.MAX_VALUE) throw new IllegalStateException("office revision exhausted");
        CouncilOffice next = new CouncilOffice(state.revision() + 1, target);
        repository.save(next);
        state = next;
        return next;
    }
}
