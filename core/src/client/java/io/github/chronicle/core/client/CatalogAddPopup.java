package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.CoreMenuModel.Picker;
import io.github.chronicle.core.menu.CoreMenuModel.View;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Bounded native picker used by editors to deliberately add one catalog entry.
 * The owner owns every domain mutation and may populate pages locally or from the server.
 */
public final class CatalogAddPopup extends Screen {
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private static final int VISIBLE_ROWS = 6;

    public interface Owner {
        void requestPage(CatalogAddPopup popup, String query, int page);
        /** Empty means the selected entry and quantity may be added. */
        String validate(Entry entry, String quantity);
        void add(Entry entry, String quantity);
        void cancel();
        default void serverUpdated(CatalogAddPopup popup, View view) { }
        default void tick(CatalogAddPopup popup) { }
        default void drawIcon(DrawContext context, Entry entry, int x, int y) { if(entry.kind().equals("BOOK"))context.drawItem(new net.minecraft.item.ItemStack(net.minecraft.item.Items.WRITTEN_BOOK),x,y); }
    }

    private final Screen parentScreen;
    private final boolean showQuantity;
    private final Owner owner;
    private Picker picker = new Picker("", "", 0, 1, List.of());
    private String query = "";
    private String quantity = "1";
    private String problem = "";
    private String currentValidation = "";
    private String selectedId = "";
    private int rowOffset;
    private boolean returnToEnd;
    private boolean busy;
    private boolean stale;
    private boolean initialRequest = true;
    private TextFieldWidget searchField;
    private TextFieldWidget quantityField;
    private ButtonWidget addButton;

    public CatalogAddPopup(Screen parentScreen, Text title, boolean showQuantity, Owner owner) {
        super(title);
        this.parentScreen = parentScreen;
        this.showQuantity = showQuantity;
        this.owner = owner;
    }

    public Screen parentScreen() { return parentScreen; }

    public void replacePage(Picker replacement) {
        if (stale) return;
        picker = replacement;
        query = replacement.query();
        rowOffset = returnToEnd ? Math.max(0, replacement.entries().size() - visibleRows()) : 0;
        returnToEnd = false;
        if (replacement.entries().stream().noneMatch(entry -> entry.id().equals(selectedId))) selectedId = "";
        busy = false;
        clearAndInit();
    }

    public void setBusy(boolean value) {
        if (stale) return;
        busy = value;
        clearAndInit();
    }

    public void markStale() {
        stale = true;
        busy = false;
        clearAndInit();
    }

    public void acceptServerView(View view) { owner.serverUpdated(this, view); }

    private int visibleRows() { return Math.clamp((height - 176) / 20, 1, VISIBLE_ROWS); }
    private int panelHeight() { return 150 + visibleRows() * 20; }

    @Override protected void init() {
        clearChildren();
        int w = Math.min(440, Math.max(240, width - 24));
        int x = (width - w) / 2;
        int y = Math.max(8, (height - panelHeight()) / 2);
        int rows = Math.min(visibleRows(), picker.entries().size());
        rowOffset = Math.clamp(rowOffset, 0, Math.max(0, picker.entries().size() - rows));

        searchField = field(x, y + 28, w - 58, "Search", query, value -> query = value);
        button("Find", x + w - 54, y + 28, 54, this::find, "Search this catalog", usable());
        for (int visible = 0; visible < rows; visible++) {
            Entry entry = picker.entries().get(rowOffset + visible);
            boolean selected = entry.id().equals(selectedId);
            int gutter = entry.kind().equals("ITEM") || entry.kind().equals("BOOK") ? 20 : 0;
            button((selected ? "> " : "") + entry.label(), x + gutter, y + 51 + visible * 20, w - gutter,
                    () -> { selectedId = entry.id(); problem = ""; clearAndInit(); }, entry.description(), usable());
        }
        int controlsY = y + 53 + visibleRows() * 20;
        if (picker.entries().size() > rows || picker.pages() > 1) {
            button("↑", x + w - 38, controlsY, 18, this::earlier, "Earlier entries", usable() && (rowOffset > 0 || picker.page() > 0));
            button("↓", x + w - 18, controlsY, 18, this::later, "Later entries", usable() && (rowOffset + rows < picker.entries().size() || picker.page() + 1 < picker.pages()));
        }
        int actionY = controlsY + 25;
        if (showQuantity) quantityField = field(x, actionY, 70, "Quantity", quantity, value -> quantity = value);
        Entry selected = selectedEntry();
        currentValidation = selected == null ? "" : owner.validate(selected, quantity);
        addButton = button("Add", x + (showQuantity ? 74 : 0), actionY, w - (showQuantity ? 74 : 0), this::addSelected,
                "Add the selected entry", usable() && selected != null && currentValidation.isBlank());
        button(stale ? "Close" : "Cancel", x, actionY + 22, w, this::cancel, stale ? "Close this stale picker" : "Return without adding anything", true);
        if (initialRequest) {
            initialRequest = false;
            requestPage(0);
        }
    }

