package io.github.chronicle.lastcouncil.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Shared vanilla-widget frame for the small scenario screens; no player-inventory imitation. */
abstract class CouncilScreen extends Screen {
    protected int left,top,panelWidth,panelHeight;
    protected String feedback="";
    protected final Screen parent;
    CouncilScreen(String title,Screen parent){super(Text.literal(title));this.parent=parent;}
    protected void frame(){panelWidth=Math.min(480,width-20);panelHeight=Math.min(360,height-20);left=(width-panelWidth)/2;top=(height-panelHeight)/2;button("Close",left+panelWidth-66,top+8,54,this::close);}
    protected ButtonWidget button(String label,int x,int y,int w,Runnable action){return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,20).build());}
    protected void body(String text,int x,int y,int w,int h){addDrawableChild(new ScrollableTextWidget(x,y,Math.max(20,w),Math.max(20,h),Text.literal(text),textRenderer).textColor(0xFFFFFFFF));}
    protected void message(String value){feedback=value;}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,Identifier.ofVanilla("popup/background"),left,top,panelWidth,panelHeight);
        context.drawTextWithShadow(textRenderer,title,left+12,top+13,0xFFFFFFFF);
        if(!feedback.isBlank())context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(feedback,panelWidth-24),left+12,top+panelHeight-13,0xFFFFFF99);
        super.render(context,mouseX,mouseY,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(client.player!=null&&client.world!=null?parent:null);}
    protected record Choice(String id,String label) { }
    protected void choose(String title,List<Choice> choices,Consumer<String> selected){client.setScreen(new Picker(title,this,choices,selected));}
    private static final class Picker extends CouncilScreen {
        final List<Choice> choices;final Consumer<String> selected;String query="";int offset;TextFieldWidget search;
        Picker(String title,Screen parent,List<Choice> choices,Consumer<String> selected){super(title,parent);this.choices=List.copyOf(choices);this.selected=selected;}
        @Override protected void init(){
            frame();search=addDrawableChild(new TextFieldWidget(textRenderer,left+12,top+36,panelWidth-24,20,Text.literal("Search")));search.setMaxLength(128);search.setText(query);
            search.setChangedListener(value->{int cursor=search.getCursor();query=value;offset=0;clearAndInit();search.setCursor(Math.min(cursor,query.length()),false);setFocused(search);});
            var filtered=choices.stream().filter(c->c.label().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();int rows=Math.max(1,(panelHeight-110)/24);offset=Math.clamp(offset,0,Math.max(0,filtered.size()-rows));
            for(int i=offset;i<Math.min(filtered.size(),offset+rows);i++){var choice=filtered.get(i);button(choice.label(),left+12,top+66+(i-offset)*24,panelWidth-24,()->{selected.accept(choice.id());client.setScreen(parent);});}
            if(filtered.isEmpty())body("No matching entries.",left+12,top+66,panelWidth-24,30);
            if(filtered.size()>rows){button("Up",left+12,top+panelHeight-38,72,()->{offset=Math.max(0,offset-rows);clearAndInit();}).active=offset>0;button("Down",left+88,top+panelHeight-38,72,()->{offset+=rows;clearAndInit();}).active=offset+rows<filtered.size();}
        }
    }
}
