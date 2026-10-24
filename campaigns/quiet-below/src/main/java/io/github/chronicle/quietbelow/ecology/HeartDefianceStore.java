package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.List;
import java.util.Set;

/** Strict atomic receipt for at-most-once Heart-defiance phases. */
public final class HeartDefianceStore {
    static final int SCHEMA_VERSION = 1;
    static final long MAX_FILE_BYTES = 64L * 1024L;
    public static final int DEFAULT_COOLDOWN_SECONDS=600;
    public static final List<Integer> COOLDOWN_CHOICES=List.of(60,300,600,1800,3600);

    public record Pin(String city,long cityRevision,String heart,long heartRevision,String dimension,EcologyTerrain.Pos root) {
        public Pin {
            new EcologyEngine.Setup(city,cityRevision,heart,heartRevision,dimension);
            Objects.requireNonNull(root,"Heart root");
        }
        public static Pin of(EcologyEngine.Setup setup,EcologyTerrain.Pos root){
            Objects.requireNonNull(setup,"Ecology setup");
            return new Pin(setup.city(),setup.cityRevision(),setup.heart(),setup.heartRevision(),setup.dimension(),root);
        }
    }

    public record Receipt(long revision,Pin pin,long claimedAtActiveTicks,long eligibleAfterActiveTicks,boolean permanent) {
        public Receipt {
            if(revision<1)throw new IllegalArgumentException("Defiance revision must be positive");
            Objects.requireNonNull(pin,"Heart pin");
            if(claimedAtActiveTicks<0)throw new IllegalArgumentException("Defiance claim time cannot be negative");
            if(eligibleAfterActiveTicks<claimedAtActiveTicks)throw new IllegalArgumentException("Defiance eligibility cannot precede its claim");
            if(permanent&&eligibleAfterActiveTicks!=Long.MAX_VALUE)throw new IllegalArgumentException("Permanent defiance receipt must never become eligible");
            if(!permanent&&eligibleAfterActiveTicks==Long.MAX_VALUE)throw new IllegalArgumentException("Cooldown defiance receipt must have a finite expiry");
        }
    }
    public record State(long revision,int cooldownSeconds,Receipt receipt){
        public State {
            if(revision<0)throw new IllegalArgumentException("Defiance state revision cannot be negative");
            if(!COOLDOWN_CHOICES.contains(cooldownSeconds))throw new IllegalArgumentException("Choose a supported Heart defiance cooldown");
            if(receipt!=null&&receipt.revision()>revision)throw new IllegalArgumentException("Defiance receipt revision exceeds state revision");
        }
        public static State defaults(){return new State(0,DEFAULT_COOLDOWN_SECONDS,null);}
    }

    @FunctionalInterface interface Committer { void commit(Path file,byte[] bytes)throws IOException; }
    private final Path file;
    private final Committer committer;

    public HeartDefianceStore(Path file){this(file,HeartDefianceStore::atomicCommit);}
    HeartDefianceStore(Path file,Committer committer){
        if(file==null||file.getFileName()==null)throw new IllegalArgumentException("Defiance state file is required");
        this.file=file.toAbsolutePath().normalize();this.committer=Objects.requireNonNull(committer);
    }

    public synchronized State load()throws IOException {
        if(!Files.exists(file))return State.defaults();
        if(!Files.isRegularFile(file)||Files.size(file)>MAX_FILE_BYTES)throw new IOException("Heart defiance state must be a regular file no larger than 64 KiB: "+file);
        try{return decode(Files.readString(file,StandardCharsets.UTF_8));}
        catch(RuntimeException failure){throw new IOException("Heart defiance state is invalid and was not replaced: "+file+" ("+failure.getMessage()+")",failure);}
    }

