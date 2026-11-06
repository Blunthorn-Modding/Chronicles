package io.github.chronicle.lastcouncil.client;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;
public final class CouncilBusinessScreen extends CouncilScreen {
    private View view;
    private final Map<UUID,Action> pending=new HashMap<>();
    private final Map<UUID,Integer> ages=new HashMap<>();
    CouncilBusinessScreen(View view,Screen parent) {
        super(view.mode()==Mode.PROCEEDINGS?"Council proceedings":"Motion lectern",parent);
        this.view=view;
        feedback=view.message();
    }
    void accept(View value) {
        if(value.mode()!=view.mode())return;
        if(!value.request().equals(CouncilPayloads.NONE)&&pending.remove(value.request())==null)return;
        ages.remove(value.request());
        if(value.revision()<view.revision())return;
        view=value;
        if(!value.message().isBlank())feedback=value.message();
        if(client.currentScreen==this)clearAndInit();
    }
    void notice(CouncilPayloads.Notice value) {
        if(pending.remove(value.request())!=null) {
            ages.remove(value.request());
            feedback=value.message();
            if(client.currentScreen==this)clearAndInit();
        }
    }
    void ballotReturned() {
        for(UUID id:new ArrayList<>(pending.keySet()))if(pending.get(id)==Action.BALLOT) {
            pending.remove(id);
            ages.remove(id);
        }
    }
    private UUID selected() {
        return view.detail()==null?CouncilPayloads.NONE:view.detail().id();
    }
    private void send(Action action,UUID ref,String text,String title,boolean secret,boolean thirds) {
        boolean read=action==Action.STATE||action==Action.MOTIONS||action==Action.BALLOT;
        if(pending.containsValue(action)||(!read&&pending.values().stream().anyMatch(a->a!=Action.STATE&&a!=Action.MOTIONS&&a!=Action.BALLOT))||!ClientPlayNetworking.canSend(Request.ID))return;
        feedback="";
        UUID id=UUID.randomUUID();
        pending.put(id,action);
        ages.put(id,0);
        if(action==Action.BALLOT)BallotAccess.expectOpening(id,this,ref);
        ClientPlayNetworking.send(new Request(id,action,view.revision(),ref,view.site(),text,title,secret,thirds));
    }
    private void send(Action action,UUID ref) {
        send(action,ref,"","",false,false);
    }
    private void send(Action action) {
        send(action,selected());
    }
    private void confirm(String title,String description,Runnable action) {
        client.setScreen(new ConfirmScreen(ok-> {
            client.setScreen(this); if(ok)action.run();
        },Text.literal(title),Text.literal(description)));
    }
    private boolean mutationPending() {
        return pending.values().stream().anyMatch(a->a!=Action.STATE&&a!=Action.MOTIONS&&a!=Action.BALLOT);
    }
    private void reason(Action action,UUID ref,int max) {
        if(mutationPending()) {
            feedback="Wait for the current action to finish.";
            return;
        }
        client.setScreen(new CouncilReasonScreen(action==Action.DEFER?"Defer motion":"Void ballot",this,max,
                text->send(action,ref,text,"",false,false)));
    }
    @Override protected void init() {
        frame();
        int x=left+12,w=panelWidth-24,y=top+36,gap=4,half=(w-gap)/2;
        button("Refresh",x,y,half,()->send(view.mode()==Mode.PROCEEDINGS?Action.STATE:Action.MOTIONS));
        button("Choose motion...",x+half+gap,y,half,()->choose("Motions",
                view.motions().stream().map(m->new Choice(m.id().toString(),m.title()+" ["+label(m.status())
                +"]")).toList(),id->send(view.mode()==Mode.PROCEEDINGS?Action.STATE:Action.MOTIONS,UUID.fromString(id))));
        y+=24;
        var d=view.detail();
        String summary=view.mode()==Mode.PROCEEDINGS?"Meeting "+view.meeting()+" - "+label(view.phase()):"Council motions";
        if(!view.problem().isBlank())summary+="\n"+view.problem();
        if(view.mode()==Mode.PROCEEDINGS)summary+="\n\n"+view.regency().summary()+(view.regency().candidates().isEmpty()?"":"\nCandidates: "+String.join(", ",view.regency().candidates()));
        if(d!=null)summary+="\n\n"+d.title()+"\n\n"+d.text()+"\n\nProposed by "+d.proposer()
                +" - "+label(d.status())+"\n"+label(d.visibility())+"; "+label(d.threshold())
                +"\nSponsors: "+String.join(", ",d.sponsors())
                +(!d.notes().isEmpty()?"\n\nNotes: "+String.join("; ",d.notes()):"");
        else summary+="\n\nChoose a motion to read its details.";
        int bottom=top+panelHeight-30;
        int rows=view.mode()==Mode.PROCEEDINGS?5:view.operator()?3:2;
        int bodyHeight=Math.max(32,bottom-y-rows*24);
        body(summary,x,y,w,bodyHeight);
        y+=bodyHeight+2;
        if(view.mode()==Mode.PROCEEDINGS) {
            boolean active=view.activeBallot()!=null&&!view.activeBallot().equals(CouncilPayloads.NONE);
            String phase=view.phase().toUpperCase(Locale.ROOT);
            Action phaseAction=phase.equals("DISCUSSION")?Action.RECESS:phase.equals("RECESS")?Action.RESUME:Action.START;
            String phaseLabel=phaseAction==Action.RECESS?"Recess":phaseAction==Action.RESUME?"Resume":"Open meeting";
            button("Call meeting",x,y,half,()->send(Action.CALL)).active=view.playing()&&!view.recoverable();
            button(phaseLabel,x+half+gap,y,half,()->send(phaseAction)).active=view.playing()&&!active&&!view.recoverable();
            y+=24;
            button("Request regency vote...",x,y,w,()->confirm("Open the regency ballot?",
                    "The electorate is fixed at opening. A completed regency vote uses this meeting's succession slot.",()->send(Action.OPEN_REGENCY,CouncilPayloads.NONE))).active=view.regency().canOpen();
            y+=24;
            button("Prepare motion...",x,y,half,()->draft(Action.PREPARE));
            button("Recorded ballots...",x+half+gap,y,half,()->choose("Recorded ballots",
                    view.history().stream().map(h->new Choice(h.id().toString(),h.title()+" ["+label(h.status())
                    +"]")).toList(),id->send(Action.BALLOT,UUID.fromString(id))));
            y+=24;
            if(active)button("View ballot",x,y,half,()->send(Action.BALLOT,view.activeBallot()));
            else if(d!=null&&d.canOpen())button("Open vote",x,y,half,()->confirm("Open this ballot?",
                    "The electorate will be fixed when voting opens.",()->send(Action.OPEN_BALLOT)));
            else button("Admit motion",x,y,half,()->send(Action.ADMIT)).active=d!=null&&d.canAdmit();
            if(d!=null&&!active)button("Defer motion...",x+half+gap,y,half,()->reason(Action.DEFER,selected(),400));
            else button("Defer motion...",x+half+gap,y,half,()-> {
            }).active=false;
            y+=24;
            if(view.recoverable())button("Recover operation",x,y,w,()->send(Action.RECOVER));
            else if(active) {
                UUID ballot=view.activeBallot();
                button("Close ballot...",x,y,half,()->confirm("Close ballot?","This records the result.",
                        ()->send(Action.CLOSE_BALLOT,ballot)));
                button("Void ballot...",x+half+gap,y,half,()->reason(Action.VOID_BALLOT,ballot,512));
            }
            else button("Close meeting",x,y,w,()->confirm("Close meeting?","This closes the current meeting.",
                    ()->send(Action.FINISH))).active=phase.equals("DISCUSSION")||phase.equals("RECESS");
        }
        else {
            button("Propose motion...",x,y,half,()->draft(Action.PROPOSE));
            button("Sponsor",x+half+gap,y,half,()->send(Action.SPONSOR)).active=d!=null&&d.canSponsor();
            y+=24;
            button("Withdraw...",x,y,half,()->confirm("Withdraw proposal?","This withdraws the selected proposal.",
                    ()->send(Action.WITHDRAW))).active=d!=null&&d.canWithdraw();
            if(view.operator())button("Prepare motion...",x+half+gap,y,half,()->draft(Action.PREPARE));
            else button("Recorded ballots...",x+half+gap,y,half,()->choose("Recorded ballots",
                    view.history().stream().filter(h->!h.status().equals("OPEN")).map(h->new Choice(h.id().toString(),h.title()+" ["+label(h.status())+"]")).toList(),id->send(Action.BALLOT,UUID.fromString(id))));
            y+=24;
            if(view.operator()) {
                button("Admit motion",x,y,half,()->send(Action.ADMIT)).active=d!=null&&d.canAdmit();
                button("Proceedings",x+half+gap,y,half,()-> {
                    if(client.player!=null)client.player.networkHandler.sendChatCommand("chronicle council proceedings");
                });
            }
        }
    }
    private static String label(String text) {
        return switch(text) {
            case "BETWEEN"->"Between meetings";
            case "GATHERING"->"Gathering";
            case "DISCUSSION"->"Discussion";
            case "RECESS"->"Recess";
            case "SUBMITTED"->"Awaiting agenda";
            case "AGENDA"->"On agenda";
            case "VOTING"->"Vote open";
            case "RECORDED"->"Recorded";
            case "WITHDRAWN"->"Withdrawn";
            case "TWO_THIRDS"->"Two thirds";
            case "MAJORITY"->"Majority";
            case "PUBLIC"->"Public";
            case "SECRET"->"Secret";
            case "OPEN"->"Open";
            case "CLOSED"->"Closed";
            case "VOID"->"Void";
            default->text;
        };
    }
    private void draft(Action action) {
        if(mutationPending()) {
            feedback="Wait for the current action to finish.";
            return;
        }
        client.setScreen(new MotionDraftScreen(this,(title,text,secret,thirds)->send(action,CouncilPayloads.NONE,
                text,title,secret,thirds)));
    }
    @Override public void tick() {
        for(UUID id:new ArrayList<>(ages.keySet())) {
            int age=ages.get(id)+1;
            if(age>200) {
                ages.remove(id);
                pending.remove(id);
                feedback="The server did not answer. Refresh before retrying.";
            }
            else ages.put(id,age);
        }
    }
    @Override public void close() {
        if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(new Request(UUID.randomUUID(),
                Action.EXIT,view.revision(),CouncilPayloads.NONE,
                view.mode()==Mode.PROCEEDINGS?"proceedings":"motions","","",false,false));
        super.close();
    }
}
