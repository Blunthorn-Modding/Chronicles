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
    private boolean invalidated;
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
        invalidated=false;
        if(!value.message().isBlank())feedback=value.message();
        if(client.currentScreen==this)rebuildPreservingFocus();
    }
    void notice(CouncilPayloads.Notice value) {
        boolean global=value.request().equals(CouncilPayloads.NONE)&&pending.isEmpty();
        if(global||pending.remove(value.request())!=null) {
            if(global)invalidated=true;
            ages.remove(value.request());
            feedback=value.message();
            if(client.currentScreen==this)rebuildPreservingFocus();
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
        send(action,ref,text,title,secret,thirds,0);
    }
    private void send(Action action,UUID ref,String text,String title,boolean secret,boolean thirds,int historyPage) {
        boolean read=action==Action.STATE||action==Action.MOTIONS||action==Action.BALLOT;
        if(pending.containsValue(action)||(!read&&pending.values().stream().anyMatch(a->a!=Action.STATE&&a!=Action.MOTIONS&&a!=Action.BALLOT))||!ClientPlayNetworking.canSend(Request.ID))return;
        feedback="";
        UUID id=UUID.randomUUID();
        pending.put(id,action);
        ages.put(id,0);
        if(action==Action.BALLOT)BallotAccess.expectOpening(id,this,ref);
        ClientPlayNetworking.send(new Request(id,action,view.revision(),ref,view.site(),text,title,secret,thirds,historyPage));
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
        if(invalidated){body("The server could not update this view. Close and reopen it.\n\n"+feedback,left+12,top+36,panelWidth-24,panelHeight-68);return;}
        int x=left+10,y=top+32,h=panelHeight-62,w=panelWidth-20;
        boolean proceedings=view.mode()==Mode.PROCEEDINGS;
        boolean compact=panelWidth<380;
        int nav=proceedings?(compact?0:100):0;
        int list=compact?0:(proceedings?105:142);
        int listX=x+(proceedings&&!compact?nav+4:0), detailX=compact?x:listX+list+4, detailW=left+panelWidth-10-detailX;
        int listY=y, listH=h;
        if(compact) {
            if(proceedings) {
                boolean active=view.activeBallot()!=null&&!view.activeBallot().equals(CouncilPayloads.NONE);
                String phase=view.phase().toUpperCase(Locale.ROOT);
                Action phaseAction=phase.equals("DISCUSSION")?Action.RECESS:phase.equals("RECESS")?Action.RESUME:Action.START;
                List<Choice> actions=new ArrayList<>();
                actions.add(new Choice("call","Call meeting")); actions.add(new Choice("phase",phaseAction==Action.RECESS?"Recess":phaseAction==Action.RESUME?"Resume":"Open meeting"));
                actions.add(new Choice("prepare","Prepare motion...")); actions.add(new Choice("motions","Motions"));
                actions.add(new Choice("ballot",view.recoverable()?"Recover":active?"View ballot":"Ballot history"));
                actions.add(new Choice("regency","Regency vote...")); actions.add(new Choice("finish","Close meeting"));
                compactRows("proceedings-actions",actions,"",x,y,w,true,id->{
                    switch(id) {
                        case "call" -> { if(view.playing()&&!view.recoverable())send(Action.CALL); }
                        case "phase" -> { if(view.playing()&&!active&&!view.recoverable())send(phaseAction); }
                        case "prepare" -> draft(Action.PREPARE);
                        case "motions" -> { if(!active)send(Action.STATE,CouncilPayloads.NONE); }
                        case "ballot" -> { if(view.recoverable())send(Action.RECOVER); else if(active)send(Action.BALLOT,view.activeBallot()); else choose("Recorded ballots",view.history().stream().map(v->new Choice(v.id().toString(),v.title())).toList(),v->send(Action.BALLOT,UUID.fromString(v))); }
                        case "regency" -> { if(view.regency().canOpen())confirm("Open the regency ballot?","The electorate is fixed at opening.",()->send(Action.OPEN_REGENCY,CouncilPayloads.NONE)); else feedback=view.regency().summary(); }
                        case "finish" -> { if(!active&&(phase.equals("DISCUSSION")||phase.equals("RECESS")))confirm("Close meeting?","This closes the current meeting.",()->send(Action.FINISH)); }
                    }
                });
                listY+=24;
            }
            listH=20; y=listY+24; h=top+panelHeight-30-y;
        }
        if(proceedings&&!compact) {
            boolean active=view.activeBallot()!=null&&!view.activeBallot().equals(CouncilPayloads.NONE);
            String phase=view.phase().toUpperCase(Locale.ROOT);
            Action phaseAction=phase.equals("DISCUSSION")?Action.RECESS:phase.equals("RECESS")?Action.RESUME:Action.START;
            String phaseLabel=phaseAction==Action.RECESS?"Recess":phaseAction==Action.RESUME?"Resume":"Open meeting";
            button("Call meeting",x,y,nav,()->send(Action.CALL)).active=view.playing()&&!view.recoverable();
            button(phaseLabel,x,y+22,nav,()->send(phaseAction)).active=view.playing()&&!active&&!view.recoverable();
            button("Prepare motion...",x,y+44,nav,()->draft(Action.PREPARE));
            button("Motions",x,y+66,nav,()->send(Action.STATE,CouncilPayloads.NONE)).active=!active;
            if(view.recoverable()) button("Recover",x,y+88,nav,()->send(Action.RECOVER));
            else if(active) button("View ballot",x,y+88,nav,()->send(Action.BALLOT,view.activeBallot()));
            else button("Ballot history",x,y+88,nav,()->choose("Recorded ballots",view.history().stream().map(v->new Choice(v.id().toString(),v.title())).toList(),id->send(Action.BALLOT,UUID.fromString(id))));
            var regencyButton=button("Regency vote...",x,y+h-42,nav,()->confirm("Open the regency ballot?","The electorate is fixed at opening.",()->send(Action.OPEN_REGENCY,CouncilPayloads.NONE)));
            regencyButton.active=view.regency().canOpen();
            regencyButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(view.regency().summary().isBlank()?"Regency":view.regency().summary())));
            button("Close meeting",x,y+h-20,nav,()->confirm("Close meeting?","This closes the current meeting.",()->send(Action.FINISH))).active=!active&&(phase.equals("DISCUSSION")||phase.equals("RECESS"));
        }
        String chosen=view.detail()==null?"":view.detail().id().toString();
        var motionChoices=view.motions().stream().map(m->new Choice(m.id().toString(),m.title()+" ["+label(m.status())+"]")).toList();
        boolean motionEnabled=!(proceedings&&view.activeBallot()!=null&&!view.activeBallot().equals(CouncilPayloads.NONE));
        if(compact)compactRows("motions",motionChoices,chosen,listX,listY,w,motionEnabled,id->send(proceedings?Action.STATE:Action.MOTIONS,UUID.fromString(id)));
        else rows("motions",motionChoices,chosen,listX,listY,list,listH,motionEnabled,id->send(proceedings?Action.STATE:Action.MOTIONS,UUID.fromString(id)));
        var d=view.detail();
        int actionRows=proceedings?2:view.chancellor()?0:3;
        boolean historyPaging=d!=null&&d.historyPages()>1;
        int detailHeight=Math.max(20,h-actionRows*22-(historyPaging?22:0));
        if(d==null&&proceedings&&view.progress()!=null) {
            var progress=view.progress();
            String text=progress.submitted()==progress.electorate()?"All "+progress.electorate()+" votes submitted":progress.submitted()+" of "+progress.electorate()+" votes submitted";
            if(!progress.outstanding().isEmpty())text+="\nOutstanding: "+String.join(", ",progress.outstanding());
            sections("active-progress",List.of(new SectionCard("Regency",view.regency().summary()),new SectionCard("Vote progress",text)),detailX,y,detailW,detailHeight);
        } else if(d==null)body("motion-detail",view.problem().isBlank()?(proceedings?"Meeting "+view.meeting()+" | "+label(view.phase())+"\n\n"+view.regency().summary()+"\n\nSelect a motion.":"Select a motion."):view.problem(),detailX,y,detailW,detailHeight);
        else {
            List<SectionCard> cards=new ArrayList<>();
            CouncilBallotSections.motion(cards,d);
            if(!view.problem().isBlank())cards.add(new SectionCard("Notice",view.problem()));
            if(proceedings&&view.progress()!=null) {
                var progress=view.progress();
                String text=progress.submitted()==progress.electorate()?"All "+progress.electorate()+" votes submitted":progress.submitted()+" of "+progress.electorate()+" votes submitted";
                if(!progress.outstanding().isEmpty())text+="\nOutstanding: "+String.join(", ",progress.outstanding());
                cards.add(Math.min(2,cards.size()),new SectionCard("Vote progress",text));
            }
            sections("motion-detail-"+selected(),cards,detailX,y,detailW,detailHeight);
        }
        int ay=y+h-actionRows*22;
        if(historyPaging) {
            int half=(detailW-4)/2;
            Action readAction=proceedings?Action.STATE:Action.MOTIONS;
            button("Newer",detailX,ay-22,half,()->send(readAction,d.id(),"","",false,false,d.historyPage()-1)).active=d.historyPage()>0;
            button("Older",detailX+half+4,ay-22,half,()->send(readAction,d.id(),"","",false,false,d.historyPage()+1)).active=d.historyPage()+1<d.historyPages();
        }
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
                feedback="The server did not answer. Reopen proceedings before retrying.";
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
