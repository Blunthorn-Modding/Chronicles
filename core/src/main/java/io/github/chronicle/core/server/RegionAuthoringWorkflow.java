package io.github.chronicle.core.server;

import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.BlockPosition;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

/** One public select/save flow for ordinary Regions and Regions with initial saved states. */
public final class RegionAuthoringWorkflow {
    private static final String TAG="chronicle_region_draft";
    private static final Map<MinecraftServer,Map<UUID,Selection>> WORLDS=new IdentityHashMap<>();
    private static final class Selection {
        final String id; final UUID token=UUID.randomUUID(); final AuthoringRuntime.Presentation previous;
        AuthorSelectionTools.Lease tool;long epoch;
        Selection(String id,AuthoringRuntime.Presentation previous){this.id=id;this.previous=previous;}
    }
    private RegionAuthoringWorkflow(){}
    public static void register(){
        AttackBlockCallback.EVENT.register((p,w,h,pos,d)->p instanceof ServerPlayerEntity player&&selectPacket(player,h,pos,1)?ActionResult.SUCCESS:ActionResult.PASS);
        UseBlockCallback.EVENT.register((p,w,h,hit)->p instanceof ServerPlayerEntity player&&selectPacket(player,h,hit.getBlockPos(),2)?ActionResult.SUCCESS:ActionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(server->{var leases=WORLDS.get(server);if(leases!=null)for(UUID id:List.copyOf(leases.keySet())){var player=server.getPlayerManager().getPlayer(id);if(player!=null&&!AuthoringRuntime.isAuthor(player))cancelOrdinary(player,false);}});
        ServerDisconnects.register((handler,server)->cancelOrdinary(handler.player,false));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var p:server.getPlayerManager().getPlayerList())cancelOrdinary(p,false);});
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }
    public static String begin(ServerCommandSource source,String name,AreaState initial)throws Exception{
        var player=editor(source);if(name==null||name.isBlank()||name.length()>96)throw new IllegalArgumentException("Enter a Region name of at most 96 characters.");
        var runtime=AuthoringRuntime.get(source.getServer());if(runtime.regionDraft(player.getUuid()).isPresent()||AreaChanges.hasDraft(source.getServer(),player.getUuid()))throw new IllegalStateException("Save or cancel the open Region selection first.");
        if(initial!=null)return AreaChanges.begin(source,name,initial).id();
        String stem=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+","_").replaceAll("^_+|_+$","");if(stem.isBlank())stem="region";if(stem.length()>96)stem=stem.substring(0,96);String id=stem;
        for(int suffix=2;runtime.service().state().sites().containsKey(id);suffix++)id=stem+"_"+suffix;
        var previous=runtime.presentation(player.getUuid());runtime.selection(player.getUuid(),new AuthoringRuntime.Selection(player.getEntityWorld().getRegistryKey().getValue().toString(),null,null));
        try{runtime.beginRegionDraft(player,id);}catch(RuntimeException failure){runtime.restorePresentation(player,runtime.presentationEpoch(player.getUuid()),previous);throw failure;}
        // beginRegionDraft lends the same tool for legacy callers; retain the original presentation here.
        var leases=WORLDS.computeIfAbsent(source.getServer(),s->new HashMap<>());var current=leases.remove(player.getUuid());if(current!=null)AuthorSelectionTools.release(player,current.tool);loan(player,previous);
        instructions(player);return id;
    }
    static void loan(ServerPlayerEntity player,AuthoringRuntime.Presentation previous){
        var server=player.getEntityWorld().getServer();var runtime=AuthoringRuntime.get(server);var draft=runtime.regionDraft(player.getUuid()).orElseThrow();var leases=WORLDS.computeIfAbsent(server,s->new HashMap<>());var existing=leases.get(player.getUuid());
        if(existing!=null&&existing.id.equals(draft.id())&&AuthorSelectionTools.live(player,existing.tool)){existing.epoch=runtime.presentationEpoch(player.getUuid());return;}
        if(existing!=null&&AuthorSelectionTools.live(player,existing.tool))AuthorSelectionTools.release(player,existing.tool);Selection selection=new Selection(draft.id(),existing!=null&&existing.id.equals(draft.id())?existing.previous:previous);selection.tool=AuthorSelectionTools.issue(player,selection.token,TAG,"Region Debug shovel");selection.epoch=runtime.presentationEpoch(player.getUuid());leases.put(player.getUuid(),selection);
        if(selection.tool==null)player.sendMessage(Text.literal("No safe slot was available for the Debug shovel. The pos1/pos2 commands still work."),false);
    }
    static void resumeTool(ServerPlayerEntity player){if(selection(player)!=null&&AuthoringRuntime.isAuthor(player)){var runtime=AuthoringRuntime.get(player.getEntityWorld().getServer());var draft=runtime.regionDraft(player.getUuid()).orElse(null);if(draft!=null&&draft.dimension().equals(player.getEntityWorld().getRegistryKey().getValue().toString()))loan(player,runtime.presentation(player.getUuid()));}}
    static void changed(ServerPlayerEntity player){var selection=selection(player);if(selection!=null)selection.epoch=AuthoringRuntime.get(player.getEntityWorld().getServer()).presentationEpoch(player.getUuid());}
    static void saved(ServerPlayerEntity player){var leases=WORLDS.get(player.getEntityWorld().getServer());Selection s=leases==null?null:leases.remove(player.getUuid());if(s!=null)AuthorSelectionTools.release(player,s.tool);}
    public static String save(ServerCommandSource source)throws Exception{return save(source,false);}
    public static String save(ServerCommandSource source,boolean confirm)throws Exception{var player=editor(source);if(AreaChanges.hasDraft(source.getServer(),player.getUuid())){AreaChanges.saveDraft(source);return "Saving the Region and its initial state. You will receive confirmation when it finishes.";}var runtime=AuthoringRuntime.get(source.getServer());if(!confirm&&runtime.needsBoundsReview(player))return runtime.reviewRegionDraft(player);var saved=runtime.saveRegionDraft(player);return "Saved Region '"+saved.id()+"'.";}
    public static String cancel(ServerCommandSource source){var player=editor(source,false);if(RuntimeHolds.reason(source.getServer())!=null&&!AreaChanges.canCancelOwnCapture(source.getServer(),player.getUuid()))throw new IllegalStateException("Finish pending operations first.");if(AreaChanges.hasDraft(source.getServer(),player.getUuid())){AreaChanges.cancel(source);return "Region selection cancelled.";}if(selection(player)==null&&AuthoringRuntime.get(source.getServer()).regionDraft(player.getUuid()).isEmpty())throw new IllegalStateException("No Region selection is open.");cancelOrdinary(player,true);return "Region selection cancelled; saved geometry was retained.";}
    private static void cancelOrdinary(ServerPlayerEntity player,boolean restore){var leases=WORLDS.get(player.getEntityWorld().getServer());Selection s=leases==null?null:leases.remove(player.getUuid());if(s==null)return;var runtime=AuthoringRuntime.get(player.getEntityWorld().getServer());AuthorSelectionTools.release(player,s.tool);runtime.discardRegionDraft(player.getUuid());if(restore&&AuthoringRuntime.isAuthor(player))runtime.restorePresentation(player,s.epoch,s.previous);}
    private static Selection selection(ServerPlayerEntity player){var leases=WORLDS.get(player.getEntityWorld().getServer());return leases==null?null:leases.get(player.getUuid());}
    public static boolean selectPacket(ServerPlayerEntity player,Hand hand,BlockPos pos,int corner){
        String token=AuthorSelectionTools.token(player.getStackInHand(hand),TAG);if(token==null)return false;
        Selection selection=selection(player);if(hand!=Hand.MAIN_HAND||selection==null||!selection.token.toString().equals(token))return true;
        try{editor(player.getCommandSource());var runtime=AuthoringRuntime.get(player.getEntityWorld().getServer());var draft=runtime.regionDraft(player.getUuid()).orElseThrow();if(!draft.id().equals(selection.id))throw new IllegalStateException("This selection tool is no longer current.");runtime.setRegionDraftCorner(player,corner,new BlockPosition(pos.getX(),pos.getY(),pos.getZ()));player.sendMessage(Text.literal("Region position "+corner+" set at "+pos.toShortString()+"."),false);}catch(Exception failure){player.sendMessage(Text.literal(Objects.requireNonNullElse(failure.getMessage(),"Region corner refused.")),false);}return true;
    }
    public static void instructions(ServerPlayerEntity player){player.sendMessage(Text.literal("Use the Debug shovel: left-click position 1, right-click position 2. Or /chronicle regions pos1 and pos2. Save with /chronicle regions save; cancel with /chronicle regions cancel."),false);}
    private static ServerPlayerEntity editor(ServerCommandSource source){return editor(source,true);}
    private static ServerPlayerEntity editor(ServerCommandSource source,boolean checkHold){var player=source.getPlayer();if(player==null||!source.hasPermissionLevel(2)||!AuthoringRuntime.isAuthor(player)||!AuthoringRuntime.editable(source.getServer()))throw new IllegalStateException("Region selection requires non-active operator Editing.");if(checkHold&&RuntimeHolds.reason(source.getServer())!=null)throw new IllegalStateException("Finish pending operations first.");return player;}
}
