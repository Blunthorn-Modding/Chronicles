package io.github.chronicle.core.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.function.Consumer;

/** Collects the initial Region draft; the server owns world selection and saving. */
final class RegionCreateScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> begin;
    private String nameDraft = "", error = "", initialState = "start";
    private boolean savedStates;
    private TextFieldWidget name;

    RegionCreateScreen(Screen parent, Consumer<String> begin) {
        super(Text.literal("Create new Region"));
        this.parent = parent;
        this.begin = begin;
    }

    @Override protected void init() {
        int w = Math.min(380, width - 24), x = (width - w) / 2, y = Math.max(12, (height - 186) / 2);
        name = addDrawableChild(new TextFieldWidget(textRenderer, x, y + 39, w, 18, Text.literal("Region name")));
        name.setMaxLength(96);
        name.setText(nameDraft);
        name.setChangedListener(value -> nameDraft = value);
        button("Saved states: " + (savedStates ? "on" : "off"), x, y + 65, w, () -> {
            savedStates = !savedStates;
            clearAndInit();
        });
        if (savedStates) {
            button((initialState.equals("start") ? "[x] " : "[ ] ") + "Start", x, y + 104, w / 2 - 2, () -> { initialState = "start"; clearAndInit(); });
            button((initialState.equals("end") ? "[x] " : "[ ] ") + "End", x + w / 2 + 2, y + 104, w / 2 - 2, () -> { initialState = "end"; clearAndInit(); });
        }
        button("Cancel", x, y + 143, w / 2 - 2, this::close);
        button("Begin selection", x + w / 2 + 2, y + 143, w / 2 - 2, this::submit);
    }

    private void submit() {
        String value = nameDraft.trim();
        if (value.isBlank()) { error = "A Region name is required."; return; }
        JsonObject draft = new JsonObject();
        draft.addProperty("name", value);
        draft.addProperty("savedStates", savedStates);
        draft.addProperty("state", savedStates ? initialState : "");
        begin.accept(draft.toString());
    }

    private void button(String label, int x, int y, int width, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, Math.max(1, width), 18).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(380, width - 24), x = (width - w) / 2, y = Math.max(12, (height - 186) / 2);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("popup/background"), x - 8, y - 8, w + 16, 186);
        context.drawTextWithShadow(textRenderer, title, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth("Choose two corners, then /chronicle regions save.", w)), x, y + 15, 0xFFA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal("Region name"), x, y + 27, 0xFFCCCCCC);
        if (savedStates) context.drawTextWithShadow(textRenderer, Text.literal("Capture this state when saving:"), x, y + 92, 0xFFCCCCCC);
        if (!error.isBlank()) context.drawTextWithShadow(textRenderer, Text.literal(error), x, y + 128, 0xFFFF9966);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
}
