package io.github.chronicle.core.client;

import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Action;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Entry;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Snapshot;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Tab;
import io.github.chronicle.core.network.AuthoringUiPayloads;
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
import java.util.List;

/**
 * A deliberately compact client for bounded authoring snapshots. It never derives positions,
 * permissions, or world state locally; each button produces a typed request for server revalidation.
 */
public final class AuthoringScreen extends Screen {
    private static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;
    private static final int REFRESH_INTERVAL_TICKS = 20 * 2;
    private Snapshot state;
    private boolean pending;
    private int pendingTicks;
    private int idleTicks;
    private int entryOffset;
    private Action requestedAction;
    /** Polling is deliberately non-blocking: the current snapshot remains server-authoritative. */
    private boolean backgroundRefresh;
    private String selectedEntry = "";
    private String selectedPurpose = "";
    private String previewedPurpose = "";
    private String selectedChoice = "";
    private String selectedLocation = "";
    private String selectedPlayer = "";
    private String selectedEntryLabel = "";
    private String selectedEntryDetail = "";
    private String selectedEntryKind = "";
    private String selectedEntryReference = "";
    private long selectedEntryRevision;
    private String selectedPurposeLabel = "";
    private String selectedLocationLabel = "";
    private String selectedPlayerLabel = "";
    private String selectedObservation = "";
    private Entry observationSavedConfig;
    private String observationRegion = "", observationFlag = "", observationDelay = "60";
    private String previewedObservation = "";
    private long observationPreviewExpires;
    private TextFieldWidget delayInput;
    private Confirmation confirmation;
    private String inlineNotice;
    private final List<Entry> visibleEntries = new ArrayList<>();
    private final java.util.Map<String, ButtonWidget> testButtons = new java.util.LinkedHashMap<>();

    public AuthoringScreen(Snapshot state) {
        super(Text.literal("Chronicle authoring"));
        this.state = state;
        inlineNotice = state.message();
    }

    /** Canonical bounded state, retained for graphical regression tests. */
    public Snapshot state() { return state; }
    /** True after a sent request until a View or the ten-second client timeout. */
    public boolean pending() { return pending; }

    /** Selects a rendered list item through its normal callback; intended for opt-in visual tests. */
    public void selectForTest(String id) {
        for (int attempt = 0; attempt < 12; attempt++) {
            for (Entry entry : visibleEntries) if (entry.id().equals(id)) { select(entry); return; }
            mouseScrolled(10, 60, 0, -1);
        }
        throw new IllegalArgumentException("Entry not present on current page: " + id);
    }

    /** Chooses a listed chest type through the same selection path as the cycle controls. */
    public void chooseTypeForTest(String id) { if (find(state.choices(), id) != null) chooseType(id); }

    /** Presses an active rendered button by its visible label; it cannot bypass widget state. */
    public boolean triggerForTest(String label) {
        ButtonWidget button = testButtons.get(label);
        if (button == null || !button.active) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
        return true;
    }

    /** Focuses a real active widget through the screen's normal keyboard-navigation API. */
    public void focusButtonForTest(String label) {
        ButtonWidget button = testButtons.get(label);
        if (button == null || !button.active) throw new IllegalArgumentException("Active button not present: " + label);
        setFocused(button);
    }

    /** Reports widget identity/state without mutating it or taking desktop cursor ownership. */
    public ButtonProbe probeButtonForTest(String label) {
        ButtonWidget button = testButtons.get(label);
        if (button == null) throw new IllegalArgumentException("Button not present: " + label);
        return new ButtonProbe(System.identityHashCode(button), button.active, button.isHovered(), button.isFocused());
    }
    /** Uses the normal text-field validation/change callback in opt-in graphical tests. */
    public void setObservationDelayForTest(String seconds) {
        if (delayInput == null || state.tab() != Tab.OBSERVATIONS) throw new IllegalStateException("Open Observations first.");
        delayInput.setText(seconds);
    }

