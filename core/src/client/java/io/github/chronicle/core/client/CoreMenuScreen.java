package io.github.chronicle.core.client;

import com.google.gson.Gson;
import io.github.chronicle.core.books.BookChance;
import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.loot.LootTableDraftJson;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.menu.CoreMenuLayout;
import io.github.chronicle.core.menu.CoreMenuPresentation;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.BlockLimitType;
import io.github.chronicle.core.menu.CoreMenuModel.BlockLimitsForm;
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
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private static final int RESPONSE_TIMEOUT = 20 * 10;
    private static final int REFRESH_TICKS = 20 * 2;
    private static final Page[] NAVIGATION = {Page.SESSION, Page.PLAYERS, Page.EVENTS, Page.CHESTS,
            Page.LOOT_TABLES, Page.BOOKS_LORE, Page.REGIONS, Page.LOCATIONS, Page.SETUP};
    private static final String[] NAVIGATION_LABELS = {"Session", "Players", "Events", "Chests",
            "Loot Tables", "Books & Lore", "Regions", "Locations", "Game setup"};

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
    private boolean debug;
    private String selected = "";
    private String locationFilter = "";
    private final Map<Page, String> pageSelections = new java.util.EnumMap<>(Page.class);
    private final Map<String, String> locationSelections = new java.util.HashMap<>();
    private String notice = "";
    private String eventPreview = "";
    private Confirmation confirmation;
    private PendingPicker pickerAction;
    private String pickerSearch = "";
    private String promptDraft = "";
    private TextFieldWidget textInput;
    private TextFieldWidget pickerSearchInput;
    private int detailScroll, detailContentHeight, detailX, detailY, detailWidth, detailHeight;
    private boolean layingOutDetail;
    private int clipTop, clipBottom;
    private TextFieldWidget eventSeconds, lootRefill;
    private TextFieldWidget lootChance, lootMin, lootMax;
    private String eventMode = "MANUAL", eventRegion = "", eventFlag = "", eventSecondsDraft = "0";
    private String regionFirst = "", regionSecond = "";
    private LootType lootDraft, lootBase;
    private boolean lootDraftStale;
    private final Map<String,LootHandoff> lootHandoffs = new java.util.HashMap<>();
    private record LootHandoff(LootType draft,LootType base,int entry,int book,int entryOffset,int bookOffset,
                               boolean booksTab,String refill,String validation,Map<Integer,String[]> items,Map<Integer,String> books) { }

    private int lootEntry = -1;
    private int lootEntryOffset;
    private int lootBook = -1;
    private int lootBookOffset;
    private boolean lootBooksTab;
    private String refillMinutesDraft = "0", lootValidation = "";
    private final Map<Integer, String[]> lootInputDrafts = new java.util.HashMap<>();
    private final Map<Integer, String> loreChanceDrafts = new java.util.HashMap<>();
    private final Map<String, io.github.chronicle.core.menu.CoreMenuModel.BookSummary> bookSummaries = new java.util.HashMap<>();
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private final List<RenderLine> renderLines = new ArrayList<>();
    private CoreMenuLayout.Layout layout;
    private LimitDialogStage limitDialogStage = LimitDialogStage.NONE;
    private String limitDraft = "";
    private long limitPolicyRevision = -1;
    private int limitChooserOffset;
    private BoundsProbe limitDialogBounds;
    private TextFieldWidget limitValueInput;
    private ButtonWidget limitSaveButton;
    private View limitBackdrop;
    private boolean limitCloseRequested;
    private String limitServerProblem = "";
    private BookEditorScreen failedBookEditor;
    private long failedBookToken;

    public CoreMenuScreen(View initial) {
        super(Text.literal("Chronicle"));
        state = initial;
        selected = initial.selected();
        notice = initial.message();
        debug = EditorMenuPreferences.debug();
        loadForms(initial.detail());
    }

    public View state() { return state; }
    public boolean pending() { return pending; }
    /** Native-smoke hook for presentation-only fact visibility. */
    public List<String> debugVisibleFactLabelsForTest() {
        if (state.detail() == null) return List.of();
        return state.detail().facts().stream().filter(fact -> CoreMenuPresentation.showFact(fact, debug))
                .map(fact -> fact.label()).toList();
    }
    /** Native-smoke hook for the shared framed popup content bounds. */
    public BoundsProbe contentBoundsForTest() {
        CoreMenuLayout.Bounds content = activeLayout().content();
        return new BoundsProbe(content.x(), content.y(), content.width(), content.height());
    }
    /** Native-smoke hook for every visible native child, including text fields. */
    public List<WidgetBounds> visibleWidgetBoundsForTest() {
        return children().stream()
                .filter(child -> child instanceof net.minecraft.client.gui.widget.ClickableWidget widget && widget.visible)
                .map(child -> (net.minecraft.client.gui.widget.ClickableWidget) child)
                .map(widget -> new WidgetBounds(widget.getMessage().getString(), widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()))
                .toList();
    }
    /** Native-smoke hook for the stable header controls. */
    public HeaderBoundsForTest headerBoundsForTest() {
        return new HeaderBoundsForTest(boundsFor("Refresh"), boundsFor(debugLabel()),
                state.operator() ? boundsFor(state.editing() ? "Stop editing" : "Enable editing") : null);
    }
    public boolean limitDialogOpenForTest() { return limitDialogStage != LimitDialogStage.NONE; }
    public String limitDialogStageForTest() { return limitDialogStage.name().toLowerCase(Locale.ROOT); }
    public String limitValueForTest() { return limitDraft; }
    public void setLimitValueForTest(String value) { if (limitValueInput == null) throw new IllegalStateException("Block-limit value is not editable."); limitValueInput.setText(value); }
    public boolean limitFieldEnabledForTest() { return limitValueInput != null && limitValueInput.active; }
    public Page limitBackgroundPageForTest() { return limitBackdrop == null ? state.page() : limitBackdrop.page(); }
    public String limitBackgroundSelectedForTest() { return limitBackdrop == null ? state.selected() : limitBackdrop.selected(); }
    public boolean debugForTest() { return debug; }
    public void toggleDebugForTest() { setDebug(!debug); }
    public boolean triggerForTest(String label) {
        ButtonWidget button = buttons.get(label);
        if (button == null || !button.active || !button.visible) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
        return true;
    }
    public void selectForTest(String id) {
        if (limitDialogStage == LimitDialogStage.CHOOSE) {
            BlockLimitType type = limitForm().types().stream().filter(row -> row.typeId().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Block-limit type is unavailable: " + id));
            selectLimitType(type);
            return;
        }
        var entries = state.picker() == null ? state.entries() : state.picker().entries();
        Entry entry = entries.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Entry is not on this page: " + id));
        chooseEntry(entry);
    }
    public void searchForTest(String value) { if (pickerSearchInput == null) throw new IllegalStateException("Open a picker first."); pickerSearchInput.setText(value); }
    public void scrollDetailForTest(int direction) { mouseScrolled(detailX + detailWidth / 2.0, detailY + detailHeight / 2.0, 0, direction < 0 ? -1 : 1); }
    public boolean hasTextPromptForTest() { return textInput != null; }
    public String textForTest() { if (textInput == null) throw new IllegalStateException("No text prompt is open."); return textInput.getText(); }
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
        long priorLimitRevision = state.blockLimits() == null ? -1 : state.blockLimits().policyRevision();
        boolean retainPrompt = textInput != null && pickerAction != null && state.picker() == null
                && updated.picker() == null && (receivedFor == null || receivedFor == Action.REFRESH)
                && state.page() == updated.page() && state.filter().equals(updated.filter())
                && updated.detail() != null && updated.detail().equals(state.detail())
                && state.phase().equals(updated.phase()) && state.session() == updated.session()
                && state.hold().equals(updated.hold())
                && updated.operator() && updated.editing() && updated.editable();
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
        if (updated.success() && (receivedFor == Action.POINT_CREATE || receivedFor == Action.BLOCK_CREATE || receivedFor == Action.BLOCK_LIMITS || receivedFor == Action.MANUAL_REFERENCES)) detailOnCompact = true;
        if (receivedFor == Action.BOOK_CREATE && updated.success() && updated.detail() != null && updated.detail().book() != null) {
            failedBookEditor = null;
            openBookEditor(updated.detail().book(), BookEditorScreen.Mode.PAGES, false);
            return;
        }
        if (receivedFor == Action.BOOK_KEEP && updated.success() && updated.detail() != null && updated.detail().lore() != null && updated.detail().lore().keep() != null) {
            client.setScreen(new BookKeepScreen(this, updated.detail().lore().keep(), this::sendKeep));
            return;
        }
        if ((receivedFor == Action.BOOK_CREATE || receivedFor == Action.BOOK_SAVE) && !updated.success() && failedBookEditor != null) {
            BookEditorScreen retry = failedBookEditor;
            failedBookEditor = null;
            retry.serverRejected(blank(updated.message(), "The server rejected this book definition. Correct it and save again."), failedBookToken != updated.token());
            client.setScreen(retry);
            return;
        }
        if (receivedFor == Action.BOOK_CREATE || receivedFor == Action.BOOK_SAVE) { failedBookEditor = null; failedBookToken = 0; }
        boolean changedAuthority = priorToken != updated.token();
        if (changedAuthority) {
            confirmation = null;
            if (!retainReview) eventPreview = "";
        }
        if (limitDialogStage == LimitDialogStage.NONE && receivedFor == Action.REFRESH) limitBackdrop = null;
        if (limitCloseRequested) {
            limitCloseRequested = false;
            pickerAction = null;
            request(Action.REFRESH, Page.SETUP, "limits", "", 0, "", "", false);
            return;
        }
        if(receivedFor==Action.LOOT_RELOAD&&updated.success()&&updated.detail()!=null)lootHandoffs.remove(updated.detail().id());
        if(receivedFor==Action.LOOT_SAVE&&updated.success()&&updated.detail()!=null)lootHandoffs.remove(updated.detail().id());
        updateLimitDialogAfterResponse(receivedFor, priorLimitRevision);
        if (!stableRefresh) {
            if (updated.picker() == null && !retainPrompt) { pickerAction = null; promptDraft = ""; }
            if (updated.detail() != null && !preserveDrafts && (changedAuthority || !updated.detail().id().equals(priorDetail))) loadForms(updated.detail());
            if(updated.detail()!=null&&updated.detail().loot()!=null)restoreLootHandoff(updated.detail().loot());
            clearAndInit();
        }
        if (close) client.setScreen(null);
    }

    /** Applies an unsolicited refresh while a child editor is visible without rebuilding or closing that child. */
    void acceptWhileEditorOpen(View updated) {
        state = updated;
        pending = false; refreshPending = false; pendingTicks = idleTicks = 0; requestedAction = null;
        if (!updated.selected().isBlank()) selected = updated.selected();
        if (!updated.message().isBlank()) notice = updated.message();
    }

    @Override protected void init() {
        buttons.clear(); renderLines.clear(); textInput = pickerSearchInput = limitValueInput = null; eventSeconds = lootChance = lootMin = lootMax = lootRefill = null;
        limitSaveButton = null;
        View liveState = state;
        boolean showLimitBackdrop = limitBackdrop != null;
        if (showLimitBackdrop) state = limitBackdrop;
        layout = CoreMenuLayout.forViewport(width, height);
        int top = layout.bodyTop();
        int navWidth = layout.navWidth();
        boolean compact = layout.compact();
        int mainLeft = layout.mainLeft();
        int mainWidth = layout.mainWidth();
        if (confirmation != null && !showLimitBackdrop) {
            drawLine(confirmation.title(), layout.content().x(), top, layout.content().width(), 0xFFFFFFFF);
            wrapped(confirmation.body(), layout.content().x(), top + 21, layout.content().width(), 0xFFCCCCCC,
                    Math.max(2, (layout.bodyBottom() - top - 47) / 10));
            int confirmationY = Math.max(top + 42, layout.bodyBottom() - 18);
            button("Cancel", layout.content().x(), confirmationY, 80, () -> { confirmation = null; if (state.picker() != null) request(Action.REFRESH, state.page(), detailId(), "", state.index(), state.filter(), "", false); else clearAndInit(); }, "Return without applying this change", true);
            button("Confirm: " + confirmation.title(), layout.content().x() + 86, confirmationY, Math.max(1, layout.content().width() - 86), () -> {
                Confirmation staged = confirmation; confirmation = null;
                if (staged.token() != state.token()) { notice = "The server state changed. Review the action again."; clearAndInit(); return; }
                staged.execute().run();
            }, "Apply the reviewed action", true);
            button("Close", layout.content().right() - 54, layout.footerY(), 54, 18, this::close, "Close this menu", true);
            return;
        }
        Page[] navigation = state.operator() ? NAVIGATION : new Page[] {Page.PLAYERS};
        for (int i = 0; i < navigation.length; i++) {
            Page page = navigation[i];
            String label = navigationLabel(page);
            int navY = top + i * 19 + (i >= 3 ? 10 : 0);
            button(label, layout.content().x(), navY, navWidth, 18, () -> open(page), page == state.page() ? "Current page" : "", true);
        }
        if (state.operator()) drawLine("Advanced", layout.content().x(), top + 3 * 19, navWidth, 0xFFA0A0A0);
        int refreshX = layout.content().right() - 54;
        button("Refresh", refreshX, layout.headerY(), 54, 18, () -> request(Action.REFRESH, state.page(), detailId(), "", state.index(), state.filter(), "", false), "Request current data", true);
        if (state.operator()) {
            int debugX = refreshX - 2 - 60;
            button(debugLabel(), debugX, layout.headerY(), 60, 18, () -> setDebug(!debug), "Show or hide editor details on this client", true);
            button(state.editing() ? "Stop editing" : "Enable editing", debugX - 2 - 87, layout.headerY(), 87, 18,
                () -> request(state.editing() ? Action.EDIT_OFF : Action.EDIT_ON, state.page(), detailId(), "", state.index(), state.filter(), "", false),
                state.editing() ? "Leave authoring edit mode" : "Ask the server to enable authoring edit mode", state.editable() || state.editing());
        }
        if (compact && detailOnCompact && state.picker() == null) {
            button("Back to list", mainLeft, top, Math.min(92, mainWidth), 18, () -> { detailOnCompact = false; clearAndInit(); }, "Show selectable items", true);
            detail(mainLeft, top + 23, mainWidth, layout.bodyBottom() - (top + 23));
        } else {
            int listWidth = compact ? mainWidth : Math.max(100, mainWidth * 38 / 100);
            list(mainLeft, top, listWidth, layout.bodyBottom() - top);
            if (!compact) detail(mainLeft + listWidth + 5, top, mainWidth - listWidth - 5, layout.bodyBottom() - top);
        }
        if (!showLimitBackdrop && pickerAction != null && state.picker() == null) promptWidgets(mainLeft, Math.max(top, layout.bodyBottom() - 18), mainWidth);
        button("Close", layout.content().right() - 54, layout.footerY(), 54, 18, this::close, "Close this menu", true);
        if (showLimitBackdrop) state = liveState;
        if (limitDialogStage != LimitDialogStage.NONE) {
            disableUnderlyingWidgets();
            addDrawable((context, mouseX, mouseY, delta) -> context.fill(layout.content().x(), layout.content().y(), layout.content().right(), layout.content().bottom(), 0x99000000));
            limitDialog();
        }
    }

    private void list(int x, int y, int w, int h) {
        if (state.page() == Page.LOCATIONS && state.picker() == null) {
            button("Points", x, y, w / 2 - 2, () -> locations("POINT"), "Named positions used by systems and starting positions", true);
            button("Block sites", x + w / 2, y, w / 2 - 2, () -> locations("BLOCK"), "Named blocks, including optional vanilla references", true);
            y += 20; h -= 20;
        }
        if (state.picker() == null && state.detail() != null) {
            for (Control creator : state.detail().controls()) {
                if (creator.action() == Action.REGION_NEW || creator.action() == Action.CHEST_BIND || creator.action() == Action.POINT_CREATE || creator.action() == Action.BLOCK_CREATE || creator.action() == Action.BOOK_NEW) {
                    control(creator, x, y, w); y += 21; h -= 21;
                }
            }
        }
        int rows = Math.max(1, Math.min(12, (h - (state.picker() == null ? 40 : 60)) / 20));
        List<Entry> entries = state.picker() == null ? state.entries() : state.picker().entries();
        int maxOffset = Math.max(0, entries.size() - rows);
        listOffset = Math.clamp(listOffset, 0, maxOffset);
        drawListHeader("Select an item", x, y, maxOffset > 0 ? w - 42 : w);
        if(maxOffset>0){
            button("↑",x+w-39,y,18,14,()->{listOffset--;clearAndInit();},"Earlier items in this page",listOffset>0);
            button("↓",x+w-19,y,18,14,()->{listOffset++;clearAndInit();},"More items in this page",listOffset<maxOffset);
        }
        for (int row = 0; row < rows && row + listOffset < entries.size(); row++) {
            Entry entry = entries.get(row + listOffset);
            String label = entry.label().isBlank() ? entry.id() : entry.label();
            button(label, x, y + 18 + row * 20, w, 18, () -> chooseEntry(entry), entry.description(), true);
        }
        if (entries.isEmpty()) drawLine(state.page() == Page.LOCATIONS && "BLOCK".equals(state.filter()) ? "No managed blocks or manual block sites." : "Nothing is available here yet.", x, y + 22, w, 0xFFA0A0A0);
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
        button("Cancel selection", x, y + 40, w, 18, this::cancelPicker, "Return without changing this selection", true);
    }

    private void cancelPicker() {
        pickerAction=null;pickerSearch="";
        request(Action.REFRESH,state.page(),detailId(),"",state.index(),state.filter(),"",false);
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
        cursor = wrapped(detail.description(), x, cursor, w, 0xFFFFFFFF, 3);
        if (detail.book() != null) {
            drawLine("Author: " + blank(detail.book().author(), "none"), x, cursor, w, 0xFFCCCCCC); cursor += 10;
            drawLine("Pages: " + detail.book().pages().size(), x, cursor, w, 0xFFCCCCCC); cursor += 10;
            drawLine("Discovery: " + bookRuleLabel(detail.book().rule()), x, cursor, w, 0xFFCCCCCC); cursor += 10;
        }
        if (detail.lore() != null && detail.lore().keep() != null) {
            var keep = detail.lore().keep();
            drawLine("Keeping: " + keep.status() + " · " + keep.tableLabel(), x, cursor, w, 0xFFCCCCCC); cursor += 10;
            if (debug && !keep.location().isBlank()) { drawLine("Kept copy: " + keep.location(), x, cursor, w, 0xFFA0A0A0); cursor += 10; }
        }
        for (var fact : detail.facts()) if (CoreMenuPresentation.showFact(fact, debug)) {
            cursor = wrapped(fact.label() + ": " + fact.value(), x, cursor, w, 0xFFCCCCCC, 8);
        }
        if (state.page() == Page.EVENTS && detail.event() != null) cursor = eventForm(detail.event(), x, cursor, w);
        if (state.page() == Page.REGIONS && detail.region() != null) cursor = regionForm(detail.region(), x, cursor, w);
        if (state.page() == Page.LOOT_TABLES && detail.loot() != null) cursor = lootForm(x, cursor, w, Math.max(45, h - (cursor - y) - 50));
        if (detail.book() != null) {
            button("Preview", x, cursor, w, () -> client.setScreen(new BookPreviewScreen(this, detail.book())),
                    "Read this definition locally without saving, issuing, or mutating inventory", true);
            cursor += 20;
        }
        if (detail.lore() != null && state.page() == Page.BOOKS_LORE) {
            for (Entry use : detail.lore().tableUses()) {
                button("Open table: " + use.label(), x, cursor, w, () -> request(Action.OPEN, Page.LOOT_TABLES, use.id(), "", 0, "", "", false), "Open this read-only usage", true); cursor += 20;
            }
            for (Entry use : detail.lore().chestUses()) {
                button("Open chest: " + use.label(), x, cursor, w, () -> request(Action.OPEN, Page.CHESTS, use.id(), "", 0, "", "", false), "Open this read-only usage", true); cursor += 20;
            }
        }
        if (detail.lore() != null && state.page() == Page.CHESTS && detail.lore().direct() != null) {
            drawLine("Specific lore book: " + bookLabel(detail.lore().direct().bookId()) + " · " + detail.lore().direct().chancePercent() + "%", x, cursor, w, 0xFFCCCCCC); cursor += 12;
        }
        for (Control unavailable : detail.controls()) if (!unavailable.enabled() && !unavailable.reason().isBlank()) {
            cursor = wrapped("Unavailable: " + unavailable.reason(), x, cursor, w, 0xFFFFCC66, 2); break;
        }
        for (Control control : detail.controls()) {
            if (control.action()==Action.BOOK_NEW || detail.region() != null && control.action() == Action.REGION_CONTINUE) continue;
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
        boolean editing = canEditLoot();
        drawLine("Entries " + (lootDraft.entries().size()+lootDraft.books().size()) + " / " + LootType.MAX_ENTRIES, x,y,w,0xFFA0A0A0);y+=12;
        drawLine("Refill minutes (0 = off)",x,y,w,0xFFA0A0A0);y+=11;
        lootRefill=numberField(x,y,Math.max(55,w/2-2),"Refill min",refillMinutesDraft,this::setRefillMinutes);lootRefill.active=editing;
        drawLine("0 disables timed refill",x+w/2,y+5,w/2,0xFFA0A0A0);y+=21;
        button(lootBooksTab?"Items":"> Items",x,y,w/2-2,()->{lootBooksTab=false;clearAndInit();},"Ordinary item rolls",true);
        button(lootBooksTab?"> Lore books":"Lore books",x+w/2,y,w/2-2,()->{lootBooksTab=true;clearAndInit();},"Named book references",true);y+=21;
        if(lootBooksTab)return loreBooksForm(x,y,w);
        int rows = Math.min(3, Math.max(1, lootDraft.entries().size()));
        lootEntryOffset = Math.max(0, Math.min(lootEntryOffset, Math.max(0, lootDraft.entries().size() - rows)));
        for (int i = 0; i < rows && i + lootEntryOffset < lootDraft.entries().size(); i++) {
            int index = i + lootEntryOffset;
            LootEntry entry = lootDraft.entries().get(index);
            button((index == lootEntry ? "> " : "") + io.github.chronicle.core.loot.LootEntryLabel.of(entry) + " " + entry.chancePercent() + "% x" + entry.minCount() + "-" + entry.maxCount(),
                    x, y + i * 20, w, () -> { lootEntry = index; clearAndInit(); }, "Select this independent loot roll", true);
        }
        y += rows * 20;
        if (lootDraft.entries().size() > rows) {
            button("< entries", x, y, w / 2 - 2, () -> { lootEntryOffset = Math.max(0, lootEntryOffset - rows); clearAndInit(); }, "Earlier loot entries", lootEntryOffset > 0);
            button("> entries", x + w / 2, y, w / 2 - 2, () -> { lootEntryOffset = Math.min(Math.max(0, lootDraft.entries().size() - rows), lootEntryOffset + rows); clearAndInit(); }, "Later loot entries", lootEntryOffset + rows < lootDraft.entries().size());
            y += 20;
        }
        button("Add item", x, y, w / 2 - 2, () -> beginPicker(new PendingPicker(Action.LOOT_SAVE, detailId(), "ITEMS")), "Add an item from the registry", editing && lootDraft.entries().size() + lootDraft.books().size() < LootType.MAX_ENTRIES);
        button("Remove item", x + w / 2, y, w / 2 - 2, this::removeLoot, "Remove selected loot entry", editing && lootEntry >= 0 && lootEntry < lootDraft.entries().size());
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
            lootChance.active = lootMin.active = lootMax.active = editing;
            y += 20;
        }
        return y;
    }

    private int loreBooksForm(int x, int y, int w) {
        boolean editing = state.editing() && state.editable();
        boolean canKeep = state.phase().equals("ACTIVE") && state.hold().isBlank();
        int rows = Math.min(3, Math.max(1, lootDraft.books().size()));
        lootBookOffset = Math.max(0, Math.min(lootBookOffset, Math.max(0, lootDraft.books().size() - rows)));
        for (int i = 0; i < rows && i + lootBookOffset < lootDraft.books().size(); i++) {
            int index = i + lootBookOffset; BookChance value = lootDraft.books().get(index);
            button((index == lootBook ? "> " : "") + bookLabel(value.bookId()) + " " + value.chancePercent() + "%", x, y + i * 20, w,
                    () -> { lootBook = index; clearAndInit(); }, "Named lore-book roll", true);
        }
        y += rows * 20;
        if(lootDraft.books().size()>rows){
            button("< books",x,y,w/2-2,()->{lootBookOffset=Math.max(0,lootBookOffset-rows);clearAndInit();},"Earlier books",lootBookOffset>0);
            button("> books",x+w/2,y,w/2-2,()->{lootBookOffset=Math.min(lootDraft.books().size()-rows,lootBookOffset+rows);clearAndInit();},"Later books",lootBookOffset+rows<lootDraft.books().size());y+=20;
        }
        button("Add book…", x, y, w / 3 - 2, () -> beginPicker(new PendingPicker(Action.BOOK_REF_ADD, detailId(), "BOOKS")), "Add at the default 4% chance", lootDraft.entries().size() + lootDraft.books().size() < LootType.MAX_ENTRIES);
        for (ButtonWidget button : buttons.values()) if (button.getMessage().getString().startsWith("Add book")) button.active &= editing;
        button("Remove book", x + w / 3, y, w / 3 - 2, this::removeBookRef, "Remove this local table reference", editing && lootBook >= 0 && lootBook < lootDraft.books().size());
        button("Open book", x + 2 * w / 3, y, w / 3 - 2, this::openSelectedBook, "Open this definition read-only", lootBook >= 0 && lootBook < lootDraft.books().size());
        y += 20;
        if (lootBook >= 0 && lootBook < lootDraft.books().size()) {
            BookChance value = lootDraft.books().get(lootBook); String raw = loreChanceDrafts.getOrDefault(lootBook, Double.toString(value.chancePercent()));
            drawLine("Chance %", x, y, w / 2 - 2, 0xFFA0A0A0);
            lootChance = numberField(x, y + 11, Math.max(55, w / 2 - 2), "Chance %", raw, this::setBookChance);
            lootChance.active = editing;
            button("Keep placed…", x + w / 2 + 2, y + 11, w / 2 - 2, () -> request(Action.BOOK_KEEP, Page.LOOT_TABLES, detailId(), value.bookId(), state.index(), state.filter(), "", false), "Keep a copy available across this table's bound chests", true);
            for (ButtonWidget button : buttons.values()) if (button.getMessage().getString().startsWith("Keep placed")) button.active &= canKeep;
            y += 31;
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
        int fieldWidth=Math.max(80,width-106);
        textInput = addDrawableChild(new TextFieldWidget(textRenderer, x, y, fieldWidth, 18, Text.literal(hint)));
        textInput.setMaxLength(160); textInput.setText(promptDraft); textInput.setChangedListener(value -> promptDraft = value);
        button("Cancel",x+fieldWidth+4,y,48,18,this::cancelPrompt,"Close this text entry without applying it",true);
        button("Send",x+fieldWidth+54,y,48,18,this::submitPrompt,"Submit this value",true);
    }

    private void cancelPrompt() {
        pickerAction=null; promptDraft="";
        clearAndInit();
    }

    private void submitPrompt() {
        if (pickerAction == null || textInput == null) return;
        String prefix = pickerAction.kind();
        String argument = (prefix.equals("1:") || prefix.equals("2:")) ? prefix + textInput.getText() : textInput.getText();
        Action action = pickerAction.action(); String id = pickerAction.id(); pickerAction = null; promptDraft="";
        Control source = state.detail().controls().stream().filter(c -> c.action() == action).findFirst().orElse(null);
        if (source != null) applyControl(source, id, argument); else request(action, state.page(), id, argument, state.index(), state.filter(), "", false);
    }

    private void activate(Control control) {
        if (!control.enabled()) return;
        if (control.action() == Action.BOOK_NEW) {
            openBookEditor(new BookDefinition("chronicle_core:draft", "Untitled book", "", "Draft lore book", List.of(""), BookDefinition.Rule.OFF), BookEditorScreen.Mode.DETAILS, true);
            return;
        }
        if (state.detail() != null && state.detail().book() != null) {
            if (control.action() == Action.BOOK_EDIT_DETAILS) { openBookEditor(state.detail().book(), BookEditorScreen.Mode.DETAILS, false); return; }
            if (control.action() == Action.BOOK_EDIT_PAGES) { openBookEditor(state.detail().book(), BookEditorScreen.Mode.PAGES, false); return; }
            if (control.action() == Action.BOOK_RULE) { openBookEditor(state.detail().book(), BookEditorScreen.Mode.RULE, false); return; }
        }
        if (control.action() == Action.BOOK_KEEP) {
            if (state.page() == Page.BOOKS_LORE && state.detail() != null && state.detail().lore() != null
                    && state.detail().lore().keep() != null && state.detail().lore().keep().active()) {
                request(Action.BOOK_KEEP, Page.BOOKS_LORE, detailId(), state.detail().lore().keep().tableId(), state.index(), state.filter(), "", false);
                return;
            }
            String kind = state.page() == Page.BOOKS_LORE ? "LOOT_TYPE" : "BOOKS";
            beginPicker(new PendingPicker(Action.BOOK_KEEP, detailId(), kind)); return;
        }
        if (control.action() == Action.CHEST_BOOK_SET) { beginPicker(new PendingPicker(Action.CHEST_BOOK_SET, detailId(), "BOOKS")); return; }
        String input = control.input();
        if (input.isBlank()) { applyControl(control, detailId(), ""); return; }
        if (input.startsWith("TOOL:")) { applyControl(control, detailId(), ""); return; }
        if (input.equals("LOOT_DRAFT")) { saveLoot(); return; }
        if (input.equals("EVENT_DRAFT")) { saveEvent(); return; }
        if (input.equals("POSITION1")) { prompt(control.action(), detailId(), "1:"); return; }
        if (input.equals("POSITION2")) { prompt(control.action(), detailId(), "2:"); return; }
        if (input.equals("GAME_SETUP")) { selected="limits";pageSelections.put(Page.SETUP,"limits");open(Page.SETUP); return; }
        if (input.equals("BLOCK_LIMIT_TYPE") && control.action() == Action.BLOCK_LIMITS) {
            openLimitChooser(); return;
        }
        if (input.equals("LOOT_TYPE") || input.equals("DESTINATION") || input.equals("REGION") || input.equals("POINT") || input.equals("BLOCK") || input.equals("FLAG") || input.equals("ITEMS") || input.equals("REGION_PURPOSE") || input.equals("REGION_BOUND") || input.equals("POINT_PURPOSE") || input.equals("POINT_BOUND") || input.equals("BLOCK_LIMIT_TYPE") || input.equals("MANUAL_REFERENCE")) {
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
        if (!canEditLoot()) return;
        if(lootDraftStale){notice="Table changed while viewing the book. Local draft retained; reload loot configuration to discard it before editing current data.";clearAndInit();return;}
        try {
            List<LootEntry> entries = new ArrayList<>(lootDraft.entries());
            for (var edited : lootInputDrafts.entrySet()) {
                LootEntry old = entries.get(edited.getKey()); String[] raw = edited.getValue();
                entries.set(edited.getKey(), new LootEntry(old.item(), Double.parseDouble(raw[0]), Integer.parseInt(raw[1]), Integer.parseInt(raw[2]), old.componentsJson()));
            }
            List<BookChance> books=new ArrayList<>(lootDraft.books());
            for(var raw:loreChanceDrafts.entrySet()){var old=books.get(raw.getKey());books.set(raw.getKey(),new BookChance(old.bookId(),Double.parseDouble(raw.getValue())));}
            lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), Double.parseDouble(refillMinutesDraft), entries, books);
            lootValidation = "";
        } catch (IllegalArgumentException exception) { lootValidation = "Check chance (0–100), counts (min ≤ max) and refill minutes."; }
        if (!lootValidation.isBlank()) { notice = lootValidation; clearAndInit(); return; }
        try { Control save = state.detail().controls().stream().filter(c -> c.action() == Action.LOOT_SAVE).findFirst().orElseThrow(); applyControl(save, detailId(), LootTableDraftJson.encode(lootDraft)); }
        catch (IllegalArgumentException exception) { notice = exception.getMessage(); clearAndInit(); }
    }

    private void removeLoot() {
        if (!canEditLoot()) return;
        if (lootDraft == null || lootEntry < 0 || lootEntry >= lootDraft.entries().size()) return;
        int removed = lootEntry;
        List<LootEntry> entries = new ArrayList<>(lootDraft.entries()); entries.remove(removed); lootEntry = -1;
        Map<Integer, String[]> retained = new java.util.HashMap<>();
        lootInputDrafts.forEach((index, raw) -> { if (index != removed) retained.put(index > removed ? index - 1 : index, raw); });
        lootInputDrafts.clear(); lootInputDrafts.putAll(retained);
        lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), entries, lootDraft.books()); clearAndInit();
    }

    private TextFieldWidget numberField(int x, int y, int width, String hint, String value, java.util.function.Consumer<String> changed) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, width, 18, Text.literal(hint)));
        field.setMaxLength(8); field.setText(value); field.setChangedListener(changed); field.setTooltip(Tooltip.of(Text.literal(hint)));
        return field;
    }
    private void setLootField(int field, String value) {
        if (!canEditLoot()) return;
        LootEntry old = lootDraft.entries().get(lootEntry);
        String[] raw = lootInputDrafts.computeIfAbsent(lootEntry, ignored -> new String[] {Double.toString(old.chancePercent()), Integer.toString(old.minCount()), Integer.toString(old.maxCount())});
        raw[field] = value;
        try { replaceLoot(Double.parseDouble(raw[0]), Integer.parseInt(raw[1]), Integer.parseInt(raw[2])); }
        catch (NumberFormatException exception) { lootValidation = "Enter a valid chance, minimum and maximum count."; }
    }
    private void setRefillMinutes(String value) {
        if (!canEditLoot()) return;
        refillMinutesDraft = value;
        try {
            double refill = Double.parseDouble(value);
            lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), refill, lootDraft.entries(), lootDraft.books());
            lootValidation = "";
        } catch (IllegalArgumentException exception) { lootValidation = "Refill minutes must be between 0 and 10080."; }
    }
    private void replaceLoot(Double chance, Integer min, Integer max) {
        if (!canEditLoot()) return;
        if (lootDraft == null || lootEntry < 0 || lootEntry >= lootDraft.entries().size()) return;
        LootEntry old = lootDraft.entries().get(lootEntry);
        double updatedChance = chance == null ? old.chancePercent() : chance;
        int updatedMin = min == null ? old.minCount() : min;
        int updatedMax = max == null ? old.maxCount() : max;
        try {
            List<LootEntry> entries = new ArrayList<>(lootDraft.entries());
            entries.set(lootEntry, new LootEntry(old.item(), updatedChance, updatedMin, updatedMax, old.componentsJson()));
            lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), entries, lootDraft.books());
            lootValidation = "";
        } catch (IllegalArgumentException exception) { lootValidation = exception.getMessage(); }
    }

    private void addBookRef(String id, String title, String author) {
        if (!canEditLoot()) return;
        if (lootDraft == null || lootDraft.entries().size() + lootDraft.books().size() >= LootType.MAX_ENTRIES
                || lootDraft.books().stream().anyMatch(book -> book.bookId().equals(id))) return;
        bookSummaries.put(id, new io.github.chronicle.core.menu.CoreMenuModel.BookSummary(id, title, author, "", false, false));
        List<BookChance> books = new ArrayList<>(lootDraft.books()); books.add(new BookChance(id, 4));
        lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), lootDraft.entries(), books);
        lootBook = books.size() - 1; lootBookOffset = Math.max(0, lootBook - 2); lootValidation = "";
    }
    private void removeBookRef() {
        if (!canEditLoot()) return;
        if (lootDraft == null || lootBook < 0 || lootBook >= lootDraft.books().size()) return;
        int removed=lootBook;List<BookChance> books=new ArrayList<>(lootDraft.books());books.remove(removed);lootBook=-1;
        Map<Integer,String> retained=new java.util.HashMap<>();loreChanceDrafts.forEach((index,value)->{if(index!=removed)retained.put(index>removed?index-1:index,value);});loreChanceDrafts.clear();loreChanceDrafts.putAll(retained);
        lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), lootDraft.entries(), books); clearAndInit();
    }
    private void setBookChance(String value) {
        if (!canEditLoot()) return;
        if (lootBook < 0 || lootBook >= lootDraft.books().size()) return;
        loreChanceDrafts.put(lootBook, value);
        try {
            List<BookChance> books = new ArrayList<>(lootDraft.books()); BookChance old = books.get(lootBook);
            books.set(lootBook, new BookChance(old.bookId(), Double.parseDouble(value)));
            lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), lootDraft.entries(), books);
            lootValidation = "";
        } catch (IllegalArgumentException exception) { lootValidation = "Enter a lore-book chance from 0 to 100."; }
    }
    private void openSelectedBook() {
        if (lootBook < 0 || lootBook >= lootDraft.books().size()) return;
        var items=new java.util.HashMap<Integer,String[]>();lootInputDrafts.forEach((key,value)->items.put(key,value.clone()));
        lootHandoffs.put(lootDraft.id(),new LootHandoff(lootDraft,lootBase,lootEntry,lootBook,lootEntryOffset,lootBookOffset,lootBooksTab,refillMinutesDraft,lootValidation,items,Map.copyOf(loreChanceDrafts)));
        request(Action.BOOK_OPEN, Page.LOOT_TABLES, detailId(), lootDraft.books().get(lootBook).bookId(), state.index(), state.filter(), "", false);
    }
    private String bookLabel(String id) {
        var summary = bookSummaries.get(id);
        return summary == null ? id : summary.title() + (summary.author().isBlank() ? "" : " — " + summary.author());
    }

    private void beginPicker(PendingPicker action) {
        pickerAction = action; pickerSearch = "";
        request(Action.OPTIONS, state.page(), detailId(), action.kind(), 0, state.filter(), "", false);
    }

    private void openLimitChooser() {
        limitBackdrop = state;
        limitDialogStage = LimitDialogStage.LOADING;
        limitDraft = "";
        limitServerProblem = "";
        limitPolicyRevision = -1;
        limitChooserOffset = 0;
        pickerAction = new PendingPicker(Action.BLOCK_LIMITS, detailId(), "BLOCK_LIMIT_TYPE");
        request(Action.OPTIONS, Page.SETUP, detailId(), "BLOCK_LIMIT_TYPE", 0, state.filter(), "", false);
    }

    private void selectLimitType(BlockLimitType type) {
        if (limitDialogStage != LimitDialogStage.CHOOSE) return;
        limitDialogStage = LimitDialogStage.LOADING;
        request(Action.BLOCK_LIMITS, Page.SETUP, "limits", type.typeId(), state.index(), state.filter(), "", false);
    }

    private void limitPickerPage(int delta) {
        if (state.picker() == null) return;
        int page = Math.max(0, Math.min(state.picker().pages() - 1, state.picker().page() + delta));
        if (page == state.picker().page()) return;
        limitDialogStage = LimitDialogStage.LOADING;
        request(Action.OPTIONS, Page.SETUP, "limits", "BLOCK_LIMIT_TYPE", page, state.filter(), "", false);
    }

    private void updateLimitDialogAfterResponse(Action receivedFor, long priorRevision) {
        if (limitDialogStage == LimitDialogStage.NONE) return;
        BlockLimitsForm form = state.blockLimits();
        if (form == null) {
            limitDialogStage = LimitDialogStage.ERROR;
            limitServerProblem = blank(state.message(), "Block-limit data is unavailable.");
            return;
        }
        if (!state.success()) {
            limitServerProblem = blank(state.message(), "The server rejected this block-limit request.");
            if (receivedFor == Action.OPTIONS || receivedFor == Action.BLOCK_LIMITS) { limitDialogStage = LimitDialogStage.ERROR; return; }
        } else {
            limitServerProblem = "";
        }
        if (receivedFor == Action.OPTIONS) {
            limitDialogStage = LimitDialogStage.CHOOSE;
            limitPolicyRevision = form.policyRevision();
            limitChooserOffset = Math.clamp(limitChooserOffset, 0, Math.max(0, form.types().size() - 7));
            return;
        }
        BlockLimitType selectedType = form.selected();
        if (receivedFor == Action.BLOCK_LIMITS && selectedType != null) {
            limitDialogStage = LimitDialogStage.EDIT;
            limitPolicyRevision = form.policyRevision();
            limitDraft = Integer.toString(selectedType.limit());
            pickerAction = null;
            return;
        }
        if (receivedFor == Action.BLOCK_LIMIT_SET && selectedType != null && state.success()) {
            limitDialogStage = LimitDialogStage.EDIT;
            limitPolicyRevision = form.policyRevision();
            limitDraft = Integer.toString(selectedType.limit());
            return;
        }
        if (limitDialogStage == LimitDialogStage.EDIT && priorRevision >= 0 && form.policyRevision() != limitPolicyRevision) {
            limitDialogStage = LimitDialogStage.STALE;
        }
    }

    private BlockLimitsForm limitForm() {
        if (state.blockLimits() == null) throw new IllegalStateException("Block-limit dialog data is unavailable.");
        return state.blockLimits();
    }

    private void closeLimitDialog() {
        limitDialogStage = LimitDialogStage.NONE;
        limitDraft = "";
        limitPolicyRevision = -1;
        limitChooserOffset = 0;
        pickerAction = null;
        pickerSearch = "";
        limitServerProblem = "";
        if (pending) {
            limitCloseRequested = true;
            clearAndInit();
            return;
        }
        request(Action.REFRESH, Page.SETUP, "limits", "", state.index(), state.filter(), "", false);
    }

    private boolean limitCanSave(BlockLimitsForm form, BlockLimitType type) {
        if (limitDialogStage != LimitDialogStage.EDIT || !form.ready() || !form.editable() || type.fixed()) return false;
        try {
            int value = Integer.parseInt(limitDraft);
            return value >= type.count() && value <= 1024;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String limitProblem(BlockLimitsForm form, BlockLimitType type) {
        if (!limitServerProblem.isBlank()) return limitServerProblem;
        if (limitDialogStage == LimitDialogStage.STALE) return "This block-limit policy changed. Close and reopen it.";
        if (!form.ready()) return blank(form.problem(), "Block limits are unavailable.");
        if (!form.editable()) return blank(form.editReason(), "Editing is unavailable.");
        if (!type.fixed()) {
            try {
                int value = Integer.parseInt(limitDraft);
                if (value < type.count()) return "Limit cannot be below " + type.count() + " placed block" + (type.count() == 1 ? "." : "s.");
                if (value > 1024) return "Limit cannot exceed 1024.";
            } catch (NumberFormatException ignored) {
                return "Enter a whole-number limit.";
            }
        }
        return "";
    }

    private void saveLimit(BlockLimitsForm form, BlockLimitType type) {
        if (!limitCanSave(form, type)) return;
        request(Action.BLOCK_LIMIT_SET, Page.SETUP, "limit:" + type.typeId(), limitDraft, state.index(), state.filter(), "", false);
    }

    private void disableUnderlyingWidgets() {
        for (var child : children()) if (child instanceof net.minecraft.client.gui.widget.ClickableWidget widget) {
            widget.active = false;
            widget.setFocused(false);
        }
    }

    private void limitDialog() {
        if (limitDialogStage == LimitDialogStage.LOADING) {
            limitDialogPanel("Block limits", 224, 96);
            modalText("Loading block types...", limitDialogBounds.x() + 8, limitDialogBounds.y() + 26, limitDialogBounds.width() - 16, 0xFFFFFFFF);
            button("Close", limitDialogBounds.x() + limitDialogBounds.width() - 62, limitDialogBounds.y() + limitDialogBounds.height() - 26, 54, 18, this::closeLimitDialog, "Return to Game setup without changing limits", true);
            return;
        }
        if (limitDialogStage == LimitDialogStage.ERROR || state.blockLimits() == null) {
            limitDialogPanel("Block limits", 224, 96);
            modalWrappedText(blank(limitServerProblem, "Block-limit data is unavailable."), limitDialogBounds.x() + 8,
                    limitDialogBounds.y() + 26, limitDialogBounds.width() - 16, 0xFFFFCC66, 3);
            button("Close", limitDialogBounds.x() + limitDialogBounds.width() - 62, limitDialogBounds.y() + limitDialogBounds.height() - 26, 54, 18, this::closeLimitDialog, "Return to Game setup", true);
            return;
        }
        BlockLimitsForm form = limitForm();
        if (limitDialogStage == LimitDialogStage.CHOOSE) {
            limitDialogPanel("Choose block type", 248, 206);
            int rowY = limitDialogBounds.y() + 25;
            List<BlockLimitType> types = form.types();
            for (int row = 0; row < 7 && row + limitChooserOffset < types.size(); row++) {
                BlockLimitType type = types.get(row + limitChooserOffset);
                button(type.label() + "  " + type.count() + " / " + type.limit(), limitDialogBounds.x() + 8, rowY + row * 18,
                        limitDialogBounds.width() - 16, 16, () -> selectLimitType(type), type.description(), true);
            }
            if (types.size() > 7) modalText("Scroll for more types", limitDialogBounds.x() + 8, rowY + 129, limitDialogBounds.width() - 16, 0xFFA0A0A0);
            if (types.isEmpty()) modalText(blank(form.problem(), "No registered block types are available."), limitDialogBounds.x() + 8, rowY, limitDialogBounds.width() - 16, 0xFFFFCC66);
            if (state.picker() != null && state.picker().pages() > 1) {
                int pageY = limitDialogBounds.y() + 156;
                button("<", limitDialogBounds.x() + 8, pageY, 18, 16, () -> limitPickerPage(-1), "Previous block-type page", state.picker().page() > 0);
                button(">", limitDialogBounds.x() + 30, pageY, 18, 16, () -> limitPickerPage(1), "Next block-type page", state.picker().page() + 1 < state.picker().pages());
                modalText("Page " + (state.picker().page() + 1) + " / " + state.picker().pages(), limitDialogBounds.x() + 52, pageY + 4, 82, 0xFFA0A0A0);
            }
            button("Close", limitDialogBounds.x() + limitDialogBounds.width() - 62, limitDialogBounds.y() + limitDialogBounds.height() - 26, 54, 18, this::closeLimitDialog, "Return to Game setup without changing limits", true);
            return;
        }
        BlockLimitType type = form.selected();
        if (type == null) {
            limitDialogPanel("Block limits", 224, 96);
            modalText(blank(form.problem(), "The selected block type is unavailable."), limitDialogBounds.x() + 8, limitDialogBounds.y() + 26, limitDialogBounds.width() - 16, 0xFFFFCC66);
            button("Close", limitDialogBounds.x() + limitDialogBounds.width() - 62, limitDialogBounds.y() + limitDialogBounds.height() - 26, 54, 18, this::closeLimitDialog, "Return to Game setup", true);
            return;
        }
        limitDialogPanel(type.label(), 224, 158);
        int x = limitDialogBounds.x() + 8;
        int y = limitDialogBounds.y() + 29;
        modalText(Integer.toString(type.count()) + " /", x, y + 5, 34, 0xFFFFFFFF);
        if (type.fixed()) {
            modalText(Integer.toString(type.limit()) + "  Fixed", x + 38, y + 5, limitDialogBounds.width() - 46, 0xFFA0A0A0);
        } else {
            limitValueInput = addDrawableChild(new TextFieldWidget(textRenderer, x + 38, y, limitDialogBounds.width() - 54, 18, Text.literal("Maximum")));
            limitValueInput.setMaxLength(4);
            limitValueInput.setTextPredicate(value -> value.matches("[0-9]{0,4}"));
            limitValueInput.setText(limitDraft);
            limitValueInput.setChangedListener(value -> {
                limitDraft = value;
                if (limitSaveButton != null) limitSaveButton.active = limitCanSave(form, type) && !pending;
            });
            limitValueInput.active = form.ready() && form.editable() && limitDialogStage == LimitDialogStage.EDIT;
        }
        if (!type.fixed()) {
            dynamicModalWrappedText(() -> limitProblem(form, type), x, y + 25, limitDialogBounds.width() - 16, 0xFFFFCC66, 3);
            button("Save", x, limitDialogBounds.y() + limitDialogBounds.height() - 26, 54, 18,
                    () -> saveLimit(form, type), "Save this maximum limit", limitCanSave(form, type));
            limitSaveButton = buttons.get("Save");
        }
        button("Close", limitDialogBounds.x() + limitDialogBounds.width() - 62, limitDialogBounds.y() + limitDialogBounds.height() - 26, 54, 18,
                this::closeLimitDialog, "Return to Game setup without changing limits", true);
    }

    private void limitDialogPanel(String title, int requestedWidth, int requestedHeight) {
        int dialogWidth = Math.min(requestedWidth, Math.max(1, width - 32));
        int dialogHeight = Math.min(requestedHeight, Math.max(1, height - 32));
        int x = layout.frame().x() + (layout.frame().width() - dialogWidth) / 2;
        int y = layout.frame().y() + (layout.frame().height() - dialogHeight) / 2;
        limitDialogBounds = new BoundsProbe(x, y, dialogWidth, dialogHeight);
        addDrawable((context, mouseX, mouseY, delta) -> {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x, y, dialogWidth, dialogHeight);
            draw(context, title, x + 8, y + 8, dialogWidth - 16, 0xFFFFFFFF);
        });
    }

    private void modalText(String value, int x, int y, int available, int color) {
        addDrawable((context, mouseX, mouseY, delta) -> draw(context, value, x, y, available, color));
    }

    private void modalWrappedText(String value, int x, int y, int available, int color, int maxLines) {
        addDrawable((context, mouseX, mouseY, delta) -> drawModalWrapped(context, value, x, y, available, color, maxLines));
    }

    private void dynamicModalWrappedText(java.util.function.Supplier<String> value, int x, int y, int available, int color, int maxLines) {
        addDrawable((context, mouseX, mouseY, delta) -> drawModalWrapped(context, value.get(), x, y, available, color, maxLines));
    }

    private void drawModalWrapped(DrawContext context, String value, int x, int y, int available, int color, int maxLines) {
        int line = 0;
        for (var ordered : textRenderer.wrapLines(Text.literal(value), Math.max(1, available))) {
            if (line >= maxLines) break;
            StringBuilder plain = new StringBuilder();
            ordered.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
            draw(context, plain.toString(), x, y + line * 10, available, color);
            line++;
        }
    }

    private void chooseEntry(Entry entry) {
        if (state.picker() != null && pickerAction != null) {
            if (pickerAction.kind().equals("ITEMS")) addLoot(entry.id());
            else if (pickerAction.action() == Action.BOOK_REF_ADD && pickerAction.kind().equals("BOOKS")) addBookRef(entry.id(), entry.label(), entry.description());
            else if (pickerAction.action() == Action.CHEST_BOOK_SET && pickerAction.kind().equals("BOOKS")) {
                pickerAction = null;
                String name = entry.label().isBlank() ? bookLabel(entry.id()) : entry.label() + (entry.description().isBlank() ? "" : " — " + entry.description());
                client.setScreen(new BookChanceScreen(this, new BookChance(entry.id(), 4), name, debug, this::saveDirectBook, this::clearDirectBook, this::cancelDirectBookPicker));
                return;
            }
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
        if (!canEditLoot()) return;
        if (lootDraft == null || lootDraft.entries().size() + lootDraft.books().size() >= LootType.MAX_ENTRIES) return;
        List<LootEntry> entries = new ArrayList<>(lootDraft.entries()); entries.add(new LootEntry(item, 100, 1, 1, "{}"));
        lootDraft = new LootType(lootDraft.id(), lootDraft.label(), lootDraft.description(), lootDraft.recommendedChests(), lootDraft.refillMinutes(), entries, lootDraft.books());
        lootEntry = entries.size() - 1; lootEntryOffset = Math.max(0, lootEntry - 2); lootValidation = "";
    }

    private void select(String id) {
        selected = id; detailOnCompact = true; actionOffset = factOffset = detailScroll = 0;
        pageSelections.put(state.page(), id);
        if (state.page() == Page.LOCATIONS) locationSelections.put(state.filter(), id);
        request(Action.OPEN, state.page(), id, "", state.index(), state.filter(), "", false);
    }
    private void open(Page page) { if (page == Page.LOCATIONS) { locations(locationFilter.isBlank() ? "POINT" : locationFilter); return; } detailOnCompact = false; selected = pageSelections.getOrDefault(page, ""); listOffset = detailScroll = 0; request(Action.OPEN, page, selected, "", 0, "", "", false); }
    private void locations(String filter) { locationFilter = filter; selected = locationSelections.getOrDefault(filter, ""); detailOnCompact = false; listOffset = detailScroll = 0; request(Action.OPEN, Page.LOCATIONS, selected, "", 0, filter, "", false); }
    private void page(int delta) { listOffset=0;request(Action.OPEN, state.page(), selected, "", Math.max(0, Math.min(state.pages() - 1, state.index() + delta)), state.filter(), "", false); }
    private void pickerPage(int delta) { if (state.picker() != null) {listOffset=0;request(Action.OPTIONS, state.page(), detailId(), state.picker().kind(), Math.max(0, Math.min(state.picker().pages() - 1, state.picker().page() + delta)), state.filter(), pickerSearch, false);} }
    private void prompt(Action action, String id, String prefix) {
        promptDraft = ""; pickerAction = new PendingPicker(action, id, prefix); clearAndInit();
    }

    private void applyControl(Control control, String id, String argument) {
        if (control.input().startsWith("TOOL:")) {
            String verb = control.input().substring("TOOL:".length());
            if (verb.isBlank()) { notice = "Game setup tool action is unavailable."; return; }
            Page page = state.page(); int index = state.index(); String filter = state.filter();
            Runnable send = () -> request(Action.TOOL_ACTION, page, id, verb, index, filter, "", false);
            if (!control.confirm()) { send.run(); return; }
            confirmation = new Confirmation(control.label(), "Target: " + blank(state.detail().title(), id) + "\nApply this game setup tool action.", state.token(), send);
            clearAndInit();
            return;
        }
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
            case REGION_ASSIGN -> "Use this saved region for " + argument + ". This pins its current revision; bounds are not copied.";
            case REGION_UNASSIGN -> "Clear this use from the selected saved region. The region stays available.";
            case POINT_ASSIGN -> "Use this saved point for " + argument + ". This pins its current revision; position and facing are not copied.";
            case POINT_UNASSIGN -> "Clear this use from the selected saved point. The point stays available.";
            case BLOCK_LIMIT_SET -> "Set this managed block limit to " + argument + ". Existing blocks stay in place; a lower limit can make future placement unavailable.";
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
        request(action, page, id, argument, state.token(), index, filter, query, background);
    }
    private void request(Action action, Page page, String id, String argument, long token, int index, String filter, String query, boolean background) {
        if (pending || !ClientPlayNetworking.canSend(CoreMenuPayloads.Request.ID)) { notice = "Chronicle menu is unavailable; wait for the server response."; return; }
        ClientPlayNetworking.send(new CoreMenuPayloads.Request(action, page, id, argument, token, index, filter, query));
        pending = true; refreshPending = background; requestedAction = action; pendingTicks = idleTicks = 0;
        if (!background) clearAndInit();
    }

    /** Opens a local book form; only its explicit save callback crosses the menu protocol. */
    private void openBookEditor(BookDefinition book, BookEditorScreen.Mode mode, boolean creating) {
        long editorToken = state.token();
        BookEditorScreen[] editor = new BookEditorScreen[1];
        editor[0] = new BookEditorScreen(this, book, mode, creating, value -> {
            failedBookEditor = editor[0];
            failedBookToken = editorToken;
            client.setScreen(this);
            request(creating ? Action.BOOK_CREATE : Action.BOOK_SAVE, Page.BOOKS_LORE, value.id(), new Gson().toJson(value), editorToken,
                    state.index(), state.filter(), "", false);
        });
        client.setScreen(editor[0]);
    }

    private void sendKeep(BookKeepScreen.Request keep) {
        com.google.gson.JsonObject value = new com.google.gson.JsonObject();
        value.addProperty("bookId", keep.bookId()); value.addProperty("tableId", keep.tableId()); value.addProperty("override", keep.override());
        client.setScreen(this);
        request(keep.action(), state.page(), detailId(), value.toString(), state.index(), state.filter(), "", false);
    }

    private void saveDirectBook(BookChance direct) {
        client.setScreen(this);
        request(Action.CHEST_BOOK_SET, Page.CHESTS, detailId(), new Gson().toJson(direct), state.index(), state.filter(), "", false);
    }
    private void clearDirectBook() {
        client.setScreen(this);
        request(Action.CHEST_BOOK_SET, Page.CHESTS, detailId(), "clear", state.index(), state.filter(), "", false);
    }
    private void cancelDirectBookPicker() {
        client.setScreen(this);
        cancelPicker();
    }

    @Override public void tick() {
        if (pending) { if (++pendingTicks >= RESPONSE_TIMEOUT) { pending = false; refreshPending = false; notice = "No server response after 10 seconds. Try Refresh."; if (limitDialogStage != LimitDialogStage.NONE) { limitDialogStage = LimitDialogStage.ERROR; limitServerProblem = "No server response. Close and reopen Block limits."; } clearAndInit(); } return; }
        if (confirmation != null || pickerAction != null || limitDialogStage == LimitDialogStage.LOADING || limitDialogStage == LimitDialogStage.CHOOSE) return;
        if (++idleTicks >= REFRESH_TICKS) request(Action.REFRESH, state.page(), detailId(), "", state.index(), state.filter(), "", true);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (limitDialogStage != LimitDialogStage.NONE) {
            if (limitDialogStage == LimitDialogStage.CHOOSE && limitDialogBounds != null
                    && mouseX >= limitDialogBounds.x() && mouseX < limitDialogBounds.x() + limitDialogBounds.width()
                    && mouseY >= limitDialogBounds.y() && mouseY < limitDialogBounds.y() + limitDialogBounds.height()) {
                int entries = limitForm().types().size();
                limitChooserOffset = Math.clamp(limitChooserOffset + (verticalAmount < 0 ? 1 : -1), 0, Math.max(0, entries - 7));
                clearAndInit();
            }
            return true;
        }
        if (mouseX >= detailX && mouseX < detailX + detailWidth && mouseY >= detailY && mouseY < detailY + detailHeight && state.detail() != null && state.picker() == null) {
            detailScroll = Math.max(0, Math.min(Math.max(0, detailContentHeight - detailHeight + 12), detailScroll + (verticalAmount < 0 ? 20 : -20)));
            clearAndInit();
            return true;
        }
        CoreMenuLayout.Layout current = activeLayout();
        if (mouseY >= current.bodyTop() && mouseY < current.bodyBottom()) { listOffset = Math.max(0, listOffset + (verticalAmount < 0 ? 1 : -1)); clearAndInit(); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (limitDialogStage != LimitDialogStage.NONE && input.key() == 256) {
            closeLimitDialog();
            return true;
        }
        return super.keyPressed(input);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        CoreMenuLayout.Layout current = activeLayout();
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, current.frame().x(), current.frame().y(), current.frame().width(), current.frame().height());
        context.drawTextWithShadow(textRenderer, Text.literal("Chronicle"), current.content().x(), current.headerY() + 5, 0xFFFFFFFF);
        String message = notice.isBlank() ? (pending ? "Waiting for server..." : "Choose an item, then use the actions on the right.") : notice;
        if (CoreMenuPresentation.showFooterMessage(notice, pending, debug))
            draw(context, message, current.mainLeft(), current.feedbackY(), Math.max(1, current.content().right() - 60 - current.mainLeft()), state.success() ? 0xFF77FF77 : 0xFFFFCC66);
        if (debug && !state.clock().isBlank())
            draw(context, state.clock(), current.mainLeft(), current.footerY() + 5,
                    Math.max(1, current.content().right() - 60 - current.mainLeft()), 0xFFA0A0A0);
        for (RenderLine line : renderLines) if (line.top() == 0 || (line.y() >= line.top() && line.y() + 9 <= line.bottom()))
            draw(context, line.value(), line.x(), line.y(), line.width(), line.color());
        super.render(context, mouseX, mouseY, deltaTicks);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { CoreMenuClient.closed(state.token()); super.close(); }

    private void setDebug(boolean value) {
        if (debug == value) return;
        debug = value;
        EditorMenuPreferences.setDebug(value);
        clearAndInit();
    }

    private void loadForms(Detail detail) {
        if (detail == null) return;
        if (detail.event() != null) { eventMode = detail.event().mode(); eventRegion = detail.event().region(); eventFlag = detail.event().flag(); eventSecondsDraft = Long.toString(detail.event().seconds()); }
        if (detail.region() != null) { regionFirst = detail.region().first(); regionSecond = detail.region().second(); }
        if (detail.lore() != null) for (var book : detail.lore().refs()) bookSummaries.put(book.id(), book);
        if (detail.loot() != null) { lootBase = lootDraft = detail.loot(); lootDraftStale=false; lootEntry = lootBook = -1; lootEntryOffset = lootBookOffset = 0; refillMinutesDraft = Double.toString(detail.loot().refillMinutes()); lootValidation = ""; lootInputDrafts.clear(); loreChanceDrafts.clear(); }
    }
    private void restoreLootHandoff(LootType current) {
        var saved=lootHandoffs.remove(current.id());if(saved==null)return;
        lootDraft=saved.draft();lootBase=saved.base();lootDraftStale=!current.equals(saved.base());
        lootEntry=saved.entry();lootBook=saved.book();lootEntryOffset=saved.entryOffset();lootBookOffset=saved.bookOffset();lootBooksTab=saved.booksTab();
        refillMinutesDraft=saved.refill();lootValidation=saved.validation();lootInputDrafts.clear();saved.items().forEach((key,value)->lootInputDrafts.put(key,value.clone()));
        loreChanceDrafts.clear();loreChanceDrafts.putAll(saved.books());
        notice=lootDraftStale?"Table changed while viewing the book. Local draft retained; reload loot configuration to discard it.":"Unsaved loot-table draft restored.";
    }
    private CoreMenuLayout.Layout activeLayout() {
        return layout == null ? CoreMenuLayout.forViewport(width, height) : layout;
    }
    private BoundsProbe boundsFor(String label) {
        ButtonWidget button = buttons.get(label);
        return button == null ? null : new BoundsProbe(button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }
    private String detailId() { return state.detail() == null ? selected : state.detail().id(); }
    private boolean canEditLoot() { return state.editing() && state.editable(); }
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
    private static String bookRuleLabel(BookDefinition.Rule rule) { return switch (rule) { case OFF -> "Distribution off"; case ONCE -> "Spawn once"; case UNTIL_FOUND -> "Until found"; case REPEAT -> "Repeat"; }; }
    private static String navigationLabel(Page page) {
        for (int index = 0; index < NAVIGATION.length; index++) if (NAVIGATION[index] == page) return NAVIGATION_LABELS[index];
        return page.name();
    }
    private static String next(String value, String... values) { for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return values[(i + 1) % values.length]; return values[0]; }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private record Confirmation(String title, String body, long token, Runnable execute) { }
    private record PendingPicker(Action action, String id, String kind) { }
    private enum LimitDialogStage { NONE, LOADING, CHOOSE, EDIT, STALE, ERROR }
    private String debugLabel() { return "Debug: " + (debug ? "on" : "off"); }
    private record RenderLine(String value, int x, int y, int width, int color, int top, int bottom) { }
    public record ButtonProbe(int identity, boolean active, boolean hovered, boolean focused) { }
    /** Rectangle reported by the native smoke harness in logical GUI coordinates. */
    public record BoundsProbe(int x, int y, int width, int height) { }
    /** A visible button or text field reported by the native smoke harness. */
    public record WidgetBounds(String label, int x, int y, int width, int height) { }
    /** Header control positions reported by the native smoke harness. */
    public record HeaderBoundsForTest(BoundsProbe refresh, BoundsProbe debug, BoundsProbe editing) { }
}
