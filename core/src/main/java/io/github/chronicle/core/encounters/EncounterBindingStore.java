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

/** Strict, revisioned map-binding file. It contains no coordinates or geometry snapshots. */
public final class EncounterBindingStore {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 128 * 1024;
    private static final int MAX_DEPTH = 8;
    private final Path file;
    private byte[] observed;

    public EncounterBindingStore(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("encounter binding file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    public synchronized EncounterBindingRegistry loadOrCreate() throws IOException {
        verifyDirectories(file.getParent());
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] initial = encode(EncounterBindingRegistry.empty()).getBytes(StandardCharsets.UTF_8);
            writeInitial(initial); observed = initial; return EncounterBindingRegistry.empty();
        }
        byte[] bytes = current();
        try { EncounterBindingRegistry result = decode(bytes); observed = bytes.clone(); return result; }
        catch (RuntimeException failure) { throw new IOException("encounter binding config is invalid and was preserved: " + file, failure); }
    }

    public synchronized EncounterBindingRegistry save(EncounterBindingRegistry current, EncounterBindingRegistry replacement) throws IOException {
        if (observed == null) throw new IllegalStateException("encounter bindings must be loaded before editing");
        Objects.requireNonNull(current, "current encounter bindings are required");
        Objects.requireNonNull(replacement, "replacement encounter bindings are required");
        if (replacement.revision() != current.revision() + 1) throw new IllegalArgumentException("encounter binding revision must advance once");
        byte[] old = current();
        if (!MessageDigest.isEqual(observed, old)) throw new IOException("encounter binding config changed outside Chronicle; reload before retrying");
        try { decode(old); } catch (RuntimeException failure) { throw new IOException("encounter binding config is invalid and was preserved: " + file, failure); }
        byte[] encoded = encode(replacement).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("encounter binding config exceeds 128 KiB");
        replaceDurably(old, encoded); observed = encoded.clone(); return replacement;
    }

    public static String encode(EncounterBindingRegistry registry) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION); root.addProperty("revision", registry.revision());
        JsonArray values = new JsonArray();
        for (EncounterBinding binding : registry.bindings().values()) {
            JsonObject value = new JsonObject(); value.addProperty("groupId", binding.groupId());
            value.addProperty("kind", binding.target().kind().name()); value.addProperty("siteId", binding.siteId());
            value.addProperty("siteRevision", binding.siteRevision()); values.add(value);
        }
        root.add("bindings", values); return root.toString();
    }

    public static EncounterBindingRegistry decode(String json) { return decode(json.getBytes(StandardCharsets.UTF_8)); }
    private static EncounterBindingRegistry decode(byte[] bytes) {
        String json = utf8(bytes); JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(json))) { reader.setStrictness(Strictness.STRICT); strict(reader, 0); if (reader.peek()!=JsonToken.END_DOCUMENT) throw new IllegalArgumentException("encounter binding JSON has trailing content"); }
        catch(IOException failure){throw new IllegalArgumentException("unable to read encounter binding JSON",failure);}
        try (JsonReader reader = new JsonReader(new StringReader(json))) { reader.setStrictness(Strictness.STRICT); parsed=JsonParser.parseReader(reader); }
        catch(IOException failure){throw new IllegalArgumentException("unable to parse encounter binding JSON",failure);}
        JsonObject root=object(parsed,"root");fields(root,Set.of("schemaVersion","revision","bindings"),"root");
        if(integer(required(root,"schemaVersion"),"schemaVersion")!=SCHEMA_VERSION)throw new IllegalArgumentException("unsupported encounter binding schemaVersion");
        JsonArray values=array(required(root,"bindings"),"bindings");if(values.size()>EncounterGroupRegistry.MAX_GROUPS)throw new IllegalArgumentException("too many encounter bindings");
        Map<String,EncounterBinding> result=new LinkedHashMap<>();
        for(JsonElement element:values){JsonObject value=object(element,"binding");fields(value,Set.of("groupId","kind","siteId","siteRevision"),"binding");String group=string(required(value,"groupId"),"groupId");EncounterBinding binding=new EncounterBinding(group,new EncounterTarget(enumeration(required(value,"kind"),EncounterTarget.Kind.class,"kind"),string(required(value,"siteId"),"siteId"),number(required(value,"siteRevision"),"siteRevision")));if(result.putIfAbsent(group,binding)!=null)throw new IllegalArgumentException("duplicate encounter binding");}
        return new EncounterBindingRegistry(number(required(root,"revision"),"revision"),result);
    }

    private byte[] current() throws IOException { verifyDirectories(file.getParent()); if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("encounter binding path is not a regular non-link file: "+file);if(Files.size(file)>MAX_FILE_BYTES)throw new IOException("encounter binding config exceeds 128 KiB");return Files.readAllBytes(file); }
    private void writeInitial(byte[] bytes)throws IOException{prepare(file.getParent());Path temp=Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp");try{force(temp,bytes);try{Files.createLink(file,temp);}catch(FileAlreadyExistsException race){throw new IOException("encounter binding config appeared while creating it",race);}}finally{Files.deleteIfExists(temp);}}
    private void replaceDurably(byte[] old,byte[] bytes)throws IOException{prepare(file.getParent());Path temp=Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp"),backupTemp=null;try{force(temp,bytes);verify(old);Path backup=file.resolveSibling(file.getFileName()+".bak");if(Files.exists(backup,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(backup)||!Files.isRegularFile(backup,LinkOption.NOFOLLOW_LINKS)))throw new IOException("encounter binding backup is not a regular non-link file");backupTemp=Files.createTempFile(file.getParent(),backup.getFileName().toString(),".tmp");force(backupTemp,old);move(backupTemp,backup);backupTemp=null;verify(old);move(temp,file);}finally{Files.deleteIfExists(temp);if(backupTemp!=null)Files.deleteIfExists(backupTemp);}}
    private void verify(byte[] expected)throws IOException{if(!MessageDigest.isEqual(expected,current()))throw new IOException("encounter binding config changed outside Chronicle during save; reload before retrying");}
    private static void verifyDirectories(Path directory)throws IOException{Path absolute=directory.toAbsolutePath().normalize(),cursor=absolute.getRoot();if(cursor==null)throw new IOException("encounter binding path has no filesystem root");for(Path part:absolute){cursor=cursor.resolve(part);if(Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(cursor)||!Files.isDirectory(cursor,LinkOption.NOFOLLOW_LINKS)))throw new IOException("encounter binding directory is a symbolic link or special file: "+cursor);}}
    private static void prepare(Path directory)throws IOException{verifyDirectories(directory);if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))Files.createDirectories(directory);verifyDirectories(directory);}
    private static void force(Path path,byte[] bytes)throws IOException{try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}}
    private static void move(Path source,Path target)throws IOException{try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException failure){throw new IOException("atomic encounter binding replacement is unsupported",failure);}}
    private static String utf8(byte[] bytes){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException failure){throw new IllegalArgumentException("encounter binding JSON is not valid UTF-8",failure);}}
    private static void strict(JsonReader reader,int depth)throws IOException{if(depth>MAX_DEPTH)throw new IllegalArgumentException("encounter binding JSON nesting is too deep");switch(reader.peek()){case BEGIN_OBJECT->{reader.beginObject();Set<String>names=new HashSet<>();while(reader.hasNext()){String name=reader.nextName();if(!names.add(name))throw new IllegalArgumentException("duplicate JSON member: "+name);strict(reader,depth+1);}reader.endObject();}case BEGIN_ARRAY->{reader.beginArray();while(reader.hasNext())strict(reader,depth+1);reader.endArray();}case STRING,NUMBER->reader.nextString();case BOOLEAN->reader.nextBoolean();case NULL->reader.nextNull();default->throw new IllegalArgumentException("unexpected encounter binding JSON token");}}
    private static JsonObject object(JsonElement value,String name){if(value==null||!value.isJsonObject())throw new IllegalArgumentException(name+" must be an object");return value.getAsJsonObject();}private static JsonArray array(JsonElement value,String name){if(value==null||!value.isJsonArray())throw new IllegalArgumentException(name+" must be an array");return value.getAsJsonArray();}private static JsonElement required(JsonObject object,String name){if(!object.has(name)||object.get(name).isJsonNull())throw new IllegalArgumentException("missing "+name);return object.get(name);}private static void fields(JsonObject object,Set<String>allowed,String name){if(!object.keySet().equals(allowed))throw new IllegalArgumentException(name+" fields do not match schema");}private static String string(JsonElement value,String name){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(name+" must be a string");return value.getAsString();}private static int integer(JsonElement value,String name){long result=number(value,name);if(result<Integer.MIN_VALUE||result>Integer.MAX_VALUE)throw new IllegalArgumentException(name+" outside integer range");return(int)result;}private static long number(JsonElement value,String name){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException(name+" must be an integer");try{return Long.parseLong(value.getAsString());}catch(NumberFormatException failure){throw new IllegalArgumentException(name+" outside long range",failure);}}private static <T extends Enum<T>>T enumeration(JsonElement value,Class<T>type,String name){String text=string(value,name);try{return Enum.valueOf(type,text);}catch(RuntimeException failure){throw new IllegalArgumentException("unknown "+name,failure);}}
}
