package io.github.chronicle.core.client;

import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.screen.OfferingScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The deliberately cryptic, slot-free player face of the authoritative Altar prayer. */
public final class OfferingScreen extends Screen implements ScreenHandlerProvider<OfferingScreenHandler> {
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private static final int TIMEOUT_TICKS = 200;
    private final OfferingScreenHandler handler;
    private OfferingPayloads.View state;
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private ButtonWidget previous;
    private ButtonWidget next;
    private int page;
    private int pendingTicks = -1;
    private boolean timedOut;
    private boolean closed;

    public OfferingScreen(OfferingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(title);
        this.handler = handler;
        this.state = handler.view();
    }

    public OfferingPayloads.View state() {
        return state;
    }

    public boolean pending() {
        return pendingTicks >= 0;
    }

    @Override
    public OfferingScreenHandler getScreenHandler() {
        return handler;
    }

    public static void register() {
        HandledScreens.register(OfferingScreenHandler.TYPE, OfferingScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(OfferingPayloads.View.ID,
                (view, context) -> context.client().execute(() -> receive(context.client(), view)));
    }

    private static void receive(MinecraftClient client, OfferingPayloads.View view) {
        if (!(client.currentScreen instanceof OfferingScreen screen) || view.syncId() != screen.handler.syncId) return;
        // Opening is supplied by Fabric's native handler construction, never by a later custom packet.
        if (view.opening()) return;
        UUID current = screen.state.token();
        if (!view.token().equals(current) && (view.parent() == null || !view.parent().equals(current))) return;
        screen.apply(view);
    }

    private void apply(OfferingPayloads.View view) {
        if (view.syncId() != handler.syncId) return;
        state = view;
        handler.view(view);
        pendingTicks = -1;
        timedOut = false;
        page = Math.clamp(page, 0, pages() - 1);
        updateWidgets();
        if (view.state() == OfferingPayloads.State.ANSWERED) close();
    }

    @Override
    protected void init() {
        buttons.clear();
        int left = (width - OfferingScreenHandler.WIDTH) / 2;
        int top = (height - OfferingScreenHandler.HEIGHT) / 2;
        button("choice0", "", left + 12, top + 48, 200, 20, () -> pray(0));
        button("choice1", "", left + 12, top + 74, 200, 20, () -> pray(1));
        button("close", "Close", left + 62, top + 104, 100, 20, this::close);
        previous = button("previous", "<", left + 12, top + 104, 20, 20, () -> { page--; updateWidgets(); });
        next = button("next", ">", left + 192, top + 104, 20, 20, () -> { page++; updateWidgets(); });
        updateWidgets();
    }

    private ButtonWidget button(String key, String label, int left, int top, int buttonWidth, int buttonHeight, Runnable action) {
        ButtonWidget widget = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(left, top, buttonWidth, buttonHeight).build());
        buttons.put(key, widget);
        return widget;
    }

    private int pages() {
        return Math.max(1, (state.choices().size() + 1) / 2);
    }

    private OfferingPayloads.Choice choice(int visible) {
        int index = page * 2 + visible;
        return index >= 0 && index < state.choices().size() ? state.choices().get(index) : null;
    }

    private void updateWidgets() {
        page = Math.clamp(page, 0, pages() - 1);
        boolean canPray = !pending() && !timedOut && state.state() == OfferingPayloads.State.READY
                && ClientPlayNetworking.canSend(OfferingPayloads.Action.ID);
        for (int visible = 0; visible < 2; visible++) {
            ButtonWidget widget = buttons.get("choice" + visible);
            OfferingPayloads.Choice choice = choice(visible);
            widget.visible = choice != null;
            if (choice != null) {
                widget.setMessage(Text.literal(textRenderer.trimToWidth(choice.label(), 188)));
                widget.active = canPray && choice.available() && !choice.complete();
            }
        }
        boolean paged = pages() > 1;
        previous.visible = next.visible = paged;
        previous.active = paged && !pending() && page > 0;
        next.active = paged && !pending() && page + 1 < pages();
    }

    private void pray(int visible) {
        OfferingPayloads.Choice choice = choice(visible);
        if (choice == null || !choice.available() || choice.complete() || pending() || timedOut
                || state.state() != OfferingPayloads.State.READY) return;
        send(1, page * 2 + visible);
    }

    private void send(int action, int selected) {
        if (pending() || !ClientPlayNetworking.canSend(OfferingPayloads.Action.ID)) return;
        pendingTicks = 0;
        timedOut = false;
        ClientPlayNetworking.send(new OfferingPayloads.Action(state.token(), action, selected));
        updateWidgets();
    }

    /** Native smoke hook for the bounded controls; no coordinate or arbitrary action enters the protocol. */
    public boolean triggerForTest(String key) {
        if ("refresh".equals(key)) {
            if (pending() || state.state() != OfferingPayloads.State.EXPIRED) return false;
            send(2, 0);
            return true;
        }
        ButtonWidget button = buttons.get(key);
        if (button == null || !button.visible || !button.active) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
        return true;
    }

    @Override
    public void tick() {
        if (pendingTicks >= 0 && ++pendingTicks >= TIMEOUT_TICKS) {
            pendingTicks = -1;
            timedOut = true;
            updateWidgets();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Screen.renderWithTooltip already draws the native background (and its blur) in 1.21.10.
        int left = (width - OfferingScreenHandler.WIDTH) / 2;
        int top = (height - OfferingScreenHandler.HEIGHT) / 2;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, left, top,
                OfferingScreenHandler.WIDTH, OfferingScreenHandler.HEIGHT);
        context.drawCenteredTextWithShadow(textRenderer, state.title(), left + 112, top + 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, stateLine(), left + 112, top + 32, 0xFFFFFFFF);
        if (pages() > 1) {
            context.drawTextWithShadow(textRenderer, (page + 1) + "/" + pages(), left + 34, top + 110, 0xFFFFFFFF);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private String stateLine() {
        if (pending()) return "The altar listens...";
        if (timedOut) return "The altar is quiet.";
        return switch (state.state()) {
            case READY -> state.choices().stream().anyMatch(OfferingPayloads.Choice::complete) ? "One answer has faded." : "The altar listens.";
            case UNAVAILABLE -> "The altar is quiet.";
            case EXHAUSTED -> allComplete() ? "No answer comes." : "One answer has faded.";
            case EXPIRED -> "The altar falls silent.";
            case ANSWERED -> "The altar remembers.";
        };
    }

    private boolean allComplete() {
        for (OfferingPayloads.Choice choice : state.choices()) if (!choice.complete()) return false;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == InputUtil.GLFW_KEY_R && state.state() == OfferingPayloads.State.EXPIRED && !pending()) {
            send(2, 0);
            return true;
        }
        return super.keyPressed(input);
    }

    private void revoke(boolean nativeClose) {
        if(closed)return;
        closed=true;
        if(ClientPlayNetworking.canSend(OfferingPayloads.Action.ID))
            ClientPlayNetworking.send(new OfferingPayloads.Action(state.token(),3,0));
        // An old screen's removal must never close an unrelated newly opened native handler.
        if(nativeClose&&client!=null&&client.player!=null&&client.player.currentScreenHandler==handler)
            client.player.closeHandledScreen();
    }
    @Override public void close() { revoke(true);super.close(); }
    @Override public void removed() { revoke(false);super.removed(); }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
