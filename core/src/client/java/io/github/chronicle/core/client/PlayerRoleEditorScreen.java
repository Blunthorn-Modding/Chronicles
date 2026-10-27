package io.github.chronicle.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.roles.PlayerRole;
import io.github.chronicle.core.roles.StartingItem;
import io.github.chronicle.core.spawn.SpawnBinding;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Local whole-role editor. The server remains responsible for IDs, item registry validation,
 * point revisions, capacity and the actual configuration save.
 */
final class PlayerRoleEditorScreen extends Screen {
    enum Mode { DETAILS, STARTING_ITEMS, LORE, STARTING_POINT, RESPAWN_POINT }

    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private final Screen returnScreen;
    private final boolean creating;
    private final Consumer<PlayerRole> saved;
    private final List<Entry> points;
    private final List<Entry> books;
    private final PlayerRole base;
    private final long openingToken;
    private Mode mode;
    private String nameDraft, descriptionDraft, capacityDraft;
    private boolean selectable;
    /** Keep the original pin until the author deliberately chooses another Point or Default. */
    private SpawnBinding starting, respawn;
    private final List<String> items = new ArrayList<>();
    private final List<String> selectedBooks = new ArrayList<>();
    private TextFieldWidget name, description, capacity, itemSearch, itemCount;
    private String itemQuery = "", selectedItemId = "", itemCountDraft = "1";
    private int itemOffset, catalogOffset, pointOffset, bookOffset;
    private boolean addingItem;
    private final Map<Integer, String> quantityDrafts = new HashMap<>();
    private String problem = "";
    private boolean stale;

    PlayerRoleEditorScreen(Screen returnScreen, PlayerRole role, List<Entry> points, List<Entry> books, long openingToken,
                           boolean creating, Consumer<PlayerRole> saved) {
        super(Text.literal("Player Role"));
        this.returnScreen = returnScreen;
        this.base = role;
        this.points = List.copyOf(points);
        this.books = List.copyOf(books);
        this.openingToken = openingToken;
        this.creating = creating;
        this.saved = saved;
        nameDraft = role.name(); descriptionDraft = role.description(); capacityDraft = Integer.toString(role.capacity());
        selectable = role.selectable();
        starting = role.starting();
        respawn = role.respawn();
        role.items().forEach(value -> items.add(value.stackJson()));
        selectedBooks.addAll(role.books());
        mode = Mode.DETAILS;
    }

    @Override protected void init() {
        retainInputs(); clearChildren(); drawProblem = "";
        name = description = capacity = itemSearch = itemCount = null;
        int w = Math.min(440, Math.max(240, width - 24)); int x = (width - w) / 2; int y = Math.max(18, (height - 226) / 2);
        switch (mode) {
            case DETAILS -> details(x, y, w);
            case STARTING_ITEMS -> items(x, y, w);
            case LORE -> lore(x, y, w);
            case STARTING_POINT -> points(x, y, w, true);
            case RESPAWN_POINT -> points(x, y, w, false);
        }
    }

    private void details(int x, int y, int w) {
        name = input(x, y + 27, w, "Role name", nameDraft, value -> nameDraft = value);
        description = input(x, y + 61, w, "Public description", descriptionDraft, value -> descriptionDraft = value);
        capacity = input(x, y + 95, 84, "Player limit", capacityDraft, value -> capacityDraft = value);
        button((selectable ? "● " : "○ ") + "Players may choose this role", x + 90, y + 95, w - 90,
                () -> { selectable = !selectable; clearAndInit(); }, "GM-only roles remain hidden from player self-choice");
        button("Starting: " + pointLabel(starting), x, y + 129, w / 2 - 2, () -> { mode = Mode.STARTING_POINT; clearAndInit(); }, "Choose the first-entry destination, or vanilla default");
        button("Respawn: " + pointLabel(respawn), x + w / 2 + 2, y + 129, w / 2 - 2, () -> { mode = Mode.RESPAWN_POINT; clearAndInit(); }, "Choose the after-death destination, or vanilla default");
        button("Starting gear… (" + items.size() + ")", x, y + 153, w / 2 - 2, () -> { mode = Mode.STARTING_ITEMS; clearAndInit(); }, "Edit one starting stack at a time");
        button("Private books… (" + selectedBooks.size() + ")", x + w / 2 + 2, y + 153, w / 2 - 2, () -> { mode = Mode.LORE; clearAndInit(); }, "Choose named Books & Lore records");
        button("Cancel", x, y + 185, 90, this::returnToMenu, "Discard this local role draft");
        button(creating ? "Create role" : "Save role", x + 96, y + 185, w - 96, this::save, "Save the complete role definition", !stale);
    }

