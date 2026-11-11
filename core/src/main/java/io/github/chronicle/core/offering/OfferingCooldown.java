package io.github.chronicle.core.offering;

import io.github.chronicle.core.offering.temporary.*;
import java.util.*;

/** Captured occupied-play cooldowns. Old temporary evidence and archive digests remain untouched. */
public final class OfferingCooldown {
    private static final String UNTIL="chronicle.cooldown_until_ticks";
    private OfferingCooldown() { }
    public static TemporaryOfferingTerms capture(TemporaryOfferingTerms terms,long clock,long ticks) {
        if(clock<0||ticks<0)throw new IllegalArgumentException("invalid offering cooldown clock");
        if(terms.evidence().containsKey(UNTIL))throw new IllegalArgumentException("reserved offering cooldown evidence");
        if(ticks==0)return terms;
        var evidence=new TreeMap<>(terms.evidence());evidence.put(UNTIL,Long.toString(Math.addExact(clock,ticks)));
        return new TemporaryOfferingTerms(terms.durationTicks(),terms.health(),terms.exhaustionPercent(),evidence);
    }
    public static long until(TemporaryOfferingClaim claim) {
        String value=claim.terms().evidence().get(UNTIL);if(value==null)return 0;
        if(!value.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("invalid captured offering cooldown");
        long until;try{until=Long.parseLong(value);}catch(NumberFormatException error){throw new IllegalArgumentException("invalid captured offering cooldown",error);}
        if(until<claim.claimedAtTicks())throw new IllegalArgumentException("offering cooldown precedes its claim");
        return until;
    }
    public static long until(OfferingLedger lasting,TemporaryOfferingLedger temporary,UUID player,String source) {
        long until=lasting.receipts().stream().filter(r->r.player().equals(player)&&r.source().equals(source)).mapToLong(OfferingReceipt::cooldownUntilTicks).max().orElse(0);
        for(var claim:temporary.claims())if(claim.player().equals(player)&&claim.source().equals(source)
                &&(claim.state()==TemporaryOfferingClaim.State.COMPLETE||claim.state()==TemporaryOfferingClaim.State.EXPIRED||claim.state()==TemporaryOfferingClaim.State.CLOSED))
            until=Math.max(until,until(claim));
        return until;
    }
    public static boolean archivable(TemporaryOfferingClaim claim,long durableClock) {
        if(durableClock<0)throw new IllegalArgumentException("durable offering clock required");
        return TemporaryOfferingLedger.terminal(claim)&&until(claim)<=durableClock;
    }
}
