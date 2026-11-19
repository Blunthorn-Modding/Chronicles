package io.github.chronicle.lastcouncil.client;
import java.util.List;
final class RebellionConfirm extends CouncilScreen {
    private final List<SectionCard> cards;private final Runnable action,cancel;
    RebellionConfirm(CouncilScreen parent,String title,List<SectionCard> cards,Runnable action){this(parent,title,cards,action,RebellionClient::returnToOwner);}
    RebellionConfirm(CouncilScreen parent,String title,List<SectionCard> cards,Runnable action,Runnable cancel){super(title,parent);this.cards=cards;this.action=action;this.cancel=cancel;}
    @Override protected void init(){frame();int x=left+12,w=panelWidth-24;sections("confirm",cards,x,top+36,w,panelHeight-105);button("Cancel",x,top+panelHeight-38,(w-4)/2,this::close);button("Confirm",x+(w+4)/2,top+panelHeight-38,(w-4)/2,()->{RebellionClient.returnToOwner();action.run();});}
    @Override public void close(){cancel.run();}
}