    private void items(int x, int y, int w) {
        if (addingItem) { itemCatalog(x, y, w); return; }
        int shown = Math.min(5, items.size());
        itemOffset = Math.clamp(itemOffset, 0, Math.max(0, items.size() - shown));
        for (int index = 0; index < shown; index++) {
            int row = itemOffset + index, rowY = y + 30 + index * 24;
            String stack = items.get(row);
            button(stackLabel(stack), x + 20, rowY, w - 170, () -> { }, stack);
            input(x + w - 146, rowY, 40, "Quantity", quantityDrafts.getOrDefault(row, stackCount(stack)),
                    value -> quantityDrafts.put(row, value));
            button("Set", x + w - 102, rowY, 42, () -> setQuantity(row), "Update this stack's quantity");
            button("Remove", x + w - 56, rowY, 56, () -> {
                items.remove(row);
                Map<Integer, String> remaining = new HashMap<>(quantityDrafts); quantityDrafts.clear();
                remaining.forEach((key, value) -> { if (key != row) quantityDrafts.put(key > row ? key - 1 : key, value); });
                problem = ""; clearAndInit();
            }, "Remove this starting supply from the draft");
        }
        if (items.isEmpty()) drawProblem = "No starting supplies assigned.";
        if (items.size() > shown) {
            button("<", x, y + 154, 45, () -> { itemOffset = Math.max(0, itemOffset - 5); clearAndInit(); }, "Earlier supplies", itemOffset > 0);
            button(">", x + 49, y + 154, 45, () -> { itemOffset = Math.min(items.size() - shown, itemOffset + 5); clearAndInit(); }, "Later supplies", itemOffset + shown < items.size());
        }
        button("Add item...", x + w - 120, y + 154, 120, () -> {
            addingItem = true; selectedItemId = ""; itemCountDraft = "1"; itemQuery = ""; itemSearch = itemCount = null; catalogOffset = 0; problem = ""; clearAndInit();
        }, "Browse available items");
        button("Back", x, y + 181, w, () -> { mode = Mode.DETAILS; problem = ""; clearAndInit(); }, "Keep this local supplies draft and return to role details");
    }

    private void itemCatalog(int x, int y, int w) {
        itemSearch = input(x, y + 27, w - 58, "Find item", itemQuery, value -> itemQuery = value);
        button("Find", x + w - 54, y + 27, 54, () -> { catalogOffset = 0; problem = ""; clearAndInit(); }, "Filter available items");
        List<Identifier> matches = matchingItems();
        int shown = Math.min(4, matches.size());
        catalogOffset = Math.clamp(catalogOffset, 0, Math.max(0, matches.size() - shown));
        for (int index = 0; index < shown; index++) {
            Identifier candidate = matches.get(catalogOffset + index); String id = candidate.toString();
            boolean assigned = plainItemAssigned(id);
            button((id.equals(selectedItemId) ? "> " : "") + itemLabel(candidate) + (assigned ? " (assigned)" : ""),
                    x + 20, y + 50 + index * 20, w - 20,
                    () -> { selectedItemId = id; problem = ""; clearAndInit(); },
                    assigned ? "Already assigned; edit its quantity in the supplies list" : id, !assigned);
        }
        if (matches.isEmpty()) drawProblem = "No matching registered item.";
        if (matches.size() > shown) {
            button("<", x, y + 132, 45, () -> { catalogOffset = Math.max(0, catalogOffset - 4); clearAndInit(); }, "Earlier available items", catalogOffset > 0);
            button(">", x + 49, y + 132, 45, () -> { catalogOffset = Math.min(matches.size() - shown, catalogOffset + 4); clearAndInit(); }, "Later available items", catalogOffset + shown < matches.size());
        }
        itemCount = input(x + 100, y + 132, 44, "Quantity", itemCountDraft, value -> itemCountDraft = value);
        button("Add item", x + 148, y + 132, w - 148, this::addItem, "Add the selected item and quantity");
        button("Cancel", x, y + 181, w, () -> { addingItem = false; problem = ""; clearAndInit(); }, "Return without adding an item; keep the role draft");
    }

