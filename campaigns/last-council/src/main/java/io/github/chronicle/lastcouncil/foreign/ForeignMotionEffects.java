package io.github.chronicle.lastcouncil.foreign;

import io.github.chronicle.core.ballots.*;
import io.github.chronicle.lastcouncil.council.*;
import java.io.IOException;
import java.util.*;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;

/** Exact public effects inside the existing saved ballot CLOSE transaction. */
public final class ForeignMotionEffects implements ProceedingsService.MotionEffects {
    private final ForeignRealmService realms;
    private final ForeignAffairsService affairs;
    public ForeignMotionEffects(ForeignRealmService realms,ForeignAffairsService affairs){this.realms=Objects.requireNonNull(realms);this.affairs=Objects.requireNonNull(affairs);}
    public void verifyUnchanged() throws IOException {realms.verify();affairs.verify();}
    public void validate(Motion motion)throws IOException {
        if(motion.foreign()==null)return;
        verifyUnchanged();requireRealm(motion.foreign().realmId());
        var current=affairs.view(motion.foreign().realmId());
        if(current.revision()!=motion.foreign().revision()||current.atWar()==(motion.foreign().kind()==ForeignKind.DECLARE_WAR))
            throw new IllegalStateException("The realm's diplomatic state changed. Propose a new motion after reviewing its record.");
    }
    public void apply(Motion motion,Ballot ballot)throws IOException {
        if(motion.foreign()==null)return;
        verifyUnchanged();requireRealm(motion.foreign().realmId());validateBallot(motion,ballot);
        if(!passed(ballot))return;
        affairs.apply(effect(motion,ballot));
    }
    public void validateHistory(ProceedingsState state,BallotState ballots)throws IOException {
        verifyUnchanged();var expected=new HashMap<UUID,ForeignAffairsService.Effect>();
        for(var motion:state.motions().values()) {
            if(motion.foreign()==null)continue;
            requireRealm(motion.foreign().realmId());
            for(UUID id:motion.ballots()) {
                Ballot ballot=ballots.ballots().get(id);
                if(ballot!=null&&ballot.status()==BallotStatus.CLOSED){validateBallot(motion,ballot);if(passed(ballot))expected.put(id,effect(motion,ballot));}
            }
        }
        var actual=new HashMap<UUID,ForeignAffairsService.Effect>();
        for(var effect:affairs.effects()) {
            if(!effect.equals(expected.get(effect.ballot())))throw new IOException("Foreign outcome differs from its saved Council ballot; preserved.");
            actual.put(effect.ballot(),effect);
        }
        for(var entry:expected.entrySet())if(!actual.containsKey(entry.getKey())&&!(state.pending()!=null
                &&state.pending().operation()==Operation.CLOSE&&state.pending().ballot().equals(entry.getKey())))
            throw new IOException("A recorded foreign outcome is missing; restore its journal before recovery.");
    }
    private void requireRealm(String id)throws IOException{if(realms.get(id)==null)throw new IOException("A referenced foreign realm is missing; restore its catalogue.");}
    private static boolean passed(Ballot b){return b.status()==BallotStatus.CLOSED&&b.votes().values().stream().filter("yes"::equals).count()>=b.definition().threshold();}
    private static ForeignAffairsService.Effect effect(Motion m,Ballot b){var f=m.foreign();return new ForeignAffairsService.Effect(b.definition().id(),m.id(),f.realmId(),f.revision(),ForeignAffairsService.Kind.valueOf(f.kind().name()),m.title());}
    private static void validateBallot(Motion m,Ballot b)throws IOException {
        var d=b.definition();
        if(b.status()!=BallotStatus.CLOSED||!d.purpose().equals(m.id().toString())||!d.title().equals(m.title())
                ||d.visibility()!=m.visibility()||d.threshold()!=BallotDefinition.twoThirds(d.electorate().size())
                ||!d.options().equals(List.of(new BallotOption("yes","Yes",true),new BallotOption("no","No",false),new BallotOption("abstain","Abstain",false))))
            throw new IOException("Foreign outcome has no matching closed two-thirds ballot.");
    }
}
