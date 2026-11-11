package io.github.chronicle.quietbelow.ecology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;

/** Bounded stable slots for maintenance scans that must not copy every position at scan start. */
final class EcologyPositions {
    private final int capacity;
    private final ArrayList<Pos> slots=new ArrayList<>();
    private final Map<Pos,Integer> indices=new HashMap<>();
    private final ArrayDeque<Integer> freed=new ArrayDeque<>();

    EcologyPositions(int capacity) {
        if(capacity<0)throw new IllegalArgumentException("Negative position capacity");
        this.capacity=capacity;
    }

    void add(Pos position) {
        Objects.requireNonNull(position,"position");
        if(indices.containsKey(position))return;
        if(!freed.isEmpty()) {
            int index=freed.removeFirst();
            slots.set(index,position);
            indices.put(position,index);
            return;
        }
        if(slots.size()>=capacity)throw new IllegalStateException("Position capacity reached");
        indices.put(position,slots.size());
        slots.add(position);
    }

    void remove(Pos position) {
        Objects.requireNonNull(position,"position");
        Integer index=indices.remove(position);
        if(index!=null) {
            slots.set(index,null);
            freed.addLast(index);
        }
    }

    void clear() {
        slots.clear();
        indices.clear();
        freed.clear();
    }

    Iterator<Pos> scan() {
        int highWater=slots.size();
        return new Iterator<>() {
            private int index;

            @Override public boolean hasNext() { return index<highWater; }

            @Override public Pos next() {
                if(!hasNext())throw new NoSuchElementException();
                int current=index++;
                return current<slots.size()?slots.get(current):null;
            }
        };
    }
}