    /** Saves the final-attempt claim before terrain changes or creature spawning. Warning beats do not claim. */
    public synchronized State claim(Pin pin,long activeTicks,boolean permanent)throws IOException {
        Objects.requireNonNull(pin,"Heart pin");
        if(activeTicks<0)throw new IllegalArgumentException("Active play time cannot be negative");
        State loaded=load();
        if(loaded.receipt()!=null){
            Receipt prior=loaded.receipt();
            if(!prior.pin().equals(pin))throw new IllegalStateException("Saved Heart defiance receipt belongs to different ecology pins");
            if(prior.permanent()||activeTicks<prior.eligibleAfterActiveTicks())throw new IllegalStateException("Heart defiance cooldown is still active");
        }
        long revision=Math.addExact(loaded.revision(),1L);
        long eligible=permanent?Long.MAX_VALUE:Math.addExact(activeTicks,Math.multiplyExact((long)loaded.cooldownSeconds(),20L));
        State next=new State(revision,loaded.cooldownSeconds(),new Receipt(revision,pin,activeTicks,eligible,permanent));
        save(next);
        return next;
    }

    public synchronized State tuneCooldown(int seconds)throws IOException {
        if(!COOLDOWN_CHOICES.contains(seconds))throw new IllegalArgumentException("Choose a supported Heart defiance cooldown");
        State loaded=load();if(loaded.cooldownSeconds()==seconds)return loaded;
        State next=new State(Math.addExact(loaded.revision(),1L),seconds,loaded.receipt());save(next);return next;
    }

