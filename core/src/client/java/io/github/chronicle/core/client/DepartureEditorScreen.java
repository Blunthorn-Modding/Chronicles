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

/** Local typed departure draft. The server pins every selected revision and validates the JSON again. */
final class DepartureEditorScreen extends Screen {
    record Draft(String id,String json) { }
    private final Screen parent; private final DepartureForm base; private final long token; private final Consumer<Draft> save, bind;
    private TextFieldWidget name;private EditBoxWidget first,finalText; private Entry anchor, region, area; private String areaState; private String problem=""; private String nameDraft,firstDraft,finalDraft,anchorId,regionId,areaId; private long anchorRevision,regionRevision;
    DepartureEditorScreen(Screen parent, DepartureForm form,long token,Consumer<Draft> save,Consumer<Draft> bind){super(Text.literal(form.creating()?"New departure":"Edit departure"));this.parent=parent;this.base=form;this.token=token;this.save=save;this.bind=bind; nameDraft=form.name();firstDraft=form.firstText();finalDraft=form.finalText();anchorId=form.anchorId();anchorRevision=form.anchorRevision();regionId=form.regionId();regionRevision=form.regionRevision();areaId=form.areaId();anchor=find(form.blockSites(),anchorId,anchorRevision);region=find(form.regions(),regionId,regionRevision);area=find(form.areas(),areaId,-1);areaState=form.areaState().isBlank()?"start":form.areaState();}
    @Override protected void init(){int w=Math.min(500,width-24),x=(width-w)/2,y=Math.max(12,(height-294)/2);name=input(x,y+28,w,"Name",nameDraft,160,v->nameDraft=v);first=warning(x,y+62,w,"First warning",firstDraft,v->firstDraft=v);finalText=warning(x,y+122,w,"Final warning",finalDraft,v->finalDraft=v);
        button("Block site: "+label(anchor,anchorId,"Choose block site"),x,y+180,w/2-2,()->choose(base.blockSites(),false,value->{anchor=value;setAnchor(value);}));button("Region: "+label(region,regionId,"Choose boarding region"),x+w/2+2,y+180,w/2-2,()->choose(base.regions(),false,value->{region=value;setRegion(value);}));
        button("Area: "+label(area,areaId,"None"),x,y+201,w/2-2,()->choose(base.areas(),true,value->{area=value;areaId=id(value);}));button("State: "+areaState,x+w/2+2,y+201,w/2-2,()->{retain();areaState=areaState.equals("end")?"start":"end";clearAndInit();});
        button("Bind looked-at block",x,y+222,w/2-2,this::bindLook);button("Save",x+w/2+2,y+222,w/2-2,this::save);button("Cancel",x,y+243,w,this::close);}
    private EditBoxWidget warning(int x,int y,int w,String label,String value,Consumer<String> changed){EditBoxWidget field=addDrawableChild(EditBoxWidget.builder().build(textRenderer,w,44,Text.literal(label)));field.setPosition(x,y);field.setMaxLength(4096);field.setText(value);field.setChangeListener(changed);return field;}
    private TextFieldWidget input(int x,int y,int w,String hint,String value,int max,Consumer<String> changed){TextFieldWidget f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,18,Text.literal(hint)));f.setMaxLength(max);f.setText(value);f.setChangedListener(changed);return f;}
    private void button(String label,int x,int y,int w,Runnable action){addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,Math.max(1,w-8))),b->action.run()).dimensions(x,y,Math.max(1,w),18).build());}
    private void save(){retain();if(nameDraft.trim().isBlank()||anchorId.isBlank()||regionId.isBlank()){problem="Name, Block site, and Region are required.";return;}save.accept(new Draft(base.id(),json()));}
    private void bindLook(){retain();bind.accept(new Draft(base.id(),json()));}
    private void choose(List<Entry> rows,boolean optional,Consumer<Entry> apply){retain();client.setScreen(new DepartureChoiceScreen(this,optional?"Choose optional area":"Choose departure reference",rows,optional,value->{apply.accept(value);clearAndInit();}));}
    private String json(){JsonObject j=new JsonObject();j.addProperty("id",base.id());j.addProperty("name",nameDraft.trim());j.addProperty("anchorId",anchorId);j.addProperty("anchorRevision",anchorRevision);j.addProperty("regionId",regionId);j.addProperty("regionRevision",regionRevision);j.addProperty("areaId",areaId);j.addProperty("areaState",areaId.isBlank()?"":areaState);j.addProperty("firstText",firstDraft);j.addProperty("finalText",finalDraft);return j.toString();}
    private void retain(){if(name!=null){nameDraft=name.getText();firstDraft=first.getText();finalDraft=finalText.getText();}} private void setAnchor(Entry e){anchorId=id(e);anchorRevision=revision(e);} private void setRegion(Entry e){regionId=id(e);regionRevision=revision(e);} private static Entry find(List<Entry> rows,String id,long rev){return rows.stream().filter(e->e.id().equals(id)&&(rev<0||e.revision()==rev)).findFirst().orElse(null);} private static Entry next(List<Entry> rows,Entry value){if(rows.isEmpty())return null;int i=rows.indexOf(value);return rows.get((i+1+rows.size())%rows.size());} private static Entry nextWithNone(List<Entry> rows,Entry value){if(value==null)return rows.isEmpty()?null:rows.get(0);int i=rows.indexOf(value);return i+1>=rows.size()?null:rows.get(i+1);} private static String id(Entry e){return e==null?"":e.id();} private static long revision(Entry e){return e==null?0:e.revision();} private static String label(Entry e,String stale,String empty){return e==null?(stale.isBlank()?empty:"Unavailable: "+stale):e.label();}
    void serverRejected(String message){problem=message;clearAndInit();}
    @Override public void render(DrawContext c,int mx,int my,float d){int w=Math.min(500,width-24),x=(width-w)/2,y=Math.max(12,(height-294)/2);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),x-8,y-8,w+16,294);c.drawTextWithShadow(textRenderer,title,x,y,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Name"),x,y+16,0xFFCCCCCC);c.drawTextWithShadow(textRenderer,Text.literal("First warning"),x,y+50,0xFFCCCCCC);c.drawTextWithShadow(textRenderer,Text.literal("Final warning"),x,y+110,0xFFCCCCCC);if(!problem.isBlank())c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(problem,w)),x,y+266,0xFFFF9966);super.render(c,mx,my,d);}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
