package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.TreatyPayloads;
import io.github.chronicle.lastcouncil.network.TreatyPayloads.*;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Small lectern screen; server views are already filtered for this recipient. */
public final class TreatyScreen extends CouncilScreen {
    private View view;
    private UUID latestState;
    private final Set<UUID> stateRequests=new LinkedHashSet<>();
    TreatyScreen(View view,Screen parent){super("Treaty lectern",parent);this.view=view;}
    public View view(){return view;}
    @Override public void close(){TreatyClient.closed(this);super.close();}
    void accept(View next){if(stateRequests.remove(next.request())&&!next.request().equals(latestState))return;if(next.revision()<view.revision())return;view=next;if(client.currentScreen instanceof Draft draft&&draft.root==this)draft.response(next);if(client.currentScreen==this)rebuildPreservingFocus();}
    private UUID request(Action action,UUID id,long revision,String title,String terms,boolean privateTerms,List<UUID> parties,int page){UUID request=UUID.randomUUID();if(action==Action.STATE){latestState=request;stateRequests.add(request);if(stateRequests.size()>128)stateRequests.remove(stateRequests.iterator().next());}TreatyClient.send(new Request(request,action,view.token(),id,revision,BlockPos.ORIGIN,title,terms,privateTerms,parties,page));return request;}
    private void state(UUID id,int page){request(Action.STATE,id,0,"","",false,List.of(),page);}
    private void openChild(Screen screen){TreatyClient.child(screen);client.setScreen(screen);}
    @Override protected void init(){
        frame();int x=left+12,w=panelWidth-24,y=top+34;Detail d=view.detail();
        button("+ Create treaty",x,y,w,()->openChild(new Draft(this,null))).active=view.active()&&view.houseActor()&&view.people().size()>=2;
        int bottom=top+panelHeight-35,contentY=y+24;boolean compact=w<350;int listW=compact?w:Math.max(110,w/3),listH=compact?(view.rows().size()>1?46:22):Math.max(44,bottom-contentY-48);
        List<Choice> choices=view.rows().stream().map(r->new Choice(r.id().toString(),r.title()+" · "+r.status())).toList();
        rows("treaties",choices,d==null?"":d.id().toString(),x,contentY,listW,listH,id->state(UUID.fromString(id),0));
        int dx=compact?x:x+listW+6,dy=compact?contentY+listH+4:contentY,dw=compact?w:w-listW-6;
        int controlsY=view.historyPages()>1?bottom-24:bottom;int detailH=Math.max(20,controlsY-dy-3);
        List<SectionCard> cards=new ArrayList<>();
        if(d==null)cards.add(new SectionCard("Treaties",view.rows().isEmpty()?"No agreements available here.":"Select an agreement."));
        else {
            cards.add(new SectionCard(d.title(),d.status()+" · "+(d.privateTerms()?"Private":"Public")+" · Revision "+d.revision()));
            cards.add(new SectionCard("Exact terms",d.terms()));
            StringBuilder parties=new StringBuilder();for(PartyView p:d.parties())parties.append(p.name()).append(" · ").append(p.house()).append(" — ").append(p.present()?"present":"absent").append(", ").append(p.signed()?"signed":"not signed").append('\n');
            parties.append("Chancellor — ").append(d.chancellorPresent()?"present":"absent");cards.add(new SectionCard("Signatures and attendance",parties.toString()));
            cards.add(new SectionCard("History ("+(view.historyPages()==0?0:view.historyPage()+1)+" / "+view.historyPages()+")",d.history().isEmpty()?"No history on this page.":String.join("\n",d.history())));
        }
        sections("treaty-detail-"+(d==null?"none":d.id()),cards,dx,dy,dw,detailH);
        if(d!=null){if(view.historyPages()>1){int bw=(dw-6)/2;button("History <",dx,controlsY,bw,()->state(d.id(),view.historyPage()-1)).active=view.historyPage()>0;button("History >",dx+bw+6,controlsY,bw,()->state(d.id(),view.historyPage()+1)).active=view.historyPage()+1<view.historyPages();}
            int ay=bottom;List<RunnableAction> acts=new ArrayList<>();if(d.canEdit())acts.add(new RunnableAction("Edit",()->openChild(new Draft(this,d))));if(d.canSign())acts.add(new RunnableAction("Sign",()->confirm(d,Action.SIGN)));if(d.canSanctify())acts.add(new RunnableAction("Sanctify",()->confirm(d,Action.SANCTIFY)));if(d.canBreak())acts.add(new RunnableAction("Break",()->confirm(d,Action.BREAK)));int each=Math.max(1,(w-Math.max(0,acts.size()-1)*4)/Math.max(1,acts.size()));for(int i=0;i<acts.size();i++){RunnableAction a=acts.get(i);button(a.label(),x+i*(each+4),ay,each,a.action());}}
        message(view.message());
    }
    private record RunnableAction(String label,Runnable action) { }
    private void confirm(Detail d,Action action){openChild(new Review(this,d,action));}
    private static abstract class Child extends CouncilScreen {
        final TreatyScreen root;
        Child(String title,TreatyScreen root){super(title,root);this.root=root;}
        @Override public void close(){TreatyClient.clearChild();client.setScreen(root);}
        boolean stale(UUID id,long revision){Detail d=root.view.detail();return d==null||!d.id().equals(id)||d.revision()!=revision;}
    }
    private static final class Review extends Child {
        private final Detail detail;private final Action action;
        Review(TreatyScreen root,Detail detail,Action action){super(action==Action.SIGN?"Sign terms":action==Action.SANCTIFY?"Sanctify agreement":"Break agreement",root);this.detail=detail;this.action=action;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+38;sections("review",List.of(new SectionCard(detail.title()+" · revision "+detail.revision(),detail.terms()),new SectionCard("Confirm",action==Action.SIGN?"Sign these exact terms for your own house. The Chancellor must still sanctify.":action==Action.SANCTIFY?"Sanctify the exact signed revision.":"Record this agreement as broken.")),x,y,w,panelHeight-110);button("Cancel",x,top+panelHeight-60,(w-4)/2,this::close);button("Confirm",x+(w+4)/2,top+panelHeight-60,(w-4)/2,()->{if(stale(detail.id(),detail.revision())){feedback="This revision changed. Close and review again.";return;}root.request(action,detail.id(),detail.revision(),"","",false,List.of(),0);close();});}
    }
    private static final class Draft extends Child {
        private final UUID id;private final long revision;private String title,terms;private boolean privacy;private final Set<UUID> selected=new LinkedHashSet<>();private final Map<UUID,String> partyLabels=new LinkedHashMap<>();
        private Attempt pending;
        private record Attempt(UUID request,UUID id,String title,String terms,boolean privacy,Set<UUID> parties) { }
        Draft(TreatyScreen root,Detail d){super(d==null?"Create treaty":"Edit treaty",root);id=d==null?TreatyPayloads.NONE:d.id();revision=d==null?0:d.revision();title=d==null?"":d.title();terms=d==null?"":d.terms();privacy=d!=null&&d.privateTerms();if(d!=null)d.parties().forEach(p->{selected.add(p.owner());partyLabels.put(p.owner(),p.name()+" / "+p.house());});else if(net.minecraft.client.MinecraftClient.getInstance().player!=null)selected.add(net.minecraft.client.MinecraftClient.getInstance().player.getUuid());}
        @Override public void render(net.minecraft.client.gui.DrawContext context,int mouseX,int mouseY,float delta){if(!id.equals(TreatyPayloads.NONE)&&stale(id,revision))feedback="Draft changed; reopen before submitting.";super.render(context,mouseX,mouseY,delta);}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+35;TextFieldWidget titleField=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Title")));titleField.setMaxLength(160);titleField.setText(title);titleField.setPlaceholder(Text.literal("Title"));titleField.setChangedListener(v->title=v);y+=24;EditBoxWidget termsField=addDrawableChild(EditBoxWidget.builder().x(x).y(y).placeholder(Text.literal("Exact terms")).build(textRenderer,w,42,Text.literal("Exact terms")));termsField.setMaxLength(4096);termsField.setText(terms);termsField.setChangeListener(v->terms=v);y+=46;button(privacy?"Private":"Public",x,y,w,()->{privacy=!privacy;rebuildPreservingFocus();});y+=24;
            button("Representatives ("+selected.size()+" selected)...",x,y,w,()->{PartyPicker picker=new PartyPicker(this);TreatyClient.child(picker);client.setScreen(picker);});
            button("Cancel",x,top+panelHeight-38,(w-4)/2,this::close);button("Submit",x+(w+4)/2,top+panelHeight-38,(w-4)/2,()->{if(pending!=null)return;if(title.isBlank()||terms.isBlank()){feedback="Enter title and exact terms.";return;}if(!id.equals(TreatyPayloads.NONE)&&stale(id,revision)){feedback="This draft changed. Close and review again.";return;}Set<UUID> present=new HashSet<>();root.view.people().forEach(p->present.add(p.owner()));if(selected.size()<2||selected.size()>16){feedback="Choose 2 to 16 representatives.";return;}if(!present.containsAll(selected)){feedback="Selected representatives must be present.";return;}UUID request=root.request(id.equals(TreatyPayloads.NONE)?Action.CREATE:Action.EDIT,id,revision,title,terms,privacy,List.copyOf(selected),0);pending=new Attempt(request,id.equals(TreatyPayloads.NONE)?request:id,title.strip(),terms.strip(),privacy,Set.copyOf(selected));feedback="Submitting...";for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget widget)widget.active=false;});
            if(!id.equals(TreatyPayloads.NONE)&&stale(id,revision))message("Draft changed; reopen before submitting.");else message("Select representatives; your house is included.");
        }
        private void response(View next){
            if(pending==null||!pending.request().equals(next.request()))return;
            Attempt attempt=pending;pending=null;Detail d=next.detail();
            if(d!=null&&d.id().equals(attempt.id())&&d.title().equals(attempt.title())&&d.terms().equals(attempt.terms())&&d.privateTerms()==attempt.privacy()&&d.parties().stream().map(PartyView::owner).collect(java.util.stream.Collectors.toSet()).equals(attempt.parties()))close();
            else {rebuildPreservingFocus();feedback=next.message().isBlank()?"Draft was not saved. Review and try again.":next.message();}
        }
    }
    private static final class PartyPicker extends CouncilScreen {
        private final Draft draft;
        PartyPicker(Draft draft){super("Representatives",draft);this.draft=draft;}
        @Override public void close(){TreatyClient.child(draft);client.setScreen(draft);}
        @Override protected void init(){
            frame();int x=left+12,w=panelWidth-24,y=top+36;
            Map<UUID,String> available=new LinkedHashMap<>();
            draft.root.view.people().forEach(p->{String label=p.name()+" / "+p.house();available.put(p.owner(),label);draft.partyLabels.put(p.owner(),label);});
            List<Choice> choices=new ArrayList<>();
            available.forEach((id,label)->choices.add(new Choice(id.toString(),(draft.selected.contains(id)?"[x] ":"[ ] ")+label)));
            for(UUID id:draft.selected)if(!available.containsKey(id))choices.add(new Choice(id.toString(),"[x] "+draft.partyLabels.getOrDefault(id,"Representative")+" / absent"));
            rows("party-picker",choices,"",x,y,w,Math.max(44,panelHeight-110),s->{
                UUID id=UUID.fromString(s),self=client.player==null?TreatyPayloads.NONE:client.player.getUuid();
                if(id.equals(self))return;
                if(draft.selected.contains(id))draft.selected.remove(id);
                else if(draft.root.view.people().stream().anyMatch(p->p.owner().equals(id))&&draft.selected.size()<16)draft.selected.add(id);
                else if(draft.selected.size()>=16)feedback="At most 16 representatives.";
                rebuildPreservingFocus();
            });
            button("Done",x,top+panelHeight-38,w,this::close);
            message(draft.selected.size()+" selected / your house is included.");
        }
    }

}
