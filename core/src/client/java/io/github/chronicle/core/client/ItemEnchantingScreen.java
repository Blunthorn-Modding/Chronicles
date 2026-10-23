package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.network.ItemEnchantingPayloads.Phase;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/** Native inventory around one entry, a silent ritual illustration and a real owned exit. */
public final class ItemEnchantingScreen extends HandledScreen<ItemEnchantingScreenHandler> {
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private static final Identifier SLOT = Identifier.ofVanilla("container/slot");
    private static final Identifier TRAY = Identifier.of("chronicle_core", "workshop/panel");
    public static final int FORGE_X=88, FORGE_Y=51, FORGE_SIZE=48;
    private ItemEnchantingPayloads.View state;
    private ButtonWidget offer;
    private int pendingTicks=-1;
    private boolean timedOut;

    public ItemEnchantingScreen(ItemEnchantingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth=ItemEnchantingScreenHandler.WIDTH;
        backgroundHeight=ItemEnchantingScreenHandler.HEIGHT;
        state=handler.view();
    }
    public ItemEnchantingPayloads.View state(){return state;}
    public boolean pending(){return pendingTicks>=0;}

    public void accept(ItemEnchantingPayloads.View next) {
        if(next.opening() || next.syncId()!=handler.syncId || !next.sessionId().equals(state.sessionId())
                || next.revision()<=state.revision())return;
        state=next;handler.view(next);pendingTicks=-1;timedOut=false;handler.clientPending(false);updateOffer();
    }
    @Override protected void init() {
        super.init();
        // This native selectable control owns focus, input and narration. Its visual face
        // is the supplied illustration, so it deliberately is not a drawable child.
        offer=addSelectableChild(ButtonWidget.builder(Text.translatable("screen.chronicle.item_workshop.offer"),ignored->sendOffer())
                .dimensions(x+FORGE_X,y+FORGE_Y,FORGE_SIZE,FORGE_SIZE).build());
        updateOffer();
    }
    private void updateOffer(){if(offer!=null)offer.active=!pending()&&!timedOut&&state.available()&&state.offerable();}

    private boolean sendOffer() {
        if(pending()||timedOut||!state.available()||!state.offerable()||client==null||client.player==null
                ||client.player.currentScreenHandler!=handler||!ClientPlayNetworking.canSend(ItemEnchantingPayloads.Offer.ID))return false;
        pendingTicks=0;handler.clientPending(true);updateOffer();
        ClientPlayNetworking.send(new ItemEnchantingPayloads.Offer(handler.syncId,state.sessionId(),state.actionToken()));
        return true;
    }
    public boolean offerForTest(){return sendOffer();}
    public boolean triggerForTest(String action){return "Offer".equals(action)&&offerForTest();}

    @Override protected void drawBackground(DrawContext context,float delta,int mouseX,int mouseY) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,POPUP,x,y,backgroundWidth,backgroundHeight);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,TRAY,x+24,y+128,176,86);
        for(Slot slot:handler.slots)
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,SLOT,x+slot.x-1,y+slot.y-1,18,18);
        if(state.phase()==Phase.RESULT&&!handler.output().isEmpty()){
            int left=x+ItemEnchantingScreenHandler.OUTPUT_X-1,top=y+ItemEnchantingScreenHandler.OUTPUT_Y-1;
            context.fill(left,top,left+18,top+1,0xFFB365CF);context.fill(left,top+17,left+18,top+18,0xFFB365CF);
            context.fill(left,top+1,left+1,top+17,0xFFB365CF);context.fill(left+17,top+1,left+18,top+17,0xFFB365CF);
        }
        Identifier illustration=!state.available()?state.artwork().offSprite():state.phase()==Phase.WORKING?
                state.artwork().workingSprite():state.artwork().idleSprite();
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,illustration,x+FORGE_X,y+FORGE_Y,FORGE_SIZE,FORGE_SIZE);
        if(state.available()&&(state.phase()==Phase.OFFERED||state.phase()==Phase.WORKING))stir(context);
        if(offer!=null&&offer.active&&offer.isFocused()){
            int left=x+FORGE_X,top=y+FORGE_Y,right=left+FORGE_SIZE,bottom=top+FORGE_SIZE;
            context.fill(left-1,top-1,right+1,top,0xFFFFFFFF);context.fill(left-1,bottom,right+1,bottom+1,0xFFFFFFFF);
            context.fill(left-1,top,left,bottom,0xFFFFFFFF);context.fill(right,top,right+1,bottom,0xFFFFFFFF);
        }
    }
    private void stir(DrawContext context) {
        long interval=state.phase()==Phase.WORKING?140:600;
        double angle=(Util.getMeasuringTimeMs()/interval%4)*Math.PI/2;
        for(int index=0;index<3;index++){
            double phase=angle+index*2*Math.PI/3;
            int left=x+111+(int)Math.round(Math.cos(phase)*4),top=y+74+(int)Math.round(Math.sin(phase)*4);
            int colour=index==0?0xDDF6B968:index==1?0xDDE18943:0xDD9B462C;
            context.fill(left,top,left+2,top+2,colour);
        }
    }
    @Override protected void drawForeground(DrawContext context,int mouseX,int mouseY) {
        context.drawCenteredTextWithShadow(textRenderer,title,112,12,0xFFFFFFFF);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        drawMouseoverTooltip(context,mouseX,mouseY);
        if(offer!=null&&offer.active&&mouseX>=x+FORGE_X&&mouseX<x+FORGE_X+FORGE_SIZE
                &&mouseY>=y+FORGE_Y&&mouseY<y+FORGE_Y+FORGE_SIZE)
            context.drawTooltip(textRenderer,Text.translatable("screen.chronicle.item_workshop.offer"),mouseX,mouseY);
    }
    @Override protected void onMouseClick(Slot slot,int index,int button,SlotActionType type) {
        if(index==ItemEnchantingScreenHandler.ENTRY_SLOT&&(pending()||timedOut))return;
        super.onMouseClick(slot,index,button,type);
    }
    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        if(pending()&&++pendingTicks>=200){pendingTicks=-1;timedOut=true;handler.clientPending(true);updateOffer();}
    }
    @Override public boolean shouldPause(){return false;}
}
