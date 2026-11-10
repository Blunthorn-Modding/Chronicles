package io.github.chronicle.lastcouncil.client;

import com.google.gson.JsonParser;
import io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.*;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Operator-only reusable source catalogue. */
final class CouncilCatalogueScreen extends CouncilScreen {
    private View view; private UUID pending; private Action pendingAction; private String query;
    private TextFieldWidget search; private boolean preview,resuming; private ByteArrayOutputStream document; private int chunkIndex,chunkCount;
    CouncilCatalogueScreen(View view,Screen parent){super("Source catalogue",parent);this.view=view;query=view.query();}
    View current(){return view;}
    void saved(View next){view=next;query=next.query();pending=null;pendingAction=null;if(client.currentScreen==this)clearAndInit();}
    void accept(View next){
        if(pending==null||!pending.equals(next.request()))return;
        pending=null;pendingAction=null;document=null;resuming=false;
        boolean focused=search!=null&&search.isFocused();int cursor=focused?search.getCursor():0;
        view=next;query=next.query();clearAndInit();
        if(focused){setFocused(search);search.setCursor(Math.min(cursor,query.length()),false);}
    }
    void accept(Chunk chunk){
        if(pending==null||!pending.equals(chunk.request())||(pendingAction!=Action.LOAD&&pendingAction!=Action.CREATE))return;
        if(chunk.index()==0){document=new ByteArrayOutputStream();chunkIndex=0;chunkCount=chunk.count();}
        if(document==null||chunk.index()!=chunkIndex||chunk.count()!=chunkCount||chunkCount<1||chunkCount>44||document.size()+chunk.data().length>2097152){pending=null;document=null;resuming=false;message("Invalid editor document received.");return;}
        document.writeBytes(chunk.data());chunkIndex++;
        if(chunkIndex==chunkCount){
            try { var object=JsonParser.parseString(document.toString(StandardCharsets.UTF_8)).getAsJsonObject();
                boolean creating=pendingAction==Action.CREATE;pending=null;document=null;
                if(resuming&&CouncilCatalogueClient.hasRetained()){resuming=false;var editor=CouncilCatalogueClient.retained().resume(this,false);CouncilCatalogueClient.clearRetained();client.setScreen(editor);}
                else if(preview){preview=false;client.setScreen(new CouncilContentEditor.Preview(this,object.has("text")?object.get("text").getAsString():""));}
                else client.setScreen(new CouncilContentEditor(this,view,object,chunk.digest(),creating));
            } catch(RuntimeException ex){pending=null;document=null;resuming=false;message("Invalid editor document received.");}
        }
    }
    void send(Action action,Category category,String q,int page,int selected,int part){
        resuming=false;
        if(action!=Action.LOAD)preview=false;
        if(!ClientPlayNetworking.canSend(Request.ID)){message("Connection unavailable.");return;}
        pending=UUID.randomUUID();pendingAction=action;document=null;
        ClientPlayNetworking.send(new Request(pending,action,category,q,page,selected,part,view.digest()));
    }
    void refresh(){send(Action.VIEW,view.category(),query,view.page(),view.selected(),view.part());}
    private void resumeDraft(){
        var editor=CouncilCatalogueClient.retained();if(editor==null)return;
        if(!view.editable()||!editor.sourceDigest().equals(view.digest())){var copy=editor.resume(this,true);CouncilCatalogueClient.clearRetained();client.setScreen(copy);return;}
        var original=editor.sourceView();send(editor.isCreating()?Action.CREATE:Action.LOAD,original.category(),original.query(),original.page(),original.selected(),original.part());resuming=true;
    }
    @Override protected void init(){
        frame();int x=left+12,w=panelWidth-24,y=top+34;
        String[] names={"Houses","Secrets","Goals","Knowledge"};Category[] categories=Category.values();int each=(w-6)/4;
        for(int i=0;i<4;i++){final Category c=categories[i];button(names[i],x+i*(each+2),y,each,()->send(Action.VIEW,c,"",0,0,0)).active=c!=view.category();}
        int bottom=top+panelHeight-38, contentY=y+24, contentH=bottom-contentY-3;
        boolean compact=panelWidth<380;int listW=compact?w:Math.max(100,w/3),listH=compact?Math.min(48,contentH/3):contentH;
        if(view.category()!=Category.KNOWLEDGE){
            if(CouncilCatalogueClient.hasRetained())button("Resume draft",x,contentY,compact?(listW-4)/2:listW,this::resumeDraft);
            else button("+ Create",x,contentY,compact?(listW-4)/2:listW,()->send(Action.CREATE,view.category(),query,view.page(),view.selected(),view.part())).active=view.editable();
            search=addDrawableChild(new TextFieldWidget(textRenderer,compact?x+(listW+4)/2:x,compact?contentY:contentY+22,compact?(listW-4)/2:listW,20,Text.literal("Search")));search.setMaxLength(128);search.setText(query);search.setPlaceholder(Text.literal("Search"));
            search.setChangedListener(v->{query=v;send(Action.VIEW,view.category(),v,0,0,0);});
        } else {search=null;if(CouncilCatalogueClient.hasRetained())button("Resume draft",x,contentY,listW,this::resumeDraft);}
        int rowY=contentY+(view.category()==Category.KNOWLEDGE?(CouncilCatalogueClient.hasRetained()?22:0):compact?22:44),rowH=Math.max(20,listH-(rowY-contentY));
        List<Choice> choices=new ArrayList<>();for(Row row:view.rows())choices.add(new Choice(Integer.toString(row.index()),row.label()));
        if(compact){
            int selected=view.selected();
            String label=view.rows().stream().filter(r->r.index()==selected).map(Row::label).findFirst().orElse("No definitions");
            int last=view.rows().stream().mapToInt(Row::index).max().orElse(-1);
            button("<",x,rowY,20,()->{int previous=selected-1;send(Action.VIEW,view.category(),query,previous/6,previous,0);}).active=selected>0;
            var selectedRow=button(textRenderer.trimToWidth(label,listW-52),x+22,rowY,listW-44,()->{});selectedRow.active=false;
            button(">",x+listW-20,rowY,20,()->{int next=selected+1;send(Action.VIEW,view.category(),query,next/6,next,0);}).active=selected<last||view.page()+1<view.pages();
        }else rows("catalogue",choices,Integer.toString(view.selected()),x,rowY,listW,rowH,id->send(Action.VIEW,view.category(),query,view.page(),Integer.parseInt(id),0));
        int dx=compact?x:x+listW+6,dy=compact?contentY+listH+4:contentY,dw=compact?w:w-listW-6,dh=compact?contentH-listH-4:contentH;
        int actionsY=dy+Math.max(0,dh-22);List<SectionCard> cards=new ArrayList<>();for(Card c:view.cards())cards.add(new SectionCard(c.title(),c.body()));
        sections("catalogue-detail",cards,dx,dy,dw,Math.max(10,dh-24));
        int third=(dw-4)/3;
        button(view.category()==Category.KNOWLEDGE?"Edit rules":"Edit",dx,actionsY,third,()->send(Action.LOAD,view.category(),query,view.page(),view.selected(),view.part())).active=view.editable();
        button("Delete",dx+third+2,actionsY,third,()->client.setScreen(new CouncilContentEditor.DeleteConfirm(this,view))).active=view.editable()&&view.category()!=Category.KNOWLEDGE&&!view.rows().isEmpty();
        button("Preview",dx+2*(third+2),actionsY,third,()->{preview=true;send(Action.LOAD,view.category(),query,view.page(),view.selected(),view.part());}).active=(view.category()==Category.SECRETS||view.category()==Category.GOALS)&&!view.rows().isEmpty();
        int bw=compact?(w-2)/2:(w-6)/4;
        if(!compact){
            button("Page <",x,bottom,bw,()->send(Action.VIEW,view.category(),query,view.page()-1,(view.page()-1)*6,0)).active=view.page()>0;
            button("Page >",x+bw+2,bottom,bw,()->send(Action.VIEW,view.category(),query,view.page()+1,(view.page()+1)*6,0)).active=view.page()+1<view.pages();
        }
        int textX=compact?x:x+2*(bw+2);
        button("Text <",textX,bottom,bw,()->send(Action.VIEW,view.category(),query,view.page(),view.selected(),view.part()-1)).active=view.part()>0;
        button("Text >",textX+bw+2,bottom,bw,()->send(Action.VIEW,view.category(),query,view.page(),view.selected(),view.part()+1)).active=view.part()+1<view.parts();
        message(!view.message().isBlank()?view.message():!view.reason().isBlank()?view.reason():"Page "+(view.page()+1)+" / "+view.pages());
    }
}
