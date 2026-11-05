package io.github.chronicle.lastcouncil.house;

import java.io.IOException;

public interface HouseRosterRepository {
    HouseRoster load() throws IOException;
    void save(HouseRoster roster) throws IOException;
}
