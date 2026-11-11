package io.github.chronicle.core.client;

import io.github.chronicle.core.books.BookDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Local, unsaved draft editor for a named book.  The menu remains the owner of
 * transport; this screen only returns a validated whole {@link BookDefinition}.
 */
final class BookEditorScreen extends Screen {
    enum Mode { DETAILS, PAGES, RULE }

    private final Screen returnScreen;
    private final Mode mode;
    private final boolean creating;
    private final Consumer<BookDefinition> saved;
    private BookDefinition draft;
    private List<String> pages;
    private BookDefinition.Rule rule;
    private String titleDraft;
    private String authorDraft;
    private String descriptionDraft;
    private TextFieldWidget title;
    private TextFieldWidget author;
    private TextFieldWidget description;
    private EditBoxWidget pageText;
    private int page;
    private int visiblePage = -1;
    private String problem = "";
    private boolean removing;
    private boolean confirmingRule;
    private boolean staleRejected;

    BookEditorScreen(Screen returnScreen, BookDefinition draft, Mode mode, boolean creating,
                     Consumer<BookDefinition> saved) {
        super(Text.literal("Books & Lore"));
        this.returnScreen = returnScreen;
        this.draft = draft;
        this.pages = new ArrayList<>(draft.pages());
        this.rule = draft.rule();
        this.titleDraft = draft.title();
        this.authorDraft = draft.author();
        this.descriptionDraft = draft.description();
        this.mode = mode;
        this.creating = creating;
        this.saved = saved;
    }

    @Override protected void init() {
        retainVisibleInputs();
        clearChildren();
        int panelWidth = Math.min(420, width - 24);
        int x = (width - panelWidth) / 2;
        int y = Math.max(18, (height - (mode == Mode.PAGES ? 212 : 156)) / 2);
        if (removing) { removeConfirmation(x, y, panelWidth); return; }
        if (confirmingRule) { ruleConfirmation(x, y, panelWidth); return; }
        switch (mode) {
            case DETAILS -> details(x, y, panelWidth);
            case PAGES -> pages(x, y, panelWidth);
            case RULE -> rule(x, y, panelWidth);
        }
    }

    private void details(int x, int y, int w) {
        title = addDrawableChild(new TextFieldWidget(textRenderer, x, y + 25, w, 18, Text.literal("Title"))); title.setMaxLength(1_024); title.setText(titleDraft); title.setChangedListener(value -> titleDraft = value);
        author = addDrawableChild(new TextFieldWidget(textRenderer, x, y + 58, w, 18, Text.literal("Author (optional)"))); author.setMaxLength(1_024); author.setText(authorDraft); author.setChangedListener(value -> authorDraft = value);
        description = addDrawableChild(new TextFieldWidget(textRenderer, x, y + 91, w, 18, Text.literal("Short organizer description"))); description.setMaxLength(1_024); description.setText(descriptionDraft); description.setChangedListener(value -> descriptionDraft = value);
        button("Cancel", x, y + 122, 92, this::returnToMenu, "Discard these local edits");
        button(creating ? "Create and edit pages" : "Save details", x + 98, y + 122, w - 98,
                this::saveDetails, "Save the complete definition; no item is created");
    }

    private void pages(int x, int y, int w) {
        page = Math.clamp(page, 0, Math.max(0, pages.size() - 1));
        pageText = addDrawableChild(EditBoxWidget.builder().build(textRenderer, w, 106, Text.literal("Page text")));
        visiblePage = page;
        pageText.setPosition(x, y + 29); pageText.setMaxLength(32_768); pageText.setText(pages.isEmpty() ? "" : pages.get(page));
        pageText.active = !pages.isEmpty();
        pageText.setChangeListener(ignored -> problem = "");
        button("< Previous", x, y + 142, 86, () -> changePage(-1), "Keep this page's local text and show the previous page");
        button("Next >", x + 90, y + 142, 72, () -> changePage(1), "Keep this page's local text and show the next page");
        button("Add page", x + 166, y + 142, 78, this::addPage, "Append a blank page");
        if (!pages.isEmpty()) button("Remove page…", x + 248, y + 142, w - 248, () -> { retainPage(); removing = true; clearAndInit(); },
                "Confirm removal of this definition page");
        button("Cancel", x, y + 166, 92, this::returnToMenu, "Discard these local page edits");
        button("Preview", x + 96, y + 166, 70, this::preview, "Preview this local draft without saving or issuing an item");
        button("Save pages", x + 170, y + 166, w - 170, this::savePages, "Save all pages together");
    }

