package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.KeepForm;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/** Server-reviewed, table-scoped keep request; it creates no local placement state. */
final class BookKeepScreen extends Screen {
    record Request(Action action, String bookId, String tableId, boolean override) { }
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private final Screen returnScreen; private final KeepForm form; private final Consumer<Request> sent;
    private boolean override;
    BookKeepScreen(Screen returnScreen, KeepForm form, Consumer<Request> sent) { super(Text.literal("Keep book placed")); this.returnScreen=returnScreen;this.form=form;this.sent=sent; }
    @Override protected void init() {
        int w=Math.min(390,Math.max(180,width-24)),x=(width-w)/2,y=Math.max(18,(height-206)/2),bottom=y+174;
        if(form.overrideNeeded()&&!form.found()&&(!form.active()||form.requiresReview())) addDrawableChild(ButtonWidget.builder(Text.literal((override ? "[x] " : "[ ] ")+"Override spawning rule for this request only"),ignored->{override=!override;clearAndInit();}).dimensions(x,y+118,w,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),ignored->client.setScreen(returnScreen)).dimensions(x,bottom,78,18).build());
        if(form.active()&&form.requiresReview()) {
            ButtonWidget stop=addDrawableChild(ButtonWidget.builder(Text.literal("Stop keeping"),ignored->send(Action.BOOK_KEEP_STOP)).dimensions(x+82,bottom,94,18).build()); stop.active=form.canAct();
            ButtonWidget review=addDrawableChild(ButtonWidget.builder(Text.literal("Confirm updated rule"),ignored->send(Action.BOOK_KEEP_REVIEW)).dimensions(x+180,bottom,w-180,18).build()); review.active=form.canAct()&&(!form.overrideNeeded()||override);
        } else {
            Action action=form.active()?Action.BOOK_KEEP_STOP:Action.BOOK_KEEP_START;
            String label=action==Action.BOOK_KEEP_START?"Enable until found":"Stop keeping";
            ButtonWidget primary=addDrawableChild(ButtonWidget.builder(Text.literal(label),ignored->send(action)).dimensions(x+82,bottom,w-82,18).build());
            primary.active=form.canAct()&&!form.found()&&(!form.overrideNeeded()||override||action==Action.BOOK_KEEP_STOP);
        }
    }
    private void send(Action action){sent.accept(new Request(action,form.bookId(),form.tableId(),override));}
    @Override public void render(DrawContext c,int mx,int my,float d){
        int w=Math.min(390,Math.max(180,width-24)),x=(width-w)/2,y=Math.max(18,(height-206)/2);
        c.drawGuiTexture(RenderPipelines.GUI_TEXTURED,POPUP,x-6,y-6,w+12,204);
        c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth("Keep "+form.bookTitle()+" placed until found?",w)),x,y,0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth("Loot table: "+form.tableLabel(),w)),x,y+13,0xFFCCCCCC);
        drawWrapped(c,form.status(),x,y+30,w,0xFFCCCCCC,2);
        drawWrapped(c,"Keep one copy available across chests using this table. It may move on a normal refill and stops when a player takes it into inventory.",x,y+54,w,0xFFCCCCCC,3);
        if(!form.reason().isBlank())drawWrapped(c,form.reason(),x,y+88,w,form.canAct()?0xFFA0A0A0:0xFFFFCC66,2);
        if(form.found())drawWrapped(c,"Already found in this campaign; keeping cannot restart.",x,y+108,w,0xFFFFCC66,2);
        super.render(c,mx,my,d);
    }
    private void drawWrapped(DrawContext c,String value,int x,int y,int w,int color,int max){int line=0;for(var ordered:textRenderer.wrapLines(Text.literal(value),Math.max(1,w))){if(line++>=max)break;StringBuilder plain=new StringBuilder();ordered.accept((i,s,cp)->{plain.appendCodePoint(cp);return true;});c.drawTextWithShadow(textRenderer,Text.literal(plain.toString()),x,y+(line-1)*10,color);}}
    @Override public boolean shouldPause(){return false;} @Override public void close(){client.setScreen(returnScreen);}
}
