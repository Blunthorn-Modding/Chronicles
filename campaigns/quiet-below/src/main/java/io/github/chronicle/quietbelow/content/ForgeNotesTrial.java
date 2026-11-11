package io.github.chronicle.quietbelow.content;

import com.google.gson.JsonParser;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.ChestTarget;
import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChestAuthoring;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.TypedChestLoot;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.nio.file.Files;
import java.util.List;
import static net.minecraft.server.command.CommandManager.literal;

/** Finite native loot/book probe, absent unless explicitly enabled on an owned offline fixture. */
public final class ForgeNotesTrial {
    private static final BlockPos CHEST = new BlockPos(0, 64, 0);
    private static final Actor ACTOR = new Actor("ForgeNotesTrial", null);
    private ForgeNotesTrial() { }
    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_FORGE_NOTES_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("quiet_below_notes_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb : List.of("prepare", "collect", "zero", "empty", "custom", "restart"))
                root.then(literal(verb).executes(context -> run(context.getSource(), verb)));
            dispatcher.register(root);
        });
    }
    private static int run(ServerCommandSource source, String verb) {
        try {
            var server = source.getServer(); var player = source.getPlayerOrThrow();
            var root = server.getRunDirectory().toRealPath();
            require(server.isDedicated() && !server.isOnlineMode() && server.getServerPort() == 25575
                    && "127.0.0.1".equals(server.getServerIp()) && source.getWorld().getRegistryKey() == World.OVERWORLD
                    && root.getParent().getFileName().toString().equals("test-runs")
                    && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                    && Files.isRegularFile(root.resolve(".forge-notes-fixture")), "Not the owned notes fixture");
            require(source.getWorld().getChunkManager().getWorldChunk(0, 0) != null, "Fixture chunk is not player loaded");
            switch (verb) {
                case "prepare" -> {
                    require(TypedChestLoot.types(server).get(ForgeRecipeDefaults.NOTES_TYPE).equals(ForgeRecipeDefaults.notesType()), "Registered default changed");
                    source.getWorld().setBlockState(CHEST, Blocks.CHEST.getDefaultState(), 3);
                    ChestAuthoring.service(server).putTypedBinding(ForgeRecipeDefaults.NOTES_TYPE,
                            new ChestTarget("notes", 1, "minecraft:overworld", 0, 64, 0, "minecraft:chest"), false);
                    configure(source, 100, false);
                    ChronicleRuntime.service(server).start(ACTOR, null);
                }
                case "collect" -> {
                    var books = books(source, false);
                    for (int i = 0; i < books.size(); i++) player.getInventory().setStack(i, books.get(i).copy());
                    player.currentScreenHandler.sendContentUpdates();
                }
                case "zero" -> {
                    ChronicleRuntime.service(server).end(ACTOR);
                    configure(source, 0, false);
                    ChronicleRuntime.service(server).start(ACTOR, null);
                }
                case "empty" -> {
                    require(inventory(source).isEmpty(), "Zero chance did not replace contents with empty loot");
                    ChronicleRuntime.service(server).end(ACTOR);
                    configure(source, 100, true);
                    ChronicleRuntime.service(server).start(ACTOR, null);
                }
                case "custom" -> {
                    books(source, true);
                    ChronicleRuntime.service(server).end(ACTOR);
                    require(server.saveAll(true, true, true), "Save not confirmed");
                }
                case "restart" -> {
                    books(source, true);
                    for (int i = 0; i < ForgeRecipeDefaults.HINTS.size(); i++) {
                        var hint = ForgeRecipeDefaults.HINTS.get(i);
                        var old = player.getInventory().getStack(i).get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                        require(old != null && old.title().raw().equals(hint.title())
                                && old.getPages(false).stream().map(Text::getString).toList().equals(hint.pages()), "Existing book changed on restart: " + hint.title());
                    }
                    var entry = TypedChestLoot.types(server).get(ForgeRecipeDefaults.NOTES_TYPE).entries().getFirst();
                    require(entry.chancePercent() == 100 && entry.componentsJson().contains("Organizer notes"), "Configured notes were overwritten");
                }
                default -> throw new IllegalArgumentException("Unknown notes probe");
            }
            source.sendFeedback(() -> Text.literal("PASS Notes " + verb), false); return 1;
        } catch (Exception error) {
            source.sendError(Text.literal("FAIL Notes " + verb + ": " + error.getMessage())); return 0;
        }
    }
    private static void configure(ServerCommandSource source, double chance, boolean custom) throws Exception {
        require(ChronicleRuntime.service(source.getServer()).state().phase() != SessionPhase.ACTIVE, "Expected inactive authoring");
        AuthoringRuntime.get(source.getServer()).enable(source.getPlayerOrThrow().getUuid());
        var old = TypedChestLoot.types(source.getServer()).get(ForgeRecipeDefaults.NOTES_TYPE);
        var entries = new java.util.ArrayList<LootEntry>();
        for (var hint : ForgeRecipeDefaults.HINTS) {
            var components = JsonParser.parseString(ForgeRecipeDefaults.bookComponents(hint)).getAsJsonObject();
            if (custom && hint == ForgeRecipeDefaults.HINTS.getFirst())
                components.getAsJsonObject("minecraft:written_book_content").addProperty("title", "Organizer notes");
            entries.add(new LootEntry("minecraft:written_book", chance, 1, 1, components.toString()));
        }
        TypedChestLoot.saveType(source, new LootType(old.id(), old.label(), old.description(), 0, 0, entries), old);
        AuthoringRuntime.get(source.getServer()).disable(source.getPlayerOrThrow().getUuid());
    }
    private static Inventory inventory(ServerCommandSource source) {
        require(source.getWorld().getBlockEntity(CHEST) instanceof Inventory, "Missing notes chest");
        return (Inventory) source.getWorld().getBlockEntity(CHEST);
    }
    private static List<ItemStack> books(ServerCommandSource source, boolean custom) {
        Inventory inventory = inventory(source); var found = new java.util.HashMap<String, ItemStack>();
        for (int slot = 0; slot < inventory.size(); slot++) if (!inventory.getStack(slot).isEmpty()) {
            ItemStack stack = inventory.getStack(slot);
            var book = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
            require(stack.isOf(Items.WRITTEN_BOOK) && stack.getCount() == 1 && book != null, "Not a single written book");
            require(found.put(book.title().raw(), stack) == null, "Duplicate journal");
        }
        require(found.size() == ForgeRecipeDefaults.HINTS.size(), "Expected all five distinct journals");
        var ordered = new java.util.ArrayList<ItemStack>();
        for (var hint : ForgeRecipeDefaults.HINTS) {
            String title = custom && hint == ForgeRecipeDefaults.HINTS.getFirst() ? "Organizer notes" : hint.title();
            ItemStack stack = found.get(title);
            require(stack != null && stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT).getPages(false).stream()
                    .map(Text::getString).toList().equals(hint.pages()), "Missing or altered pages: " + title);
            ordered.add(stack);
        }
        return List.copyOf(ordered);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
