package io.github.chronicle.core.offering.temporary;

import io.github.chronicle.core.offering.OfferingDefinition;

import java.util.*;

/** Bounded immutable temporary-offering authority, independent from the lasting-bargain ledger. */
public record TemporaryOfferingLedger(long revision, List<TemporaryOfferingClaim> claims, Set<String> closedSources) {
    public static final int MAX_CLAIMS=16_384, MAX_PLAYER_CLAIMS=128, MAX_UNRESOLVED=256, MAX_CLOSED_SOURCES=256;
    public static final TemporaryOfferingLedger EMPTY=new TemporaryOfferingLedger(0,List.of(),Set.of());
    public TemporaryOfferingLedger {
        if(revision<0||claims==null||claims.size()>MAX_CLAIMS||closedSources==null||closedSources.size()>MAX_CLOSED_SOURCES)
            throw new IllegalArgumentException("invalid temporary offering ledger bounds");
        Map<UUID,Integer> perPlayer=new HashMap<>();Set<UUID> ids=new HashSet<>();int unresolved=0;
        for(var claim:claims){if(claim==null||!ids.add(claim.transaction()))throw new IllegalArgumentException("duplicate temporary offering transaction");
            if(perPlayer.merge(claim.player(),1,Integer::sum)>MAX_PLAYER_CLAIMS)throw new IllegalArgumentException("too many temporary offering claims for one player");
            if(claim.unresolved()&&++unresolved>MAX_UNRESOLVED)throw new IllegalArgumentException("too many unresolved temporary offering claims");}
        TreeSet<String> closed=new TreeSet<>();for(String source:closedSources){OfferingDefinition.validId(source);if(!closed.add(source))throw new IllegalArgumentException("duplicate closed temporary offering source");}
        claims=List.copyOf(claims);closedSources=Collections.unmodifiableSet(closed);
    }
    public Optional<TemporaryOfferingClaim> claim(UUID transaction){return claims.stream().filter(value->value.transaction().equals(transaction)).findFirst();}
    public List<TemporaryOfferingClaim> unresolved(){return claims.stream().filter(TemporaryOfferingClaim::unresolved).toList();}
    public TemporaryOfferingLedger add(long expected,TemporaryOfferingClaim claim){
        if(expected!=revision)throw new IllegalStateException("temporary offering authority changed");
        if(closedSources.contains(claim.source()))throw new IllegalStateException("temporary offering source is closed");
        var existing=claim(claim.transaction());if(existing.isPresent()){if(!existing.get().equals(claim))throw new IllegalStateException("temporary transaction belongs to another claim");return this;}
        ArrayList<TemporaryOfferingClaim> next=new ArrayList<>(claims);next.add(claim);return new TemporaryOfferingLedger(Math.addExact(revision,1),next,closedSources);
    }
    public TemporaryOfferingLedger transition(long expected,UUID transaction,TemporaryOfferingClaim.State from,TemporaryOfferingClaim.State to,String reviewer){
        if(expected!=revision)throw new IllegalStateException("temporary offering authority changed");
        ArrayList<TemporaryOfferingClaim> next=new ArrayList<>(claims);for(int i=0;i<next.size();i++){var claim=next.get(i);if(!claim.transaction().equals(transaction))continue;
            if(claim.state()==to)return this;if(claim.state()!=from)throw new IllegalStateException("temporary offering claim state changed");requireTransition(from,to);next.set(i,claim.with(to,reviewer));return new TemporaryOfferingLedger(Math.addExact(revision,1),next,closedSources);}
        throw new IllegalArgumentException("unknown temporary offering transaction");
    }
    public TemporaryOfferingLedger removeArchived(Collection<UUID> transactions){
        if(transactions==null||transactions.isEmpty())return this;Set<UUID> ids=Set.copyOf(transactions);ArrayList<TemporaryOfferingClaim> next=new ArrayList<>();
        for(var claim:claims){if(!ids.contains(claim.transaction()))next.add(claim);else if(!terminal(claim))throw new IllegalStateException("only terminal temporary claims may be archived");}
        if(next.size()+ids.size()!=claims.size())throw new IllegalArgumentException("unknown or duplicate temporary archive transaction");
        return new TemporaryOfferingLedger(Math.addExact(revision,1),next,closedSources);
    }
    public TemporaryOfferingLedger expire(long clock){
        ArrayList<TemporaryOfferingClaim> next=new ArrayList<>(claims);boolean changed=false;
        for(int i=0;i<next.size();i++){var claim=next.get(i);if(claim.state()==TemporaryOfferingClaim.State.COMPLETE&&claim.terms().durationTicks()>0&&clock>=claim.expiresAtTicks()){next.set(i,claim.with(TemporaryOfferingClaim.State.EXPIRED,null));changed=true;}}
        return changed?new TemporaryOfferingLedger(Math.addExact(revision,1),next,closedSources):this;
    }
    public TemporaryOfferingLedger close(String source){
        OfferingDefinition.validId(source);if(closedSources.contains(source))return this;TreeSet<String> closed=new TreeSet<>(closedSources);closed.add(source);ArrayList<TemporaryOfferingClaim> next=new ArrayList<>(claims);
        for(int i=0;i<next.size();i++){var claim=next.get(i);if(claim.source().equals(source)&&claim.state()==TemporaryOfferingClaim.State.COMPLETE)next.set(i,claim.with(TemporaryOfferingClaim.State.CLOSED,null));}
        return new TemporaryOfferingLedger(Math.addExact(revision,1),next,closed);
    }
    public static boolean terminal(TemporaryOfferingClaim claim){return switch(claim.state()){case REVIEWED,EXPIRED,CLOSED->true;case COMPLETE->claim.terms().durationTicks()==0;default->false;};}
    public static void requireTransition(TemporaryOfferingClaim.State from,TemporaryOfferingClaim.State to){boolean legal=switch(from){case CLAIMED->to==TemporaryOfferingClaim.State.COMPLETE||to==TemporaryOfferingClaim.State.REVIEWED;case COMPLETE->to==TemporaryOfferingClaim.State.EXPIRED||to==TemporaryOfferingClaim.State.CLOSED;case REVIEWED,EXPIRED,CLOSED->false;};if(!legal)throw new IllegalStateException("temporary offering claim state cannot rewind or skip authority");}
}
