package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.server.GameSetupTools;
import io.github.chronicle.lastcouncil.content.CouncilContent;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Projects reusable Council definitions only; every request rechecks operator authority. */
public final class CouncilCatalogueRuntime {
    private static final int PAGE=6, PART=2048;
    private CouncilCatalogueRuntime() { }
    public static void register(){
        GameSetupTools.register(new GameSetupTools.Tool("last_council:catalogue","Source catalogue","Inspect reusable Last Council source definitions.",
                List.of(new GameSetupTools.Verb("open","Open source catalogue",GameSetupTools.Guard.READ_ONLY,false)),
                source->new GameSetupTools.Status(0,List.of(new Fact("Content","House information, secret and goal templates, and knowledge rules.")),Map.of()),
                (source,verb)->{var player=source.getPlayer();if(player==null)throw new IllegalStateException("Open the catalogue as a player.");send(player,new Request(new UUID(0,0),Category.HOUSES,"",0,0,0),true);return "";}));
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(request,context)->context.server().execute(()->send(context.player(),request,false)));
    }
    private static void send(ServerPlayerEntity player,Request request,boolean opening){
        var server=player.getEntityWorld().getServer();
        if(!server.isOnThread()||server.getPlayerManager().getPlayer(player.getUuid())!=player||!player.getCommandSource().hasPermissionLevel(2)||!ServerPlayNetworking.canSend(player,View.ID))return;
        if(request.query()==null||request.query().length()>128||request.page()<0||request.selected()<0||request.part()<0)return;
        try {ServerPlayNetworking.send(player,project(CouncilRuntime.content(server),request,opening));} catch(IllegalStateException unavailable) { if(opening)throw unavailable; }
    }
    record Entry(String label,String detail) { }
    static View project(CouncilContent content,Request request,boolean opening){
        List<Entry> entries=new ArrayList<>();
        switch(request.category()){
            case HOUSES -> content.houses().values().stream().sorted(Comparator.comparing(h->h.id())).forEach(h->entries.add(new Entry(h.title(),"ID: "+h.id()+"\nTitle: "+h.title()+"\nReputation: "+h.reputation()+"\nDescription:\n"+h.description()+"\nInterests:\n"+h.interests()+"\nDefault candidate: "+(h.defaultCandidate()?"Yes":"No"))));
            case SECRETS -> content.secrets().stream().sorted(Comparator.comparing(s->s.id())).forEach(s->{StringBuilder detail=new StringBuilder("ID: ").append(s.id()).append("\nWeight: ").append(s.weight()).append("\nHouse scope: ").append(String.join(", ",new TreeSet<>(s.applicableHouseIds()))).append("\nText:\n").append(s.text()).append("\nFact defaults:");new TreeMap<>(s.facts()).forEach((k,v)->detail.append("\n").append(k).append(": ").append(v));if(s.facts().isEmpty())detail.append(" None");entries.add(new Entry(s.id(),detail.toString()));});
            case GOALS -> content.goals().stream().sorted(Comparator.comparing(g->g.id())).forEach(g->entries.add(new Entry(g.id(),"ID: "+g.id()+"\nWeight: "+g.weight()+"\nHouse scope: "+String.join(", ",new TreeSet<>(g.applicableHouseIds()))+"\nText:\n"+g.text())));
            case KNOWLEDGE -> entries.add(new Entry("Knowledge rules","Chance: "+content.knowledgeChancePercent()+"% per secret and other representative.\nEach representative can learn a secret assigned to another representative; self-knowledge is excluded. There is no separate authored knowledge pool or guaranteed coverage.\nSecrets per House: "+content.secretsPerHouse()+"\nGoals per House: "+content.goalsPerHouse()));
        }
        String query=request.query().toLowerCase(Locale.ROOT);entries.removeIf(e->!e.label().toLowerCase(Locale.ROOT).contains(query)&&!e.detail().toLowerCase(Locale.ROOT).contains(query));
        int pages=Math.max(1,(entries.size()+PAGE-1)/PAGE),page=Math.min(request.page(),pages-1),start=page*PAGE,end=Math.min(entries.size(),start+PAGE);List<Entry> visible=entries.subList(start,end);
        int selected=visible.isEmpty()?0:Math.min(request.selected(),visible.size()-1);String detail=visible.isEmpty()?"No matching source entries.":visible.get(selected).detail();int count=detail.codePointCount(0,detail.length()),parts=Math.max(1,(count+PART-1)/PART),part=Math.min(request.part(),parts-1);int from=detail.offsetByCodePoints(0,Math.min(count,part*PART)),to=detail.offsetByCodePoints(from,Math.min(PART,count-part*PART));
        return new View(request.request(),opening,request.category(),request.query(),page,pages,selected,visible.stream().map(e->shortLabel(e.label())).toList(),part,parts,detail.substring(from,to));
    }
    private static String shortLabel(String value){return value.length()<=256?value:value.substring(0,255)+"…";}
}
