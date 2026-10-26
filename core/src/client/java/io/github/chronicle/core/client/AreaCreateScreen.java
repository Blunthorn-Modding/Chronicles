package io.github.chronicle.core.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** Native first step for an Area change: the server owns the resulting world-selection draft. */
final class AreaCreateScreen extends Screen {
    private final Screen parent; private final Consumer<String> begin; private TextFieldWidget name; private String error="",nameDraft="";
    AreaCreateScreen(Screen parent, Consumer<String> begin) { super(Text.literal("Create new area")); this.parent=parent;this.begin=begin; }
    @Override protected void init(){int w=Math.min(360,width-24),x=(width-w)/2,y=Math.max(24,(height-150)/2);name=addDrawableChild(new TextFieldWidget(textRenderer,x,y+42,w,18,Text.literal("Area change name")));name.setMaxLength(160);name.setText(nameDraft);name.setChangedListener(v->nameDraft=v);button("Cancel",x,y+93,w/3,this::close);button("Begin with Start",x+w/3+3,y+93,w/3-3,()->send("start"));button("Begin with End",x+2*w/3+3,y+93,w/3-3,()->send("end"));}
    private void send(String state){String value=name.getText().trim();if(value.isBlank()){error="A name is required.";return;}JsonObject json=new JsonObject();json.addProperty("name",value);json.addProperty("state",state);begin.accept(json.toString());}
    private void button(String label,int x,int y,int w,Runnable run){addDrawableChild(ButtonWidget.builder(Text.literal(label),b->run.run()).dimensions(x,y,w,18).build());}
    @Override public void render(DrawContext c,int mx,int my,float d){int pw=Math.min(360,width-24),px=(width-pw)/2,py=Math.max(24,(height-150)/2);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),px-8,py-8,pw+16,134);c.drawTextWithShadow(textRenderer,Text.literal("Create new area"),width/2-170,Math.max(24,(height-150)/2),0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Which saved state exists first? World corner selection follows."),width/2-170,Math.max(24,(height-150)/2)+15,0xFFCCCCCC);c.drawTextWithShadow(textRenderer,Text.literal("Area name"),px,py+29,0xFFCCCCCC);if(!error.isBlank())c.drawTextWithShadow(textRenderer,Text.literal(error),width/2-170,Math.max(24,(height-150)/2)+72,0xFFFF9966);super.render(c,mx,my,d);}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
