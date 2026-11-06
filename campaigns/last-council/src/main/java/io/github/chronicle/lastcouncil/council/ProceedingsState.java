package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.ballots.*;
import java.util.*;

/** Public Council business and pinned opening intents; secret choices live only in Core's ledger. */
public record ProceedingsState(long revision, int meeting, Phase phase, String votingRegion,
        Set<String> lecterns, Map<UUID, Motion> motions, Map<UUID, Attempt> attempts,
        UUID activeBallot, Pending pending, Map<UUID, Call> calls) {
    public enum Phase { BETWEEN, GATHERING, DISCUSSION, RECESS }
    public enum MotionStatus { SUBMITTED, AGENDA, VOTING, RECORDED, WITHDRAWN }
    public enum Threshold { MAJORITY, TWO_THIRDS }
    public enum Operation { OPEN, CLOSE, VOID }
    public ProceedingsState {
        if (revision < 0 || meeting < 0) throw new IllegalArgumentException("Invalid Council revision or meeting.");
        Objects.requireNonNull(phase); Objects.requireNonNull(votingRegion);
        if (!votingRegion.isEmpty()) siteId(votingRegion);
        lecterns = Set.copyOf(lecterns); motions = Map.copyOf(motions);
        attempts = Map.copyOf(attempts); calls = Map.copyOf(calls);
        if (lecterns.size() > 32 || motions.size() > 128 || attempts.size() > 256 || calls.size() > 256)
            throw new IllegalArgumentException("Council history is full.");
        lecterns.forEach(ProceedingsState::siteId);
        var fixedMotions=motions; var fixedAttempts=attempts;
        motions.forEach((id,m) -> { if (!id.equals(m.id())) throw new IllegalArgumentException("Motion identity mismatch."); });
        attempts.forEach((id,a) -> {
            Motion m = fixedMotions.get(a.motion());
            if (!id.equals(a.definition().id()) || m == null || !m.ballots().contains(id)
                    || !a.definition().purpose().equals(m.id().toString()) || !a.definition().title().equals(m.title()))
                throw new IllegalArgumentException("Ballot attempt does not match its motion.");
        });
        motions.values().forEach(m -> m.ballots().forEach(id -> {
            Attempt a = fixedAttempts.get(id);
            if (a == null || !a.motion().equals(m.id())) throw new IllegalArgumentException("Missing motion attempt.");
        }));
        if (activeBallot != null && (!attempts.containsKey(activeBallot)
                || motions.get(attempts.get(activeBallot).motion()).status() != MotionStatus.VOTING))
            throw new IllegalArgumentException("Active ballot is not on the floor.");
        if(activeBallot!=null&&(!attempts.get(activeBallot).region().id().equals(votingRegion)||phase!=Phase.DISCUSSION||attempts.get(activeBallot).meeting()!=meeting))
            throw new IllegalArgumentException("Active ballot meeting or Region changed.");
        long voting = motions.values().stream().filter(m -> m.status() == MotionStatus.VOTING).count();
        if (voting != (activeBallot == null ? 0 : 1)) throw new IllegalArgumentException("Conflicting voting motions.");
        if (pending != null && !pending.ballot().equals(activeBallot)) throw new IllegalArgumentException("Pending ballot changed.");
        calls.forEach((id,c) -> { if (!id.equals(c.id())) throw new IllegalArgumentException("Call identity mismatch."); });
    }
    public static ProceedingsState empty() {
        return new ProceedingsState(0,0,Phase.BETWEEN,"",Set.of(),Map.of(),Map.of(),null,null,Map.of());
    }
    public record Motion(UUID id, String title, String text, UUID proposer, String proposerHouse,
            BallotVisibility visibility, Threshold threshold, boolean hostPrepared, Set<String> sponsors,
            MotionStatus status, List<UUID> ballots, List<String> notes) {
        public Motion {
            Objects.requireNonNull(id); Objects.requireNonNull(proposer); Objects.requireNonNull(visibility);
            Objects.requireNonNull(threshold); Objects.requireNonNull(status);
            validText(title,160,false); validText(text,4096,true); Objects.requireNonNull(proposerHouse);
            if (!hostPrepared && proposerHouse.isBlank()) throw new IllegalArgumentException("A proposal needs its house.");
            if (!proposerHouse.isBlank()) houseId(proposerHouse);
            sponsors=Set.copyOf(sponsors); ballots=List.copyOf(ballots); notes=List.copyOf(notes);
            if (sponsors.size()>31 || ballots.size()>16 || notes.size()>32 || new HashSet<>(ballots).size()!=ballots.size())
                throw new IllegalArgumentException("Motion history is full.");
            sponsors.forEach(ProceedingsState::houseId); notes.forEach(note -> validText(note,512,true));
            if (sponsors.contains(proposerHouse)) throw new IllegalArgumentException("A house cannot sponsor itself.");
        }
        public boolean ready(int required) { return hostPrepared || sponsors.size() >= required; }
        public Motion with(MotionStatus status, Set<String> sponsors, List<UUID> ballots, List<String> notes) {
            return new Motion(id,title,text,proposer,proposerHouse,visibility,threshold,hostPrepared,sponsors,status,ballots,notes);
        }
    }
    /** The original geometry and representative identities stay fixed for the life of this ballot. */
    public record Attempt(UUID motion, int meeting, AuthoringSite.Region region,
            BallotDefinition definition, UUID openedBy) {
        public Attempt {
            Objects.requireNonNull(motion); Objects.requireNonNull(region); Objects.requireNonNull(definition);
            Objects.requireNonNull(openedBy); if(meeting<1)throw new IllegalArgumentException("Invalid meeting.");
        }
    }
    public record Pending(Operation operation, UUID ballot, UUID actor, String reason) {
        public Pending {
            Objects.requireNonNull(operation); Objects.requireNonNull(ballot); Objects.requireNonNull(actor);
            Objects.requireNonNull(reason);
            if(operation==Operation.VOID)validText(reason,512,true);
            else if(!reason.isEmpty())throw new IllegalArgumentException("Only a void has a reason.");
        }
    }
    public record Call(UUID id, UUID actor, String at, int meeting) {
        public Call { Objects.requireNonNull(id); Objects.requireNonNull(actor); validText(at,64,false); if(meeting<0)throw new IllegalArgumentException("Invalid call meeting."); }
    }
    static void validText(String value,int max,boolean multiline) {
        if(value!=null)for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))throw new IllegalArgumentException("Malformed Unicode.");}
            else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Malformed Unicode.");
        }
        if(value==null||value.isBlank()||value.codePointCount(0,value.length())>max
                ||value.codePoints().anyMatch(c -> c==0xA7 || Character.isISOControl(c)&&!(multiline&&c=='\n')))
            throw new IllegalArgumentException("Text is empty, too long or contains unsupported formatting.");
    }
    private static void siteId(String id) { if(!AuthoringSite.ID.matcher(id).matches())throw new IllegalArgumentException("Invalid site ID."); }
    private static void houseId(String id) { if(id==null||id.length()>128||!AuthoringSite.NAMESPACED_ID.matcher(id).matches())throw new IllegalArgumentException("Invalid house ID."); }
}
