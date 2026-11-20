package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.council.*;
import io.github.chronicle.lastcouncil.network.ElectionPayloads;
import io.github.chronicle.lastcouncil.network.ElectionPayloads.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

/** Receiver-specific election permissions over the existing authoritative proceedings journal. */
public final class ElectionRuntime {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,int[]>> LIMITS=new IdentityHashMap<>();
    private static final class Session {
        final ServerPlayerEntity player;final long token=RANDOM.nextLong(1,Long.MAX_VALUE);final ProceedingsService authority;
        View sent;int page;long seenRevision;boolean invalid;
        Session(ServerPlayerEntity player,ProceedingsService authority){this.player=player;this.authority=authority;seenRevision=authority.state().revision();}
    }
    private ElectionRuntime() { }
    public static void register(){
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{SESSIONS.remove(s);LIMITS.remove(s);});
        ServerDisconnects.register((handler,s)->{var sessions=SESSIONS.get(s);if(sessions!=null)sessions.remove(handler.player.getUuid());var limits=LIMITS.get(s);if(limits!=null)limits.remove(handler.player.getUuid());});
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(q,c)->c.server().execute(()->receive(c.player(),q)));
        ServerTickEvents.END_SERVER_TICK.register(ElectionRuntime::tick);
    }
    private static void receive(ServerPlayerEntity player,Request q){
        var s=player.getEntityWorld().getServer();if(!CouncilRuntime.active()||s.getPlayerManager().getPlayer(player.getUuid())!=player)return;
        var sessions=SESSIONS.computeIfAbsent(s,key->new HashMap<>());var session=sessions.get(player.getUuid());
        try{
            if(q.action()==Action.OPEN){
                if(!rate(s,player.getUuid()))return;
                var authority=ProceedingsRuntime.electionAuthority(s);session=new Session(player,authority);sessions.put(player.getUuid(),session);send(s,session,q.request(),true,"",false);return;
            }
            if(session==null||session.player!=player||q.token()!=session.token)return;
            if(q.action()==Action.CLOSE){sessions.remove(player.getUuid());return;}
            if(!rate(s,player.getUuid()))return;
            var authority=ProceedingsRuntime.electionAuthority(s);
            if(session.invalid||authority!=session.authority){session.invalid=true;throw new IllegalStateException("Council records reloaded. Close and reopen Election.");}
            if(q.action()==Action.STATE){session.page=q.page();send(s,session,q.request(),false,"",false);return;}
            authority.revision(q.revision());var state=authority.state();String refusal;
            if(q.action()==Action.WITHDRAW){
                refusal=withdrawalRefusal(s,player,state);if(!refusal.isBlank())throw new IllegalStateException(refusal);
                String house=CouncilRuntime.rawRoster(s).assignments().get(player.getUuid()).houseId();
                authority.withdrawCandidate(q.request(),house,player.getUuid(),q.reason());
                s.getPlayerManager().broadcast(net.minecraft.text.Text.literal(ProceedingsRuntime.electionLabel(s,house)+" withdrew from the election: "+q.reason().strip()),false);
            }else if(q.action()==Action.END){
                refusal=endRefusal(s,player,state);if(!refusal.isBlank())throw new IllegalStateException(refusal);
                authority.endSuccessionUnresolved(q.request(),player.getUuid(),q.reason());
                s.getPlayerManager().broadcast(net.minecraft.text.Text.literal("The Chancellor ended the election unresolved: "+q.reason().strip()),false);
            }else throw new IllegalArgumentException("Choose an election action.");
            session.page=0;send(s,session,q.request(),false,"Election decision recorded.",true);ProceedingsRuntime.electionChanged(s);refresh(s);
        }catch(IOException failure){ProceedingsRuntime.electionFailed(s,failure);if(session!=null)safeSend(s,session,q.request(),"Election records need Council recovery. Preserve the saved journals.");}
        catch(RuntimeException refused){if(session!=null)safeSend(s,session,q.request(),Objects.toString(refused.getMessage(),"Election action refused."));}
    }
    private static boolean rate(MinecraftServer s,UUID player){int second=s.getTicks()/20;int[] limit=LIMITS.computeIfAbsent(s,key->new HashMap<>()).computeIfAbsent(player,key->new int[]{second,0});if(limit[0]!=second){limit[0]=second;limit[1]=0;}return ++limit[1]<=12;}
    private static String commonRefusal(MinecraftServer s,ServerPlayerEntity player,ProceedingsState state){
        if(RuntimeHolds.reason(s)!=null)return "Resolve pending recovery before changing the election.";
        if(ChronicleRuntime.service(s).state().phase()!=SessionPhase.ACTIVE||SessionBuilding.enabled(s)||AuthoringRuntime.isAuthor(player))return "Resume active play outside Editing and Building Mode first.";
        if(state.pending()!=null||state.activeBallot()!=null)return "Close or void the current ballot first.";
        if(state.succession().stage()!=SuccessionState.Stage.CONTESTED)return "The election is not contested.";
        return "";
    }
    private static String withdrawalRefusal(MinecraftServer s,ServerPlayerEntity player,ProceedingsState state){
        String common=commonRefusal(s,player,state);if(!common.isBlank())return common;
        if(CouncilRuntime.isChancellor(s,player.getUuid()))return "The Chancellor cannot withdraw a House candidate.";
        var own=CouncilRuntime.roster(s).assignments().get(player.getUuid());
        if(own==null||!own.councilMember()||!PlayRuntime.eligibleIds(s).contains(player.getUuid())||!state.succession().remaining().contains(own.houseId()))return "Your House is not a participating remaining candidate.";
        var latest=state.succession().rounds().values().stream().max(Comparator.comparingInt(SuccessionState.Round::order)).orElseThrow();
        if(latest.status()==io.github.chronicle.core.ballots.BallotStatus.VOID&&latest.meeting()==state.meeting())return "A same-meeting replacement must retain the voided ballot's slate.";
        return "";
    }
    private static String endRefusal(MinecraftServer s,ServerPlayerEntity player,ProceedingsState state){
        if(!CouncilRuntime.isChancellor(s,player.getUuid()))return "Only the assigned Chancellor may end the election.";
        return commonRefusal(s,player,state);
    }
    private record HistoryLine(int order,String text) { }
    private static List<String> history(MinecraftServer s,SuccessionState election){
        var lines=new ArrayList<HistoryLine>();
        election.rounds().values().forEach(round->{String outcome;
            if(round.status()==io.github.chronicle.core.ballots.BallotStatus.OPEN)outcome="Regency ballot open.";
            else if(round.status()==io.github.chronicle.core.ballots.BallotStatus.VOID)outcome="Regency ballot voided.";
            else outcome=switch(round.result().decision()){
                case CROWNED->ProceedingsRuntime.electionLabel(s,round.result().house())+" was elected ruler.";
                case ELIMINATED->ProceedingsRuntime.electionLabel(s,round.result().house())+" was eliminated; their House retains its vote.";
                case VACANT->"The throne was left vacant.";
            };
            lines.add(new HistoryLine(round.order(),"Meeting "+round.meeting()+": "+outcome));});
        election.withdrawals().forEach(w->lines.add(new HistoryLine(w.order(),"Meeting "+w.meeting()+": "+ProceedingsRuntime.electionLabel(s,w.house())+(w.voluntary()?" withdrew: ":" left the candidate slate: ")+w.reason())));
        if(election.end()!=null)lines.add(new HistoryLine(Integer.MAX_VALUE,election.end().legacy()?"Election ended unresolved. This legacy record has no recorded reason.":"Meeting "+election.end().meeting()+": Chancellor ended the election unresolved: "+election.end().reason()));
        return lines.stream().sorted(Comparator.comparingInt(HistoryLine::order).reversed()).map(HistoryLine::text).toList();
    }
    private static View project(MinecraftServer s,Session session,UUID request,boolean opening,String message,boolean saved)throws IOException{
        var authority=ProceedingsRuntime.electionAuthority(s);
        if(session.invalid||authority!=session.authority){session.invalid=true;return unavailable(s,session,request,opening,"Council records reloaded. Close and reopen Election.");}
        var state=authority.state();session.seenRevision=state.revision();var election=state.succession();boolean chancellor=CouncilRuntime.isChancellor(s,session.player.getUuid());
        String own="";var assignment=CouncilRuntime.rawRoster(s).assignments().get(session.player.getUuid());
        if(!chancellor&&assignment!=null&&election.remaining().contains(assignment.houseId()))own=ProceedingsRuntime.electionLabel(s,assignment.houseId());
        var candidates=election.stage()==SuccessionState.Stage.CROWNED?List.of(ProceedingsRuntime.electionLabel(s,election.crowned())):election.remaining().stream().sorted().map(h->ProceedingsRuntime.electionLabel(s,h)).toList();
        var all=history(s,election);int pages=Math.max(1,(all.size()+15)/16);session.page=Math.clamp(session.page,0,pages-1);
        String warning="";if(chancellor&&election.stage()==SuccessionState.Stage.CONTESTED)try{warning=ElectionPlanning.warning(election,state.meeting(),state.phase(),ComplicationsRuntime.declaredMeetings(s));}catch(IOException|RuntimeException unavailable){warning="The declared meeting plan is unavailable.";}
        String withdrawal=withdrawalRefusal(s,session.player,state),end=endRefusal(s,session.player,state);
        String stage=switch(election.stage()){case NOT_STARTED->"Not started";case CONTESTED->"Contested";case CROWNED->"Ruler elected";case VACANT->"Throne vacant";case UNRESOLVED->"Ended unresolved";};
        return new View(request,opening,session.token,state.revision(),stage,candidates,all.stream().skip(session.page*16L).limit(16).toList(),session.page,pages,own,chancellor,withdrawal.isBlank(),withdrawal,chancellor&&end.isBlank(),end,warning,message,saved);
    }
    private static View unavailable(MinecraftServer s,Session session,UUID request,boolean opening,String message){return new View(request,opening,session.token,session.seenRevision,"Unavailable",List.of(),List.of(),0,1,"",CouncilRuntime.isChancellor(s,session.player.getUuid()),false,"Recover Council records first.",false,"Recover Council records first.","",message,false);}
    private static View normalized(View v){return new View(ElectionPayloads.NONE,false,v.token(),v.revision(),v.stage(),v.candidates(),v.history(),v.page(),v.pages(),v.ownCandidate(),v.chancellor(),v.canWithdraw(),v.withdrawRefusal(),v.canEnd(),v.endRefusal(),v.warning(),"",false);}
    private static void send(MinecraftServer s,Session session,UUID request,boolean opening,String message,boolean saved)throws IOException{
        if(!ServerPlayNetworking.canSend(session.player,View.ID))return;var next=project(s,session,request,opening,message,saved);var comparable=normalized(next);
        if(request.equals(ElectionPayloads.NONE)&&!opening&&comparable.equals(session.sent))return;
        ServerPlayNetworking.send(session.player,next);session.sent=comparable;
    }
    private static void safeSend(MinecraftServer s,Session session,UUID request,String message){try{send(s,session,request,false,message,false);}catch(IOException|RuntimeException failure){if(ServerPlayNetworking.canSend(session.player,View.ID))ServerPlayNetworking.send(session.player,unavailable(s,session,request,false,message));}}
    private static void refresh(MinecraftServer s){var sessions=SESSIONS.get(s);if(sessions!=null)for(var session:List.copyOf(sessions.values()))safeSend(s,session,ElectionPayloads.NONE,"");}
    private static void tick(MinecraftServer s){if(s.getTicks()%10==0&&CouncilRuntime.active())refresh(s);}
}
