package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.DossierScreen;
import io.github.chronicle.core.network.ProfilePayloads;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Public directory plus one authenticated recipient's selected papers page. */
public final class PersonalScreen extends CouncilScreen {
    private ProfilePayloads.View card;
    private PersonalView view;
    private DossierScreen cardEditor;
    private int tab,offset,waitingTicks;
    private Section section=Section.INFORMATION;
    private String otherHouse="";
    private UUID pending;
    PersonalScreen(ProfilePayloads.View card){super("Character & houses",null);this.card=card;}
    void acceptCard(ProfilePayloads.View value){card=value;clearAndInit();}
    void accept(PersonalView value){if(!value.request().equals(pending))return;view=value;pending=null;waitingTicks=0;feedback=value.message();clearAndInit();}
    void notice(Notice notice){if(notice.request().equals(pending)){pending=null;waitingTicks=0;feedback=notice.message();}}
    private void request(){if(client==null||client.player==null||!ClientPlayNetworking.canSend(PersonalRequest.ID))return;pending=UUID.randomUUID();waitingTicks=0;ClientPlayNetworking.send(new PersonalRequest(pending,section,offset));}
    @Override protected void init(){
        if(cardEditor!=null){card=cardEditor.snapshot();cardEditor=null;}
        frame();int inner=panelWidth-24,tabWidth=(inner-8)/3;
        String[] labels={"Character","My house","Other houses"};for(int i=0;i<3;i++){final int index=i;button(labels[i],left+12+i*(tabWidth+4),top+35,tabWidth,()->{tab=index;clearAndInit();if(index==1)request();}).active=index!=tab;}
        int x=left+12,y=top+65,w=inner,h=panelHeight-112;
        if(tab==0){body(card.name()+"\n"+card.pronouns()+"\n\n"+card.description(),x,y,w,h-28);button("Edit character card…",x,top+panelHeight-44,w,()->{cardEditor=new DossierScreen(card,this);client.setScreen(cardEditor);});}
        else if(view==null)body(pending==null?"Refresh to read your house information.":"Reading house information…",x,y,w,h);
        else if(tab==1)ownHouse(x,y,w,h);
        else otherHouses(x,y,w,h);
        if(tab!=0)button("Refresh",left+panelWidth-92,top+panelHeight-44,80,this::request);
        if(view==null&&pending==null)request();
    }
    private void ownHouse(int x,int y,int w,int h){
        if(view.ownHouse().isBlank()){body(view.message(),x,y,w,h);return;}
        var own=view.houses().stream().filter(v->v.id().equals(view.ownHouse())).findFirst().orElse(null);
        boolean compact=panelWidth<380||panelHeight<280;int bodyY=y;
        if(compact){button(sectionName(section),x,y,w,()->choose("House papers",Arrays.stream(Section.values()).map(s->new Choice(s.name(),sectionName(s))).toList(),id->{section=Section.valueOf(id);offset=0;request();}));bodyY+=28;}
        else {int i=0;for(var option:Section.values()){final var selected=option;button(sectionName(option),x,y+i++*24,126,()->{section=selected;offset=0;request();}).active=option!=section;}x+=136;w-=136;}
        String text=(own==null?"":own.title()+"\n\n")+(view.section()==section?String.join("\n\n",view.paragraphs()):"Reading…");
        if(view.section()==section&&view.paragraphs().isEmpty())text+=view.message().isBlank()?"No information in this section.":view.message();
        body(text,x,bodyY,w,h-(bodyY-y));
        if(view.section()==section){
            if(view.previous())button("Previous",left+12,top+panelHeight-44,80,()->{offset=Math.max(0,view.offset()-8);request();});
            if(view.next())button("Next",left+98,top+panelHeight-44,65,()->{offset=view.offset()+8;request();});
        }
    }
    private void otherHouses(int x,int y,int w,int h){
        var others=view.houses().stream().filter(v->!v.id().equals(view.ownHouse())).toList();
        if(others.isEmpty()){body("No other houses are configured.",x,y,w,h);return;}
        var selected=others.stream().filter(v->v.id().equals(otherHouse)).findFirst().orElse(others.getFirst());otherHouse=selected.id();
        button(selected.title()+"…",x,y,w,()->choose("Other houses",others.stream().map(v->new Choice(v.id(),v.title())).toList(),id->{otherHouse=id;}));
        body(selected.reputation()+"\n\n"+selected.description()+"\n\n"+selected.interests()+"\n\nRepresentative: "+(selected.representative().isBlank()?"Not assigned":selected.representative())+(selected.candidate()?"\nCrown candidate":""),x,y+28,w,h-28);
    }
    private static String sectionName(Section section){return switch(section){case INFORMATION->"House information";case SECRETS->"Secrets";case GOALS->"Goals";case KNOWN_SECRETS->"Other houses' secrets";};}
    @Override public void tick(){if(pending!=null&&++waitingTicks>200){pending=null;feedback="The server did not answer. Refresh to retry.";}}
}
