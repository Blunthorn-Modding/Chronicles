package io.github.chronicle.core.client;

import io.github.chronicle.core.network.DeparturePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Dedicated stacked departure confirmation. The final screen keeps the first review beneath it. */
public final class DepartureConfirmScreen extends Screen {
    private static final int TIMEOUT_TICKS = 200;

    private DeparturePayloads.View state;
    private final DepartureConfirmScreen firstReview;
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private String status;
    private UUID pending;
    private int pendingTicks = -1;

    DepartureConfirmScreen(DeparturePayloads.View state, DepartureConfirmScreen firstReview) {
        super(Text.literal(state.title()));
        this.state = state;
        this.firstReview = firstReview;
        this.status = state.status();
    }

    public DeparturePayloads.View state() { return state; }

    public boolean triggerForTest(String label) {
        ButtonWidget button = buttons.get(label);
        if (button == null || !button.active || !button.visible) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
        return true;
    }

    static DepartureConfirmScreen first(DeparturePayloads.View state) {
        return new DepartureConfirmScreen(state, null);
    }

    static DepartureConfirmScreen finalReview(DeparturePayloads.View state, DepartureConfirmScreen first) {
        return new DepartureConfirmScreen(state, first);
    }

    void accept(DeparturePayloads.View updated) {
        if (!updated.token().equals(state.token())) return;
        state = updated;
        status = updated.status();
        pending = null;
        pendingTicks = -1;
        clearAndInit();
    }

    @Override protected void init() {
        buttons.clear();
        int panelWidth = Math.min(state.stage()==2?520:560, Math.max(220, width - 32));
        int panelHeight = Math.min(state.stage()==2?280:300, Math.max(180, height - 32));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        addDrawableChild(new ScrollableTextWidget(left + 12, top + 38, panelWidth - 24,
                Math.max(54, panelHeight - 116), Text.literal(state.details()), textRenderer).textColor(0xFFFFFFFF));
        addDrawableChild(new ScrollableTextWidget(left + 12, top + panelHeight - 70, panelWidth - 24,
                28, Text.literal(status), textRenderer).textColor(0xFFCCCCCC));

        int buttonWidth = Math.max(90, (panelWidth - 27) / 2);
        int buttonY = top + panelHeight - 30;
        String cancelLabel = state.completed() ? "Close" : state.stage() == 2 ? "Back" : "Cancel";
        ButtonWidget cancel = button(cancelLabel, left + 12, buttonY, buttonWidth, true, this::close);
        button(state.acceptLabel(), left + panelWidth - 12 - buttonWidth, buttonY, buttonWidth,
                usable() && state.available(), () -> send(DeparturePayloads.CONTINUE_OR_CONFIRM));
        setInitialFocus(cancel);
    }

    private ButtonWidget button(String label, int x, int y, int width, boolean active, Runnable action) {
        ButtonWidget widget = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(x, y, width, 20).build());
        widget.active = active;
        buttons.put(label, widget);
        return widget;
    }

    private boolean usable() {
        return !state.completed() && pending == null && ClientPlayNetworking.canSend(DeparturePayloads.Action.ID);
    }

    private void send(int action) {
        if (!usable()) return;
        pending = state.token();
        pendingTicks = 0;
        status = "Waiting for the campaign server...";
        clearAndInit();
        ClientPlayNetworking.send(new DeparturePayloads.Action(pending, action));
    }

    @Override public void close() {
        if (state.completed()) {
            client.setScreen(null);
            return;
        }
        if (!usable()) return;
        if (state.stage() == 2) send(DeparturePayloads.BACK);
        else {
            ClientPlayNetworking.send(new DeparturePayloads.Action(state.token(), DeparturePayloads.CANCEL));
            client.setScreen(null);
        }
    }

    @Override public void tick() {
        if (pending != null && ++pendingTicks >= TIMEOUT_TICKS) {
            pending = null;
            pendingTicks = -1;
            status = "No response. Reopen the departure to review it again.";
            clearAndInit();
        }
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (state.stage() == 2 && firstReview != null) {
            firstReview.render(context, -10_000, -10_000, delta);
            context.fill(0, 0, width, height, 0x99000000);
        }
        int panelWidth = Math.min(state.stage()==2?520:560, Math.max(220, width - 32));
        int panelHeight = Math.min(state.stage()==2?280:300, Math.max(180, height - 32));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),left,top,panelWidth,panelHeight);
        int border=state.stage()==2?0xFFFF5555:0xFFFFAA55;
        context.fill(left,top,left+panelWidth,top+1,border);context.fill(left,top+panelHeight-1,left+panelWidth,top+panelHeight,border);context.fill(left,top,left+1,top+panelHeight,border);context.fill(left+panelWidth-1,top,left+panelWidth,top+panelHeight,border);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 15,
                state.stage() == 2 ? 0xFFFF7777 : 0xFFFFCC66);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
