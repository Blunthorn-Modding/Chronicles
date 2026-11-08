package io.github.chronicle.lastcouncil.client;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;
public final class BallotScreen extends CouncilScreen {
    private Vote view;
    private UUID pending;
    private int waiting,offset,rosterOffset;
    BallotScreen(Vote view,Screen parent) {
        super("Ballot",parent);
        this.view=view;
    }
    Vote snapshot() { return view; }
    void accept(Vote value) {
        if(!value.ballot().id().equals(view.ballot().id()))return;
        if(!value.request().equals(CouncilPayloads.NONE)&&!value.request().equals(pending))return;
        if(value.request().equals(pending)) {
            pending=null;
            waiting=0;
        }
        view=value;
        if(client.currentScreen==this)rebuildPreservingFocus();
    }
    void notice(CouncilPayloads.Notice value) {
        if(value.request().equals(pending)) {
            pending=null;
            waiting=0;
            feedback=value.message();
            if(client.currentScreen==this)rebuildPreservingFocus();
        }
    }
    private void send(Action action,String option) {
        if(pending!=null||!ClientPlayNetworking.canSend(Request.ID))return;
        pending=UUID.randomUUID();
        waiting=0;
        ClientPlayNetworking.send(new Request(pending,action,0,view.ballot().id(),"vote",option,"",false,false));
    }
    private String optionLabel(String id) {
        return view.ballot().options().stream().filter(o->o.id().equals(id)).map(o->o.label()).findFirst().orElse(id);
    }
    private int rosterHeight() { return panelHeight<280?54:96; }
    private int rosterWidth() {
        int full=panelWidth-24;
        return rosterTotal(full)>rosterHeight()?full-40:full;
    }
    @Override protected void init() {
        frame();
        int x=left+12,w=panelWidth-24,y=top+36;
        var b=view.ballot();
        int rosterH=rosterHeight(),rosterWidth=rosterWidth();
        int rosterMax=Math.max(0,rosterTotal(rosterWidth)-rosterH);
        rosterOffset=Math.clamp(rosterOffset,0,rosterMax);
        if(rosterMax>0) {
            button("^",x+w-18,y,18,()->{rosterOffset=Math.max(0,rosterOffset-30);rebuildPreservingFocus();}).active=rosterOffset>0;
            button("v",x+w-18,y+22,18,()->{rosterOffset=Math.min(rosterMax,rosterOffset+30);rebuildPreservingFocus();}).active=rosterOffset<rosterMax;
        }
        y+=rosterH+4;
        boolean captured=client.player!=null&&view.voters().stream().anyMatch(v->v.player().equals(client.player.getUuid()));
        boolean uncast=b.ownChoice()==null||b.ownChoice().isBlank();
        boolean showChoices=captured&&uncast&&b.status().name().equals("OPEN");
        int bottom=top+panelHeight-25;
        int slots=showChoices?(panelHeight<280?1:Math.min(3,b.options().size())):0;
        boolean paging=showChoices&&b.options().size()>slots;
        int controls=slots*22+(paging?22:0);
        int infoH=Math.max(20,bottom-y-controls);
        List<SectionCard> cards=new ArrayList<>();
        cards.add(new SectionCard("Question",b.title()));
        cards.add(new SectionCard("Proposal",view.description()));
        cards.add(new SectionCard("Vote type",CouncilBallotSections.label(b.visibility().name())));
        if(captured&&uncast&&!view.reason().isBlank())cards.add(new SectionCard("Eligibility",view.reason()));
        if(b.reason()!=null&&!b.reason().isBlank())cards.add(new SectionCard("Reason",b.reason()));
        if(b.status().name().equals("CLOSED")) {
            StringBuilder result=new StringBuilder(b.passedOptions().isEmpty()?"No option passed":"Passed: "+String.join(", ",b.passedOptions().stream().map(this::optionLabel).toList()));
            b.totals().forEach((id,count)->result.append("\n").append(optionLabel(id)).append(": ").append(count));
            cards.add(new SectionCard("Result",result.toString()));
        }
        sections("ballot-info",cards,x,y,w,infoH);
        y+=infoH;
        offset=Math.clamp(offset,0,Math.max(0,b.options().size()-slots));
        for(int i=offset;i<Math.min(b.options().size(),offset+slots);i++) {
            var option=b.options().get(i);
            button(textRenderer.trimToWidth(option.label(),w-8),x,y,w,()->client.setScreen(new ConfirmScreen(ok->{
                client.setScreen(this);if(ok)send(Action.CAST,option.id());
            },Text.literal("Confirm your choice?"),Text.literal("Your first accepted choice is final: "+option.label())))).active=view.canCast();
            y+=22;
        }
        if(paging) {
            int half=(w-4)/2;
            button("Previous",x,y,half,()->{offset=Math.max(0,offset-slots);clearAndInit();}).active=offset>0;
            button("Next",x+half+4,y,half,()->{offset=Math.min(b.options().size()-slots,offset+slots);clearAndInit();}).active=offset+slots<b.options().size();
        }
    }
    private int rosterTotal(int w) {
        int columns=Math.max(1,w/108), cell=(w-(columns-1)*8)/columns,total=0;
        for(int first=0;first<view.voters().size();first+=columns) {
            int lines=1;
            for(int i=first;i<Math.min(first+columns,view.voters().size());i++)lines=Math.max(lines,textRenderer.wrapLines(Text.literal(view.voters().get(i).name()),cell-4).size());
            total+=30+lines*10+4;
        }
        return total;
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        int w=rosterWidth(),x=left+12+(panelWidth-24-w)/2,y=top+36,h=rosterHeight();
        int columns=Math.max(1,w/108),cell=(w-(columns-1)*8)/columns;
        rosterOffset=Math.clamp(rosterOffset,0,Math.max(0,rosterTotal(w)-h));
        context.enableScissor(x,y,x+w,y+h);
        int rowY=y-rosterOffset;
        for(int first=0;first<view.voters().size();first+=columns) {
            int count=Math.min(columns,view.voters().size()-first),rowX=x+(w-(count*cell+(count-1)*8))/2,lines=1;
            for(int j=0;j<count;j++)lines=Math.max(lines,textRenderer.wrapLines(Text.literal(view.voters().get(first+j).name()),cell-4).size());
            for(int j=0;j<count;j++) {
                var voter=view.voters().get(first+j);int px=rowX+j*(cell+8),headX=px+(cell-26)/2;
                String choice=view.ballot().visibility().name().equals("PUBLIC")?view.ballot().namedVotes().get(voter.house()):null;
                int color=choice==null?0xFF888888:switch(choice.toUpperCase(Locale.ROOT)){case "YES"->0xFF45B95A;case "NO"->0xFFDF5555;case "ABSTAIN"->0xFFD5A443;default->0xFF888888;};
                context.fill(headX,rowY,headX+26,rowY+26,color);
                var entry=client.player==null?null:client.player.networkHandler.getPlayerListEntry(voter.player());
                PlayerSkinDrawer.draw(context,entry==null?DefaultSkinHelper.getSkinTextures(voter.player()):entry.getSkinTextures(),headX+1,rowY+1,24);
                var nameLines=textRenderer.wrapLines(Text.literal(voter.name()),cell-4);
                for(int k=0;k<nameLines.size();k++)context.drawTextWithShadow(textRenderer,nameLines.get(k),px+(cell-textRenderer.getWidth(nameLines.get(k)))/2,rowY+30+k*10,0xFFFFFFFF);
            }
            rowY+=30+lines*10+4;
        }
        context.disableScissor();
        if(rosterTotal(w)>h)context.fill(x+w-2,y,x+w,y+h,0xFF888888);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        int y=top+36,h=rosterHeight();
        if(mx>=left+12&&mx<left+panelWidth-12&&my>=y&&my<y+h){rosterOffset=Math.clamp(rosterOffset-(int)Math.signum(vertical)*30,0,Math.max(0,rosterTotal(rosterWidth())-h));rebuildPreservingFocus();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void tick() {
        if(pending!=null&&++waiting>200) {
            pending=null;
            feedback="The server did not answer. Reopen the ballot before retrying.";
        }
    }
    @Override public void close() {
        if(parent instanceof CouncilBusinessScreen business)business.ballotReturned();
        if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(new Request(UUID.randomUUID(),
                Action.EXIT,0,CouncilPayloads.NONE,"vote","","",false,false));
        super.close();
    }
}