    void accept(Snapshot updated) {
        boolean preserveWidgets = backgroundRefresh && requestedAction == Action.REFRESH && updated.sameControls(state);
        Entry savedObservation = find(updated.choices(), selectedObservation);
        if (updated.tab() == Tab.OBSERVATIONS && savedObservation != null && !savedObservation.equals(observationSavedConfig))
            loadObservation(savedObservation);
        if (updated.tab() != state.tab() || updated.page() != state.page()) entryOffset = 0;
        if (updated.token() != state.token()) { confirmation = null; previewedObservation = ""; }
        if (requestedAction == Action.OBS_PREVIEW && updated.success()) {
            previewedObservation = selectedObservation; observationPreviewExpires = System.nanoTime() + 30_000_000_000L;
        }
        if (requestedAction == Action.OBS_FIRE || !updated.success()) previewedObservation = "";
        if (requestedAction == Action.SPAWN_PREVIEW && updated.success()) previewedPurpose = selectedPurpose;
        if (updated.previewPlayer().isBlank()) previewedPurpose = "";
        state = updated;
        pending = false;
        pendingTicks = 0;
        idleTicks = 0;
        if (!updated.message().isBlank()) inlineNotice = updated.message();
        else if (requestedAction != Action.REFRESH) inlineNotice = null;
        requestedAction = null;
        backgroundRefresh = false;
        if (!contains(updated.entries(), selectedEntry)) {
            selectedEntry = "";
            selectedEntryLabel = selectedEntryDetail = selectedEntryKind = selectedEntryReference = "";
            selectedEntryRevision = 0;
        }
        if (updated.tab() == Tab.SPAWN_LOCATIONS && contains(updated.entries(), selectedLocation)) selectedEntry = selectedLocation;
        if (updated.tab() == Tab.SPAWN_PLAYERS && contains(updated.entries(), selectedPlayer)) selectedEntry = selectedPlayer;
        if (updated.tab() == Tab.OBSERVATIONS && contains(updated.entries(), selectedObservation)) selectedEntry = selectedObservation;
        Entry current = find(updated.entries(), selectedEntry);
        if (current != null) {
            selectedEntryLabel = current.label(); selectedEntryDetail = current.detail(); selectedEntryKind = current.kind();
            selectedEntryReference = current.reference(); selectedEntryRevision = current.revision();
        }
        // Stable polling must not recreate widgets. Rebuilding every two seconds cleared hover,
        // focus and confirmation state and visibly flickered even when the server snapshot was identical.
        if (!preserveWidgets) clearAndInit();
    }

    void notice(String message) {
        boolean preserveWidgets = backgroundRefresh;
        pending = false;
        requestedAction = Action.REFRESH;
        backgroundRefresh = false;
        inlineNotice = message;
        if (!preserveWidgets) clearAndInit();
    }

    void openCurrentTab() { request(Action.OPEN, "", "", state.tab(), 0); }

    @Override
    protected void init() {
        testButtons.clear();
        delayInput = null;
        int left = 5;
        int full = Math.max(310, width - 10);
        button("Refresh", width - 59, 2, 54, 16, () -> request(Action.REFRESH, "", "", state.tab(), state.page()), true);
        int tabWidth = Math.max(49, Math.min(62, (full - 4) / 6));
        Tab[] tabs = {Tab.CHESTS, Tab.PLACES, Tab.ROLES, Tab.SPAWNS, Tab.POLICIES, Tab.SESSION};
        for (int index = 0; index < tabs.length; index++) {
            Tab tab = tabs[index];
            button(tab.name().substring(0, 1) + tab.name().substring(1).toLowerCase(), left + index * tabWidth, 35, tabWidth - 2, 18,
                    () -> request(Action.OPEN, "", "", tab, 0), true);
        }
        addEntryList(left, 57, full, Math.max(3, Math.min(7, (height - 170) / 20)));
        int controlsY = Math.max(121, height - 98);
        switch (state.tab()) {
            case CHESTS -> chests(left, controlsY, full);
            case PLACES -> places(left, controlsY, full);
            case ROLES -> roles(left, controlsY, full);
            case SPAWNS -> spawns(left, controlsY, full);
            case SPAWN_LOCATIONS -> spawnLocations(left, controlsY, full);
            case SPAWN_PLAYERS -> spawnPlayers(left, controlsY, full);
            case SESSION -> session(left, controlsY, full);
            case PARTICIPANTS -> participants(left, controlsY, full);
            case OBSERVATIONS -> observations(left, controlsY, full);
            case OBS_REGIONS -> button("Use selected region", left, controlsY, full, 18,
                    () -> request(Action.OPEN, "", "", Tab.OBSERVATIONS, 0), !selectedEntry.isBlank());
            case OBS_FLAGS -> button("Use selected flag", left, controlsY, full, 18,
                    () -> request(Action.OPEN, "", "", Tab.OBSERVATIONS, 0), !selectedEntry.isBlank());
            case POLICIES -> policies(left, controlsY, full);
            case POLICY_PURPOSES -> button("Use selected purpose", left, controlsY, full, 18,
                    () -> request(Action.OPEN, "", "", Tab.POLICIES, 0), !selectedPurpose.isBlank());
        }
        int navY = height - 24;
        button("< Page", left, navY, 68, 18, () -> request(Action.OPEN, "", "", state.tab(), Math.max(0, state.page() - 1)), state.page() > 0);
        button((state.page() + 1) + "/" + state.pages(), left + 71, navY, 43, 18, () -> { }, false);
        button("Page >", left + 117, navY, 68, 18, () -> request(Action.OPEN, "", "", state.tab(), state.page() + 1), state.page() + 1 < state.pages());
        button(state.author() ? "Mode off" : "Mode on", left + 188, navY, 62, 18,
                () -> request(state.author() ? Action.MODE_OFF : Action.MODE_ON, "", "", state.tab(), state.page()), state.editable());
        button("Close", width - 62, navY, 57, 18, this::close, true);
    }

