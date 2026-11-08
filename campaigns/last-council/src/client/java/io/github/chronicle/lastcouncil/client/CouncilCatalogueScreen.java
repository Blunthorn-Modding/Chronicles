package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.CataloguePayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Read-only, paged source definitions; never renders a live assignment. */
final class CouncilCatalogueScreen extends CouncilScreen {
    private View view; private UUID pending; private String query=""; private TextFieldWidget search;
    CouncilCatalogueScreen(View view,Screen parent){super("Source catalogue",parent);this.view=view;query=view.query();}
    void accept(View next){if(pending==null||!pending.equals(next.request()))return;boolean focused=search!=null&&search.isFocused();int cursor=focused?search.getCursor():0;pending=null;view=next;clearAndInit();if(focused){setFocused(search);search.setCursor(Math.min(cursor,query.length()),false);}}
    private void request(Category category,String q,int page,int selected,int part){
        if(!ClientPlayNetworking.canSend(Request.ID))return;
        pending=UUID.randomUUID();ClientPlayNetworking.send(new Request(pending,category,q,page,selected,part));
    }
    @Override protected void init(){
        frame();int x=left+12,w=panelWidth-24,y=top+35;
        String[] names={"Houses","Secrets","Goals","Knowledge"};Category[] categories=Category.values();int each=(w-6)/4;
        for(int i=0;i<4;i++){final Category category=categories[i];button(names[i],x+i*(each+2),y,each,()->request(category,query,0,0,0)).active=category!=view.category();}
        search=addDrawableChild(new TextFieldWidget(textRenderer,x,y+24,w,20,Text.literal("Search source catalogue")));search.setMaxLength(128);search.setText(query);search.setChangedListener(value->{query=value;request(view.category(),value,0,0,0);});
        int controlsY=top+panelHeight-33,contentY=y+48,contentHeight=controlsY-contentY-3;boolean compact=panelWidth<380;
        int listW=compact?w:Math.max(100,w/3),listH=compact?Math.min(46,Math.max(24,contentHeight/3)):contentHeight;
        List<Choice> choices=new ArrayList<>();for(int i=0;i<view.labels().size();i++)choices.add(new Choice(Integer.toString(i),view.labels().get(i)));
        rows("catalogue",choices,Integer.toString(view.selected()),x,contentY,listW,listH,id->request(view.category(),query,view.page(),Integer.parseInt(id),0));
        int detailX=compact?x:x+listW+6,detailY=compact?contentY+listH+4:contentY,detailW=compact?w:w-listW-6,detailH=compact?contentHeight-listH-4:contentHeight;
        sections("catalogue-detail-"+view.category()+"-"+view.page()+"-"+view.selected()+"-"+view.part(),List.of(new SectionCard("Source details",view.detail())),detailX,detailY,detailW,Math.max(10,detailH));
        int bw=(w-6)/4;
        button("Page <",x,controlsY,bw,()->request(view.category(),query,view.page()-1,0,0)).active=view.page()>0;
        button("Page >",x+bw+2,controlsY,bw,()->request(view.category(),query,view.page()+1,0,0)).active=view.page()+1<view.pages();
        button("Text <",x+2*(bw+2),controlsY,bw,()->request(view.category(),query,view.page(),view.selected(),view.part()-1)).active=view.part()>0;
        button("Text >",x+3*(bw+2),controlsY,bw,()->request(view.category(),query,view.page(),view.selected(),view.part()+1)).active=view.part()+1<view.parts();
        message("Page "+(view.page()+1)+" / "+view.pages()+"; text "+(view.part()+1)+" / "+view.parts());
    }
}
