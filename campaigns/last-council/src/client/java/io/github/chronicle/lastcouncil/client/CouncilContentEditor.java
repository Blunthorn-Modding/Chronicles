package io.github.chronicle.lastcouncil.client;

import com.google.gson.*;
import io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.*;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Focused, paged editor for one literal source definition. */
final class CouncilContentEditor extends CouncilScreen {
    private final CouncilCatalogueScreen catalogue; private View view; private final JsonObject draft;
    private final String digest; private final boolean creating; private int page; private UUID pending; private boolean staleDraft,uncertain; private int waitTicks;
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
        Category category=view.category();int pages=category==Category.HOUSES?5:category==Category.KNOWLEDGE?3:category==Category.SECRETS?5:4;
        page=Math.clamp(page,0,pages-1);
        if(category==Category.HOUSES){switch(page){
            case 0 -> {field("Identifier", "id",x,y,w,creating,false);field("Title","title",x,y+47,w,true,false);}
            case 1 -> field("Reputation","reputation",x,y,w,true,true);
            case 2 -> field("Description","description",x,y,w,true,true);
            case 3 -> field("Interests","interests",x,y,w,true,true);
            case 4 -> button("Default candidate: "+(draft.has("defaultCandidate")&&draft.get("defaultCandidate").getAsBoolean()?"Yes":"No"),x,y,w,()->{draft.addProperty("defaultCandidate",!draft.get("defaultCandidate").getAsBoolean());clearAndInit();});
        }} else if(category==Category.KNOWLEDGE){String[] keys={"secretsPerHouse","goalsPerHouse","knowledgeChancePercent"};String[] labels={"Secrets per House (0-4)","Goals per House (0-4)","Knowledge chance (0-100%)"};numeric(labels[page],keys[page],x,y,w);}
        else {switch(page){
            case 0 -> {field("Identifier","id",x,y,w,creating,false);numeric("Weight","weight",x,y+47,w);}
            case 1 -> field("Literal template text","text",x,y,w,true,true);
            case 2 -> {body("Applicable Houses",x,y,w,12);button("Choose Houses...",x,y+15,w,()->client.setScreen(new Scope(this,view.houses(),draft)));}
            case 3 -> {if(category==Category.SECRETS){body("Fact defaults",x,y,w,12);button("Edit facts...",x,y+15,w,()->client.setScreen(new Facts(this,draft)));}else previewButton(x,y,w);}
            case 4 -> previewButton(x,y,w);
        }}
        int bw=(w-12)/4;button("<",x,bottom,bw,()->{page--;clearAndInit();}).active=page>0;
        button(">",x+bw+4,bottom,bw,()->{page++;clearAndInit();}).active=page+1<pages;
        button("Cancel",x+2*(bw+4),bottom,bw,this::close);
        button("Save",x+3*(bw+4),bottom,bw,this::save).active=pending==null&&!staleDraft;
        message(feedback.isBlank()?"Field page "+(page+1)+" / "+pages:feedback);
    }
    private void previewButton(int x,int y,int w){button("Preview sample...",x,y,w,()->client.setScreen(new Preview(this,value("text"))));}
    private void field(String label,String key,int x,int y,int w,boolean editable,boolean multiline){
        body(label,x,y,w,12);
        if(multiline){var f=addDrawableChild(EditBoxWidget.builder().x(x).y(y+13).build(textRenderer,w,Math.max(35,Math.min(100,panelHeight-120)),Text.literal(label)));f.setMaxLength(2097152);f.setText(value(key));f.setChangeListener(v->set(key,v));}
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
    private static final class Facts extends CouncilScreen {
        final JsonObject draft;final LinkedHashMap<String,String> facts=new LinkedHashMap<>();String selected="";
        Facts(Screen parent,JsonObject draft){super("Fact defaults",parent);this.draft=draft;if(draft.has("facts"))draft.getAsJsonObject("facts").entrySet().forEach(e->facts.put(e.getKey(),e.getValue().getAsString()));}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24,y=top+35;button("+ Add fact",x,y,w,()->client.setScreen(new Fact(this,"","",(k,v)->{if(facts.containsKey(k))return false;facts.put(k,v);selected=k;return true;})));List<Choice> choices=facts.keySet().stream().map(k->new Choice(k,k)).toList();rows("facts",choices,selected,x,y+23,w,panelHeight-150,id->{selected=id;clearAndInit();});int b=top+panelHeight-60;button("Edit",x,b,70,()->client.setScreen(new Fact(this,selected,facts.get(selected),(k,v)->{if(!k.equals(selected)&&facts.containsKey(k))return false;facts.remove(selected);facts.put(k,v);selected=k;return true;}))).active=facts.containsKey(selected);button("Remove",x+74,b,70,()->{facts.remove(selected);selected="";clearAndInit();}).active=facts.containsKey(selected);button("Cancel",x,top+panelHeight-38,90,this::close);button("Done",x+94,top+panelHeight-38,90,()->{JsonObject o=new JsonObject();facts.forEach(o::addProperty);draft.add("facts",o);close();});}
    }
    private static final class Fact extends CouncilScreen {
        String key,val;final Consumer2 done;
        Fact(Screen parent,String key,String val,Consumer2 done){super("Fact default",parent);this.key=key;this.val=val;this.done=done;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;input("Key",key,x,top+35,w,v->key=v);body("Value",x,top+82,w,12);var f=addDrawableChild(EditBoxWidget.builder().x(x).y(top+95).build(textRenderer,w,Math.max(30,Math.min(70,panelHeight-145)),Text.literal("Value")));f.setMaxLength(2097152);f.setText(val);f.setChangeListener(v->val=v);button("Cancel",x,top+panelHeight-38,90,this::close);button("Done",x+94,top+panelHeight-38,90,()->{if(key.isBlank()){message("Enter a fact key.");return;}if(!done.accept(key,val)){message("That fact key already exists.");return;}close();});}
        void input(String label,String value,int x,int y,int w,Consumer<String> change){body(label,x,y,w,12);var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y+13,w,20,Text.literal(label)));f.setMaxLength(2097152);f.setText(value);f.setChangedListener(change);}
    }
    private interface Consumer2 {boolean accept(String key,String value);}
    static final class Preview extends CouncilScreen {
        final String template;String bindings="{}",result="";
        Preview(Screen parent,String template){super("Sample preview",parent);this.template=template;}
        @Override protected void init(){frame();int x=left+12,w=panelWidth-24;body("Explicit JSON string bindings",x,top+35,w,12);var f=addDrawableChild(EditBoxWidget.builder().x(x).y(top+48).build(textRenderer,w,45,Text.literal("Sample bindings")));f.setMaxLength(2097152);f.setText(bindings);f.setChangeListener(v->bindings=v);body("preview",result,x,top+97,w,Math.max(15,panelHeight-145));button("Render",x,top+panelHeight-38,90,this::renderSample);button("Close",x+94,top+panelHeight-38,90,this::close);}
        void renderSample(){try{JsonObject o=JsonParser.parseString(bindings).getAsJsonObject();for(var e:o.entrySet())if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException();Matcher m=Pattern.compile("\\$\\{([^}]+)}").matcher(template);StringBuffer out=new StringBuffer();Set<String> missing=new LinkedHashSet<>();while(m.find()){String key=m.group(1);if(!o.has(key))missing.add(key);m.appendReplacement(out,Matcher.quoteReplacement(o.has(key)?o.get(key).getAsString():m.group()));}m.appendTail(out);result=out.toString();feedback=missing.isEmpty()?"":"Missing: "+String.join(", ",missing);clearAndInit();}catch(RuntimeException e){message("Enter a JSON object with string values.");}}
    }
}
