package io.github.chronicle.lastcouncil.house;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HouseRosterTest {
    private static final UUID A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final HouseDefinition HOUSE = new HouseDefinition("council:vale", "Vale", "Wheat", "Lands", "Trade", true);
    private static HouseAssignment assignment(UUID owner) { return new HouseAssignment(owner, HOUSE.id(), true, true); }
    private static final class Memory implements HouseRosterRepository {
        HouseRoster state = HouseRoster.empty(); boolean fail;
        public HouseRoster load() { return state; }
        public void save(HouseRoster next) throws IOException { if (fail) throw new IOException("failure"); state = next; }
    }
    @Test void serviceRejectsStaleDuplicateUnknownAndPreservesFailedSave() throws Exception {
        Memory repo = new Memory();
        HouseRosterService service = new HouseRosterService(Map.of(HOUSE.id(), HOUSE), repo);
        service.assign(0, assignment(A));
        assertEquals(1, service.assign(1, assignment(A)).revision());
        assertThrows(IllegalStateException.class, () -> service.assign(0, assignment(A)));
        assertThrows(IllegalArgumentException.class, () -> service.assign(1, assignment(B)));
        assertThrows(IllegalArgumentException.class, () -> service.assign(1, new HouseAssignment(B, "council:missing", true, false)));
        repo.fail = true;
        assertThrows(IOException.class, () -> service.unassign(1, A));
        assertEquals(assignment(A), service.snapshot().assignments().get(A));
        assertEquals(1, service.snapshot().revision());
        assertThrows(IllegalArgumentException.class, () -> new HouseAssignment(A, HOUSE.id(), false, true));
    }
    @Test void restartAndConflict(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("roster.json");
        HouseRosterStore first = new HouseRosterStore(path);
        HouseRosterService service = new HouseRosterService(Map.of(HOUSE.id(), HOUSE), first);
        service.assign(0, assignment(A));
        HouseRosterStore second = new HouseRosterStore(path);
        assertEquals(service.snapshot(), second.load());
        Files.writeString(path, "external");
        assertThrows(IOException.class, () -> service.unassign(1, A));
        assertEquals(1, service.snapshot().revision());
        assertEquals("external", Files.readString(path));
    }
    @Test void sourceRefreshAllowsNewHousesWithoutRewritingMembership() throws Exception {
        Memory repo=new Memory();var service=new HouseRosterService(Map.of(HOUSE.id(),HOUSE),repo);
        service.assign(0,assignment(A));var before=service.snapshot();
        var added=new HouseDefinition("council:new","New","","","",false);
        assertThrows(IllegalStateException.class,()->service.replaceDefinitions(Map.of(added.id(),added)));
        assertEquals(before,service.snapshot());assertEquals(before,repo.state);
        service.replaceDefinitions(Map.of(HOUSE.id(),HOUSE,added.id(),added));
        assertEquals(before,service.snapshot());
        service.assign(before.revision(),new HouseAssignment(B,added.id(),true,false));
        assertEquals(added.id(),service.snapshot().assignments().get(B).houseId());
    }
    @Test void malformedFilesAreNeverOverwritten(@TempDir Path dir) throws Exception {
        String id = A.toString();
        String entry = "\"" + id + "\":{\"owner\":\"" + id + "\",\"houseId\":\"council:vale\",\"councilMember\":true,\"candidate\":false}";
        String[] bad = {
            "{\"schema\":1,\"schema\":1,\"revision\":0,\"assignments\":{}}",
            "{\"schema\":1,\"revision\":0,\"assignments\":{" + entry + "," + entry + "}}",
            "{\"schema\":1,\"revision\":0,\"assignments\":{" + entry.replace("true", "1") + "}}",
            "{\"schema\":1,\"revision\":0,\"assignments\":{" + entry.replace(id + "\",\"houseId", B + "\",\"houseId") + "}}",
            "{\"schema\":1,\"revision\":0.0,\"assignments\":{}}",
            "{\"schema\":1,\"revision\":0,\"assignments\":{},\"extra\":0}"
        };
        Path path = dir.resolve("roster.json");
        for (String text : bad) {
            Files.writeString(path, text);
            HouseRosterStore store = new HouseRosterStore(path);
            assertThrows(IOException.class, store::load, text);
            assertThrows(IOException.class, () -> store.save(HouseRoster.empty()));
            assertEquals(text, Files.readString(path));
        }
        byte[] invalid = {(byte)0xc3, (byte)0x28}; Files.write(path, invalid);
        HouseRosterStore store = new HouseRosterStore(path);
        assertThrows(IOException.class, store::load);
        assertArrayEquals(invalid, Files.readAllBytes(path));
    }
    @Test void validatesBoundsAndOwnerKeys() {
        assertThrows(IllegalArgumentException.class, () -> new HouseRoster(0, Map.of(B, assignment(A))));
        assertThrows(IllegalArgumentException.class, () -> new HouseRoster(0, Map.of(A, assignment(A), B, assignment(B))));
        assertThrows(IllegalArgumentException.class, () -> new HouseDefinition("invalid", "", "", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new HouseDefinition("a:b", "x".repeat(81), "", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new HouseDefinition("a:b", "  ", "", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new HouseDefinition("a:b", "Title", "bad\u00a7", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new HouseDefinition("a:b", "Title", "", "bad\u0000", "", false));
        var unicode = new HouseDefinition("a:b", " " + "😀".repeat(80) + " ", "", "line\nline", "", false);
        assertEquals("😀".repeat(80), unicode.title());
    }
}
