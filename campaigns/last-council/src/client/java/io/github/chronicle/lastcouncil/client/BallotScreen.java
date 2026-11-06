package io.github.chronicle.lastcouncil.client;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;
public final class BallotScreen extends CouncilScreen {
    private Vote view;
    private UUID pending;
    private int waiting,offset;
    BallotScreen(Vote view,Screen parent) {
        super("Ballot",parent);
        this.view=view;
    }
    Vote snapshot() { return view; }
    void accept(Vote value) {
        if(!value.ballot().id().equals(view.ballot().id()))return;
        if(!value.request().equals(CouncilPayloads.NONE)&&!value.request().equals(pending))return;
        if(value.request().equals(pending)) {
            pending=null;
            waiting=0;
        }
        view=value;
        if(client.currentScreen==this)clearAndInit();
    }
    void notice(CouncilPayloads.Notice value) {
        if(value.request().equals(pending)) {
            pending=null;
            waiting=0;
            feedback=value.message();
            if(client.currentScreen==this)clearAndInit();
        }
    }
    private void send(Action action,String option) {
        if(pending!=null||!ClientPlayNetworking.canSend(Request.ID))return;
        pending=UUID.randomUUID();
        waiting=0;
        ClientPlayNetworking.send(new Request(pending,action,0,view.ballot().id(),"vote",option,"",false,false));
    }
    private String optionLabel(String id) {
        return view.ballot().options().stream().filter(o->o.id().equals(id)).map(o->o.label()).findFirst().orElse(id);
    }
    private String names() {
        StringBuilder out=new StringBuilder();
        view.ballot().namedVotes().forEach((house,
                choice)->out.append("\n").append(view.ballot().electorateLabels().getOrDefault(house,
                house)).append(": ").append(optionLabel(choice)));
        return out.toString();
    }
    private String totals() {
        StringBuilder out=new StringBuilder();
        view.ballot().totals().forEach((option,
                count)->out.append("\n").append(optionLabel(option)).append(": ").append(count));
        return out.toString();
    }
    @Override protected void init() {
        frame();
        int x=left+12,w=panelWidth-24,y=top+36;
        var b=view.ballot();
        String info=b.title()+"\n"+view.description()+"\nThreshold: "+b.threshold()+" of "
                +b.electorateLabels().size()+"; "+b.visibility()+"; "+b.status();
        if(b.ownChoice()!=null&&!b.ownChoice().isBlank())info+="\nYour choice: "+optionLabel(b.ownChoice());
        if(!view.reason().isBlank())info+="\n"+view.reason();
        if(b.reason()!=null&&!b.reason().isBlank())info+="\nReason: "+b.reason();
        if(b.visibility().name().equals("PUBLIC")&&!b.namedVotes().isEmpty())info+="\nVotes:"+names();
        if(b.status().name().equals("CLOSED")&&!b.totals().isEmpty())info+="\nTotals:"+totals();
        int available=top+panelHeight-32-y;
        int slots=Math.max(1,Math.min(3,(available-80)/24));
        int h=Math.max(40,available-(slots+2)*24);
        body(info,x,y,w,h);
        y+=h+2;
        offset=Math.clamp(offset,0,Math.max(0,b.options().size()-slots));
        for(int i=offset; i<Math.min(b.options().size(),offset+slots); i++) {
            var option=b.options().get(i);
            button(option.label(),x,y,w,()->client.setScreen(new ConfirmScreen(ok-> {
                client.setScreen(this); if(ok)send(Action.CAST,option.id());
            },Text.literal("Confirm your choice?"),Text.literal("Your first accepted choice is final: "
                    +option.label())))).active=view.canCast();
            y+=24;
        }
        if(b.options().size()>slots) {
            int half=(w-4)/2;
            button("Previous",x,y,half,()-> {
                offset=Math.max(0,offset-slots); clearAndInit();
            }).active=offset>0;
            button("Next",x+half+4,y,half,()-> {
                offset+=slots; clearAndInit();
            }).active=offset+slots<b.options().size();
            y+=24;
        }
        button("Refresh",x,y,w,()->send(Action.BALLOT,""));
    }
    @Override public void tick() {
        if(pending!=null&&++waiting>200) {
            pending=null;
            feedback="The server did not answer. Refresh before retrying.";
        }
    }
    @Override public void close() {
        if(parent instanceof CouncilBusinessScreen business)business.ballotReturned();
        if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(new Request(UUID.randomUUID(),
                Action.EXIT,0,CouncilPayloads.NONE,"vote","","",false,false));
        super.close();
    }
}
