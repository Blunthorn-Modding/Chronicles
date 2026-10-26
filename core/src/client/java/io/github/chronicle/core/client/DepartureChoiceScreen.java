package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.Consumer;

/** Bounded local selector for already server-pinned departure choices. */
final class DepartureChoiceScreen extends Screen {
    private final Screen parent; private final List<Entry> rows; private final boolean optional; private final Consumer<Entry> selected; private int offset;
    DepartureChoiceScreen(Screen parent,String title,List<Entry> rows,boolean optional,Consumer<Entry> selected){super(Text.literal(title));this.parent=parent;this.rows=List.copyOf(rows);this.optional=optional;this.selected=selected;}
    @Override protected void init(){int w=Math.min(420,width-24),x=(width-w)/2,y=Math.max(15,(height-210)/2);int count=Math.min(7,rows.size()-offset);for(int i=0;i<count;i++){Entry row=rows.get(offset+i);button(row.label(),x,y+20+i*20,w,()->choose(row));}if(optional)button("None",x,y+162,w/2-2,()->choose(null));button("<",optional?x+w/2+2:x,y+162,optional?w/4-2:w/2-2,()->page(-7),offset>0);button(">",optional?x+3*w/4+2:x+w/2+2,y+162,optional?w/4-2:w/2-2,()->page(7),offset+7<rows.size());button("Cancel",x,y+184,w,this::close);}
    private void choose(Entry value){client.setScreen(parent);selected.accept(value);} private void page(int delta){offset=Math.clamp(offset+delta,0,Math.max(0,rows.size()-7));clearAndInit();} private void button(String label,int x,int y,int w,Runnable run){button(label,x,y,w,run,true);} private void button(String label,int x,int y,int w,Runnable run,boolean enabled){ButtonWidget b=addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,Math.max(1,w-8))),ignored->run.run()).dimensions(x,y,Math.max(1,w),18).build());b.active=enabled;}
    @Override public void render(DrawContext c,int mx,int my,float d){int x=(width-Math.min(420,width-24))/2,y=Math.max(15,(height-210)/2);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),x-8,y-8,Math.min(420,width-24)+16,218);c.drawTextWithShadow(textRenderer,title,x,y,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Choose a saved record. Selections remain pinned by revision."),x,y+10,0xFFA0A0A0);super.render(c,mx,my,d);}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
