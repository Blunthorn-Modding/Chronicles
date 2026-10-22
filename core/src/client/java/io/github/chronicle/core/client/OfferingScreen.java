package io.github.chronicle.core.client;

import io.github.chronicle.core.network.OfferingPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read the complete permanent trade, explicitly consent, then confirm against a one-use offer. */
public final class OfferingScreen extends Screen {
    private OfferingPayloads.View state;
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private boolean consent;
    private int pending = -1, firstChoice;
    private String notice;
    private boolean timedOut;
    private boolean replacedByServer, closed;
    public OfferingScreen(OfferingPayloads.View state) { super(Text.literal(state.title())); this.state = state; notice = state.status(); }
    public OfferingPayloads.View state() { return state; }
    public boolean triggerForTest(String key) {
        ButtonWidget button = buttons.get(key);
        if (button == null || !button.active) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0)); return true;
    }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OfferingPayloads.View.ID, (view, context) -> context.client().execute(() -> {
            MinecraftClient client = context.client();
            if (client.currentScreen instanceof OfferingScreen screen) {
                if (view.opening() && !view.token().equals(screen.state.token())) {
                    screen.replacedByServer=true;client.setScreen(new OfferingScreen(view));
                }
                else screen.accept(view);
            } else if (view.opening() && !view.completed()) client.setScreen(new OfferingScreen(view));
        }));
    }
    private void accept(OfferingPayloads.View view) {
        if (timedOut || (pending < 0 && !view.token().equals(state.token()))) return;
        state = view; notice = view.status(); pending = -1; consent = false;
        clearAndInit();
    }
    @Override protected void init() {
        buttons.clear();
        int left = 12, leftWidth = Math.min(110, width / 3), gap = 8, right = left + leftWidth + gap;
        addDrawableChild(new ScrollableTextWidget(left, 32, width - 24, 32, Text.literal(notice), textRenderer).textColor(0xFFCCCCCC));
        int top = 72, bodyHeight = Math.max(32, height - top - 82);
        int visible = Math.max(1, (bodyHeight - 22) / 22);
        firstChoice = Math.clamp(firstChoice, 0, Math.max(0, state.choices().size() - visible));
        for (int index = firstChoice; index < Math.min(state.choices().size(), firstChoice + visible); index++) {
            var choice = state.choices().get(index); int selected = index;
            String suffix=" "+choice.count()+"/"+choice.limit();
            String label=textRenderer.trimToWidth((index == state.selected() ? "> " : "")+choice.label(),Math.max(1,leftWidth-8-textRenderer.getWidth(suffix)))+suffix;
            button("choice" + index, label, left, top + (index - firstChoice) * 22, leftWidth,
                    usable() && index != state.selected(), () -> send(0, selected));
        }
        if (state.choices().size() > visible) {
            button("previous", "<", left, top + bodyHeight - 20, leftWidth / 2 - 2, usable() && firstChoice > 0, () -> { firstChoice--; clearAndInit(); });
            button("next", ">", left + leftWidth / 2, top + bodyHeight - 20, leftWidth / 2, usable() && firstChoice + visible < state.choices().size(), () -> { firstChoice++; clearAndInit(); });
        }
        var choice = state.choices().get(state.selected());
        String details = choice.label() + " (" + choice.count() + "/" + choice.limit() + ")\n\n" + choice.description()
                + "\n\nResult: " + choice.result() + "\n\nSurvives death and reconnecting. No player reversal. Campaign eradication removes both sides."
                + (choice.problem().isEmpty() ? "" : "\n\nUnavailable: " + choice.problem());
        addDrawableChild(new ScrollableTextWidget(right, top, Math.max(80, width - right - 12), bodyHeight,
                Text.literal(details), textRenderer).textColor(0xFFFFFFFF));
        button("consent", consent ? "[x] I accept the permanent trade" : "[ ] I accept the permanent trade", left, height - 68, width - 24,
                usable() && choice.problem().isEmpty(), () -> { consent = !consent; clearAndInit(); });
        int buttonWidth = (width - 30) / 3;
        button("confirm", "Accept bargain", left, height - 28, buttonWidth, usable() && consent && choice.problem().isEmpty(), () -> send(1, state.selected()));
        button("refresh", "Refresh", left + buttonWidth + 3, height - 28, buttonWidth, usable(), () -> send(2, state.selected()));
        button("close", "Close", left + (buttonWidth + 3) * 2, height - 28, buttonWidth, true, this::close);
    }
    private boolean usable() { return pending < 0 && !timedOut && !state.completed() && ClientPlayNetworking.canSend(OfferingPayloads.Action.ID); }
    private void button(String key, String label, int x, int y, int width, boolean active, Runnable action) {
        ButtonWidget widget = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, width, 20).build());
        widget.active = active; buttons.put(key, widget);
    }
    private void send(int action, int selected) {
        if (!usable()) return;
        ClientPlayNetworking.send(new OfferingPayloads.Action(state.token(), action, selected));
        pending = 0; consent = false; notice = "Waiting for the Altar server..."; clearAndInit();
    }
    @Override public void tick() {
        if (pending >= 0 && ++pending >= 200) {
            pending = -1; timedOut = true; consent = false;
            notice = "No response. Reopen the Altar to check your purchase count before trying again."; clearAndInit();
        }
    }
    @Override public void close() {
        closed=true;
        if (ClientPlayNetworking.canSend(OfferingPayloads.Action.ID)) ClientPlayNetworking.send(new OfferingPayloads.Action(state.token(), 3, state.selected()));
        super.close();
    }
    @Override public void removed() {
        // Replacing this screen through Escape/another UI also revokes a pending refreshed successor.
        if (!closed && !replacedByServer && client != null && ClientPlayNetworking.canSend(OfferingPayloads.Action.ID))
            ClientPlayNetworking.send(new OfferingPayloads.Action(state.token(), 3, state.selected()));
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
