package io.github.chronicle.core.client;

import io.github.chronicle.core.books.BookChance;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** Small native form for a chest's optional direct named-book roll. */
final class BookChanceScreen extends Screen {
    private final Screen returnScreen; private final BookChance choice; private final String displayName; private final boolean debug; private final Consumer<BookChance> save; private final Runnable clear; private final Runnable cancel;
    private TextFieldWidget chance; private String problem = ""; private String chanceDraft;
    BookChanceScreen(Screen returnScreen, BookChance choice, String displayName, boolean debug, Consumer<BookChance> save, Runnable clear, Runnable cancel) { super(Text.literal("Specific lore book")); this.returnScreen=returnScreen;this.choice=choice;this.displayName=displayName;this.debug=debug;this.save=save;this.clear=clear;this.cancel=cancel; }
    @Override protected void init() {
        if(chance!=null)chanceDraft=chance.getText();
        int w=Math.min(320,width-24),x=(width-w)/2,y=Math.max(24,(height-120)/2);
        chance=addDrawableChild(new TextFieldWidget(textRenderer,x,y+42,w,18,Text.literal("Chance (0–100)")));chance.setMaxLength(32);chance.setText(chanceDraft==null?Double.toString(choice.chancePercent()):chanceDraft);
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),ignored->cancel.run()).dimensions(x,y+75,72,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"),ignored->{clear.run();client.setScreen(returnScreen);}).dimensions(x+76,y+75,72,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save chest setting"),ignored->save()).dimensions(x+152,y+75,w-152,18).build());
    }
    private void save(){try{save.accept(new BookChance(choice.bookId(),Double.parseDouble(chance.getText())));}catch(IllegalArgumentException ex){problem="Enter a finite chance from 0 to 100.";clearAndInit();}}
    @Override public void render(DrawContext c,int mx,int my,float d){int w=Math.min(320,width-24),x=(width-w)/2,y=Math.max(24,(height-120)/2);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),x-6,y-6,w+12,111);c.drawTextWithShadow(textRenderer,Text.literal("Specific lore book"),x,y,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(displayName,w)),x,y+20,0xFFCCCCCC);if(debug)c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(choice.bookId(),w)),x,y+30,0xFFA0A0A0);if(!problem.isBlank())c.drawTextWithShadow(textRenderer,Text.literal(problem),x,y+64,0xFFFF6666);super.render(c,mx,my,d);}
    @Override public boolean shouldPause(){return false;} @Override public void close(){cancel.run();}
}
