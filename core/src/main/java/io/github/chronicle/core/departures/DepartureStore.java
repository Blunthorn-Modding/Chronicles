package io.github.chronicle.core.departures;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.books.BookConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict, exclusively locked and atomically replaced world-scoped departure authority. */
public final class DepartureStore implements DepartureRepository, AutoCloseable {
    public static final int SCHEMA_VERSION=1;
    public static final long MAX_FILE_BYTES=16L*1024*1024;
    private static final byte[] INITIALIZED="chronicle-departures-schema-1\n".getBytes(StandardCharsets.UTF_8);
    private static final Set<String> ROOT=Set.of("schemaVersion","revision","definitions","tombstones","used","operations","departed","concluded");
    private static final Set<String> DEFINITION=Set.of("id","name","revision","exactBlockAnchor","boardingRegion","firstText","finalText","areaLink","singleUse");
    private static final Set<String> SITE=Set.of("id","revision");
    private static final Set<String> LINK=Set.of("areaId","requestedState");
    private static final Set<String> EFFECT=Set.of("operationId","areaId","requestedState","snapshotSha256");
    private static final Set<String> OPERATION=Set.of("operationId","definition","actor","onlineParticipants","concludeCampaign","preparedAreaEffect","stage","playerEffects","areaEffects","conclusion","createdAt");
    private static final Set<String> RECEIPT=Set.of("disposition","evidence");
    private static final Set<String> USED=Set.of("definitionId","operationId");
    private static final Set<String> DEPARTED=Set.of("playerId","operationId");
    private static final Set<String> OUTCOME=Set.of("operationId","receipt","concludedAt");

    private final Path file,marker,lockPath;
    private final Map<String,DepartureDefinition> defaults;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private byte[] observedBytes;
    private DepartureState observed;
    private boolean closed;

