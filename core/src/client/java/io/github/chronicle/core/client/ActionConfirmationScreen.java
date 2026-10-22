package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ActionConfirmationPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Explicit generic danger confirmation; it never opens from a late server reply. */
public final class ActionConfirmationScreen extends Screen {
    private static final int TIMEOUT_TICKS = 200;
    private static ActionConfirmationScreen openScreen;
    private ActionConfirmationPayloads.View state;
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private String status;
    private UUID pending;
    private int pendingTicks = -1;
    private boolean replacing;

    public ActionConfirmationScreen(ActionConfirmationPayloads.View state) { super(Text.literal(state.title())); this.state = state; status = state.status(); }
    public ActionConfirmationPayloads.View state() { return state; }
    public boolean triggerForTest(String label) {
        ButtonWidget button = buttons.get(label);
        if (button == null || !button.active || !button.visible) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0)); return true;
    }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ActionConfirmationPayloads.View.ID,
                (view, context) -> context.client().execute(() -> receive(context.client(), view)));
    }
    private static void receive(MinecraftClient client, ActionConfirmationPayloads.View view) {
        if (client.currentScreen instanceof ActionConfirmationScreen screen) {
            if (view.opening() && !view.token().equals(screen.state.token())) {
                screen.replacing = true;
                openScreen = new ActionConfirmationScreen(view); client.setScreen(openScreen); return;
            }
            screen.accept(view); openScreen = screen; return;
        }
        // Refreshed/completed responses must never pull a player back into a confirmation.
        if (!view.opening() || view.completed()) return;
        openScreen = new ActionConfirmationScreen(view); client.setScreen(openScreen);
    }
    private void accept(ActionConfirmationPayloads.View updated) {
        if (pending != null && !pending.equals(updated.token()) && updated.completed()) return;
        if (pending == null && !updated.token().equals(state.token())) return;
        state = updated; status = updated.status(); pending = null; pendingTicks = -1; clearAndInit();
    }
    @Override protected void init() {
        buttons.clear();
        int margin = 12, content = Math.max(160, width - margin * 2), detailsTop = 42;
        addDrawableChild(new ScrollableTextWidget(margin, detailsTop, content, Math.max(44, height - 128), Text.literal(state.details()), textRenderer).textColor(0xFFFFFFFF));
        addDrawableChild(new ScrollableTextWidget(margin, height - 78, content, 30, Text.literal(status), textRenderer).textColor(0xFFCCCCCC));
        int third = Math.max(72, (content - 6) / 3);
        button("accept", state.acceptLabel(), margin, height - 30, third, usable() && state.available(), () -> send(0));
        button("refresh", "Refresh", margin + third + 3, height - 30, third, usable(), () -> send(1));
        button("cancel", "Cancel", margin + (third + 3) * 2, height - 30, third, true, () -> { cancel(); super.close(); });
    }
    private void button(String id, String label, int x, int y, int width, boolean active, Runnable action) {
        ButtonWidget widget = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, width, 20).build());
        widget.active = active; buttons.put(id, widget);
    }
    private boolean usable() { return !state.completed() && pending == null && ClientPlayNetworking.canSend(ActionConfirmationPayloads.Action.ID); }
    private void send(int action) {
        if (!usable()) return;
        pending = state.token(); pendingTicks = 0; status = "Waiting for the campaign server..."; clearAndInit();
        ClientPlayNetworking.send(new ActionConfirmationPayloads.Action(pending, action));
    }
    private void cancel() {
        if (state != null && ClientPlayNetworking.canSend(ActionConfirmationPayloads.Action.ID))
            ClientPlayNetworking.send(new ActionConfirmationPayloads.Action(state.token(), 2));
    }
    @Override public void tick() {
        if (pending != null && ++pendingTicks >= TIMEOUT_TICKS) {
            pending = null; pendingTicks = -1; status = "No response. Reopen the action to check its status."; clearAndInit();
        }
    }
    @Override public void removed() {
        if (!replacing) cancel();
        if (openScreen == this) openScreen = null;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFAA55);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