    private boolean usable() { return !busy && !stale; }
    private TextFieldWidget field(int x, int y, int w, String hint, String value, java.util.function.Consumer<String> changed) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(hint)));
        field.setMaxLength(showQuantity && "Quantity".equals(hint) ? 9 : 80);
        field.setText(value);
        field.setChangedListener(changed);
        field.active = usable();
        return field;
    }
    private void earlier() {
        if (!usable()) return;
        if (rowOffset > 0) { rowOffset--; clearAndInit(); }
        else if (picker.page() > 0) { returnToEnd = true; requestPage(picker.page() - 1); }
    }
    private void later() {
        if (!usable()) return;
        if (rowOffset + visibleRows() < picker.entries().size()) { rowOffset++; clearAndInit(); }
        else if (picker.page() + 1 < picker.pages()) { returnToEnd = false; requestPage(picker.page() + 1); }
    }
    private void find() { returnToEnd = false; requestPage(0); }
    private void requestPage(int page) {
        if (!usable()) return;
        selectedId = "";
        problem = "";
        busy = true;
        query = query.trim();
        owner.requestPage(this, query, page);
        if (busy) clearAndInit();
    }
    private void addSelected() {
        if (!usable()) return;
        Entry entry = selectedEntry();
        if (entry == null) { problem = "Choose an entry from this page first."; clearAndInit(); return; }
        String validation = owner.validate(entry, quantity);
        if (validation != null && !validation.isBlank()) { problem = validation; clearAndInit(); return; }
        client.setScreen(parentScreen);
        owner.add(entry, quantity);
    }
    private Entry selectedEntry() { return picker.entries().stream().filter(value -> value.id().equals(selectedId)).findFirst().orElse(null); }
    private void cancel() {
        client.setScreen(parentScreen);
        owner.cancel();
    }
    private ButtonWidget button(String label, int x, int y, int w, Runnable action, String tooltip, boolean enabled) {
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, Math.max(1, w), 18).build());
        button.active = enabled;
        button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(tooltip)));
        return button;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(440, Math.max(240, width - 24));
        int x = (width - w) / 2;
        int y = Math.max(8, (height - panelHeight()) / 2);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, panelHeight());
        context.drawTextWithShadow(textRenderer, title, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Search, select one entry, then add it."), x, y + 14, 0xFFA0A0A0);
        int rows = Math.min(visibleRows(), picker.entries().size());
        for (int visible = 0; visible < rows; visible++) owner.drawIcon(context, picker.entries().get(rowOffset + visible), x, y + 52 + visible * 20);
        String message = stale ? "Chronicle changed. Close this picker and reopen the editor." : busy ? "Loading entries…" : !problem.isBlank() ? problem : currentValidation;
        if (picker.entries().isEmpty() && message.isBlank()) message = "No matching entries.";
        if (!message.isBlank()) context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(message, w)), x, y + 128 + visibleRows() * 20, stale ? 0xFFFFCC66 : 0xFFFF9966);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void close() { cancel(); }
    @Override public void tick() {
        owner.tick(this);
        Entry selected = selectedEntry();
        currentValidation = selected == null ? "" : owner.validate(selected, quantity);
        if (addButton != null) addButton.active = usable() && selected != null && currentValidation.isBlank();
    }
    @Override public boolean shouldPause() { return false; }
}