    public DepartureStore(Path file,Collection<DepartureDefinition> defaults)throws IOException{
        if(file==null||file.getFileName()==null)throw new IllegalArgumentException("departure data file is required");
        this.file=file.toAbsolutePath().normalize();this.marker=sibling(".initialized");this.lockPath=sibling(".lock");
        LinkedHashMap<String,DepartureDefinition> checked=new LinkedHashMap<>();
        if(defaults!=null)for(DepartureDefinition definition:defaults){if(definition==null||definition.revision()!=1||checked.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("departure defaults must be unique revision-1 definitions");}
        if(checked.size()>DepartureState.MAX_DEFINITIONS)throw new IllegalArgumentException("too many departure defaults");this.defaults=Map.copyOf(checked);
        Path parent=parent();Files.createDirectories(parent);if(Files.isSymbolicLink(lockPath))throw new IOException("departure lock cannot be a symbolic link: "+lockPath);
        FileChannel channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock acquired=null;
        try{try{acquired=channel.tryLock();}catch(OverlappingFileLockException e){throw new IOException("departure authority is already open: "+file,e);}if(acquired==null)throw new IOException("departure authority is already open: "+file);}
        catch(IOException|RuntimeException e){channel.close();throw e;}lockChannel=channel;lock=acquired;
    }

    public DepartureStore(Path file)throws IOException{this(file,Set.of());}

    /** Defaults are materialized only when neither marker nor authority has ever existed. */
    @Override public synchronized DepartureState load()throws IOException{
        requireOpen();if(observed!=null){verifyUnchanged();return observed;}
        boolean markerExists=Files.exists(marker,LinkOption.NOFOLLOW_LINKS),fileExists=Files.exists(file,LinkOption.NOFOLLOW_LINKS);
        if(!markerExists){
            if(fileExists)throw new IOException("departure authority exists without its initialization marker: "+file);
            if(Files.exists(sibling(".bak"),LinkOption.NOFOLLOW_LINKS))throw new IOException("departure backup exists without its authority; manual recovery is required: "+file);
            writeMarker();DepartureState initial=new DepartureState(0,defaults,Set.of(),Map.of(),Map.of(),Map.of(),Optional.empty());byte[] bytes=encode(initial).getBytes(StandardCharsets.UTF_8);writeInitial(bytes);return observe(bytes,initial);
        }
        verifyMarker();if(!fileExists)throw new IOException("initialized departure authority is missing; manual recovery is required: "+file);
        byte[] bytes=currentBytes();return observe(bytes,decodeChecked(bytes));
    }

    @Override public synchronized void save(DepartureState replacement)throws IOException{
        requireOpen();if(replacement==null)throw new IllegalArgumentException("replacement departure state is required");if(observed==null||observedBytes==null)throw new IllegalStateException("departure authority must be loaded before saving");
        if(replacement.revision()!=Math.addExact(observed.revision(),1))throw new IllegalStateException("departure replacement revision must advance exactly once");
        verifyUnchanged();byte[] next=encode(replacement).getBytes(StandardCharsets.UTF_8);if(next.length>MAX_FILE_BYTES)throw new IOException("departure authority exceeds 16 MiB");replaceDurably(observedBytes,next);observe(next,replacement);
    }

    public synchronized void verifyUnchanged()throws IOException{requireOpen();if(observed==null||observedBytes==null)throw new IllegalStateException("departure authority must be loaded before verification");verifyMarker();byte[] now=currentBytes();if(!MessageDigest.isEqual(now,observedBytes))throw new IOException("departure authority changed outside Chronicle; refusing overwrite: "+file);decodeChecked(now);}

    @Override public synchronized void close()throws IOException{if(closed)return;closed=true;IOException failure=null;try{lock.release();}catch(IOException e){failure=e;}try{lockChannel.close();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}if(failure!=null)throw failure;}

    static String encode(DepartureState state){
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",SCHEMA_VERSION);root.addProperty("revision",state.revision());JsonArray definitions=new JsonArray();state.definitions().values().forEach(value->definitions.add(definition(value)));root.add("definitions",definitions);
        JsonArray tombstones=new JsonArray();state.tombstones().forEach(tombstones::add);root.add("tombstones",tombstones);
        JsonArray used=new JsonArray();state.usedByDefinition().forEach((definitionId,operationId)->{JsonObject value=new JsonObject();value.addProperty("definitionId",definitionId);value.addProperty("operationId",operationId.toString());used.add(value);});root.add("used",used);
        JsonArray operations=new JsonArray();state.operations().values().forEach(value->operations.add(operation(value)));root.add("operations",operations);
        JsonArray departed=new JsonArray();state.departedByPlayer().forEach((playerId,operationId)->{JsonObject value=new JsonObject();value.addProperty("playerId",playerId.toString());value.addProperty("operationId",operationId.toString());departed.add(value);});root.add("departed",departed);
        root.add("concluded",state.concluded().<JsonElement>map(DepartureStore::outcome).orElse(JsonNull.INSTANCE));return root.toString();
    }

    static DepartureState decode(String source){
        if(source==null||source.getBytes(StandardCharsets.UTF_8).length>MAX_FILE_BYTES)throw new IllegalArgumentException("departure authority is absent or too large");JsonObject root=BookConfig.strictObject(source);exact(root,ROOT,"root");if(integer(root,"schemaVersion")!=SCHEMA_VERSION)throw new IllegalArgumentException("unsupported departure schemaVersion");long revision=longNumber(root,"revision");
        JsonArray definitionValues=array(root,"definitions",DepartureState.MAX_DEFINITIONS);LinkedHashMap<String,DepartureDefinition> definitions=new LinkedHashMap<>();for(JsonElement element:definitionValues){DepartureDefinition definition=definition(object(element,"definition"));if(definitions.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("duplicate departure definition ID");}
        JsonArray tombstoneValues=array(root,"tombstones",DepartureState.MAX_HISTORY);LinkedHashSet<String> tombstones=new LinkedHashSet<>();for(JsonElement element:tombstoneValues){String id=string(element,"tombstone",64);if(!tombstones.add(id))throw new IllegalArgumentException("duplicate departure tombstone");}
        JsonArray usedValues=array(root,"used",DepartureState.MAX_DEFINITIONS);LinkedHashMap<String,UUID> used=new LinkedHashMap<>();for(JsonElement element:usedValues){JsonObject value=object(element,"used entry");exact(value,USED,"used entry");String id=string(value.get("definitionId"),"used definition ID",64);if(used.putIfAbsent(id,uuid(value,"operationId"))!=null)throw new IllegalArgumentException("duplicate used departure ID");}
        JsonArray operationValues=array(root,"operations",DepartureState.MAX_HISTORY);LinkedHashMap<UUID,DepartureOperation> operations=new LinkedHashMap<>();for(JsonElement element:operationValues){DepartureOperation operation=operation(object(element,"operation"));if(operations.putIfAbsent(operation.operationId(),operation)!=null)throw new IllegalArgumentException("duplicate departure operation UUID");}
        JsonArray departedValues=array(root,"departed",DepartureState.MAX_DEPARTED_PLAYERS);LinkedHashMap<UUID,UUID> departed=new LinkedHashMap<>();for(JsonElement element:departedValues){JsonObject value=object(element,"departed entry");exact(value,DEPARTED,"departed entry");UUID player=uuid(value,"playerId");if(departed.putIfAbsent(player,uuid(value,"operationId"))!=null)throw new IllegalArgumentException("duplicate departed player UUID");}
        Optional<CampaignConclusion> concluded=root.get("concluded").isJsonNull()?Optional.empty():Optional.of(outcome(object(root.get("concluded"),"conclusion")));
        return new DepartureState(revision,definitions,tombstones,used,operations,departed,concluded);
    }

    private static JsonObject definition(DepartureDefinition value){JsonObject result=new JsonObject();result.addProperty("id",value.id());result.addProperty("name",value.name());result.addProperty("revision",value.revision());result.add("exactBlockAnchor",value.exactBlockAnchor().<JsonElement>map(DepartureStore::site).orElse(JsonNull.INSTANCE));result.add("boardingRegion",value.boardingRegion().<JsonElement>map(DepartureStore::site).orElse(JsonNull.INSTANCE));result.addProperty("firstText",value.firstText());result.addProperty("finalText",value.finalText());result.add("areaLink",value.areaLink().<JsonElement>map(DepartureStore::link).orElse(JsonNull.INSTANCE));result.addProperty("singleUse",value.singleUse());return result;}
    private static DepartureDefinition definition(JsonObject value){exact(value,DEFINITION,"definition");return new DepartureDefinition(string(value,"id",64),string(value,"name",DepartureDefinition.MAX_NAME_BYTES),longNumber(value,"revision"),optional(value,"exactBlockAnchor",DepartureStore::site),optional(value,"boardingRegion",DepartureStore::site),string(value,"firstText",DepartureDefinition.MAX_TEXT_BYTES),string(value,"finalText",DepartureDefinition.MAX_TEXT_BYTES),optional(value,"areaLink",DepartureStore::link),bool(value,"singleUse"));}
    private static JsonObject site(SiteRef value){JsonObject result=new JsonObject();result.addProperty("id",value.id());result.addProperty("revision",value.revision());return result;}
    private static SiteRef site(JsonObject value){exact(value,SITE,"site reference");return new SiteRef(string(value,"id",64),longNumber(value,"revision"));}
    private static JsonObject link(AreaLink value){JsonObject result=new JsonObject();result.addProperty("areaId",value.areaId());result.addProperty("requestedState",value.requestedState().name());return result;}
    private static AreaLink link(JsonObject value){exact(value,LINK,"area link");return new AreaLink(string(value,"areaId",64),enumeration(value,"requestedState",AreaState.class));}
    private static JsonObject effect(PreparedAreaEffect value){JsonObject result=new JsonObject();result.addProperty("operationId",value.operationId().toString());result.addProperty("areaId",value.areaId());result.addProperty("requestedState",value.requestedState().name());result.addProperty("snapshotSha256",value.snapshotSha256());return result;}
    private static PreparedAreaEffect effect(JsonObject value){exact(value,EFFECT,"prepared area effect");return new PreparedAreaEffect(uuid(value,"operationId"),string(value,"areaId",64),enumeration(value,"requestedState",AreaState.class),string(value,"snapshotSha256",64));}
    private static JsonObject receipt(DepartureReceipt value){JsonObject result=new JsonObject();result.addProperty("disposition",value.disposition().name());result.addProperty("evidence",value.evidence());return result;}
    private static DepartureReceipt receipt(JsonObject value){exact(value,RECEIPT,"receipt");return new DepartureReceipt(enumeration(value,"disposition",DepartureReceipt.Disposition.class),string(value,"evidence",512));}
    private static JsonObject operation(DepartureOperation value){JsonObject result=new JsonObject();result.addProperty("operationId",value.operationId().toString());result.add("definition",definition(value.definition()));result.addProperty("actor",value.actor().toString());JsonArray roster=new JsonArray();value.onlineParticipants().forEach(player->roster.add(player.toString()));result.add("onlineParticipants",roster);result.addProperty("concludeCampaign",value.concludeCampaign());result.add("preparedAreaEffect",value.preparedAreaEffect().<JsonElement>map(DepartureStore::effect).orElse(JsonNull.INSTANCE));result.addProperty("stage",value.stage().name());result.add("playerEffects",value.playerEffects().<JsonElement>map(DepartureStore::receipt).orElse(JsonNull.INSTANCE));result.add("areaEffects",value.areaEffects().<JsonElement>map(DepartureStore::receipt).orElse(JsonNull.INSTANCE));result.add("conclusion",value.conclusion().<JsonElement>map(DepartureStore::receipt).orElse(JsonNull.INSTANCE));result.addProperty("createdAt",value.createdAt());return result;}
    private static DepartureOperation operation(JsonObject value){exact(value,OPERATION,"operation");JsonArray rosterValues=array(value,"onlineParticipants",DepartureIntent.MAX_PARTICIPANTS);java.util.ArrayList<UUID> roster=new java.util.ArrayList<>(rosterValues.size());for(JsonElement element:rosterValues)roster.add(uuid(element,"online participant"));return new DepartureOperation(uuid(value,"operationId"),definition(object(value.get("definition"),"pinned definition")),uuid(value,"actor"),roster,bool(value,"concludeCampaign"),optional(value,"preparedAreaEffect",DepartureStore::effect),enumeration(value,"stage",DepartureOperation.Stage.class),optional(value,"playerEffects",DepartureStore::receipt),optional(value,"areaEffects",DepartureStore::receipt),optional(value,"conclusion",DepartureStore::receipt),string(value,"createdAt",128));}
    private static JsonObject outcome(CampaignConclusion value){JsonObject result=new JsonObject();result.addProperty("operationId",value.operationId().toString());result.add("receipt",receipt(value.receipt()));result.addProperty("concludedAt",value.concludedAt());return result;}
    private static CampaignConclusion outcome(JsonObject value){exact(value,OUTCOME,"campaign conclusion");return new CampaignConclusion(uuid(value,"operationId"),receipt(object(value.get("receipt"),"conclusion receipt")),string(value,"concludedAt",128));}

    private DepartureState decodeChecked(byte[] bytes)throws IOException{try{return decode(new String(bytes,StandardCharsets.UTF_8));}catch(RuntimeException e){throw new IOException("departure authority is invalid and was preserved: "+file+" ("+e.getMessage()+")",e);}}
    private DepartureState observe(byte[] bytes,DepartureState value){observedBytes=bytes.clone();observed=value;return value;}
    private byte[] currentBytes()throws IOException{if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("departure authority is not a regular file: "+file);long size=Files.size(file);if(size<1||size>MAX_FILE_BYTES)throw new IOException("departure authority size is outside limits: "+file);byte[] bytes=Files.readAllBytes(file);if(bytes.length>MAX_FILE_BYTES)throw new IOException("departure authority exceeds 16 MiB: "+file);return bytes;}
    private void writeMarker()throws IOException{if(Files.isSymbolicLink(marker))throw new IOException("departure marker cannot be a symbolic link: "+marker);try(FileChannel channel=FileChannel.open(marker,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){writeAndForce(channel,INITIALIZED);}catch(FileAlreadyExistsException e){throw new IOException("departure marker appeared concurrently: "+marker,e);}}
    private void verifyMarker()throws IOException{if(Files.isSymbolicLink(marker)||!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)||Files.size(marker)!=INITIALIZED.length||!MessageDigest.isEqual(Files.readAllBytes(marker),INITIALIZED))throw new IOException("departure initialization marker is invalid: "+marker);}
    private void writeInitial(byte[] bytes)throws IOException{Path temporary=Files.createTempFile(parent(),file.getFileName().toString(),".tmp");try{forceWrite(temporary,bytes);try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);}catch(FileAlreadyExistsException e){throw new IOException("departure authority appeared while initializing",e);}catch(AtomicMoveNotSupportedException e){throw new IOException("atomic departure initialization is not supported",e);}}finally{Files.deleteIfExists(temporary);}}
    private void replaceDurably(byte[] old,byte[] next)throws IOException{Path parent=parent(),temporary=Files.createTempFile(parent,file.getFileName().toString(),".tmp"),backupTemporary=null;try{forceWrite(temporary,next);Path backup=sibling(".bak");if(Files.isSymbolicLink(backup))throw new IOException("departure backup cannot be a symbolic link: "+backup);backupTemporary=Files.createTempFile(parent,backup.getFileName().toString(),".tmp");forceWrite(backupTemporary,old);verifyExact(old);moveReplace(backupTemporary,backup);backupTemporary=null;verifyExact(old);moveReplace(temporary,file);}catch(IOException e){throw new IOException("unable to save departure authority; published authority was not changed",e);}finally{Files.deleteIfExists(temporary);if(backupTemporary!=null)Files.deleteIfExists(backupTemporary);}}
    private void verifyExact(byte[] expected)throws IOException{if(!MessageDigest.isEqual(currentBytes(),expected))throw new IOException("departure authority changed outside Chronicle");}
    private Path sibling(String suffix){return file.resolveSibling(file.getFileName()+suffix);}private Path parent()throws IOException{Path value=file.getParent();if(value==null)throw new IOException("departure authority has no parent directory");return value;}private void requireOpen(){if(closed)throw new IllegalStateException("departure store is closed");}
    private static void forceWrite(Path target,byte[] bytes)throws IOException{try(FileChannel channel=FileChannel.open(target,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){writeAndForce(channel,bytes);}}private static void writeAndForce(FileChannel channel,byte[] bytes)throws IOException{ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}private static void moveReplace(Path from,Path to)throws IOException{try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){throw new IOException("atomic replacement is not supported for departure authority: "+to,e);}}

