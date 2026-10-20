package io.github.chronicle.core.client;

import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.loot.LootTableDraftJson;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.Control;
import io.github.chronicle.core.menu.CoreMenuModel.Detail;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.CoreMenuModel.EventForm;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import io.github.chronicle.core.menu.CoreMenuModel.RegionForm;
import io.github.chronicle.core.menu.CoreMenuModel.View;
import io.github.chronicle.core.network.CoreMenuPayloads;
import io.github.chronicle.core.play.ObservationConfiguration;
import io.github.chronicle.core.play.PlayState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Native presentation for the bounded Core menu protocol. It stores only unfinished form drafts;
 * selection, permissions and all mutations remain server-authoritative.
 */
public final class CoreMenuScreen extends Screen {
    private static final int RESPONSE_TIMEOUT = 20 * 10;
    private static final int REFRESH_TICKS = 20 * 2;
    private static final Page[] NAVIGATION = {Page.SESSION, Page.PLAYERS, Page.EVENTS, Page.CHESTS,
            Page.LOOT_TABLES, Page.REGIONS, Page.LOCATIONS, Page.SETUP};
    private static final String[] NAVIGATION_LABELS = {"Session", "Players", "Events", "Chests",
            "Loot Tables", "Regions", "Locations", "Game setup"};

    private View state;
    private boolean pending;
    private boolean refreshPending;
    private Action requestedAction;
    private int pendingTicks;
    private int idleTicks;
    private int listOffset;
    private int actionOffset;
    private int factOffset;
    private boolean detailOnCompact;
    private String selected = "";
    private String locationFilter = "";
    private final Map<Page, String> pageSelections = new java.util.EnumMap<>(Page.class);
    private final Map<String, String> locationSelections = new java.util.HashMap<>();
    private String notice = "";
    private String eventPreview = "";
    private Confirmation confirmation;
    private PendingPicker pickerAction;
    private String pickerSearch = "";
    private TextFieldWidget textInput;
    private TextFieldWidget pickerSearchInput;
    private int detailScroll, detailContentHeight, detailX, detailY, detailWidth, detailHeight;
    private boolean layingOutDetail;
    private int clipTop, clipBottom;
    private TextFieldWidget eventSeconds, lootRefill;
    private TextFieldWidget lootChance, lootMin, lootMax;
    private String eventMode = "MANUAL", eventRegion = "", eventFlag = "", eventSecondsDraft = "0";
    private String regionFirst = "", regionSecond = "";
    private LootType lootDraft;
    private int lootEntry = -1;
    private int lootEntryOffset;
    private String refillMinutesDraft = "0", lootValidation = "";
    private final Map<Integer, String[]> lootInputDrafts = new java.util.HashMap<>();
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private final List<RenderLine> renderLines = new ArrayList<>();

    public CoreMenuScreen(View initial) {
        super(Text.literal("Chronicle"));
        state = initial;
        selected = initial.selected();
        notice = initial.message();
        loadForms(initial.detail());
    }

    public View state() { return state; }
    public boolean pending() { return pending; }
    public boolean triggerForTest(String label) {
        ButtonWidget button = buttons.get(label);
        if (button == null || !button.active || !button.visible) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
        return true;
    }
    public void selectForTest(String id) {
        var entries = state.picker() == null ? state.entries() : state.picker().entries();
        Entry entry = entries.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Entry is not on this page: " + id));
        chooseEntry(entry);
    }
    public void searchForTest(String value) { if (pickerSearchInput == null) throw new IllegalStateException("Open a picker first."); pickerSearchInput.setText(value); }
    public void scrollDetailForTest(int direction) { mouseScrolled(detailX + detailWidth / 2.0, detailY + detailHeight / 2.0, 0, direction < 0 ? -1 : 1); }
    public void textForTest(String value) { if (textInput == null) throw new IllegalStateException("No text prompt is open."); textInput.setText(value); }
    public void setEventSecondsForTest(String value) { if (eventSeconds == null) throw new IllegalStateException("Open an event form first."); eventSeconds.setText(value); }
    public void selectLootEntryForTest(int index) { if (lootDraft == null || index < 0 || index >= lootDraft.entries().size()) throw new IllegalArgumentException("Loot entry is unavailable: " + index); lootEntry = index; lootEntryOffset = Math.max(0, index - 2); clearAndInit(); }
    public void setLootChanceForTest(String value) { if (lootChance == null) throw new IllegalStateException("Select a loot entry first."); lootChance.setText(value); }
    public void setLootMinForTest(String value) { if (lootMin == null) throw new IllegalStateException("Select a loot entry first."); lootMin.setText(value); }
    public void setLootMaxForTest(String value) { if (lootMax == null) throw new IllegalStateException("Select a loot entry first."); lootMax.setText(value); }
    public void setLootRefillForTest(String value) { if (lootRefill == null) throw new IllegalStateException("Open a loot table first."); lootRefill.setText(value); }
    public ButtonProbe probeButtonForTest(String label) {
        ButtonWidget button = buttons.get(label);
        if (button == null) throw new IllegalArgumentException("Button not present: " + label);
        return new ButtonProbe(System.identityHashCode(button), button.active, button.isHovered(), button.isFocused());
    }

