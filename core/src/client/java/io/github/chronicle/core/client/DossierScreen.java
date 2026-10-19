package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ProfilePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.function.Predicate;

/** A compact, public character card. The server remains the authority for every saved field. */
public final class DossierScreen extends Screen {
    private static final int NAME_CLIENT_LIMIT = 96;
    private static final int PRONOUNS_CLIENT_LIMIT = 64;
    private static final int DESCRIPTION_CLIENT_LIMIT = 1024;
    private static final int NAME_CODE_POINT_LIMIT = 48;
    private static final int PRONOUNS_CODE_POINT_LIMIT = 32;
    private static final int DESCRIPTION_CODE_POINT_LIMIT = 512;
    private static final int SAVE_TIMEOUT_TICKS = 20 * 10;

    private ProfilePayloads.View profile;
    private String name;
    private String pronouns;
    private String description;
    private TextFieldWidget nameField;
    private TextFieldWidget pronounsField;
    private TextFieldWidget descriptionField;
    private ButtonWidget saveButton;
    private ScrollableTextWidget readOnlyDescription;
    private String noticeKey;
    private int pendingTicks = -1;
    private boolean draftsDirty;

    public DossierScreen(ProfilePayloads.View profile) {
        super(Text.translatable("screen.chronicle.profile.title"));
        this.profile = profile;
        this.name = profile.name();
        this.pronouns = profile.pronouns();
        this.description = profile.description();
        this.noticeKey = profile.saved() ? "chronicle.profile.saved" : null;
    }

    public UUID owner() {
        return profile.owner();
    }

    /** Updates the card using canonical server data and retains owner drafts until a save succeeds. */
    public void accept(ProfilePayloads.View updated) {
        profile = updated;
        if (updated.saved()) {
            pendingTicks = -1;
            name = updated.name();
            pronouns = updated.pronouns();
            description = updated.description();
            noticeKey = "chronicle.profile.saved";
            refreshFields();
        } else if (!draftsDirty) {
            name = updated.name();
            pronouns = updated.pronouns();
            description = updated.description();
            refreshFields();
        }
        if (!profile.editable()) {
            clearAndInit();
        }
        updateSaveButton();
    }

    /** Shows server feedback without logging data or replacing in-progress edits. */
    public static void showNotice(MinecraftClient client, String translationKey) {
        if (client.currentScreen instanceof DossierScreen screen) {
            screen.pendingTicks = -1;
            screen.noticeKey = translationKey;
            screen.updateSaveButton();
        } else if (client.player != null) {
            client.inGameHud.getChatHud().addMessage(Text.translatable(translationKey));
        }
    }

    @Override
    protected void init() {
        int contentWidth = Math.max(180, Math.min(300, width - 32));
        int left = (width - contentWidth) / 2;
        if (!profile.editable()) {
            initReadOnly(left, contentWidth);
            return;
        }
        int fieldY = Math.max(48, height / 2 - 60);
        nameField = field(left, fieldY, contentWidth, "screen.chronicle.profile.name", NAME_CLIENT_LIMIT, NAME_CODE_POINT_LIMIT, value -> name = value, name);
        pronounsField = field(left, fieldY + 34, contentWidth, "screen.chronicle.profile.pronouns", PRONOUNS_CLIENT_LIMIT, PRONOUNS_CODE_POINT_LIMIT, value -> pronouns = value, pronouns);
        descriptionField = field(left, fieldY + 68, contentWidth, "screen.chronicle.profile.description", DESCRIPTION_CLIENT_LIMIT, DESCRIPTION_CODE_POINT_LIMIT, value -> description = value, description);
        saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.chronicle.profile.save"), button -> save())
                .dimensions(left, fieldY + 90, contentWidth - 74, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.chronicle.profile.done"), button -> close())
                .dimensions(left + contentWidth - 70, fieldY + 90, 70, 20).build());
        updateSaveButton();
    }

