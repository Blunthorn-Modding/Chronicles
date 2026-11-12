package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import io.github.chronicle.lastcouncil.network.BanquetPayloads;
import io.github.chronicle.lastcouncil.network.BanquetPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Operator supply controls and separately authorized Chancellor log. */
final class BanquetScreen extends CouncilScreen {
    private View view;
    private Request pending;
    private ButtonWidget retryButton;
    private UUID recipient=BanquetPayloads.NONE;
    private String drinks="0", poison="0", antidotes="0";
    private boolean enabledDraft; private String onsetDraft, durationDraft;
    BanquetScreen(View view,Screen parent){super(view.logView()?"Banquet log":"Banquet",parent);this.view=view;enabledDraft=view.config().enabled();onsetDraft=Integer.toString(view.config().onsetMinutes());durationDraft=Integer.toString(view.config().durationMinutes());if(!view.logView()){pending=BanquetClient.compatiblePendingGift(view.worldId());feedback=BanquetClient.giftBlocked(view.worldId())?"Unresolved gift in another world, or world identity unavailable.":BanquetClient.takeGiftNotice();}}
    void accept(View next){
        if(pending==null || !pending.request().equals(next.request()) || (pending.action()==Action.GIFT && !view.worldId().equals(next.worldId())))return;
        boolean refreshSettings=view.revision()!=next.revision()||!view.config().equals(next.config());
        boolean concurrentSettings=pending.action()==Action.SAVE&&refreshSettings&&!next.message().equals("Settings saved.");
        if(next.logView() && !view.logView() && !next.held()){pending=null;client.setScreen(new BanquetScreen(next,this));return;}
        boolean changed=view.revision()!=next.revision() || !view.config().equals(next.config()) || view.editable()!=next.editable() || view.canGift()!=next.canGift() || view.canLog()!=next.canLog() || !view.players().equals(next.players()) || !view.log().equals(next.log()) || view.logView()!=next.logView() || view.held()!=next.held();
        view=next;
        if(pending.action()!=Action.GIFT || !next.held())pending=null;
        if(refreshSettings){enabledDraft=next.config().enabled();onsetDraft=Integer.toString(next.config().onsetMinutes());durationDraft=Integer.toString(next.config().durationMinutes());}
        message(concurrentSettings?"Settings changed; latest values loaded.":next.message());if(changed)clearAndInit();else updateRetry();
    }
    private void send(Action action,BanquetConfig config,UUID target,int d,int p,int a){
        if(pending!=null || !ClientPlayNetworking.canSend(Request.ID)){message("A request is pending or the connection is unavailable.");return;}
        pending=new Request(UUID.randomUUID(),action,view.revision(),config,target,d,p,a);
        if(action==Action.GIFT)BanquetClient.trackGift(pending,view.worldId());
        ClientPlayNetworking.send(pending);message("Waiting for server...");updateRetry();
    }
    private boolean available(){if(pending!=null){message("A request is pending. Retry it or wait for the server.");return false;}return true;}
    private void updateRetry(){if(retryButton!=null){retryButton.visible=pending!=null;retryButton.active=pending!=null;}}
    private void retry(){if(pending!=null&&ClientPlayNetworking.canSend(Request.ID)){ClientPlayNetworking.send(pending);message("Checking the same request...");}}
    @Override protected void init(){
        if(view.logView())frame();else frame(480,180);int x=left+12,w=panelWidth-24,y=top+36;
        if(view.logView()){
            String text=view.log().isEmpty()?"No log entries.":String.join("\n\n",view.log().stream().map(LogEntry::text).toList());
            body("log",text,x,y,w,Math.max(20,panelHeight-90));
        }else{
            var c=view.config();body("Banquet: "+(c.enabled()?"Enabled":"Disabled")+"\nOnset: "+c.onsetMinutes()+" minutes  |  Illness: "+c.durationMinutes()+" minutes",x,y,w,35);
            int bw=(w-(view.canLog()?8:4))/(view.canLog()?3:2);
            button("Settings...",x,y+42,bw,()->{if(available())client.setScreen(new Settings(this));}).active=view.editable();
            button("Give supplies...",x+bw+4,y+42,bw,()->{if(available())client.setScreen(new Gift(this));}).active=view.canGift()&&!BanquetClient.giftBlocked(view.worldId());
            if(view.canLog())button("Private log",x+2*(bw+4),y+42,bw,()->send(Action.LOG,null,BanquetPayloads.NONE,0,0,0));
        }
        retryButton=button("Retry request",x,top+panelHeight-38,110,this::retry);updateRetry();
        if(!view.message().isBlank()&&feedback.isBlank())message(view.message());
    }
    private static final class Settings extends CouncilScreen {
        private final BanquetScreen owner;private boolean enabled;private String onset,duration;
        Settings(BanquetScreen owner){super("Banquet settings",owner);this.owner=owner;enabled=owner.enabledDraft;onset=owner.onsetDraft;duration=owner.durationDraft;}
        @Override protected void init(){frame(480,180);int x=left+12,w=panelWidth-24,y=top+36;
            button("Banquet: "+(enabled?"Enabled":"Disabled"),x,y,w,()->{enabled=!enabled;clearAndInit();});
            int col=(w-4)/2;body("Onset (1-60 min)",x,y+28,col,12);body("Illness (1-60 min)",x+col+4,y+28,col,12);
            field(x,y+41,col,onset,v->onset=v);field(x+col+4,y+41,col,duration,v->duration=v);
            button("Save",x,top+panelHeight-38,90,()->{try{int a=Integer.parseInt(onset),b=Integer.parseInt(duration);if(a<1||a>60||b<1||b>60)throw new IllegalArgumentException();owner.enabledDraft=enabled;owner.onsetDraft=onset;owner.durationDraft=duration;client.setScreen(owner);owner.send(Action.SAVE,new BanquetConfig(enabled,a,b),BanquetPayloads.NONE,0,0,0);}catch(IllegalArgumentException e){message("Enter minutes from 1 to 60.");}});
            button("Cancel",x+94,top+panelHeight-38,90,this::close);
        }
        private void field(int x,int y,int w,String value,java.util.function.Consumer<String> changed){var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Minutes")));f.setMaxLength(2);f.setText(value);f.setChangedListener(changed);}
    }
    private static final class Gift extends CouncilScreen {
        private final BanquetScreen owner;
        Gift(BanquetScreen owner){super("Give supplies",owner);this.owner=owner;}
        @Override protected void init(){frame(480,180);int x=left+12,w=panelWidth-24,y=top+36;
            String name=owner.view.players().stream().filter(p->p.id().equals(owner.recipient)).map(PlayerChoice::name).findFirst().orElse("Choose online participant...");
            button(textRenderer.trimToWidth(name,w-12),x,y,w,()->choose("Choose participant",owner.view.players().stream().map(p->new Choice(p.id().toString(),p.name())).toList(),id->owner.recipient=UUID.fromString(id)));
            int col=(w-8)/3;body("Drinks",x,y+28,col,12);body("Poison",x+col+4,y+28,col,12);body("Antidotes",x+2*(col+4),y+28,col,12);
            field(x,y+41,col,owner.drinks,v->owner.drinks=v);field(x+col+4,y+41,col,owner.poison,v->owner.poison=v);field(x+2*(col+4),y+41,col,owner.antidotes,v->owner.antidotes=v);
            if(panelHeight>=180)body("Each quantity: 0-16. Total: 1-36.",x,y+69,w,20);
            button("Review...",x,top+panelHeight-38,90,this::review);button("Cancel",x+94,top+panelHeight-38,90,this::close);
        }
        private void field(int x,int y,int w,String value,java.util.function.Consumer<String> changed){var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Quantity")));f.setMaxLength(2);f.setText(value);f.setChangedListener(changed);}
        private void review(){try{int d=Integer.parseInt(owner.drinks),p=Integer.parseInt(owner.poison),a=Integer.parseInt(owner.antidotes);if(d<0||d>16||p<0||p>16||a<0||a>16||d+p+a<1||d+p+a>36||owner.view.players().stream().noneMatch(v->v.id().equals(owner.recipient)))throw new IllegalArgumentException();String name=owner.view.players().stream().filter(v->v.id().equals(owner.recipient)).findFirst().orElseThrow().name();client.setScreen(new Confirm(this,owner,name,d,p,a));}catch(IllegalArgumentException e){message("Choose a participant and valid quantities.");}}
    }
    private static final class Confirm extends CouncilScreen {
        private final BanquetScreen owner;private final String name;private final int d,p,a;private boolean sent;
        Confirm(Screen parent,BanquetScreen owner,String name,int d,int p,int a){super("Confirm supplies",parent);this.owner=owner;this.name=name;this.d=d;this.p=p;this.a=a;}
        @Override protected void init(){frame(480,180);int x=left+12,w=panelWidth-24;body("Give "+name+":\n"+d+" drinks, "+p+" poison, "+a+" antidotes?",x,top+40,w,80);button("Confirm",x,top+panelHeight-38,90,()->{if(sent)return;sent=true;client.setScreen(owner);owner.send(Action.GIFT,null,owner.recipient,d,p,a);});button("Cancel",x+94,top+panelHeight-38,90,this::close);}
    }
}