    private JsonObject stackObject(String stack) { return JsonParser.parseString(stack).getAsJsonObject(); }
    private String stackCount(String stack) {
        try { JsonObject object = stackObject(stack); return object.has("count") ? object.get("count").getAsString() : "1"; }
        catch (RuntimeException exception) { return "?"; }
    }
    private Identifier stackId(String stack) {
        try { return Identifier.of(stackObject(stack).get("id").getAsString()); }
        catch (RuntimeException exception) { return null; }
    }
    private String stackLabel(String stack) {
        Identifier id = stackId(stack);
        if (id == null || !Registries.ITEM.containsId(id)) return "Unavailable item";
        String name = Registries.ITEM.get(id).getName().getString();
        try { if (stackObject(stack).has("components") && !stackObject(stack).getAsJsonObject("components").isEmpty()) name += " (custom)"; }
        catch (RuntimeException ignored) { }
        return name;
    }
    private boolean plainItemAssigned(String id) {
        for (String stack : items) {
            try {
                JsonObject object = stackObject(stack);
                if (object.has("id") && id.equals(object.get("id").getAsString())
                        && object.keySet().stream().allMatch(key -> key.equals("id") || key.equals("count")
                                || (key.equals("components") && object.get(key).isJsonObject() && object.getAsJsonObject(key).isEmpty()))) return true;
            } catch (RuntimeException ignored) { }
        }
        return false;
    }
    private void applyQuantity(int row) {
        JsonObject object = stackObject(items.get(row));
        Identifier id = stackId(items.get(row));
        if (id == null || !Registries.ITEM.containsId(id)) throw new IllegalArgumentException("This item is unavailable.");
        int count;
        try { count = Integer.parseInt(quantityDrafts.getOrDefault(row, stackCount(items.get(row)))); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Enter a whole number for the quantity."); }
        int max = Registries.ITEM.get(id).getMaxCount();
        if (count < 1 || count > max) throw new IllegalArgumentException("Count must be between 1 and " + max + " for this item.");
        object.addProperty("count", count);
        items.set(row, object.toString()); quantityDrafts.remove(row);
    }
    private void setQuantity(int row) {
        try { applyQuantity(row); problem = ""; clearAndInit(); }
        catch (RuntimeException exception) { problem = exception.getMessage(); }
    }

    private String drawProblem = "";
    private void points(int x, int y, int w, boolean starting) {
        SpawnBinding current = starting ? this.starting : respawn;
        button((current == null ? "> " : "") + "Vanilla default", x, y + 27, w,
                () -> { if (starting) this.starting = null; else this.respawn = null; mode = Mode.DETAILS; clearAndInit(); },
                "Do not use a role-specific destination");
        if (current != null && availablePoint(current) == null) {
            button("Pinned point unavailable: " + compact(current.locationId()) + " (rev " + current.locationRevision() + ")", x, y + 47, w,
                    () -> { }, "This saved pin is retained. Choose Default or a current Point to replace it.", false);
        }
        int shown = Math.min(current != null && availablePoint(current) == null ? 5 : 6, points.size());
        pointOffset = Math.clamp(pointOffset, 0, Math.max(0, points.size() - shown));
        for (int index = 0; index < shown; index++) {
            Entry entry = points.get(pointOffset + index);
            int rowY = y + 47 + (current != null && availablePoint(current) == null ? 19 : 0) + index * 19;
            button((matches(entry, current) ? "> " : "") + entry.label(), x, rowY, w,
                    () -> { if (starting) this.starting = pin(entry, "chronicle_core:role_start"); else this.respawn = pin(entry, "chronicle_core:role_respawn"); mode = Mode.DETAILS; clearAndInit(); }, entry.description());
        }
        if (points.size() > shown) {
            button("< points", x, y + 162, w / 2 - 2, () -> { pointOffset = Math.max(0, pointOffset - shown); clearAndInit(); }, "Earlier saved points", pointOffset > 0);
            button("> points", x + w / 2 + 2, y + 162, w / 2 - 2, () -> { pointOffset = Math.min(points.size() - shown, pointOffset + shown); clearAndInit(); }, "Later saved points", pointOffset + shown < points.size());
        }
        button("Back", x, y + 181, w, () -> { mode = Mode.DETAILS; clearAndInit(); }, "Keep the current point selection and return to role details");
    }
    private void lore(int x, int y, int w) {
        int shown = Math.min(6, books.size());
        bookOffset = Math.clamp(bookOffset, 0, Math.max(0, books.size() - shown));
        for (int index = 0; index < shown; index++) {
            Entry entry = books.get(bookOffset + index); boolean selected = selectedBooks.contains(entry.id());
            button((selected ? "● " : "○ ") + entry.label(), x, y + 30 + index * 20, w, () -> toggleBook(entry.id()), entry.description());
        }
        if (books.isEmpty()) drawProblem = "No named Books & Lore records are available.";
        if (books.size() > shown) {
            button("< books", x, y + 151, w / 2 - 2, () -> { bookOffset = Math.max(0, bookOffset - shown); clearAndInit(); }, "Earlier named books", bookOffset > 0);
            button("> books", x + w / 2 + 2, y + 151, w / 2 - 2, () -> { bookOffset = Math.min(books.size() - shown, bookOffset + shown); clearAndInit(); }, "Later named books", bookOffset + shown < books.size());
        }
        button("Back", x, y + 181, w, () -> { mode = Mode.DETAILS; clearAndInit(); }, "Keep this local book selection and return to role details");
    }

    private TextFieldWidget input(int x, int y, int w, String hint, String value, Consumer<String> changed) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(hint)));
        field.setMaxLength(1_024); field.setText(value); field.setChangedListener(changed); return field;
    }
    private Entry availablePoint(SpawnBinding binding) { return binding == null ? null : points.stream().filter(point -> matches(point, binding)).findFirst().orElse(null); }
    private boolean matches(Entry point, SpawnBinding binding) { return binding != null && point.id().equals(binding.locationId()) && point.revision() == binding.locationRevision(); }
    private SpawnBinding pin(Entry point, String purpose) { return new SpawnBinding(purpose, point.id(), point.revision()); }
    private String pointLabel(SpawnBinding binding) { return binding == null ? "Vanilla default" : java.util.Optional.ofNullable(availablePoint(binding)).map(Entry::label).orElse("Unavailable: " + binding.locationId()); }
    private void addItem() {
        if (selectedItemId.isBlank()) { problem = "Choose an item from the registry first."; return; }
        if (plainItemAssigned(selectedItemId)) { problem = "Already assigned; edit its quantity in the supplies list."; return; }
        if (items.size() >= PlayerRole.MAX_ITEMS) { problem = "A role may contain at most " + PlayerRole.MAX_ITEMS + " item stacks."; return; }
        try {
            Identifier id = Identifier.of(selectedItemId); Item selected = Registries.ITEM.get(id);
            int count = Integer.parseInt(itemCountDraft);
            if (count < 1 || count > selected.getMaxCount()) throw new IllegalArgumentException("Count must be between 1 and " + selected.getMaxCount() + " for this item.");
            // This compact stack form is decoded and registry-checked again by the server.
            items.add("{\"id\":\"" + id + "\",\"count\":" + count + "}");
            problem = ""; addingItem = false; selectedItemId = ""; itemCountDraft = "1"; itemCount = null; clearAndInit();
        } catch (IllegalArgumentException exception) { problem = exception.getMessage(); }
    }
    private void toggleBook(String id) { if (selectedBooks.contains(id)) selectedBooks.remove(id); else if (selectedBooks.size() < PlayerRole.MAX_BOOKS) selectedBooks.add(id); else { problem = "A role may reference at most " + PlayerRole.MAX_BOOKS + " books."; return; } clearAndInit(); }
    private void save() {
        retainInputs();
        try {
            for (int row : List.copyOf(quantityDrafts.keySet())) applyQuantity(row);
            int cap = Integer.parseInt(capacityDraft);
            PlayerRole value = new PlayerRole(base.id(), nameDraft, descriptionDraft, selectable, cap, starting, respawn,
                    items.stream().map(StartingItem::new).toList(), selectedBooks);
            saved.accept(value);
        } catch (IllegalArgumentException exception) { problem = exception.getMessage(); clearAndInit(); }
    }
    private List<Identifier> matchingItems() {
        String needle = itemQuery.trim().toLowerCase(java.util.Locale.ROOT);
        return Registries.ITEM.getIds().stream().filter(id -> needle.isBlank() || id.toString().contains(needle)
                        || Registries.ITEM.get(id).getName().getString().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Identifier::toString)).toList();
    }
    private String itemLabel(Identifier id) {
        String name = Registries.ITEM.get(id).getName().getString();
        return name.equals(id.toString()) ? id.toString() : name + " - " + id;
    }
    private void retainInputs() {
        if (name != null) nameDraft = name.getText(); if (description != null) descriptionDraft = description.getText(); if (capacity != null) capacityDraft = capacity.getText();
        if (itemSearch != null) itemQuery = itemSearch.getText(); if (itemCount != null) itemCountDraft = itemCount.getText();
    }
    private void returnToMenu() { client.setScreen(returnScreen); }
    void serverRejected(String value) { problem = value; clearAndInit(); }
    void serverUpdated(io.github.chronicle.core.menu.CoreMenuModel.View updated) { if (updated.token() != openingToken) { stale = true; clearAndInit(); } }
    CoreMenuScreen parentMenu() { return returnScreen instanceof CoreMenuScreen menu ? menu : null; }
    private void button(String label, int x, int y, int w, Runnable action, String hint) { button(label, x, y, w, action, hint, true); }
    private void button(String label, int x, int y, int w, Runnable action, String hint, boolean enabled) { ButtonWidget widget = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, Math.max(1, w), 18).build()); widget.active = enabled; widget.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(hint))); }
    private static String compact(String value) { return value.length() <= 45 ? value : value.substring(0, 42) + "..."; }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(440, Math.max(240, width - 24)); int x = (width - w) / 2; int y = Math.max(18, (height - 226) / 2);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, 226);
        String title = switch (mode) { case DETAILS -> creating ? "New player role" : "Edit player role"; case STARTING_ITEMS -> addingItem ? "Available items" : "Assigned starting supplies"; case LORE -> "Private lore books"; case STARTING_POINT -> "Starting position"; case RESPAWN_POINT -> "Respawn position"; };
        context.drawTextWithShadow(textRenderer, Text.literal(title), x, y, 0xFFFFFFFF);
        if (mode == Mode.DETAILS) { context.drawTextWithShadow(textRenderer, Text.literal("Role name"), x, y + 14, 0xFFCCCCCC); context.drawTextWithShadow(textRenderer, Text.literal("Public description"), x, y + 48, 0xFFCCCCCC); context.drawTextWithShadow(textRenderer, Text.literal("Player limit"), x, y + 82, 0xFFCCCCCC); }
        if (mode == Mode.STARTING_ITEMS) {
            context.drawTextWithShadow(textRenderer, Text.literal(addingItem ? "Search, select an item, and enter a quantity." : "Edit quantities or remove supplies from this role."), x, y + 14, 0xFFA0A0A0);
            if (addingItem) {
                List<Identifier> matches = matchingItems();
                for (int i = 0; i < Math.min(4, matches.size() - catalogOffset); i++)
                    context.drawItem(new ItemStack(Registries.ITEM.get(matches.get(catalogOffset + i))), x, y + 51 + i * 20);
            } else {
                for (int i = 0; i < Math.min(5, items.size() - itemOffset); i++) {
                    Identifier id = stackId(items.get(itemOffset + i));
                    if (id != null && Registries.ITEM.containsId(id)) context.drawItem(new ItemStack(Registries.ITEM.get(id)), x, y + 31 + i * 24);
                }
            }
        }
        if (mode == Mode.LORE) context.drawTextWithShadow(textRenderer, Text.literal("These named books are personal first-entry entitlements."), x, y + 14, 0xFFA0A0A0);
        if (mode == Mode.STARTING_POINT || mode == Mode.RESPAWN_POINT) context.drawTextWithShadow(textRenderer, Text.literal("Choose a saved Point, or leave this role on vanilla rules."), x, y + 14, 0xFFA0A0A0);
        String message = stale ? "Chronicle changed. This draft is preserved, but reopen the role before saving." : !problem.isBlank() ? problem : drawProblem;
        if (!message.isBlank()) context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(message, w)), x, y + 207, stale ? 0xFFFFCC66 : 0xFFFF9966);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { returnToMenu(); }
}