    private void rule(int x, int y, int w) {
        int row = y + 27;
        for (BookDefinition.Rule value : BookDefinition.Rule.values()) {
            BookDefinition.Rule selected = rule;
            button((selected == value ? "● " : "○ ") + ruleLabel(value), x, row, w,
                    () -> selectRule(value), ruleHelp(value));
            row += 20;
        }
        button("Cancel", x, row + 2, 92, this::returnToMenu, "Leave the definition unchanged");
        button("Save rule", x + 98, row + 2, w - 98, () -> { confirmingRule = true; clearAndInit(); },
                "Review the future-only discovery rule change");
    }

    private void removeConfirmation(int x, int y, int w) {
        button("Cancel", x, y + 93, 92, () -> { removing = false; clearAndInit(); }, "Keep this page");
        button("Remove page " + (page + 1), x + 98, y + 93, w - 98, () -> {
            pageText=null;visiblePage=-1;pages.remove(page); page = Math.max(0, page - 1); removing = false; clearAndInit();
        }, "Remove only this definition page; discovered copies are unchanged");
    }

    private void ruleConfirmation(int x, int y, int w) {
        button("Cancel", x, y + 93, 92, () -> { confirmingRule = false; clearAndInit(); }, "Do not change the rule");
        button("Confirm rule", x + 98, y + 93, w - 98, () -> {
            confirmingRule = false;
            if (staleRejected) { problem = "This local draft is stale. Cancel and reopen the current definition before saving."; clearAndInit(); return; }
            try { BookDefinition value = validated(draft.title(), draft.author(), draft.description(), pages, rule); draft = value; saved.accept(value); }
            catch (IllegalArgumentException exception) { problem = exception.getMessage(); clearAndInit(); }
        }, "Save future eligibility only; existing copies and discovery history remain unchanged");
    }

    private void saveDetails() {
        if (staleRejected) { problem = "This local draft is stale. Cancel and reopen the current definition before saving."; clearAndInit(); return; }
        try {
            BookDefinition value = validated(titleDraft, authorDraft, descriptionDraft, pages, rule);
            draft = value;
            saved.accept(value);
        } catch (IllegalArgumentException exception) { problem = exception.getMessage(); }
    }

    private void savePages() {
        if (staleRejected) { problem = "This local draft is stale. Cancel and reopen the current definition before saving."; clearAndInit(); return; }
        retainPage();
        try { BookDefinition value = validated(draft.title(), draft.author(), draft.description(), pages, rule); draft = value; saved.accept(value); }
        catch (IllegalArgumentException exception) { problem = exception.getMessage(); clearAndInit(); }
    }

    private void changePage(int delta) { retainPage(); page = Math.clamp(page + delta, 0, Math.max(0, pages.size() - 1)); clearAndInit(); }
    private void selectRule(BookDefinition.Rule value) {
        if (value != BookDefinition.Rule.OFF && pages.stream().allMatch(String::isBlank)) {
            problem = "Add a nonblank page before enabling distribution.";
            return;
        }
        rule = value; problem = ""; clearAndInit();
    }
    private void addPage() {
        retainPage();
        if (pages.size() >= BookDefinition.MAX_PAGES) { problem = "A book may contain at most " + BookDefinition.MAX_PAGES + " pages."; return; }
        pages.add(""); page = pages.size() - 1; clearAndInit();
    }
    private void retainPage() {
        if (pageText == null || visiblePage < 0 || visiblePage >= pages.size()) return;
        pages.set(visiblePage, pageText.getText());
    }
    private void retainVisibleInputs() {
        if (title != null) titleDraft = title.getText();
        if (author != null) authorDraft = author.getText();
        if (description != null) descriptionDraft = description.getText();
        retainPage();
    }
    private void preview() {
        retainPage();
        try { client.setScreen(new BookPreviewScreen(this, validated(draft.title(), draft.author(), draft.description(), pages, rule))); }
        catch (IllegalArgumentException exception) { problem = exception.getMessage(); clearAndInit(); }
    }
    private void returnToMenu() { client.setScreen(returnScreen); }
    void serverRejected(String message, boolean stale) { problem = message; staleRejected = stale; clearAndInit(); }
    CoreMenuScreen parentMenu() { return returnScreen instanceof CoreMenuScreen menu ? menu : null; }

