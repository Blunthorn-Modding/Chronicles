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
    View snapshot(){return view;}
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
        int x=left+10,y=top+32,h=panelHeight-62,w=panelWidth-20;
        boolean proceedings=view.mode()==Mode.PROCEEDINGS;
        int nav=proceedings?(panelWidth<380?84:130):0;
        int list=proceedings?(panelWidth<380?72:105):(panelWidth<380?98:142);
        int listX=x+(proceedings?nav+4:0), detailX=listX+list+4, detailW=left+panelWidth-10-detailX;
        if(proceedings) {
            boolean active=view.activeBallot()!=null&&!view.activeBallot().equals(CouncilPayloads.NONE);
            String phase=view.phase().toUpperCase(Locale.ROOT);
            Action phaseAction=phase.equals("DISCUSSION")?Action.RECESS:phase.equals("RECESS")?Action.RESUME:Action.START;
            String phaseLabel=phaseAction==Action.RECESS?"Recess":phaseAction==Action.RESUME?"Resume":"Open meeting";
            button("Call meeting",x,y,nav,()->send(Action.CALL)).active=view.playing()&&!view.recoverable();
            button(phaseLabel,x,y+22,nav,()->send(phaseAction)).active=view.playing()&&!active&&!view.recoverable();
            button("Prepare motion...",x,y+44,nav,()->draft(Action.PREPARE));
            button("Motions",x,y+66,nav,()->send(Action.STATE,CouncilPayloads.NONE)).active=!active;
            if(active) button("View ballot",x,y+88,nav,()->send(Action.BALLOT,view.activeBallot()));
            else if(view.recoverable()) button("Recover",x,y+88,nav,()->send(Action.RECOVER));
            else button("Ballot history",x,y+88,nav,()->choose("Recorded ballots",view.history().stream().map(v->new Choice(v.id().toString(),v.title())).toList(),id->send(Action.BALLOT,UUID.fromString(id))));
            var regencyButton=button("Regency vote...",x,y+h-42,nav,()->confirm("Open the regency ballot?","The electorate is fixed at opening.",()->send(Action.OPEN_REGENCY,CouncilPayloads.NONE)));
            regencyButton.active=view.regency().canOpen();
            regencyButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(view.regency().summary().isBlank()?"Regency":view.regency().summary())));
            button("Close meeting",x,y+h-20,nav,()->confirm("Close meeting?","This closes the current meeting.",()->send(Action.FINISH))).active=!active&&(phase.equals("DISCUSSION")||phase.equals("RECESS"));
        }
        String chosen=view.detail()==null?"":view.detail().id().toString();
        rows("motions",view.motions().stream().map(m->new Choice(m.id().toString(),m.title()+" ["+label(m.status())+"]")).toList(),chosen,listX,y,list,h,!(proceedings&&view.activeBallot()!=null&&!view.activeBallot().equals(CouncilPayloads.NONE)),id->send(proceedings?Action.STATE:Action.MOTIONS,UUID.fromString(id)));
        var d=view.detail();
        int actionRows=proceedings?2:view.chancellor()?0:3;
        int detailHeight=Math.max(20,h-actionRows*22);
        if(d==null)body("motion-detail",view.problem().isBlank()?(proceedings?"Meeting "+view.meeting()+" | "+label(view.phase())+"\n\n"+view.regency().summary()+"\n\nSelect a motion.":"Select a motion."):view.problem(),detailX,y,detailW,detailHeight);
        else {
            String details="Proposed by "+d.proposer()+"\n"+label(d.status())+" | "+label(d.visibility())+" | "+label(d.threshold())+"\nSponsors: "+String.join(", ",d.sponsors())+(d.notes().isEmpty()?"":"\nNotes: "+String.join("; ",d.notes()));
            List<SectionCard> cards=new ArrayList<>();
            cards.add(new SectionCard("Motion",d.title()));
            if(!view.problem().isBlank())cards.add(new SectionCard("Notice",view.problem()));
            cards.add(new SectionCard("Description",d.text()));
            cards.add(new SectionCard("Details",details));
            sections("motion-detail-"+selected(),cards,detailX,y,detailW,detailHeight);
        }
        int ay=y+h-actionRows*22;
        if(proceedings){
            boolean active=view.activeBallot()!=null&&!view.activeBallot().equals(CouncilPayloads.NONE);
            if(active){UUID ballot=view.activeBallot();button("Close ballot...",detailX,ay,detailW,()->confirm("Close ballot?","This records the result.",()->send(Action.CLOSE_BALLOT,ballot)));button("Void ballot...",detailX,ay+22,detailW,()->reason(Action.VOID_BALLOT,ballot,512));}
            else {button(d!=null&&d.canAdmit()?"Admit motion":"Open vote",detailX,ay,detailW,()->{if(d!=null&&d.canAdmit())send(Action.ADMIT);else confirm("Open this ballot?","The electorate will be fixed when voting opens.",()->send(Action.OPEN_BALLOT));}).active=d!=null&&(d.canAdmit()||d.canOpen());
                button("Defer...",detailX,ay+22,detailW,()->reason(Action.DEFER,selected(),400)).active=d!=null&&d.canDefer();}
        } else if(!view.chancellor()) {
            button("Propose...",detailX,ay,detailW,()->draft(Action.PROPOSE)).active=view.canPropose();
            button("Sponsor",detailX,ay+22,detailW,()->send(Action.SPONSOR)).active=d!=null&&d.canSponsor();
            button("Withdraw...",detailX,ay+44,detailW,()->confirm("Withdraw proposal?","This withdraws the selected proposal.",()->send(Action.WITHDRAW))).active=d!=null&&d.canWithdraw();
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
            case "DEFERRED"->"Deferred";
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
