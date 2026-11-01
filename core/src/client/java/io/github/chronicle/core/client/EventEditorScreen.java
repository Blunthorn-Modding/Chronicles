package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.EventSource;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * A local five-section Event draft. This class intentionally has no event-domain authority:
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

    private enum Page { BASICS, WHEN, REQUIREMENTS, DELAY, ACTIONS }
    private enum Panel { SECTION, ADD_ACTION, EDIT_FLAG, EDIT_TEXT, EDIT_SOUND, EDIT_STATE, EDIT_ENCOUNTER }
    private enum PickerPurpose { SOUND, AUDIENCE_REGION, TRIGGER_REGION, FLAG, STATE, TRIGGER, TARGET, ACTIVITY, ACTION_FLAG, ENCOUNTER_GROUP, ENCOUNTER_POINT, ENCOUNTER_REGION }

    private final CoreMenuScreen parent;
    private final Transport transport;
    private final long openingToken;
    private final boolean creating;
    private final long revision;
    private Page page = Page.BASICS;
    private Panel panel = Panel.SECTION;
    private final List<String> requiredFlags = new ArrayList<>();
    private final Map<String, Boolean> flagChanges = new LinkedHashMap<>();
    private String sourceType = "", sourceTarget = "", sourceActivity = "", editFlag = "";
    private String sourceTypeLabel = "", sourceTargetLabel = "", sourceActivityLabel = "";
    private boolean editFlagValue = true, delayEnabled;
    private String savedMessage, savedSound, savedAudience, savedAudienceRegion;
    private long savedAudienceRevision;
    private Map<String, Boolean> savedFlagChanges;
    private List<EventEditorForm.EncounterAction> savedEncounterActions;
    private int requirementOffset, actionOffset, editStateIndex = -1, editEncounterIndex = -1;
    private PickerPurpose pickerPurpose;
    private CatalogAddPopup popup;
    private String pickerKind = "";
    private String idDraft, nameDraft, descriptionDraft, messageDraft, soundDraft;
    private String audienceDraft, audienceRegionDraft, modeDraft, triggerRegionDraft, secondsDraft, flagDraft;
    private long audienceRegionRevision, triggerRegionRevision;
    private final List<EventEditorForm.Change> changes = new ArrayList<>();
    private final List<EventEditorForm.EncounterAction> encounterActions = new ArrayList<>();
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
        secondsDraft = Long.toString(value.seconds()); delayEnabled = value.seconds() > 0; flagDraft = value.flag(); changes.addAll(value.changes());
        requiredFlags.addAll(value.requiredFlags()); flagChanges.putAll(value.flagChanges()); encounterActions.addAll(value.encounterActions());
        if (value.source() != null) { sourceType = value.source().type(); sourceTarget = value.source().target(); sourceActivity = value.source().activity(); }
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
        if (popup != null && view.picker() != null && view.picker().kind().equals(pickerKind)) {
            var picker = view.picker();
            if ((pickerPurpose == PickerPurpose.TARGET || pickerPurpose == PickerPurpose.ACTIVITY) && picker.query().startsWith(sourceType + "|"))
                picker = new CoreMenuModel.Picker(picker.kind(), picker.query().substring(sourceType.length() + 1), picker.page(), picker.pages(), picker.entries());
            popup.replacePage(picker);
        }
    }

    /** Preserve the authored draft for inspection but refuse further mutations after a stale reply. */
    public void rejected(String message, boolean stale) {
        problem = message == null ? "The server rejected this event draft." : message;
        this.stale |= stale;
        if (this.stale && popup != null) popup.markStale();
        if (client.currentScreen == this) clearAndInit();
    }

    @Override protected void init() {
        retainInputs(); idField = null; nameField = null; descriptionField = null; messageField = null; secondsField = null; clearChildren();
        int w = Math.min(440, Math.max(240, width - 24));
        int x = (width - w) / 2, y = Math.max(6, (height - 240) / 2 + 6);
        if (panel == Panel.SECTION) {
            button("<", x, y + 22, 26, () -> navigate(-1), "Previous section", usable() && page.ordinal() > 0);
            button(pageLabel(page) + " " + (page.ordinal() + 1) + "/5", x + 30, y + 22, w - 60, () -> {}, pageHelp(page), false);
            button(">", x + w - 26, y + 22, 26, () -> navigate(1), "Next section", usable() && page.ordinal() < Page.values().length - 1);
            switch (page) {
                case BASICS -> basics(x, y, w);
                case WHEN -> when(x, y, w);
                case REQUIREMENTS -> requirements(x, y, w);
                case DELAY -> delay(x, y, w);
                case ACTIONS -> actions(x, y, w);
            }
        } else actionPanel(x, y, w);
        button("Cancel", x, y + 206, 92, () -> { if (panel == Panel.SECTION) returnToParent(); else { restoreAction(); panel = Panel.SECTION; clearAndInit(); } }, panel == Panel.SECTION ? "Discard this local draft" : "Return to actions", true);
        if (panel == Panel.SECTION) button(creating ? "Create event" : "Save edits", x + 96, y + 206, w - 96, this::save,
                creating ? "Create this configured event" : "Save all event changes", usable() && !hasUnresolvedChanges());
        else button("Done", x + 96, y + 206, w - 96, () -> { retainInputs(); panel = Panel.SECTION; clearAndInit(); }, "Return to actions", usable());
    }

    private void navigate(int direction) { retainInputs(); page = Page.values()[page.ordinal() + direction]; problem = ""; clearAndInit(); }
    public void navigateForTest(int direction) { if (panel == Panel.SECTION && page.ordinal() + direction >= 0 && page.ordinal() + direction < Page.values().length) navigate(direction); }
    private void basics(int x, int y, int w) {
        int row = y + 60;
        if (creating) {
            idField = field(x, row, w, "Event ID (generated, editable)", idDraft, 128, value -> { idDraft = value; if (!updatingGeneratedId) manuallyEditedId = true; }); row += 34;
        }
        nameField = field(x, row, w, "Event name", nameDraft, 80, value -> {
            nameDraft = value;
            if (creating && !manuallyEditedId) { idDraft = generatedId(value); if (idField != null) { updatingGeneratedId = true; idField.setText(idDraft); updatingGeneratedId = false; } }
        });
        descriptionField = field(x, row + 34, w, "Organizer description (optional)", descriptionDraft, 512, value -> descriptionDraft = value);
    }
    private void when(int x, int y, int w) {
        button("Trigger: " + triggerLabel(), x, y + 60, w, () -> openPicker(PickerPurpose.TRIGGER), "Choose when this event is eligible", usable());
        if ("ENTRY".equals(modeDraft)) button(regionButton("Entry Region", triggerRegionDraft), x, y + 82, w, () -> openPicker(PickerPurpose.TRIGGER_REGION), "Choose an entry Region", usable());
        if ("SYSTEM".equals(modeDraft)) {
            button("Target: " + (sourceTarget.isBlank() ? "Choose…" : compact(sourceTargetLabel.isBlank() ? sourceTarget : sourceTargetLabel, 32)), x, y + 82, w, () -> openPicker(PickerPurpose.TARGET), "Choose a registered target", usable());
            button("Activity: " + (sourceActivity.isBlank() ? "Any activity" : compact(sourceActivityLabel.isBlank() ? sourceName(sourceActivity) : sourceActivityLabel, 32)), x, y + 104, w, () -> openPicker(PickerPurpose.ACTIVITY), "Choose an optional activity filter", usable());
        }
    }
    private void requirements(int x, int y, int w) {
        button("Add prerequisite…", x, y + 60, w - 46, () -> openPicker(PickerPurpose.FLAG), "All required flags must be On", usable() && requiredFlags.size() < 32);
        listArrows(x, y, w, requirementOffset, requiredFlags.size(), true);
        requirementOffset = Math.clamp(requirementOffset, 0, Math.max(0, requiredFlags.size() - 4));
        for (int i = 0; i < 4 && requirementOffset + i < requiredFlags.size(); i++) {
            int index = requirementOffset + i, row = y + 84 + i * 22; String flag = requiredFlags.get(index);
            button(compact(flag, 32), x, row, w - 64, () -> {}, flag, false);
            button("Remove", x + w - 60, row, 60, () -> { requiredFlags.remove(index); clearAndInit(); }, "Remove prerequisite", usable());
        }
    }
    private void delay(int x, int y, int w) {
        int half = (w - 4) / 2;
        button((!delayEnabled ? "● " : "○ ") + "No delay", x, y + 60, half, () -> { delayEnabled = false; clearAndInit(); }, "Run without a countdown", usable() && !"TIMER".equals(modeDraft));
        button((delayEnabled ? "● " : "○ ") + "Active-play delay", x + half + 4, y + 60, w - half - 4, () -> { delayEnabled = true; clearAndInit(); }, "Countdown uses eligible active play", usable());
        if (delayEnabled) secondsField = field(x, y + 100, w, "Active seconds", secondsDraft, 9, value -> secondsDraft = value);
    }
    private List<String> actionKeys() {
        List<String> keys = new ArrayList<>();
        flagChanges.keySet().forEach(k -> keys.add("F:" + k));
        if (!messageDraft.isBlank()) keys.add("M:"); if (!soundDraft.isBlank()) keys.add("S:");
        for (int i = 0; i < changes.size(); i++) keys.add("R:" + i);
        for (int i = 0; i < encounterActions.size(); i++) keys.add("E:" + i);
        return keys;
    }
    private void actions(int x, int y, int w) {
        List<String> keys = actionKeys(); actionOffset = Math.clamp(actionOffset, 0, Math.max(0, keys.size() - 4));
        button("Add action…", x, y + 60, w - 46, () -> { snapshotAction(); panel = Panel.ADD_ACTION; clearAndInit(); }, "Add an event outcome", usable());
        listArrows(x, y, w, actionOffset, keys.size(), false);
        for (int i = 0; i < 4 && actionOffset + i < keys.size(); i++) {
            String key = keys.get(actionOffset + i); int row = y + 84 + i * 22;
            String label = key.startsWith("F:") ? "Flag " + key.substring(2) + " → " + (flagChanges.get(key.substring(2)) ? "On" : "Off") : key.equals("M:") ? "Send text" : key.equals("S:") ? "Play sound: " + soundDraft : key.startsWith("E:") ? "Spawn: " + encounterActions.get(Integer.parseInt(key.substring(2))).groupLabel() : "Region: " + changes.get(Integer.parseInt(key.substring(2))).label();
            button(compact(label, 28), x, row, w - 100, () -> {}, label, false);
            button("Edit", x + w - 96, row, 46, () -> editAction(key), "Edit this action", usable());
            button("Remove", x + w - 48, row, 48, () -> { removeAction(key); clearAndInit(); }, "Remove this action", usable());
        }
    }
    private void listArrows(int x, int y, int w, int offset, int size, boolean flags) {
        button("↑", x + w - 44, y + 60, 20, () -> { if (flags) requirementOffset--; else actionOffset--; clearAndInit(); }, "Earlier entries", usable() && offset > 0);
        button("↓", x + w - 20, y + 60, 20, () -> { if (flags) requirementOffset++; else actionOffset++; clearAndInit(); }, "Later entries", usable() && offset + 4 < size);
    }
    private void editAction(String key) {
        snapshotAction();
        if (key.startsWith("F:")) { editFlag = key.substring(2); editFlagValue = flagChanges.get(editFlag); panel = Panel.EDIT_FLAG; }
        else if (key.equals("M:")) panel = Panel.EDIT_TEXT;
        else if (key.equals("S:")) panel = Panel.EDIT_SOUND;
        else if (key.startsWith("E:")) { editEncounterIndex = Integer.parseInt(key.substring(2)); panel = Panel.EDIT_ENCOUNTER; }
        else { editStateIndex = Integer.parseInt(key.substring(2)); panel = Panel.EDIT_STATE; }
        clearAndInit();
    }
    private void snapshotAction() { retainInputs(); savedMessage = messageDraft; savedSound = soundDraft; savedAudience = audienceDraft; savedAudienceRegion = audienceRegionDraft; savedAudienceRevision = audienceRegionRevision; savedFlagChanges = new LinkedHashMap<>(flagChanges); savedEncounterActions = List.copyOf(encounterActions); }
    private void restoreAction() { if (savedFlagChanges == null) return; messageDraft = savedMessage; soundDraft = savedSound; audienceDraft = savedAudience; audienceRegionDraft = savedAudienceRegion; audienceRegionRevision = savedAudienceRevision; flagChanges.clear(); flagChanges.putAll(savedFlagChanges); encounterActions.clear(); encounterActions.addAll(savedEncounterActions); messageField = null; savedFlagChanges = null; savedEncounterActions = null; }
    private void removeAction(String key) { if (key.startsWith("F:")) flagChanges.remove(key.substring(2)); else if (key.equals("M:")) messageDraft = ""; else if (key.equals("S:")) soundDraft = ""; else if (key.startsWith("E:")) encounterActions.remove(Integer.parseInt(key.substring(2))); else changes.remove(Integer.parseInt(key.substring(2))); }
    private void actionPanel(int x, int y, int w) {
        switch (panel) {
            case ADD_ACTION -> {
                button("Update flag…", x, y + 60, w, () -> { editFlag = ""; editFlagValue = true; panel = Panel.EDIT_FLAG; clearAndInit(); }, "Set a saved flag On or Off", usable() && flagChanges.size() < 32);
                button("Send text…", x, y + 82, w, () -> { panel = Panel.EDIT_TEXT; clearAndInit(); }, "One message per Event", usable() && messageDraft.isBlank());
                button("Play sound…", x, y + 104, w, () -> { panel = Panel.EDIT_SOUND; clearAndInit(); }, "One sound per Event", usable() && soundDraft.isBlank());
                button("Apply Region Saved state…", x, y + 126, w, () -> openPicker(PickerPurpose.STATE), "Choose an existing saved state", usable() && changes.size() < EventEditorForm.MAX_CHANGES);
                button("Spawn encounter group…", x, y + 148, w, () -> { encounterActions.add(new EventEditorForm.EncounterAction("", "", "GROUP", "", "", 0, false, "Choose an encounter group.")); editEncounterIndex = encounterActions.size() - 1; panel = Panel.EDIT_ENCOUNTER; clearAndInit(); }, "Spawn a bounded saved group when this Event runs", usable() && encounterActions.size() < EventEditorForm.MAX_ENCOUNTER_ACTIONS);
            }
            case EDIT_FLAG -> {
                button("Flag: " + (editFlag.isBlank() ? "Choose…" : compact(editFlag, 32)), x, y + 60, w, () -> openPicker(PickerPurpose.ACTION_FLAG), "Choose a registered flag", usable());
                int half = (w - 4) / 2;
                button((editFlagValue ? "● " : "○ ") + "On", x, y + 82, half, () -> { editFlagValue = true; if (!editFlag.isBlank()) flagChanges.put(editFlag, true); clearAndInit(); }, "Set this flag On", usable());
                button((!editFlagValue ? "● " : "○ ") + "Off", x + half + 4, y + 82, w - half - 4, () -> { editFlagValue = false; if (!editFlag.isBlank()) flagChanges.put(editFlag, false); clearAndInit(); }, "Set this flag Off", usable());
            }
            case EDIT_STATE -> {
                EventEditorForm.Change change = changes.get(editStateIndex);
                button("Replace saved state…", x, y + 60, w, () -> openPicker(PickerPurpose.STATE), "Choose another saved state", usable());
                button("Show bounds", x, y + 82, w, () -> transport.showBounds(change), change.current() ? "Show target bounds" : "This saved state is unavailable", usable() && change.current());
            }
            case EDIT_TEXT -> { messageField = field(x, y + 60, w, "Message", messageDraft, 512, value -> messageDraft = value); mediaControls(x, y, w); }
            case EDIT_SOUND -> { button(soundDraft.isBlank() ? "Choose sound…" : "Sound: " + compact(soundDraft, 32), x, y + 60, w, () -> openPicker(PickerPurpose.SOUND), "Choose sound", usable()); mediaControls(x, y, w); }
            case EDIT_ENCOUNTER -> encounterPanel(x, y, w);
            default -> { }
        }
    }
    private void encounterPanel(int x,int y,int w){EventEditorForm.EncounterAction action=encounterActions.get(editEncounterIndex);button(action.groupId().isBlank()?"Encounter group: Choose…":"Encounter group: "+compact(action.groupLabel(),30),x,y+60,w,()->openPicker(PickerPurpose.ENCOUNTER_GROUP),"Choose a saved encounter composition",usable());button((action.targetKind().equals("GROUP")?"● ":"○ ")+"Use group target",x,y+82,w,()->setEncounterTarget("GROUP","","",0,!action.groupId().isBlank(),action.groupId().isBlank()?"Choose an encounter group.":""),"Use the group’s exact map binding",usable()&&!action.groupId().isBlank());int half=(w-4)/2;button((action.targetKind().equals("POINT")?"● ":"○ ")+"Choose Point…",x,y+104,half,()->openPicker(PickerPurpose.ENCOUNTER_POINT),"Override with an exact map Point",usable()&&!action.groupId().isBlank());button((action.targetKind().equals("REGION")?"● ":"○ ")+"Choose Region…",x+half+4,y+104,w-half-4,()->openPicker(PickerPurpose.ENCOUNTER_REGION),"Override with an exact map Region",usable()&&!action.groupId().isBlank());if(!action.siteId().isBlank())button("Target: "+compact(action.siteLabel(),36),x,y+126,w,()->{},action.problem().isBlank()?action.siteId()+" @ "+action.siteRevision():action.problem(),false);}
    private void setEncounterTarget(String kind,String site,String label,long revision,boolean current,String issue){EventEditorForm.EncounterAction old=encounterActions.get(editEncounterIndex);encounterActions.set(editEncounterIndex,new EventEditorForm.EncounterAction(old.groupId(),old.groupLabel(),kind,site,label,revision,current,issue));clearAndInit();}
    private void mediaControls(int x, int y, int w) {
        int half = (w - 4) / 2;
        button(choice("", audienceDraft, "PARTICIPANTS"), x, y + 94, half, () -> { audienceDraft = "PARTICIPANTS"; clearAndInit(); }, "Send to participants", usable());
        button(choice("", audienceDraft, "REGION"), x + half + 4, y + 94, w - half - 4, () -> { audienceDraft = "REGION"; clearAndInit(); }, "Send to a Region", usable());
        if ("REGION".equals(audienceDraft)) button(regionButton("Audience Region", audienceRegionDraft), x, y + 116, w, () -> openPicker(PickerPurpose.AUDIENCE_REGION), "Choose audience Region", usable());
        button("Preview privately", x, y + 150, w, this::preview, "Preview only your unsaved text and sound", usable() && (!messageDraft.isBlank() || !soundDraft.isBlank()));
    }
    private String triggerLabel() { return switch (modeDraft) { case "MANUAL" -> "Manual"; case "ENTRY" -> "Region entry"; case "TIMER" -> "Active play timer"; case "CONDITION" -> "When flags are On"; case "SYSTEM" -> sourceTypeLabel.isBlank() ? sourceName(sourceType) : sourceTypeLabel; default -> "Choose…"; }; }

    private void openPicker(PickerPurpose purpose) {
        if (!usable()) return;
        retainInputs(); pickerPurpose = purpose; pickerKind = switch (purpose) {
            case SOUND -> "EVENT_SOUND"; case AUDIENCE_REGION, TRIGGER_REGION -> "EVENT_REGION";
            case FLAG, ACTION_FLAG -> "EVENT_FLAG"; case STATE -> "EVENT_STATE";
            case TRIGGER -> "EVENT_TRIGGER"; case TARGET -> "EVENT_TARGET"; case ACTIVITY -> "EVENT_ACTIVITY";
            case ENCOUNTER_GROUP -> "EVENT_ENCOUNTER_GROUP"; case ENCOUNTER_POINT -> "EVENT_POINT"; case ENCOUNTER_REGION -> "EVENT_REGION";
        };
        popup = new CatalogAddPopup(this, Text.literal(pickerTitle(purpose)), false, new CatalogAddPopup.Owner() {
            @Override public void requestPage(CatalogAddPopup source, String query, int page) { transport.requestCatalog(source, pickerKind, (pickerPurpose == PickerPurpose.TARGET || pickerPurpose == PickerPurpose.ACTIVITY) ? sourceType + "|" + query.substring(0, Math.min(query.length(), 80)) : query, page); }
            @Override public String validate(Entry entry, String ignored) { return validatePicker(entry); }
            @Override public void add(Entry entry, String ignored) { applyPicker(entry); }
            @Override public void cancel() { popup = null; }
            @Override public void serverUpdated(CatalogAddPopup source, CoreMenuModel.View view) { EventEditorScreen.this.serverUpdated(view); }
        });
        client.setScreen(popup);
    }

    private String validatePicker(Entry entry) {
        if (pickerPurpose == PickerPurpose.FLAG && requiredFlags.contains(entry.id())) return "Already required.";
        if (pickerPurpose == PickerPurpose.ACTION_FLAG && flagChanges.containsKey(entry.id()) && !entry.id().equals(editFlag)) return "This flag already has an action.";
        if ((pickerPurpose == PickerPurpose.TARGET || pickerPurpose == PickerPurpose.ACTIVITY) && !entry.id().startsWith(sourceType + "|")) return "This option belongs to a different source.";
        if (!"EVENT_STATE".equals(pickerKind)) return "";
        String[] pin = splitState(entry.id());
        if (pin == null) return "This saved state is no longer available.";
        boolean duplicate = java.util.stream.IntStream.range(0, changes.size()).anyMatch(i -> (panel != Panel.EDIT_STATE || i != editStateIndex) && changes.get(i).regionId().equals(pin[0]));
        return duplicate ? "That Region already has an event Saved state target." : "";
    }

    private void applyPicker(Entry entry) {
        if (stale) return;
        switch (pickerPurpose) {
            case SOUND -> soundDraft = entry.id();
            case AUDIENCE_REGION -> { audienceDraft = "REGION"; audienceRegionDraft = entry.id(); audienceRegionRevision = entry.revision(); }
            case TRIGGER_REGION -> { triggerRegionDraft = entry.id(); triggerRegionRevision = entry.revision(); }
            case FLAG -> { if (!"none".equals(entry.id())) requiredFlags.add(entry.id()); }
            case ACTION_FLAG -> { if (!editFlag.isBlank()) flagChanges.remove(editFlag); editFlag = entry.id(); flagChanges.put(editFlag, editFlagValue); }
            case TRIGGER -> { String id = entry.id(); if (Set.of("MANUAL", "ENTRY", "TIMER", "CONDITION").contains(id)) { modeDraft = id; sourceType = ""; sourceTarget = ""; sourceActivity = ""; sourceTypeLabel = ""; sourceTargetLabel = ""; sourceActivityLabel = ""; if ("TIMER".equals(id)) delayEnabled = true; } else { if (!id.equals(sourceType)) { sourceTarget = ""; sourceActivity = ""; sourceTargetLabel = ""; sourceActivityLabel = ""; } sourceType = id; sourceTypeLabel = entry.label(); modeDraft = "SYSTEM"; } }
            case TARGET -> { sourceTarget = entry.id().substring(sourceType.length() + 1); sourceTargetLabel = entry.label(); }
            case ACTIVITY -> { sourceActivity = entry.id().substring(sourceType.length() + 1); sourceActivityLabel = entry.label(); }
            case STATE -> {
                String[] pin = splitState(entry.id());
                if (pin != null) { EventEditorForm.Change change = new EventEditorForm.Change(pin[0], entry.label(), pin[1], entry.revision(), entry.reference(), "", true, ""); if (panel == Panel.EDIT_STATE) changes.set(editStateIndex, change); else changes.add(change); } panel = Panel.SECTION;
            }
            case ENCOUNTER_GROUP -> { boolean current=entry.revision()>0;encounterActions.set(editEncounterIndex,new EventEditorForm.EncounterAction(entry.id(),entry.label(),"GROUP","","",0,current,current?"":"This group has no current default target; choose a Point or Region override.")); }
            case ENCOUNTER_POINT, ENCOUNTER_REGION -> { String kind=pickerPurpose==PickerPurpose.ENCOUNTER_POINT?"POINT":"REGION";setEncounterTarget(kind,entry.id(),entry.label(),entry.revision(),true,""); }
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
        transport.preview(form(true));
    }
    private EventEditorForm form() { return form(false); }
    private EventEditorForm form(boolean preview) {
        // A media preview may be requested while a different page has an unfinished timer.
        // Keep the text draft untouched; the full Save validator reports it before persistence.
        long seconds = preview || !delayEnabled ? 0 : Math.max(0, Math.min(EventEditorForm.MAX_SECONDS, parseSeconds()));
        return new EventEditorForm(idDraft.trim(), revision, nameDraft.trim(), descriptionDraft, messageDraft, soundDraft,
                audienceDraft, audienceRegionDraft, audienceRegionRevision, modeDraft, triggerRegionDraft, triggerRegionRevision,
                seconds, List.copyOf(requiredFlags), List.copyOf(changes), creating, Map.copyOf(flagChanges), new EventSource(sourceType, sourceTarget, sourceActivity),List.copyOf(encounterActions));
    }
    private String validation() {
        if (stale) return "Chronicle changed. This draft is preserved; reopen the event before saving.";
        if (idDraft.trim().isBlank()) return "An event ID is required.";
        if (nameDraft.trim().isBlank()) return "An event name is required.";
        if ((!messageDraft.isBlank() || !soundDraft.isBlank()) && !"PARTICIPANTS".equals(audienceDraft) && !"REGION".equals(audienceDraft)) return "Choose an audience.";
        if ((!messageDraft.isBlank() || !soundDraft.isBlank()) && "REGION".equals(audienceDraft) && audienceRegionDraft.isBlank()) return "Choose an audience Region.";
        if (!Set.of("MANUAL", "ENTRY", "TIMER", "CONDITION", "SYSTEM").contains(modeDraft)) return "Choose a trigger.";
        if ("ENTRY".equals(modeDraft) && triggerRegionDraft.isBlank()) return "Choose an entry Region.";
        if (delayEnabled && (parseSeconds() <= 0 || parseSeconds() > EventEditorForm.MAX_SECONDS)) return "Active seconds must be between 1 and " + EventEditorForm.MAX_SECONDS + ".";
        if ("CONDITION".equals(modeDraft) && requiredFlags.isEmpty()) return "Choose a flag. Create flags under Advanced → Flags.";
        if (messageDraft.isBlank() && soundDraft.isBlank() && changes.isEmpty() && flagChanges.isEmpty() && encounterActions.isEmpty()) return "Add at least one action.";
        Set<String> pins = new HashSet<>();
        for (EventEditorForm.Change change : changes) {
            if (change.regionId().isBlank() || change.state().isBlank() || !change.current() || !change.problem().isBlank()) return "Repair unavailable Region Saved states before saving.";
            if (!pins.add(change.regionId())) return "A Region may have only one event Saved state target.";
        }
        for(EventEditorForm.EncounterAction action:encounterActions)if(action.groupId().isBlank()||!action.current()||!action.problem().isBlank())return "Repair unavailable encounter groups or map targets before saving.";
        return "";
    }
    private boolean hasUnresolvedChanges() {
        return changes.stream().anyMatch(change -> !change.current() || !change.problem().isBlank())||encounterActions.stream().anyMatch(action->!action.current()||!action.problem().isBlank());
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
        field.setMaxLength(max); field.setText(value); field.setPlaceholder(Text.literal(hint));field.setTooltip(Tooltip.of(Text.literal(hint))); field.setChangedListener(changed); field.active = usable(); return field;
    }
    private void button(String label, int x, int y, int width, Runnable action, String tooltip, boolean enabled) {
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, Math.max(1, width - 8))), ignored -> action.run()).dimensions(x, y, Math.max(1, width), 18).build());
        button.active = enabled; button.setTooltip(Tooltip.of(Text.literal(tooltip)));
    }
    private boolean usable() { return !stale; }
    private void returnToParent() { client.setScreen(parent); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(440, Math.max(240, width - 24)); int x = (width - w) / 2; int y = Math.max(6, (height - 240) / 2 + 6);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, 240);
        context.drawTextWithShadow(textRenderer, title, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(pageDescription(),w)), x, y + 12, 0xFFA0A0A0);
        for(var child:children())if(child instanceof TextFieldWidget field)context.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(field.getMessage().getString(),field.getWidth())),field.getX(),field.getY()-10,0xFFCCCCCC);
        if (panel == Panel.SECTION && page == Page.BASICS && !creating) context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth("Event ID: " + idDraft,w)), x, y + 145, 0xFFCCCCCC);
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
    private static String sourceName(String id) {
        int colon = id.lastIndexOf(':');
        String path = id.substring(colon + 1).replace('_', ' ').replace('/', ' ').trim();
        return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
    private static String generatedId(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return "chronicle_core:event_" + (slug.isBlank() ? "event" : slug);
    }
    private static String compact(String value, int characters) { return value.length() <= characters ? value : value.substring(0, Math.max(1, characters - 1)) + "…"; }
    private static String choice(String prefix, String actual, String expected) { return (actual.equals(expected) ? "● " : "○ ") + (prefix.isBlank() ? pretty(expected) : prefix + ": " + pretty(expected)); }
    private static String regionButton(String prefix, String id) { return prefix + ": " + (id.isBlank() ? "Choose…" : compact(id, 38)); }
    private static String pretty(String value) { return switch (value) { case "PARTICIPANTS" -> "Participants"; case "REGION" -> "Selected Region"; case "TIMER" -> "Active time"; default -> value.substring(0, 1) + value.substring(1).toLowerCase(Locale.ROOT); }; }
    private static String pageLabel(Page value) { return switch (value) { case BASICS -> "Basics"; case WHEN -> "When"; case REQUIREMENTS -> "Requirements"; case DELAY -> "Delay"; case ACTIONS -> "What happens"; }; }
    private static String pageHelp(Page value) { return switch (value) { case BASICS -> "Name and describe this event"; case WHEN -> "Choose a trigger and its target"; case REQUIREMENTS -> "All required flags must be On"; case DELAY -> "Active-play countdown"; case ACTIONS -> "Choose event outcomes"; }; }
    private static String pickerTitle(PickerPurpose purpose) { return switch (purpose) { case SOUND -> "Choose sound"; case AUDIENCE_REGION -> "Choose audience Region"; case TRIGGER_REGION -> "Choose entry Region"; case FLAG, ACTION_FLAG -> "Choose registered flag"; case STATE -> "Add Region Saved state"; case TRIGGER -> "Choose trigger"; case TARGET -> "Choose target"; case ACTIVITY -> "Choose activity";case ENCOUNTER_GROUP->"Choose encounter group";case ENCOUNTER_POINT->"Choose spawn Point";case ENCOUNTER_REGION->"Choose spawn Region"; }; }
    private String pageDescription() { if (panel != Panel.SECTION) return "Edit actions in this local draft."; return switch (page) { case BASICS -> "Name this event; the server validates its ID."; case WHEN -> "Choose when this event becomes eligible."; case REQUIREMENTS -> "All required flags must be On."; case DELAY -> "Any required flag turning Off resets the full delay."; case ACTIONS -> "Actions take effect only when the event runs."; }; }
}