    private void initReadOnly(int left, int contentWidth) {
        addDrawableChild(new ScrollableTextWidget(left, 53, contentWidth, 22, Text.literal(name), textRenderer)
                .textColor(0xFFFFFFFF));
        addDrawableChild(new ScrollableTextWidget(left, 93, contentWidth, 22, Text.literal(pronouns), textRenderer)
                .textColor(0xFFFFFFFF));
        readOnlyDescription = addDrawableChild(new ScrollableTextWidget(left, 130, contentWidth,
                Math.max(44, height - 184), Text.literal(description), textRenderer).textColor(0xFFFFFFFF));
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.chronicle.profile.done"), button -> close())
                .dimensions(left, height - 28, contentWidth, 20).build());
    }

    private TextFieldWidget field(int left, int y, int fieldWidth, String label, int maximumLength, int maximumCodePoints,
                                  java.util.function.Consumer<String> changed, String value) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, left, y, fieldWidth, 20, Text.translatable(label)));
        field.setMaxLength(maximumLength);
        field.setTextPredicate(plainTextAtMost(maximumLength, maximumCodePoints));
        field.setText(value);
        field.setChangedListener(updated -> {
            changed.accept(updated);
            draftsDirty = true;
            updateSaveButton();
        });
        return field;
    }

    private static Predicate<String> plainTextAtMost(int maximumLength, int maximumCodePoints) {
        return value -> value.length() <= maximumLength
                && value.codePointCount(0, value.length()) <= maximumCodePoints
                && value.indexOf('\u00A7') < 0
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private void save() {
        if (pendingTicks >= 0) {
            return;
        }
        if (!ClientPlayNetworking.canSend(ProfilePayloads.Save.ID)) {
            noticeKey = "chronicle.profile.error.offline";
            updateSaveButton();
            return;
        }
        ClientPlayNetworking.send(new ProfilePayloads.Save(profile.revision(), name, pronouns, description));
        pendingTicks = 0;
        noticeKey = "chronicle.profile.saving";
        updateSaveButton();
    }

    @Override
    public void tick() {
        if (pendingTicks >= 0 && ++pendingTicks >= SAVE_TIMEOUT_TICKS) {
            pendingTicks = -1;
            noticeKey = "chronicle.profile.error.timeout";
            updateSaveButton();
        }
    }

    private void refreshFields() {
        if (nameField != null) {
            nameField.setText(name);
            pronounsField.setText(pronouns);
            descriptionField.setText(description);
        }
        draftsDirty = false;
    }

    /** Development-only hook used by the opt-in in-game visual smoke check. */
    void setVisualSmokeDraft() {
        if (nameField != null) {
            nameField.setText("Ada Ash");
            pronounsField.setText("she/her");
            descriptionField.setText("A calm cartographer with ink-stained gloves.");
        }
    }

    void saveForVisualSmoke() {
        save();
    }

    boolean visualSmokeDraftRetained() {
        return "Ada Ash".equals(name) && "she/her".equals(pronouns)
                && "A calm cartographer with ink-stained gloves.".equals(description);
    }

    boolean visualSmokeSaved() {
        return profile.saved() && profile.revision() > 0 && visualSmokeDraftRetained();
    }

    private void updateSaveButton() {
        if (saveButton != null) {
            boolean valid = isSaveValid();
            saveButton.active = pendingTicks < 0 && valid;
            saveButton.setMessage(Text.translatable(pendingTicks >= 0 ? "screen.chronicle.profile.saving" : "screen.chronicle.profile.save"));
            if (pendingTicks < 0 && !valid) {
                noticeKey = "chronicle.profile.error.invalid";
            }
        }
        if (nameField != null) {
            boolean editable = pendingTicks < 0;
            nameField.active = editable;
            pronounsField.active = editable;
            descriptionField.active = editable;
            nameField.setEditable(editable);
            pronounsField.setEditable(editable);
            descriptionField.setEditable(editable);
        }
    }

    private boolean isSaveValid() {
        return !name.strip().isEmpty()
                && plainTextAtMost(NAME_CLIENT_LIMIT, NAME_CODE_POINT_LIMIT).test(name)
                && plainTextAtMost(PRONOUNS_CLIENT_LIMIT, PRONOUNS_CODE_POINT_LIMIT).test(pronouns)
                && plainTextAtMost(DESCRIPTION_CLIENT_LIMIT, DESCRIPTION_CODE_POINT_LIMIT).test(description);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
        int contentWidth = Math.max(180, Math.min(300, width - 32));
        int left = (width - contentWidth) / 2;
        int fieldY = Math.max(48, height / 2 - 60);
        if (profile.editable()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("screen.chronicle.profile.name"), left, fieldY - 11, 0xFFA0A0A0);
            context.drawTextWithShadow(textRenderer, Text.translatable("screen.chronicle.profile.pronouns"), left, fieldY + 23, 0xFFA0A0A0);
            context.drawTextWithShadow(textRenderer, Text.translatable("screen.chronicle.profile.description"), left, fieldY + 57, 0xFFA0A0A0);
            context.drawTextWithShadow(textRenderer, Text.translatable("screen.chronicle.profile.public_help"), left, fieldY + 128, 0xFFA0A0A0);
        } else {
            context.drawTextWithShadow(textRenderer, Text.translatable("screen.chronicle.profile.name"), left, 42, 0xFFA0A0A0);
            context.drawTextWithShadow(textRenderer, Text.translatable("screen.chronicle.profile.pronouns"), left, 82, 0xFFA0A0A0);
            context.drawTextWithShadow(textRenderer, Text.translatable("screen.chronicle.profile.description"), left, 118, 0xFFA0A0A0);
        }
        if (noticeKey != null) {
            int noticeY = profile.editable() ? Math.min(height - 36, fieldY + 144) : height - 50;
            context.drawWrappedTextWithShadow(textRenderer, Text.translatable(noticeKey), left, noticeY, contentWidth,
                    noticeKey.startsWith("chronicle.profile.error") ? 0xFFFF7777 : 0xFF77FF77);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
