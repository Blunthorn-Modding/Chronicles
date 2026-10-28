package io.github.chronicle.core.server;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.BlockPosition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Strict immutable batch plans plus one observed-write mutable execution journal. */
final class EventBatchFiles {
    private static final int PLAN_MAGIC=0x43454250,PLAN_SCHEMA=1,JOURNAL_MAGIC=0x4345424a,JOURNAL_SCHEMA=1;
    private static final int MAX_PLAN_BYTES=4*1024*1024,MAX_JOURNAL_BYTES=4*1024*1024,MAX_BATCHES=256;
    private EventBatchFiles(){}

    enum Phase{PREPARED,APPLYING,TERRAIN_SAVED,WORLD_COMMITTED,CONFLICT}
    enum Step{PREVERIFY,WRITE,VERIFY_TARGET,VERIFY_ALL,COMMIT}

    record Target(String areaId,long areaRevision,AreaState state,AreaBounds bounds,
                  SnapshotRef before,SnapshotRef after){
        Target{
            if(areaId==null||!io.github.chronicle.core.authoring.AuthoringSite.ID.matcher(areaId).matches())throw new IllegalArgumentException("event batch Area ID is invalid");
            if(areaRevision<1)throw new IllegalArgumentException("event batch Area revision must be positive");
            Objects.requireNonNull(state,"event batch state is required");Objects.requireNonNull(bounds,"event batch bounds are required");
            Objects.requireNonNull(before,"event batch before snapshot is required");Objects.requireNonNull(after,"event batch after snapshot is required");
            if(!bounds.equals(before.bounds())||!bounds.equals(after.bounds()))throw new IllegalArgumentException("event batch snapshots do not match target bounds");
        }
    }

    record Plan(UUID id,UUID actor,String createdAt,String effectsHash,long contentRevision,
                long decodedBytes,int totalCells,int uniqueChunks,List<Target> targets){
        Plan{
            Objects.requireNonNull(id,"event batch ID is required");Objects.requireNonNull(actor,"event batch actor is required");
            Instant parsed=Instant.parse(createdAt);if(!parsed.toString().equals(createdAt))throw new IllegalArgumentException("event batch time is not canonical");
            if(effectsHash==null||!effectsHash.matches("effects-sha256:[0-9a-f]{64}"))throw new IllegalArgumentException("event batch effects hash is invalid");
            if(contentRevision<0)throw new IllegalArgumentException("event batch content revision is invalid");
            if(decodedBytes<1||decodedBytes>64L*1024*1024)throw new IllegalArgumentException("event batch retained evidence exceeds 64 MiB");
            if(totalCells<1||totalCells>262_144)throw new IllegalArgumentException("event batch cell count is outside limits");
            if(uniqueChunks<1||uniqueChunks>64)throw new IllegalArgumentException("event batch chunk count is outside limits");
            targets=List.copyOf(targets);if(targets.isEmpty()||targets.size()>8)throw new IllegalArgumentException("event batch requires 1-8 targets");
            String dimension=targets.getFirst().bounds().dimension();int cells=0;Set<String> areas=new HashSet<>(),regions=new HashSet<>();Set<Long> chunks=new HashSet<>();
            for(int index=0;index<targets.size();index++){Target target=targets.get(index);if(!dimension.equals(target.bounds().dimension()))throw new IllegalArgumentException("event batch targets must share one dimension");if(!areas.add(target.areaId())||!regions.add(target.bounds().regionId()))throw new IllegalArgumentException("event batch target is duplicated");for(int old=0;old<index;old++)if(overlaps(target.bounds(),targets.get(old).bounds()))throw new IllegalArgumentException("event batch targets overlap");cells=Math.addExact(cells,target.bounds().cells());addChunks(chunks,target.bounds());}
            if(cells!=totalCells)throw new IllegalArgumentException("event batch total cell count does not match its targets");
            if(chunks.size()!=uniqueChunks)throw new IllegalArgumentException("event batch unique chunk count does not match its targets");
        }
    }

