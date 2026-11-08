package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.ballots.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;

/** Caller owns operator/session/presence checks. Durable intents bridge this journal and Core ballots. */
public final class ProceedingsService {
    private final ProceedingsRepository repository;
    private final BallotService ballots;
    private final Clock clock;
    private ProceedingsState state;
    private final java.util.function.IntUnaryOperator draw;
    private final ProgressionGate progression;
    public interface ProgressionGate {
        void beforeRegency(int meeting) throws IOException;
        void beforeMeetingClose(int meeting) throws IOException;
    }
    private static final ProgressionGate NO_COMPLICATIONS=new ProgressionGate() {
        public void beforeRegency(int meeting) { }
        public void beforeMeetingClose(int meeting) { }
    };
    public ProceedingsService(ProceedingsRepository repository, BallotService ballots, Clock clock) throws IOException {
        this(repository,ballots,clock,new java.security.SecureRandom()::nextInt,NO_COMPLICATIONS);
    }
    public ProceedingsService(ProceedingsRepository repository,BallotService ballots,Clock clock,
            java.util.function.IntUnaryOperator draw,ProgressionGate progression) throws IOException {
        this.draw=Objects.requireNonNull(draw);this.progression=Objects.requireNonNull(progression);
        this.repository=Objects.requireNonNull(repository); this.ballots=Objects.requireNonNull(ballots);
        this.clock=Objects.requireNonNull(clock); state=Objects.requireNonNull(repository.load());
        validateLinks();
    }
    public ProceedingsState state() { return state; }
    public BallotView view(UUID ballot, UUID viewer) { return ballots.view(ballot,viewer); }
    public int submissionCount(UUID ballot) { return ballots.submissionCount(ballot); }
    public void revision(long expected) {
        if(expected!=state.revision())throw new IllegalStateException("Council business changed. Review the current screen.");
    }
    private void idle() {
        if(state.pending()!=null)throw new IllegalStateException("Finish the pending Council operation first.");
    }
    public void region(String id) throws IOException { idle(); noBallot(); publish(d -> d.region=id); }
    public void lectern(String id, boolean enable) throws IOException {
        idle(); noBallot(); publish(d -> { if(enable)d.lecterns.add(id);else d.lecterns.remove(id); });
    }
    public boolean call(UUID id, UUID actor) throws IOException {
        idle(); Call old=state.calls().get(id);
        if(old!=null) {
            repository.verifyUnchanged();
            if(!old.actor().equals(actor))throw new IllegalStateException("Meeting call identity changed.");
            return false;
        }
        publish(d -> { d.calls.put(id,new Call(id,actor,clock.instant().toString(),d.meeting)); if(d.phase==Phase.BETWEEN)d.phase=Phase.GATHERING; });
        return true;
    }
    public void openMeeting() throws IOException {
        idle(); noBallot();
        if(state.phase()!=Phase.BETWEEN&&state.phase()!=Phase.GATHERING)throw new IllegalStateException("Proceedings already opened.");
        publish(d -> { d.meeting=Math.incrementExact(d.meeting);d.phase=Phase.DISCUSSION; });
    }
    public void recess() throws IOException {
        idle();noBallot();if(state.phase()!=Phase.DISCUSSION)throw new IllegalStateException("Proceedings are not in discussion.");
        publish(d -> d.phase=Phase.RECESS);
    }
    public void resumeMeeting() throws IOException {
        idle();noBallot();if(state.phase()!=Phase.RECESS)throw new IllegalStateException("Proceedings are not in recess.");
        publish(d -> d.phase=Phase.DISCUSSION);
    }
    public void closeMeeting() throws IOException {
        idle();noBallot();if(state.phase()!=Phase.DISCUSSION&&state.phase()!=Phase.RECESS)throw new IllegalStateException("No open proceedings.");
        progression.beforeMeetingClose(state.meeting());
        publish(d -> d.phase=Phase.BETWEEN);
    }
    public void propose(Motion motion) throws IOException {
        idle(); if(motion.status()!=MotionStatus.SUBMITTED||!motion.ballots().isEmpty()||!motion.sponsors().isEmpty()||!motion.notes().isEmpty())
            throw new IllegalArgumentException("Submit a new proposal without prior decisions.");
        Motion saved=motion.hostPrepared()?motion.with(MotionStatus.AGENDA,motion.sponsors(),motion.ballots(),motion.notes()):motion;
        Motion old=state.motions().get(motion.id());
        if(old!=null) { repository.verifyUnchanged();if(old.equals(saved))return;throw new IllegalStateException("Proposal ID already exists."); }
        publish(d -> d.motions.put(saved.id(),saved));
    }
    public void sponsor(UUID id, String house) throws IOException {
        idle(); Motion m=motion(id);
        if(m.status()!=MotionStatus.SUBMITTED||m.proposerHouse().equals(house))throw new IllegalStateException("Only another house may sponsor an awaiting proposal.");
        if(m.sponsors().contains(house)){repository.verifyUnchanged();return;}
        Set<String> sponsors=new HashSet<>(m.sponsors());sponsors.add(house);
        publish(d -> d.motions.put(id,m.with(m.status(),sponsors,m.ballots(),m.notes())));
    }
    public boolean deferredThisMeeting(UUID id){return Objects.equals(state.deferredAt().get(id),state.meeting());}
    public List<UUID> pendingMotions() {
        Set<UUID> regency=new HashSet<>();state.succession().rounds().keySet().forEach(id->regency.add(state.attempts().get(id).motion()));
        return state.motions().values().stream().filter(m->!regency.contains(m.id())&&!deferredThisMeeting(m.id())
                &&(m.status()==MotionStatus.SUBMITTED||m.status()==MotionStatus.AGENDA||m.status()==MotionStatus.VOTING)).map(Motion::id).sorted().toList();
    }
    public void admit(UUID id) throws IOException {
        idle();Motion m=motion(id);
        if(deferredThisMeeting(id))throw new IllegalStateException("This motion is deferred until a later meeting.");
        if(m.status()!=MotionStatus.SUBMITTED||!m.ready(1))throw new IllegalStateException("This proposal needs another house's sponsorship.");
        publish(d -> d.motions.put(id,m.with(MotionStatus.AGENDA,m.sponsors(),m.ballots(),m.notes())));
    }
    public void defer(UUID id,String reason) throws IOException {
        idle();Motion m=motion(id);validText(reason,400,true);
        if(state.phase()!=Phase.DISCUSSION&&state.phase()!=Phase.RECESS)throw new IllegalStateException("Defer a motion during an open meeting.");
        if(deferredThisMeeting(id)){repository.verifyUnchanged();return;}
        if(m.status()!=MotionStatus.SUBMITTED&&m.status()!=MotionStatus.AGENDA)throw new IllegalStateException("This motion cannot be deferred.");
        List<String> notes=new ArrayList<>(m.notes());notes.add(clock.instant()+" - Deferred: "+reason);
        publish(d -> {d.motions.put(id,m.with(MotionStatus.SUBMITTED,m.sponsors(),m.ballots(),notes));d.deferredAt.put(id,state.meeting());});
    }
    public void withdraw(UUID id,String house) throws IOException {
        idle();Motion m=motion(id);
        if(!m.proposerHouse().equals(house)||!m.ballots().isEmpty()||m.status()==MotionStatus.WITHDRAWN)
            throw new IllegalStateException("Only your own proposal may be withdrawn before its first vote.");
        publish(d -> d.motions.put(id,m.with(MotionStatus.WITHDRAWN,m.sponsors(),m.ballots(),m.notes())));
    }
    public Ballot open(UUID motion,UUID actor,AuthoringSite.Region region,Map<String,BallotElector> electorate) throws IOException {
        idle();noBallot();Motion m=motion(motion);
        if(state.succession().rounds().keySet().stream().anyMatch(id->state.attempts().get(id).motion().equals(motion)))
            throw new IllegalStateException("Use Request regency vote for succession, including a replacement.");
        if(state.phase()!=Phase.DISCUSSION||m.status()!=MotionStatus.AGENDA||deferredThisMeeting(motion))throw new IllegalStateException("Bring an agenda motion to open proceedings first.");
        if(!region.id().equals(state.votingRegion()))throw new IllegalStateException("Choose the configured voting Region.");
        int threshold=m.threshold()==Threshold.MAJORITY?BallotDefinition.majority(electorate.size()):BallotDefinition.twoThirds(electorate.size());
        BallotDefinition definition=new BallotDefinition(UUID.randomUUID(),m.id().toString(),m.title(),
                List.of(new BallotOption("yes","Yes",true),new BallotOption("no","No",false),new BallotOption("abstain","Abstain",false)),electorate,threshold,m.visibility());
        return begin(m,actor,region,definition,state.succession());
    }
    /** The first opening locks the slate; later openings cannot add candidates. */
    public Ballot openRegency(UUID actor,AuthoringSite.Region region,Map<String,BallotElector> electorate,
            Map<String,String> configuredCandidates) throws IOException {
        idle();noBallot();
        if(state.phase()!=Phase.DISCUSSION||!region.id().equals(state.votingRegion()))
            throw new IllegalStateException("Open discussion in the configured voting Region first.");
        if(!pendingMotions().isEmpty())throw new IllegalStateException("Vote on or defer every pending motion before requesting regency.");
        Set<String> slate=state.succession().started()?state.succession().remaining():Set.copyOf(configuredCandidates.keySet());
        if(state.succession().started()&&!configuredCandidates.keySet().containsAll(slate)) {
            var missing=new TreeSet<>(slate);missing.removeAll(configuredCandidates.keySet());
            throw new IllegalStateException("All remaining candidates must be present: "+String.join(", ",missing));
        }
        if(!state.succession().started()&&slate.size()<2)throw new IllegalStateException("Choose at least two initial crown candidates.");
        if(slate.isEmpty())throw new IllegalStateException("No crown candidates remain.");
        if(slate.size()>15)throw new IllegalStateException("A regency ballot supports at most fifteen candidates.");
        // Validate presence before any complication gate or durable operation.
        progression.beforeRegency(state.meeting());
        UUID id=UUID.randomUUID(),motionId=UUID.randomUUID();
        SuccessionState election=state.succession().open(id,state.meeting(),slate);
        List<BallotOption> options=new ArrayList<>();
        if(slate.size()==1) {
            options.add(new BallotOption("yes","Yes",true));options.add(new BallotOption("no","No",false));
        } else for(String house:slate.stream().sorted().toList())
            options.add(new BallotOption(house,Objects.requireNonNullElse(configuredCandidates.get(house),house),true));
        options.add(new BallotOption("abstain","Abstain",false));
        String text=slate.size()==1?"Confirm "+Objects.requireNonNullElse(configuredCandidates.get(slate.iterator().next()),slate.iterator().next())+" as ruler."
                :"Choose a ruler for the realm.";
        Motion motion=new Motion(motionId,"The regency",text,actor,"",BallotVisibility.SECRET,Threshold.MAJORITY,true,
                Set.of(),MotionStatus.AGENDA,List.of(),List.of());
        BallotDefinition definition=new BallotDefinition(id,motionId.toString(),motion.title(),options,electorate,
                BallotDefinition.majority(electorate.size()),BallotVisibility.SECRET);
        return begin(motion,actor,region,definition,election);
    }
    private Ballot begin(Motion motion,UUID actor,AuthoringSite.Region region,BallotDefinition definition,SuccessionState election) throws IOException {
        Attempt attempt=new Attempt(motion.id(),state.meeting(),region,definition,actor);
        List<UUID> attempts=new ArrayList<>(motion.ballots());attempts.add(definition.id());
        publish(d -> {
            d.attempts.put(definition.id(),attempt);d.active=definition.id();d.succession=election;
            d.motions.put(motion.id(),motion.with(MotionStatus.VOTING,motion.sponsors(),attempts,motion.notes()));
            d.pending=new Pending(Operation.OPEN,definition.id(),actor,"");
        });
        recover();return ballots.state().ballots().get(definition.id());
    }
    public void withdrawCandidate(String house,UUID actor,String reason) throws IOException {
        idle();noBallot();publish(d->d.succession=d.succession.withdraw(house,actor,reason,state.meeting()));
    }
    public void endSuccessionUnresolved() throws IOException {
        idle();noBallot();publish(d->d.succession=d.succession.endUnresolved());
    }
    public Ballot close(UUID id, UUID actor) throws IOException {
        idle();requireActive(id);
        verifyUnchanged();
        SuccessionState.Round round=state.succession().rounds().get(id);
        SuccessionRules.Result result=round==null?null:regencyResult(id,draw);
        // Persist the draw before Core close, so a partial close cannot reroll it.
        publish(d -> d.pending=new Pending(Operation.CLOSE,id,actor,"",result));
        recover();return ballots.state().ballots().get(id);
    }
    public Ballot voidBallot(UUID id, UUID actor,String reason) throws IOException {
        idle();requireActive(id);validText(reason,512,true);
        publish(d -> d.pending=new Pending(Operation.VOID,id,actor,reason));
        recover();return ballots.state().ballots().get(id);
    }
    public Ballot cast(UUID id,UUID actor,String choice) throws IOException {
        idle();requireActive(id);return ballots.cast(id,actor,choice);
    }
    /** Resume the same saved operation; never capture a new electorate or choose a replacement ID. */
    public void verifyUnchanged() throws IOException { repository.verifyUnchanged();ballots.verifyUnchanged(); }
    public void recover() throws IOException {
        verifyUnchanged();
        Pending p=state.pending(); if(p==null){validateLinks();return;}
        Attempt a=state.attempts().get(p.ballot());
        if(p.result()!=null&&!p.result().equals(regencyResult(p.ballot(),size-> {
            var tied=p.result().tiedLast();int index=tied.indexOf(p.result().house());
            if(tied.size()!=size||index<0)throw new IllegalStateException("Saved elimination draw changed.");return index;
        })))throw new IllegalStateException("Saved regency outcome differs from its ballot.");
        switch(p.operation()) {
            case OPEN -> ballots.open(a.definition(),p.actor());
            case CLOSE -> ballots.close(p.ballot(),p.actor());
            case VOID -> ballots.voidBallot(p.ballot(),p.actor(),p.reason());
        }
        publish(d -> {
            if(p.operation()!=Operation.OPEN) {
                Motion m=d.motions.get(a.motion());
                d.motions.put(m.id(),m.with(p.operation()==Operation.CLOSE?MotionStatus.RECORDED:MotionStatus.AGENDA,m.sponsors(),m.ballots(),m.notes()));
                if(d.succession.rounds().containsKey(p.ballot()))d.succession=d.succession.finish(p.ballot(),
                        p.operation()==Operation.CLOSE?BallotStatus.CLOSED:BallotStatus.VOID,p.result());
                d.active=null;
            }
            d.pending=null;
        });
        validateLinks();
    }
    private SuccessionRules.Result regencyResult(UUID id,java.util.function.IntUnaryOperator choice) {
        Ballot ballot=ballots.state().ballots().get(id);Map<String,Integer> totals=new HashMap<>();
        ballot.definition().options().forEach(o->totals.put(o.id(),0));
        ballot.votes().values().forEach(option->totals.merge(option,1,Integer::sum));
        return SuccessionRules.decide(state.succession().rounds().get(id).slate(),totals,ballot.definition().electorate().size(),choice);
    }
    private void validateLinks() {
        if(!state.attempts().keySet().containsAll(ballots.state().ballots().keySet()))throw new IllegalStateException("Unrecognized Council ballot in ledger.");
        for(var e:state.attempts().entrySet()) {
            Ballot b=ballots.state().ballots().get(e.getKey());
            boolean pending=state.pending()!=null&&state.pending().ballot().equals(e.getKey());
            if(b==null) { if(pending&&state.pending().operation()==Operation.OPEN)continue;throw new IllegalStateException("Council ballot is missing."); }
            if(!b.definition().equals(e.getValue().definition())||!b.openedBy().equals(e.getValue().openedBy()))throw new IllegalStateException("Council ballot snapshot changed.");
            boolean active=e.getKey().equals(state.activeBallot());
            if(!pending && active!=(b.status()==BallotStatus.OPEN))throw new IllegalStateException("Council ballot terminal state differs from its journal.");
            SuccessionState.Round round=state.succession().rounds().get(e.getKey());
            if(round!=null) {
                if(!pending&&round.status()!=b.status())throw new IllegalStateException("Regency round terminal state differs.");
                if(round.result()!=null&&!round.result().equals(regencyResult(e.getKey(),size-> {
                    var tied=round.result().tiedLast();int index=tied.indexOf(round.result().house());
                    if(tied.size()!=size||index<0)throw new IllegalStateException("Recorded elimination draw changed.");return index;
                })))throw new IllegalStateException("Recorded regency result differs from its ballot.");
            }
        }
    }
    private Motion motion(UUID id) { Motion m=state.motions().get(id);if(m==null)throw new IllegalArgumentException("Choose an existing motion.");return m; }
    private void requireActive(UUID id) { if(!Objects.equals(id,state.activeBallot()))throw new IllegalStateException("This is not the current ballot."); }
    private void noBallot() { if(state.activeBallot()!=null)throw new IllegalStateException("Close or void the current ballot first."); }
    private void publish(Consumer<Draft> change) throws IOException {
        repository.verifyUnchanged(); Draft draft=new Draft(state);change.accept(draft);
        ProceedingsState next=draft.build(Math.incrementExact(state.revision()));repository.save(next);state=next;
    }
    private static final class Draft {
        int meeting;Phase phase;String region;Set<String> lecterns;
        Map<UUID,Motion> motions;Map<UUID,Attempt> attempts;UUID active;Pending pending;Map<UUID,Call> calls;SuccessionState succession;Map<UUID,Integer> deferredAt;
        Draft(ProceedingsState s) { meeting=s.meeting();phase=s.phase();region=s.votingRegion();lecterns=new HashSet<>(s.lecterns());motions=new HashMap<>(s.motions());attempts=new HashMap<>(s.attempts());active=s.activeBallot();pending=s.pending();calls=new HashMap<>(s.calls());succession=s.succession();deferredAt=new HashMap<>(s.deferredAt()); }
        ProceedingsState build(long revision) { return new ProceedingsState(revision,meeting,phase,region,lecterns,motions,attempts,active,pending,calls,succession,deferredAt); }
    }
}
