package io.github.chronicle.lastcouncil.complications;

import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.file.Path;

/** Injectable observed-byte storage. Implementations must fail rather than overwrite changed data. */
public interface ComplicationStorage {
    byte[] load() throws IOException;
    void save(byte[] bytes) throws IOException;
    default void verifyUnchanged() throws IOException {}

    static ComplicationStorage file(Path path) {
        AtomicDataFile file = new AtomicDataFile(path, 4 * 1024 * 1024);
        return new ComplicationStorage() {
            public void verifyUnchanged() throws IOException { file.verifyUnchanged(); }
            public byte[] load() throws IOException { return file.load(); }
            public void save(byte[] bytes) throws IOException { file.save(bytes); }
        };
    }
}