    void accept(View updated) {
        String priorDetail = detailId();
        long priorToken = state.token();
        Action receivedFor = requestedAction;
        boolean stableRefresh = refreshPending && updated.sameControls(state);
        boolean preserveDrafts = requestedAction == Action.OPTIONS || (requestedAction == Action.LOOT_SAVE && !updated.success());
        if (updated.detail() != null && updated.detail().id().equals(priorDetail)
                && (updated.detail().loot() != null || updated.detail().event() != null)
                && requestedAction != Action.OPEN && requestedAction != Action.LOOT_RELOAD
                && !(updated.success() && (requestedAction == Action.LOOT_SAVE || requestedAction == Action.EVENT_SAVE))) preserveDrafts = true;
        boolean close = updated.close();
        if (requestedAction == Action.EVENT_PREVIEW && updated.success()) eventPreview = updated.message();
        boolean retainReview = (requestedAction == Action.EVENT_REVIEW || requestedAction == Action.PLAYER_MOVE_REVIEW) && updated.success();
        if (retainReview) eventPreview = updated.message();
        if (requestedAction == Action.EVENT_RUN || requestedAction == Action.PLAYER_MOVE || !updated.success()) eventPreview = "";
        state = updated;
        pending = false; refreshPending = false; pendingTicks = idleTicks = 0; requestedAction = null;
        if (!updated.selected().isBlank()) selected = updated.selected();
        if (!updated.message().isBlank()) notice = updated.message();
        if (receivedFor == Action.LOOT_SAVE && !updated.success()) notice = notice + " Draft retained; reload before choosing to replace it.";
        if (updated.detail() != null && updated.detail().region() != null && updated.page() == Page.REGIONS) detailOnCompact = true;
        if (updated.success() && (receivedFor == Action.POINT_CREATE || receivedFor == Action.BLOCK_CREATE)) detailOnCompact = true;
        boolean changedAuthority = priorToken != updated.token();
        if (changedAuthority) {
            confirmation = null;
            if (!retainReview) eventPreview = "";
        }
        if (!stableRefresh) {
            if (updated.picker() == null) pickerAction = null;
            if (updated.detail() != null && !preserveDrafts && (changedAuthority || !updated.detail().id().equals(priorDetail))) loadForms(updated.detail());
            clearAndInit();
        }
        if (close) client.setScreen(null);
    }

    @Override protected void init() {
        buttons.clear(); renderLines.clear(); textInput = pickerSearchInput = null; eventSeconds = lootChance = lootMin = lootMax = lootRefill = null;
        int top = 31;
        int navWidth = Math.max(86, Math.min(100, width / 5));
        boolean compact = width <= 480;
        int mainLeft = navWidth + 7;
        int mainWidth = Math.max(120, width - mainLeft - 7);
        if (confirmation != null) {
            drawLine(confirmation.title(), 12, 40, width - 24, 0xFFFFFFFF);
            wrapped(confirmation.body(), 12, 61, width - 24, 0xFFCCCCCC, Math.max(2, (height - 125) / 10));
            button("Cancel", 12, height - 54, 80, () -> { confirmation = null; if (state.picker() != null) request(Action.REFRESH, state.page(), detailId(), "", state.index(), state.filter(), "", false); else clearAndInit(); }, "Return without applying this change", true);
            button("Confirm: " + confirmation.title(), 98, height - 54, width - 110, () -> {
                Confirmation staged = confirmation; confirmation = null;
                if (staged.token() != state.token()) { notice = "The server state changed. Review the action again."; clearAndInit(); return; }
                staged.execute().run();
            }, "Apply the reviewed action", true);
            button("Close", width - 59, height - 25, 54, 18, this::close, "Close this menu", true);
            return;
        }
        Page[] navigation = state.operator() ? NAVIGATION : new Page[] {Page.PLAYERS};
        for (int i = 0; i < navigation.length; i++) {
            Page page = navigation[i];
            String label = navigationLabel(page);
            int navY = top + i * 20 + (i >= 3 ? 10 : 0);
            button(label, 5, navY, navWidth - 8, 18, () -> open(page), page == state.page() ? "Current page" : "", true);
        }
        if (state.operator()) drawLine("Advanced", 5, top + 3 * 20, navWidth - 8, 0xFFA0A0A0);
        button("Refresh", width - 59, 5, 54, 18, () -> request(Action.REFRESH, state.page(), "", "", state.index(), state.filter(), "", false), "Request current data", true);
        if (state.operator()) button(state.editing() ? "Stop editing" : "Enable editing", Math.max(5, width - 150), 5, 87,
                () -> request(state.editing() ? Action.EDIT_OFF : Action.EDIT_ON, state.page(), "", "", state.index(), state.filter(), "", false),
                state.editing() ? "Leave authoring edit mode" : "Ask the server to enable authoring edit mode", state.editable() || state.editing());
        if (compact && detailOnCompact && state.picker() == null) {
            button("Back to list", mainLeft, top, Math.min(92, mainWidth), 18, () -> { detailOnCompact = false; clearAndInit(); }, "Show selectable items", true);
            detail(mainLeft, top + 23, mainWidth, height - top - 89);
        } else {
            int listWidth = compact ? mainWidth : Math.max(100, mainWidth * 38 / 100);
            list(mainLeft, top, listWidth, height - top - 66);
            if (!compact) detail(mainLeft + listWidth + 5, top, mainWidth - listWidth - 5, height - top - 66);
        }
        if (pickerAction != null && state.picker() == null) promptWidgets(mainLeft, height - 66, mainWidth);
        button("Close", width - 59, height - 25, 54, 18, this::close, "Close this menu", true);
    }