    record Entry(UUID id,String planSha256,Phase phase,Step step,int target,int cursor,String detail,String updatedAt){
        Entry{
            Objects.requireNonNull(id,"event batch journal ID is required");
            if(planSha256==null||!planSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("event batch plan hash is invalid");
            Objects.requireNonNull(phase,"event batch phase is required");Objects.requireNonNull(step,"event batch step is required");
            if(target<0||target>8||cursor<0||cursor>262_144)throw new IllegalArgumentException("event batch cursor is outside limits");
            if(detail==null||detail.getBytes(StandardCharsets.UTF_8).length>4096)throw new IllegalArgumentException("event batch detail is too long");
            Instant parsed=Instant.parse(updatedAt);if(!parsed.toString().equals(updatedAt))throw new IllegalArgumentException("event batch update time is not canonical");
            if(phase==Phase.PREPARED&&step!=Step.PREVERIFY)throw new IllegalArgumentException("prepared event batch step is invalid");
            if(phase==Phase.APPLYING&&(step==Step.PREVERIFY||step==Step.COMMIT))throw new IllegalArgumentException("applying event batch step is invalid");
            if(phase==Phase.WORLD_COMMITTED&&step!=Step.COMMIT)throw new IllegalArgumentException("committed event batch step is invalid");
            if(phase==Phase.TERRAIN_SAVED&&step!=Step.COMMIT)throw new IllegalArgumentException("terrain-saved event batch step is invalid");
            if(phase==Phase.CONFLICT&&step==Step.COMMIT)throw new IllegalArgumentException("conflicting event batch step is invalid");
        }
        Entry at(Phase nextPhase,Step nextStep,int nextTarget,int nextCursor,String nextDetail){return new Entry(id,planSha256,nextPhase,nextStep,nextTarget,nextCursor,nextDetail,Instant.now().toString());}
    }

    record Journal(long revision,Map<UUID,Entry> entries){
        Journal{
            if(revision<0)throw new IllegalArgumentException("event batch journal revision is negative");
            entries=Collections.unmodifiableMap(new LinkedHashMap<>(entries));
            if(entries.size()>MAX_BATCHES)throw new IllegalArgumentException("event batch journal is full");
            for(var item:entries.entrySet())if(!item.getKey().equals(item.getValue().id()))throw new IllegalArgumentException("event batch journal key mismatch");
        }
        static Journal empty(){return new Journal(0,Map.of());}
        void requireNewBatchCapacity(){if(entries.size()>=MAX_BATCHES)throw new IllegalStateException("World-change history is full (256 runs); preserve the stopped world for maintenance before starting another batch.");}
        Journal put(Entry entry){LinkedHashMap<UUID,Entry> next=new LinkedHashMap<>(entries);Entry old=next.put(entry.id(),entry);if(old==null&&next.size()>MAX_BATCHES)throw new IllegalStateException("event batch journal is full");return new Journal(Math.addExact(revision,1),next);}
    }

    /** A saved terrain receipt alone never authorizes metadata commit after process continuity was lost. */
    static Entry restartProof(Plan plan,Entry entry){
        if(!plan.id().equals(entry.id()))throw new IllegalArgumentException("event batch restart identity mismatch");
        return entry.phase()==Phase.TERRAIN_SAVED?entry.at(Phase.APPLYING,Step.VERIFY_ALL,0,0,""):entry;
    }

