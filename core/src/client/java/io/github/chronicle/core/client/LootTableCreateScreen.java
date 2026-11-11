package io.github.chronicle.core.client;

import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.menu.CoreMenuModel.View;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Local metadata form; explicit Create saves an empty table through the parent menu. */
public final class LootTableCreateScreen extends Screen {
    private final CoreMenuScreen parent;
    private final Consumer<LootType> create;
    private final long token;
    private String name = "", id = "", description = "Custom loot table.", error = "";
    private boolean stale;
    private ButtonWidget createButton;

    LootTableCreateScreen(CoreMenuScreen parent, Consumer<LootType> create) {
        super(Text.literal("Create new loot table"));
        this.parent = parent;
        this.create = create;
        token = parent.state().token();
    }

    CoreMenuScreen parentMenu() { return parent; }
    long openingToken() { return token; }

    void serverUpdated(View view) {
        if (view.token() != token || !view.operator() || !view.editing() || !view.editable() || !view.hold().isBlank()) {
            stale = true;
            error = "Server state changed. Close and reopen Create.";
            clearAndInit();
        }
    }

    void rejected(String message, boolean changed) {
        error = message;
        stale |= changed;
    }

    @Override protected void init() {
        int w = panelWidth(), x = (width - w) / 2, y = panelTop();
        field("Name", name, 80, x, y + 42, w, value -> name = value);
        field("ID (optional)", id, 160, x, y + 78, w, value -> id = value);
        field("Description", description, 256, x, y + 114, w, value -> description = value);
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), ignored -> close()).dimensions(x, y + 164, w / 2 - 2, 18).build());
        createButton = addDrawableChild(ButtonWidget.builder(Text.literal("Create"), ignored -> submit()).dimensions(x + w / 2 + 2, y + 164, w / 2 - 2, 18).build());
        updateEnabled();
    }

    private void field(String label, String value, int maximum, int x, int y, int w, Consumer<String> change) {
        var field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(label)));
        field.setMaxLength(maximum);
        field.setText(value);
        field.setEditable(!stale);
        field.setChangedListener(text -> { change.accept(text); error = ""; updateEnabled(); });
    }

    private LootType definition() {
        String label = name.trim(), key = id.trim();
        if (key.isBlank()) {
            String slug = Normalizer.normalize(label, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (slug.isBlank()) throw new IllegalArgumentException("Enter a name or a custom ID.");
            key = "chronicle_core:loot_" + slug.substring(0, Math.min(64, slug.length()));
        }
        return new LootType(key, label, description.isBlank() ? "Custom loot table." : description.trim(), 0, 0, List.of(), List.of());
    }

    private void updateEnabled() {
        if (createButton == null) return;
        try { definition(); createButton.active = !stale; }
        catch (IllegalArgumentException failure) { createButton.active = false; }
    }

    private void submit() {
        if (stale) return;
        try { create.accept(definition()); }
        catch (IllegalArgumentException failure) { error = failure.getMessage(); }
    }

    private int panelWidth() { return Math.min(380, width - 40); }
    private int panelTop() { return Math.max(12, (height - 196) / 2); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = panelWidth(), x = (width - w) / 2, y = panelTop();
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("popup/background"), x - 8, y - 8, w + 16, 198);
        line(context, title.getString(), x, y, w, 0xFFFFFFFF);
        line(context, "Create an empty table, then add its contents.", x, y + 15, w, 0xFFA0A0A0);
        line(context, "Name", x, y + 30, w, 0xFFCCCCCC);
        line(context, "ID (optional; generated from name)", x, y + 66, w, 0xFFCCCCCC);
        line(context, "Description", x, y + 102, w, 0xFFCCCCCC);
        if (!error.isBlank()) line(context, error, x, y + 142, w, 0xFFFF9966);
        super.render(context, mouseX, mouseY, delta);
    }

    private void line(DrawContext context, String value, int x, int y, int width, int color) {
        context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(value, width)), x, y, color);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
}