    private void addEntryList(int left, int y, int full, int rows) {
        visibleEntries.clear();
        entryOffset = Math.max(0, Math.min(entryOffset, Math.max(0, state.entries().size() - rows)));
        int rowWidth = full;
        for (int index = 0; index < Math.min(rows, state.entries().size() - entryOffset); index++) {
            Entry entry = state.entries().get(entryOffset + index);
            visibleEntries.add(entry);
            String selected = entry.id().equals(selectedEntry) ? "* " : "";
            entryButton(selected + entry.label(), entry, left, y + index * 19, rowWidth, () -> select(entry));
        }
    }

    private void chests(int left, int y, int full) {
        Entry choice = find(state.choices(), selectedChoice);
        button("Loot <", left, y, 55, 18, () -> cycleChoice(-1), !state.choices().isEmpty());
        button("Loot: " + clipped(choice == null ? "choose type" : choice.label(), 22), left + 57, y, full - 114, 18, () -> cycleChoice(1), !state.choices().isEmpty());
        button("Loot >", left + full - 55, y, 55, 18, () -> cycleChoice(1), !state.choices().isEmpty());
        boolean edit = canEdit();
        button("Bind looked container", left, y + 20, full / 2 - 2, 18,
                () -> request(Action.CHEST_BIND, selectedChoice, "", state.tab(), state.page()), edit && choice != null);
        button("Reload loot", left + full / 2 + 2, y + 20, full / 2 - 2, 18,
                () -> request(Action.LOOT_RELOAD, "", "", state.tab(), state.page()), edit);
        button("Show selected", left, y + 40, full / 3 - 2, 18,
                () -> request(Action.CHEST_SHOW, selectedEntry, "", state.tab(), state.page()), !selectedEntry.isBlank());
        confirmButton("Retype selected", Action.CHEST_RETYPE, selectedEntry, selectedChoice, left + full / 3 + 1, y + 40, full / 3 - 2,
                edit && !selectedEntry.isBlank() && choice != null, "Future fills replace all contents.");
        confirmButton("Unbind selected", Action.CHEST_UNBIND, selectedEntry, "", left + 2 * full / 3 + 2, y + 40, full / 3 - 2,
                edit && !selectedEntry.isBlank(), "Future fills replace all contents.");
    }

    private void places(int left, int y, int full) {
        boolean edit = canEdit();
        button("Show", left, y, full / 3 - 2, 18, () -> request(Action.SITE_SHOW, selectedEntry, "", state.tab(), state.page()), !selectedEntry.isBlank());
        confirmButton("Replace", Action.SITE_REPLACE, selectedEntry, "", left + full / 3 + 1, y, full / 3 - 2, edit && !selectedEntry.isBlank(), "Replace the selected saved geometry.");
        confirmButton("Remove", Action.SITE_REMOVE, selectedEntry, "", left + 2 * full / 3 + 2, y, full / 3 - 2, edit && !selectedEntry.isBlank(), "Remove the selected saved place.");
        selectionControls(left, y + 21, full, edit);
    }

