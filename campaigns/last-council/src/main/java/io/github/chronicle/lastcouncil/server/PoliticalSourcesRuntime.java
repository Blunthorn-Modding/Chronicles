package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.progression.SourceLedger;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.progression.*;
import io.github.chronicle.lastcouncil.treaties.TreatyService;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.util.*;

/** Public political occurrences use Core receipts, never a second action engine. */
public final class PoliticalSourcesRuntime {
    private static final Set<MinecraftServer> STARTED=Collections.newSetFromMap(new IdentityHashMap<>());
    private PoliticalSourcesRuntime() { }
    public static void register() {
        if(CouncilRuntime.active()) {
            provider("vote_completed","Council vote completed","A completed Council ballot and its public outcome; never individual secret choices.",
                    List.of(),"passed","rejected");
            provider("succession_result","Succession result","A recorded public Regency result.",List.of(choice("election","The election")),"crowned","eliminated","vacant","withdrawn","unresolved");
            provider("treaty_changed","Public treaty activity","Only public historical treaty actions. Private agreements never emit this source.",List.of(choice("*","Any public treaty")),"signed","sanctified","broken");
            provider("rebellion_changed","Rebellion or reinstatement","A completed public change for the selected House.",null,"declared","joined","reinstated");
            for(String prefix:List.of("council-outcome:","council-treaty:","council-rebellion:","council-election:"))
                EventSources.registerAuthority(prefix,PoliticalSourcesRuntime::resolve,true);
        }
        // Run after parent startup/recovery: upgrading must not backfill old political history.
        ServerLifecycleEvents.SERVER_STARTED.register(s->{if(CouncilRuntime.active())STARTED.add(s);});
        ServerLifecycleEvents.SERVER_STOPPED.register(STARTED::remove);
    }
    private static EventSources.Choice choice(String id,String title){return new EventSources.Choice(id,title,"Recorded public activity");}
    private static void provider(String suffix,String title,String description,List<EventSources.Choice> targets,String... activities){
        EventSources.registerProvider("last_council:"+suffix,title,description,
                s->{if(suffix.equals("vote_completed"))return ProceedingsRuntime.motionSourceChoices(s);if(targets!=null)return targets;var choices=new ArrayList<EventSources.Choice>();choices.add(choice("*","Any House"));CouncilRuntime.houses(s).values().forEach(h->choices.add(choice(h.id(),h.title())));return List.copyOf(choices);},
                Arrays.stream(activities).map(a->choice(a,switch(a){case "passed"->"Passed";case "rejected"->"Did not pass";case "crowned"->"Ruler elected";case "eliminated"->"Candidate eliminated";case "vacant"->"Throne left vacant";case "signed"->"Signature recorded";case "sanctified"->"Agreement sanctified";case "broken"->"Agreement broken";case "withdrawn"->"Candidate withdrew voluntarily";case "unresolved"->"Election ended unresolved";case "declared"->"Rebellion declared";case "joined"->"House joined rebellion";default->"House reinstated";})).toList(),false);
    }
    public static void prepare(MinecraftServer s,List<PoliticalOccurrence> occurrences) {
        if(!STARTED.contains(s)||RuntimeHolds.reason(s)!=null||!EventSources.activeOperation(s))return;
        for(var occurrence:occurrences) {
            EventSource source=new EventSource(occurrence.type(),occurrence.target(),occurrence.activity());
            String id="source:"+UUID.randomUUID();
            // Normal child application reuses its planned preparation; held/startup recovery creates none.
            var old=EventSources.pending(s).stream().filter(r->same(r,occurrence)).findFirst();
            if(old.isPresent())continue;
            EventSources.prepare(s,id,source,null,true,occurrence.authority());
        }
    }
    public static void planTreaty(MinecraftServer s,TreatyService.Mutation mutation)throws IOException{
        prepare(s,TreatyRuntime.authority(s).politicalPlan(mutation));
    }
    private static boolean same(SourceLedger.Receipt r,PoliticalOccurrence e){return r.actor()==null&&r.authority().equals(e.authority())&&r.type().equals(e.type())&&r.target().equals(e.target())&&r.activity().equals(e.activity());}
    private static boolean resolve(MinecraftServer s,SourceLedger.Receipt receipt) {
        if(!STARTED.contains(s))return false;
        try {
            List<PoliticalOccurrence> evidence;
            if(receipt.authority().startsWith("council-election:")){
                if(!ProceedingsRuntime.effectsReady(s))return false;
                evidence=ProceedingsRuntime.electionAuthority(s).politicalElectionEvidence(receipt.authority());
            }else if(receipt.authority().startsWith("council-outcome:")) {
                if(!ProceedingsRuntime.effectsReady(s))return false;
                evidence=ProceedingsRuntime.politicalEvidence(s,receipt.authority());
            }else if(receipt.authority().startsWith("council-treaty:")) {
                if(!ConflictRuntime.publicationReady(s))return false;
                evidence=TreatyRuntime.authority(s).politicalEvidence(receipt.authority());
            }else {
                if(!ProceedingsRuntime.effectsReady(s)||!RebellionRuntime.sourcesReady(s))return false;
                evidence=RebellionRuntime.politicalEvidence(s,receipt.authority());
            }
            if(evidence.isEmpty())throw new IOException("No committed political outcome proves this preparation. Review the exact uncertain source.");
            if(evidence.stream().noneMatch(e->same(receipt,e)))throw new IOException("Political source differs from committed evidence.");
            return true;
        }catch(IOException failure){throw new IllegalStateException("Political source authority needs exact recovery.",failure);}
    }
}
