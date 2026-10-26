package io.github.chronicle.core.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.Consumer;

/** Small native chooser for a typed Area state; it never derives availability from presentation text. */
final class AreaStateScreen extends Screen {
    private final Screen parent; private final String message; private final List<String> choices; private final Consumer<String> selected; private final java.util.Set<String> unavailable;
    AreaStateScreen(Screen parent, String title, String message, List<String> choices, Consumer<String> selected,java.util.Set<String> unavailable) {
        super(Text.literal(title)); this.parent=parent; this.message=message; this.choices=List.copyOf(choices); this.selected=selected;this.unavailable=java.util.Set.copyOf(unavailable);
    }
    @Override protected void init() { int w=Math.min(390,width-24), x=(width-w)/2, y=Math.max(25,(height-130)/2); int count=choices.size();
        for(int i=0;i<count;i++){String choice=choices.get(i); ButtonWidget option=addDrawableChild(ButtonWidget.builder(Text.literal(label(choice)), b->{client.setScreen(parent);selected.accept(choice);}).dimensions(x+i*(w/count),y+62,Math.max(1,w/count-3),18).build());option.active=!unavailable.contains(choice);if(!option.active)option.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("This state is missing or its Region changed.")));}
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),b->close()).dimensions(x,y+85,w,18).build()); }
    private String label(String value){return switch(value){case "start"->"Start";case "end"->"End";case "unchanged"->"Leave unchanged";default->value;};}
    @Override public void render(DrawContext c,int mx,int my,float d){int w=Math.min(390,width-24),x=(width-w)/2,y=Math.max(25,(height-130)/2);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),x-8,y-8,w+16,128);c.drawTextWithShadow(textRenderer,title,x,y,0xFFFFFFFF);int line=y+18;for(var wrapped:textRenderer.wrapLines(Text.literal(message),w)){c.drawTextWithShadow(textRenderer,wrapped,x,line,0xFFCCCCCC);line+=10;}super.render(c,mx,my,d);}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
