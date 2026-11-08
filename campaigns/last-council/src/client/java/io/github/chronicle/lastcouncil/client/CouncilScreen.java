package io.github.chronicle.lastcouncil.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.OrderedText;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Shared vanilla-widget frame for the small scenario screens; no player-inventory imitation. */
abstract class CouncilScreen extends Screen {
    protected int left,top,panelWidth,panelHeight;
    protected String feedback="";
    protected final Screen parent;
    CouncilScreen(String title,Screen parent){super(Text.literal(title));this.parent=parent;}
    protected void frame(){readings.clear();lists.clear();cardStacks.clear();panelWidth=Math.min(480,width-20);panelHeight=Math.min(360,height-20);left=(width-panelWidth)/2;top=(height-panelHeight)/2;button("Close",left+panelWidth-66,top+8,54,this::close);}
    protected ButtonWidget button(String label,int x,int y,int w,Runnable action){return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,20).build());}
    private final List<Reading> readings=new ArrayList<>();
    private final Map<String,Integer> listOffsets=new HashMap<>();
    private final List<ListArea> lists=new ArrayList<>();
    private record ListArea(String key,int x,int y,int w,int h,int size,int rows) { }
    protected void rows(String key,List<Choice> choices,String selected,int x,int y,int w,int h,Consumer<String> select){rows(key,choices,selected,x,y,w,h,true,select);}
    protected void rows(String key,List<Choice> choices,String selected,int x,int y,int w,int h,boolean enabled,Consumer<String> select){
        int count=Math.max(1,(h-24)/22), max=Math.max(0,choices.size()-count);
        int offset=Math.clamp(listOffsets.getOrDefault(key,0),0,max);listOffsets.put(key,offset);
        lists.add(new ListArea(key,x,y,w,h,choices.size(),count));
        for(int i=offset;i<Math.min(choices.size(),offset+count);i++){
            Choice c=choices.get(i);String label=(c.id().equals(selected)?"> ":"")+c.label();
            var row=button(textRenderer.trimToWidth(label,w-8),x,y+(i-offset)*22,w,()->select.accept(c.id()));
            row.active=enabled;
            row.setTooltip(Tooltip.of(Text.literal(c.label())));
        }
        if(max>0){int half=(w-4)/2;button("Up",x,y+h-20,half,()->{listOffsets.put(key,Math.max(0,offset-count));clearAndInit();}).active=offset>0;
            button("Down",x+half+4,y+h-20,half,()->{listOffsets.put(key,Math.min(max,offset+count));clearAndInit();}).active=offset<max;}
    }
    protected void compactRows(String key,List<Choice> choices,String selected,int x,int y,int w,boolean enabled,Consumer<String> select){
        int saved=listOffsets.getOrDefault(key,0);
        if(!enabled&&!selected.isBlank())for(int i=0;i<choices.size();i++)if(choices.get(i).id().equals(selected)){saved=i;break;}
        int offset=Math.clamp(saved,0,Math.max(0,choices.size()-1));
        listOffsets.put(key,offset);
        button("<",x,y,20,()->{listOffsets.put(key,Math.max(0,offset-1));rebuildPreservingFocus();}).active=enabled&&offset>0;
        if(!choices.isEmpty()) {
            Choice c=choices.get(offset);
            var row=button(textRenderer.trimToWidth((c.id().equals(selected)?"> ":"")+c.label(),w-48),x+22,y,w-44,()->select.accept(c.id()));
            row.active=enabled; row.setTooltip(Tooltip.of(Text.literal(c.label())));
        }
        button(">",x+w-20,y,20,()->{listOffsets.put(key,Math.min(choices.size()-1,offset+1));rebuildPreservingFocus();}).active=enabled&&offset+1<choices.size();
    }
    private final Map<String,Integer> readingOffsets=new HashMap<>();
    private record Reading(String key,int x,int y,int w,int h,List<OrderedText> lines) { }
    protected void body(String text,int x,int y,int w,int h){body("body",text,x,y,w,h);}
    protected void body(String key,String text,int x,int y,int w,int h){
        List<OrderedText> lines=new ArrayList<>();
        for(String paragraph:text.split("\\n",-1)) {
            if(paragraph.isEmpty()) lines.addAll(textRenderer.wrapLines(Text.literal(" "),Math.max(1,w-8)));
            else lines.addAll(textRenderer.wrapLines(Text.literal(paragraph),Math.max(1,w-8)));
        }
        readings.add(new Reading(key,x,y,w,h,lines));
    }
    protected record SectionCard(String heading,String text) { }
    private record DrawCard(String heading,List<OrderedText> lines,int y,int height) { }
    private record CardStack(String key,int x,int y,int w,int h,int total,List<DrawCard> cards) { }
    private final List<CardStack> cardStacks=new ArrayList<>();
    private final Map<String,Integer> cardOffsets=new HashMap<>();
    protected void sections(String key,List<SectionCard> sections,int x,int y,int w,int h){
        List<DrawCard> cards=new ArrayList<>();int position=0;
        for(SectionCard section:sections){
            List<OrderedText> lines=new ArrayList<>();
            for(String paragraph:section.text().split("\\n",-1))
                lines.addAll(textRenderer.wrapLines(Text.literal(paragraph.isEmpty()?" ":paragraph),Math.max(1,w-34)));
            int height=20+lines.size()*10;cards.add(new DrawCard(section.heading(),lines,position,height));position+=height+4;
        }
        int contentWidth=position>h?w-20:w;
        cardStacks.add(new CardStack(key,x,y,contentWidth,h,position,cards));
        if(position>h) {
            int max=position-h, offset=Math.clamp(cardOffsets.getOrDefault(key,0),0,max);
            var up=addDrawableChild(ButtonWidget.builder(Text.literal("^"),b->{cardOffsets.put(key,Math.max(0,offset-30));rebuildPreservingFocus();}).dimensions(x+w-18,y,18,Math.max(10,h/2)).build());
            up.active=offset>0;
            var down=addDrawableChild(ButtonWidget.builder(Text.literal("v"),b->{cardOffsets.put(key,Math.min(max,offset+30));rebuildPreservingFocus();}).dimensions(x+w-18,y+Math.max(10,h/2),18,Math.max(10,h-Math.max(10,h/2))).build());
            down.active=offset<max;
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        for(CardStack stack:cardStacks) if(mx>=stack.x&&mx<stack.x+stack.w&&my>=stack.y&&my<stack.y+stack.h){
            int max=Math.max(0,stack.total-stack.h);
            cardOffsets.put(stack.key,Math.clamp(cardOffsets.getOrDefault(stack.key,0)-(int)Math.signum(vertical)*30,0,max));rebuildPreservingFocus();return true;
        }
        for(ListArea a:lists) if(mx>=a.x&&mx<a.x+a.w&&my>=a.y&&my<a.y+a.h){
            int max=Math.max(0,a.size-a.rows);listOffsets.put(a.key,Math.clamp(listOffsets.getOrDefault(a.key,0)-(int)Math.signum(vertical),0,max));clearAndInit();return true;
        }
        for(Reading r:readings) if(mx>=r.x&&mx<r.x+r.w&&my>=r.y&&my<r.y+r.h){
            int max=Math.max(0,r.lines.size()-(r.h/10));
            readingOffsets.put(r.key,Math.clamp(readingOffsets.getOrDefault(r.key,0)-(int)Math.signum(vertical)*3,0,max));
            return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    protected void rebuildPreservingFocus(){
        String focused=getFocused() instanceof ButtonWidget button?button.getMessage().getString():null;
        clearAndInit();
        if(focused!=null)for(var child:children())if(child instanceof ButtonWidget button&&button.active&&button.getMessage().getString().equals(focused)){setFocused(button);break;}
    }
    protected void message(String value){feedback=value;}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,Identifier.ofVanilla("popup/background"),left,top,panelWidth,panelHeight);
        context.drawTextWithShadow(textRenderer,title,left+12,top+13,0xFFFFFFFF);
        if(!feedback.isBlank())context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(feedback,panelWidth-24),left+12,top+panelHeight-13,0xFFFFFF99);
        for(Reading r:readings){
            int offset=Math.clamp(readingOffsets.getOrDefault(r.key,0),0,Math.max(0,r.lines.size()-r.h/10));
            context.enableScissor(r.x,r.y,r.x+r.w,r.y+r.h);
            for(int i=offset;i<r.lines.size()&&(i-offset)*10<r.h;i++) context.drawTextWithShadow(textRenderer,r.lines.get(i),r.x,r.y+(i-offset)*10,0xFFFFFFFF);
            context.disableScissor();
            if(r.lines.size()*10>r.h) context.fill(r.x+r.w-2,r.y,r.x+r.w,r.y+r.h,0xFF888888);
        }
        for(CardStack stack:cardStacks){
            int offset=Math.clamp(cardOffsets.getOrDefault(stack.key,0),0,Math.max(0,stack.total-stack.h));
            context.enableScissor(stack.x,stack.y,stack.x+stack.w,stack.y+stack.h);
            for(DrawCard card:stack.cards){
                int cy=stack.y+card.y-offset;
                context.fill(stack.x,cy,stack.x+stack.w-3,cy+card.height,0x225F6A6F);
                context.fill(stack.x,cy,stack.x+stack.w-3,cy+1,0xFF777C79);
                context.drawTextWithShadow(textRenderer,Text.literal(card.heading),stack.x+4,cy+4,0xFFFFDEA3);
                for(int i=0;i<card.lines.size();i++)context.drawTextWithShadow(textRenderer,card.lines.get(i),stack.x+4,cy+16+i*10,0xFFFFFFFF);
            }
            context.disableScissor();
            if(stack.total>stack.h)context.fill(stack.x+stack.w-2,stack.y,stack.x+stack.w,stack.y+stack.h,0xFF888888);
        }
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
