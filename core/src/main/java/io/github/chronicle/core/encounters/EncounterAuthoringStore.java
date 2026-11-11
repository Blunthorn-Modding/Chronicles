package io.github.chronicle.core.encounters;

import com.google.gson.*;
import com.google.gson.stream.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Atomically publishes the logically separate group and map-binding registries as one catalog. */
public final class EncounterAuthoringStore {
    public static final int SCHEMA_VERSION=1;
    public static final long MAX_FILE_BYTES=384*1024;
    private static final int MAX_DEPTH=20;
    private static final byte[] MARKER="chronicle-encounter-authoring-v1\n".getBytes(StandardCharsets.UTF_8);
    private final Path file,marker,legacyGroups,legacyBindings;
    private final EncounterTypeAllowlist allowlist;
    private final BeforePublish beforePublish;
    private byte[] observed;

    @FunctionalInterface interface BeforePublish { void run() throws IOException; }

    public EncounterAuthoringStore(Path file,Path marker,Path legacyGroups,Path legacyBindings,EncounterTypeAllowlist allowlist){
        this(file,marker,legacyGroups,legacyBindings,allowlist,()->{});
    }
    EncounterAuthoringStore(Path file,Path marker,Path legacyGroups,Path legacyBindings,EncounterTypeAllowlist allowlist,BeforePublish beforePublish){
        if(file==null||file.getFileName()==null||marker==null||marker.getFileName()==null)throw new IllegalArgumentException("encounter authoring paths are required");
        this.file=file.toAbsolutePath().normalize();this.marker=marker.toAbsolutePath().normalize();this.legacyGroups=Objects.requireNonNull(legacyGroups).toAbsolutePath().normalize();this.legacyBindings=Objects.requireNonNull(legacyBindings).toAbsolutePath().normalize();this.allowlist=Objects.requireNonNull(allowlist);this.beforePublish=Objects.requireNonNull(beforePublish);
        if(!this.file.getParent().equals(this.marker.getParent())||!this.file.getParent().equals(this.legacyGroups.getParent())||!this.file.getParent().equals(this.legacyBindings.getParent()))throw new IllegalArgumentException("encounter authoring files must share one directory");
    }

    public synchronized EncounterAuthoringCatalog loadOrCreate(EncounterGroupRegistry defaults)throws IOException{
        validateGroups(defaults);verifyDirectories(file.getParent());boolean catalogExists=Files.exists(file,LinkOption.NOFOLLOW_LINKS),marked=Files.exists(marker,LinkOption.NOFOLLOW_LINKS);
        if(!catalogExists&&marked)throw new IOException("initialized encounter authoring catalog is missing and was not recreated: "+file);
        if(catalogExists){byte[] bytes=current();EncounterAuthoringCatalog result=decodeChecked(bytes,EncounterGroupRegistry.empty());observed=bytes.clone();if(!marked)writeMarker();else verifyMarker();return result;}
        EncounterAuthoringCatalog initial;
        boolean oldGroups=Files.exists(legacyGroups,LinkOption.NOFOLLOW_LINKS),oldBindings=Files.exists(legacyBindings,LinkOption.NOFOLLOW_LINKS);
        if(oldGroups||oldBindings){if(!oldGroups||!oldBindings)throw new IOException("legacy encounter group/binding files are asymmetric; no catalog was imported");initial=importLegacy(defaults);}
        else initial=new EncounterAuthoringCatalog(defaults,EncounterBindingRegistry.empty());
        byte[] bytes=encode(initial);writeInitial(bytes);observed=bytes.clone();writeMarker();return initial;
    }

    public synchronized EncounterAuthoringCatalog save(EncounterAuthoringCatalog current,EncounterAuthoringCatalog replacement)throws IOException{
        if(observed==null)throw new IllegalStateException("encounter authoring catalog must be loaded before editing");Objects.requireNonNull(current);Objects.requireNonNull(replacement);validateCatalog(replacement);
        if(replacement.groups().revision()!=current.groups().revision()+1||replacement.bindings().revision()!=current.bindings().revision()+1)throw new IllegalArgumentException("both encounter catalog revisions must advance once");
        verifyMarker();byte[] old=current();if(!MessageDigest.isEqual(observed,old))throw new IOException("encounter authoring catalog changed outside Chronicle; reload before retrying");decodeChecked(old,EncounterGroupRegistry.empty());byte[] bytes=encode(replacement);replaceDurably(old,bytes);observed=bytes.clone();return replacement;
    }

