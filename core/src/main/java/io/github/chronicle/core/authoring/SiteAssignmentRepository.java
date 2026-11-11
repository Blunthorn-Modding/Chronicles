package io.github.chronicle.core.authoring;

import java.io.IOException;

public interface SiteAssignmentRepository {
    SiteAssignments load() throws IOException;
    void save(SiteAssignments assignments) throws IOException;
}
