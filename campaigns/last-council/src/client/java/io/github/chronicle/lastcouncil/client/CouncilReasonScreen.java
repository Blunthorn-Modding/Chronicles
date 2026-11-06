package io.github.chronicle.lastcouncil.client;
import java.util.function.Consumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
final class CouncilReasonScreen extends CouncilScreen {
    private final int max;
    private final Consumer<String> submit;
    private String value="";
    CouncilReasonScreen(String title,Screen parent,int max,Consumer<String> submit) {
        super(title,parent);
        this.max=max;
        this.submit=submit;
    }
    @Override protected void init() {
        frame();
        int x=left+12,w=panelWidth-24,y=top+40;
        body("Enter a reason. This will be recorded.",x,y,w,30);
        y+=36;
        TextFieldWidget field=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Reason")));
        field.setMaxLength(max);
        field.setText(value);
        field.setChangedListener(s->value=s);
        setFocused(field);
        y+=28;
        button("Submit",x,y,(w-4)/2,()-> {
            if(value.isBlank()) {
                feedback="A reason is required."; return;
            }
            client.setScreen(parent); submit.accept(value);
        });
        button("Cancel",x+(w+4)/2,y,(w-4)/2,this::close);
    }
}
