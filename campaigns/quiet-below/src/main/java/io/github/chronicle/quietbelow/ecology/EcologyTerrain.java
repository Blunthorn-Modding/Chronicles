package io.github.chronicle.quietbelow.ecology;

import java.util.*;

/** Small Minecraft-independent world port. Implementations must reject unloaded reads. */
public interface EcologyTerrain {
    boolean loaded(Pos p);
    State get(Pos p);
    boolean solidFace(Pos p, int face);
    boolean substrate(Pos p);
    int light(Pos p);
    boolean set(Pos p, State state);

    record Pos(int x, int y, int z) {
        public Pos {
            if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || Math.abs((long) y) > 4096)
                throw new IllegalArgumentException("Ecology position outside supported bounds");
        }
        public Pos offset(int face) { int[] d = DIRECTIONS[face]; return new Pos(x + d[0], y + d[1], z + d[2]); }
        public Pos add(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }

    // Pairs are opposites: down/up, north/south, west/east.
    int[][] DIRECTIONS = {{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    String[] FACES = {"down","up","north","south","west","east"};

    record State(String id, Map<String, String> properties) {
        public State {
            if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid ecology block ID");
            properties = Map.copyOf(properties);
            if (properties.size() > 32) throw new IllegalArgumentException("Too many block properties");
            properties.forEach((k,v) -> {
                if (!k.matches("[a-z0-9_]{1,64}") || !v.matches("[a-z0-9_.-]{1,64}"))
                    throw new IllegalArgumentException("Invalid block property");
            });
        }
        public boolean network() { return Set.of("minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst",
                "minecraft:sculk_sensor", "minecraft:sculk_shrieker").contains(id); }
        public boolean vein() { return id.equals("minecraft:sculk_vein"); }
        public boolean air() { return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air"); }
        public boolean face(int face) { return "true".equals(properties.get(FACES[face])); }
        public State with(String key, String value) { var p = new TreeMap<>(properties); p.put(key, value); return new State(id, p); }
        /** Sensor power/phase, catalyst bloom and shrieking are transient native state. */
        public boolean sameOwnedShape(State other) {
            if (!id.equals(other.id)) return false;
            if (vein()) { for (int f=0; f<6; f++) if (face(f) != other.face(f)) return false; }
            return !id.equals("minecraft:sculk_shrieker") || Objects.equals(properties.get("can_summon"), other.properties.get("can_summon"));
        }
    }

    State SCULK = new State("minecraft:sculk", Map.of());
    State AIR = new State("minecraft:air", Map.of());
    static State vein(int face) {
        Map<String,String> properties = new TreeMap<>();
        for (String f : FACES) properties.put(f,"false");
        properties.put(FACES[face],"true"); properties.put("waterlogged","false");
        return new State("minecraft:sculk_vein",properties);
    }

    /** Continuous face/edge contact, excluding a point-only corner and diagonal full blocks. */
    static boolean connected(Pos a, State as, Pos b, State bs) {
        int dx=b.x-a.x, dy=b.y-a.y, dz=b.z-a.z;
        if (Math.abs(dx)>1 || Math.abs(dy)>1 || Math.abs(dz)>1 || Math.abs(dx)+Math.abs(dy)+Math.abs(dz)>2) return false;
        if (!as.vein() && !bs.vein()) return Math.abs(dx)+Math.abs(dy)+Math.abs(dz)==1;
        for (int af=-1; af<6; af++) {
            if (as.vein() ? af<0 || !as.face(af) : af>=0) continue;
            double[] ab=box(0,0,0,af);
            for (int bf=-1; bf<6; bf++) {
                if (bs.vein() ? bf<0 || !bs.face(bf) : bf>=0) continue;
                double[] bb=box(dx,dy,dz,bf); int positive=0; boolean intersects=true;
                for(int axis=0;axis<3;axis++) {
                    double overlap=Math.min(ab[axis+3],bb[axis+3])-Math.max(ab[axis],bb[axis]);
                    if(overlap<0) { intersects=false; break; }
                    if(overlap>0) positive++;
                }
                if(intersects && positive>=1) return true;
            }
        }
        return false;
    }
    private static double[] box(int x,int y,int z,int face) {
        double[] b={x,y,z,x+1,y+1,z+1};
        if(face>=0) { int axis=face<2?1:face<4?2:0; double edge=b[axis]+(face%2); b[axis]=edge; b[axis+3]=edge; }
        return b;
    }
}