    private EncounterAuthoringCatalog importLegacy(EncounterGroupRegistry defaults)throws IOException{
        byte[] groupBytes=readLegacy(legacyGroups,EncounterGroupStore.MAX_FILE_BYTES,"legacy encounter groups"),bindingBytes=readLegacy(legacyBindings,EncounterBindingStore.MAX_FILE_BYTES,"legacy encounter bindings");
        try{EncounterGroupRegistry configured=EncounterGroupStore.decode(utf8(groupBytes));TreeMap<String,EncounterGroup> merged=new TreeMap<>(defaults.groups());merged.putAll(configured.groups());EncounterAuthoringCatalog result=new EncounterAuthoringCatalog(new EncounterGroupRegistry(configured.revision(),merged),EncounterBindingStore.decode(utf8(bindingBytes)));validateCatalog(result);return result;}
        catch(RuntimeException failure){throw new IOException("legacy encounter group/binding files are invalid and were preserved",failure);}
    }
    private byte[] readLegacy(Path path,long max,String label)throws IOException{if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException(label+" path is not a regular non-link file: "+path);if(Files.size(path)>max)throw new IOException(label+" exceeds its size limit");return Files.readAllBytes(path);}
    private EncounterAuthoringCatalog decodeChecked(byte[] bytes,EncounterGroupRegistry defaults)throws IOException{try{EncounterAuthoringCatalog configured=decode(bytes);TreeMap<String,EncounterGroup> merged=new TreeMap<>(defaults.groups());merged.putAll(configured.groups().groups());EncounterAuthoringCatalog result=new EncounterAuthoringCatalog(new EncounterGroupRegistry(configured.groups().revision(),merged),configured.bindings());validateCatalog(result);return result;}catch(RuntimeException failure){throw new IOException("encounter authoring catalog is invalid and was preserved: "+file,failure);}}
    private void validateCatalog(EncounterAuthoringCatalog catalog){validateGroups(catalog.groups());new EncounterAuthoringCatalog(catalog.groups(),catalog.bindings());}
    private void validateGroups(EncounterGroupRegistry groups){for(EncounterGroup group:groups.groups().values())group.requireAllowed(allowlist);}

