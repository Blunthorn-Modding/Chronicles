package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

/** Operator setup is separate from the appointed Chancellor's proceedings. */
public final class HouseSetupScreen extends CouncilScreen {
    private AdminView view;
    private UUID pending;
    private int waitingTicks,tab;
    private String houseId;
    private boolean candidate;
    HouseSetupScreen(AdminView view){super("Council setup",null);apply(view);}
    private void apply(AdminView value){view=value;feedback=value.message();var target=value.players().stream().filter(p->p.id().equals(value.target())).findFirst().orElse(null);houseId=target==null?"":target.houseId();candidate=target!=null&&target.candidate();}
    void accept(AdminView value){if(!value.request().equals(pending))return;pending=null;waitingTicks=0;apply(value);clearAndInit();}
    void notice(Notice value){if(value.request().equals(pending)){pending=null;waitingTicks=0;feedback=value.message();clearAndInit();}}
    private void send(Operation operation){send(operation,view.target());}
    private void send(Operation operation,UUID target){if(pending!=null||!ClientPlayNetworking.canSend(AdminRequest.ID))return;pending=UUID.randomUUID();waitingTicks=0;ClientPlayNetworking.send(new AdminRequest(pending,operation,view.revision(),target,houseId,candidate,view.preview(),view.officeRevision()));}
    private void confirm(String title,String text,Runnable action){client.setScreen(new ConfirmScreen(ok->{client.setScreen(this);if(ok)action.run();},Text.literal(title),Text.literal(text)));}
    @Override protected void init(){
        frame();int x=left+12,y=top+34,w=panelWidth-24;
        var selected=view.players().stream().filter(p->p.id().equals(view.target())).findFirst().orElse(null);
        button(selected==null?"Choose person...":selected.label()+"...",x,y,w,()->choose("Choose person",view.players().stream().map(p->new Choice(p.id().toString(),p.label())).toList(),id->send(Operation.OPEN,UUID.fromString(id))));
        y+=25;int third=(w-8)/3;String[] tabs={"House","Chancellor","Papers"};
        for(int i=0;i<3;i++){int chosen=i;button(tabs[i],x+i*(third+4),y,third,()->{tab=chosen;clearAndInit();}).active=i!=tab;}
        y+=26;
        if(tab==0){
            var house=view.houses().stream().filter(h->h.id().equals(houseId)).findFirst().orElse(null);
            boolean officeHolder=view.target().equals(view.chancellor());
            var selectHouse=button(house==null?"Choose house...":house.title()+"...",x,y,w,()->choose("Choose house",view.houses().stream().filter(h->view.players().stream().noneMatch(p->!p.id().equals(view.target())&&p.houseId().equals(h.id()))).map(h->new Choice(h.id(),h.title())).toList(),id->{houseId=id;candidate=(view.editable()||(view.lateAssignable()&&!view.electionStarted()))&&view.houses().stream().anyMatch(h->h.id().equals(id)&&h.defaultCandidate());}));
            selectHouse.active=(view.editable()||view.lateAssignable())&&!officeHolder;
            if(officeHolder)selectHouse.setTooltip(Tooltip.of(Text.literal("The Chancellor cannot also represent a house.")));
            y+=24;button("Crown candidate: "+(candidate?"Yes":"No"),x,y,w,()->{candidate=!candidate;clearAndInit();}).active=(view.editable()||(view.lateAssignable()&&!view.electionStarted()))&&!officeHolder;
            y+=24;int half=(w-4)/2;
            button(view.lateAssignable()?"Assign late...":"Save assignment",x,y,half,()->{
                if(view.lateAssignable())confirm("Assign late?", "Assign "+(selected==null?"selected person":selected.label())+" to "+(house==null?houseId:house.title())+"? Existing papers and past ballots are preserved.",()->send(Operation.ASSIGN));
                else send(Operation.ASSIGN);
            }).active=(view.editable()||view.lateAssignable())&&!officeHolder&&!houseId.isBlank();
            button("Remove assignment",x+half+4,y,half,()->send(Operation.UNASSIGN)).active=view.editable()&&selected!=null&&!selected.houseId().isBlank();
            y+=27;if(y<top+panelHeight-27)body(view.lateAssignable()?"Add this character to an unused House. Existing papers and past ballots remain unchanged.":!view.assignmentReason().isBlank()?view.assignmentReason():view.reason().isBlank()?"House papers issue automatically when play first starts.":view.reason(),x,y,w,top+panelHeight-27-y);
        }else if(tab==1){
            String name=view.players().stream().filter(p->p.id().equals(view.chancellor())).map(Player::label).findFirst().orElse(view.chancellor().equals(CouncilPayloads.NONE)?"Not assigned":"Appointed person (offline)");
            int bottom=top+panelHeight-44;
            body("Current Chancellor\n"+name+"\n\nOperators can play a house; the appointed Chancellor cannot.",x,y,w,Math.max(20,bottom-28-y));
            var appoint=button("Appoint selected person...",x,bottom-24,w,()->confirm("Appoint Chancellor?","This person controls Council proceedings and cannot represent a house.",()->send(Operation.APPOINT_CHANCELLOR)));
            appoint.active=view.officeEditable()&&selected!=null&&selected.houseId().isBlank()&&!view.target().equals(view.chancellor());
            appoint.setTooltip(Tooltip.of(Text.literal(selected!=null&&!selected.houseId().isBlank()?"Remove this person's house assignment first.":view.officeReason().isBlank()?"Assign the selected person as Chancellor.":view.officeReason())));
            button("Clear Chancellor...",x,bottom,w,()->confirm("Clear Chancellor?","Proceedings will be unavailable until another Chancellor is appointed.",()->send(Operation.CLEAR_CHANCELLOR))).active=view.officeEditable()&&!view.chancellor().equals(CouncilPayloads.NONE);
        }else{
            String status=view.committed()?"House papers have been issued.":"House papers issue automatically when play first starts. No manual draw is required.";
            if(!view.preview().equals(CouncilPayloads.NONE))status+="\n\nDraft secrets\n"+String.join("\n\n",view.previewSecrets())+"\n\nDraft house goals\n"+String.join("\n\n",view.previewGoals());
            int row=top+panelHeight-68;body("papers",status,x,y,w,Math.max(20,row-4-y));
            int half=(w-4)/2;
            button("Preview draft",x,row,half,()->send(Operation.PREVIEW)).active=view.editable();
            button("Commit draft...",x+half+4,row,half,()->confirm("Commit this draw early?","Starting play will keep these exact papers.",()->send(Operation.COMMIT))).active=view.editable()&&!view.preview().equals(CouncilPayloads.NONE);
            var retry=button("Retry paper issuance",x,row+24,w,()->send(Operation.RETRY_PAPERS));retry.active=view.officeEditable()&&!view.committed();
            retry.setTooltip(Tooltip.of(Text.literal("Use only to resume a held automatic paper issue after play has started.")));
        }
    }
    @Override public void tick(){if(pending!=null&&++waitingTicks>200){pending=null;feedback="The server did not answer. Reopen setup before retrying.";}}
}
