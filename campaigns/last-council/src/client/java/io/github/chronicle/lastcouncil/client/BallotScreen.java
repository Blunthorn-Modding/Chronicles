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
        if(client.currentScreen==this)clearAndInit();
    }
    void notice(CouncilPayloads.Notice value) {
        if(value.request().equals(pending)) {
            pending=null;
            waiting=0;
            feedback=value.message();
            if(client.currentScreen==this)clearAndInit();
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
    private String names() {
        StringBuilder out=new StringBuilder();
        view.ballot().namedVotes().forEach((house,
                choice)->out.append("\n").append(view.ballot().electorateLabels().getOrDefault(house,
                house)).append(": ").append(optionLabel(choice)));
        return out.toString();
    }
    private String totals() {
        StringBuilder out=new StringBuilder();
        view.ballot().totals().forEach((option,
                count)->out.append("\n").append(optionLabel(option)).append(": ").append(count));
        return out.toString();
    }
    @Override protected void init() {
        frame();
        int x=left+12,w=panelWidth-24,y=top+36;
        var b=view.ballot();
        int rosterHeight=panelHeight<280?44:84, columns=Math.max(1,(w-42)/72), visible=Math.max(1,rosterHeight/40);
        int rosterRows=(view.voters().size()+columns-1)/columns, maxRoster=Math.max(0,rosterRows-visible);
        rosterOffset=Math.clamp(rosterOffset,0,maxRoster);
        if(maxRoster>0){
            button("\u2191",x+w-40,y,18,()->{rosterOffset=Math.max(0,rosterOffset-visible);clearAndInit();}).active=rosterOffset>0;
            button("\u2193",x+w-20,y,18,()->{rosterOffset=Math.min(maxRoster,rosterOffset+visible);clearAndInit();}).active=rosterOffset<maxRoster;
        }
        y+=panelHeight<280?48:88;
        String info=b.title()+"\n"+view.description()+"\n"+(b.visibility().name().equals("PUBLIC")?"Public vote":"Secret vote")+" | "+b.threshold()+" of "+b.electorateLabels().size()+" required";
        if(b.ownChoice()!=null&&!b.ownChoice().isBlank())info+="\nYour choice: "+optionLabel(b.ownChoice());
        boolean captured=client.player!=null&&view.voters().stream().anyMatch(v->v.player().equals(client.player.getUuid()));
        boolean uncast=b.ownChoice()==null||b.ownChoice().isBlank();
        if(captured&&uncast&&!view.reason().isBlank())info+="\n"+view.reason();
        if(b.reason()!=null&&!b.reason().isBlank())info+="\nReason: "+b.reason();
        if(b.status().name().equals("CLOSED")&&!b.totals().isEmpty())info+="\nTotals:"+totals();
        int available=top+panelHeight-32-y;
        boolean showChoices=captured&&uncast&&b.status().name().equals("OPEN");
        int slots=showChoices?Math.max(1,Math.min(3,(available-58)/24)):0;
        boolean paging=showChoices&&b.options().size()>slots;
        int h=Math.max(24,available-slots*24-(paging?24:0)-26);
        body(info,x,y,w,h);
        y+=h+2;
        offset=Math.clamp(offset,0,Math.max(0,b.options().size()-slots));
        for(int i=offset; i<Math.min(b.options().size(),offset+slots); i++) {
            var option=b.options().get(i);
            button(option.label(),x,y,w,()->client.setScreen(new ConfirmScreen(ok-> {
                client.setScreen(this); if(ok)send(Action.CAST,option.id());
            },Text.literal("Confirm your choice?"),Text.literal("Your first accepted choice is final: "
                    +option.label())))).active=view.canCast();
            y+=24;
        }
        if(paging) {
            int half=(w-4)/2;
            button("Previous",x,y,half,()-> {
                offset=Math.max(0,offset-slots); clearAndInit();
            }).active=offset>0;
            button("Next",x+half+4,y,half,()-> {
                offset+=slots; clearAndInit();
            }).active=offset+slots<b.options().size();
            y+=24;
        }
        button("Refresh",x,y,w,()->send(Action.BALLOT,""));
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        int x=left+12,y=top+36,w=panelWidth-24,cell=72,columns=Math.max(1,(w-42)/cell),height=panelHeight<280?44:84;
        var voters=view.voters();int visible=Math.max(1,height/40),rows=(voters.size()+columns-1)/columns;
        rosterOffset=Math.clamp(rosterOffset,0,Math.max(0,rows-visible));
        context.enableScissor(x,y,x+w-42,y+height);
        String hovered=null;
        for(int i=rosterOffset*columns;i<voters.size()&&i<(rosterOffset+visible)*columns;i++) {
            var voter=voters.get(i);int px=x+(i%columns)*cell,py=y+(i/columns-rosterOffset)*40;
            String choice=view.ballot().visibility().name().equals("PUBLIC")?view.ballot().namedVotes().get(voter.house()):null;
            int color=0xFF888888;
            if(choice!=null) color=switch(choice.toUpperCase(Locale.ROOT)){case "YES"->0xFF45B95A;case "NO"->0xFFDF5555;case "ABSTAIN"->0xFFD5A443;default->0xFF888888;};
            int headX=px+(cell-26)/2;
            context.fill(headX,py,headX+26,py+26,color);
            var entry=client.player==null?null:client.player.networkHandler.getPlayerListEntry(voter.player());
            PlayerSkinDrawer.draw(context,entry==null?DefaultSkinHelper.getSkinTextures(voter.player()):entry.getSkinTextures(),headX+1,py+1,24);
            String name=textRenderer.trimToWidth(voter.name(),cell-4);
            context.drawTextWithShadow(textRenderer,name,px+(cell-textRenderer.getWidth(name))/2,py+28,0xFFFFFFFF);
            if(mouseX>=px&&mouseX<px+cell&&mouseY>=py&&mouseY<py+38&&mouseY<y+height){
                String state=view.ballot().visibility().name().equals("SECRET")?"Secret ballot":choice==null?"Not cast":optionLabel(choice);
                hovered=voter.name()+" | "+state;
            }
        }
        context.disableScissor();
        if(rows>visible)context.fill(x+w-43,y,x+w-41,y+height,0xFF888888);
        if(hovered!=null)context.drawTooltip(textRenderer,Text.literal(hovered),mouseX,mouseY);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        int y=top+36,h=panelHeight<280?44:84;
        if(mx>=left+12&&mx<left+panelWidth-12&&my>=y&&my<y+h){rosterOffset=Math.max(0,rosterOffset-(int)Math.signum(vertical));clearAndInit();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void tick() {
        if(pending!=null&&++waiting>200) {
            pending=null;
            feedback="The server did not answer. Refresh before retrying.";
        }
    }
    @Override public void close() {
        if(parent instanceof CouncilBusinessScreen business)business.ballotReturned();
        if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(new Request(UUID.randomUUID(),
                Action.EXIT,0,CouncilPayloads.NONE,"vote","","",false,false));
        super.close();
    }
}
