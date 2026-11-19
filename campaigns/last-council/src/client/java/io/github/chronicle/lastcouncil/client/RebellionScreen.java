package io.github.chronicle.lastcouncil.client;
import io.github.chronicle.lastcouncil.network.RebellionPayloads;
import io.github.chronicle.lastcouncil.network.RebellionPayloads.*;
import java.util.*;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

/** Shared registry used by the personal screen and public command. */
final class RebellionScreen extends CouncilScreen {
    RebellionScreen(){super("Rebellions",null);}
    @Override protected void init(){frame();panel(this,left+12,top+36,panelWidth-24,panelHeight-74);}
    @Override public void close(){RebellionClient.close();super.close();}
    static void panel(CouncilScreen screen,int x,int y,int w,int h){
        View v=RebellionClient.view();if(v==null){screen.body("Reading rebellions...",x,y,w,h);return;}
        boolean compact=w<230,hasRows=!v.rows().isEmpty();int list=hasRows?(compact?w:Math.max(82,w/3)):0;
        var choices=v.rows().stream().map(r->new Choice(r.id().toString(),r.title())).toList();String selected=v.detail()==null?"":v.detail().id().toString();
        if(hasRows){if(compact)screen.compactRows("rebellion-list",choices,selected,x,y,w,!RebellionClient.busy(),id->RebellionClient.send(Action.STATE,UUID.fromString(id),"",""));
            else screen.rows("rebellion-list",choices,selected,x,y,list,h-24,!RebellionClient.busy(),id->RebellionClient.send(Action.STATE,UUID.fromString(id),"",""));}
        int dx=compact||!hasRows?x:x+list+6,dy=compact&&hasRows?y+24:y,dw=compact||!hasRows?w:w-list-6;
        List<SectionCard> cards=new ArrayList<>();
        if(!compact)cards.add(new SectionCard("Council participation",v.ownHouse().isBlank()?"No House assigned":v.refusal().isBlank()?"Your House remains an active participant.":v.refusal()));
        Detail d=v.detail();if(d==null)cards.add(new SectionCard("Rebellions","No rebellion selected."));else{cards.add(new SectionCard(d.title(),"Founded by "+d.founder()));cards.add(new SectionCard("Public statement",d.statement()));cards.add(new SectionCard("Houses",String.join("\n",d.houses())));cards.add(new SectionCard("Public record",String.join("\n",d.history())));}
        screen.sections("rebellion-detail-"+(d==null?"none":d.id()),cards,dx,dy,dw,Math.max(20,y+h-24-dy));
        int bw=(dw-4)/2;screen.button("Declare...",dx,y+h-20,bw,()->RebellionClient.child(new Declare(screen))).active=v.canDeclare()&&!RebellionClient.busy();
        screen.button("Join...",dx+bw+4,y+h-20,bw,()->confirm(screen,d,false,"",null)).active=d!=null&&v.canJoin()&&!RebellionClient.busy();
    }
    private static final String WARNING="Your House loses its Council seat and palace access. You cannot vote, propose or sponsor motions, or be a crown candidate. Rebellion withdraws you from an ongoing succession; reinstatement cannot return you to a locked or advanced slate. You remain an active participant.";
    private static void confirm(CouncilScreen owner,Detail detail,boolean declare,String statement,Declare draft){
        RebellionClient.child(new RebellionConfirm(owner,declare?"Confirm rebellion?":"Join rebellion?",List.of(new SectionCard(declare?"Public statement":"Faction",declare?statement:detail.title()),new SectionCard("Consequences",WARNING)),()->{if(draft!=null)RebellionClient.retainDraft(draft);RebellionClient.send(declare?Action.DECLARE:Action.JOIN,declare?RebellionPayloads.NONE:detail.id(),statement,"");},draft==null?RebellionClient::returnToOwner:()->RebellionClient.child(draft)));
    }
    static final class Declare extends CouncilScreen {
        private String statement="";private final CouncilScreen owner;
        Declare(CouncilScreen owner){super("Declare rebellion",owner);this.owner=owner;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+36;body("Public statement",x,y,w,16);int eh=Math.max(30,Math.min(70,panelHeight-190));EditBoxWidget box=addDrawableChild(EditBoxWidget.builder().x(x).y(y+18).build(textRenderer,w,eh,Text.literal("Public statement")));box.setMaxLength(512);box.setText(statement);box.setChangeListener(s->statement=s);sections("warning",List.of(new SectionCard("Consequences",WARNING)),x,y+22+eh,w,Math.max(20,panelHeight-120-eh));button("Cancel",x,top+panelHeight-38,(w-4)/2,this::close);button(RebellionClient.needsRefresh()?"Refresh":"Review...",x+(w+4)/2,top+panelHeight-38,(w-4)/2,()->{if(RebellionClient.needsRefresh()){RebellionClient.refresh();return;}if(statement.isBlank()){feedback="Enter a public statement.";return;}confirm(owner,null,true,statement,this);}).active=RebellionClient.needsRefresh()||(RebellionClient.view()!=null&&RebellionClient.view().canDeclare()&&!RebellionClient.busy());}
        @Override public void close(){RebellionClient.abandonDraft();RebellionClient.returnToOwner();}
    }
}
