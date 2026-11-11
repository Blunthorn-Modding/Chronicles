package io.github.chronicle.core.campaign;

import java.util.Objects;

/** Presentation identity excludes elapsed time and unrelated campaign mutations. */
public final class SessionNoticePolicy {
    private SessionNoticePolicy() { }
    public record State(SessionPhase phase, int session, String hold, boolean building) {
        public State(SessionPhase phase, int session, String hold) { this(phase,session,hold,false); }
        public State { if(session<0)throw new IllegalArgumentException("Negative session");Objects.requireNonNull(hold); }
        public boolean persistent() { return phase!=SessionPhase.ACTIVE||!hold.isBlank(); }
        public boolean broadcastAfter(State previous,boolean refresh,boolean periodic) {
            return !equals(previous)||(persistent()&&(refresh||periodic));
        }
    }
}
