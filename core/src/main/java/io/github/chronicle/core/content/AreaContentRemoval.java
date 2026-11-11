package io.github.chronicle.core.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Durable target-removal claim. It records evidence but performs no world mutation. */
public record AreaContentRemoval(UUID operationId, long expectedContentRevision,
                                 Map<String,Long> affectedBindingRevisions,
                                 Set<String> retiredBindingIds,
                                 Set<ManagedBookRemoval> managedBooks,
                                 Stage stage, String preparedAt,
                                 Optional<String> durableWorldReceipt,
                                 Optional<String> committedAt) {
    public enum Stage { PREPARED, COMMITTED }
    public static final int MAX_AFFECTED_BINDINGS=ChestContentState.MAX_BINDINGS;
    public static final int MAX_MANAGED_BOOKS=256;

    public AreaContentRemoval {
        if(operationId==null)throw new IllegalArgumentException("area-content operation ID is required");
        if(expectedContentRevision<0)throw new IllegalArgumentException("expected chest-content revision cannot be negative");
        if(affectedBindingRevisions==null||affectedBindingRevisions.size()>MAX_AFFECTED_BINDINGS)throw new IllegalArgumentException("too many affected chest bindings");
        LinkedHashMap<String,Long> affected=new LinkedHashMap<>();affectedBindingRevisions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{ContentIds.requireId(entry.getKey(),"affected binding");ContentIds.requireRevision(entry.getValue(),"affected binding");affected.put(entry.getKey(),entry.getValue());});
        if(retiredBindingIds==null||retiredBindingIds.size()>MAX_AFFECTED_BINDINGS)throw new IllegalArgumentException("too many retired chest bindings");
        LinkedHashSet<String> retired=new LinkedHashSet<>();retiredBindingIds.stream().sorted().forEach(id->{ContentIds.requireId(id,"retired binding");if(!affected.containsKey(id))throw new IllegalArgumentException("retired binding must also be affected: "+id);retired.add(id);});
        if(managedBooks==null||managedBooks.size()>MAX_MANAGED_BOOKS)throw new IllegalArgumentException("too many managed books in area removal");
        for(ManagedBookRemoval book:managedBooks)if(book==null)throw new IllegalArgumentException("managed book evidence cannot contain null");
        LinkedHashSet<ManagedBookRemoval> books=new LinkedHashSet<>();LinkedHashSet<String> bookIds=new LinkedHashSet<>();managedBooks.stream().sorted(java.util.Comparator.comparing(ManagedBookRemoval::bookId)).forEach(book->{if(!bookIds.add(book.bookId()))throw new IllegalArgumentException("area removal may claim each managed book once");books.add(book);});
        if(affected.isEmpty()&&books.isEmpty())throw new IllegalArgumentException("area content removal must affect a binding or managed book");
        if(stage==null)throw new IllegalArgumentException("area-content removal stage is required");
        ContentIds.requireText(preparedAt,"area-content preparation time",128);
        durableWorldReceipt=durableWorldReceipt==null?Optional.empty():durableWorldReceipt;
        committedAt=committedAt==null?Optional.empty():committedAt;
        if(stage==Stage.PREPARED&&(durableWorldReceipt.isPresent()||committedAt.isPresent()))throw new IllegalArgumentException("prepared area removal cannot contain completion evidence");
        if(stage==Stage.COMMITTED&&(durableWorldReceipt.isEmpty()||committedAt.isEmpty()))throw new IllegalArgumentException("committed area removal requires durable world evidence");
        durableWorldReceipt.ifPresent(value->ContentIds.requireText(value,"durable world receipt",512));
        committedAt.ifPresent(value->ContentIds.requireText(value,"area-content completion time",128));
        affectedBindingRevisions=Collections.unmodifiableMap(affected);retiredBindingIds=Collections.unmodifiableSet(retired);managedBooks=Collections.unmodifiableSet(books);
    }

    public static AreaContentRemoval prepared(UUID operationId,long expectedRevision,Map<String,Long> affected,
                                              Set<String> retired,Set<ManagedBookRemoval> books,String preparedAt){
        return new AreaContentRemoval(operationId,expectedRevision,affected,retired,books,Stage.PREPARED,preparedAt,Optional.empty(),Optional.empty());
    }

    public boolean matchesPrepare(long expectedRevision,Set<String> affectedIds,Set<String> retiredIds,
                                  Set<ManagedBookRemoval> books,String at){
        return expectedContentRevision==expectedRevision&&affectedBindingRevisions.keySet().equals(affectedIds)
                &&retiredBindingIds.equals(retiredIds)&&managedBooks.equals(books)&&preparedAt.equals(at);
    }

    public AreaContentRemoval committed(String receipt,String at){
        return new AreaContentRemoval(operationId,expectedContentRevision,affectedBindingRevisions,retiredBindingIds,managedBooks,Stage.COMMITTED,preparedAt,Optional.of(receipt),Optional.of(at));
    }

    public Set<String> managedBookIds(){LinkedHashSet<String> result=new LinkedHashSet<>();managedBooks.forEach(book->result.add(book.bookId()));return Collections.unmodifiableSet(result);}
}
