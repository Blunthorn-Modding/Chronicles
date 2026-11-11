package io.github.chronicle.core.profile;

import java.io.IOException;
import java.util.UUID;

/** Reads and updates profiles using optimistic revision checks. */
public final class ProfileService {
    private final ProfileStore store;

    public ProfileService(ProfileStore store) {
        if (store == null) {
            throw new IllegalArgumentException("profile store is required");
        }
        this.store = store;
    }

    public CharacterProfile get(UUID owner, String fallbackName) throws IOException {
        return store.load(owner).orElseGet(() -> new CharacterProfile(owner, fallbackName, "", "", 0));
    }

    public CharacterProfile update(UUID owner, long expectedRevision, String name, String pronouns, String description) throws IOException {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expected revision must be non-negative");
        }
        CharacterProfile current = store.load(owner).orElse(null);
        long revision = current == null ? 0 : current.revision();
        if (expectedRevision != revision) {
            throw new IllegalStateException("profile revision is stale; expected " + expectedRevision + " but found " + revision);
        }
        if (revision == CharacterProfile.REVISION_LIMIT) {
            throw new IllegalStateException("profile revision cannot exceed " + CharacterProfile.REVISION_LIMIT);
        }
        CharacterProfile updated = new CharacterProfile(owner, name, pronouns, description, revision + 1);
        store.save(updated);
        return updated;
    }
}
