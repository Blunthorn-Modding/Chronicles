package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ItemEnchantingPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;

/** Compact, server-authoritative workshop view for one held target and offer. */
public final class ItemEnchantingScreen extends Screen {
    private static final int PENDING_TIMEOUT_TICKS = 20 * 10;
    private ItemEnchantingPayloads.View state;
    private final Map<String, ButtonWidget> testButtons = new LinkedHashMap<>();
    private ScrollableTextWidget recipesWidget;
    private ScrollableTextWidget statusWidget;
    private String localStatus;
    private int pendingTicks = -1;
    private java.util.UUID requestToken;
    private java.util.UUID timedOutToken;

    public ItemEnchantingScreen(ItemEnchantingPayloads.View state) {
        super(Text.literal(state.title()));
        this.state = state;
        this.localStatus = state.status();
    }

    public ItemEnchantingPayloads.View state() { return state; }
    public boolean pending() { return pendingTicks >= 0; }

    /** Development/native smoke hook matching the other Core screens. */
    public boolean triggerForTest(String label) {
        ButtonWidget button = testButtons.get(label);
        if (button == null || !button.active || !button.visible) return false;
        button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
        return true;
    }

    /** Accepts the authoritative reply for the current offer without reopening the screen. */
    public void accept(ItemEnchantingPayloads.View updated) {
        if (!pending() && timedOutToken != null && timedOutToken.equals(updated.token())) return;
        if (pending()) {
            // A refresh creates a new offer token; a stale completed reply from
            // the previous offer must never replace that pending refresh.
            if (updated.completed() && !updated.token().equals(requestToken)) return;
        } else if (!updated.token().equals(state.token())) {
            return;
        }
        state = updated;
        localStatus = updated.status();
        pendingTicks = -1;
        requestToken = null;
        timedOutToken = null;
        clearAndInit();
    }

    @Override
    protected void init() {
        testButtons.clear();
        int margin = 12;
        int contentWidth = Math.max(160, width - margin * 2);
        int y = 38;
        drawButton("Enchant", margin, height - 28, Math.max(72, contentWidth / 3 - 3), canEnchant(), () -> send(0));
        drawButton("Refresh", margin + contentWidth / 3, height - 28, Math.max(72, contentWidth / 3 - 3), canRefresh(), () -> send(1));
        drawButton("Close", margin + contentWidth * 2 / 3, height - 28, Math.max(72, contentWidth / 3), true, this::close);
        statusWidget=addDrawableChild(new ScrollableTextWidget(margin,102,contentWidth,34,Text.literal(localStatus),textRenderer).textColor(0xFFCCCCCC));
        int recipeTop = 154;
        recipesWidget = addDrawableChild(new ScrollableTextWidget(margin, recipeTop, contentWidth,
                Math.max(26, height - recipeTop - 38), Text.literal(state.recipes()), textRenderer).textColor(0xFFD0D0D0));
    }

    private void drawButton(String label, int x, int y, int buttonWidth, boolean active, Runnable action) {
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(x, y, buttonWidth, 20).build());
        button.active = active;
        testButtons.put(label, button);
    }

    private boolean canEnchant() {
        return !state.completed() && timedOutToken == null && state.available() && !pending() && ClientPlayNetworking.canSend(ItemEnchantingPayloads.Action.ID);
    }

    private boolean canRefresh() {
        return !state.completed() && timedOutToken == null && !pending() && ClientPlayNetworking.canSend(ItemEnchantingPayloads.Action.ID);
    }

    private void send(int action) {
        if (pending() || state.completed() || !ClientPlayNetworking.canSend(ItemEnchantingPayloads.Action.ID)) return;
        requestToken = state.token();
        timedOutToken = null;
        ClientPlayNetworking.send(new ItemEnchantingPayloads.Action(requestToken, action));
        pendingTicks = 0;
        localStatus = "Waiting for the workshop server…";
        updateButtons();
    }

    private void updateButtons() {
        if(statusWidget!=null)statusWidget.setMessage(Text.literal(localStatus));
        ButtonWidget enchant = testButtons.get("Enchant");
        ButtonWidget refresh = testButtons.get("Refresh");
        if (enchant != null) enchant.active = canEnchant();
        if (refresh != null) refresh.active = canRefresh();
    }

    @Override
    public void tick() {
        if (pendingTicks >= 0 && ++pendingTicks >= PENDING_TIMEOUT_TICKS) {
            pendingTicks = -1;
            timedOutToken = requestToken;
            requestToken = null;
            localStatus = "No response from the workshop. Reopen the station to try again.";
            updateButtons();
        }
    }

    @Override
    public void close() {
        if (ClientPlayNetworking.canSend(ItemEnchantingPayloads.Action.ID) && state != null) {
            ClientPlayNetworking.send(new ItemEnchantingPayloads.Action(state.token(), 2));
        }
        ItemEnchantingClient.closed(this);
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int margin = 12;
        int contentWidth = Math.max(160, width - margin * 2);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
        int y = 38;
        drawField(context, "Main hand", state.target(), margin, y, contentWidth);
        drawField(context, "Offhand", state.ingredient(), margin, y + 20, contentWidth);
        drawField(context, "Result", state.result(), margin, y + 40, contentWidth);
        context.drawTextWithShadow(textRenderer,Text.literal("Available recipes"),margin,142,0xFFA0A0A0);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawField(DrawContext context, String label, String value, int x, int y, int width) {
        context.drawTextWithShadow(textRenderer, Text.literal(label + ":"), x, y, 0xFFA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(value, Math.max(1, width - 58))), x + 58, y, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() { return false; }
}
