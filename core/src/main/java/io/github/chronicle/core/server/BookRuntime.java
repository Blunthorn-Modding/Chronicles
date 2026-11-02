package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.books.BookDefinition;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned book definitions. Delivery/discovery state belongs to the shared chest journal. */
public final class BookRuntime {
    private static final Map<MinecraftServer, State> WORLDS = new IdentityHashMap<>();
    private static final String HOLD = "chronicle_core:books";
    private BookRuntime() { }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { load(server); }
            catch (IOException | RuntimeException failure) {
                ChronicleCore.LOGGER.error("Book catalog unavailable; existing config retained", failure);
                RuntimeHolds.hold(server, HOLD, "Book configuration unavailable; correct "+ScenarioAuthoringProfiles.contentFile("books.json")+" and reload Books & Lore.");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }
    private static void load(MinecraftServer server) throws IOException {
        var config = new BookConfig(ScenarioAuthoringProfiles.contentFile("books.json"));
        var books = ScenarioAuthoringProfiles.active().isPresent()?config.loadExisting():config.loadOrCreate(ChronicleCore.books().books());
        if(WORLDS.containsKey(server)){
            for(var type:TypedChestLoot.types(server).values())for(var ref:type.books())if(!books.containsKey(ref.bookId()))throw new IllegalArgumentException("Book still referenced by loot table: "+ref.bookId());
            for(var binding:ChestAuthoring.service(server).state().bindings().values())if(binding.directBook()!=null&&!books.containsKey(binding.directBook().bookId()))throw new IllegalArgumentException("Book still referenced by bound chest: "+binding.directBook().bookId());
        }
        for(var request:ChestAuthoring.service(server).state().bookDelivery().keepRequests().values())
            if(request.status()!=io.github.chronicle.core.books.BookKeepRequest.Status.CANCELLED&&request.status()!=io.github.chronicle.core.books.BookKeepRequest.Status.COMPLETED_FOUND&&!books.containsKey(request.bookId()))throw new IllegalArgumentException("Book has an unfinished keep request: "+request.bookId());
        WORLDS.put(server, new State(config, books));
        RuntimeHolds.release(server, HOLD);
    }
    public static Map<String, BookDefinition> books(MinecraftServer server) { return state(server).books; }
    public static void reload(ServerCommandSource source) throws IOException { requireEditor(source); load(source.getServer()); }
    public static BookDefinition create(ServerCommandSource source, BookDefinition draft) throws IOException {
        requireEditor(source);
        if (draft.rule() != BookDefinition.Rule.OFF) throw new IllegalArgumentException("A new book starts with distribution off");
        var book = new BookDefinition(BookDefinition.GENERATED_ID_ROOT + UUID.randomUUID(), draft.title(), draft.author(), draft.description(), draft.pages(), draft.rule());
        var state = state(source.getServer());
        state.books = state.config.replace(book, null);
        return book;
    }
    public static void save(ServerCommandSource source, BookDefinition book, BookDefinition expected) throws IOException {
        requireEditor(source);
        var state = state(source.getServer());
        if (expected == null || !expected.id().equals(book.id()) || !expected.equals(state.books.get(book.id())))
            throw new IllegalStateException("This book changed elsewhere. Review the refreshed version before saving.");
        state.books = state.config.replace(book, expected);
    }
    private static State state(MinecraftServer server) {
        var result = WORLDS.get(server);
        if (result == null) throw new IllegalStateException("Book definitions unavailable; correct books.json and reload Books & Lore.");
        return result;
    }
    private static void requireEditor(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2) || source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer()) || !AuthoringRuntime.editable(source.getServer()))
            throw new IllegalStateException("Book edits require operator permission and non-active server editing mode.");
    }
    private static final class State {
        final BookConfig config;
        Map<String, BookDefinition> books;
        State(BookConfig config, Map<String, BookDefinition> books) { this.config = config; this.books = books; }
    }
}
