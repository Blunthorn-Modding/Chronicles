package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.ballots.*;
import io.github.chronicle.lastcouncil.rebellion.RebellionService;
import java.io.IOException;
import java.util.*;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;

/** Reinstatement commits within the existing saved CLOSE, never from an operator override. */
public final class ReinstatementMotionEffects implements ProceedingsService.MotionEffects {
    private final RebellionService service;
    public ReinstatementMotionEffects(RebellionService service){this.service=Objects.requireNonNull(service);}
    public void verifyUnchanged()throws IOException{service.verify();}
    public void validate(Motion m)throws IOException{if(m.reinstatement()!=null){verifyUnchanged();service.validateTargets(m.reinstatement().targets());}}
    public void apply(Motion m,Ballot b)throws IOException{
        if(m.reinstatement()==null)return;verifyUnchanged();validateBallot(m,b);
        if(passed(b))service.apply(effect(m,b));
    }
    public void validateHistory(ProceedingsState state,BallotState ballots)throws IOException{
        verifyUnchanged();Map<UUID,RebellionService.Effect> expected=new HashMap<>();
        for(var m:state.motions().values())if(m.reinstatement()!=null)for(var id:m.ballots()){
            var b=ballots.ballots().get(id);if(b!=null&&b.status()==BallotStatus.CLOSED){validateBallot(m,b);if(passed(b))expected.put(id,effect(m,b));}
        }
        Set<UUID> actual=new HashSet<>();for(var e:service.effects()){
            if(!e.equals(expected.get(e.ballot())))throw new IOException("Reinstatement receipt differs from its saved ballot; preserve the journals.");
            actual.add(e.ballot());
        }
        for(var id:expected.keySet())if(!actual.contains(id)&&!(state.pending()!=null&&state.pending().operation()==Operation.CLOSE&&state.pending().ballot().equals(id)))
            throw new IOException("A recorded reinstatement is missing; restore its journal before recovery.");
    }
    private static RebellionService.Effect effect(Motion m,Ballot b){return new RebellionService.Effect(b.definition().id(),m.id(),m.reinstatement().targets(),m.title());}
    private static boolean passed(Ballot b){return b.status()==BallotStatus.CLOSED&&b.votes().values().stream().filter("yes"::equals).count()>=b.definition().threshold();}
    private static void validateBallot(Motion m,Ballot b)throws IOException{
        var d=b.definition();if(b.status()!=BallotStatus.CLOSED||!d.purpose().equals(m.id().toString())||!d.title().equals(m.title())||d.visibility()!=m.visibility()
                ||d.threshold()!=BallotDefinition.twoThirds(d.electorate().size())
                ||!d.options().equals(List.of(new BallotOption("yes","Yes",true),new BallotOption("no","No",false),new BallotOption("abstain","Abstain",false))))
            throw new IOException("Reinstatement has no matching closed two-thirds ballot.");
    }
    public static ProceedingsService.MotionEffects combine(ProceedingsService.MotionEffects first,ProceedingsService.MotionEffects second){
        return new ProceedingsService.MotionEffects(){
            public void verifyUnchanged()throws IOException{first.verifyUnchanged();second.verifyUnchanged();}
            public void validate(Motion m)throws IOException{first.validate(m);second.validate(m);}
            public void apply(Motion m,Ballot b)throws IOException{first.apply(m,b);second.apply(m,b);}
            public void validateHistory(ProceedingsState s,BallotState b)throws IOException{first.validateHistory(s,b);second.validateHistory(s,b);}
        };
    }
}
