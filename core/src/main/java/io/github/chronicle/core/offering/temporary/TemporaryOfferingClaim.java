package io.github.chronicle.core.offering.temporary;

import io.github.chronicle.core.offering.OfferingDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Durable claim precedes every external cost or benefit. CLAIMED is deliberately ambiguous. */
public record TemporaryOfferingClaim(UUID transaction, UUID player, String source, String offering,
                                     String station, long claimedAtTicks, long expiresAtTicks,
                                     TemporaryOfferingTerms terms, State state, String reviewedBy) {
    public enum State { CLAIMED, COMPLETE, REVIEWED, EXPIRED, CLOSED }
    public TemporaryOfferingClaim {
        if (transaction == null || player == null || terms == null || state == null)
            throw new IllegalArgumentException("temporary offering claim identity is required");
        OfferingDefinition.validId(source); OfferingDefinition.validId(offering);
        if (station == null || station.isBlank() || station.length() > 512 || station.indexOf('\0') >= 0)
            throw new IllegalArgumentException("invalid temporary offering station identity");
        if (claimedAtTicks < 0 || expiresAtTicks != Math.addExact(claimedAtTicks, terms.durationTicks()))
            throw new IllegalArgumentException("invalid temporary offering active clock");
        if (state == State.REVIEWED) {
            if (reviewedBy == null || reviewedBy.isBlank() || reviewedBy.length() > 160)
                throw new IllegalArgumentException("reviewed temporary claim needs an operator");
        } else if (reviewedBy != null) throw new IllegalArgumentException("only reviewed claims have a reviewer");
        // Validate new reserved metadata without changing the legacy digest/serialization format.
        String cooldown=terms.evidence().get("chronicle.cooldown_until_ticks");
        if(cooldown!=null){try{if(!cooldown.matches("0|[1-9][0-9]*")||Long.parseLong(cooldown)<claimedAtTicks)throw new IllegalArgumentException("invalid captured offering cooldown");}catch(NumberFormatException error){throw new IllegalArgumentException("invalid captured offering cooldown",error);}}
    }
    public boolean unresolved() { return state == State.CLAIMED; }
    public boolean active(long clock) { return state == State.COMPLETE && terms.durationTicks() > 0 && clock < expiresAtTicks; }
    public String digest() {
        StringBuilder value=new StringBuilder("temporary-offering-1");
        add(value,transaction.toString());add(value,player.toString());add(value,source);add(value,offering);add(value,station);
        add(value,Long.toString(claimedAtTicks));add(value,Long.toString(expiresAtTicks));
        add(value,Long.toString(terms.durationTicks()));add(value,Integer.toString(terms.health()));add(value,Integer.toString(terms.exhaustionPercent()));
        terms.evidence().forEach((key,item)->{add(value,key);add(value,item);});
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 unavailable",exception);}
    }
    private static void add(StringBuilder target,String value){target.append('|').append(value.length()).append(':').append(value);}
    public TemporaryOfferingClaim with(State next,String reviewer){return new TemporaryOfferingClaim(transaction,player,source,offering,station,claimedAtTicks,expiresAtTicks,terms,next,reviewer);}
}