    private void save(State next)throws IOException {
        byte[] bytes=encode(next).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_FILE_BYTES)throw new IOException("Heart defiance state serialization exceeds 64 KiB: "+file);
        committer.commit(file,bytes);
    }

    /** A phase is eligible only when it began after the saved cooldown, never midway through it. */
    public static boolean cycleEligible(Receipt receipt,Pin pin,long cycleStartedAtActiveTicks){
        Objects.requireNonNull(pin,"Heart pin");
        if(cycleStartedAtActiveTicks<0)return false;
        return receipt==null||receipt.pin().equals(pin)&&!receipt.permanent()
                &&cycleStartedAtActiveTicks>=receipt.eligibleAfterActiveTicks();
    }

    static String encode(State state){
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",SCHEMA_VERSION);root.addProperty("revision",state.revision());root.addProperty("cooldownSeconds",state.cooldownSeconds());
        if(state.receipt()==null)root.add("receipt",com.google.gson.JsonNull.INSTANCE);else{
            Receipt receipt=state.receipt();JsonObject encoded=new JsonObject();encoded.addProperty("revision",receipt.revision());
            JsonObject pin=new JsonObject();pin.addProperty("city",receipt.pin().city());pin.addProperty("cityRevision",receipt.pin().cityRevision());
            pin.addProperty("heart",receipt.pin().heart());pin.addProperty("heartRevision",receipt.pin().heartRevision());pin.addProperty("dimension",receipt.pin().dimension());
            JsonObject position=new JsonObject();position.addProperty("x",receipt.pin().root().x());position.addProperty("y",receipt.pin().root().y());position.addProperty("z",receipt.pin().root().z());pin.add("root",position);encoded.add("pin",pin);
            encoded.addProperty("claimedAtActiveTicks",receipt.claimedAtActiveTicks());encoded.addProperty("eligibleAfterActiveTicks",receipt.eligibleAfterActiveTicks());encoded.addProperty("permanent",receipt.permanent());root.add("receipt",encoded);
        }
        return root.toString();
    }

    static State decode(String source){
        JsonElement parsed;
        try(JsonReader reader=new JsonReader(new StringReader(source))){
            reader.setStrictness(Strictness.STRICT);parsed=JsonParser.parseReader(reader);
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Unexpected trailing JSON content");
        }catch(IOException|JsonParseException failure){throw new IllegalArgumentException("Unable to read defiance JSON",failure);}
        JsonObject root=object(parsed,Set.of("schemaVersion","revision","cooldownSeconds","receipt"),"root");
        if(integer(root,"schemaVersion")!=SCHEMA_VERSION)throw new IllegalArgumentException("Unsupported Heart defiance schema");
        long revision=natural(root,"revision");int cooldown=integer(root,"cooldownSeconds");JsonElement receiptValue=required(root,"receipt");
        if(receiptValue.isJsonNull())return new State(revision,cooldown,null);
        JsonObject receipt=object(receiptValue,Set.of("revision","pin","claimedAtActiveTicks","eligibleAfterActiveTicks","permanent"),"receipt");
        JsonObject pin=object(required(receipt,"pin"),Set.of("city","cityRevision","heart","heartRevision","dimension","root"),"pin");
        JsonObject position=object(required(pin,"root"),Set.of("x","y","z"),"root position");
        Pin decodedPin=new Pin(text(pin,"city"),natural(pin,"cityRevision"),text(pin,"heart"),natural(pin,"heartRevision"),text(pin,"dimension"),
                new EcologyTerrain.Pos(signed(position,"x"),signed(position,"y"),signed(position,"z")));
        Receipt decodedReceipt=new Receipt(natural(receipt,"revision"),decodedPin,natural(receipt,"claimedAtActiveTicks"),natural(receipt,"eligibleAfterActiveTicks"),bool(receipt,"permanent"));
        return new State(revision,cooldown,decodedReceipt);
    }

    private static void atomicCommit(Path file,byte[] bytes)throws IOException {
        Path parent=file.getParent();if(parent==null)throw new IOException("Heart defiance state requires a parent directory: "+file);
        Files.createDirectories(parent);
        if(Files.exists(file))new HeartDefianceStore(file).load();
        Path temporary=Files.createTempFile(parent,file.getFileName().toString(),".tmp"),backupTemporary=null;
        try{
            forceWrite(temporary,bytes);
            if(Files.exists(file)){
                Path backup=file.resolveSibling(file.getFileName()+".bak");backupTemporary=Files.createTempFile(parent,backup.getFileName().toString(),".tmp");
                Files.copy(file,backupTemporary,StandardCopyOption.REPLACE_EXISTING);force(backupTemporary);moveReplace(backupTemporary,backup);backupTemporary=null;
            }
            moveReplace(temporary,file);
        }finally{deleteQuietly(temporary);deleteQuietly(backupTemporary);}
    }
    private static void forceWrite(Path file,byte[] bytes)throws IOException {try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}}
    private static void force(Path file)throws IOException {try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}}
    private static void moveReplace(Path source,Path target)throws IOException {try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException failure){throw new IOException("Atomic replacement is not supported for Heart defiance state: "+target,failure);}}
    private static void deleteQuietly(Path file){if(file!=null)try{Files.deleteIfExists(file);}catch(IOException ignored){}}

    private static JsonElement required(JsonObject object,String key){JsonElement value=object.get(key);if(value==null)throw new IllegalArgumentException("Missing "+key);return value;}
    private static JsonObject object(JsonElement value,Set<String> keys,String label){if(value==null||!value.isJsonObject())throw new IllegalArgumentException(label+" must be an object");JsonObject object=value.getAsJsonObject();if(!object.keySet().equals(keys))throw new IllegalArgumentException("Unexpected "+label+" fields");return object;}
    private static String text(JsonObject object,String key){JsonElement value=required(object,key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be text");return value.getAsString();}
    private static boolean bool(JsonObject object,String key){JsonElement value=required(object,key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(key+" must be boolean");return value.getAsBoolean();}
    private static int integer(JsonObject object,String key){return Math.toIntExact(natural(object,key));}
    private static long natural(JsonObject object,String key){JsonElement value=required(object,key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException(key+" must be a canonical non-negative integer");try{return Long.parseLong(value.getAsString());}catch(NumberFormatException failure){throw new IllegalArgumentException(key+" is outside supported range",failure);}}
    private static int signed(JsonObject object,String key){JsonElement value=required(object,key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException(key+" must be a canonical integer");try{return Integer.parseInt(value.getAsString());}catch(NumberFormatException failure){throw new IllegalArgumentException(key+" is outside supported range",failure);}}
}
