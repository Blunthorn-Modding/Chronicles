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
    private UUID copyPending;private int copyTicks;
    PersonalScreen(ProfilePayloads.View card){super("Character & houses",null);this.card=card;}
    void acceptCard(ProfilePayloads.View value){card=value;clearAndInit();}
    void accept(PersonalView value){if(!value.request().equals(pending))return;view=value;pending=null;waitingTicks=0;feedback=value.message();clearAndInit();}
    void notice(Notice notice){if(notice.request().equals(pending)){pending=null;waitingTicks=0;feedback=notice.message();}}
    void copyStarted(UUID id){copyPending=id;copyTicks=0;feedback="Requesting a physical copy...";clearAndInit();}
    void copyNotice(Notice notice){if(notice.request().equals(copyPending)){copyPending=null;copyTicks=0;}feedback=notice.message();if(client.currentScreen==this)rebuildPreservingFocus();}
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
        int list=panelWidth<380?98:126;int bodyY=y;h-=24;
        rows("papers",Arrays.stream(Section.values()).map(s->new Choice(s.name(),sectionName(s))).toList(),section.name(),x,y,list,h,id->{section=Section.valueOf(id);offset=0;request();clearAndInit();});
        x+=list+6;w-=list+6;
        String text=(own==null?"":own.title()+"\n\n")+(view.section()==section?String.join("\n\n",view.paragraphs()):"Reading…");
        if(view.section()==section&&view.paragraphs().isEmpty())text+=view.message().isBlank()?"No information in this section.":view.message();
        if(view.section()==section&&section==Section.INFORMATION&&own!=null)sections("own-house",List.of(
            new SectionCard("House",own.title()),new SectionCard("Reputation",own.reputation()),
            new SectionCard("House information",own.description()),new SectionCard("Interests",own.interests())
        ),x,bodyY,w,h-(bodyY-y));
        else if(view.section()==section&&!view.paragraphs().isEmpty())sections("papers-"+section,
            view.paragraphs().stream().map(paragraph->new SectionCard(sectionName(section),paragraph)).toList(),x,bodyY,w,h-(bodyY-y));
        else body(text,x,bodyY,w,h-(bodyY-y));
        button("Copy paper...",x,top+panelHeight-68,w,this::copyPaper).active=copyPending==null&&pending==null&&view.section()==section;
        if(view.section()==section){
            if(view.previous())button("Previous",left+12,top+panelHeight-44,80,()->{offset=Math.max(0,view.offset()-8);request();});
            if(view.next())button("Next",left+98,top+panelHeight-44,65,()->{offset=view.offset()+8;request();});
        }
    }
    private void copyPaper(){
        if(view==null||view.section()!=section||copyPending!=null)return;
        Section requested=section;List<Choice> choices=new ArrayList<>();
        if(requested==Section.INFORMATION)choices.add(new Choice("0","House information"));
        else for(int i=0;i<view.paragraphs().size();i++){
            int index=view.offset()+i;String paragraph=view.paragraphs().get(i).replace('\n',' ');
            choices.add(new Choice(Integer.toString(index),sectionName(requested)+" "+(index+1)+" / "+textRenderer.trimToWidth(paragraph,200)));
        }
        if(choices.isEmpty()){feedback="There is no paper to copy in this section.";return;}
        choose("Choose a paper to copy",choices,id->PaperCopiesClient.request(this,requested,Integer.parseInt(id)));
    }
    private void otherHouses(int x,int y,int w,int h){
        var others=view.houses().stream().filter(v->!v.id().equals(view.ownHouse())).toList();
        if(others.isEmpty()){body("No other houses are configured.",x,y,w,h);return;}
        var selected=others.stream().filter(v->v.id().equals(otherHouse)).findFirst().orElse(others.getFirst());otherHouse=selected.id();
        int list=panelWidth<380?98:142;
        rows("houses",others.stream().map(v->new Choice(v.id(),v.title())).toList(),otherHouse,x,y,list,h,id->{otherHouse=id;clearAndInit();});
        sections("house-detail-"+selected.id(),List.of(
            new SectionCard("House",selected.title()),
            new SectionCard("Reputation",selected.reputation()),
            new SectionCard("House information",selected.description()),
            new SectionCard("Interests",selected.interests()),
            new SectionCard("Representative",(selected.representative().isBlank()?"Not assigned":selected.representative())+(selected.candidate()?"\nCrown candidate":""))
        ),x+list+6,y,w-list-6,h);
    }
    private static String sectionName(Section section){return switch(section){case INFORMATION->"House information";case SECRETS->"Secrets";case GOALS->"Goals";case KNOWN_SECRETS->"Other houses' secrets";};}
    @Override public void tick(){if(pending!=null&&++waitingTicks>200){pending=null;feedback="The server did not answer. Refresh to retry.";}if(copyPending!=null&&++copyTicks>200){copyPending=null;feedback="Copy response delayed. Check your inventory before requesting another copy.";rebuildPreservingFocus();}}
}
