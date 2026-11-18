package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.ForeignPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

/** Public diplomatic records or the operator's reusable realm catalogue. */
public final class ForeignScreen extends CouncilScreen {
    private View view; private UUID pending; private int age;
    ForeignScreen(View view){super(view.editing()?"Foreign realms":"Foreign affairs",null);this.view=view;}
    View view(){return view;}
    void accept(View next){
        if(next.revision()<view.revision())return;
        boolean matched=pending!=null&&pending.equals(next.request());
        if(matched){pending=null;age=0;}
        view=next;
        if(!next.message().isBlank())feedback=next.message();
        if(client.currentScreen==this)rebuildPreservingFocus();
        else if(matched&&client.currentScreen instanceof Editor editor)editor.response(next);
    }
    private void request(Action action,String realm,String name,String description){request(action,realm,name,description,0);}
    private void request(Action action,String realm,String name,String description,int historyPage){
        if(pending!=null||!ClientPlayNetworking.canSend(Request.ID))return;
        pending=UUID.randomUUID();age=0;
        ForeignClient.send(new Request(pending,action,view.token(),view.catalogRevision(),realm,name,description,historyPage));
    }
    private void child(Screen screen){ForeignClient.child(screen);client.setScreen(screen);}
    @Override public void close(){ForeignClient.closed(this);super.close();}
    @Override public void tick(){if(pending!=null&&++age>200){pending=null;feedback="The server did not answer. Close and reopen before retrying.";}}
    @Override protected void init(){
        frame();int x=left+12,w=panelWidth-24,y=top+36,bottom=top+panelHeight-38;
        if(view.editing()){
            button("Create realm...",x,y,w,()->child(new Editor(this,null))).active=view.editable()&&pending==null;y+=24;
        }else{
            int half=(w-4)/2;
            button("House conflicts",x,y,half,()->{ForeignClient.closed(this);if(client.player!=null)client.player.networkHandler.sendChatCommand("chronicle council conflicts");});
            button("Foreign affairs",x+half+4,y,half,()->{}).active=false;y+=24;
        }
        var d=view.detail();boolean compact=w<350;int lw=compact?w:Math.max(110,w/3),lh=compact?(view.rows().size()>1?46:22):bottom-y-4;
        rows("foreign-realms",view.rows().stream().map(r->new Choice(r.id(),r.name()+" / "+(r.atWar()?"At war":"Not at war"))).toList(),d==null?"":d.id(),x,y,lw,lh,id->request(Action.STATE,id,"",""));
        int dx=compact?x:x+lw+6,dy=compact?y+lh+4:y,dw=compact?w:w-lw-6;
        int actions=d!=null&&(view.editing()||d.historyPages()>1)?22:0;
        List<SectionCard> cards=new ArrayList<>();
        if(d==null)cards.add(new SectionCard("Foreign realms",view.rows().isEmpty()?"No foreign realms have been defined.":"Select a realm."));
        else {cards.add(new SectionCard(d.name(),d.atWar()?"At war":"Not at war"));cards.add(new SectionCard("Description",d.description()));if(!view.editing())cards.add(new SectionCard("Public history",d.history().isEmpty()?"No diplomatic decisions recorded.":String.join("\n",d.history())));}
        sections("foreign-"+(d==null?"none":d.id()),cards,dx,dy,dw,Math.max(20,bottom-dy-actions-4));
        if(actions>0&&view.editing()){int half=(dw-4)/2;button("Edit...",dx,bottom,half,()->child(new Editor(this,d))).active=view.editable()&&pending==null;
            button("Delete...",dx+half+4,bottom,half,()->child(new ConfirmScreen(ok->{ForeignClient.child(null);client.setScreen(this);if(ok)request(Action.DELETE,d.id(),"","");},Text.literal("Delete realm?"),Text.literal("Referenced realms cannot be deleted.")))).active=view.editable()&&pending==null;}
        if(!view.editing()&&d!=null&&d.historyPages()>1){int half=(dw-4)/2;button("Earlier history",dx,bottom,half,()->request(Action.STATE,d.id(),"","",d.historyPage()+1)).active=d.historyPage()+1<d.historyPages();button("Later history",dx+half+4,bottom,half,()->request(Action.STATE,d.id(),"","",d.historyPage()-1)).active=d.historyPage()>0;}
        if(!view.message().isBlank())message(view.message());
    }
    private static final class Editor extends CouncilScreen {
        private final ForeignScreen root;private final Detail original;private String name,description;private boolean submitted;
        Editor(ForeignScreen root,Detail detail){super(detail==null?"Create realm":"Edit realm",root);this.root=root;original=detail;name=detail==null?"":detail.name();description=detail==null?"":detail.description();}
        void response(View value){if(!submitted)return;submitted=false;feedback=value.message();if(value.saved()){close();}else if(client.currentScreen==this)rebuildPreservingFocus();}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+48;
            TextFieldWidget field=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Name")));field.setMaxLength(320);field.setText(name);field.setChangedListener(v->name=v);
            int h=Math.max(35,panelHeight-160);EditBoxWidget box=addDrawableChild(EditBoxWidget.builder().x(x).y(y+38).build(textRenderer,w,h,Text.literal("Description")));box.setMaxLength(8192);box.setText(description);box.setChangeListener(v->description=v);
            int half=(w-4)/2,by=top+panelHeight-38;button("Cancel",x,by,half,this::close);button("Save",x+half+4,by,half,()->{if(name.isBlank()||name.length()>320||description.length()>8192){feedback="Enter a name within the allowed limits.";return;}if(root.pending!=null)return;submitted=true;root.request(original==null?Action.CREATE:Action.UPDATE,original==null?"":original.id(),name,description);}).active=root.view.editable()&&root.pending==null;
        }
        @Override public void tick(){root.tick();if(submitted&&root.pending==null){submitted=false;feedback=root.feedback;}}
        @Override public void render(net.minecraft.client.gui.DrawContext context,int mouseX,int mouseY,float delta){super.render(context,mouseX,mouseY,delta);context.drawTextWithShadow(textRenderer,Text.literal("Name"),left+12,top+36,0xFFFFFFFF);context.drawTextWithShadow(textRenderer,Text.literal("Description"),left+12,top+74,0xFFFFFFFF);}
        @Override public void close(){ForeignClient.child(null);client.setScreen(root);}
    }
}
