package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.BookGiveForm;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.CoreMenuModel.View;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

/** A server-reviewed picker and confirmation for a deliberate, single lore-book grant. */
final class BookGiveScreen extends Screen {
    record Request(Action action, String bookId, String targetId, long openingToken) { }

    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private static final int PER_PAGE = 6;
    private final Screen returnScreen;
    private final BookGiveForm form;
    private final long openingToken;
    private final Consumer<Request> sent;
    private int page;
    private boolean stale;

    BookGiveScreen(Screen returnScreen, BookGiveForm form, long openingToken, Consumer<Request> sent) {
        super(Text.literal("Give book to player"));
        this.returnScreen = returnScreen;
        this.form = form;
        this.openingToken = openingToken;
        this.sent = sent;
    }

    @Override protected void init() {
        int w = Math.min(390, Math.max(210, width - 24));
        int x = (width - w) / 2;
        int y = Math.max(18, (height - 196) / 2);
        int bottom = y + 166;
        // A picker has no target yet, so its server form deliberately reports canGive=false.
        // It is still safe to choose one when the reviewed list is non-empty and has no refusal reason.
        boolean ready = !stale && (confirming() ? form.canGive() : !form.players().isEmpty() && form.reason().isBlank());
        if (confirming()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), ignored -> client.setScreen(returnScreen))
                    .dimensions(x, bottom, 78, 18).build());
            ButtonWidget give = addDrawableChild(ButtonWidget.builder(Text.literal("Give one copy"),
                            ignored -> send(Action.BOOK_GIVE, form.targetId()))
                    .dimensions(x + 82, bottom, w - 82, 18).build());
            give.active = ready;
            return;
        }
        int pages = pages();
        int start = page * PER_PAGE;
        List<Entry> players = form.players();
        for (int i = 0; i < PER_PAGE && start + i < players.size(); i++) {
            Entry player = players.get(start + i);
            ButtonWidget choose = addDrawableChild(ButtonWidget.builder(Text.literal(player.label()),
                            ignored -> send(Action.BOOK_GIVE_REVIEW, player.id()))
                    .dimensions(x, y + 55 + i * 18, w, 17).build());
            choose.active = ready;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), ignored -> client.setScreen(returnScreen))
                .dimensions(x, bottom, 78, 18).build());
        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), ignored -> { page--; clearAndInit(); })
                .dimensions(x + 82, bottom, 24, 18).build());
        previous.active = page > 0;
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), ignored -> { page++; clearAndInit(); })
                .dimensions(x + 110, bottom, 24, 18).build());
        next.active = page + 1 < pages;
    }

    void serverUpdated(View updated) {
        if (updated.token() != openingToken) {
            stale = true;
            clearAndInit();
        }
    }

    Screen parentMenu() { return returnScreen; }

    private boolean confirming() { return !form.targetId().isBlank(); }
    private int pages() { return Math.max(1, (form.players().size() + PER_PAGE - 1) / PER_PAGE); }

    private void send(Action action, String targetId) {
        boolean allowed = action == Action.BOOK_GIVE_REVIEW
                ? !form.players().isEmpty() && form.reason().isBlank()
                : form.canGive();
        if (stale || !allowed) return;
        // Return to the live parent before the request so the response is routed through its token state.
        client.setScreen(returnScreen);
        sent.accept(new Request(action, form.bookId(), targetId, openingToken));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = Math.min(390, Math.max(210, width - 24));
        int x = (width - w) / 2;
        int y = Math.max(18, (height - 196) / 2);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, 196);
        if (confirming()) renderConfirmation(context, x, y, w);
        else renderPicker(context, x, y, w);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPicker(DrawContext context, int x, int y, int w) {
        draw(context, "Give " + form.bookTitle(), x, y, w, 0xFFFFFFFF, 1);
        if (stale) {
            draw(context, "The Chronicle state changed. Close this window and review the book again.", x, y + 18, w, 0xFFFFCC66, 3);
        } else if (!form.reason().isBlank()) {
            draw(context, form.reason(), x, y + 18, w, 0xFFFFCC66, 3);
        } else if (form.players().isEmpty()) {
            draw(context, "No active participants can receive this book right now.", x, y + 18, w, 0xFFCCCCCC, 3);
        } else {
            draw(context, "Choose a participant.", x, y + 18, w, 0xFFCCCCCC, 1);
            draw(context, "Page " + (page + 1) + " / " + pages(), x + 140, y + 40, w - 140, 0xFFA0A0A0, 1);
        }
    }

    private void renderConfirmation(DrawContext context, int x, int y, int w) {
        draw(context, "Give " + form.bookTitle() + " to " + form.targetName() + "?", x, y, w, 0xFFFFFFFF, 2);
        draw(context, "One copy will be placed directly in their inventory.", x, y + 30, w, 0xFFCCCCCC, 2);
        draw(context, "If this book is unfound, this player becomes its first finder.", x, y + 52, w, 0xFFCCCCCC, 2);
        if (stale) draw(context, "The Chronicle state changed. Close this window and review the book again.", x, y + 80, w, 0xFFFFCC66, 3);
        else if (!form.reason().isBlank()) draw(context, form.reason(), x, y + 80, w, 0xFFFFCC66, 3);
    }

    private void draw(DrawContext context, String value, int x, int y, int w, int color, int maxLines) {
        int line = 0;
        for (var ordered : textRenderer.wrapLines(Text.literal(value), Math.max(1, w))) {
            if (line++ >= maxLines) break;
            StringBuilder plain = new StringBuilder();
            ordered.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
            context.drawTextWithShadow(textRenderer, Text.literal(plain.toString()), x, y + (line - 1) * 10, color);
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(returnScreen); }
}
