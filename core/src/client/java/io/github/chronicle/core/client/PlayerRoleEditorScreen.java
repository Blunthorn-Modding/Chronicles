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
    private TextFieldWidget name, description, capacity;
    private int itemOffset, pointOffset, bookOffset;
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
        name = description = capacity = null;
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
        button("Add item...", x + w - 120, y + 154, 120, this::openItemPicker, "Browse available items");
        button("Back", x, y + 181, w, () -> { mode = Mode.DETAILS; problem = ""; clearAndInit(); }, "Keep this local supplies draft and return to role details");
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
        int shown = Math.min(6, selectedBooks.size());
        bookOffset = Math.clamp(bookOffset, 0, Math.max(0, selectedBooks.size() - shown));
        for (int index = 0; index < shown; index++) {
            int row = bookOffset + index;
            String id = selectedBooks.get(row);
            Entry entry = books.stream().filter(book -> book.id().equals(id)).findFirst().orElse(null);
            button(entry == null ? "Unavailable: " + id : entry.label(), x, y + 30 + index * 20, w - 62, () -> { },
                    entry == null ? "This pinned book is retained until removed." : entry.description(), false);
            button("Remove", x + w - 58, y + 30 + index * 20, 58, () -> { selectedBooks.remove(row); problem = ""; clearAndInit(); }, "Remove this book from the role draft");
        }
        if (selectedBooks.isEmpty()) drawProblem = "No private lore books assigned.";
        boolean paged = selectedBooks.size() > shown;
        if (paged) {
            button("<", x, y + 154, 36, () -> { bookOffset = Math.max(0, bookOffset - shown); clearAndInit(); }, "Earlier assigned books", bookOffset > 0);
            button(">", x + w - 36, y + 154, 36, () -> { bookOffset = Math.min(selectedBooks.size() - shown, bookOffset + shown); clearAndInit(); }, "Later assigned books", bookOffset + shown < selectedBooks.size());
        }
        button("Add lore book...", x + (paged ? 40 : 0), y + 154, w - (paged ? 80 : 0), this::openBookPicker, "Browse named Books & Lore records", selectedBooks.size() < PlayerRole.MAX_BOOKS && !stale);
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
    private void openItemPicker() { client.setScreen(new CatalogAddPopup(this, Text.literal("Add starting item"), true, new CatalogAddPopup.Owner() {
        @Override public void requestPage(CatalogAddPopup popup, String query, int page) { popup.replacePage(itemPicker(query, page)); }
        @Override public String validate(Entry entry, String quantity) {
            if (plainItemAssigned(entry.id())) return "Already assigned; edit its quantity in the supplies list.";
            if (items.size() >= PlayerRole.MAX_ITEMS) return "A role may contain at most " + PlayerRole.MAX_ITEMS + " item stacks.";
            try {
                Identifier id = Identifier.of(entry.id()); Item item = Registries.ITEM.get(id); int count = Integer.parseInt(quantity);
                if (!Registries.ITEM.containsId(id) || count < 1 || count > item.getMaxCount()) return "Count must be between 1 and " + item.getMaxCount() + " for this item.";
            } catch (RuntimeException exception) { return "Enter a whole number for the quantity."; }
            return "";
        }
        @Override public void add(Entry entry, String quantity) { items.add("{\"id\":\"" + entry.id() + "\",\"count\":" + Integer.parseInt(quantity) + "}"); problem = ""; clearAndInit(); }
        @Override public void cancel() { }
        @Override public void serverUpdated(CatalogAddPopup popup, io.github.chronicle.core.menu.CoreMenuModel.View updated) { if (updated.token() != openingToken) popup.markStale(); }
        @Override public void drawIcon(net.minecraft.client.gui.DrawContext context, Entry entry, int x, int y) {
            Identifier id = Identifier.of(entry.id()); if (Registries.ITEM.containsId(id)) context.drawItem(new ItemStack(Registries.ITEM.get(id)), x, y);
        }
    })); }
    private void openBookPicker() { client.setScreen(new CatalogAddPopup(this, Text.literal("Add lore book"), false, new CatalogAddPopup.Owner() {
        @Override public void requestPage(CatalogAddPopup popup, String query, int page) { popup.replacePage(bookPicker(query, page)); }
        @Override public String validate(Entry entry, String quantity) {
            if (selectedBooks.contains(entry.id())) return "This book is already assigned to the role.";
            return selectedBooks.size() >= PlayerRole.MAX_BOOKS ? "A role may reference at most " + PlayerRole.MAX_BOOKS + " books." : "";
        }
        @Override public void add(Entry entry, String quantity) { selectedBooks.add(entry.id()); problem = ""; clearAndInit(); }
        @Override public void cancel() { }
        @Override public void serverUpdated(CatalogAddPopup popup, io.github.chronicle.core.menu.CoreMenuModel.View updated) { if (updated.token() != openingToken) popup.markStale(); }
    })); }
    private io.github.chronicle.core.menu.CoreMenuModel.Picker itemPicker(String query, int page) {
        List<Entry> entries = matchingItems(query).stream().map(id -> new Entry(id.toString(), itemLabel(id), id.toString(), "ITEM", "", 0)).toList();
        return picker("items", query, page, entries);
    }
    private io.github.chronicle.core.menu.CoreMenuModel.Picker bookPicker(String query, int page) {
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        List<Entry> entries = books.stream().filter(entry -> needle.isBlank() || entry.id().toLowerCase(java.util.Locale.ROOT).contains(needle) || entry.label().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
        return picker("books", query, page, entries);
    }
    private io.github.chronicle.core.menu.CoreMenuModel.Picker picker(String kind, String query, int page, List<Entry> entries) {
        int pages = Math.max(1, (entries.size() + 11) / 12); int selectedPage = Math.clamp(page, 0, pages - 1); int from = selectedPage * 12;
        return new io.github.chronicle.core.menu.CoreMenuModel.Picker(kind, query, selectedPage, pages, entries.subList(from, Math.min(entries.size(), from + 12)));
    }
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
    private List<Identifier> matchingItems(String query) {
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
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
    }
    private void returnToMenu() { client.setScreen(returnScreen); }
    void serverRejected(String value, boolean changed) { problem = value; stale |= changed; clearAndInit(); }
    void serverUpdated(io.github.chronicle.core.menu.CoreMenuModel.View updated) { if (updated.token() != openingToken) { stale = true; clearAndInit(); } }
    CoreMenuScreen parentMenu() { return returnScreen instanceof CoreMenuScreen menu ? menu : null; }
    private void button(String label, int x, int y, int w, Runnable action, String hint) { button(label, x, y, w, action, hint, true); }
    private void button(String label, int x, int y, int w, Runnable action, String hint, boolean enabled) { ButtonWidget widget = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, Math.max(1, w), 18).build()); widget.active = enabled; widget.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(hint))); }
    private static String compact(String value) { return value.length() <= 45 ? value : value.substring(0, 42) + "..."; }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(440, Math.max(240, width - 24)); int x = (width - w) / 2; int y = Math.max(18, (height - 226) / 2);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, 226);
        String title = switch (mode) { case DETAILS -> creating ? "New player role" : "Edit player role"; case STARTING_ITEMS -> "Assigned starting supplies"; case LORE -> "Private lore books"; case STARTING_POINT -> "Starting position"; case RESPAWN_POINT -> "Respawn position"; };
        context.drawTextWithShadow(textRenderer, Text.literal(title), x, y, 0xFFFFFFFF);
        if (mode == Mode.DETAILS) { context.drawTextWithShadow(textRenderer, Text.literal("Role name"), x, y + 14, 0xFFCCCCCC); context.drawTextWithShadow(textRenderer, Text.literal("Public description"), x, y + 48, 0xFFCCCCCC); context.drawTextWithShadow(textRenderer, Text.literal("Player limit"), x, y + 82, 0xFFCCCCCC); }
        if (mode == Mode.STARTING_ITEMS) {
            context.drawTextWithShadow(textRenderer, Text.literal("Edit quantities or remove supplies from this role."), x, y + 14, 0xFFA0A0A0);
            for (int i = 0; i < Math.min(5, items.size() - itemOffset); i++) {
                Identifier id = stackId(items.get(itemOffset + i));
                if (id != null && Registries.ITEM.containsId(id)) context.drawItem(new ItemStack(Registries.ITEM.get(id)), x, y + 31 + i * 24);
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
