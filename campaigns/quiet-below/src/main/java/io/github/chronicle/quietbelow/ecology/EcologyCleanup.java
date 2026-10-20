package io.github.chronicle.quietbelow.ecology;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;

/** Loaded-only, bounded cleanup scheduling. Restart/load rescans rather than trusting a saved cursor. */
public final class EcologyCleanup {
    public static final int MAX_CHUNKS=16384, READS=512, WRITES=2;
    private final int bottom,height;
    private final LinkedHashMap<Long,Integer> queue=new LinkedHashMap<>();
    private final Set<Long> dirty=new HashSet<>();
    private final Set<Pos> changed=new LinkedHashSet<>();
    private long completed,removed;
    public EcologyCleanup(int bottom,int height) {
        if(bottom< -4096||height<1||(long)bottom+height>4096)throw new IllegalArgumentException("Unsupported cleanup height");
        this.bottom=bottom;this.height=height;
    }
    public void load(int x,int z) {
        long key=key(x,z);
        if(!queue.containsKey(key)&&queue.size()>=MAX_CHUNKS)throw new IllegalStateException("Sculk cleanup loaded-chunk queue is full");
        queue.putIfAbsent(key,0);
    }
    public void changed(int x,int z) {long key=key(x,z);if(queue.containsKey(key))dirty.add(key);else load(x,z);}
    public void changed(Pos p) {if(changed.size()<EcologyEngine.MAX_CELLS)changed.add(p);changed(p.x()>>4,p.z()>>4);}
    public void unload(int x,int z) {long key=key(x,z);queue.remove(key);dirty.remove(key);}
    public int pending() {return queue.size();}
    public long completed() {return completed;}
    public long removed() {return removed;}
    public void tick(EcologyTerrain w,EcologyEngine engine,boolean playing) throws IOException {
        if(!playing||!engine.cleanup()||queue.isEmpty()||(engine.claim()!=null&&!engine.claim().complete()))return;
        int writes=0;
        for(int n=0;n<16&&!changed.isEmpty()&&writes<WRITES&&engine.writeBudgetRemaining()>0;n++) {
            Pos p=changed.iterator().next();changed.remove(p);if(engine.purge(w,p,true)){writes++;removed++;}
        }
        var first=queue.entrySet().iterator().next();long key=first.getKey();int cursor=first.getValue();
        int cx=(int)(key>>32),cz=(int)key,size=256*height,reads=0;boolean retry=false;
        while(cursor<size&&reads++<READS&&writes<WRITES&&engine.writeBudgetRemaining()>0) {
            int x=(cx<<4)+cursor%16,y=bottom+cursor/256,z=(cz<<4)+(cursor/16)%16;cursor++;
            if(Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000)continue;
            Pos p=new Pos(x,y,z);
            if(!w.loaded(p))continue;
            if(w.get(p).network()&&!engine.owns(p)) {
                if(engine.purge(w,p,true)){writes++;removed++;}else retry=true;
            }
        }
        queue.remove(key);
        if(retry)dirty.add(key);
        if(cursor<size)queue.put(key,cursor);
        else if(dirty.remove(key))queue.put(key,0);
        else completed++;
    }
    private static long key(int x,int z) {return ((long)x<<32)|(z&0xffffffffL);}
}
