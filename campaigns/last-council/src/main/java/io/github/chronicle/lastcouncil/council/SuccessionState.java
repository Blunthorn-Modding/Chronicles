package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.ballots.BallotStatus;
import java.util.*;

/** Scenario-owned succession history. No individual secret choices are stored here. */
public record SuccessionState(Stage stage, Set<String> initial, Set<String> remaining, String crowned,
        Map<UUID,Round> rounds, List<Withdrawal> withdrawals) {
    public enum Stage { NOT_STARTED, CONTESTED, CROWNED, VACANT, UNRESOLVED }
    public SuccessionState {
        Objects.requireNonNull(stage); Objects.requireNonNull(crowned);
        initial=Set.copyOf(initial); remaining=Set.copyOf(remaining); rounds=Map.copyOf(rounds); withdrawals=List.copyOf(withdrawals);
        if(initial.size()>15||rounds.size()>128||withdrawals.size()>15||!initial.containsAll(remaining))
            throw new IllegalArgumentException("Invalid succession history.");
        initial.forEach(SuccessionState::house);
        if(stage==Stage.NOT_STARTED) {
            if(!initial.isEmpty()||!remaining.isEmpty()||!crowned.isEmpty()||!rounds.isEmpty()||!withdrawals.isEmpty())
                throw new IllegalArgumentException("An unstarted election has history.");
        } else if(initial.size()<2||rounds.isEmpty())throw new IllegalArgumentException("The initial slate needs at least two candidates.");
        if(stage==Stage.CROWNED?!remaining.contains(crowned):!crowned.isEmpty())throw new IllegalArgumentException("Invalid crown.");
        if(stage==Stage.CONTESTED&&remaining.isEmpty())throw new IllegalArgumentException("No candidate remains.");
        if(stage!=Stage.NOT_STARTED)validateHistory(stage,initial,remaining,crowned,rounds,withdrawals);

    }
    public static SuccessionState empty(){return new SuccessionState(Stage.NOT_STARTED,Set.of(),Set.of(),"",Map.of(),List.of());}
    public boolean started(){return stage!=Stage.NOT_STARTED;}
    public int lastCompletedMeeting(){return rounds.values().stream().filter(r->r.status()==BallotStatus.CLOSED).mapToInt(Round::meeting).max().orElse(0);}
    public record Round(int order,int meeting,Set<String> slate,BallotStatus status,SuccessionRules.Result result) {
        public Round {
            Objects.requireNonNull(status);slate=Set.copyOf(slate);slate.forEach(SuccessionState::house);
            if(order<1||meeting<1||slate.isEmpty()||slate.size()>15||(status==BallotStatus.CLOSED)!=(result!=null))
                throw new IllegalArgumentException("Invalid succession round.");
            if(result!=null&&!result.house().isEmpty()&&!slate.contains(result.house()))throw new IllegalArgumentException("Result is outside slate.");
            if(result!=null&&!slate.containsAll(result.tiedLast()))throw new IllegalArgumentException("Draw is outside slate.");
        }
    }
    public record Withdrawal(int order,String house,UUID actor,String reason,int meeting) {
        public Withdrawal { SuccessionState.house(house);Objects.requireNonNull(actor);ProceedingsState.validText(reason,400,true);if(order<1||meeting<1)throw new IllegalArgumentException("Invalid withdrawal meeting."); }
    }
    public SuccessionState open(UUID id,int meeting,Set<String> candidates) {
        if(stage!=Stage.NOT_STARTED&&stage!=Stage.CONTESTED)throw new IllegalStateException("Succession has already ended.");
        if(meeting<=lastCompletedMeeting())throw new IllegalStateException("The next regency vote needs a later Council meeting.");
        if(rounds.values().stream().anyMatch(r->r.status()==BallotStatus.OPEN))throw new IllegalStateException("A regency ballot is already open.");
        Set<String> slate=started()?remaining:Set.copyOf(candidates);
        if(!started()&&slate.size()<2)throw new IllegalStateException("Choose at least two initial crown candidates.");
        if(slate.size()>15)throw new IllegalStateException("A regency ballot supports at most fifteen candidates.");
        Map<UUID,Round> next=new HashMap<>(rounds);next.put(id,new Round(rounds.size()+withdrawals.size()+1,meeting,slate,BallotStatus.OPEN,null));
        return new SuccessionState(Stage.CONTESTED,started()?initial:slate,slate,"",next,withdrawals);
    }
    public SuccessionState finish(UUID id,BallotStatus status,SuccessionRules.Result result) {
        Round round=rounds.get(id);if(round==null||round.status()!=BallotStatus.OPEN||status==BallotStatus.OPEN)
            throw new IllegalStateException("This regency round is not open.");
        Map<UUID,Round> next=new HashMap<>(rounds);next.put(id,new Round(round.order(),round.meeting(),round.slate(),status,result));
        Set<String> survivors=new HashSet<>(remaining);Stage outcome=stage;String monarch="";
        if(result!=null)switch(result.decision()) {
            case CROWNED -> {outcome=Stage.CROWNED;monarch=result.house();}
            case VACANT -> outcome=Stage.VACANT;
            case ELIMINATED -> survivors.remove(result.house());
        }
        return new SuccessionState(outcome,initial,survivors,monarch,next,withdrawals);
    }
    public SuccessionState withdraw(String house,UUID actor,String reason,int meeting) {
        if(stage!=Stage.CONTESTED||!remaining.contains(house)||rounds.values().stream().anyMatch(r->r.status()==BallotStatus.OPEN))
            throw new IllegalStateException("Only a remaining candidate may withdraw between ballots.");
        Round latest=rounds.values().stream().max(Comparator.comparingInt(Round::order)).orElseThrow();
        if(latest.status()==BallotStatus.VOID&&latest.meeting()==meeting)
            throw new IllegalStateException("A same-meeting replacement must retain the voided ballot's candidate slate.");
        Set<String> survivors=new HashSet<>(remaining);survivors.remove(house);
        List<Withdrawal> next=new ArrayList<>(withdrawals);next.add(new Withdrawal(rounds.size()+withdrawals.size()+1,house,actor,reason,meeting));
        return new SuccessionState(survivors.isEmpty()?Stage.VACANT:stage,initial,survivors,"",rounds,next);
    }
    public SuccessionState endUnresolved() {
        if(stage!=Stage.CONTESTED||rounds.values().stream().anyMatch(r->r.status()==BallotStatus.OPEN))
            throw new IllegalStateException("Only unfinished succession between ballots can be ended.");
        return new SuccessionState(Stage.UNRESOLVED,initial,remaining,"",rounds,withdrawals);
    }
    private record Step(int order,int meeting,Round round,Withdrawal withdrawal) { }
    private static void validateHistory(Stage stage,Set<String> initial,Set<String> remaining,String crowned,
            Map<UUID,Round> rounds,List<Withdrawal> withdrawals) {
        List<Step> history=new ArrayList<>();
        rounds.values().forEach(r->history.add(new Step(r.order(),r.meeting(),r,null)));
        withdrawals.forEach(w->history.add(new Step(w.order(),w.meeting(),null,w)));
        history.sort(Comparator.comparingInt(Step::order));
        Set<String> expected=new HashSet<>(initial);Stage outcome=Stage.CONTESTED;String monarch="";
        int sequence=0,lastMeeting=0,lastCompleted=0;boolean open=false;Round previousRound=null;
        for(Step step:history) {
            if(step.order()!=++sequence||step.meeting()<lastMeeting||open||outcome!=Stage.CONTESTED)
                throw new IllegalArgumentException("Succession history is out of order or continues after its end.");
            lastMeeting=step.meeting();
            if(step.round()!=null) {
                Round round=step.round();previousRound=round;
                if(!round.slate().equals(expected)||round.meeting()<=lastCompleted)
                    throw new IllegalArgumentException("A regency round changed its slate or repeated a completed meeting.");
                if(round.status()==BallotStatus.OPEN)open=true;
                if(round.status()==BallotStatus.CLOSED) {
                    lastCompleted=round.meeting();var result=round.result();
                    switch(result.decision()) {
                        case ELIMINATED -> {
                            if(expected.size()<=2||!expected.remove(result.house()))throw new IllegalArgumentException("Invalid elimination.");
                        }
                        case CROWNED -> {outcome=Stage.CROWNED;monarch=result.house();}
                        case VACANT -> {if(expected.size()>2)throw new IllegalArgumentException("An unfinished elimination round cannot declare vacancy.");outcome=Stage.VACANT;}
                    }
                }
            } else {
                if(sequence==1||previousRound!=null&&previousRound.status()==BallotStatus.VOID&&previousRound.meeting()==step.meeting())
                    throw new IllegalArgumentException("A same-meeting replacement cannot change its candidate slate.");
                if(!expected.remove(step.withdrawal().house()))throw new IllegalArgumentException("Invalid candidate withdrawal.");
                if(expected.isEmpty())outcome=Stage.VACANT;
            }
        }
        boolean unresolved=stage==Stage.UNRESOLVED&&outcome==Stage.CONTESTED&&!open;
        if(!remaining.equals(expected)||!crowned.equals(monarch)||stage!=outcome&&!unresolved)
            throw new IllegalArgumentException("Succession state differs from its chronological history.");
    }
    private static void house(String id){if(id==null||id.length()>128||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid candidate ID.");}
}