    private void list(int x, int y, int w, int h) {
        if (state.page() == Page.LOCATIONS && state.picker() == null) {
            button("Points", x, y, w / 2 - 2, () -> locations("POINT"), "Named positions used by systems and starting positions", true);
            button("Block sites", x + w / 2, y, w / 2 - 2, () -> locations("BLOCK"), "Named blocks, including optional vanilla references", true);
            y += 20; h -= 20;
        }
        if (state.picker() == null && state.detail() != null) {
            for (Control creator : state.detail().controls()) {
                if (creator.action() == Action.REGION_NEW || creator.action() == Action.CHEST_BIND || creator.action() == Action.POINT_CREATE || creator.action() == Action.BLOCK_CREATE) {
                    control(creator, x, y, w); y += 21; h -= 21; break;
                }
            }
        }
        drawListHeader("Select an item", x, y, w);
        int rows = Math.max(1, Math.min(9, (h - (state.picker() == null ? 40 : 60)) / 20));
        List<Entry> entries = state.picker() == null ? state.entries() : state.picker().entries();
        int maxOffset = Math.max(0, entries.size() - rows);
        listOffset = Math.min(listOffset, maxOffset);
        for (int row = 0; row < rows && row + listOffset < entries.size(); row++) {
            Entry entry = entries.get(row + listOffset);
            String label = entry.label().isBlank() ? entry.id() : entry.label();
            button(label, x, y + 18 + row * 20, w, 18, () -> chooseEntry(entry), entry.description(), true);
        }
        if (entries.isEmpty()) drawLine("Nothing is available here yet.", x, y + 22, w, 0xFFA0A0A0);
        if (state.picker() != null) pickerControls(x, y + 18 + rows * 20, w);
        else pageControls(x, y + 18 + rows * 20, w);
    }

