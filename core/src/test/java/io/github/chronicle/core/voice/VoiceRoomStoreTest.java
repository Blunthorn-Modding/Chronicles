package io.github.chronicle.core.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VoiceRoomStoreTest {
    @TempDir Path world;
    private static final VoiceRoomStore.Binding ROOM = new VoiceRoomStore.Binding("palace", 4);

    @Test void roundTripAndWitnessPreserveExactReferences() throws IOException {
        var store = new VoiceRoomStore(world);
        assertEquals(Map.of(), store.load());
        store.save(Map.of("palace", ROOM, "garden", new VoiceRoomStore.Binding("garden", 8)));
        assertEquals("chronicle-voice-rooms-v1\n", Files.readString(world.resolve("voice-rooms.initialized")));
        String serialized = Files.readString(world.resolve("voice-rooms.json"));
        assertTrue(serialized.indexOf("garden") < serialized.indexOf("palace"));
        assertEquals(Map.of("palace", ROOM, "garden", new VoiceRoomStore.Binding("garden", 8)), new VoiceRoomStore(world).load());
        Files.delete(world.resolve("voice-rooms.json"));
        assertThrows(IOException.class, () -> new VoiceRoomStore(world).load());
        assertThrows(IOException.class, () -> store.save(Map.of()));
        assertFalse(Files.exists(world.resolve("voice-rooms.json")));
    }

    @Test void refusesChangedMarkerAndInvalidMarker() throws IOException {
        var store = new VoiceRoomStore(world);
        store.load(); store.save(Map.of("palace", ROOM));
        Files.writeString(world.resolve("voice-rooms.initialized"), "invalid\n");
        assertThrows(IOException.class, store::verifyUnchanged);
        assertThrows(IOException.class, () -> store.save(Map.of()));
        assertThrows(IOException.class, () -> new VoiceRoomStore(world).load());
    }

    @Test void rejectsStrictSchemaAndPreservesCorruptFiles() throws IOException {
        Files.writeString(world.resolve("voice-rooms.initialized"), "chronicle-voice-rooms-v1\n");
        String[] invalid = {
                "{\"schemaVersion\":1,\"bindings\":[],\"bindings\":[]}",
                "{\"schemaVersion\":1,\"bindings\":[],\"other\":0}",
                "{\"schemaVersion\":1}",
                "{\"schemaVersion\":1,\"bindings\":[{\"regionId\":\"palace\"}]}",
                "{\"schemaVersion\":1,\"bindings\":[{\"regionId\":\"palace\",\"regionRevision\":1.0}]}",
                "{\"schemaVersion\":1,\"bindings\":[{\"regionId\":\"palace\",\"regionRevision\":1},{\"regionId\":\"palace\",\"regionRevision\":2}]}",
                "{\"schemaVersion\":1,\"bindings\":[]} true"
        };
        Path file = world.resolve("voice-rooms.json");
        for (String input : invalid) {
            Files.writeString(file, input);
            var store = new VoiceRoomStore(world);
            assertThrows(IOException.class, store::load, input);
            assertThrows(IOException.class, () -> store.save(Map.of()));
            assertEquals(input, Files.readString(file));
        }
        Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> new VoiceRoomStore(world).load());
    }

    @Test void missingWitnessCannotBeSilentlyRecreatedOnReload() throws IOException {
        var store = new VoiceRoomStore(world);
        store.load(); store.save(Map.of("palace", ROOM));
        byte[] retained = Files.readAllBytes(world.resolve("voice-rooms.json"));
        Files.delete(world.resolve("voice-rooms.initialized"));
        assertThrows(IOException.class, store::verifyUnchanged);
        assertThrows(IOException.class, () -> new VoiceRoomStore(world).load());
        assertFalse(Files.exists(world.resolve("voice-rooms.initialized")));
        assertArrayEquals(retained, Files.readAllBytes(world.resolve("voice-rooms.json")));
    }

    @Test void validatesBindingAndMapIdentity() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new VoiceRoomStore.Binding("Bad ID", 1));
        assertThrows(IllegalArgumentException.class, () -> new VoiceRoomStore.Binding("valid", 0));
        var store = new VoiceRoomStore(world);
        assertThrows(IOException.class, () -> store.save(Map.of()));
        store.load();
        assertThrows(IOException.class, () -> store.save(Map.of("other", ROOM)));
        assertFalse(Files.exists(world.resolve("voice-rooms.initialized")));
    }
}
