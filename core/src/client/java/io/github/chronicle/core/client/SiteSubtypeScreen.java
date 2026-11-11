package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Independent subtype selections stay local until Apply. */
final class SiteSubtypeScreen extends Screen {
    private final List<Entry> entries; private final Set<String> selected = new LinkedHashSet<>();
    private final Consumer<Set<String>> apply; private final Runnable cancel; private int offset;
    SiteSubtypeScreen(Screen parent, List<Entry> entries, Consumer<Set<String>> apply, Runnable cancel) {
        super(Text.literal("Subtypes")); this.entries=List.copyOf(entries); this.apply=apply; this.cancel=cancel;
        entries.stream().filter(e -> e.kind().startsWith("ASSIGNED")).forEach(e -> selected.add(e.id()));
    }
    @Override protected void init() {
        int w=Math.min(420,width-24),x=(width-w)/2,y=Math.max(15,(height-230)/2);
        for(int i=0;i<Math.min(7,entries.size()-offset);i++) {
            Entry entry=entries.get(offset+i); boolean enabled=!entry.kind().equals("UNAVAILABLE") && !entry.kind().equals("ASSIGNED_LOCKED");
            ButtonWidget b=addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth((selected.contains(entry.id()) ? "[x] " : "[ ] ")+entry.label(),w-8)), ignored -> { if (!selected.remove(entry.id())) selected.add(entry.id()); clearAndInit(); }).dimensions(x,y+24+i*20,w,18).build());
            b.active=enabled; b.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(entry.description())));
        }
        button("<",x,y+166,w/2-2,()->page(-7),offset>0); button(">",x+w/2+2,y+166,w/2-2,()->page(7),offset+7<entries.size());
        button("Apply",x,y+188,w/2-2,()->apply.accept(Set.copyOf(selected)),true); button("Cancel",x+w/2+2,y+188,w/2-2,this::close,true);
    }
    private void page(int delta){offset=Math.clamp(offset+delta,0,Math.max(0,entries.size()-7));clearAndInit();}
    private void button(String text,int x,int y,int w,Runnable action,boolean enabled){ButtonWidget b=addDrawableChild(ButtonWidget.builder(Text.literal(text),ignored->action.run()).dimensions(x,y,w,18).build());b.active=enabled;}
    @Override public void render(DrawContext c,int mx,int my,float d){int w=Math.min(420,width-24),x=(width-w)/2,y=Math.max(15,(height-230)/2);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),x-8,y-8,w+16,230);c.drawTextWithShadow(textRenderer,title,x,y,0xFFFFFFFF);super.render(c,mx,my,d);}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){cancel.run();}
}
