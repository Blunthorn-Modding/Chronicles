package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.EventEditorForm;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A local four-page Event draft.  This class intentionally has no event-domain authority:
 * catalog pins and all state changes are checked again by the server when {@link Transport#save}
 * or {@link Transport#preview} is called.
 */
public final class EventEditorScreen extends Screen {
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");

    public interface Transport {
        void requestCatalog(CatalogAddPopup popup, String kind, String query, int page);
        void save(EventEditorForm form);
        void preview(EventEditorForm form);
        void showBounds(EventEditorForm.Change change);
    }

    private enum Page { BASICS, MEDIA, TRIGGER, CHANGES }
    private enum PickerPurpose { SOUND, AUDIENCE_REGION, TRIGGER_REGION, FLAG, STATE }

    private final CoreMenuScreen parent;
    private final Transport transport;
    private final long openingToken;
    private final boolean creating;
    private final long revision;
    private Page page = Page.BASICS;
    private PickerPurpose pickerPurpose;
    private CatalogAddPopup popup;
    private String pickerKind = "";
    private String idDraft, nameDraft, descriptionDraft, messageDraft, soundDraft;
    private String audienceDraft, audienceRegionDraft, modeDraft, triggerRegionDraft, secondsDraft, flagDraft;
    private long audienceRegionRevision, triggerRegionRevision;
    private final List<EventEditorForm.Change> changes = new ArrayList<>();
    private boolean manuallyEditedId;
    private boolean updatingGeneratedId;
    private boolean stale;
    private String problem = "";
    private int changeOffset;
    private TextFieldWidget idField, nameField, descriptionField, messageField, secondsField;

    public EventEditorScreen(CoreMenuScreen parent, EventEditorForm initial, Transport transport) {
        super(Text.literal(initial.creating() ? "Create event" : "Edit event"));
        this.parent = java.util.Objects.requireNonNull(parent, "parent");
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        EventEditorForm value = java.util.Objects.requireNonNull(initial, "initial");
        openingToken = parent.state().token();
        creating = value.creating(); revision = value.revision();
        idDraft = value.id(); nameDraft = value.name(); descriptionDraft = value.description();
        messageDraft = value.message(); soundDraft = value.sound(); audienceDraft = value.audience();
        audienceRegionDraft = value.audienceRegion(); audienceRegionRevision = value.audienceRegionRevision();
        modeDraft = value.mode(); triggerRegionDraft = value.triggerRegion(); triggerRegionRevision = value.triggerRegionRevision();
        secondsDraft = Long.toString(value.seconds()); flagDraft = value.flag(); changes.addAll(value.changes());
    }

    public CoreMenuScreen parentMenu() { return parent; }
    public long openingToken() { return openingToken; }
    /** Focused native-smoke probes; they do not bypass the normal widget callbacks. */
    public String pageForTest() { return page.name(); }
    public EventEditorForm valuesForTest() { retainInputs(); return form(); }
    public void setDraftForTest(String field, String value) {
        switch (field) {
            case "id" -> { idDraft = value; manuallyEditedId = true; if (idField != null) idField.setText(value); }
            case "name" -> { nameDraft = value; if (nameField != null) nameField.setText(value); }
            case "description" -> { descriptionDraft = value; if (descriptionField != null) descriptionField.setText(value); }
            case "message" -> { messageDraft = value; if (messageField != null) messageField.setText(value); }
            case "seconds" -> { secondsDraft = value; if (secondsField != null) secondsField.setText(value); }
            default -> throw new IllegalArgumentException("Unknown event draft field: " + field);
        }
    }
    public boolean pressForTest(String label) {
        return children().stream().filter(child -> child instanceof ButtonWidget)
                .map(child -> (ButtonWidget) child).filter(button -> button.active && button.visible && button.getMessage().getString().equals(label))
                .findFirst().map(button -> { button.onPress(new net.minecraft.client.input.MouseInput(0, 0)); return true; }).orElse(false);
    }
    public List<CoreMenuScreen.WidgetBounds> visibleWidgetBoundsForTest() {
        return children().stream().filter(child -> child instanceof net.minecraft.client.gui.widget.ClickableWidget widget && widget.visible)
                .map(child -> (net.minecraft.client.gui.widget.ClickableWidget) child)
                .map(widget -> new CoreMenuScreen.WidgetBounds(widget.getMessage().getString(), widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())).toList();
    }

    /** Called by the menu route even while a catalog popup is on top. */
    public void serverUpdated(CoreMenuModel.View view) {
        if (view.token() != openingToken || !view.operator() || !view.editable() || !view.editing() || !view.hold().isBlank()) {
            stale = true;
            if (popup != null) popup.markStale();
            if (client.currentScreen == this) clearAndInit();
            return;
        }
        if (popup != null && view.picker() != null && view.picker().kind().equals(pickerKind)) popup.replacePage(view.picker());
    }

    /** Preserve the authored draft for inspection but refuse further mutations after a stale reply. */
    public void rejected(String message, boolean stale) {
        problem = message == null ? "The server rejected this event draft." : message;
        this.stale |= stale;
        if (this.stale && popup != null) popup.markStale();
        if (client.currentScreen == this) clearAndInit();
    }

    @Override protected void init() {
        retainInputs(); clearChildren();
        int w = Math.min(440, Math.max(240, width - 24));
        int x = (width - w) / 2; int y = Math.max(8, (height - 240) / 2);
        int tab = Math.max(50, (w - 6) / 4);
        for (int i = 0; i < Page.values().length; i++) {
            Page candidate = Page.values()[i];
            button((page == candidate ? "> " : "") + pageLabel(candidate), x + i * tab, y + 22,
                    i == 3 ? w - 3 * tab : tab - 2, () -> { page = candidate; problem = ""; clearAndInit(); },
                    pageHelp(candidate), usable());
        }
        switch (page) {
            case BASICS -> basics(x, y, w);
            case MEDIA -> media(x, y, w);
            case TRIGGER -> trigger(x, y, w);
            case CHANGES -> changes(x, y, w);
        }
        button("Cancel", x, y + 206, 92, this::returnToParent, "Discard this local draft", true);
        button(creating ? "Create event" : "Save edits", x + 96, y + 206, w - 96, this::save,
                creating ? "Create this configured event" : "Save all event changes", usable() && !hasUnresolvedChanges());
    }

    private void basics(int x, int y, int w) {
        int row = y + 55;
        if (creating) {
            idField = field(x, row, w, "Event ID (generated, editable)", idDraft, 128, value -> { idDraft = value; if (!updatingGeneratedId) manuallyEditedId = true; });
            row += 34;
        }
        nameField = field(x, row, w, "Event name", nameDraft, 80, value -> {
            nameDraft = value;
            if (creating && !manuallyEditedId) {
                idDraft = generatedId(value);
                if (idField != null) { updatingGeneratedId = true; idField.setText(idDraft); updatingGeneratedId = false; }
            }
        });
        descriptionField = field(x, row + 34, w, "Organizer description (optional)", descriptionDraft, 512, value -> descriptionDraft = value);
    }

    private void media(int x, int y, int w) {
        messageField = field(x, y + 55, w, "Message (optional)", messageDraft, 512, value -> messageDraft = value);
        button(soundDraft.isBlank() ? "Choose sound…" : "Sound: " + compact(soundDraft, 38), x, y + 89, w, () -> openPicker(PickerPurpose.SOUND),
                "Choose an optional registered sound", usable());
        button("Clear sound", x, y + 111, w, () -> { soundDraft = ""; problem = ""; clearAndInit(); }, "Use no sound", usable() && !soundDraft.isBlank());
        button("Preview privately", x, y + 145, w, this::preview, "Play this unsaved message and sound only for you", usable());
    }

    private void trigger(int x, int y, int w) {
        int half = (w - 4) / 2;
        button(choice("Audience", audienceDraft, "PARTICIPANTS"), x, y + 55, half, () -> { audienceDraft = "PARTICIPANTS"; audienceRegionDraft = ""; audienceRegionRevision = 0; clearAndInit(); }, "Send to active participants", usable());
        button(choice("Audience", audienceDraft, "REGION"), x + half + 4, y + 55, w - half - 4, () -> { audienceDraft = "REGION"; clearAndInit(); }, "Send to a selected Region", usable());
        if ("REGION".equals(audienceDraft)) button(regionButton("Audience Region", audienceRegionDraft), x, y + 77, w, () -> openPicker(PickerPurpose.AUDIENCE_REGION), "Choose who receives this event", usable());

        int row = y + ("REGION".equals(audienceDraft) ? 111 : 89);
        int quarter = Math.max(48, (w - 9) / 4);
        for (int i = 0; i < 4; i++) {
            String mode = new String[] {"MANUAL", "ENTRY", "TIMER", "CONDITION"}[i];
            button(choice("", modeDraft, mode), x + i * (quarter + 3), row, i == 3 ? w - i * (quarter + 3) : quarter,
                    () -> { modeDraft = mode; problem = ""; clearAndInit(); }, triggerHelp(mode), usable());
        }
        row += 22;
        if ("ENTRY".equals(modeDraft)) { button(regionButton("Entry Region", triggerRegionDraft), x, row, w, () -> openPicker(PickerPurpose.TRIGGER_REGION), "Start when a participant enters this Region", usable()); row += 22; }
        if ("TIMER".equals(modeDraft)) { secondsField = field(x, row, w, "Active seconds", secondsDraft, 9, value -> secondsDraft = value); row += 22; }
        String flagLabel = flagDraft.isBlank() ? ("CONDITION".equals(modeDraft) ? "Choose required flag…" : "Optional flag…") : "Flag: " + compact(flagDraft, 36);
        button(flagLabel, x, row, w - 44, () -> openPicker(PickerPurpose.FLAG),
                "Choose an optional prerequisite; Condition requires one", usable());
        button("Clear", x + w - 40, row, 40, () -> { flagDraft = ""; clearAndInit(); }, "Remove this flag prerequisite", usable() && !flagDraft.isBlank());
    }

    private void changes(int x, int y, int w) {
        button("Add Saved state…", x, y + 55, w - 46, () -> openPicker(PickerPurpose.STATE), "Add an existing Region Saved state", usable() && changes.size() < EventEditorForm.MAX_CHANGES);
        int rows = Math.min(4, Math.max(1, changes.size() - changeOffset));
        changeOffset = Math.clamp(changeOffset, 0, Math.max(0, changes.size() - rows));
        for (int i = 0; i < rows && changeOffset + i < changes.size(); i++) {
            int index = changeOffset + i; EventEditorForm.Change change = changes.get(index); int row = y + 78 + i * 25;
            int showWidth = 75;
            button(compact(change.label().isBlank() ? change.regionId() + " - " + change.state() : change.label(), 32), x, row, w - showWidth - 4,
                    () -> transport.showBounds(change), change.current() ? "Show this target's bounds" : "This saved state is unavailable", usable() && change.current());
            button("Remove", x + w - showWidth, row, showWidth, () -> { changes.remove(index); problem = ""; clearAndInit(); }, "Remove this target only", usable());
        }
        if (changes.size() > rows) {
            button("↑", x + w - 44, y + 55, 20, () -> { changeOffset--; clearAndInit(); }, "Earlier changes", usable() && changeOffset > 0);
            button("↓", x + w - 20, y + 55, 20, () -> { changeOffset++; clearAndInit(); }, "Later changes", usable() && changeOffset + rows < changes.size());
        }
    }

    private void openPicker(PickerPurpose purpose) {
        if (!usable()) return;
        retainInputs(); pickerPurpose = purpose; pickerKind = switch (purpose) {
            case SOUND -> "EVENT_SOUND"; case AUDIENCE_REGION, TRIGGER_REGION -> "EVENT_REGION";
            case FLAG -> "EVENT_FLAG"; case STATE -> "EVENT_STATE";
        };
        popup = new CatalogAddPopup(this, Text.literal(pickerTitle(purpose)), false, new CatalogAddPopup.Owner() {
            @Override public void requestPage(CatalogAddPopup source, String query, int page) { transport.requestCatalog(source, pickerKind, query, page); }
            @Override public String validate(Entry entry, String ignored) { return validatePicker(entry); }
            @Override public void add(Entry entry, String ignored) { applyPicker(entry); }
            @Override public void cancel() { popup = null; }
            @Override public void serverUpdated(CatalogAddPopup source, CoreMenuModel.View view) { EventEditorScreen.this.serverUpdated(view); }
        });
        client.setScreen(popup);
    }

    private String validatePicker(Entry entry) {
        if (!"EVENT_STATE".equals(pickerKind)) return "";
        String[] pin = splitState(entry.id());
        if (pin == null) return "This saved state is no longer available.";
        boolean duplicate = changes.stream().anyMatch(change -> change.regionId().equals(pin[0]));
        return duplicate ? "That Region already has an event Saved state target." : "";
    }

    private void applyPicker(Entry entry) {
        if (stale) return;
        switch (pickerPurpose) {
            case SOUND -> soundDraft = entry.id();
            case AUDIENCE_REGION -> { audienceDraft = "REGION"; audienceRegionDraft = entry.id(); audienceRegionRevision = entry.revision(); }
            case TRIGGER_REGION -> { triggerRegionDraft = entry.id(); triggerRegionRevision = entry.revision(); }
            case FLAG -> flagDraft = "none".equals(entry.id()) ? "" : entry.id();
            case STATE -> {
                String[] pin = splitState(entry.id());
                if (pin != null) changes.add(new EventEditorForm.Change(pin[0], entry.label(), pin[1], entry.revision(), entry.reference(), "", true, ""));
            }
        }
        popup = null; problem = ""; clearAndInit();
    }

    private void save() {
        retainInputs(); String error = validation();
        if (!error.isBlank()) { problem = error; clearAndInit(); return; }
        transport.save(form());
    }
    private void preview() {
        retainInputs(); String error = mediaValidation();
        if (!error.isBlank()) { problem = error; clearAndInit(); return; }
        transport.preview(form());
    }
    private EventEditorForm form() {
        // A media preview may be requested while a different page has an unfinished timer.
        // Keep the text draft untouched; the full Save validator reports it before persistence.
        long seconds = Math.max(0, Math.min(EventEditorForm.MAX_SECONDS, parseSeconds()));
        return new EventEditorForm(idDraft.trim(), revision, nameDraft.trim(), descriptionDraft, messageDraft, soundDraft,
                audienceDraft, audienceRegionDraft, audienceRegionRevision, modeDraft, triggerRegionDraft, triggerRegionRevision,
                seconds, flagDraft, List.copyOf(changes), creating);
    }
    private String validation() {
        if (stale) return "Chronicle changed. This draft is preserved; reopen the event before saving.";
        if (idDraft.trim().isBlank()) return "An event ID is required.";
        if (nameDraft.trim().isBlank()) return "An event name is required.";
        if (!"PARTICIPANTS".equals(audienceDraft) && !"REGION".equals(audienceDraft)) return "Choose an audience.";
        if ("REGION".equals(audienceDraft) && audienceRegionDraft.isBlank()) return "Choose an audience Region.";
        if (!Set.of("MANUAL", "ENTRY", "TIMER", "CONDITION").contains(modeDraft)) return "Choose a trigger.";
        if ("ENTRY".equals(modeDraft) && triggerRegionDraft.isBlank()) return "Choose an entry Region.";
        if ("TIMER".equals(modeDraft) && (parseSeconds() <= 0 || parseSeconds() > EventEditorForm.MAX_SECONDS)) return "Active seconds must be between 1 and " + EventEditorForm.MAX_SECONDS + ".";
        if ("CONDITION".equals(modeDraft) && flagDraft.isBlank()) return "Choose a registered flag.";
        if (messageDraft.isBlank() && soundDraft.isBlank() && changes.isEmpty()) return "Add a message, sound, or Region Saved state.";
        Set<String> pins = new HashSet<>();
        for (EventEditorForm.Change change : changes) {
            if (change.regionId().isBlank() || change.state().isBlank() || !change.current() || !change.problem().isBlank()) return "Repair unavailable Region Saved states before saving.";
            if (!pins.add(change.regionId())) return "A Region may have only one event Saved state target.";
        }
        return "";
    }
    private boolean hasUnresolvedChanges() {
        return changes.stream().anyMatch(change -> !change.current() || !change.problem().isBlank());
    }
    private String mediaValidation() {
        if (stale) return "Chronicle changed. This draft is preserved; reopen the event before previewing.";
        return messageDraft.isBlank() && soundDraft.isBlank() ? "Add a message or sound to preview." : "";
    }
    private long parseSeconds() { try { return Long.parseLong(secondsDraft.trim()); } catch (NumberFormatException ignored) { return -1; } }
    private void retainInputs() {
        if (idField != null) idDraft = idField.getText(); if (nameField != null) nameDraft = nameField.getText();
        if (descriptionField != null) descriptionDraft = descriptionField.getText(); if (messageField != null) messageDraft = messageField.getText();
        if (secondsField != null) secondsDraft = secondsField.getText();
    }
    private TextFieldWidget field(int x, int y, int width, String hint, String value, int max, java.util.function.Consumer<String> changed) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, width, 18, Text.literal(hint)));
        field.setMaxLength(max); field.setText(value); field.setChangedListener(changed); field.active = usable(); return field;
    }
    private void button(String label, int x, int y, int width, Runnable action, String tooltip, boolean enabled) {
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, Math.max(1, width - 8))), ignored -> action.run()).dimensions(x, y, Math.max(1, width), 18).build());
        button.active = enabled; button.setTooltip(Tooltip.of(Text.literal(tooltip)));
    }
    private boolean usable() { return !stale; }
    private void returnToParent() { client.setScreen(parent); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(440, Math.max(240, width - 24)); int x = (width - w) / 2; int y = Math.max(8, (height - 240) / 2);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, 240);
        context.drawTextWithShadow(textRenderer, title, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(pageDescription()), x, y + 12, 0xFFA0A0A0);
        if (page == Page.BASICS && !creating) context.drawTextWithShadow(textRenderer, Text.literal("Event ID: " + compact(idDraft, 44)), x, y + 43, 0xFFCCCCCC);
        String feedback = stale ? "Chronicle changed. Reopen before saving." : problem;
        if (!feedback.isBlank()) context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(feedback, w)), x, y + 190, stale ? 0xFFFFCC66 : 0xFFFF9966);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { returnToParent(); }

    private static String[] splitState(String id) {
        int split = id.lastIndexOf('|');
        if (split <= 0 || split == id.length() - 1) return null;
        String state = id.substring(split + 1).toUpperCase(Locale.ROOT);
        return (state.equals("START") || state.equals("END")) ? new String[] { id.substring(0, split), state } : null;
    }
    private static String generatedId(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return "chronicle_core:event_" + (slug.isBlank() ? "event" : slug);
    }
    private static String compact(String value, int characters) { return value.length() <= characters ? value : value.substring(0, Math.max(1, characters - 1)) + "…"; }
    private static String choice(String prefix, String actual, String expected) { return (actual.equals(expected) ? "● " : "○ ") + (prefix.isBlank() ? pretty(expected) : prefix + ": " + pretty(expected)); }
    private static String regionButton(String prefix, String id) { return prefix + ": " + (id.isBlank() ? "Choose…" : compact(id, 38)); }
    private static String pretty(String value) { return switch (value) { case "PARTICIPANTS" -> "Participants"; case "REGION" -> "Selected Region"; case "TIMER" -> "Active time"; default -> value.substring(0, 1) + value.substring(1).toLowerCase(Locale.ROOT); }; }
    private static String pageLabel(Page value) { return switch (value) { case BASICS -> "Basics"; case MEDIA -> "Message & sound"; case TRIGGER -> "Trigger"; case CHANGES -> "Region changes"; }; }
    private static String pageHelp(Page value) { return switch (value) { case BASICS -> "Name and describe this event"; case MEDIA -> "Choose optional private-preview media"; case TRIGGER -> "Choose audience and automatic trigger"; case CHANGES -> "Apply existing Region Saved states"; }; }
    private static String triggerHelp(String value) { return switch (value) { case "MANUAL" -> "Run only when the organizer asks"; case "ENTRY" -> "Run when a participant enters a Region"; case "TIMER" -> "Run after active play time"; default -> "Run when a registered flag is present"; }; }
    private static String pickerTitle(PickerPurpose purpose) { return switch (purpose) { case SOUND -> "Choose sound"; case AUDIENCE_REGION -> "Choose audience Region"; case TRIGGER_REGION -> "Choose entry Region"; case FLAG -> "Choose registered flag"; case STATE -> "Add Region Saved state"; }; }
    private String pageDescription() { return switch (page) { case BASICS -> "Name this event; the server validates its ID."; case MEDIA -> "Message and sound are optional, but an event needs an effect."; case TRIGGER -> "Audience and trigger references are separate from changed Regions."; case CHANGES -> "Saved states are applied only after server review."; }; }
}