    private void roles(int left, int y, int full) {
        boolean edit = canEdit();
        boolean bound = selectedEntryRevision > 0;
        button("Save role", left, y, full / 2 - 2, 18, () -> request(Action.ROLE_SAVE, selectedEntry, "", state.tab(), state.page()), edit && !selectedEntry.isBlank() && !bound);
        confirmButton("Replace role", Action.ROLE_REPLACE, selectedEntry, "", left + full / 2 + 2, y, full / 2 - 2, edit && !selectedEntry.isBlank() && bound, "Replace this role's current site binding.");
        selectionControls(left, y + 21, full, edit);
    }

    private void selectionControls(int left, int y, int full, boolean edit) {
        int cell = (full - 6) / 4;
        button("Corner 1", left, y, cell, 18, () -> request(Action.CORNER_1, "", "", state.tab(), state.page()), edit);
        button("Corner 2", left + cell + 2, y, cell, 18, () -> request(Action.CORNER_2, "", "", state.tab(), state.page()), edit);
        button("Target 1", left + 2 * (cell + 2), y, cell, 18, () -> request(Action.TARGET_1, "", "", state.tab(), state.page()), edit);
        button("Target 2", left + 3 * (cell + 2), y, cell, 18, () -> request(Action.TARGET_2, "", "", state.tab(), state.page()), edit);
        button("Clear selection", left, y + 20, full / 2 - 2, 18, () -> request(Action.CLEAR_SELECTION, "", "", state.tab(), state.page()), edit);
        button("Hide preview", left + full / 2 + 2, y + 20, full / 2 - 2, 18, () -> request(Action.HIDE_PREVIEW, "", "", state.tab(), state.page()), true);
    }

    private void spawns(int left, int y, int full) {
        boolean edit = canEdit();
        boolean runtimeMove = state.phase().equalsIgnoreCase("ACTIVE") && state.hold().isBlank();
        button("Choose location", left, y, full / 2 - 2, 18, () -> request(Action.OPEN, "", "", Tab.SPAWN_LOCATIONS, 0), true);
        button("Choose player", left + full / 2 + 2, y, full / 2 - 2, 18, () -> request(Action.OPEN, "", "", Tab.SPAWN_PLAYERS, 0), true);
        button("Bind", left, y + 20, full / 3 - 2, 18, () -> request(Action.SPAWN_BIND, selectedPurpose, selectedLocation, state.tab(), state.page()), edit && !selectedPurpose.isBlank() && !selectedLocation.isBlank());
        confirmButton("Rebind", Action.SPAWN_REBIND, selectedPurpose, selectedLocation, left + full / 3 + 1, y + 20, full / 3 - 2, edit && !selectedPurpose.isBlank() && !selectedLocation.isBlank(), "Rebind " + selectedPurposeLabel + " to " + selectedLocationLabel + ".");
        confirmButton("Remove", Action.SPAWN_REMOVE, selectedPurpose, "", left + 2 * full / 3 + 2, y + 20, full / 3 - 2, edit && !selectedPurpose.isBlank(), "Remove this spawn purpose binding.");
        button("Preview player", left, y + 40, full / 2 - 2, 18, () -> request(Action.SPAWN_PREVIEW, selectedPurpose, selectedPlayer, state.tab(), state.page()), runtimeMove && !selectedPurpose.isBlank() && !selectedPlayer.isBlank());
        confirmButton("Confirm move", Action.SPAWN_CONFIRM, selectedPurpose, selectedPlayer, left + full / 2 + 2, y + 40, full / 2 - 2,
                runtimeMove && !selectedPurpose.isBlank() && !selectedPlayer.isBlank() && selectedPlayer.equals(state.previewPlayer()) && selectedPurpose.equals(previewedPurpose), "Move after preview: " + spawnContext());
    }

    private void spawnLocations(int left, int y, int full) {
        button("Use selected location", left, y, full, 18, () -> request(Action.OPEN, "", "", Tab.SPAWNS, 0), !selectedLocation.isBlank());
    }

    private void spawnPlayers(int left, int y, int full) {
        button("Use selected player", left, y, full, 18, () -> request(Action.OPEN, "", "", Tab.SPAWNS, 0), !selectedPlayer.isBlank());
    }

