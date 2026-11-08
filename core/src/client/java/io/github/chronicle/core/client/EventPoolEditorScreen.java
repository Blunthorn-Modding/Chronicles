package io.github.chronicle.core.client;

import io.github.chronicle.core.eventpool.*;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import io.github.chronicle.core.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local drafts of reusable pool definitions; server remains authoritative. */
public final class EventPoolEditorScreen extends Screen {
    private final CoreMenuScreen parent;
    private EventPoolEditorPayloads.Response state;
    private EventPoolEditorView view;
    private EventPoolDefinition draft;
    private String selectedEntry = "", status = "";
    private int poolOffset, entryOffset;
    private boolean pending, conflict;
    private int pendingTicks;
    EventPoolEditorScreen(CoreMenuScreen parent, EventPoolEditorPayloads.Response state, EventPoolEditorView view) {
        super(Text.literal("Event pools")); this.parent=parent; this.state=state; this.view=view;
    }
    void accept(EventPoolEditorPayloads.Response response) {
        if (!response.token().equals(state.token())) return;
        if (pending && response.revision() != state.revision() && !response.message().equals("Pool saved.") && !response.message().equals("Pool deleted.")) conflict=true;
        state=response; view=EventPoolEditorClient.parse(response.data()); status=conflict ? "Pool catalogue changed. Close and reopen before editing." : response.message(); pending=false;pendingTicks=0;
        if (status.equals("Pool saved.")) draft=view.pools().stream().filter(p->draft!=null && p.id().equals(draft.id())).findFirst().orElse(null);
        if (status.equals("Pool deleted.")) { draft=null; selectedEntry=""; }
        clearAndInit();
    }
    @Override protected void init() {
        clearChildren(); int left=10, top=28, gap=6, listWidth=Math.max(105, (width-26)*2/5), right=left+listWidth+gap, detailWidth=width-right-10;
        int rows=Math.max(1,(height-174)/20);
        button("Create pool",left,top,listWidth,()->client.setScreen(new NameScreen(this)),state.editable()&&!pending&&!conflict);
        poolOffset=Math.clamp(poolOffset,0,Math.max(0,view.pools().size()-rows));
        for(int i=0;i<rows && poolOffset+i<view.pools().size();i++) { var pool=view.pools().get(poolOffset+i); int y=top+22+i*20;
            button(textRenderer.trimToWidth((draft!=null&&draft.id().equals(pool.id())?"> ":"")+pool.label(),listWidth-8),left,y,listWidth,()->{draft=pool;selectedEntry="";entryOffset=0;status="";clearAndInit();},true);
        }
        if(view.pools().size()>rows) { button("^",left,height-76,30,()->{poolOffset=Math.max(0,poolOffset-1);clearAndInit();},poolOffset>0); button("v",left+32,height-76,30,()->{poolOffset++;clearAndInit();},poolOffset+rows<view.pools().size()); }
        if(draft!=null) {
            button("Add existing Event",right,top,detailWidth, this::picker,state.editable()&&!pending&&!conflict&&draft.entries().size()<64);
            entryOffset=Math.clamp(entryOffset,0,Math.max(0,draft.entries().size()-rows));
            for(int i=0;i<rows && entryOffset+i<draft.entries().size();i++) { var entry=draft.entries().get(entryOffset+i); int y=top+22+i*20; String label=label(entry.eventId());
                button(textRenderer.trimToWidth((selectedEntry.equals(entry.eventId())?"> ":"")+label+" ("+entry.weight()+")",detailWidth-8),right,y,detailWidth,()->{selectedEntry=entry.eventId();clearAndInit();},true);
            }
            if(draft.entries().size()>rows) {button("^",right,height-76,30,()->{entryOffset=Math.max(0,entryOffset-1);clearAndInit();},entryOffset>0);button("v",right+32,height-76,30,()->{entryOffset++;clearAndInit();},entryOffset+rows<draft.entries().size());}
            if(!selectedEntry.isEmpty()) {
                var entry=draft.entries().stream().filter(e->e.eventId().equals(selectedEntry)).findFirst().orElse(null);
                if(entry!=null){var weightField=addDrawableChild(new TextFieldWidget(textRenderer,right+66,height-76,45,18,Text.literal("Weight"))); weightField.setMaxLength(5);weightField.setText(Integer.toString(entry.weight()));weightField.setEditable(state.editable()&&!pending&&!conflict);weightField.setChangedListener(value->{try{int weight=Integer.parseInt(value);if(weight<1||weight>10000)throw new NumberFormatException();var entries=new ArrayList<>(draft.entries());int index=java.util.stream.IntStream.range(0,entries.size()).filter(i->entries.get(i).eventId().equals(selectedEntry)).findFirst().orElseThrow();entries.set(index,new EventPoolDefinition.Entry(selectedEntry,weight));draft=new EventPoolDefinition(draft.id(),draft.label(),entries);status="";}catch(RuntimeException invalid){status="Weight must be 1 to 10000.";}});button("Remove",right+114,height-76,Math.max(30,detailWidth-114),()->remove(entry),state.editable()&&!pending&&!conflict);}
            }
            addDrawableChild(new ScrollableTextWidget(right,height-132,detailWidth,36,Text.literal(draft.label()+(selectedEntry.isEmpty()?"":"\n"+label(selectedEntry)+"\n"+diagnostic(selectedEntry))),textRenderer).textColor(0xffcccccc));
            button("Save pool",right,height-24,detailWidth/2-2, this::save,state.editable()&&!pending&&!conflict);
            button("Delete",right+detailWidth/2,height-24,detailWidth-detailWidth/2,()->client.setScreen(new DeleteScreen(this)),state.editable()&&!pending&&!conflict&&view.pools().stream().anyMatch(p->p.id().equals(draft.id())));
        }
        button("Back",left,height-24,listWidth, this::close,true);
    }
    private void button(String label,int x,int y,int w,Runnable action,boolean enabled) { var b=addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored->action.run()).dimensions(x,y,Math.max(1,w),18).build()); b.active=enabled; }
    private void weight(EventPoolDefinition.Entry target,int amount) { var entries=new ArrayList<>(draft.entries()); entries.set(entries.indexOf(target),new EventPoolDefinition.Entry(target.eventId(),target.weight()+amount)); draft=new EventPoolDefinition(draft.id(),draft.label(),entries); clearAndInit(); }
    private void remove(EventPoolDefinition.Entry target) { var entries=new ArrayList<>(draft.entries()); entries.removeIf(e->e.eventId().equals(target.eventId())); draft=new EventPoolDefinition(draft.id(),draft.label(),entries); selectedEntry=""; clearAndInit(); }
    private void save() { if(pending||conflict)return;pendingTicks=0; if (status.equals("Weight must be 1 to 10000.")) return; try { String data=new String(EventPoolCatalogStore.encode(Map.of(draft.id(),draft)),StandardCharsets.UTF_8); pending=true; ClientPlayNetworking.send(new EventPoolEditorPayloads.Request(1,state.token(),state.revision(),data)); clearAndInit(); } catch(Exception e) { status="Cannot save this pool."; clearAndInit(); } }
    private String label(String id) { return view.events().stream().filter(e->e.id().equals(id)).map(EventPoolEditorView.Option::label).findFirst().orElse(id); }
    private String diagnostic(String id) { return view.events().stream().filter(e->e.id().equals(id)).map(EventPoolEditorView.Option::problem).findFirst().orElse("Event is missing."); }
    private void picker() {
        CatalogAddPopup popup=new CatalogAddPopup(this,Text.literal("Add existing Event"),false,new CatalogAddPopup.Owner(){
            public void requestPage(CatalogAddPopup popup,String query,int page){var filtered=view.events().stream().filter(e->(e.label()+" "+e.id()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList(); int pages=Math.max(1,(filtered.size()+11)/12);int current=Math.clamp(page,0,pages-1);var items=filtered.stream().skip(current*12L).limit(12).map(e->new Entry(e.id(),e.label(),e.problem(),"EVENT",e.id(),0)).toList();popup.replacePage(new Picker("EVENT",query,current,pages,items));}
            public String validate(Entry entry,String quantity){if(!diagnostic(entry.id()).isEmpty())return diagnostic(entry.id());if(draft.entries().stream().anyMatch(e->e.eventId().equals(entry.id())))return "Already in this pool.";return "";}
            public void add(Entry entry,String quantity){var entries=new ArrayList<>(draft.entries());entries.add(new EventPoolDefinition.Entry(entry.id(),1));draft=new EventPoolDefinition(draft.id(),draft.label(),entries);selectedEntry=entry.id();client.setScreen(EventPoolEditorScreen.this);}
            public void cancel(){client.setScreen(EventPoolEditorScreen.this);}
        }); client.setScreen(popup);
    }
    void create(String name) { try { String id="chronicle_core:pool_"+UUID.randomUUID().toString().replace("-",""); draft=new EventPoolDefinition(id,name,List.of());selectedEntry="";status="New pool: save to create.";client.setScreen(this); } catch(IllegalArgumentException invalid){status="Enter a valid pool name.";client.setScreen(this);} }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),2,2,width-4,height-4);
        context.drawTextWithShadow(textRenderer,title,10,8,0xffffffff);
        if(draft!=null&&!selectedEntry.isBlank())context.drawTextWithShadow(textRenderer,Text.literal("Weight"),Math.max(105,(width-26)*2/5)+82,height-86,0xffcccccc);
        if(conflict)status="Pool catalogue changed or timed out. Close and reopen before editing.";
        String info=draft==null?"Choose a pool.":draft.label()+(selectedEntry.isEmpty()?"":" — "+label(selectedEntry)+" "+diagnostic(selectedEntry));
        context.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(info,width-20)),10,height-43,0xffcccccc);
        if(!status.isEmpty())context.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(status,width-20)),10,height-53,0xffffcc66);
        super.render(context,mouseX,mouseY,delta);
    }
    @Override public void tick(){if(pending&&++pendingTicks>200){pending=false;conflict=true;status="Server did not answer. Close and reopen the pool editor.";clearAndInit();}}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
    private void delete() { if(pending||conflict)return;pendingTicks=0;pending=true;ClientPlayNetworking.send(new EventPoolEditorPayloads.Request(2,state.token(),state.revision(),draft.id()));client.setScreen(this); }
    private static final class DeleteScreen extends Screen {
        private final EventPoolEditorScreen parent;
        DeleteScreen(EventPoolEditorScreen parent){super(Text.literal("Delete pool?"));this.parent=parent;}
        protected void init(){int w=Math.min(300,width-24),x=(width-w)/2,y=height/2;addDrawableChild(ButtonWidget.builder(Text.literal("Delete"),b->parent.delete()).dimensions(x,y,w/2-2,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),b->close()).dimensions(x+w/2+2,y,w/2-2,20).build());}
        public void render(DrawContext context,int x,int y,float delta){int w=Math.min(300,width-24);context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),(width-w)/2-6,height/2-58,w+12,116);context.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-20,0xffffffff);super.render(context,x,y,delta);}
        public void close(){client.setScreen(parent);} public boolean shouldPause(){return false;}
    }
    private static final class NameScreen extends Screen {
        private final EventPoolEditorScreen parent; private TextFieldWidget field;
        NameScreen(EventPoolEditorScreen parent){super(Text.literal("Create pool"));this.parent=parent;}
        protected void init(){int w=Math.min(300,width-24),x=(width-w)/2,y=height/2-25;field=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Pool name")));field.setMaxLength(80);setInitialFocus(field);addDrawableChild(ButtonWidget.builder(Text.literal("Create"),b->parent.create(field.getText())).dimensions(x,y+26,w/2-2,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),b->close()).dimensions(x+w/2+2,y+26,w/2-2,20).build());}
        public void render(DrawContext context,int x,int y,float delta){int w=Math.min(300,width-24);context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.ofVanilla("popup/background"),(width-w)/2-6,height/2-58,w+12,116);context.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-40,0xffffffff);super.render(context,x,y,delta);}
        public void close(){client.setScreen(parent);} public boolean shouldPause(){return false;}
    }
}