    public static byte[] encode(EncounterAuthoringCatalog catalog){JsonObject root=new JsonObject();root.addProperty("schemaVersion",SCHEMA_VERSION);root.add("groups",JsonParser.parseString(EncounterGroupStore.encode(catalog.groups())));root.add("bindings",JsonParser.parseString(EncounterBindingStore.encode(catalog.bindings())));byte[] bytes=root.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_FILE_BYTES)throw new IllegalArgumentException("encounter authoring catalog exceeds 384 KiB");return bytes;}
    public static EncounterAuthoringCatalog decode(byte[] bytes){String json=utf8(bytes);try(JsonReader reader=new JsonReader(new StringReader(json))){reader.setStrictness(Strictness.STRICT);strict(reader,0);if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("encounter authoring JSON has trailing content");}catch(IOException failure){throw new IllegalArgumentException("unable to read encounter authoring JSON",failure);}JsonObject root;try(JsonReader reader=new JsonReader(new StringReader(json))){reader.setStrictness(Strictness.STRICT);root=JsonParser.parseReader(reader).getAsJsonObject();}catch(Exception failure){throw new IllegalArgumentException("unable to parse encounter authoring JSON",failure);}if(!root.keySet().equals(Set.of("schemaVersion","groups","bindings")))throw new IllegalArgumentException("encounter authoring root fields do not match schema");if(!root.get("schemaVersion").isJsonPrimitive()||!root.getAsJsonPrimitive("schemaVersion").isNumber()||!root.get("schemaVersion").getAsString().equals(Integer.toString(SCHEMA_VERSION)))throw new IllegalArgumentException("unsupported encounter authoring schemaVersion");return new EncounterAuthoringCatalog(EncounterGroupStore.decode(root.get("groups").toString()),EncounterBindingStore.decode(root.get("bindings").toString()));}

    private byte[] current()throws IOException{verifyDirectories(file.getParent());if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("encounter authoring path is not a regular non-link file: "+file);if(Files.size(file)>MAX_FILE_BYTES)throw new IOException("encounter authoring catalog exceeds 384 KiB");return Files.readAllBytes(file);}
    private void writeInitial(byte[] bytes)throws IOException{prepare(file.getParent());Path temp=Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp");try{force(temp,bytes);try{Files.createLink(file,temp);}catch(FileAlreadyExistsException race){throw new IOException("encounter authoring catalog appeared while creating it",race);}}finally{Files.deleteIfExists(temp);}}
    private void writeMarker()throws IOException{prepare(marker.getParent());if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)){verifyMarker();return;}Path temp=Files.createTempFile(marker.getParent(),marker.getFileName().toString(),".tmp");try{force(temp,MARKER);try{Files.createLink(marker,temp);}catch(FileAlreadyExistsException race){verifyMarker();}}finally{Files.deleteIfExists(temp);}}
    private void verifyMarker()throws IOException{if(Files.isSymbolicLink(marker)||!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)||Files.size(marker)!=MARKER.length||!MessageDigest.isEqual(MARKER,Files.readAllBytes(marker)))throw new IOException("encounter authoring initialized marker is invalid: "+marker);}
    private void replaceDurably(byte[] old,byte[] bytes)throws IOException{prepare(file.getParent());Path temp=Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp"),backupTemp=null;try{force(temp,bytes);verify(old);Path backup=file.resolveSibling(file.getFileName()+".bak");if(Files.exists(backup,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(backup)||!Files.isRegularFile(backup,LinkOption.NOFOLLOW_LINKS)))throw new IOException("encounter authoring backup is not a regular non-link file");backupTemp=Files.createTempFile(file.getParent(),backup.getFileName().toString(),".tmp");force(backupTemp,old);move(backupTemp,backup);backupTemp=null;verify(old);beforePublish.run();move(temp,file);}finally{Files.deleteIfExists(temp);if(backupTemp!=null)Files.deleteIfExists(backupTemp);}}
    private void verify(byte[] expected)throws IOException{if(!MessageDigest.isEqual(expected,current()))throw new IOException("encounter authoring catalog changed outside Chronicle during save; reload before retrying");}
    private static void verifyDirectories(Path directory)throws IOException{Path absolute=directory.toAbsolutePath().normalize(),cursor=absolute.getRoot();if(cursor==null)throw new IOException("encounter authoring path has no filesystem root");for(Path part:absolute){cursor=cursor.resolve(part);if(Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(cursor)||!Files.isDirectory(cursor,LinkOption.NOFOLLOW_LINKS)))throw new IOException("encounter authoring directory is a symbolic link or special file: "+cursor);}}
    private static void prepare(Path directory)throws IOException{verifyDirectories(directory);if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))Files.createDirectories(directory);verifyDirectories(directory);}
    private static void force(Path path,byte[] bytes)throws IOException{try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}}
    private static void move(Path source,Path target)throws IOException{try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException failure){throw new IOException("atomic encounter authoring replacement is unsupported",failure);}}
    private static String utf8(byte[] bytes){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException failure){throw new IllegalArgumentException("encounter authoring JSON is not valid UTF-8",failure);}}
    private static void strict(JsonReader reader,int depth)throws IOException{if(depth>MAX_DEPTH)throw new IllegalArgumentException("encounter authoring JSON nesting is too deep");switch(reader.peek()){case BEGIN_OBJECT->{reader.beginObject();Set<String> names=new HashSet<>();while(reader.hasNext()){String name=reader.nextName();if(!names.add(name))throw new IllegalArgumentException("duplicate JSON member: "+name);strict(reader,depth+1);}reader.endObject();}case BEGIN_ARRAY->{reader.beginArray();while(reader.hasNext())strict(reader,depth+1);reader.endArray();}case STRING,NUMBER->reader.nextString();case BOOLEAN->reader.nextBoolean();case NULL->reader.nextNull();default->throw new IllegalArgumentException("unexpected encounter authoring JSON token");}}
}
