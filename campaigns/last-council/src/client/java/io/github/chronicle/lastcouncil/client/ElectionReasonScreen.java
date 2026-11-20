package io.github.chronicle.lastcouncil.client;
import io.github.chronicle.lastcouncil.network.ElectionPayloads.Action;
import java.util.List;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
final class ElectionReasonScreen extends CouncilScreen {
    private final Action action;private final String candidate;private String reason="";
    ElectionReasonScreen(CouncilScreen owner,Action action,String candidate){super(action==Action.END?"End election?":"Withdraw candidacy?",owner);this.action=action;this.candidate=candidate;}
    @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+36;
        String description=action==Action.END?"End this election unresolved. Its history and ordinary Council business remain; this does not reset the election or depose a crowned ruler.":candidate+" will be permanently removed from this election's slate. Your House retains its Council participation and vote.";
        int eh=Math.max(30,Math.min(60,panelHeight-180));
        int ey=top+panelHeight-100-eh;
        sections("consequences",List.of(new SectionCard("Consequences",description)),x,y,w,Math.max(20,ey-18-y));
        body("Public reason (required)",x,ey-14,w,14);
        EditBoxWidget box=addDrawableChild(EditBoxWidget.builder().x(x).y(ey).build(textRenderer,w,eh,Text.literal("Public reason")));box.setMaxLength(400);box.setText(reason);box.setChangeListener(s->reason=s);
        button("Cancel",x,top+panelHeight-38,(w-4)/2,this::close);
        button("Confirm",x+(w+4)/2,top+panelHeight-38,(w-4)/2,()->{if(reason.isBlank()){feedback="Enter a public reason.";return;}ElectionClient.send(action,reason,ElectionClient.view().page());}).active=!ElectionClient.busy();
    }
    @Override public void close(){ElectionClient.returnToOwner();}
}
