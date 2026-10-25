package io.github.chronicle.core.client;

import io.github.chronicle.core.books.BookDefinition;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.Text;

/** Vanilla literal-text reader used only for a local authoring preview. */
final class BookPreviewScreen extends BookScreen {
    private final Screen returnScreen;

    BookPreviewScreen(Screen returnScreen, BookDefinition definition) {
        super(new BookScreen.Contents(definition.pages().stream().<Text>map(Text::literal).toList()));
        this.returnScreen = returnScreen;
    }

    @Override public void close() { returnToEditor(); }
    private void returnToEditor() { client.setScreen(returnScreen); }
}
