package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.complications.ComplicationConfig;
import io.github.chronicle.lastcouncil.network.ComplicationPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Operator settings, with no projection of hidden scheduling or draws. */
final class ComplicationSettingsScreen extends CouncilScreen {
    private static final long MINUTE=1200;
    private View view; private UUID pending; private String pool,total,earliest,latest,spacing;
    private final SortedSet<Integer> meetings=new TreeSet<>(); private int page; private int tab;
    ComplicationSettingsScreen(View view,Screen parent){super("Council complications",parent);this.view=view;load();}
    private void load(){var c=view.config();pool=c.poolId();total=Integer.toString(c.total());earliest=Long.toString(c.earliestTicks()/MINUTE);latest=Long.toString(c.latestTicks()/MINUTE);spacing=Long.toString(c.spacingTicks()/MINUTE);meetings.clear();meetings.addAll(c.meetings());}
    void accept(View next){if(pending==null||!pending.equals(next.request()))return;pending=null;view=next;load();clearAndInit();}
    private void send(Action action,ComplicationConfig config,List<Integer> remaining,String reason){
        if(pending!=null||!ClientPlayNetworking.canSend(Request.ID)){message("Waiting for server or connection unavailable.");return;}
        pending=UUID.randomUUID();ClientPlayNetworking.send(new Request(pending,action,view.revision(),config,remaining,reason));message("Waiting for server...");
    }
    private boolean canEdit(){return view.editable()&&!view.started()&&pending==null;}
    @Override protected void init(){
        frame();int x=left+12,w=panelWidth-24,y=top+34;
        int tw=(w-8)/3;
        button("Settings",x,y,tw,()->{tab=0;clearAndInit();}).active=tab!=0;
        button("Timing",x+tw+4,y,tw,()->{tab=1;clearAndInit();}).active=tab!=1;
        button("Meetings",x+2*(tw+4),y,tw,()->{tab=2;clearAndInit();}).active=tab!=2;
        if(tab==2) meetingControls(x,y+25,w); else settingsControls(x,y+25,w);
        int bottom=top+panelHeight-38;
        if(!view.started())button("Save settings",x,bottom,110,this::save).active=canEdit();
        else {
            button("Cancel future...",x,bottom,105,this::cancelFuture).active=view.editable()&&pending==null&&view.config().total()>0;
            button("Early end...",x+109,bottom,90,()->confirm("Exceptional early end","Undelivered complications will be recorded as undelivered.",true,reason->send(Action.EARLY_END,null,List.of(),reason))).active=view.editable()&&pending==null&&view.config().total()>0;
        }
        if(tab==0&&view.recoverable()){button("Recover",x,y+124,70,()->send(Action.RECOVER,null,List.of(),"")).active=pending==null;if(view.uncertain())button("Acknowledge",x+74,y+124,90,()->confirm("Acknowledge delivery?","Keep the exact Event outcome without replaying uncertain media.",false,reason->send(Action.ACKNOWLEDGE,null,List.of(),reason))).active=pending==null;}
        message(!view.message().isBlank()?view.message():view.status());
    }
    private void settingsControls(int x,int y,int w){
        if(tab==0){
            body("Event pool",x,y,w,12);String label=view.pools().stream().filter(p->p.id().equals(pool)).map(PoolChoice::label).findFirst().orElse(pool);
            button(textRenderer.trimToWidth(label,w-12),x,y+13,w,()->choose("Choose Event pool",view.pools().stream().map(p->new Choice(p.id(),p.label())).toList(),id->{pool=id;})).active=canEdit();
            body("Evening total",x,y+40,w,12);field(x,y+52,w,total,v->total=v,canEdit());
            body("Zero disables complications for this evening.",x,y+76,w,20);
        }else{
            body("Earliest active Council minute",x,y,w,12);field(x,y+12,w,earliest,v->earliest=v,canEdit());
            body("Latest target minute",x,y+36,w,12);field(x,y+48,w,latest,v->latest=v,canEdit());
            body("Minimum spacing (minutes)",x,y+72,w,12);field(x,y+84,w,spacing,v->spacing=v,canEdit());
            body("Active Council minutes after opening.",x,y+108,w,20);
        }
    }
    private void field(int x,int y,int w,String value,java.util.function.Consumer<String> change,boolean enabled){var field=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Number")));field.setMaxLength(8);field.setText(value);field.setEditable(enabled);field.setChangedListener(change);}
    private void meetingControls(int x,int y,int w){
        body(view.started()?"Remove future meetings, then confirm.":"Select eligible meetings.",x,y,w,20);int start=page*8+1;
        for(int i=0;i<8;i++){int meeting=start+i;int col=i%2,row=i/2,bw=(w-4)/2;boolean selected=meetings.contains(meeting);
            boolean allowed=view.started()?view.editable()&&meeting>view.currentMeeting()&&view.config().meetings().contains(meeting)&&pending==null:canEdit();
            button((selected?"[x] ":"[ ] ")+"Meeting "+meeting,x+col*(bw+4),y+15+row*21,bw,()->{if(!meetings.remove(meeting))meetings.add(meeting);rebuildPreservingFocus();}).active=allowed;
        }
        button("< Previous",x,y+100,100,()->{page--;clearAndInit();}).active=page>0;
        button("Next >",x+104,y+100,100,()->{page++;clearAndInit();}).active=page<7;

    }
    private void save(){try{int n=Integer.parseInt(total);long a=Math.multiplyExact(Long.parseLong(earliest),MINUTE),b=Math.multiplyExact(Long.parseLong(latest),MINUTE),s=Math.multiplyExact(Long.parseLong(spacing),MINUTE);send(Action.SAVE,new ComplicationConfig(pool,n,List.copyOf(meetings),a,b,s),List.of(),"");}catch(IllegalArgumentException|ArithmeticException e){message("Check the numbers, meeting selection and timing capacity.");}}
    private void cancelFuture(){
        if(meetings.equals(new TreeSet<>(view.config().meetings()))){message("Remove at least one future opportunity first.");return;}
        boolean noFuture=meetings.stream().noneMatch(n->n>view.currentMeeting());
        confirm("Cancel future opportunities?",noFuture?"No future opportunity remains. Give a reason for the undelivered obligations.":"Remaining obligations will be redistributed across remaining eligible opportunities.",noFuture,reason->send(Action.CANCEL_FUTURE,null,List.copyOf(meetings),reason));
    }
    private void confirm(String title,String description,boolean required,java.util.function.Consumer<String> action){client.setScreen(new Confirmation(title,this,description,required,action));}
    private static final class Confirmation extends CouncilScreen {
        private final String description;private final boolean required;private final java.util.function.Consumer<String> action;private String reason="";
        Confirmation(String title,Screen parent,String description,boolean required,java.util.function.Consumer<String> action){super(title,parent);this.description=description;this.required=required;this.action=action;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;body(description,x,top+40,w,52);if(required){body("Reason (required)",x,top+98,w,12);var input=addDrawableChild(new TextFieldWidget(textRenderer,x,top+112,w,20,Text.literal("Reason")));input.setMaxLength(512);input.setText(reason);input.setChangedListener(v->reason=v);}
            button("Confirm",x,top+panelHeight-38,90,()->{if(required&&reason.isBlank()){message("Enter a reason.");return;}client.setScreen(parent);action.accept(reason.trim());});button("Cancel",x+94,top+panelHeight-38,90,this::close);}
    }
}
