package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.RoleAssignmentForm;
import io.github.chronicle.core.menu.CoreMenuModel.RoleChoice;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

/** Server-reviewed self-choice or GM assignment confirmation for player roles. */
final class PlayerRoleChoiceScreen extends Screen {
    record Request(Action action, String playerId, String roleId, long token) { }
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private final Screen returnScreen;
    private final RoleAssignmentForm form;
    private final long token;
    private final Consumer<Request> sent;
    private int page;
    private boolean stale;

    PlayerRoleChoiceScreen(Screen returnScreen, RoleAssignmentForm form, long token, Consumer<Request> sent) {
        super(Text.literal("Player roles"));
        this.returnScreen = returnScreen; this.form = form; this.token = token; this.sent = sent;
    }

    @Override protected void init() {
        int w = Math.min(410, Math.max(230, width - 24)); int x = (width - w) / 2; int y = Math.max(18, (height - 224) / 2);
        if (confirming()) {
            button("Cancel", x, y + 184, 86, () -> client.setScreen(returnScreen), "Return without assigning a role");
            boolean active = !stale && (form.canChoose() || form.canAssign());
            ButtonWidget confirm = addDrawableChild(ButtonWidget.builder(Text.literal(form.pendingRoleId().equals("-")?"Clear role":form.canAssign() ? "Assign role" : "Choose role"), ignored -> send(form.canAssign() ? Action.ROLE_ASSIGN : Action.ROLE_CHOOSE, form.pendingRoleId()))
                    .dimensions(x + 92, y + 184, w - 92, 18).build());
            confirm.active = active;
            return;
        }
        List<RoleChoice> choices = form.choices(); int start = page * 6;
        for (int index = 0; index < 6 && start + index < choices.size(); index++) {
            RoleChoice choice = choices.get(start + index);
            String label = (choice.available() ? "" : "Unavailable: ") + choice.name() + " (" + choice.assigned() + " / " + choice.capacity() + ")";
            ButtonWidget select = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> send(form.canAssign() ? Action.ROLE_ASSIGN_REVIEW : Action.ROLE_CHOOSE, choice.id()))
                    .dimensions(x, y + 61 + index * 19, w, 18).build());
            select.active = !stale && choice.available();
        }
        button("Cancel", x, y + 184, 86, () -> client.setScreen(returnScreen), "Return without changing the role");
        button("<", x + 92, y + 184, 22, () -> { page--; clearAndInit(); }, "Earlier roles").active = page > 0;
        button(">", x + 118, y + 184, 22, () -> { page++; clearAndInit(); }, "More roles").active = start + 6 < choices.size();
    }

    void serverUpdated(boolean current) { if (!current) { stale = true; clearAndInit(); } }
    Screen parentMenu() { return returnScreen; }
    /** Only a server-created pending role is a confirmation. Existing assignments are not prompts. */
    private boolean confirming() { return !form.pendingRoleId().isBlank(); }
    private void send(Action action, String roleId) {
        if (stale || roleId.isBlank()) return;
        client.setScreen(returnScreen); sent.accept(new Request(action, form.playerId(), roleId, token));
    }
    private ButtonWidget button(String label, int x, int y, int w, Runnable action, String hint) {
        ButtonWidget value = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, Math.max(1, w), 18).build());
        value.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(hint))); return value;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(410, Math.max(230, width - 24)); int x = (width - w) / 2; int y = Math.max(18, (height - 224) / 2);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, 214);
        if (confirming()) {
            draw(context, form.pendingRoleId().equals("-") ? "Clear this player’s role?" : (form.canAssign() ? "Assign " : "Choose ") + form.roleName() + "?", x, y, w, 0xFFFFFFFF, 2);
            draw(context, "This changes future role policy. It does not move the player, grant a kit, or repeat books.", x, y + 29, w, 0xFFCCCCCC, 3);
            if (form.started()) draw(context, "This player has already started; first-entry grants will not replay.", x, y + 75, w, 0xFFFFCC66, 2);
        } else {
            draw(context, form.canAssign() ? "Assign player role" : "Choose my role", x, y, w, 0xFFFFFFFF, 1);
            draw(context, form.canAssign() ? "Choose a role for this player." : "Choose an available role before first entry.", x, y + 20, w, 0xFFCCCCCC, 2);
            if (stale) draw(context, "The Chronicle state changed. Close this window and review the current choices.", x, y + 42, w, 0xFFFFCC66, 3);
        }
        super.render(context, mouseX, mouseY, delta);
    }
    private void draw(DrawContext context, String text, int x, int y, int w, int color, int rows) {
        String remaining = text; for (int row = 0; row < rows && !remaining.isEmpty(); row++) { String line = textRenderer.trimToWidth(remaining, w); context.drawTextWithShadow(textRenderer, Text.literal(line), x, y + row * 10, color); remaining = remaining.substring(line.length()); }
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(returnScreen); }
}
