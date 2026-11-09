package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.*;
import io.github.chronicle.core.scenario.ScenarioExportPreview;
import java.util.*;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Native scenario manager. Only acknowledged server results expose copyable paths. */
final class ScenarioContentScreen extends Screen {
    private enum Mode { LANDING, CREATE, PREVIEW, RESULT }
    private static final Identifier POPUP=Identifier.ofVanilla("popup/background");
    private final CoreMenuScreen parent;
    private long openingToken;
    private List<ScenarioExportPreview> providers;
    private ScenarioExportPreview preview;
    private ScenarioResult result;
    private Mode mode;
    private Action pendingAction;
    private long pendingRequestToken;
    private String requestedProvider="";
    private boolean editedId,generatingId;
    private int pendingTicks,providerOffset;
    private String status="",titleDraft="",idDraft="custom:scenario";
    private boolean stale,failed;
    private TextFieldWidget titleField,idField;
    private ButtonWidget submit;

    ScenarioContentScreen(CoreMenuScreen parent,long token,List<ScenarioExportPreview> providers,ScenarioExportPreview preview,String status){
        super(Text.literal("Scenario content"));this.parent=parent;openingToken=token;this.providers=List.copyOf(providers);this.preview=preview;this.status=status==null?"":status;mode=preview==null?Mode.LANDING:Mode.PREVIEW;
    }
    long openingToken(){return openingToken;}
    CoreMenuScreen parentMenu(){return parent;}
    private int panelWidth(){return Math.min(440,Math.max(240,width-24));}
    private int panelTop(){return Math.max(6,(height-240)/2+6);}
    private boolean pending(){return pendingAction!=null;}
    private ButtonWidget button(String label,int x,int y,int w,Runnable action){
        return addDrawableChild(ButtonWidget.builder(Text.literal(label),ignored->action.run()).dimensions(x,y,w,18).build());
    }
    @Override protected void init(){
        if(titleField!=null)titleDraft=titleField.getText();if(idField!=null)idDraft=idField.getText();titleField=idField=null;submit=null;
        int w=panelWidth(),x=(width-w)/2,y=panelTop(),footer=y+206;
        if(mode==Mode.LANDING){
            button("Create custom scenario...",x,y+46,w,()->{mode=Mode.CREATE;status="";stale=false;clearAndInit();}).active=!pending();
            int rows=Math.min(5,providers.size());providerOffset=Math.clamp(providerOffset,0,Math.max(0,providers.size()-rows));
            for(int i=0;i<rows;i++){var provider=providers.get(providerOffset+i);
                button(textRenderer.trimToWidth(provider.title(),w-12),x,y+72+i*20,w,()->{requestedProvider=provider.providerId();send(Action.SCENARIO_CONTENT_OPEN,()->parent.openScenarioContent(provider.providerId(),this));}).active=!pending();
            }
            if(providers.size()>rows){
                button("Up",x+w-104,y+22,50,()->{providerOffset--;clearAndInit();}).active=!pending()&&providerOffset>0;
                button("Down",x+w-50,y+22,50,()->{providerOffset++;clearAndInit();}).active=!pending()&&providerOffset+rows<providers.size();
            }
            if(parent.state().scenarioResult()!=null&&!parent.state().scenarioResult().kind().equals("PREVIEW"))button("Last result",x,footer,90,()->{result=parent.state().scenarioResult();mode=Mode.RESULT;status="";clearAndInit();});
        }else if(mode==Mode.CREATE){
            titleField=addDrawableChild(new TextFieldWidget(textRenderer,x,y+52,w,18,Text.literal("Scenario name")));titleField.setMaxLength(160);titleField.setText(titleDraft);
            idField=addDrawableChild(new TextFieldWidget(textRenderer,x,y+92,w,18,Text.literal("Scenario ID")));idField.setMaxLength(160);idField.setText(idDraft);
            titleField.setChangedListener(value->{titleDraft=value;if(!editedId){String slug=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");if(slug.length()>130)slug=slug.substring(0,130);generatingId=true;idField.setText("custom:"+(slug.isEmpty()?"scenario":slug));generatingId=false;}updateSubmit();});
            idField.setChangedListener(value->{idDraft=value;if(!generatingId)editedId=true;updateSubmit();});setInitialFocus(titleField);
            submit=button(pending()?"Creating...":"Create setup",x,footer,Math.max(110,w-116),()->{
                String requestedTitle=titleField.getText().strip(),requestedId=idField.getText().strip();
                titleField.setText(requestedTitle);idField.setText(requestedId);
                send(Action.SCENARIO_CONTENT_CREATE,()->parent.createScenarioContent(this,requestedId,requestedTitle));
            });updateSubmit();
            button("Back",x+w-112,footer,54,this::landing).active=!pending();
        }else if(mode==Mode.PREVIEW){
            submit=button(pending()?"Exporting...":"Export content",x,footer,Math.max(110,w-116),()->send(Action.SCENARIO_CONTENT_EXPORT,()->parent.exportScenarioContent(this)));
            submit.active=!pending()&&!stale&&preview!=null&&preview.available();
            button("Back",x+w-112,footer,54,this::landing).active=!pending();
        }else{
            var path=addDrawableChild(new TextFieldWidget(textRenderer,x,y+100,w,18,Text.literal("Server file")));
            path.setMaxLength(2048);path.setText(result.path());path.setEditable(false);
            button("Copy path",x,y+124,90,()->{client.keyboard.setClipboard(result.path());status="Full server path copied.";});
            button("Back",x,footer,90,this::landing);
        }
        button("Close",x+w-54,footer,54,this::close);
    }
    private void updateSubmit(){if(submit!=null&&mode==Mode.CREATE)submit.active=!pending()&&!stale&&!titleDraft.isBlank()&&idDraft.strip().matches("[a-z0-9_.-]+:[a-z0-9_./-]+");}
    private void landing(){mode=Mode.LANDING;preview=null;result=null;status="";stale=false;failed=false;clearAndInit();}
    private void send(Action action,Runnable request){if(pending()||stale)return;pendingAction=action;pendingRequestToken=openingToken;pendingTicks=0;failed=false;status=action==Action.SCENARIO_CONTENT_CREATE?"Creating a separate authoring setup...":action==Action.SCENARIO_CONTENT_EXPORT?"Exporting; waiting for the server...":"Loading scenario content...";clearAndInit();request.run();}
    void rejected(String reason){pendingAction=null;failed=true;status=reason;clearAndInit();}
    void markStale(String reason){stale=true;rejected(reason);}
    void serverUpdated(View updated,boolean changedToken){
        providers=List.copyOf(updated.scenarioProviders());openingToken=updated.token();
        if(pending()&&!updated.success()){rejected(updated.message().isBlank()?"The server refused this action.":updated.message());return;}
        if(pending()&&updated.scenarioResult()!=null&&updated.scenarioResult().requestToken()==pendingRequestToken){
            var acknowledged=updated.scenarioResult();
            boolean matches=pendingAction==Action.SCENARIO_CONTENT_CREATE?acknowledged.kind().equals("KIT")&&acknowledged.scenarioId().equals(idDraft):
                    pendingAction==Action.SCENARIO_CONTENT_EXPORT&&preview!=null&&!acknowledged.kind().equals("KIT")&&acknowledged.scenarioId().equals(preview.providerId());
            if(matches){result=acknowledged;mode=Mode.RESULT;pendingAction=null;stale=false;failed=false;status="";clearAndInit();return;}
        }
        if(pendingAction==Action.SCENARIO_CONTENT_OPEN&&updated.scenarioPreview()!=null&&updated.scenarioResult()!=null&&updated.scenarioResult().kind().equals("PREVIEW")&&updated.scenarioResult().requestToken()==pendingRequestToken&&updated.scenarioPreview().providerId().equals(requestedProvider)){
            preview=updated.scenarioPreview();mode=Mode.PREVIEW;pendingAction=null;stale=false;failed=false;status="";clearAndInit();return;
        }
        if(!pending()&&mode==Mode.PREVIEW&&changedToken&&updated.scenarioPreview()==null)markStale("Server state changed. Go Back and select this scenario again.");
    }
    @Override public void tick(){super.tick();if(pending()&&++pendingTicks>=200)markStale("The server has not confirmed this action. Close and reopen; check Last result before retrying.");}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        int w=panelWidth(),x=(width-w)/2,y=panelTop();
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,POPUP,x-6,y-6,w+12,240);
        line(context,mode==Mode.RESULT?result.kind().equals("KIT")?"Authoring setup created":result.kind().equals("PROFILE")?"Scenario profile exported":"Content exported":"Scenario content",x,y+20,w,0xFFFFFFFF);
        if(mode==Mode.LANDING){if(providers.isEmpty())wrapped(context,"Create an empty scenario using the installed mechanics.",x,y+76,w,0xFFCCCCCC,3);}
        else if(mode==Mode.CREATE){
            line(context,"Scenario name",x,y+39,w,0xFFCCCCCC);line(context,"Scenario ID",x,y+79,w,0xFFCCCCCC);
            wrapped(context,"Starts with empty reusable content. Installed mechanics remain available.",x,y+122,w,0xFFCCCCCC,3);
            line(context,"Uses a separate world and server configuration.",x,y+156,w,0xFFFFCC66);
        }else if(mode==Mode.PREVIEW&&preview!=null){
            line(context,preview.title(),x,y+40,w,0xFFCCCCCC);
            line(context,"Roles: "+preview.roles()+"    Books: "+preview.books(),x,y+70,w,0xFFFFFFFF);
            line(context,"Loot tables: "+preview.lootTables()+"    Events: "+preview.events(),x,y+82,w,0xFFFFFFFF);
            line(context,"Encounter groups: "+preview.encounterGroups(),x,y+94,w,0xFFFFFFFF);
            line(context,"Event pools: "+preview.eventPools()+"    Scenario sections: "+preview.extensions(),x,y+106,w,0xFFFFFFFF);
            wrapped(context,"Exports reusable content. Map setup and player progress stay with the current world.",x,y+123,w,0xFFCCCCCC,2);
            line(context,"Event templates need target-map review.",x,y+152,w,0xFFFFCC66);
        }else if(mode==Mode.RESULT){
            wrapped(context,result.kind().equals("KIT")?"Empty content; the current world was not changed.":"The server saved and verified this file.",x,y+46,w,0xFF77FF77,3);
            line(context,"Server file",x,y+86,w,0xFFCCCCCC);
            String next=result.kind().equals("CONTENT")?"Reviewed input for the next scenario mod build. The running mod was not rewritten.":
                    "Start a separate server with this file at config/chronicle/scenario-profile.json and the required mechanics mods. Use its normal editors to work on the content.";
            wrapped(context,next,x,y+151,w,0xFFCCCCCC,4);
        }
        if(mode!=Mode.RESULT&&!status.isBlank())wrapped(context,status,x,y+177,w,failed?0xFFFFAA77:0xFF77FF77,3);
        else if(mode==Mode.RESULT&&!status.isBlank())line(context,status,x,y+194,w,0xFF77FF77);
        super.render(context,mouseX,mouseY,delta);
    }
    private void line(DrawContext c,String value,int x,int y,int w,int color){c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(value,w)),x,y,color);}
    private void wrapped(DrawContext c,String value,int x,int y,int w,int color,int max){int count=0;for(var line:textRenderer.wrapLines(Text.literal(value),w)){if(count==max)break;c.drawTextWithShadow(textRenderer,line,x,y+count++*10,color);}}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){parent.dismissScenario(this);client.setScreen(parent);}
}