    private interface Decoder<T>{T decode(JsonObject value);}private static <T> Optional<T> optional(JsonObject parent,String field,Decoder<T> decoder){JsonElement element=required(parent,field);return element.isJsonNull()?Optional.empty():Optional.of(decoder.decode(object(element,field)));}
    private static JsonElement required(JsonObject value,String field){JsonElement result=value.get(field);if(result==null)throw new IllegalArgumentException(field+" is required");return result;}private static JsonObject object(JsonElement value,String field){if(value==null||!value.isJsonObject())throw new IllegalArgumentException(field+" must be an object");return value.getAsJsonObject();}
    private static JsonArray array(JsonObject value,String field,int maximum){JsonElement element=required(value,field);if(!element.isJsonArray())throw new IllegalArgumentException(field+" must be an array");JsonArray result=element.getAsJsonArray();if(result.size()>maximum)throw new IllegalArgumentException(field+" exceeds its entry limit");return result;}
    private static void exact(JsonObject value,Set<String> fields,String label){if(!value.keySet().equals(fields))throw new IllegalArgumentException(label+" fields do not match schema");}
    private static String string(JsonObject value,String field,int maximumBytes){return string(required(value,field),field,maximumBytes);}private static String string(JsonElement value,String field,int maximumBytes){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(field+" must be a string");return DepartureValidation.text(value.getAsString(),0,maximumBytes,field);}
    private static long longNumber(JsonObject value,String field){JsonElement element=required(value,field);if(!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isNumber()||!element.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException(field+" must be a non-negative integer");try{return Long.parseLong(element.getAsString());}catch(NumberFormatException e){throw new IllegalArgumentException(field+" is outside range",e);}}
    private static int integer(JsonObject value,String field){long result=longNumber(value,field);if(result>Integer.MAX_VALUE)throw new IllegalArgumentException(field+" is outside range");return(int)result;}private static boolean bool(JsonObject value,String field){JsonElement element=required(value,field);if(!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(field+" must be boolean");return element.getAsBoolean();}
    private static UUID uuid(JsonObject value,String field){return uuid(required(value,field),field);}private static UUID uuid(JsonElement value,String field){try{return UUID.fromString(string(value,field,36));}catch(IllegalArgumentException e){throw new IllegalArgumentException(field+" must be a UUID",e);}}
    private static <E extends Enum<E>> E enumeration(JsonObject value,String field,Class<E> type){String text=string(value,field,64);try{return Enum.valueOf(type,text);}catch(IllegalArgumentException e){throw new IllegalArgumentException(field+" has an unknown value",e);}}
}
