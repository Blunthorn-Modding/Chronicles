package io.github.chronicle.core.encounters;

import java.util.*;

public record EncounterOperationJournal(long revision, Map<UUID, EncounterOperation> operations) {
    public static final int MAX_LIVE_OPERATIONS = 128;
    public EncounterOperationJournal { if(revision<0)throw new IllegalArgumentException("operation journal revision must be non-negative");if(operations==null||operations.size()>MAX_LIVE_OPERATIONS)throw new IllegalArgumentException("live encounter operation capacity is 128");TreeMap<UUID,EncounterOperation> copy=new TreeMap<>();for(var entry:operations.entrySet()){if(entry.getKey()==null||entry.getValue()==null||!entry.getKey().equals(entry.getValue().operationId()))throw new IllegalArgumentException("operation journal entry is invalid");copy.put(entry.getKey(),entry.getValue());}operations=Collections.unmodifiableMap(copy); }
    public static EncounterOperationJournal empty(){return new EncounterOperationJournal(0,Map.of());}
    EncounterOperationJournal put(EncounterOperation operation){if(operations.containsKey(operation.operationId()))throw new IllegalArgumentException("operation UUID already exists");if(operations.size()==MAX_LIVE_OPERATIONS)throw new IllegalStateException("live encounter operation history is full; archive completed operations first");TreeMap<UUID,EncounterOperation> next=new TreeMap<>(operations);next.put(operation.operationId(),operation);return new EncounterOperationJournal(Math.addExact(revision,1),next);}
    EncounterOperationJournal replace(EncounterOperation operation){if(!operations.containsKey(operation.operationId()))throw new IllegalArgumentException("operation UUID is not live");TreeMap<UUID,EncounterOperation> next=new TreeMap<>(operations);next.put(operation.operationId(),operation);return new EncounterOperationJournal(Math.addExact(revision,1),next);}
    EncounterOperationJournal remove(UUID id){TreeMap<UUID,EncounterOperation> next=new TreeMap<>(operations);if(next.remove(id)==null)throw new IllegalArgumentException("operation UUID is not live");return new EncounterOperationJournal(Math.addExact(revision,1),next);}
}