    static final class Store{
        private final Path file;private Boolean observedPresent;private byte[] observedHash;private boolean loadedValid;
        Store(MinecraftServer server){this(root(server).resolve("journal.bin"));}
        Store(Path file){this.file=Objects.requireNonNull(file).toAbsolutePath().normalize();}
        synchronized Journal load()throws IOException{
            if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)){observedPresent=false;observedHash=null;loadedValid=true;return Journal.empty();}
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_JOURNAL_BYTES)throw new IOException("event batch journal type/size is invalid");
            byte[] bytes=Files.readAllBytes(file);try{Journal result=decodeJournal(bytes);observedPresent=true;observedHash=shaBytes(bytes);loadedValid=true;return result;}catch(RuntimeException failure){loadedValid=false;throw new IOException("event batch journal is invalid and was preserved",failure);}
        }
        synchronized void save(Journal journal)throws IOException{
            if(!loadedValid)throw new IOException("load a valid event batch journal before saving");byte[] bytes=encodeJournal(journal);if(bytes.length>MAX_JOURNAL_BYTES)throw new IOException("event batch journal exceeds size limit");
            Path parent=file.getParent();Files.createDirectories(parent);boolean present=Files.exists(file,LinkOption.NOFOLLOW_LINKS);
            if(observedPresent!=null&&present!=observedPresent)throw new IOException("event batch journal presence changed externally");
            if(present&&observedHash!=null&&!MessageDigest.isEqual(observedHash,shaBytes(readBounded(file,MAX_JOURNAL_BYTES))))throw new IOException("event batch journal changed externally");
            Path temp=Files.createTempFile(parent,".event-batches-",".tmp");try{
                writeForced(temp,bytes);boolean now=Files.exists(file,LinkOption.NOFOLLOW_LINKS);if(now!=present)throw new IOException("event batch journal changed before publication");
                if(now&&observedHash!=null&&!MessageDigest.isEqual(observedHash,shaBytes(readBounded(file,MAX_JOURNAL_BYTES))))throw new IOException("event batch journal changed before publication");
                try{if(present)Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else Files.createLink(file,temp);}catch(AtomicMoveNotSupportedException failure){throw new IOException("atomic event batch journal replacement is unsupported",failure);}
                observedPresent=true;observedHash=shaBytes(bytes);
            }finally{Files.deleteIfExists(temp);}
        }
        synchronized void verifyUnchanged()throws IOException{
            if(!loadedValid||observedPresent==null)throw new IOException("load a valid Event batch authority before verifying");
            boolean present=Files.exists(file,LinkOption.NOFOLLOW_LINKS);if(present!=observedPresent)throw new IOException("Event batch authority presence changed externally");
            if(present&&(observedHash==null||!MessageDigest.isEqual(observedHash,shaBytes(readBounded(file,MAX_JOURNAL_BYTES)))))throw new IOException("Event batch authority changed externally");
        }
    }

    static String savePlan(MinecraftServer server,Plan plan)throws IOException{
        byte[] bytes=encodePlan(plan);if(bytes.length>MAX_PLAN_BYTES)throw new IOException("event batch plan exceeds size limit");String hash=sha(bytes);
        Path target=planPath(server,plan.id());Files.createDirectories(target.getParent());
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){byte[] old=readBounded(target,MAX_PLAN_BYTES);if(!Arrays.equals(bytes,old))throw new IOException("immutable event batch plan changed");return hash;}
        Path temp=Files.createTempFile(target.getParent(),".event-plan-",".tmp");try{writeForced(temp,bytes);try{Files.createLink(target,temp);}catch(FileAlreadyExistsException race){if(!Arrays.equals(bytes,readBounded(target,MAX_PLAN_BYTES)))throw new IOException("immutable event batch plan changed",race);}}finally{Files.deleteIfExists(temp);}return hash;
    }

    static Plan loadPlan(MinecraftServer server,UUID id,String expectedHash)throws IOException{
        if(expectedHash==null||!expectedHash.matches("[0-9a-f]{64}"))throw new IOException("event batch plan hash is invalid");byte[] bytes=readBounded(planPath(server,id),MAX_PLAN_BYTES);
        if(!MessageDigest.isEqual(fromHex(expectedHash),shaBytes(bytes)))throw new IOException("event batch plan does not match its committed hash");
        try{Plan plan=decodePlan(bytes);if(!plan.id().equals(id))throw new IllegalArgumentException("event batch plan identity mismatch");return plan;}catch(RuntimeException failure){throw new IOException("event batch plan is invalid and was preserved",failure);}
    }

    private static Path root(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-batches");}
    private static Path planPath(MinecraftServer server,UUID id){return root(server).resolve(id+".plan.bin");}

    static byte[] encodePlan(Plan plan)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(PLAN_MAGIC);out.writeInt(PLAN_SCHEMA);text(out,plan.id().toString());text(out,plan.actor().toString());text(out,plan.createdAt());text(out,plan.effectsHash());out.writeLong(plan.contentRevision());out.writeLong(plan.decodedBytes());out.writeInt(plan.totalCells());out.writeInt(plan.uniqueChunks());out.writeInt(plan.targets().size());
            for(Target target:plan.targets()){text(out,target.areaId());out.writeLong(target.areaRevision());text(out,target.state().name());bounds(out,target.bounds());ref(out,target.before());ref(out,target.after());}
        }return bytes.toByteArray();
    }
    static Plan decodePlan(byte[] bytes){try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
        if(in.readInt()!=PLAN_MAGIC||in.readInt()!=PLAN_SCHEMA)throw new IllegalArgumentException("event batch plan magic/schema is invalid");UUID id=uuid(text(in,36,"batch ID")),actor=uuid(text(in,36,"actor"));String created=text(in,128,"created time"),effects=text(in,96,"effects hash");long content=in.readLong(),decoded=in.readLong();int cells=in.readInt(),chunks=in.readInt(),count=count(in,1,8,"target count");List<Target> targets=new ArrayList<>();for(int i=0;i<count;i++)targets.add(new Target(text(in,129,"Area ID"),in.readLong(),enumValue(AreaState.class,text(in,16,"Area state")),bounds(in),ref(in),ref(in)));if(in.read()!=-1)throw new IllegalArgumentException("event batch plan has trailing bytes");return new Plan(id,actor,created,effects,content,decoded,cells,chunks,targets);
    }catch(EOFException failure){throw new IllegalArgumentException("event batch plan is truncated",failure);}catch(IOException failure){throw new IllegalArgumentException("event batch plan cannot be decoded",failure);}}

    static byte[] encodeJournal(Journal journal)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.writeInt(JOURNAL_MAGIC);out.writeInt(JOURNAL_SCHEMA);out.writeLong(journal.revision());out.writeInt(journal.entries().size());for(Entry entry:journal.entries().values()){text(out,entry.id().toString());text(out,entry.planSha256());text(out,entry.phase().name());text(out,entry.step().name());out.writeInt(entry.target());out.writeInt(entry.cursor());textAllowEmpty(out,entry.detail());text(out,entry.updatedAt());}}return bytes.toByteArray();}
    static Journal decodeJournal(byte[] bytes){try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){if(in.readInt()!=JOURNAL_MAGIC||in.readInt()!=JOURNAL_SCHEMA)throw new IllegalArgumentException("event batch journal magic/schema is invalid");long revision=in.readLong();int count=count(in,0,MAX_BATCHES,"journal entry count");Map<UUID,Entry> entries=new LinkedHashMap<>();for(int i=0;i<count;i++){Entry entry=new Entry(uuid(text(in,36,"batch ID")),text(in,64,"plan hash"),enumValue(Phase.class,text(in,32,"phase")),enumValue(Step.class,text(in,32,"step")),in.readInt(),in.readInt(),textAllowEmpty(in,4096,"detail"),text(in,128,"updated time"));if(entries.putIfAbsent(entry.id(),entry)!=null)throw new IllegalArgumentException("duplicate event batch ID");}if(in.read()!=-1)throw new IllegalArgumentException("event batch journal has trailing bytes");return new Journal(revision,entries);}catch(EOFException failure){throw new IllegalArgumentException("event batch journal is truncated",failure);}catch(IOException failure){throw new IllegalArgumentException("event batch journal cannot be decoded",failure);}}

    private static void bounds(DataOutputStream out,AreaBounds b)throws IOException{text(out,b.regionId());text(out,b.dimension());out.writeLong(b.regionRevision());position(out,b.min());position(out,b.max());}
    private static AreaBounds bounds(DataInputStream in)throws IOException{return new AreaBounds(text(in,129,"Region ID"),text(in,256,"dimension"),in.readLong(),position(in),position(in));}
    private static void ref(DataOutputStream out,SnapshotRef r)throws IOException{text(out,r.sha256());bounds(out,r.bounds());out.writeInt(r.cells());out.writeLong(r.encodedBytes());}
    private static SnapshotRef ref(DataInputStream in)throws IOException{return new SnapshotRef(text(in,64,"snapshot hash"),bounds(in),in.readInt(),in.readLong());}
    private static void position(DataOutputStream out,BlockPosition p)throws IOException{out.writeInt(p.x());out.writeInt(p.y());out.writeInt(p.z());}
    private static BlockPosition position(DataInputStream in)throws IOException{return new BlockPosition(in.readInt(),in.readInt(),in.readInt());}
    private static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length<1)throw new IllegalArgumentException("event batch text is empty");out.writeInt(bytes.length);out.write(bytes);}
    private static void textAllowEmpty(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static String text(DataInputStream in,int max,String label)throws IOException{int length=in.readInt();if(length<1||length>max)throw new IllegalArgumentException(label+" byte length is outside limits");return utf8(in,length,label);}
    private static String textAllowEmpty(DataInputStream in,int max,String label)throws IOException{int length=in.readInt();if(length<0||length>max)throw new IllegalArgumentException(label+" byte length is outside limits");return utf8(in,length,label);}
    private static String utf8(DataInputStream in,int length,String label)throws IOException{byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException();String value=new String(bytes,StandardCharsets.UTF_8);if(!Arrays.equals(bytes,value.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException(label+" is not valid UTF-8");return value;}
    private static int count(DataInputStream in,int min,int max,String label)throws IOException{int value=in.readInt();if(value<min||value>max)throw new IllegalArgumentException(label+" is outside limits");return value;}
    private static UUID uuid(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw new IllegalArgumentException();return id;}catch(RuntimeException failure){throw new IllegalArgumentException("UUID is not canonical",failure);}}
    private static <T extends Enum<T>>T enumValue(Class<T> type,String value){try{return Enum.valueOf(type,value);}catch(RuntimeException failure){throw new IllegalArgumentException("unknown "+type.getSimpleName(),failure);}}
    private static byte[] readBounded(Path file,int max)throws IOException{if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)<1||Files.size(file)>max)throw new IOException("event batch evidence is missing or oversized");return Files.readAllBytes(file);}
    private static boolean overlaps(AreaBounds a,AreaBounds b){return a.dimension().equals(b.dimension())&&a.min().x()<=b.max().x()&&a.max().x()>=b.min().x()&&a.min().y()<=b.max().y()&&a.max().y()>=b.min().y()&&a.min().z()<=b.max().z()&&a.max().z()>=b.min().z();}
    private static void addChunks(Set<Long> chunks,AreaBounds bounds){for(int x=Math.floorDiv(bounds.min().x(),16);x<=Math.floorDiv(bounds.max().x(),16);x++)for(int z=Math.floorDiv(bounds.min().z(),16);z<=Math.floorDiv(bounds.max().z(),16);z++)chunks.add(((long)x<<32)^(z&0xffffffffL));}
    private static void writeForced(Path file,byte[] bytes)throws IOException{try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}}
    private static byte[] shaBytes(byte[] bytes)throws IOException{try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException impossible){throw new IOException("SHA-256 unavailable",impossible);}}
    private static String sha(byte[] bytes)throws IOException{return HexFormat.of().formatHex(shaBytes(bytes));}
    private static byte[] fromHex(String value){return HexFormat.of().parseHex(value);}
}
