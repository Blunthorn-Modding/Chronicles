package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.ConflictPayloads;
import io.github.chronicle.lastcouncil.network.ConflictPayloads.*;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

/** Compact shared list/detail frame with focused, consequential confirmations. */
public final class ConflictScreen extends CouncilScreen {
    private View view;private String reason="",threshold="";
    ConflictScreen(View view){super(view.settings()?"Conflicts settings":view.challenge()!=null?"Challenge representative":"Conflicts",null);this.view=view;threshold=Integer.toString(view.threshold());}
    public View view(){return view;}
    void accept(View next){if(next.revision()<view.revision())return;boolean settingsChanged=next.threshold()!=view.threshold();view=next;if(settingsChanged)threshold=Integer.toString(next.threshold());if(client.currentScreen==this)rebuildPreservingFocus();}
    @Override public void close(){ConflictClient.closed(this);super.close();}
    private void request(Action action,UUID id,long revision,String side,String text,int number){ConflictClient.send(new Request(UUID.randomUUID(),action,view.token(),id,revision,side,text,number));}
    private void child(Screen child){ConflictClient.child(child);client.setScreen(child);}
    @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+36,bottom=top+panelHeight-38;
        if(view.settings()){body("Civil war label threshold (2 to 16 houses).",x,y,w,28);TextFieldWidget input=addDrawableChild(new TextFieldWidget(textRenderer,x,y+32,w,20,Text.literal("Civil war threshold")));input.setMaxLength(2);input.setText(threshold);input.setChangedListener(t->threshold=t);input.active=view.editable();button("Save",x,bottom,w,()->{try{int n=Integer.parseInt(threshold);if(n<2||n>16)throw new NumberFormatException();request(Action.SAVE_SETTINGS,ConflictPayloads.NONE,view.threshold(),"","",n);}catch(NumberFormatException e){feedback="Use a whole number from 2 to 16.";}}).active=view.editable();message(view.message().isBlank()&&!view.editable()?"Pause play and enable Editing to change settings.":view.message());return;}
        if(view.challenge()!=null){var c=view.challenge();int inputH=Math.clamp(panelHeight-175,30,65);sections("target",List.of(new SectionCard(c.targetName(),c.targetHouse())),x,y,w,44);EditBoxWidget input=addDrawableChild(EditBoxWidget.builder().x(x).y(y+48).placeholder(Text.literal("Public reason")).build(textRenderer,w,inputH,Text.literal("Public reason")));input.setMaxLength(512);input.setText(reason);input.setChangeListener(t->reason=t);String warning=c.privateWarning().isBlank()?"The declaration is unilateral.":c.privateWarning();body("challenge-warning",warning,x,y+52+inputH,w,Math.max(20,bottom-y-inputH-58));button("Review declaration",x,bottom,w,()->{if(reason.isBlank()){feedback="Enter a public reason.";return;}List<SectionCard> review=new ArrayList<>();if(!c.privateWarning().isBlank())review.add(new SectionCard("Agreement notice",c.privateWarning()));review.add(new SectionCard(c.targetName(),c.targetHouse()));review.add(new SectionCard("Public reason",reason));review.add(new SectionCard("Confirm","This declaration is unilateral. Your House keeps its Council seat and palace access."));child(new Confirmation(this,"Declare war?",review,()->request(Action.DECLARE,ConflictPayloads.NONE,0,"",reason,0)));});message(view.message());return;}
        int filterWidth=(w-4)/2;
        button("House conflicts",x,y,filterWidth,()->{}).active=false;
        button("Foreign affairs",x+filterWidth+4,y,filterWidth,()->{ConflictClient.closed(this);ForeignClient.open();});
        y+=24;
        var d=view.detail();boolean compact=w<350;int lw=compact?w:Math.max(110,w/3),lh=compact?(view.rows().size()>1?46:22):bottom-y-4;
        rows("conflicts",view.rows().stream().map(r->new Choice(r.id().toString(),r.title()+" / "+friendly(r.status()))).toList(),d==null?"":d.id().toString(),x,y,lw,lh,id->request(Action.STATE,UUID.fromString(id),0,"","",0));
        int dx=compact?x:x+lw+6,dy=compact?y+lh+4:y,dw=compact?w:w-lw-6;List<SectionCard> cards=new ArrayList<>();
        if(d==null)cards.add(new SectionCard("Conflicts","No conflicts have been declared. Use a Challenge token on another House representative to begin."));
        else{cards.add(new SectionCard(d.title(),friendly(d.status())));cards.add(new SectionCard("Public reason",d.reason()));cards.add(new SectionCard(d.first().getFirst().title()+" side",names(d.first())));cards.add(new SectionCard(d.second().getFirst().title()+" side",names(d.second())));cards.add(new SectionCard("Public record",String.join("\n",d.history())));if(d.founder()&&d.canAct())cards.add(new SectionCard("Founding House","Leave through an agreed peace at the treaty lectern."));}
        sections("conflict-"+(d==null?"none":d.id()),cards,dx,dy,dw,Math.max(20,bottom-dy-4));
        if(d!=null&&d.canAct()&&!d.founder()){
            if(d.ownSide().isBlank()){int bw=(w-4)/2;sideButton(d,d.first().getFirst(),"Join",x,bottom,bw);sideButton(d,d.second().getFirst(),"Join",x+bw+4,bottom,bw);}
            else{var side=d.ownSide().equals(d.first().getFirst().id())?d.second().getFirst():d.first().getFirst();int bw=(w-4)/2;sideButton(d,side,"Change side",x,bottom,bw);button("Withdraw",x+bw+4,bottom,bw,()->child(new Confirmation(this,"Withdraw?",List.of(new SectionCard(d.title(),"Your House leaves this conflict. The public record will retain the withdrawal.")),()->request(Action.WITHDRAW,d.id(),d.revision(),"","",0))));}
        }
        message(view.message());
    }
    private static String friendly(String value){return value.replace("ACTIVE","Active").replace("PAUSED","Ceasefire").replace("ENDED","Ended");}
    private static String names(List<ConflictPayloads.House> houses){return String.join("\n",houses.stream().map(h->h.title()+" / "+h.name()).toList());}
    private void sideButton(Detail d,ConflictPayloads.House side,String label,int x,int y,int w){button(label.equals("Join")?"Join "+side.title():label,x,y,w,()->child(new Confirmation(this,label+"?",List.of(new SectionCard(d.title(),"Your House will support "+side.title()+". This will be publicly recorded.")),()->request(label.equals("Join")?Action.JOIN:Action.CHANGE,d.id(),d.revision(),side.id(),"",0))));}
    private static final class Confirmation extends CouncilScreen {
        final ConflictScreen root;final List<SectionCard> cards;final Runnable confirm;
        Confirmation(ConflictScreen root,String title,List<SectionCard> cards,Runnable confirm){super(title,root);this.root=root;this.cards=List.copyOf(cards);this.confirm=confirm;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;sections("confirm",cards,x,top+36,w,panelHeight-105);button("Cancel",x,top+panelHeight-38,(w-4)/2,this::close);button("Confirm",x+(w+4)/2,top+panelHeight-38,(w-4)/2,()->{confirm.run();close();});}
        @Override public void close(){ConflictClient.child(null);client.setScreen(root);}
    }
}
