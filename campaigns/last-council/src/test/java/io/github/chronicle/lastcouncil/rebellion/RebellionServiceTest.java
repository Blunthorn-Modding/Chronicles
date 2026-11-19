package io.github.chronicle.lastcouncil.rebellion;

import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.chronicle.lastcouncil.rebellion.RebellionJournal.MAX_ENTRIES;
import static io.github.chronicle.lastcouncil.rebellion.RebellionService.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class RebellionServiceTest {
    private static final House ROWAN = new House("test:rowan", "House Rowan");
    private static final House MERE = new House("test:mere", "House Mere");
    private static final House HART = new House("test:hart", "House Hart");

    @Test void declarationAndJoiningKeepOneMembershipAndIndependentPublicRecords(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("rebellions.json");
        var registry = new RebellionService(path);
        UUID first = UUID.randomUUID();
        String statement = "We declare our cause.\n" + "🌾".repeat(MAX_STATEMENT - 22);
        assertEquals(MAX_STATEMENT, statement.codePointCount(0, statement.length()));
        assertThrows(IllegalArgumentException.class, () -> registry.declare(new UUID(0, 0), 0, ROWAN, statement));
        Faction declared = registry.declare(first, 0, ROWAN, statement);
        Snapshot prior = registry.snapshot();
        assertEquals(declared, registry.declare(first, 0, ROWAN, statement));
        assertThrows(IllegalArgumentException.class, () -> registry.declare(first, 0, ROWAN, "Changed"));
        assertThrows(IllegalArgumentException.class, () -> registry.join(first, 0, ROWAN, first));
        assertThrows(IllegalStateException.class, () -> registry.declare(UUID.randomUUID(), 0, MERE, "Stale"));
        assertThrows(IllegalArgumentException.class, () -> registry.declare(UUID.randomUUID(), 1, MERE, statement + "🌾"));
        assertThrows(IllegalArgumentException.class, () -> registry.join(UUID.randomUUID(), 1, MERE, UUID.randomUUID()));
        UUID second = UUID.randomUUID();
        registry.declare(second, 1, MERE, "A separate cause.");
        UUID join = UUID.randomUUID();
        registry.join(join, 2, HART, first);
        registry.join(join, 2, HART, first);
        assertThrows(IllegalStateException.class, () -> registry.join(UUID.randomUUID(), 3,
                new House(HART.houseId(), "New title"), second));
        assertThrows(IllegalStateException.class, () -> registry.declare(UUID.randomUUID(), 3, HART, "Third cause."));

        Snapshot current = registry.snapshot();
        assertEquals(3, current.revision());
        assertEquals(new Membership(first, 3, HART), registry.membership(HART.houseId()));
        assertEquals(List.of(ROWAN, HART), current.factions().get(first).members());
        assertEquals(List.of(MERE), current.factions().get(second).members());
        assertEquals(List.of(new PublicEvent(Action.DECLARED, ROWAN, 1), new PublicEvent(Action.JOINED, HART, 3)),
                current.factions().get(first).history());
        assertEquals(List.of(ROWAN), prior.factions().get(first).members());
        assertThrows(UnsupportedOperationException.class, () -> current.memberships().clear());
        assertThrows(UnsupportedOperationException.class, () -> declared.history().clear());
        assertEquals(current, new RebellionService(path).snapshot());
    }

    @Test void visitsAreHouseScopedAndCannotBeReusedAfterExit(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("rebellions.json");
        var registry = new RebellionService(path);
        UUID faction = UUID.randomUUID();
        registry.declare(faction, 0, ROWAN, "Our cause.");
        registry.join(UUID.randomUUID(), 1, MERE, faction);
        List<Target> originalTargets = registry.targets(List.of(ROWAN.houseId(), MERE.houseId()));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> registry.grantVisit(UUID.randomUUID(), 2, HART.houseId()));
        assertEquals(VisitStatus.WAITING, registry.grantVisit(first, 2, ROWAN.houseId()).status());
        assertThrows(IllegalStateException.class, () -> registry.grantVisit(UUID.randomUUID(), 3, ROWAN.houseId()));
        registry.grantVisit(second, 3, MERE.houseId());
        assertThrows(IllegalStateException.class, () -> registry.markExited(UUID.randomUUID(), 4, ROWAN.houseId(), first));
        assertThrows(IllegalStateException.class, () -> registry.markEntered(UUID.randomUUID(), 4, ROWAN.houseId(), second));
        UUID entered = UUID.randomUUID();
        assertEquals(VisitStatus.INSIDE, registry.markEntered(entered, 4, ROWAN.houseId(), first).status());
        assertThrows(IllegalStateException.class, () -> registry.markEntered(UUID.randomUUID(), 5, ROWAN.houseId(), first));

        var reopened = new RebellionService(path);
        assertEquals(VisitStatus.INSIDE, reopened.snapshot().visits().get(ROWAN.houseId()).status());
        assertEquals(VisitStatus.WAITING, reopened.snapshot().visits().get(MERE.houseId()).status());
        UUID exited = UUID.randomUUID();
        assertEquals(VisitStatus.ENDED, reopened.markExited(exited, 5, ROWAN.houseId(), first).status());
        reopened.markExited(exited, 5, ROWAN.houseId(), first);
        reopened.markEntered(entered, 4, ROWAN.houseId(), first);
        assertEquals(6, reopened.snapshot().revision());
        assertEquals(VisitStatus.ENDED, reopened.snapshot().visits().get(ROWAN.houseId()).status());
        assertThrows(IllegalStateException.class, () -> reopened.markEntered(UUID.randomUUID(), 6, ROWAN.houseId(), first));
        UUID replacement = UUID.randomUUID();
        reopened.grantVisit(replacement, 6, ROWAN.houseId());
        assertThrows(IllegalStateException.class, () -> reopened.markEntered(UUID.randomUUID(), 7, ROWAN.houseId(), first));
        reopened.validateTargets(originalTargets);
        assertEquals(2, reopened.snapshot().memberships().size());
        assertEquals(2, reopened.snapshot().factions().get(faction).history().size());
    }

    @Test void exactRestorationIsAtomicAcrossFactionsAndSafeToReplay(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("rebellions.json");
        var registry = new RebellionService(path);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.declare(first, 0, ROWAN, "Our cause.");
        registry.declare(second, 1, MERE, "Separate cause.");
        Effect stale = effect(registry, ROWAN, MERE);
        registry.join(UUID.randomUUID(), 2, HART, second);
        assertThrows(IllegalStateException.class, () -> registry.validateTargets(stale.targets()));
        assertThrows(IllegalStateException.class, () -> registry.apply(stale));
        assertEquals(3, registry.snapshot().memberships().size());
        assertEquals(0, registry.effects().size());
        assertThrows(IllegalArgumentException.class, () -> registry.targets(List.of(MERE.houseId(), MERE.houseId())));

        Effect exact = effect(registry, ROWAN, MERE, HART);
        UUID grant = UUID.randomUUID();
        registry.grantVisit(grant, 3, ROWAN.houseId());
        registry.markEntered(UUID.randomUUID(), 4, ROWAN.houseId(), grant);
        registry.grantVisit(UUID.randomUUID(), 5, MERE.houseId());
        registry.apply(exact);
        assertTrue(registry.snapshot().memberships().isEmpty());
        assertEquals(List.of(exact), registry.effects());
        assertEquals(VisitStatus.ENDED, registry.snapshot().visits().get(ROWAN.houseId()).status());
        assertEquals(VisitStatus.ENDED, registry.snapshot().visits().get(MERE.houseId()).status());
        assertEquals(ROWAN, registry.snapshot().factions().get(first).founder());
        assertEquals(List.of(new PublicEvent(Action.DECLARED, MERE, 2), new PublicEvent(Action.JOINED, HART, 3),
                        new PublicEvent(Action.REINSTATED, MERE, 7), new PublicEvent(Action.REINSTATED, HART, 7)),
                registry.snapshot().factions().get(second).history());
        assertTrue(registry.snapshot().factions().get(second).members().isEmpty());

        var reopened = new RebellionService(path);
        reopened.apply(exact);
        assertEquals(registry.snapshot(), reopened.snapshot());
        reopened.join(UUID.randomUUID(), 7, ROWAN, first);
        reopened.apply(exact);
        assertEquals(new Membership(first, 8, ROWAN), reopened.membership(ROWAN.houseId()));
        assertThrows(IllegalStateException.class, () -> reopened.apply(new Effect(UUID.randomUUID(), UUID.randomUUID(),
                List.of(exact.targets().getFirst()), exact.title())));
        assertThrows(IllegalArgumentException.class, () -> reopened.apply(new Effect(exact.ballot(), exact.motion(), exact.targets(), "Altered")));
        assertThrows(IllegalArgumentException.class, () -> reopened.apply(new Effect(UUID.randomUUID(), exact.motion(),
                reopened.targets(List.of(ROWAN.houseId())), exact.title())));
        assertEquals(List.of(exact), reopened.effects());
    }

    @Test void uncertainWriteRecoversOnEitherSideOfPublication() throws Exception {
        var storage = new MemoryStorage();
        var registry = new RebellionService(storage);
        UUID operation = UUID.randomUUID();
        storage.failBefore = true;
        assertThrows(IOException.class, () -> registry.declare(operation, 0, ROWAN, "Our cause."));
        assertTrue(registry.needsReload());
        assertEquals(0, registry.snapshot().revision());
        storage.failBefore = false;
        assertThrows(IOException.class, () -> registry.declare(operation, 0, ROWAN, "Our cause."));
        registry.reload();
        assertFalse(registry.needsReload());
        registry.declare(operation, 0, ROWAN, "Our cause.");

        Effect effect = effect(registry, ROWAN);
        storage.failAfter = true;
        assertThrows(IOException.class, () -> registry.apply(effect));
        assertTrue(registry.needsReload());
        assertNotNull(registry.membership(ROWAN.houseId()));
        assertTrue(registry.effects().isEmpty());
        storage.failAfter = false;
        assertThrows(IOException.class, () -> registry.apply(effect));
        registry.reload();
        assertNull(registry.membership(ROWAN.houseId()));
        byte[] saved = storage.bytes.clone();
        registry.apply(effect);
        assertArrayEquals(saved, storage.bytes);
        assertEquals(List.of(effect), registry.effects());
    }

    @Test void observedFileCasAlsoChecksIdempotentRetriesAndMissingJournal(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("rebellions.json");
        var first = new RebellionService(path);
        var second = new RebellionService(path);
        UUID operation = UUID.randomUUID();
        first.declare(operation, 0, ROWAN, "Our cause.");
        byte[] valid = Files.readAllBytes(path);
        assertThrows(IOException.class, () -> second.declare(UUID.randomUUID(), 0, MERE, "Stale writer."));
        assertTrue(second.needsReload());
        assertArrayEquals(valid, Files.readAllBytes(path));
        second.reload();
        second.declare(operation, 0, ROWAN, "Our cause.");
        Files.writeString(path, "external edit");
        assertThrows(IOException.class, () -> second.declare(operation, 0, ROWAN, "Our cause."));
        assertThrows(IOException.class, second::reload);
        assertEquals("external edit", Files.readString(path));
        Files.delete(path);
        assertThrows(IOException.class, second::reload);
        assertFalse(Files.exists(path));
        assertNotNull(second.membership(ROWAN.houseId()));
        assertTrue(second.needsReload());
        Files.write(path, valid);
        second.reload();
        assertEquals(first.snapshot(), second.snapshot());
        assertFalse(second.needsReload());
    }

    @Test void strictCodecPreservesMalformedBytesAndRejectsImpossibleTransitions() throws Exception {
        var storage = new MemoryStorage();
        var registry = new RebellionService(storage);
        UUID faction = UUID.randomUUID();
        registry.declare(faction, 0, ROWAN, "Our cause.");
        byte[] valid = storage.bytes.clone();
        List<byte[]> malformed = new ArrayList<>();
        malformed.add(new byte[]{(byte) 0xff});
        malformed.add(new byte[RebellionJournal.MAX_BYTES + 1]);
        malformed.add("{\"schema\":1,\"schema\":1,\"revision\":0,\"history\":[]}".getBytes(UTF_8));
        malformed.add("{\"schema\":1.0,\"revision\":0,\"history\":[]}".getBytes(UTF_8));
        malformed.add("{\"schema\":2,\"revision\":0,\"history\":[]}".getBytes(UTF_8));
        malformed.add("{\"schema\":1,\"revision\":0,\"history\":[],\"privateLore\":[]}".getBytes(UTF_8));
        malformed.add("{\"schema\":1,\"revision\":0,\"history\":[]} {}".getBytes(UTF_8));

        JsonObject json = BookConfig.strictObject(new String(valid, UTF_8));
        var history = json.getAsJsonArray("history");
        JsonObject original = history.get(0).getAsJsonObject();
        String unpaired = new String(valid, UTF_8).replace("Our cause.", "\\ud800");
        malformed.add(unpaired.getBytes(UTF_8));
        original.addProperty("expected", 1);
        malformed.add(json.toString().getBytes(UTF_8));
        original.addProperty("expected", 0);
        original.addProperty("id", "1-2-3-4-5");
        malformed.add(json.toString().getBytes(UTF_8));
        original.addProperty("id", faction.toString());
        original.getAsJsonObject("house").addProperty("privateLore", "Hidden");
        malformed.add(json.toString().getBytes(UTF_8));
        original.getAsJsonObject("house").remove("privateLore");
        history.add(original.deepCopy());
        malformed.add(json.toString().getBytes(UTF_8));
        json.addProperty("revision", 2);
        malformed.add(json.toString().getBytes(UTF_8));
        malformed.add(RebellionJournal.encode(List.of(new RebellionJournal.Enter(UUID.randomUUID(), 0, ROWAN.houseId(), faction))));
        malformed.add(RebellionJournal.encode(List.of(new RebellionJournal.Declare(faction, 0, ROWAN, "Our cause."),
                new RebellionJournal.Restore(new Effect(UUID.randomUUID(), UUID.randomUUID(),
                        List.of(new Target(ROWAN.houseId(), faction, 2, 2)), "Forged restored membership")))));
        for (byte[] bytes : malformed) {
            storage.bytes = bytes.clone();
            assertThrows(IOException.class, () -> new RebellionService(storage));
            assertArrayEquals(bytes, storage.bytes);
        }
        storage.bytes = valid;
        registry.reload();
        assertEquals(1, registry.snapshot().revision());
    }

    @Test void journalCapacityReservesVisitExitAndLoyaltyRestoration() throws Exception {
        var storage = new MemoryStorage();
        List<RebellionJournal.Entry> entries = new ArrayList<>();
        UUID faction = UUID.randomUUID();
        entries.add(new RebellionJournal.Declare(faction, 0, ROWAN, "Our cause."));
        entries.add(new RebellionJournal.Join(UUID.randomUUID(), 1, MERE, faction));
        while (entries.size() < MAX_ENTRIES - 5) {
            UUID grant = UUID.randomUUID();
            entries.add(new RebellionJournal.Grant(grant, entries.size(), ROWAN.houseId()));
            entries.add(new RebellionJournal.Enter(UUID.randomUUID(), entries.size(), ROWAN.houseId(), grant));
            entries.add(new RebellionJournal.Exit(UUID.randomUUID(), entries.size(), ROWAN.houseId(), grant));
        }
        UUID pending = UUID.randomUUID();
        entries.add(new RebellionJournal.Grant(pending, entries.size(), ROWAN.houseId()));
        storage.bytes = RebellionJournal.encode(entries);
        var registry = new RebellionService(storage);
        long revision = registry.snapshot().revision();
        assertEquals(MAX_ENTRIES - 4, revision);
        assertThrows(IllegalStateException.class, () -> registry.grantVisit(UUID.randomUUID(), revision, MERE.houseId()));
        assertThrows(IllegalStateException.class, () -> registry.join(UUID.randomUUID(), revision, HART, faction));
        registry.markEntered(UUID.randomUUID(), revision, ROWAN.houseId(), pending);
        registry.markExited(UUID.randomUUID(), revision + 1, ROWAN.houseId(), pending);
        Effect first = effect(registry, ROWAN);
        registry.apply(first);
        Effect second = effect(registry, MERE);
        registry.apply(second);
        registry.apply(second);
        assertEquals(MAX_ENTRIES, registry.snapshot().revision());
        assertTrue(registry.snapshot().memberships().isEmpty());
        assertEquals(List.of(first, second), new RebellionService(storage).effects());
        assertThrows(IllegalStateException.class, () -> registry.declare(UUID.randomUUID(), MAX_ENTRIES, HART, "Rebellion."));
    }

    @Test void concurrentMembershipsFitOneRosterAndCapacityReturnsOnReinstatement() throws Exception {
        var storage = new MemoryStorage();
        List<RebellionJournal.Entry> entries = new ArrayList<>();
        UUID faction = UUID.randomUUID();
        entries.add(new RebellionJournal.Declare(faction, 0, ROWAN, "Our cause."));
        while (entries.size() < MAX_TARGETS) {
            int number = entries.size();
            entries.add(new RebellionJournal.Join(UUID.randomUUID(), number, new House("test:house" + number, "House " + number), faction));
        }
        storage.bytes = RebellionJournal.encode(entries);
        var registry = new RebellionService(storage);
        assertEquals(MAX_TARGETS, registry.snapshot().memberships().size());
        assertThrows(IllegalStateException.class, () -> registry.join(UUID.randomUUID(), MAX_TARGETS, MERE, faction));
        registry.apply(effect(registry, ROWAN));
        registry.join(UUID.randomUUID(), MAX_TARGETS + 1, MERE, faction);
        assertEquals(MAX_TARGETS, new RebellionService(storage).snapshot().memberships().size());
    }

    private static Effect effect(RebellionService registry, House... houses) throws IOException {
        var targets = registry.targets(List.of(houses).stream().map(House::houseId).toList());
        return new Effect(UUID.randomUUID(), UUID.randomUUID(), targets, "Reinstate Houses");
    }

    private static final class MemoryStorage implements RebellionService.Storage {
        private byte[] bytes;
        private boolean failBefore;
        private boolean failAfter;

        @Override public byte[] load() { return bytes == null ? null : bytes.clone(); }

        @Override public void save(byte[] value) throws IOException {
            if (failBefore) throw new IOException("Failed before publishing");
            bytes = value.clone();
            if (failAfter) throw new IOException("Failed after publishing");
        }
    }
    @Test void politicalObserverIsPrewriteAndRestoreReportsEveryHouse(@TempDir Path directory) throws Exception {
        var service=new RebellionService(directory.resolve("rebellion.json")); UUID faction=UUID.randomUUID(),join=UUID.randomUUID();
        service.observer(events->{throw new IOException("refuse");});
        assertThrows(IOException.class,()->service.declare(faction,0,ROWAN,"Cause"));
        String declaration=service.politicalPlan(copy->copy.declare(faction,0,ROWAN,"Cause")).getFirst().authority();
        assertTrue(service.politicalEvidence(declaration).isEmpty());
        var seen=new ArrayList<io.github.chronicle.lastcouncil.progression.PoliticalOccurrence>(); service.observer(seen::addAll);
        service.declare(faction,0,ROWAN,"Cause"); service.join(join,1,MERE,faction);
        var targets=service.targets(List.of(ROWAN.houseId(),MERE.houseId())); UUID ballot=UUID.randomUUID();
        var effect=new Effect(ballot,UUID.randomUUID(),targets,"Reinstate");
        var planned=service.politicalPlan(copy->copy.apply(effect)); assertEquals(2,planned.size());
        String authority=planned.getFirst().authority();
        assertTrue(service.politicalEvidence(authority).isEmpty()); service.apply(effect);
        var facts=service.politicalEvidence(authority);
        assertEquals(2,facts.size()); assertEquals(java.util.Set.of(ROWAN.houseId(),MERE.houseId()),facts.stream().map(x->x.target()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(facts.stream().allMatch(x->x.activity().equals("reinstated")));
        int count=seen.size(); service.apply(effect); service.reload(); assertEquals(count,seen.size());
        assertEquals(facts,service.politicalEvidence(authority));
    }
    @Test void politicalEvidenceRecoversAfterUncertainPublication() throws Exception {
        var storage=new MemoryStorage(); var service=new RebellionService(storage); var id=UUID.randomUUID();
        var seen=new ArrayList<io.github.chronicle.lastcouncil.progression.PoliticalOccurrence>(); service.observer(seen::addAll);
        String authority=service.politicalPlan(copy->copy.declare(id,0,ROWAN,"Cause")).getFirst().authority();
        storage.failAfter=true; assertThrows(IOException.class,()->service.declare(id,0,ROWAN,"Cause"));
        assertThrows(IOException.class,()->service.politicalEvidence(authority));
        storage.failAfter=false; service.reload(); assertEquals(seen,service.politicalEvidence(authority));
    }
    @Test void politicalEvidenceRejectsDifferentValidJournalPrefix(@TempDir Path directory) throws Exception {
        var first=new RebellionService(directory.resolve("first.json")); var other=new RebellionService(directory.resolve("other.json"));
        first.declare(UUID.randomUUID(),0,ROWAN,"Cause"); other.declare(UUID.randomUUID(),0,MERE,"Cause");
        UUID operation=UUID.randomUUID(); String authority=first.politicalPlan(copy->copy.join(operation,1,HART,first.snapshot().factions().keySet().iterator().next())).getFirst().authority();
        assertThrows(IOException.class,()->other.politicalEvidence(authority));
        other.join(operation,1,HART,other.snapshot().factions().keySet().iterator().next());
        assertThrows(IOException.class,()->other.politicalEvidence(authority));
    }
}