    private void pickerControls(int x, int y, int w) {
        button("<", x, y, 18, 18, () -> pickerPage(-1), "Previous picker page", state.picker().page() > 0);
        button(">", x + 20, y, 18, 18, () -> pickerPage(1), "Next picker page", state.picker().page() + 1 < state.picker().pages());
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x + 42, y, Math.max(55, w - 42), 18, Text.literal("Search")));
        pickerSearchInput = field;
        field.setMaxLength(80); field.setText(pickerSearch);
        field.setChangedListener(value -> pickerSearch = value);
        button("Find", x, y + 20, w, 18, () -> request(Action.OPTIONS, state.page(), detailId(), state.picker().kind(), 0,
                state.filter(), pickerSearch, false), "Search this picker", true);
    }

    private void pageControls(int x, int y, int w) {
        button("<", x, y, 18, 18, () -> page(-1), "Previous page", state.index() > 0);
        button(">", x + 20, y, 18, 18, () -> page(1), "Next page", state.index() + 1 < state.pages());
        drawLine("Page " + (state.index() + 1) + " / " + state.pages(), x + 42, y + 5, w - 42, 0xFFA0A0A0);
    }

    private void detail(int x, int y, int w, int h) {
        Detail detail = state.detail();
        if (detail == null) { drawLine("Choose an item to see its details and actions.", x, y, w, 0xFFA0A0A0); return; }
        detailX = x; detailY = y; detailWidth = w; detailHeight = h;
        drawListHeader(detail.title(), x, y, w);
        int firstWidget = children().size();
        layingOutDetail = true; clipTop = y + 17; clipBottom = y + h - 11;
        int cursor = y + 19 - detailScroll;
        if (detail.region() != null) {
            Control handoff = detail.controls().stream().filter(c -> c.action() == Action.REGION_CONTINUE).findFirst().orElse(null);
            if (handoff != null) { control(handoff, x, cursor, w); cursor += 21; }
        }
        if ((state.page() == Page.EVENTS || state.page() == Page.PLAYERS) && !eventPreview.isBlank()) {
            drawLine(state.page() == Page.EVENTS ? "Event preview" : "Movement review", x, cursor, w, 0xFF77CCFF);
            cursor = wrapped(eventPreview, x, cursor + 10, w, 0xFFCCCCCC, 3);
        }
        cursor = wrapped(detail.description(), x, cursor, w, 0xFFA0A0A0, 3);
        for (var fact : detail.facts()) {
            cursor = wrapped(fact.label() + ": " + fact.value(), x, cursor, w, 0xFFCCCCCC, 8);
        }
        if (state.page() == Page.EVENTS && detail.event() != null) cursor = eventForm(detail.event(), x, cursor, w);
        if (state.page() == Page.REGIONS && detail.region() != null) cursor = regionForm(detail.region(), x, cursor, w);
        if (state.page() == Page.LOOT_TABLES && detail.loot() != null) cursor = lootForm(x, cursor, w, Math.max(45, h - (cursor - y) - 50));
        for (Control unavailable : detail.controls()) if (!unavailable.enabled() && !unavailable.reason().isBlank()) {
            cursor = wrapped("Unavailable: " + unavailable.reason(), x, cursor, w, 0xFFFFCC66, 2); break;
        }
        for (Control control : detail.controls()) {
            if (detail.region() != null && control.action() == Action.REGION_CONTINUE) continue;
            control(control, x, cursor, w); cursor += 20;
        }
        detailContentHeight = cursor + detailScroll - y;
        for (int i = firstWidget; i < children().size(); i++) {
            if (children().get(i) instanceof net.minecraft.client.gui.widget.ClickableWidget widget
                    && (widget.getY() < clipTop || widget.getY() + widget.getHeight() > clipBottom)) {
                widget.visible = false; widget.active = false;
            }
        }
        layingOutDetail = false;
        if (detailContentHeight > h) drawLine("Scroll details " + (detailScroll > 0 ? "↑ " : "") + (detailScroll + h < detailContentHeight ? "↓" : ""), x, y + h - 8, w, 0xFFA0A0A0);
    }

    private int eventForm(EventForm form, int x, int y, int w) {
        button("Mode: " + pretty(eventMode), x, y, w / 3 - 2, () -> { eventMode = next(eventMode, "MANUAL", "ENTRY", "TIMER", "CONDITION"); clearAndInit(); }, "Choose event trigger", state.editing() && state.editable());
        button("Region", x + w / 3, y, w / 3 - 2, () -> beginPicker(new PendingPicker(Action.EVENT_SAVE, detailId(), "REGION")), "Choose the event region", true);
        button("Flag", x + 2 * w / 3, y, w / 3 - 2, () -> beginPicker(new PendingPicker(Action.EVENT_SAVE, detailId(), "FLAG")), "Choose an optional flag", true);
        if (eventMode.equals("TIMER")) {
        drawLine("Delay in active seconds", x, y + 22, w, 0xFFA0A0A0);
        eventSeconds = addDrawableChild(new TextFieldWidget(textRenderer, x, y + 33, w, 18, Text.literal("Active seconds")));
        eventSeconds.setMaxLength(6); eventSeconds.setText(eventSecondsDraft);
        eventSeconds.setTextPredicate(value -> value.matches("[0-9]{0,6}")); eventSeconds.setChangedListener(value -> eventSecondsDraft = value);
        y += 33;
        }
        drawLine("Region: " + blank(eventRegion, "none") + " | Flag: " + blank(eventFlag, "none"), x, y + 23, w, 0xFFA0A0A0);
        return y + 37;
    }

    private int regionForm(RegionForm form, int x, int y, int w) {
        drawLine("Corner 1: " + blank(regionFirst, "use pos1 in world"), x, y, w, 0xFFA0A0A0);
        drawLine("Corner 2: " + blank(regionSecond, "use pos2 in world"), x, y + 10, w, 0xFFA0A0A0);
        button("Set corner 1", x, y + 22, w / 2 - 2, () -> prompt(Action.REGION_POSITION, detailId(), "1:"), "Enter x y z for the first corner", state.editing() && state.editable());
        button("Set corner 2", x + w / 2, y + 22, w / 2 - 2, () -> prompt(Action.REGION_POSITION, detailId(), "2:"), "Enter x y z for the second corner", state.editing() && state.editable());
        return y + 43;
    }

    private int lootForm(int x, int y, int w, int available) {
        if (lootDraft == null) return y;
        drawLine("Entries " + lootDraft.entries().size() + " / " + LootType.MAX_ENTRIES + " · refill " + lootDraft.refillMinutes() + " min", x, y, w, 0xFFA0A0A0); y += 12;
        int rows = Math.min(3, Math.max(1, lootDraft.entries().size()));
        lootEntryOffset = Math.max(0, Math.min(lootEntryOffset, Math.max(0, lootDraft.entries().size() - rows)));
        for (int i = 0; i < rows && i + lootEntryOffset < lootDraft.entries().size(); i++) {
            int index = i + lootEntryOffset;
            LootEntry entry = lootDraft.entries().get(index);
            button((index == lootEntry ? "> " : "") + entry.item() + " " + entry.chancePercent() + "% x" + entry.minCount() + "-" + entry.maxCount(),
                    x, y + i * 20, w, () -> { lootEntry = index; clearAndInit(); }, "Select this independent loot roll", true);
        }
        y += rows * 20;
        if (lootDraft.entries().size() > rows) {
            button("< entries", x, y, w / 2 - 2, () -> { lootEntryOffset = Math.max(0, lootEntryOffset - rows); clearAndInit(); }, "Earlier loot entries", lootEntryOffset > 0);
            button("> entries", x + w / 2, y, w / 2 - 2, () -> { lootEntryOffset = Math.min(Math.max(0, lootDraft.entries().size() - rows), lootEntryOffset + rows); clearAndInit(); }, "Later loot entries", lootEntryOffset + rows < lootDraft.entries().size());
            y += 20;
        }
        button("Add item", x, y, w / 2 - 2, () -> beginPicker(new PendingPicker(Action.LOOT_SAVE, detailId(), "ITEMS")), "Add an item from the registry", lootDraft.entries().size() < LootType.MAX_ENTRIES);
        button("Remove item", x + w / 2, y, w / 2 - 2, this::removeLoot, "Remove selected loot entry", lootEntry >= 0 && lootEntry < lootDraft.entries().size());
        y += 20;
        drawLine("Refill minutes (0 = off)", x, y, w, 0xFFA0A0A0); y += 11;
        lootRefill = numberField(x, y, Math.max(55, w / 2 - 2), "Refill min", refillMinutesDraft, this::setRefillMinutes);
        drawLine("0 disables scheduled refill", x + w / 2, y + 5, w / 2, 0xFFA0A0A0);
        y += 20;
        if (lootEntry >= 0 && lootEntry < lootDraft.entries().size()) {
            LootEntry entry = lootDraft.entries().get(lootEntry);
            String[] raw = lootInputDrafts.getOrDefault(lootEntry, new String[] {Double.toString(entry.chancePercent()), Integer.toString(entry.minCount()), Integer.toString(entry.maxCount())});
            int chanceWidth = Math.max(45, w / 2 - 2);
            drawLine("Chance %", x, y, chanceWidth, 0xFFA0A0A0);
            drawLine("Min / Max", x + chanceWidth + 4, y, w - chanceWidth - 4, 0xFFA0A0A0); y += 11;
            lootChance = numberField(x, y, chanceWidth, "Chance %", raw[0], value -> setLootField(0, value));
            lootMin = numberField(x + chanceWidth + 4, y, Math.max(28, (w - chanceWidth - 4) / 2 - 2), "Min", raw[1], value -> setLootField(1, value));
            lootMax = numberField(x + chanceWidth + 4 + Math.max(28, (w - chanceWidth - 4) / 2), y, Math.max(28, (w - chanceWidth - 4) / 2 - 2), "Max", raw[2], value -> setLootField(2, value));
            y += 20;
        }
        return y;
    }

    private void control(Control control, int x, int y, int w) {
        String label = control.label();
        boolean enabled = control.enabled();
        button(label, x, y, w, () -> activate(control), control.reason(), enabled);
    }

    private void promptWidgets(int x, int y, int width) {
        String hint = pickerAction.kind().startsWith("1:") || pickerAction.kind().startsWith("2:")
                ? "x y z" : "Text";
        textInput = addDrawableChild(new TextFieldWidget(textRenderer, x, y, Math.max(80, width - 54), 18, Text.literal(hint)));
        textInput.setMaxLength(160);
        button("Send", x + Math.max(80, width - 50), y, 50, 18, this::submitPrompt, "Submit this value", true);
    }

    private void submitPrompt() {
        if (pickerAction == null || textInput == null) return;
        String prefix = pickerAction.kind();
        String argument = (prefix.equals("1:") || prefix.equals("2:")) ? prefix + textInput.getText() : textInput.getText();
        Action action = pickerAction.action(); String id = pickerAction.id(); pickerAction = null;
        Control source = state.detail().controls().stream().filter(c -> c.action() == action).findFirst().orElse(null);
        if (source != null) applyControl(source, id, argument); else request(action, state.page(), id, argument, state.index(), state.filter(), "", false);
    }

    private void activate(Control control) {
        if (!control.enabled()) return;
        String input = control.input();
        if (input.isBlank()) { applyControl(control, detailId(), ""); return; }
        if (input.equals("LOOT_DRAFT")) { saveLoot(); return; }
        if (input.equals("EVENT_DRAFT")) { saveEvent(); return; }
        if (input.equals("POSITION1")) { prompt(control.action(), detailId(), "1:"); return; }
        if (input.equals("POSITION2")) { prompt(control.action(), detailId(), "2:"); return; }
        if (input.equals("LOOT_TYPE") || input.equals("DESTINATION") || input.equals("REGION") || input.equals("POINT") || input.equals("BLOCK") || input.equals("FLAG") || input.equals("ITEMS")) {
            beginPicker(new PendingPicker(control.action(), detailId(), input)); return;
        }
        prompt(control.action(), detailId(), "");
    }

    private void saveEvent() {
        long seconds = 0;
        try { if (eventMode.equals("TIMER")) seconds = Long.parseLong(eventSeconds == null ? "0" : eventSeconds.getText()); }
        catch (NumberFormatException exception) { notice = "Timer must be a whole number of seconds."; return; }
        try {
            ObservationConfiguration draft = new ObservationConfiguration(PlayState.Mode.valueOf(eventMode), eventRegion, eventFlag,
                    eventMode.equals("TIMER") ? seconds : 0);
            request(Action.EVENT_SAVE, Page.EVENTS, detailId(), draft.encode(), state.index(), state.filter(), "", false);
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage(); clearAndInit();
        }
    }

    private void saveLoot() {
        try {
            List<LootEntry> entries = new ArrayList<>(lootDraft.entries());
            for (var edited : lootInputDrafts.entrySet()) {
                LootEntry old = entries.get(edited.getKey()); String[] raw = edited.getValue();
                entries.set(edited.getKey(), new LootEntry(old.item(), Double.parseDouble(raw[0]), Integer.parseInt(raw[1]), Integer.parseInt(raw[2]), old.componentsJson()));
            }
            lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), Double.parseDouble(refillMinutesDraft), entries);
            lootValidation = "";
        } catch (IllegalArgumentException exception) { lootValidation = "Check chance (0–100), counts (min ≤ max) and refill minutes."; }
        if (!lootValidation.isBlank()) { notice = lootValidation; clearAndInit(); return; }
        try { Control save = state.detail().controls().stream().filter(c -> c.action() == Action.LOOT_SAVE).findFirst().orElseThrow(); applyControl(save, detailId(), LootTableDraftJson.encode(lootDraft)); }
        catch (IllegalArgumentException exception) { notice = exception.getMessage(); clearAndInit(); }
    }

    private void removeLoot() {
        if (lootDraft == null || lootEntry < 0 || lootEntry >= lootDraft.entries().size()) return;
        int removed = lootEntry;
        List<LootEntry> entries = new ArrayList<>(lootDraft.entries()); entries.remove(removed); lootEntry = -1;
        Map<Integer, String[]> retained = new java.util.HashMap<>();
        lootInputDrafts.forEach((index, raw) -> { if (index != removed) retained.put(index > removed ? index - 1 : index, raw); });
        lootInputDrafts.clear(); lootInputDrafts.putAll(retained);
        lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), entries); clearAndInit();
    }

    private TextFieldWidget numberField(int x, int y, int width, String hint, String value, java.util.function.Consumer<String> changed) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, width, 18, Text.literal(hint)));
        field.setMaxLength(8); field.setText(value); field.setChangedListener(changed); field.setTooltip(Tooltip.of(Text.literal(hint)));
        return field;
    }
    private void setLootField(int field, String value) {
        LootEntry old = lootDraft.entries().get(lootEntry);
        String[] raw = lootInputDrafts.computeIfAbsent(lootEntry, ignored -> new String[] {Double.toString(old.chancePercent()), Integer.toString(old.minCount()), Integer.toString(old.maxCount())});
        raw[field] = value;
        try { replaceLoot(Double.parseDouble(raw[0]), Integer.parseInt(raw[1]), Integer.parseInt(raw[2])); }
        catch (NumberFormatException exception) { lootValidation = "Enter a valid chance, minimum and maximum count."; }
    }
    private void setRefillMinutes(String value) {
        refillMinutesDraft = value;
        try {
            double refill = Double.parseDouble(value);
            lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), refill, lootDraft.entries());
            lootValidation = "";
        } catch (IllegalArgumentException exception) { lootValidation = "Refill minutes must be between 0 and 10080."; }
    }
    private void replaceLoot(Double chance, Integer min, Integer max) {
        if (lootDraft == null || lootEntry < 0 || lootEntry >= lootDraft.entries().size()) return;
        LootEntry old = lootDraft.entries().get(lootEntry);
        double updatedChance = chance == null ? old.chancePercent() : chance;
        int updatedMin = min == null ? old.minCount() : min;
        int updatedMax = max == null ? old.maxCount() : max;
        try {
            List<LootEntry> entries = new ArrayList<>(lootDraft.entries());
            entries.set(lootEntry, new LootEntry(old.item(), updatedChance, updatedMin, updatedMax, old.componentsJson()));
            lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), entries);
            lootValidation = "";
        } catch (IllegalArgumentException exception) { lootValidation = exception.getMessage(); }
    }

    private void beginPicker(PendingPicker action) {
        pickerAction = action; pickerSearch = "";
        request(Action.OPTIONS, state.page(), detailId(), action.kind(), 0, state.filter(), "", false);
    }

    private void chooseEntry(Entry entry) {
        if (state.picker() != null && pickerAction != null) {
            if (pickerAction.kind().equals("ITEMS")) addLoot(entry.id());
            else if (pickerAction.action() == Action.EVENT_SAVE && pickerAction.kind().equals("REGION")) eventRegion = entry.id();
            else if (pickerAction.action() == Action.EVENT_SAVE && pickerAction.kind().equals("FLAG")) eventFlag = entry.id().equals("none") ? "" : entry.id();
            else {
                Action action = pickerAction.action(); String id = pickerAction.id(); pickerAction = null;
                Control source = state.detail().controls().stream().filter(c -> c.action() == action).findFirst().orElseThrow();
                applyControl(source, id, entry.id());
                return;
            }
            pickerAction = null;
            request(Action.REFRESH, state.page(), detailId(), "", state.index(), state.filter(), "", false);
            return;
        }
        select(entry.id());
    }

    private void addLoot(String item) {
        if (lootDraft == null || lootDraft.entries().size() >= LootType.MAX_ENTRIES) return;
        List<LootEntry> entries = new ArrayList<>(lootDraft.entries()); entries.add(new LootEntry(item, 100, 1, 1, "{}"));
        lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), entries);
        lootEntry = entries.size() - 1; lootEntryOffset = Math.max(0, lootEntry - 2); lootValidation = "";
    }

    private void select(String id) {
        selected = id; detailOnCompact = true; listOffset = actionOffset = factOffset = detailScroll = 0;
        pageSelections.put(state.page(), id);
        if (state.page() == Page.LOCATIONS) locationSelections.put(state.filter(), id);
        request(Action.OPEN, state.page(), id, "", state.index(), state.filter(), "", false);
    }
    private void open(Page page) { if (page == Page.LOCATIONS) { locations(locationFilter.isBlank() ? "POINT" : locationFilter); return; } detailOnCompact = false; selected = pageSelections.getOrDefault(page, ""); listOffset = detailScroll = 0; request(Action.OPEN, page, selected, "", 0, "", "", false); }
    private void locations(String filter) { locationFilter = filter; selected = locationSelections.getOrDefault(filter, ""); detailOnCompact = false; listOffset = detailScroll = 0; request(Action.OPEN, Page.LOCATIONS, selected, "", 0, filter, "", false); }
    private void page(int delta) { request(Action.OPEN, state.page(), selected, "", Math.max(0, Math.min(state.pages() - 1, state.index() + delta)), state.filter(), "", false); }
    private void pickerPage(int delta) { if (state.picker() != null) request(Action.OPTIONS, state.page(), detailId(), state.picker().kind(), Math.max(0, Math.min(state.picker().pages() - 1, state.picker().page() + delta)), state.filter(), pickerSearch, false); }
    private void prompt(Action action, String id, String prefix) {
        pickerAction = new PendingPicker(action, id, prefix); clearAndInit();
    }

    private void applyControl(Control control, String id, String argument) {
        Page page = state.page(); int index = state.index(); String filter = state.filter();
        Runnable send = () -> request(control.action(), page, id, argument, index, filter, "", false);
        if (!control.confirm()) { send.run(); return; }
        String effect = switch (control.action()) {
            case LOOT_SAVE -> "Save this entire loot table. Bound chests use it at their next configured fill; no chest is filled now.";
            case LOOT_RELOAD -> "Replace the loaded loot definitions with the server JSON configuration. Unsaved table edits will be discarded.";
            case CHEST_BIND, CHEST_RETYPE -> "Bind the targeted chest to loot table " + argument + ". Future session/timed fills replace all its contents.";
            case CHEST_UNBIND -> "Remove this chest binding. Its current contents stay in the chest.";
            case SETUP_ASSIGN -> "Assign existing site " + argument + " to this requirement. This references its saved revision; geometry is not copied.";
            case SETUP_CLEAR -> "Clear this requirement's assignment. The saved site stays available.";
            case EVENT_RUN -> "Deliver this event to the reviewed live audience.\n" + eventPreview;
            case EVENT_RESET -> "Allow this event to run again. Its configured timer delay starts again; nothing is delivered now.";
            case STARTING_RESET -> "Permit another automatic starting-position attempt for this player. This may move them on the next eligible join.";
            case STARTING_OFF -> "Disable this player's automatic starting position. Their previous placement history is retained.";
            case RESPAWN_OFF -> "Disable this player's custom after-death destination and use their normal Minecraft respawn rules.";
            case PLAYER_MOVE -> "Move this exact player to the reviewed safe destination now.\n" + eventPreview;
            case LEAVE -> "Remove your participation and automatic destinations. Your character card and previous placement history are retained.";
            case PARTICIPANT_REMOVE -> "Stop counting this player as an active participant. Their card and destination settings stay saved.";
            case EVENT_DISABLE_AUTO -> "Disable automatic triggering while preserving manual control, audience and completion history.";
            case EVENT_REMOVE -> "Remove the trigger configuration. The registered event and its completion history stay saved.";
            case CONDITION_SET -> "Set this live game condition to " + argument + ". This can satisfy automatic event prerequisites.";
            case SESSION_START -> "Start a play window and unfreeze world simulation. Bound loot chests receive their configured session fill, replacing contents.";
            case SESSION_END -> "Finish the current play window and freeze world simulation. Campaign progress remains saved.";
            case SITE_DELETE -> "Delete this saved site. Systems pinned to it will require a new assignment.";
            case REGION_SAVE -> "Replace the bounds of this region under the same ID. References to its previous revision must be reviewed.";
            case REGION_NEW -> "Start a new draft named " + argument + ", replacing your current draft. Save any bounds you want to keep before continuing.";
            case REGION_EDIT -> "Load this region's saved bounds into your current draft. Unsaved positions in the previous draft will be discarded.";
            default -> "Apply this change to the selected target.";
        };
        String target = state.detail().title();
        if (control.action() == Action.CHEST_BIND) target = state.detail().facts().stream().filter(f -> f.label().equals("Looked-at container")).map(f -> f.value()).findFirst().orElse("No supported container in reach");
        confirmation = new Confirmation(control.label(), "Target: " + blank(target, id) + "\n" + effect, state.token(), send);
        clearAndInit();
    }

    private void request(Action action, Page page, String id, String argument, int index, String filter, String query, boolean background) {
        if (pending || !ClientPlayNetworking.canSend(CoreMenuPayloads.Request.ID)) { notice = "Chronicle menu is unavailable; wait for the server response."; return; }
        ClientPlayNetworking.send(new CoreMenuPayloads.Request(action, page, id, argument, state.token(), index, filter, query));
        pending = true; refreshPending = background; requestedAction = action; pendingTicks = idleTicks = 0;
        if (!background) clearAndInit();
    }

    @Override public void tick() {
        if (pending) { if (++pendingTicks >= RESPONSE_TIMEOUT) { pending = false; refreshPending = false; notice = "No server response after 10 seconds. Try Refresh."; clearAndInit(); } return; }
        if (confirmation != null || pickerAction != null) return;
        if (++idleTicks >= REFRESH_TICKS) request(Action.REFRESH, state.page(), detailId(), "", state.index(), state.filter(), "", true);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= detailX && mouseX < detailX + detailWidth && mouseY >= detailY && mouseY < detailY + detailHeight && state.detail() != null && state.picker() == null) {
            detailScroll = Math.max(0, Math.min(Math.max(0, detailContentHeight - detailHeight + 12), detailScroll + (verticalAmount < 0 ? 20 : -20)));
            clearAndInit();
            return true;
        }
        if (state.picker() == null && mouseY > 45 && mouseY < height - 60) { listOffset = Math.max(0, listOffset + (verticalAmount < 0 ? 1 : -1)); clearAndInit(); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xF01C211A);
        context.drawTextWithShadow(textRenderer, Text.literal("Chronicle"), 5, 6, 0xFFFFFFFF);
        String mode = state.editing() ? "Editing enabled" : "Editing off";
        draw(context, "Session " + state.session() + " " + state.phase() + " · " + mode + (state.hold().isBlank() ? "" : " · Hold: " + state.hold()), 5, 20, width - 10, state.editing() ? 0xFF77FF77 : 0xFFFFCC66);
        String message = notice.isBlank() ? (pending ? "Waiting for server..." : "Choose an item, then use the actions on the right.") : notice;
        draw(context, message, 5, height - 43, width - 68, state.success() ? 0xFF77FF77 : 0xFFFFCC66);
        if (!state.clock().isBlank()) draw(context, state.clock(), 5, height - 24, width - 70, 0xFFA0A0A0);
        for (RenderLine line : renderLines) if (line.top() == 0 || (line.y() >= line.top() && line.y() + 9 <= line.bottom())) draw(context, line.value(), line.x(), line.y(), line.width(), line.color());
        super.render(context, mouseX, mouseY, deltaTicks);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { CoreMenuClient.closed(state.token()); super.close(); }

    private void loadForms(Detail detail) {
        if (detail == null) return;
        if (detail.event() != null) { eventMode = detail.event().mode(); eventRegion = detail.event().region(); eventFlag = detail.event().flag(); eventSecondsDraft = Long.toString(detail.event().seconds()); }
        if (detail.region() != null) { regionFirst = detail.region().first(); regionSecond = detail.region().second(); }
        if (detail.loot() != null) { lootDraft = detail.loot(); lootEntry = -1; lootEntryOffset = 0; refillMinutesDraft = Double.toString(detail.loot().refillMinutes()); lootValidation = ""; lootInputDrafts.clear(); }
    }
    private String detailId() { return state.detail() == null ? selected : state.detail().id(); }
    private void button(String label, int x, int y, int w, int h, Runnable action, String hint, boolean enabled) {
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, Math.max(1, w - 8))), ignored -> action.run()).dimensions(x, y, Math.max(1, w), h).build());
        button.active = enabled && !pending; button.setTooltip(Tooltip.of(Text.literal(hint == null ? "" : hint))); buttons.put(label, button);
    }
    private void button(String label, int x, int y, int w, Runnable action, String hint, boolean enabled) { button(label, x, y, w, 18, action, hint, enabled); }
    private void drawListHeader(String value, int x, int y, int width) { drawLine(value, x, y + 4, width, 0xFFFFFFFF); }
    private void drawLine(String value, int x, int y, int available, int color) { renderLines.add(new RenderLine(value, x, y, available, color, layingOutDetail ? clipTop : 0, layingOutDetail ? clipBottom : 0)); }
    private void draw(DrawContext context, String value, int x, int y, int available, int color) { if (context != null) context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(value, Math.max(1, available))), x, y, color); }
    private int wrapped(String value, int x, int y, int width, int color, int maxLines) {
        int line = 0;
        for (var ordered : textRenderer.wrapLines(Text.literal(value), Math.max(1, width))) {
            if (line >= maxLines) break;
            StringBuilder plain = new StringBuilder();
            ordered.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
            drawLine(plain.toString(), x, y + line * 10, width, color);
            line++;
        }
        return y + Math.max(1, line) * 10;
    }
    private static String pretty(String value) { return value.isBlank() ? "none" : value.substring(0, 1) + value.substring(1).toLowerCase(Locale.ROOT); }
    private static String navigationLabel(Page page) {
        for (int index = 0; index < NAVIGATION.length; index++) if (NAVIGATION[index] == page) return NAVIGATION_LABELS[index];
        return page.name();
    }
    private static String next(String value, String... values) { for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return values[(i + 1) % values.length]; return values[0]; }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private record Confirmation(String title, String body, long token, Runnable execute) { }
    private record PendingPicker(Action action, String id, String kind) { }
    private record RenderLine(String value, int x, int y, int width, int color, int top, int bottom) { }
    public record ButtonProbe(int identity, boolean active, boolean hovered, boolean focused) { }
}
