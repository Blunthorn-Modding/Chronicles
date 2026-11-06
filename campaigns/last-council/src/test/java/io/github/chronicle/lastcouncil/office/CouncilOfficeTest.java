package io.github.chronicle.lastcouncil.office;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CouncilOfficeTest {
    private static final UUID A = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID B = UUID.fromString("abcdefab-1234-1234-1234-123456789abc");
    @TempDir Path directory;

    @Test void roundTripAndVacancy() throws Exception {
        Path path = directory.resolve("office.json");
        CouncilOfficeService service = new CouncilOfficeService(new CouncilOfficeStore(path));
        assertEquals(CouncilOffice.empty(), service.snapshot());
        assertEquals(new CouncilOffice(1, A), service.appoint(A, Set.of()));
        assertEquals(new CouncilOffice(1, A), new CouncilOfficeStore(path).load());
        assertEquals(new CouncilOffice(2, null), service.clear());
        assertEquals(new CouncilOffice(2, null), new CouncilOfficeStore(path).load());
    }

    @Test void malformedFilesCannotBeOverwritten() throws Exception {
        String[] invalid = {
            "{}", "{\"schema\":2,\"revision\":0,\"chancellor\":null}",
            "{\"schema\":1,\"revision\":0,\"chancellor\":null,\"extra\":1}",
            "{\"schema\":1,\"revision\":-1,\"chancellor\":null}",
            "{\"schema\":1,\"revision\":0,\"chancellor\":\"12345678-1234-1234-1234-123456789ABC\"}",
            "{\"schema\":1,\"schema\":1,\"revision\":0,\"chancellor\":null}",
            "{\"schema\":1,\"revision\":0,\"chancellor\":false}"
        };
        Path path = directory.resolve("office.json");
        for (String input : invalid) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            Files.write(path, bytes);
            CouncilOfficeStore store = new CouncilOfficeStore(path);
            assertThrows(IOException.class, store::load, input);
            assertThrows(IOException.class, () -> store.save(CouncilOffice.empty()));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
        byte[] malformed = {(byte) 0xc3, (byte) 0x28};
        Files.write(path, malformed);
        CouncilOfficeStore store = new CouncilOfficeStore(path);
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(CouncilOffice.empty()));
        assertArrayEquals(malformed, Files.readAllBytes(path));
    }

    @Test void externalEditsBlockChangesAndIdempotentOperations() throws Exception {
        Path path = directory.resolve("office.json");
        CouncilOfficeService service = new CouncilOfficeService(new CouncilOfficeStore(path));
        service.appoint(A, Set.of());
        Files.writeString(path, "external", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> service.appoint(A, Set.of()));
        assertThrows(IOException.class, () -> service.appoint(B, Set.of()));
        assertThrows(IOException.class, service::clear);
        assertEquals(new CouncilOffice(1, A), service.snapshot());
        assertEquals("external", Files.readString(path));
    }

    @Test void serviceEnforcesHouseExclusionAndRetainsAuthorityOnFailure() throws Exception {
        class Repo implements CouncilOfficeRepository {
            CouncilOffice saved = CouncilOffice.empty(); int saves; int verifies; boolean fail;
            public CouncilOffice load() { return saved; }
            public void save(CouncilOffice next) throws IOException { if (fail) throw new IOException("failure"); saves++; saved = next; }
            public void verifyUnchanged() { verifies++; }
        }
        Repo repo = new Repo();
        CouncilOfficeService service = new CouncilOfficeService(repo);
        service.clear();
        assertEquals(1, repo.verifies);
        assertThrows(IllegalArgumentException.class, () -> service.appoint(A, Set.of(A)));
        assertEquals(CouncilOffice.empty(), service.snapshot());
        service.appoint(A, Set.of());
        service.appoint(A, Set.of());
        assertEquals(1, repo.saves);
        assertEquals(2, repo.verifies);
        repo.fail = true;
        assertThrows(IOException.class, () -> service.appoint(B, Set.of()));
        assertThrows(IOException.class, service::clear);
        assertEquals(new CouncilOffice(1, A), service.snapshot());
        assertEquals(service.snapshot(), repo.saved);
    }
}
