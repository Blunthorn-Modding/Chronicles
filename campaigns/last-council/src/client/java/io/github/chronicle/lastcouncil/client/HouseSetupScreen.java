package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;

/** The operator assignment/draw popup opened from Core Players, not another Game setup category. */
public final class HouseSetupScreen extends CouncilScreen {
    private AdminView view;
    private UUID pending;
    private int waitingTicks;
    private String houseId;
    private boolean candidate,draw;
    HouseSetupScreen(AdminView view){super("House assignments",null);apply(view);}
    private void apply(AdminView value){view=value;feedback=value.message();var target=value.players().stream().filter(p->p.id().equals(value.target())).findFirst().orElse(null);houseId=target==null?"":target.houseId();candidate=target!=null&&target.candidate();}
    void accept(AdminView value){if(!value.request().equals(pending))return;pending=null;waitingTicks=0;apply(value);clearAndInit();}
    void notice(Notice notice){if(notice.request().equals(pending)){pending=null;waitingTicks=0;feedback=notice.message();clearAndInit();}}
    private void send(Operation operation){send(operation,view.target());}
    private void send(Operation operation,UUID target){if(pending!=null||!ClientPlayNetworking.canSend(AdminRequest.ID))return;pending=UUID.randomUUID();waitingTicks=0;ClientPlayNetworking.send(new AdminRequest(pending,operation,view.revision(),target,houseId,candidate,view.preview()));}
    @Override protected void init(){
        frame();int x=left+12,y=top+36,w=panelWidth-24;
        var selected=view.players().stream().filter(p->p.id().equals(view.target())).findFirst().orElse(null);
        button(selected==null?"Choose character…":selected.label()+"…",x,y,w,()->choose("Choose character",view.players().stream().map(p->new Choice(p.id().toString(),p.label())).toList(),id->send(Operation.OPEN,UUID.fromString(id))));y+=28;
        if(!draw){
            var house=view.houses().stream().filter(h->h.id().equals(houseId)).findFirst().orElse(null);
            button(house==null?"Choose house…":house.title()+"…",x,y,w,()->choose("Choose house",view.houses().stream().filter(h->view.players().stream().noneMatch(p->!p.id().equals(view.target())&&p.houseId().equals(h.id()))).map(h->new Choice(h.id(),h.title())).toList(),id->{houseId=id;candidate=view.houses().stream().anyMatch(h->h.id().equals(id)&&h.defaultCandidate());})).active=view.editable();y+=26;
            button("Crown candidate: "+(candidate?"Yes":"No"),x,y,w,()->{candidate=!candidate;clearAndInit();}).active=view.editable();y+=28;
            button("Save assignment",x,y,w/2-3,()->send(Operation.ASSIGN)).active=view.editable()&&!houseId.isBlank()&&pending==null;
            button("Remove assignment",x+w/2+3,y,w/2-3,()->send(Operation.UNASSIGN)).active=view.editable()&&selected!=null&&!selected.houseId().isBlank()&&pending==null;y+=30;
            button("Prepare house papers…",x,y,w,()->{draw=true;clearAndInit();});y+=26;
            if(top+panelHeight-30-y>=24)body(view.reason().isBlank()?"Link this existing character to one house. Public biography and starting gear stay on their existing screens.":view.reason(),x,y,w,top+panelHeight-30-y);
            else if(!view.reason().isBlank())feedback=view.reason();
        }else{
            String status=view.committed()?"House papers are committed.":view.preview().equals(CouncilPayloads.NONE)?"Generate a draw, review each recipient, then commit it.":"Reviewing the selected character's proposed papers.";
            String preview=status+"\n\nSecrets\n"+String.join("\n\n",view.previewSecrets())+"\n\nHouse goals\n"+String.join("\n\n",view.previewGoals());
            body(preview,x,y,w,panelHeight-150);
            int row=top+panelHeight-72;
            button(view.preview().equals(CouncilPayloads.NONE)?"Generate draw":"Reroll draw",x,row,w/2-3,()->send(Operation.PREVIEW)).active=view.editable()&&pending==null;
            button("Commit papers…",x+w/2+3,row,w/2-3,()->client.setScreen(new ConfirmScreen(accepted->{client.setScreen(this);if(accepted)send(Operation.COMMIT);},Text.literal("Commit this character draw?"),Text.literal("Assignments and private papers become fixed for this experience. Reconnects and restarts will not reroll them.")))).active=view.editable()&&!view.preview().equals(CouncilPayloads.NONE)&&pending==null;
            button("Back to assignments",x,top+panelHeight-44,w,()->{draw=false;clearAndInit();});
        }
    }
    @Override public void tick(){if(pending!=null&&++waitingTicks>200){pending=null;feedback="The server did not answer. Reopen the current roster before retrying.";clearAndInit();}}
}