    private void session(int left, int y, int full) {
        String phase = state.phase().toUpperCase(java.util.Locale.ROOT);
        confirmButton("Start session", Action.SESSION_START, "", "", left, y, full / 2 - 2, phase.equals("INACTIVE") || phase.equals("ENDED"), "Start refills all bound loot chests.");
        button("Pause", left + full / 2 + 2, y, full / 2 - 2, 18, () -> request(Action.SESSION_PAUSE, "", "", state.tab(), state.page()), phase.equals("ACTIVE"));
        button("Resume", left, y + 20, full / 2 - 2, 18, () -> request(Action.SESSION_RESUME, "", "", state.tab(), state.page()), phase.equals("PAUSED"));
        confirmButton("End session", Action.SESSION_END, "", "", left + full / 2 + 2, y + 20, full / 2 - 2, phase.equals("ACTIVE") || phase.equals("PAUSED"), "End the current session.");
        button("Participants", left, y + 40, full / 2 - 2, 18, () -> request(Action.OPEN, "", "", Tab.PARTICIPANTS, 0), true);
        button("Observations", left + full / 2 + 2, y + 40, full / 2 - 2, 18, () -> request(Action.OPEN, "", "", Tab.OBSERVATIONS, 0), true);
    }

    private void participants(int left, int y, int full) {
        boolean edit = canEdit() && selectedEntryKind.equals("PLAYER");
        confirmButton("Enroll", Action.PARTICIPANT_ON, selectedEntry, "", left, y, full / 2 - 2,
                edit && selectedEntryReference.isBlank(), "Enroll this player in active play and public observation audiences.");
        confirmButton("Exclude", Action.PARTICIPANT_OFF, selectedEntry, "", left + full / 2 + 2, y, full / 2 - 2,
                edit && !selectedEntryReference.isBlank(), "Remove participation; retain observation history and existing spawn policies.");
        button("Back to Session", left, y + 40, full, 18, () -> request(Action.OPEN, "", "", Tab.SESSION, 0), true);
    }

    private void observations(int left, int y, int full) {
        int cell = full / 3;
        button("Region: " + (observationRegion.isBlank() ? "choose" : observationRegion), left, y, cell - 2, 18,
                () -> request(Action.OPEN, "", "", Tab.OBS_REGIONS, 0), true);
        button("Flag: " + (observationFlag.isBlank() ? "none" : observationFlag), left + cell + 1, y, cell - 2, 18,
                () -> request(Action.OPEN, "", "", Tab.OBS_FLAGS, 0), true);
        delayInput = new TextFieldWidget(textRenderer, left + 2 * cell + 2, y, full - 2 * cell - 2, 18, Text.literal("Active delay in seconds"));
        delayInput.setMaxLength(6);
        delayInput.setTextPredicate(value -> value.matches("[0-9]{0,6}"));
        delayInput.setText(observationDelay);
        delayInput.setChangedListener(value -> { observationDelay = value; confirmation = null; });
        delayInput.setTooltip(Tooltip.of(Text.literal("Timer delay in seconds of active, occupied play (1 to 604800).")));
        delayInput.active = canEdit() && (!pending || backgroundRefresh);
        addDrawableChild(delayInput);
        boolean selected = !selectedObservation.isBlank() && selectedEntryKind.equals("OBSERVATION");
        int quarter = full / 4;
        PlayState.Mode[] modes = PlayState.Mode.values();
        for (int index = 0; index < modes.length; index++) {
            var mode = modes[index];
            String label = mode.name().substring(0, 1) + mode.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            boolean confirming = confirmation != null && confirmation.matches(Action.OBS_REPLACE, selectedObservation, observationConfig(mode), state.token());
            button(confirming ? "Confirm: " + label : label, left + index * quarter, y + 20, quarter - 2, 18,
                    () -> saveObservation(mode), canEdit() && selected);
        }
        boolean active = state.phase().equalsIgnoreCase("ACTIVE") && state.hold().isBlank() && selected && selectedEntryRevision > 0 && selectedEntryReference.isBlank();
        button("Preview", left, y + 40, quarter - 2, 18, () -> request(Action.OBS_PREVIEW, selectedObservation, "", state.tab(), state.page()), active);
        confirmButton("Fire", Action.OBS_FIRE, selectedObservation, "", left + quarter, y + 40, quarter - 2,
                active && selectedObservation.equals(previewedObservation) && System.nanoTime() < observationPreviewExpires, "Send the previewed cue once to the same eligible audience.");
        confirmButton("Off", Action.OBS_OFF, selectedObservation, "", left + 2 * quarter, y + 40, quarter - 2,
                canEdit() && selected && selectedEntryRevision > 0, "Turn off this binding; retain delivery history.");
        confirmButton("Reset", Action.OBS_RESET, selectedObservation, "", left + 3 * quarter, y + 40, quarter - 2,
                canEdit() && selected && !selectedEntryReference.isBlank(), "Clear completion deliberately. The cue can fire again; timer delay restarts now.");
    }

