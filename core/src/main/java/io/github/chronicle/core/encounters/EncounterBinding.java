package io.github.chronicle.core.encounters;

import io.github.chronicle.core.authoring.AuthoringSite;
import java.util.Objects;

/** Map-owned logical target reference. Coordinate geometry remains owned by AuthoringSite. */
public record EncounterBinding(String groupId, EncounterTarget target) {
    public enum TargetKind { POINT, REGION }
    public EncounterBinding {
        groupId = EncounterIds.registry(groupId, "encounter group ID");
        Objects.requireNonNull(target, "encounter target is required");
    }
    public EncounterBinding(String groupId, TargetKind targetKind, String siteId, long siteRevision) {
        this(groupId, new EncounterTarget(EncounterTarget.Kind.valueOf(targetKind.name()), siteId, siteRevision));
    }
    public TargetKind targetKind() { return TargetKind.valueOf(target.kind().name()); }
    public String siteId() { return target.siteId(); }
    public long siteRevision() { return target.siteRevision(); }
}
