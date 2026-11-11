package io.github.chronicle.core.client;

import com.google.gson.JsonObject;
import io.github.chronicle.core.menu.CoreMenuModel.DepartureForm;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.Consumer;

/** Local departure draft. References remain pinned and are validated again by the server. */
final class DepartureEditorScreen extends Screen {
    record Draft(String id,String json) { }
    private final Screen parent; private final DepartureForm base; private final Consumer<Draft> save, bind;
    private TextFieldWidget name; private EditBoxWidget first, finalText;
    private Entry anchor, region; private String areaState, problem="", nameDraft, firstDraft, finalDraft, anchorId, regionId, areaId;
    private long anchorRevision, regionRevision;
    DepartureEditorScreen(Screen parent,DepartureForm form,long token,Consumer<Draft> save,Consumer<Draft> bind){
        super(Text.literal("Configure departure")); this.parent=parent; base=form; this.save=save; this.bind=bind;
        nameDraft=form.name(); firstDraft=form.firstText(); finalDraft=form.finalText(); anchorId=form.anchorId(); anchorRevision=form.anchorRevision();
        regionId=form.regionId(); regionRevision=form.regionRevision(); areaId=form.areaId(); areaState=form.areaState();
        anchor=find(form.blockSites(),anchorId,anchorRevision); region=find(form.regions(),regionId,regionRevision);
    }
    private Entry regionArea(){return base.areas().stream().filter(e->e.reference().equals(regionId)).findFirst().orElse(null);}
    private boolean available(Entry e,String state){return (state.equals("start") || state.equals("end")) && e!=null && (e.kind().equals("START_END") || e.kind().equals(state.equals("start")?"START":"END"));}
    private boolean unresolved(){if(areaId.isBlank())return false; Entry e=regionArea(); return e==null || !e.id().equals(areaId) || !available(e,areaState);}
    @Override protected void init(){
        int w=Math.min(500,width-24),x=(width-w)/2,y=Math.max(8,(height-330)/2);
        name=input(x,y+28,w,"Name",nameDraft,160,v->nameDraft=v); first=warning(x,y+62,w,"First warning",firstDraft,v->firstDraft=v); finalText=warning(x,y+122,w,"Final warning",finalDraft,v->finalDraft=v);
        name.setEditable(!base.used()); first.active=!base.used(); finalText.active=!base.used();
        if(anchorId.isBlank()||anchor==null&&base.blockSites().isEmpty()) button("Bind looked-at block",x,y+180,w/2-2,this::bindLook,!base.used());
        else if(anchor==null && !base.blockSites().isEmpty()) button("Use current block site",x,y+180,w/2-2,()->{setAnchor(base.blockSites().get(0));clearAndInit();},!base.used());
        button("Region: "+label(region,regionId,"Choose boarding Region"),x+w/2+2,y+180,w/2-2,()->choose(base.regions(),value->{region=value;regionId=id(value);regionRevision=revision(value);clearAndInit();}),!base.used());
        Entry current=regionArea();
        if(current!=null || !areaId.isBlank()) {
            button("Leave unchanged",x,y+224,w/3-2,()->setState(null,""),!base.used());
            button("Start",x+w/3,y+224,w/3-2,()->setState(current,"start"),!base.used()&&available(current,"start"));
            button("End",x+2*w/3,y+224,w/3-2,()->setState(current,"end"),!base.used()&&available(current,"end"));
        }
        button("Save",x,y+268,w/2-2,this::save,!base.used()&&!unresolved()&&!anchorId.isBlank()&&anchor!=null&&region!=null);
        button("Cancel",x+w/2+2,y+268,w/2-2,this::close,true);
    }
    private void setState(Entry e,String state){retain();areaId=e==null?"":e.id();areaState=state;problem="";clearAndInit();}
    private EditBoxWidget warning(int x,int y,int w,String label,String value,Consumer<String> changed){EditBoxWidget f=addDrawableChild(EditBoxWidget.builder().build(textRenderer,w,44,Text.literal(label)));f.setPosition(x,y);f.setMaxLength(4096);f.setText(value);f.setChangeListener(changed);return f;}
    private TextFieldWidget input(int x,int y,int w,String hint,String value,int max,Consumer<String> changed){TextFieldWidget f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,18,Text.literal(hint)));f.setMaxLength(max);f.setText(value);f.setChangedListener(changed);return f;}
    private void button(String label,int x,int y,int w,Runnable action,boolean enabled){ButtonWidget b=addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,Math.max(1,w-8))),ignored->action.run()).dimensions(x,y,Math.max(1,w),18).build());b.active=enabled;}
    private void save(){retain();if(nameDraft.trim().isBlank()||anchorId.isBlank()||regionId.isBlank()||unresolved()){problem="Name, a bound block, and a valid Region and state are required.";return;}save.accept(new Draft(base.id(),json()));}
    private void bindLook(){retain();bind.accept(new Draft(base.id(),json()));}
    private void choose(List<Entry> rows,Consumer<Entry> apply){retain();client.setScreen(new DepartureChoiceScreen(this,"Choose boarding Region",rows,false,apply));}
    private String json(){JsonObject j=new JsonObject();j.addProperty("id",base.id());j.addProperty("name",nameDraft.trim());j.addProperty("anchorId",anchorId);j.addProperty("anchorRevision",anchorRevision);j.addProperty("regionId",regionId);j.addProperty("regionRevision",regionRevision);j.addProperty("areaId",areaId);j.addProperty("areaState",areaId.isBlank()?"":areaState);j.addProperty("firstText",firstDraft);j.addProperty("finalText",finalDraft);return j.toString();}
    private void retain(){if(name!=null){nameDraft=name.getText();firstDraft=first.getText();finalDraft=finalText.getText();}}
    private void setAnchor(Entry e){anchor=e;anchorId=id(e);anchorRevision=revision(e);}
    private static Entry find(List<Entry> rows,String id,long rev){return rows.stream().filter(e->e.id().equals(id)&&e.revision()==rev).findFirst().orElse(null);}
    private static String id(Entry e){return e==null?"":e.id();} private static long revision(Entry e){return e==null?0:e.revision();}
    private static String label(Entry e,String stale,String empty){return e==null?(stale.isBlank()?empty:"Unavailable: "+stale):e.label();}
    void serverRejected(String message){problem=message;clearAndInit();}
    @Override public void render(DrawContext c,int mx,int my,float d){
        int w=Math.min(500,width-24),x=(width-w)/2,y=Math.max(8,(height-330)/2);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),x-8,y-8,w+16,330);
        c.drawTextWithShadow(textRenderer,title,x,y,0xFFFFFFFF); c.drawTextWithShadow(textRenderer,Text.literal("Name"),x,y+16,0xFFCCCCCC); c.drawTextWithShadow(textRenderer,Text.literal("First warning"),x,y+50,0xFFCCCCCC); c.drawTextWithShadow(textRenderer,Text.literal("Final warning"),x,y+110,0xFFCCCCCC);
        c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth("Bound block: "+label(anchor,anchorId,"unbound"),w)),x,y+169,0xFFCCCCCC);
        if(regionArea()!=null || !areaId.isBlank()){c.drawTextWithShadow(textRenderer,Text.literal("After departure, the Region should be:"),x,y+205,0xFFCCCCCC);String selected=unresolved()?"Unresolved: "+areaId+" / "+areaState:areaId.isBlank()?"Selected: Leave unchanged":"Selected: "+areaState;c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(selected,w)),x,y+245,unresolved()?0xFFFF9966:0xFFCCCCCC);}
        String message=base.used()?"This departure has been used and cannot be edited.":problem; if(!message.isBlank())c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(message,w)),x,y+292,0xFFFF9966); super.render(c,mx,my,d);
    }
    @Override public boolean shouldPause(){return false;} @Override public void close(){client.setScreen(parent);}
}