    private BookDefinition validated(String title, String author, String description, List<String> pages, BookDefinition.Rule rule) {
        return new BookDefinition(draft.id(), title, author, description, pages, rule);
    }
    private void button(String label, int x, int y, int w, Runnable action, String hint) {
        ButtonWidget result = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run()).dimensions(x, y, Math.max(1, w), 18).build());
        result.setTooltip(Tooltip.of(Text.literal(hint)));
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int panelWidth = Math.min(420, width - 24); int x = (width - panelWidth) / 2; int y = Math.max(18, (height - (mode == Mode.PAGES ? 212 : 156)) / 2);
        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, net.minecraft.util.Identifier.ofVanilla("popup/background"), x-6, y-6, panelWidth+12, mode==Mode.PAGES?210:148);
        String heading = removing ? "Remove page " + (page + 1) + "?" : confirmingRule ? "Save discovery rule?" : switch (mode) {
            case DETAILS -> creating ? "New lore book" : "Edit book details";
            case PAGES -> "Edit pages — " + draft.title() + "  " + (pages.isEmpty() ? 0 : page + 1) + " / " + pages.size();
            case RULE -> "Discovery rule";
        };
        context.drawTextWithShadow(textRenderer, Text.literal(heading), x, y, 0xFFFFFFFF);
        if (mode == Mode.DETAILS && !removing && !confirmingRule) {
            context.drawTextWithShadow(textRenderer, Text.literal("Title"), x, y + 14, 0xFFCCCCCC);
            context.drawTextWithShadow(textRenderer, Text.literal("Author (optional)"), x, y + 47, 0xFFCCCCCC);
            context.drawTextWithShadow(textRenderer, Text.literal("Organizer description"), x, y + 80, 0xFFCCCCCC);
        }
        if (mode == Mode.PAGES && !removing && !confirmingRule) context.drawTextWithShadow(textRenderer, Text.literal("Literal page text; no formatting codes."), x, y + 18, 0xFFA0A0A0);
        if (removing) context.drawTextWithShadow(textRenderer, Text.literal("This affects the definition, not any already discovered inventory copy."), x, y + 28, 0xFFCCCCCC);
        if (confirmingRule) context.drawTextWithShadow(textRenderer, Text.literal("This changes future eligibility only; it does not alter copies or discovery history."), x, y + 28, 0xFFCCCCCC);
        String feedback = staleRejected ? "Local unsent draft - cancel and reopen before saving." : problem;
        int feedbackY = mode == Mode.PAGES ? 189 : mode == Mode.DETAILS ? 111 : 131;
        if (!feedback.isBlank()) context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(feedback, panelWidth)), x, y + feedbackY, staleRejected ? 0xFFFFCC66 : 0xFFFF6666);
        super.render(context, mouseX, mouseY, deltaTicks);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { returnToMenu(); }

    private static String ruleLabel(BookDefinition.Rule rule) { return switch (rule) { case OFF -> "Distribution off"; case ONCE -> "Spawn once"; case UNTIL_FOUND -> "Until found"; case REPEAT -> "Repeat"; }; }
    private static String ruleHelp(BookDefinition.Rule rule) { return switch (rule) { case OFF -> "Stop new managed copies; existing copies remain unchanged."; case ONCE -> "Allow one managed issuance only."; case UNTIL_FOUND -> "Continue eligible rolls until a player receives the book."; case REPEAT -> "Continue eligible rolls after discovery."; }; }
}
