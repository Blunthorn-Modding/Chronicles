package io.github.chronicle.lastcouncil.content;

import static org.junit.jupiter.api.Assertions.*;
import io.github.chronicle.lastcouncil.house.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HouseCastExtensionTest {
    private static final UUID A = HouseCastTest.A, B = HouseCastTest.B, C = HouseCastTest.C;
    private static HouseRoster oldRoster() {
        return new HouseRoster(7, Map.of(A, new HouseAssignment(A, "c:a", true, false),
                B, new HouseAssignment(B, "c:b", true, false)));
    }
    private static HouseRoster nextRoster() {
        var assignments = new HashMap<>(oldRoster().assignments());
        assignments.put(C, new HouseAssignment(C, "c:c", true, false));
        return new HouseRoster(8, assignments);
    }
    private static CouncilContent changedContent(int chance) {
        var base = HouseCastTest.content(chance);
        return new CouncilContent(base.houses(),
                List.of(new CouncilContent.SecretTemplate("new", 1, "${house.title} owns ${fact.capacity}",
                        Map.of("fact.capacity", "seven"), base.houses().keySet())),
                List.of(new CouncilContent.GoalTemplate("newgoal", 1, "Give ${fact.capacity} to ${other.title}", base.houses().keySet())),
                1, 1, chance);
    }
    @Test void preservesCommittedPapersAndUsesActualOldSecrets() {
        var old = HouseCastGenerator.generate(HouseCastTest.content(100), oldRoster(), 44);
        var extended = HouseCastGenerator.append(changedContent(100), oldRoster(), old, nextRoster(), C, 23);
        assertEquals(old.castId(), extended.castId());
        assertEquals(old.seed(), extended.seed());
        assertEquals(8, extended.rosterRevision());
        assertSame(old.papers().get(A), extended.papers().get(A));
        assertSame(old.papers().get(B), extended.papers().get(B));
        var added = extended.papers().get(C);
        assertEquals(List.of("c owns seven"), added.secrets());
        assertTrue(Set.of("Give seven to a", "Give seven to b").contains(added.goals().getFirst()));
        assertEquals(Set.of(new HouseCast.KnownSecret("c:a", old.papers().get(A).secrets().getFirst()),
                new HouseCast.KnownSecret("c:b", old.papers().get(B).secrets().getFirst())), Set.copyOf(added.knownSecrets()));
        assertEquals(extended, HouseCastGenerator.append(changedContent(100), oldRoster(), old, nextRoster(), C, 23));
        assertTrue(HouseCastGenerator.append(changedContent(0), oldRoster(), old, nextRoster(), C, 23).papers().get(C).knownSecrets().isEmpty());
    }
    @Test void rejectsInvalidExtensionsAndMismatchedCommittedCast() {
        var old = HouseCastGenerator.generate(HouseCastTest.content(0), oldRoster(), 44);
        assertThrows(IllegalArgumentException.class, () -> HouseCastGenerator.append(changedContent(0), oldRoster(), old, nextRoster(), A, 1));
        assertThrows(IllegalArgumentException.class, () -> HouseCastGenerator.append(changedContent(0), oldRoster(), old, new HouseRoster(9, nextRoster().assignments()), C, 1));
        var changed = new HashMap<>(nextRoster().assignments());
        changed.put(A, new HouseAssignment(A, "c:a", false, false));
        assertThrows(IllegalArgumentException.class, () -> HouseCastGenerator.append(changedContent(0), oldRoster(), old, new HouseRoster(8, changed), C, 1));
        assertThrows(IllegalArgumentException.class, () -> new HouseRoster(8, Map.of(A, oldRoster().assignments().get(A), C, new HouseAssignment(C, "c:a", true, false))));
        var mismatched = new HouseCast(old.castId(), 6, old.seed(), old.papers());
        assertThrows(IllegalArgumentException.class, () -> HouseCastGenerator.append(changedContent(0), oldRoster(), mismatched, nextRoster(), C, 1));
        var wrongHouses = new HouseRoster(7, Map.of(A, new HouseAssignment(A, "c:b", true, false), B, new HouseAssignment(B, "c:a", true, false)));
        assertThrows(IllegalArgumentException.class, () -> HouseCastGenerator.append(changedContent(0), wrongHouses, old, nextRoster(), C, 1));
    }
}
