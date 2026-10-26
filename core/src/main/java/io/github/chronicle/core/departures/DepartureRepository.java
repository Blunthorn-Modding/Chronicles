package io.github.chronicle.core.departures;

import java.io.IOException;

public interface DepartureRepository {
    DepartureState load() throws IOException;
    void save(DepartureState replacement) throws IOException;
}
