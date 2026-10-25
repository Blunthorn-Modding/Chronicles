package io.github.chronicle.core.books;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Startup-only registry of campaign-supplied book definitions. */
public final class BookCatalog {
    public static final int MAX_BOOKS = 256;
    private final TreeMap<String, BookDefinition> books = new TreeMap<>();
    private boolean frozen;

    public synchronized void register(BookDefinition definition) {
        if (frozen) throw new IllegalStateException("Book catalog is frozen");
        if (definition == null) throw new IllegalArgumentException("book definition is required");
        if (books.containsKey(definition.id())) throw new IllegalArgumentException("duplicate book definition: " + definition.id());
        if (books.size() >= MAX_BOOKS) throw new IllegalArgumentException("book catalog accepts at most " + MAX_BOOKS + " definitions");
        books.put(definition.id(), definition);
    }

    public synchronized void freeze() { frozen = true; }

    public synchronized Map<String, BookDefinition> books() {
        return Collections.unmodifiableMap(new TreeMap<>(books));
    }
}
