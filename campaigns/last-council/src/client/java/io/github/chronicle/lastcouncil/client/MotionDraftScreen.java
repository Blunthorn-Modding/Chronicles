package io.github.chronicle.lastcouncil.client;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
final class MotionDraftScreen extends CouncilScreen {
    interface Submit {
        void accept(String title,String body,boolean secret,boolean thirds);
    }
    private final Submit submit;
    private String name="",content="";
    private boolean secret,thirds;
    MotionDraftScreen(Screen parent,Submit submit) {
        super("Motion draft",parent);
        this.submit=submit;
    }
    @Override protected void init() {
        frame();
        int x=left+12,w=panelWidth-24,y=top+49;
        TextFieldWidget titleField=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Title")));
        titleField.setMaxLength(320);
        titleField.setText(name);
        titleField.setChangedListener(s->name=s);
        y=top+88;
        int editorHeight=Math.max(42,panelHeight-182);
        EditBoxWidget bodyField=addDrawableChild(EditBoxWidget.builder().build(textRenderer,w,editorHeight,
                Text.literal("Motion text")));
        bodyField.setPosition(x,y);
        bodyField.setMaxLength(8192);
        bodyField.setText(content);
        bodyField.setChangeListener(s->content=s);
        y+=editorHeight+6;
        int half=(w-4)/2;
        button("Visibility: "+(secret?"Secret":"Public"),x,y,half,()-> {
            secret=!secret; clearAndInit();
        });
        button("Threshold: "+(thirds?"2/3":"Majority"),x+half+4,y,half,()-> {
            thirds=!thirds; clearAndInit();
        });
        y+=26;
        button("Submit",x,y,half,()-> {
            if(name.isBlank()||content.isBlank()||name.codePointCount(0,name.length())>160||content.codePointCount(0,content.length())>4096) {
                feedback="Title and text required (maximum 160 and 4096 characters)."; return;
            }
            client.setScreen(parent); submit.accept(name,content,secret,thirds);
        });
        button("Cancel",x+half+4,y,half,this::close);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        context.drawTextWithShadow(textRenderer,Text.literal("Title"),left+12,top+36,0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,Text.literal("Proposal"),left+12,top+76,0xFFFFFFFF);
    }
}
