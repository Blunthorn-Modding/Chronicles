package io.github.chronicle.lastcouncil.client;
import net.minecraft.client.gui.screen.Screen;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
final class MotionDraftScreen extends CouncilScreen {
    interface Submit {
        void accept(String title,String body,boolean secret,boolean thirds,Category category,String realm,long revision);
    }
    interface LegacySubmit { void accept(String title,String body,boolean secret,boolean thirds); }
    private final Submit submit;
    private String name="",content="";
    private boolean secret,thirds;
    private Category category=Category.GENERAL;
    private RealmChoice realm;
    private List<RealmChoice> realms;
    MotionDraftScreen(Screen parent,LegacySubmit submit) { this(parent,List.of(),(title,body,secret,thirds,category,realm,revision)->submit.accept(title,body,secret,thirds)); }
    MotionDraftScreen(Screen parent,List<RealmChoice> realms,Submit submit) {
        super("Motion draft",parent);
        this.submit=submit; this.realms=List.copyOf(realms);
    }
    @Override protected void init() {
        frame();
        boolean compact=panelHeight<260;
        int x=left+12,w=panelWidth-24,y=top+(compact?42:49);
        TextFieldWidget titleField=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Title")));
        titleField.setMaxLength(320);
        titleField.setText(name);
        titleField.setChangedListener(s->name=s);
        y=top+(compact?76:88);
        int editorHeight=Math.max(compact?24:30,panelHeight-226);
        EditBoxWidget bodyField=addDrawableChild(EditBoxWidget.builder().build(textRenderer,w,editorHeight,
                Text.literal("Motion text")));
        bodyField.setPosition(x,y);
        bodyField.setMaxLength(8192);
        bodyField.setText(content);
        bodyField.setChangeListener(s->content=s);
        y+=editorHeight+16;
        int half=(w-4)/2;
        button("Category: "+categoryLabel(category),x,y,w,()->choose("Motion category",List.of(new Choice("GENERAL","General"),new Choice("DECLARE_WAR","Declare foreign war"),new Choice("MAKE_PEACE","Make peace")),id->{category=Category.valueOf(id);realm=null;}));
        y+=22;
        if(category!=Category.GENERAL) {
            var eligible=realms.stream().filter(r->r.atWar()==(category==Category.MAKE_PEACE)).toList();
            int refreshWidth=Math.min(100,w/3);
            button(realm==null?"Choose foreign realm...":realm.name(),x,y,w-refreshWidth-4,()->choose("Choose foreign realm",eligible.stream().map(r->new Choice(r.id(),r.name())).toList(),id->realm=eligible.stream().filter(r->r.id().equals(id)).findFirst().orElse(null))).active=!eligible.isEmpty();
            button("Refresh realms",x+w-refreshWidth,y,refreshWidth,()->{var owner=ownerBusiness();if(owner!=null)owner.refreshRealms();}).active=ownerBusiness()!=null;
        }
        y+=22;
        button("Visibility: "+(secret?"Secret":"Public"),x,y,half,()-> {
            secret=!secret; clearAndInit();
        });
        button("Threshold: "+(category!=Category.GENERAL||thirds?"2/3":"Majority"),x+half+4,y,half,()-> {
            thirds=!thirds; clearAndInit();
        }).active=category==Category.GENERAL;
        y+=26;
        button("Submit",x,y,half,()-> {
            if(name.isBlank()||content.isBlank()||name.codePointCount(0,name.length())>160||content.codePointCount(0,content.length())>4096) {
                feedback="Title and text required (maximum 160 and 4096 characters)."; return;
            }
            if(category!=Category.GENERAL&&realm==null){feedback="Choose an eligible foreign realm.";return;}
            client.setScreen(parent); submit.accept(name,content,secret,category!=Category.GENERAL||thirds,category,realm==null?"":realm.id(),realm==null?0:realm.revision());
        });
        button("Cancel",x+half+4,y,half,this::close);
    }
    CouncilBusinessScreen ownerBusiness(){return parent instanceof CouncilBusinessScreen business?business:null;}
    void refreshRealms(List<RealmChoice> choices){
        realms=List.copyOf(choices);
        if(realm!=null){String id=realm.id();realm=realms.stream().filter(r->r.id().equals(id)&&r.atWar()==(category==Category.MAKE_PEACE)).findFirst().orElse(null);}
        feedback="Realm choices refreshed. Review your selection.";
        if(client.currentScreen==this)rebuildPreservingFocus();
    }
    void refused(String message){feedback=message;}
    @Override public void close(){if(parent instanceof CouncilBusinessScreen business)business.discardDraft(this);super.close();}
    private static String categoryLabel(Category c){return switch(c){case GENERAL->"General";case DECLARE_WAR->"Declare foreign war";case MAKE_PEACE->"Make peace";};}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        context.drawTextWithShadow(textRenderer,Text.literal("Title"),left+12,top+(panelHeight<260?30:36),0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,Text.literal("Proposal"),left+12,top+(panelHeight<260?64:76),0xFFFFFFFF);
    }
}
