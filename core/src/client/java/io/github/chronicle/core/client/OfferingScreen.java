package io.github.chronicle.core.client;

import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.screen.OfferingScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Compact real-inventory Altar workstation; all bargain authority remains server-side. */
public final class OfferingScreen extends HandledScreen<OfferingScreenHandler> {
    private static final int TIMEOUT_TICKS = 200;
    private OfferingPayloads.View state;
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private ButtonWidget previous, next, accept, refresh;
    private int page, pendingTicks = -1;
    private UUID pendingToken;
    private boolean timedOut, closed;
    private String localStatus;

    public OfferingScreen(OfferingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = OfferingScreenHandler.WIDTH;
        backgroundHeight = OfferingScreenHandler.HEIGHT;
        state = handler.view();
        localStatus = state.status();
        page = state.selected() / 2;
    }

    public OfferingPayloads.View state() { return state; }
    public boolean pending() { return pendingTicks >= 0; }

    /** Native smoke hook: choice0/choice1 are the visible page slots; confirm and refresh are fixed controls. */
    public boolean triggerForTest(String key) {
        ButtonWidget button = buttons.get(key);
        if (button == null || !button.active || !button.visible) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
        return true;
    }

    public static void register() {
        HandledScreens.register(OfferingScreenHandler.TYPE, OfferingScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(OfferingPayloads.View.ID,
                (view, context) -> context.client().execute(() -> receive(context.client(), view)));
    }

    private static void receive(MinecraftClient client, OfferingPayloads.View view) {
        if (!(client.currentScreen instanceof OfferingScreen screen) || view.syncId() != screen.handler.syncId) return;
        UUID current = screen.state.token();
        // Updates can only advance the currently open native handler's nonce chain.
        if (!view.token().equals(current) && (view.parent() == null || !view.parent().equals(current))) return;
        screen.apply(view);
    }

    private void apply(OfferingPayloads.View view) {
        if (view.syncId() != handler.syncId) return;
        boolean identical = view.equals(state);
        state = view;
        handler.view(view);
        pendingTicks = -1;
        pendingToken = null;
        timedOut = false;
        localStatus = view.status();
        page = view.selected() / 2;
        if (!identical) updateWidgets();
        else updateEnabled();
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        previous = button("previous", "<", x + 12, y + 18, 9, 9, false, () -> { page--; updateWidgets(); });
        next = button("next", ">", x + 279, y + 18, 9, 9, false, () -> { page++; updateWidgets(); });
        button("choice0", "", x + 8, y + 29, 140, 24, false, () -> choose(0));
        button("choice1", "", x + 152, y + 29, 140, 24, false, () -> choose(1));
        accept = button("confirm", "Make permanent bargain", x + 52, y + 108, 150, 20, false, () -> send(1, state.selected()));
        refresh = button("refresh", "Refresh", x + 208, y + 108, 80, 20, false, () -> send(2, state.selected()));
        updateWidgets();
    }

    private ButtonWidget button(String key, String label, int left, int top, int width, int height, boolean active, Runnable action) {
        ButtonWidget widget = addDrawableChild(key.startsWith("choice")
                ? new CardButton(left,top,width,height,ignored->action.run())
                : ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(left, top, width, height).build());
        widget.active = active;
        buttons.put(key, widget);
        return widget;
    }

    private int pages() { return Math.max(1, (state.choices().size() + 1) / 2); }
    private OfferingPayloads.Choice choice(int visible) {
        int index = page * 2 + visible;
        return index >= 0 && index < state.choices().size() ? state.choices().get(index) : null;
    }

    private void choose(int visible) {
        OfferingPayloads.Choice choice = choice(visible);
        if (choice == null || page * 2 + visible == state.selected()) return;
        send(0, page * 2 + visible);
    }

    private void updateWidgets() {
        page = Math.clamp(page, 0, pages() - 1);
        for (int visible = 0; visible < 2; visible++) {
            ButtonWidget widget = buttons.get("choice" + visible);
            OfferingPayloads.Choice choice = choice(visible);
            widget.visible = choice != null;
            if (choice != null) {
                String label = choice.label() + " " + choice.count() + "/" + choice.limit();
                widget.setMessage(Text.literal(textRenderer.trimToWidth(label, 132)));
                widget.setTooltip(Tooltip.of(Text.literal(tooltip(choice))));
                widget.active = canChoose(choice);
            }
        }
        previous.visible = next.visible = pages() > 1;
        previous.active = !pending() && page > 0;
        next.active = !pending() && page + 1 < pages();
        updateEnabled();
    }

    private void updateEnabled() {
        OfferingPayloads.Choice selected = state.choices().get(state.selected());
        accept.active = canAccept(selected);
        refresh.active = canRefresh();
        for (int visible = 0; visible < 2; visible++) {
            ButtonWidget widget = buttons.get("choice" + visible);
            OfferingPayloads.Choice choice = choice(visible);
            if (choice != null) widget.active = canChoose(choice);
        }
        if (previous != null) previous.active = !pending() && page > 0;
        if (next != null) next.active = !pending() && page + 1 < pages();
    }

    private boolean canChoose(OfferingPayloads.Choice choice) {
        return !pending() && !timedOut && state.stationReady() && !state.completed()
                && choice.count() < choice.limit() && ClientPlayNetworking.canSend(OfferingPayloads.Action.ID);
    }

    private boolean canAccept(OfferingPayloads.Choice choice) {
        return canChoose(choice) && choice.after() != null && choice.problem().isEmpty() && termsFit(choice);
    }

    private boolean termsFit(OfferingPayloads.Choice choice) {
        return textRenderer.getWidth("Cost: " + choice.cost()) <= 276
                && textRenderer.getWidth("Bonus: " + choice.bonus()) <= 276;
    }

    private boolean canRefresh() {
        return !pending() && ClientPlayNetworking.canSend(OfferingPayloads.Action.ID);
    }

    private void send(int action, int selected) {
        if (pending() || !ClientPlayNetworking.canSend(OfferingPayloads.Action.ID)) return;
        if (action == 0 && (selected < 0 || selected >= state.choices().size() || !canChoose(state.choices().get(selected)))) return;
        if (action == 1 && !canAccept(state.choices().get(state.selected()))) return;
        pendingToken = state.token();
        pendingTicks = 0;
        timedOut = false;
        localStatus = "Waiting for the Altar...";
        ClientPlayNetworking.send(new OfferingPayloads.Action(pendingToken, action, selected));
        updateEnabled();
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (pendingTicks >= 0 && ++pendingTicks >= TIMEOUT_TICKS) {
            pendingTicks = -1;
            pendingToken = null;
            timedOut = true;
            localStatus = "No response. Check your count, then Refresh.";
            updateEnabled();
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float deltaTicks, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFFC6C6C6);
        context.fill(x, y, x + backgroundWidth, y + 2, 0xFFF0F0F0);
        context.fill(x, y, x + 2, y + backgroundHeight, 0xFFF0F0F0);
        context.fill(x, y + backgroundHeight - 6, x + backgroundWidth, y + backgroundHeight, 0xFF777777);
        context.fill(x + 8, y + 29, x + 148, y + 53, 0xFFAAAAAA);
        context.fill(x + 152, y + 29, x + 292, y + 53, 0xFFAAAAAA);
        for (Slot slot : handler.slots) {
            int slotX = x + slot.x - 1, slotY = y + slot.y - 1;
            context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF555555);
            context.fill(slotX + 2, slotY + 2, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
        int selected = state.selected() - page * 2;
        if (selected >= 0 && selected < 2) {
            int cardX = selected == 0 ? x + 8 : x + 152;
            context.fill(cardX, y + 29, cardX + 140, y + 30, 0xFF2D7668);
            context.fill(cardX, y + 52, cardX + 140, y + 53, 0xFF2D7668);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawCenteredTextWithShadow(textRenderer, title, backgroundWidth / 2, 6, 0xFF3D3441);
        String station = pending() ? (pages() > 1 ? "Waiting..." : "Waiting for Altar...") : conciseStationText();
        context.drawTextWithShadow(textRenderer, Text.literal(station), pages() > 1 ? 24 : 12, 18,
                pending() ? 0xFFAA6633 : state.stationReady() ? 0xFF267C59 : 0xFFAA4422);
        context.drawTextWithShadow(textRenderer, Text.literal("*"), 4, 18, state.stationReady() ? 0xFF267C59 : 0xFFAA4422);
        if (pages() > 1) context.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + "/" + pages()), 150, 18, 0xFF555555);
        OfferingPayloads.Choice selected = state.choices().get(state.selected());
        if (termsFit(selected)) {
            context.drawTextWithShadow(textRenderer, Text.literal("Cost: " + selected.cost()), 12, 56, 0xFF333333);
            context.drawTextWithShadow(textRenderer, Text.literal("Bonus: " + selected.bonus()), 12, 66, 0xFF333333);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("Terms too long for this workstation."), 12, 56, 0xFFAA4422);
            context.drawTextWithShadow(textRenderer, Text.literal("See full terms on the bargain card."), 12, 66, 0xFFAA4422);
        }
        OfferingPayloads.Stats before = selected.before(), after = selected.after();
        if (after != null) {
            context.drawTextWithShadow(textRenderer, Text.literal("Health: " + hearts(before.health()) + "->" + hearts(after.health())
                    + "h  Food: " + before.food() + "->" + after.food()), 12, 76, 0xFF333333);
            context.drawTextWithShadow(textRenderer, Text.literal("Speed: " + percent(before.speed()) + "% -> " + percent(after.speed()) + "%"), 12, 86, 0xFF333333);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("Health: " + hearts(before.health()) + "h  Food: " + before.food() + " (current)"), 12, 76, 0xFF333333);
            context.drawTextWithShadow(textRenderer, Text.literal("Speed: " + percent(before.speed()) + "% (current)"), 12, 86, 0xFF333333);
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Permanent; no reversal. Ends with network."), 150, 96, 0xFF8A2424);
        context.drawTextWithShadow(textRenderer, Text.literal("Inventory"), 8, 130, 0xFF333333);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        drawSelectedCardBorder(context);
        drawMouseoverTooltip(context,mouseX,mouseY);
        if (mouseX >= x + 12 && mouseX < x + 288 && mouseY >= y + 18 && mouseY < y + 27) {
            String reason = pending() ? localStatus : fullStationText();
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(reason), 240), mouseX, mouseY);
        }
    }

    /** Native button visuals/focus/narration, with two deliberately positioned text rows. */
    private static final class CardButton extends ButtonWidget {
        CardButton(int left,int top,int width,int height,PressAction action) {
            super(left,top,width,height,Text.empty(),action,DEFAULT_NARRATION_SUPPLIER);
        }
        @Override public void drawMessage(DrawContext context,net.minecraft.client.font.TextRenderer renderer,int color) {
            context.drawCenteredTextWithShadow(renderer,getMessage(),getX()+getWidth()/2,getY()+4,color);
            context.drawCenteredTextWithShadow(renderer,Text.literal("Sacrifice -> Bonus"),getX()+getWidth()/2,getY()+14,
                    active?0xFFDDDDDD:0xFF999999);
        }
    }

    private void drawSelectedCardBorder(DrawContext context) {
        int selected = state.selected() - page * 2;
        if (selected < 0 || selected > 1) return;
        int cardX = selected == 0 ? x + 8 : x + 152;
        context.fill(cardX, y + 29, cardX + 140, y + 30, 0xFF2D7668);
        context.fill(cardX, y + 52, cardX + 140, y + 53, 0xFF2D7668);
        context.fill(cardX, y + 29, cardX + 1, y + 53, 0xFF2D7668);
        context.fill(cardX + 139, y + 29, cardX + 140, y + 53, 0xFF2D7668);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == InputUtil.GLFW_KEY_LEFT && chooseKeyboard(0)) return true;
        if (input.key() == InputUtil.GLFW_KEY_RIGHT && chooseKeyboard(1)) return true;
        if (input.key() == InputUtil.GLFW_KEY_R && canRefresh()) { send(2, state.selected()); return true; }
        if ((input.key() == InputUtil.GLFW_KEY_ENTER || input.key() == InputUtil.GLFW_KEY_KP_ENTER)
                && accept != null && accept.isFocused() && accept.active) {
            accept.onPress(new net.minecraft.client.input.MouseInput(0, 0));
            return true;
        }
        return super.keyPressed(input);
    }

    private boolean chooseKeyboard(int visible) {
        OfferingPayloads.Choice choice = choice(visible);
        if (choice == null || !canChoose(choice) || page * 2 + visible == state.selected()) return false;
        send(0, page * 2 + visible);
        return true;
    }

    private String conciseStationText() {
        boolean paged = pages() > 1;
        if (state.stationReady() && allChoicesComplete()) return paged ? "Complete" : "All Altar bargains complete.";
        if (state.stationReady() && state.completed()) return paged ? "Expired - Refresh" : "Offer expired or unavailable - Refresh";
        if (state.stationReady()) return paged ? "Ready" : "Network power: ready";
        String problem = state.stationProblem();
        int available = paged ? 110 : 276;
        if (!problem.isEmpty() && textRenderer.getWidth(problem) <= available) return problem;
        return paged ? "Unavailable - hover" : "Altar unavailable - hover for details";
    }

    private String fullStationText() {
        OfferingPayloads.Choice selected = state.choices().get(state.selected());
        StringBuilder detail = new StringBuilder();
        appendDetail(detail, state.stationProblem());
        appendDetail(detail, state.status());
        appendDetail(detail, selected.problem());
        if (detail.isEmpty()) detail.append(state.stationReady() ? "Network power: ready" : "Altar unavailable");
        return detail.toString();
    }

    private boolean allChoicesComplete() {
        for (OfferingPayloads.Choice choice : state.choices()) if (choice.count() < choice.limit()) return false;
        return true;
    }

    private static void appendDetail(StringBuilder detail, String value) {
        if (value == null || value.isBlank()) return;
        if (!detail.isEmpty()) detail.append('\n');
        detail.append(value);
    }

    private String tooltip(OfferingPayloads.Choice choice) {
        String unavailable = choice.problem().isEmpty() ? "" : "\nUnavailable: " + choice.problem();
        return choice.description() + "\nCost: " + choice.cost() + "\nBonus: " + choice.bonus()
                + "\nSurvives death and reconnecting. No reversal. Both end with the network." + unavailable;
    }

    private String trim(String value, int pixels) { return textRenderer.trimToWidth(value == null ? "" : value, pixels); }
    private String hearts(double health) { return String.format(java.util.Locale.ROOT, "%.1f", health / 2.0); }
    private String percent(double speed) { return String.format(java.util.Locale.ROOT, "%.0f", speed * 1000.0); }

    @Override
    public void close() {
        revoke();
        super.close();
    }

    @Override
    public void removed() {
        revoke();
        super.removed();
    }

    private void revoke() {
        if (closed || state == null || !ClientPlayNetworking.canSend(OfferingPayloads.Action.ID)) return;
        closed = true;
        ClientPlayNetworking.send(new OfferingPayloads.Action(state.token(), 3, state.selected()));
    }

    @Override
    public boolean shouldPause() { return false; }
}
