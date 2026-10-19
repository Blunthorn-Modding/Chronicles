package io.github.chronicle.core.profile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterProfileTest {
    private static final UUID OWNER = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void normalizesOptionalTextAndCountsUnicodeCodePoints() {
        CharacterProfile profile = new CharacterProfile(OWNER, "  Nova  ", null, "  Scout  ", 0);
        assertEquals("Nova", profile.name());
        assertEquals("", profile.pronouns());
        assertEquals("Scout", profile.description());

        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile(OWNER, "😀".repeat(CharacterProfile.NAME_LIMIT + 1), "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile(OWNER, "\u00A7cNova", "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile(OWNER, "Nova\n", "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile(OWNER, " ", "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile(null, "Nova", "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile(OWNER, "Nova", "", "", -1));
    }
}
