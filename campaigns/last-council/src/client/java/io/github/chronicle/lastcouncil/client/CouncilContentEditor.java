package io.github.chronicle.lastcouncil.client;

import com.google.gson.*;
import io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.*;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Focused, paged editor for one literal source definition. */
final class CouncilContentEditor extends CouncilScreen {
    private final CouncilCatalogueScreen catalogue; private View view; private final JsonObject draft;
    private final String digest; private final boolean creating; private int page; private UUID pending; private boolean staleDraft,uncertain; private int waitTicks; private EditBoxWidget textField; private boolean focusText;
    CouncilContentEditor(CouncilCatalogueScreen parent,View view,JsonObject draft,String digest,boolean creating){
        super(creating?"Create definition":"Edit definition",parent);this.catalogue=parent;this.view=view;this.draft=draft;this.digest=digest;this.creating=creating;
    }
    private String value(String key){return draft.has(key)&&!draft.get(key).isJsonNull()?draft.get(key).getAsString():"";}
    private void set(String key,String value){draft.addProperty(key,value);}
    private void number(String key,String value){try{draft.addProperty(key,Integer.parseInt(value));}catch(NumberFormatException e){draft.addProperty(key,value);}}
    View sourceView(){return view;}
    String sourceDigest(){return digest;}
    boolean isCreating(){return creating;}
    CouncilContentEditor resume(CouncilCatalogueScreen parent,boolean stale){var editor=new CouncilContentEditor(parent,view,draft.deepCopy(),digest,creating);editor.page=page;editor.staleDraft=stale;if(stale)editor.feedback="Source is read-only or changed; copy this draft before reopening the definition.";return editor;}
    boolean outcomeUncertain(){return uncertain;}
    void reconciled(){pending=null;uncertain=false;}
    void waitTick(){
        if(pending==null||uncertain||++waitTicks<200)return;
        uncertain=true;CouncilCatalogueClient.retain(this);
        String reason="Save response timed out; its outcome is uncertain. Reopen the source catalogue to check and resume your draft.";
        message(reason);if(client.currentScreen==this)clearAndInit();else if(client.player!=null)client.player.sendMessage(Text.literal(reason),false);
    }
    void accept(View next){acceptPending(next);}
    boolean acceptPending(View next){
        if(pending==null||!pending.equals(next.request()))return false;
        CouncilCatalogueClient.saved(pending);pending=null;boolean wasUncertain=uncertain;uncertain=false;view=next;
        if("Saved.".equals(next.message())){
            CouncilCatalogueClient.clearRetained(this);
            catalogue.saved(next);
            if(client.currentScreen==this)client.setScreen(catalogue);
            else if(wasUncertain&&client.player!=null)client.player.sendMessage(Text.literal("The source save completed."),false);
        }else{
            String reason=next.message().isBlank()?next.reason():next.message();message(reason);
            if(client.currentScreen==this&&!wasUncertain)client.setScreen(new Refusal(this,reason));
            else if(client.currentScreen==this){CouncilCatalogueClient.retain(this);clearAndInit();}
            else {CouncilCatalogueClient.retain(this);if(client.player!=null)client.player.sendMessage(Text.literal("Source save refused: "+reason+" Resume the unsaved draft from the source catalogue."),false);}
        }
        return true;
    }
    @Override public void close(){if(pending!=null&&!uncertain){message("Waiting for save response.");return;}super.close();}
    private void save(){
        if(pending!=null)return;
        byte[] data=new GsonBuilder().disableHtmlEscaping().create().toJson(draft).getBytes(StandardCharsets.UTF_8);if(data.length>2097152){message("Definition exceeds the 2 MiB limit.");return;}
        if(!ClientPlayNetworking.canSend(Request.ID)||!ClientPlayNetworking.canSend(Upload.ID)){message("Connection unavailable.");return;}
        if(staleDraft){message("Source changed; copy this draft before reopening the definition.");return;}
        pending=UUID.randomUUID();if(!CouncilCatalogueClient.saving(pending,this)){pending=null;message("Resume the other unsaved draft first.");return;}waitTicks=0;uncertain=false;clearAndInit();int count=Math.max(1,(data.length+49151)/49152);
        for(int i=0;i<count;i++)ClientPlayNetworking.send(new Upload(pending,digest,i,count,Arrays.copyOfRange(data,i*49152,Math.min(data.length,(i+1)*49152))));
        ClientPlayNetworking.send(new Request(pending,Action.SAVE,view.category(),view.query(),view.page(),view.selected(),view.part(),digest));message("Saving...");
    }
    @Override protected void init(){
        frame();int x=left+12,w=panelWidth-24,y=top+36,bottom=top+panelHeight-38;
        if(pending!=null){
            if(!uncertain)for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget widget)widget.active=false;
            body(uncertain?"Save outcome uncertain. Close and reopen the source catalogue to check and resume the draft.":"Saving. Please wait for the server response.",x,y,w,55);message(uncertain?"Save outcome uncertain.":"Saving...");return;
        }
        Category category=view.category();int pages=category==Category.HOUSES?5:category==Category.KNOWLEDGE?3:category==Category.SECRETS?4:3;
        page=Math.clamp(page,0,pages-1);
        if(category==Category.HOUSES){switch(page){
            case 0 -> field("House name","title",x,y,w,true,false);
            case 1 -> field("Reputation","reputation",x,y,w,true,true);
            case 2 -> field("Description","description",x,y,w,true,true);
            case 3 -> field("Interests","interests",x,y,w,true,true);
            case 4 -> button("Default candidate: "+(draft.has("defaultCandidate")&&draft.get("defaultCandidate").getAsBoolean()?"Yes":"No"),x,y,w,()->{draft.addProperty("defaultCandidate",!draft.get("defaultCandidate").getAsBoolean());clearAndInit();});
        }} else if(category==Category.KNOWLEDGE){String[] keys={"secretsPerHouse","goalsPerHouse","knowledgeChancePercent"};String[] labels={"Secrets per House (0-4)","Goals per House (0-4)","Knowledge chance (0-100%)"};numeric(labels[page],keys[page],x,y,w);}
        else {switch(page){
            case 0 -> {field("Name","id",x,y,w,creating,false);numeric("Draw weight","weight",x,y+47,w);}
            case 1 -> {field(category==Category.SECRETS?"Secret text":"Goal text","text",x,y,w,true,true);button("Insert variable...",x,bottom-23,w,()->client.setScreen(new Variables(this,draft,category==Category.SECRETS)));}
            case 2 -> {body("Applicable Houses",x,y,w,12);button("Choose Houses...",x,y+15,w,()->client.setScreen(new Scope(this,view.houses(),draft)));}
            case 3 -> {body("Optional numbers are chosen from an inclusive range when this secret is assigned.",x,y,w,40);button("Manage numbers...",x,y+44,w,()->client.setScreen(new Numbers(this,draft)));}
        }}
        int bw=(w-12)/4;button("<",x,bottom,bw,()->{page--;clearAndInit();}).active=page>0;
        button(">",x+bw+4,bottom,bw,()->{page++;clearAndInit();}).active=page+1<pages;
        button("Cancel",x+2*(bw+4),bottom,bw,this::close);
        button("Save",x+3*(bw+4),bottom,bw,this::save).active=pending==null&&!staleDraft;
        message(feedback.isBlank()?"Field page "+(page+1)+" / "+pages:feedback);
    }
    private void field(String label,String key,int x,int y,int w,boolean editable,boolean multiline){
        body(label,x,y,w,12);
        if(multiline){int height=Math.max(35,Math.min(100,panelHeight-140));boolean returning="text".equals(key)&&focusText&&textField!=null&&textField.getWidth()==w&&textField.getHeight()==height;var f=returning?textField:EditBoxWidget.builder().x(x).y(y+13).build(textRenderer,w,height,Text.literal(label));f.setPosition(x,y+13);addDrawableChild(f);if(!returning){f.setMaxLength(2097152);f.setText(value(key));f.setChangeListener(v->set(key,v));}if("text".equals(key)){textField=f;if(focusText){setFocused(f);focusText=false;}}}
        else {var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y+13,w,20,Text.literal(label)));f.setMaxLength(2097152);f.setText(value(key));f.setEditable(editable);f.setChangedListener(v->set(key,v));}
    }
    private void numeric(String label,String key,int x,int y,int w){body(label,x,y,w,12);var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y+13,w,20,Text.literal(label)));f.setMaxLength(12);f.setText(value(key));f.setChangedListener(v->number(key,v));}
    private static final class Refusal extends CouncilScreen {
        private final String reason;
        Refusal(Screen parent,String reason){super("Save refused",parent);this.reason=reason;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;body("refusal",reason,x,top+37,w,panelHeight-80);button("Back to draft",x,top+panelHeight-38,110,this::close);}
    }
    static final class DeleteConfirm extends CouncilScreen {
        private final CouncilCatalogueScreen catalogue;private final View view;
        DeleteConfirm(CouncilCatalogueScreen parent,View view){super("Delete definition?",parent);this.catalogue=parent;this.view=view;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;body("Delete the selected reusable definition? References will be checked by the server.",x,top+40,w,50);button("Cancel",x,top+panelHeight-38,90,this::close);button("Delete",x+94,top+panelHeight-38,90,()->{client.setScreen(catalogue);catalogue.send(Action.DELETE,view.category(),view.query(),view.page(),view.selected(),view.part());});}
    }
    private static final class Scope extends CouncilScreen {
        final List<HouseRef> houses;final JsonObject draft;final Set<String> chosen=new LinkedHashSet<>();
        Scope(Screen parent,List<HouseRef> houses,JsonObject draft){super("Applicable Houses",parent);this.houses=houses;this.draft=draft;if(draft.has("applicableHouseIds"))for(JsonElement e:draft.getAsJsonArray("applicableHouseIds"))chosen.add(e.getAsString());}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;List<Choice> choices=new ArrayList<>();for(HouseRef h:houses)choices.add(new Choice(h.id(),(chosen.contains(h.id())?"[x] ":"[ ] ")+h.label()));rows("scope",choices,"",x,top+36,w,panelHeight-80,id->{if(!chosen.remove(id))chosen.add(id);clearAndInit();});button("Cancel",x,top+panelHeight-38,90,this::close);button("Done",x+94,top+panelHeight-38,90,()->{JsonArray a=new JsonArray();chosen.forEach(a::add);draft.add("applicableHouseIds",a);close();});}
    }
    private static final class Variables extends CouncilScreen {
        final CouncilContentEditor editor;final JsonObject draft;final boolean secret;String selected="house.own";
        Variables(CouncilContentEditor parent,JsonObject draft,boolean secret){super("Insert variable",parent);this.editor=parent;this.draft=draft;this.secret=secret;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;List<Choice> choices=new ArrayList<>();
            choices.add(new Choice("house.own","Own House - original recipient"));
            choices.add(new Choice("house.other","Other House - random other participant"));
            if(secret&&draft.has("numbers"))draft.getAsJsonObject("numbers").entrySet().forEach(e->{JsonObject range=e.getValue().getAsJsonObject();choices.add(new Choice("number."+e.getKey(),e.getKey()+" ("+range.get("min").getAsInt()+" to "+range.get("max").getAsInt()+")"));});
            rows("variables",choices,selected,x,top+36,w,panelHeight-80,id->{selected=id;clearAndInit();});
            button("Cancel",x,top+panelHeight-38,90,this::close);button("Insert",x+94,top+panelHeight-38,90,()->{editor.textField.setFocused(true);("${"+selected+"}").codePoints().forEach(c->editor.textField.charTyped(new net.minecraft.client.input.CharInput(c,0)));editor.focusText=true;close();});
        }
    }
    private static final class Numbers extends CouncilScreen {
        final JsonObject draft;final LinkedHashMap<String,JsonObject> numbers=new LinkedHashMap<>();String selected="";
        Numbers(Screen parent,JsonObject draft){super("Number variables",parent);this.draft=draft;if(draft.has("numbers"))draft.getAsJsonObject("numbers").entrySet().forEach(e->numbers.put(e.getKey(),e.getValue().getAsJsonObject().deepCopy()));}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+35;
            int facts=draft.has("facts")?draft.getAsJsonObject("facts").size():0;
            button("+ Add number",x,y,w,()->client.setScreen(new NumberEdit(this,"","0","0",(k,min,max)->{if(numbers.containsKey(k))return false;numbers.put(k,range(min,max));selected=k;return true;}))).active=numbers.size()+facts<124;
            List<Choice> choices=numbers.entrySet().stream().map(e->new Choice(e.getKey(),e.getKey()+" ("+e.getValue().get("min").getAsInt()+" to "+e.getValue().get("max").getAsInt()+")")).toList();
            if(panelHeight<220)compactRows("numbers",choices,selected,x,y+23,w,true,id->{selected=id;clearAndInit();});
            else rows("numbers",choices,selected,x,y+23,w,panelHeight-150,id->{selected=id;clearAndInit();});int b=top+panelHeight-60;
            button("Edit",x,b,70,()->{JsonObject r=numbers.get(selected);client.setScreen(new NumberEdit(this,selected,r.get("min").getAsString(),r.get("max").getAsString(),(k,min,max)->{if(!k.equals(selected)&&numbers.containsKey(k))return false;numbers.remove(selected);numbers.put(k,range(min,max));selected=k;return true;}));}).active=numbers.containsKey(selected);
            button("Remove",x+74,b,70,()->{numbers.remove(selected);selected="";clearAndInit();}).active=numbers.containsKey(selected);
            button("Cancel",x,top+panelHeight-38,90,this::close);button("Done",x+94,top+panelHeight-38,90,()->{JsonObject o=new JsonObject();numbers.forEach(o::add);draft.add("numbers",o);close();});
        }
        private static JsonObject range(int min,int max){JsonObject o=new JsonObject();o.addProperty("min",min);o.addProperty("max",max);return o;}
    }
    private interface NumberDone {boolean accept(String name,int min,int max);}
    private static final class NumberEdit extends CouncilScreen {
        String name,min,max;final NumberDone done;final boolean editing;
        NumberEdit(Screen parent,String name,String min,String max,NumberDone done){super("Number variable",parent);this.name=name;this.min=min;this.max=max;this.done=done;this.editing=!name.isBlank();}
        @Override protected void init(){frame();boolean compact=panelHeight<220;int x=left+12,w=panelWidth-24,y=top+(compact?30:35),step=compact?30:34,label=compact?10:12,height=compact?18:20;
            body("Name",x,y,w,label);var n=addDrawableChild(new TextFieldWidget(textRenderer,x,y+label,w,height,Text.literal("Name")));n.setMaxLength(64);n.setText(name);n.setEditable(!editing);n.setChangedListener(v->name=v);
            body("Minimum (inclusive)",x,y+step,w,label);var lo=addDrawableChild(new TextFieldWidget(textRenderer,x,y+step+label,w,height,Text.literal("Minimum")));lo.setMaxLength(11);lo.setText(min);lo.setChangedListener(v->min=v);
            body("Maximum (inclusive)",x,y+2*step,w,label);var hi=addDrawableChild(new TextFieldWidget(textRenderer,x,y+2*step+label,w,height,Text.literal("Maximum")));hi.setMaxLength(11);hi.setText(max);hi.setChangedListener(v->max=v);
            button("Cancel",x,top+panelHeight-38,90,this::close);button("Done",x+94,top+panelHeight-38,90,()->{if(!name.matches("[A-Za-z_][A-Za-z0-9_]*")){message("Use a name starting with a letter or underscore, then letters, numbers or underscores.");return;}try{int a=Integer.parseInt(min),b=Integer.parseInt(max);if(a>b){message("Minimum must not exceed maximum.");return;}if(!done.accept(name,a,b)){message("That number name already exists.");return;}close();}catch(NumberFormatException e){message("Enter signed whole numbers for both bounds.");}});
        }
    }
}
