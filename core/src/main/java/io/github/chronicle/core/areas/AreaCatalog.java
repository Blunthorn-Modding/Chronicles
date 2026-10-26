package io.github.chronicle.core.areas;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record AreaCatalog(long revision, Map<String,AreaChange> areas, Set<String> tombstones) {
    public static final int MAX_AREAS = 128;
    public static final int MAX_TOMBSTONES = 4096;
    public AreaCatalog {
        if(revision<0)throw new IllegalArgumentException("catalog revision must be non-negative");
        if(areas==null||areas.size()>MAX_AREAS)throw new IllegalArgumentException("too many areas");
        if(tombstones==null||tombstones.size()>MAX_TOMBSTONES)throw new IllegalArgumentException("too many area tombstones");
        LinkedHashSet<String> checkedTombstones=tombstones.stream().sorted().peek(id->{if(id==null||!io.github.chronicle.core.authoring.AuthoringSite.ID.matcher(id).matches())throw new IllegalArgumentException("area tombstone ID is invalid");}).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashMap<String,AreaChange> a=new LinkedHashMap<>();areas.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{if(e.getKey()==null||e.getValue()==null||!e.getKey().equals(e.getValue().id())||checkedTombstones.contains(e.getKey()))throw new IllegalArgumentException("area catalog entry is invalid");a.put(e.getKey(),e.getValue());});
        areas=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(a));tombstones=java.util.Collections.unmodifiableSet(checkedTombstones);
    }
    public static AreaCatalog empty(){return new AreaCatalog(0,Map.of(),Set.of());}
}
