package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.View;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

/** Local draft for creating a flag; the server validates and persists it. */
public final class FlagCreateScreen extends Screen {
    private final CoreMenuScreen parent;
    private final Consumer<String> create;
    private final long token;
    private String id = "", error = "";
    private boolean stale;
    private TextFieldWidget idField;
    private ButtonWidget createButton;

    FlagCreateScreen(CoreMenuScreen parent, Consumer<String> create) {
        super(Text.literal("Create flag"));
        this.parent = parent;
        this.create = create;
        token = parent.state().token();
    }

    CoreMenuScreen parentMenu() { return parent; }
    long openingToken() { return token; }

    void serverUpdated(View view) {
        if (view.token() != token || !view.operator() || !view.editable() || !view.editing() || !view.hold().isBlank()) {
            stale = true;
            error = "Server state changed. Close and reopen Create.";
            clearAndInit();
        }
    }

    void rejected(String message, boolean changed) {
        error = message == null ? "The server rejected this flag." : message;
        stale |= changed;
        if (stale && idField != null) idField.setEditable(false);
        updateEnabled();
    }

    @Override protected void init() {
        int w = panelWidth(), x = (width - w) / 2, y = panelTop();
        idField = addDrawableChild(new TextFieldWidget(textRenderer, x, y + 48, w, 18, Text.literal("Flag ID")));
        idField.setMaxLength(128);
        idField.setText(id);
        idField.setPlaceholder(Text.literal("chronicle_core:my_flag"));
        idField.setEditable(!stale);
        idField.setChangedListener(value -> { id = value; error = ""; updateEnabled(); });
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), ignored -> close()).dimensions(x, y + 119, w / 2 - 2, 18).build());
        createButton = addDrawableChild(ButtonWidget.builder(Text.literal("Create"), ignored -> submit()).dimensions(x + w / 2 + 2, y + 119, w / 2 - 2, 18).build());
        updateEnabled();
    }

    private boolean valid() {
        return id.length() <= 128 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private void updateEnabled() { if (createButton != null) createButton.active = !stale && valid(); }

    private void submit() {
        if (stale || !valid()) return;
        try { create.accept(id); }
        catch (IllegalArgumentException failure) { error = failure.getMessage() == null ? "Could not create flag." : failure.getMessage(); }
    }

    private int panelWidth() { return Math.min(380, width - 40); }
    private int panelTop() { return Math.max(12, (height - 153) / 2); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = panelWidth(), x = (width - w) / 2, y = panelTop();
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("popup/background"), x - 8, y - 8, w + 16, 153);
        context.drawTextWithShadow(textRenderer, title, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("New flags start Off."), x, y + 15, 0xFFA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal("Flag ID"), x, y + 36, 0xFFCCCCCC);
        int line = 0;
        for (var wrapped : textRenderer.wrapLines(Text.literal(error), Math.max(1, w))) {
            if (line >= 3) break;
            context.drawTextWithShadow(textRenderer, wrapped, x, y + 76 + 10 * line++, 0xFFFF9966);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    public void setIdForTest(String value) { id = value; if (idField != null) idField.setText(value); }
    public String idForTest() { return id; }
    public boolean pressForTest(String label) {
        return children().stream().filter(child -> child instanceof ButtonWidget)
                .map(child -> (ButtonWidget) child).filter(button -> button.active && button.visible && button.getMessage().getString().equals(label))
                .findFirst().map(button -> { button.onPress(new net.minecraft.client.input.MouseInput(0, 0)); return true; }).orElse(false);
    }
    public List<CoreMenuScreen.WidgetBounds> visibleWidgetBoundsForTest() {
        return children().stream().filter(child -> child instanceof ClickableWidget widget && widget.visible)
                .map(child -> (ClickableWidget) child)
                .map(widget -> new CoreMenuScreen.WidgetBounds(widget.getMessage().getString(), widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())).toList();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
}
