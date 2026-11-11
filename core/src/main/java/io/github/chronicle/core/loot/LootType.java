package io.github.chronicle.core.loot;

import java.util.List;
import java.util.regex.Pattern;

/** A bounded, authorable chest loot profile. */
public record LootType(String id, String label, String description, int recommendedChests, double refillMinutes, List<LootEntry> entries, List<io.github.chronicle.core.books.BookChance> books) {
    public LootType(String id,String label,String description,int recommendedChests,double refillMinutes,List<LootEntry> entries){this(id,label,description,recommendedChests,refillMinutes,entries,List.of());}
    public static final int MAX_ENTRIES = 32;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public LootType {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("loot type id must be a namespaced identifier");
        if (label == null || label.isBlank() || label.length() > 80 || label.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("loot type label must be plain text, 1-80 characters");
        if (description == null || description.isBlank() || description.length() > 256 || description.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("loot type description must be plain text, 1-256 characters");
        if (recommendedChests < 0 || recommendedChests > 512) throw new IllegalArgumentException("recommendedChests must be between 0 and 512");
        if (!Double.isFinite(refillMinutes) || refillMinutes < 0 || refillMinutes > 10080) throw new IllegalArgumentException("refillMinutes must be finite and between 0 and 10080");
        if (entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("loot type entries must contain at most " + MAX_ENTRIES + " entries");
        entries = List.copyOf(entries);
        books = List.copyOf(books);
        if(entries.size()+books.size()>MAX_ENTRIES)throw new IllegalArgumentException("Loot table exceeds "+MAX_ENTRIES+" combined entries");
        if(books.stream().map(io.github.chronicle.core.books.BookChance::bookId).distinct().count()!=books.size())throw new IllegalArgumentException("Duplicate named book reference");
        if (entries.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("loot type entries must not contain null");
    }

    /** Converts configured minutes to server ticks; zero means refills are disabled. */
    public long refillTicks() {
        return refillMinutes == 0 ? 0 : Math.max(1, Math.round(refillMinutes * 1200));
    }
}