    private String observationConfig(PlayState.Mode mode) {
        try { return new ObservationConfiguration(mode, observationRegion, observationFlag, mode == PlayState.Mode.TIMER ? Long.parseLong(observationDelay) : 0).encode(); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private void saveObservation(PlayState.Mode mode) {
        String config = observationConfig(mode);
        if (config == null) { inlineNotice = "Choose the required region/flag and a valid timer delay (1 to 604800 seconds)."; return; }
        Action action = selectedEntryRevision == 0 ? Action.OBS_SAVE : Action.OBS_REPLACE;
        if (action == Action.OBS_SAVE || confirmation != null && confirmation.matches(action, selectedObservation, config, state.token()))
            request(action, selectedObservation, config, state.tab(), state.page());
        else {
            confirmation = new Confirmation(action, selectedObservation, config, state.token(), "Replace this observation binding. Existing completion is retained.");
            inlineNotice = confirmation.help(); clearAndInit();
        }
    }

    private void policies(int left, int y, int full) {
        boolean edit = canEdit() && !selectedEntry.isBlank() && selectedEntryKind.equals("PLAYER");
        button("Purpose: " + (selectedPurposeLabel.isBlank() ? "choose a saved binding" : selectedPurposeLabel), left, y, full, 18,
                () -> request(Action.OPEN, "", "", Tab.POLICY_PURPOSES, 0), true);
        confirmButton("First arrival once", Action.AUTO_ARRIVAL_ON, selectedEntry, selectedPurpose, left, y + 20, full / 2 - 2,
                edit && !selectedPurpose.isBlank(), "Enable one first-arrival attempt on an eligible join or ACTIVE transition. No move now.");
        confirmButton("Death respawn", Action.AUTO_RESPAWN_ON, selectedEntry, selectedPurpose, left + full / 2 + 2, y + 20, full / 2 - 2,
                edit && !selectedPurpose.isBlank(), "Use this purpose after actual deaths during ACTIVE. Bed/anchor settings remain intact.");
        confirmButton("Arrival off", Action.AUTO_ARRIVAL_OFF, selectedEntry, "", left, y + 40, full / 3 - 2, edit, "Disable first arrival; retain its attempt history.");
        confirmButton("Respawn off", Action.AUTO_RESPAWN_OFF, selectedEntry, "", left + full / 3 + 1, y + 40, full / 3 - 2, edit, "Disable automatic death respawn.");
        confirmButton("Reset arrival", Action.AUTO_ARRIVAL_RESET, selectedEntry, "", left + 2 * full / 3 + 2, y + 40, full / 3 - 2,
                edit && !selectedEntryReference.isBlank(), "Remove the first-arrival claim. An enabled policy may move this player on the next eligible join or ACTIVE transition.");
    }

    private void select(Entry entry) {
        selectedEntry = entry.id();
        selectedEntryLabel = entry.label();
        selectedEntryDetail = entry.detail();
        selectedEntryKind = entry.kind();
        selectedEntryReference = entry.reference();
        selectedEntryRevision = entry.revision();
        if (state.tab() == Tab.SPAWNS || state.tab() == Tab.POLICY_PURPOSES) { selectedPurpose = entry.id(); selectedPurposeLabel = entry.label(); }
        if (state.tab() == Tab.CHESTS && find(state.choices(), entry.id()) != null) selectedChoice = entry.id();
        if (state.tab() == Tab.SPAWN_LOCATIONS) { selectedLocation = entry.id(); selectedLocationLabel = entry.label(); }
        if (state.tab() == Tab.SPAWN_PLAYERS) { selectedPlayer = entry.id(); selectedPlayerLabel = entry.label(); }
        if (state.tab() == Tab.OBSERVATIONS) {
            selectedObservation = entry.id(); previewedObservation = "";
            Entry config = find(state.choices(), entry.id());
            if (config != null) loadObservation(config);
            else { observationSavedConfig = null; observationRegion = ""; observationFlag = ""; observationDelay = "60"; }
        }
        if (state.tab() == Tab.OBS_REGIONS) observationRegion = entry.id().equals("none") ? "" : entry.id();
        if (state.tab() == Tab.OBS_FLAGS) observationFlag = entry.id().equals("none") ? "" : entry.id();
        confirmation = null;
        clearAndInit();
    }

    private void loadObservation(Entry config) {
        observationSavedConfig = config;
        var saved = ObservationConfiguration.decode(config.detail());
        observationRegion = saved.regionId(); observationFlag = saved.requiredFlag();
        observationDelay = Long.toString(saved.delaySeconds() == 0 ? 60 : saved.delaySeconds());
    }

    private void cycleChoice(int direction) {
        if (state.choices().isEmpty()) return;
        int index = 0;
        for (int i = 0; i < state.choices().size(); i++) if (state.choices().get(i).id().equals(selectedChoice)) { index = i; break; }
        chooseType(state.choices().get(Math.floorMod(index + direction, state.choices().size())).id());
    }

    private void chooseType(String id) {
        selectedChoice = id;
        confirmation = null;
        clearAndInit();
    }

    private void entryButton(String label, Entry entry, int x, int y, int w, Runnable action) {
        String tooltip = entry.kind() + ": " + entry.detail()
                + (entry.reference().isBlank() ? "" : " | bound: " + entry.reference())
                + (entry.revision() > 0 ? " | revision " + entry.revision() : "");
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, Math.max(1, w - 8))), ignored -> action.run())
                .dimensions(x, y, Math.max(1, w), 18).build());
        button.setTooltip(Tooltip.of(Text.literal(screenText(tooltip))));
        button.active = !pending || backgroundRefresh;
        testButtons.put(label, button);
    }

    private void confirmButton(String label, Action action, String id, String argument, int x, int y, int w, boolean enabled, String help) {
        boolean confirming = confirmation != null && confirmation.matches(action, id, argument, state.token());
        button(confirming ? "Confirm: " + label : label, x, y, w, 18, () -> {
            if (confirmation != null && confirmation.matches(action, id, argument, state.token())) request(action, id, argument, state.tab(), state.page());
            else { confirmation = new Confirmation(action, id, argument, state.token(), help); inlineNotice = help; clearAndInit(); }
        }, enabled);
    }

    private void button(String label, int x, int y, int w, int h, Runnable action, boolean enabled) {
        String visible = textRenderer.trimToWidth(label, Math.max(1, w - 8));
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(visible), ignored -> action.run()).dimensions(x, y, Math.max(1, w), h).build());
        button.setTooltip(Tooltip.of(Text.literal(screenText(label))));
        button.active = enabled && (!pending || backgroundRefresh);
        testButtons.put(label, button);
    }

    private boolean canEdit() { return state.editable() && state.author(); }

    private void request(Action action, String id, String argument, Tab tab, int page) {
        request(action, id, argument, tab, page, false);
    }

    private void request(Action action, String id, String argument, Tab tab, int page, boolean polling) {
        if (pending || !ClientPlayNetworking.canSend(AuthoringUiPayloads.Request.ID)) {
            inlineNotice = "Authoring UI is unavailable; wait for a server response.";
            return;
        }
        ClientPlayNetworking.send(new AuthoringUiPayloads.Request(action, id, argument, state.token(), tab, page));
        requestedAction = action;
        backgroundRefresh = polling;
        pending = true;
        pendingTicks = 0;
        idleTicks = 0;
        // An explicit request disables controls until its response. Background refreshes leave
        // the existing widgets live so unchanged snapshots do not flicker every two seconds.
        if (!polling) clearAndInit();
    }

    @Override
    public void tick() {
        if (!previewedObservation.isBlank() && System.nanoTime() >= observationPreviewExpires) {
            previewedObservation = ""; confirmation = null; clearAndInit();
        }
        if (pending) {
            if (++pendingTicks >= RESPONSE_TIMEOUT_TICKS) { pending = false; inlineNotice = "No authoring response after 10 seconds. Try Refresh."; clearAndInit(); }
            return;
        }
        if (++idleTicks >= REFRESH_INTERVAL_TICKS) request(Action.REFRESH, "", "", state.tab(), state.page(), true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int rows = Math.max(3, Math.min(7, (height - 170) / 20));
        if (mouseY >= 57 && mouseY < 57 + rows * 19 && !state.entries().isEmpty()) {
            int next = Math.max(0, Math.min(Math.max(0, state.entries().size() - rows), entryOffset + (verticalAmount < 0 ? 1 : -1)));
            if (next != entryOffset) { entryOffset = next; clearAndInit(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(screenText(title.getString())), width / 2, 5, 0xFFFFFFFF);
        String mode = state.author() ? "Author mode ON" : "Author mode OFF — enable it before editing";
        context.drawTextWithShadow(textRenderer, Text.literal(screenText("Session " + state.session() + " " + state.phase() + " | " + mode)), 5, 17, state.author() ? 0xFF77FF77 : 0xFFFFCC66);
        if (!state.editable()) context.drawTextWithShadow(textRenderer, Text.literal(screenText("ACTIVE: pause the session before editing; use Session for controls.")), 5, 26, 0xFFFF7777);
        boolean playTab = state.tab() == Tab.SESSION || state.tab() == Tab.PARTICIPANTS || state.tab() == Tab.OBSERVATIONS;
        String summary = (state.hold().isBlank() ? "" : "Hold: " + clipped(state.hold(), 42) + " | ") + (playTab ? state.selection() : "Target: " + state.target());
        Entry choice = find(state.choices(), selectedChoice);
        String selected = state.tab() == Tab.CHESTS && choice != null
                ? "Fills replace ALL contents | " + choice.detail()
                : state.tab() == Tab.SPAWNS && !selectedPurpose.isBlank()
                ? spawnContext()
                : selectedEntryLabel.isBlank() ? "Selection: " + clipped(state.selection(), 54)
                : "Selected " + selectedEntryKind + ": " + selectedEntryLabel + " - " + selectedEntryDetail;
        int controlsY = Math.max(121, height - 98);
        context.drawTextWithShadow(textRenderer, Text.literal(screenText(summary)), 5, controlsY - 24, 0xFFA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal(screenText(selected)), 5, controlsY - 13, 0xFFA0A0A0);
        String notice = inlineNotice == null || inlineNotice.isBlank() ? (pending && !backgroundRefresh ? "Waiting for server..." : "Scroll entries; select an item, then choose an action.") : inlineNotice;
        context.drawTextWithShadow(textRenderer, Text.literal(screenText(notice)), 5, height - 36, state.success() ? 0xFF77FF77 : 0xFFFFCC66);
        super.render(context, mouseX, mouseY, deltaTicks);
        if (mouseY >= height - 37 && mouseY <= height - 26)
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(notice), Math.max(160, Math.min(360, width - 20))), mouseX, mouseY);
        else if (mouseY >= controlsY - 25 && mouseY <= controlsY - 3)
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(mouseY < controlsY - 14 ? summary : selected), Math.max(160, Math.min(360, width - 20))), mouseX, mouseY);
    }

    @Override public boolean shouldPause() { return false; }

    private static Entry find(List<Entry> entries, String id) { for (Entry entry : entries) if (entry.id().equals(id)) return entry; return null; }
    private static boolean contains(List<Entry> entries, String id) { return find(entries, id) != null; }
    private String screenText(String value) { return textRenderer.trimToWidth(value, Math.max(1, width - 10)); }
    private String spawnContext() {
        return "Purpose: " + (selectedPurposeLabel.isBlank() ? selectedPurpose : selectedPurposeLabel)
                + " | Location: " + (selectedLocationLabel.isBlank() ? selectedLocation : selectedLocationLabel)
                + " | Player: " + (selectedPlayerLabel.isBlank() ? selectedPlayer : selectedPlayerLabel);
    }
    private static String clipped(String value, int limit) { return value == null ? "" : value.length() <= limit ? value : value.substring(0, Math.max(0, limit - 1)) + "…"; }
    private record Confirmation(Action action, String id, String argument, long token, String help) {
        boolean matches(Action otherAction, String otherId, String otherArgument, long otherToken) {
            return action == otherAction && id.equals(otherId) && argument.equals(otherArgument) && token == otherToken;
        }
    }

    /** Immutable graphical-test observation of an actual rendered button. */
    public record ButtonProbe(int identity, boolean active, boolean hovered, boolean focused) { }
}
