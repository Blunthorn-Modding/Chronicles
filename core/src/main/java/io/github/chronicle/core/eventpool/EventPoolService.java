package io.github.chronicle.core.eventpool;

import io.github.chronicle.core.play.EventConfiguration;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

/** Saves a draw before exposing it; a failed save requires reopening the store. */
public final class EventPoolService {
    public interface Storage { byte[] load() throws IOException;void save(byte[] bytes)throws IOException;void verifyUnchanged()throws IOException; }
    public record Candidate(EventConfiguration configuration,int session,UUID actor,List<UUID> recipients) {
        public Candidate { Objects.requireNonNull(configuration);recipients=List.copyOf(recipients);new EventPoolDraw("validation","pool:validation","0".repeat(64),new UUID(0,0),configuration,session,actor,recipients); }
    }
    private final Storage storage;private Map<String,EventPoolDraw> draws;private boolean poisoned;
    public EventPoolService(Storage storage)throws IOException {this.storage=Objects.requireNonNull(storage);byte[] bytes=storage.load();draws=bytes==null?Map.of():EventPoolStore.decode(bytes);}
    public synchronized void verifyUnchanged()throws IOException {if(poisoned)throw new IOException("Reload pool storage after an uncertain save");storage.verifyUnchanged();}
    public synchronized Map<String,EventPoolDraw> draws(){if(poisoned)throw new IllegalStateException("Reopen pool storage after an uncertain save");return draws;}
    public synchronized EventPoolDraw claim(EventPoolDefinition pool,String key,Map<String,Candidate> candidates,IntUnaryOperator random,Supplier<UUID> operation)throws IOException {
        if(poisoned)throw new IllegalStateException("Reopen pool storage after an uncertain save");
        Objects.requireNonNull(pool);Objects.requireNonNull(key);Objects.requireNonNull(candidates);Objects.requireNonNull(random);Objects.requireNonNull(operation);
        storage.verifyUnchanged();
        EventPoolDraw existing=draws.get(key);
        if(existing!=null){if(!existing.poolId().equals(pool.id()))throw new IllegalStateException("Draw key belongs to another pool");return existing;}
        if(draws.size()>=256)throw new IllegalStateException("Pool draw history is full");
        var reserved=new HashSet<String>();draws.values().forEach(d->reserved.add(d.configuration().definition().id()));
        var eligible=pool.entries().stream().filter(e->candidates.containsKey(e.eventId())&&!reserved.contains(e.eventId())).sorted(Comparator.comparing(EventPoolDefinition.Entry::eventId)).toList();
        if(eligible.isEmpty())throw new IllegalStateException("No eligible unreserved Events in pool");
        for(var entry:eligible)if(!entry.eventId().equals(Objects.requireNonNull(candidates.get(entry.eventId())).configuration().definition().id()))throw new IllegalArgumentException("Candidate ID mismatch");
        int total=eligible.stream().mapToInt(EventPoolDefinition.Entry::weight).sum();int pick=random.applyAsInt(total);
        if(pick<0||pick>=total)throw new IllegalArgumentException("Random draw outside bounds");
        EventPoolDefinition.Entry selected=null;for(var entry:eligible){if(pick<entry.weight()){selected=entry;break;}pick-=entry.weight();}
        Candidate c=candidates.get(Objects.requireNonNull(selected).eventId());
        EventPoolDraw result=new EventPoolDraw(key,pool.id(),pool.fingerprint(),Objects.requireNonNull(operation.get()),c.configuration(),c.session(),c.actor(),c.recipients());
        if(draws.values().stream().anyMatch(d->d.operationId().equals(result.operationId())))throw new IllegalArgumentException("Duplicate pool operation ID");
        Map<String,EventPoolDraw> next=new LinkedHashMap<>(draws);next.put(key,result);byte[] bytes=EventPoolStore.encode(next);
        try{storage.save(bytes);}catch(IOException|RuntimeException failure){poisoned=true;throw failure;}
        draws=Map.copyOf(next);return result;
    }
}
