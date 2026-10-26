package io.github.chronicle.core.roles;

import io.github.chronicle.core.spawn.SpawnBinding;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict YAML reader plus atomic, exact-byte checked campaign role configuration. */
public final class PlayerRoleConfig {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_YAML_DEPTH = 16;
    private static final int MAX_YAML_NODES = 8_192;
    private static final LoadSettings SETTINGS = LoadSettings.builder().setAllowDuplicateKeys(false).setMaxAliasesForCollections(0).setCodePointLimit((int) MAX_FILE_BYTES).build();
    private static final DumpSettings DUMP_SETTINGS = DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).setIndent(2).setIndicatorIndent(0).setSplitLines(false).build();
    @FunctionalInterface interface CreateCheck {void run() throws IOException;}
    private final CreateCheck beforeCreate;
    private final Path file; private byte[] observedBytes; private Map<String, PlayerRole> observed;
    public PlayerRoleConfig(Path file) {this(file,()->{});}
    PlayerRoleConfig(Path file,CreateCheck beforeCreate) {this.beforeCreate=java.util.Objects.requireNonNull(beforeCreate); if(file==null||file.getFileName()==null)throw new IllegalArgumentException("Player Role config file is required");this.file=file.toAbsolutePath().normalize(); }
    /** Existing files are authoritative: bundled defaults are used only for first creation. */
    public synchronized Map<String,PlayerRole> loadOrCreate(Map<String,PlayerRole> defaults) throws IOException {
        var valid=valid(defaults,"Player Role defaults");
        if(!Files.exists(file)){if(Files.exists(marker()))throw new IOException("Previously initialized Player Role config is missing; restore it instead of recreating defaults: "+file);byte[] bytes=encode(valid).getBytes(StandardCharsets.UTF_8); create(bytes);initializeMarker();return observe(bytes,valid);}
        byte[] bytes=current();try{var decoded=decode(new String(bytes,StandardCharsets.UTF_8));initializeMarker();return observe(bytes,decoded);}catch(RuntimeException e){throw new IOException("Player Role config is invalid and was not replaced: "+file+" ("+e.getMessage()+")",e);}
    }
    public synchronized Map<String,PlayerRole> replace(PlayerRole replacement,PlayerRole expected) throws IOException {
        if(replacement==null||expected==null||!replacement.id().equals(expected.id()))throw new IllegalArgumentException("replacement and matching expected Player Role are required");
        ready(expected); var candidate=new TreeMap<>(observed);candidate.put(replacement.id(),replacement);return publish(candidate);
    }
    public synchronized Map<String,PlayerRole> create(PlayerRole role) throws IOException {
        if(role==null)throw new IllegalArgumentException("Player Role is required");readyBytes();if(observed.containsKey(role.id()))throw new IllegalArgumentException("Player Role already exists");var candidate=new TreeMap<>(observed);candidate.put(role.id(),role);return publish(candidate);
    }
    public synchronized Map<String,PlayerRole> delete(String id,PlayerRole expected) throws IOException {
        if(id==null||expected==null||!id.equals(expected.id()))throw new IllegalArgumentException("matching expected Player Role is required");ready(expected);var candidate=new TreeMap<>(observed);candidate.remove(id);return publish(candidate);
    }
    private Path marker(){return file.resolveSibling(file.getFileName()+".initialized");}
    private void initializeMarker() throws IOException {
        Path marker=marker();byte[] expected="chronicle-player-roles-v1\n".getBytes(StandardCharsets.UTF_8);
        if(Files.exists(marker)){if(!Files.isRegularFile(marker)||Files.size(marker)>128||!MessageDigest.isEqual(Files.readAllBytes(marker),expected))throw new IOException("Player Role initialization marker is invalid; existing evidence retained");return;}
        Files.createDirectories(parent());
        try(FileChannel channel=FileChannel.open(marker,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            ByteBuffer buffer=ByteBuffer.wrap(expected);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
    }
    private void ready(PlayerRole expected) throws IOException { readyBytes();if(!expected.equals(observed.get(expected.id())))throw new IllegalStateException("Player Role changed since this editor opened; reopen it before saving"); }
    private void readyBytes() throws IOException { if(observed==null||observedBytes==null)throw new IllegalStateException("Player Role config must be loaded before editing");if(!MessageDigest.isEqual(observedBytes,current()))throw new IOException("Player Role config changed outside Chronicle; reload it before saving "+file); }
    private Map<String,PlayerRole> publish(Map<String,PlayerRole> candidate) throws IOException { var valid=valid(candidate,"Player Roles");byte[] old=current(), bytes=encode(valid).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_FILE_BYTES)throw new IOException("Player Role config serialization exceeds 2 MiB");replace(old,bytes);return observe(bytes,valid); }
    private Map<String,PlayerRole> observe(byte[] bytes,Map<String,PlayerRole> roles){observedBytes=bytes.clone();observed=valid(roles,"Player Roles");return observed;}
    private byte[] current() throws IOException {if(!Files.isRegularFile(file)||Files.size(file)>MAX_FILE_BYTES)throw new IOException("Player Role config is missing, not regular, or exceeds 2 MiB: "+file);return Files.readAllBytes(file);}
    private void create(byte[] bytes)throws IOException{Path parent=parent();Files.createDirectories(parent);Path tmp=Files.createTempFile(parent,file.getFileName().toString(),".tmp");try{write(tmp,bytes);try{beforeCreate.run();Files.createLink(file,tmp);}catch(FileAlreadyExistsException e){throw new IOException("Player Role config appeared while creating it",e);}catch(AtomicMoveNotSupportedException e){throw new IOException("atomic creation is not supported for Player Role config",e);}}finally{Files.deleteIfExists(tmp);}}
    private void replace(byte[] old,byte[] bytes)throws IOException{Path parent=parent(),tmp=Files.createTempFile(parent,file.getFileName().toString(),".tmp"),bakTmp=null;try{write(tmp,bytes);verifyObserved(old);Path backup=file.resolveSibling(file.getFileName()+".bak");bakTmp=Files.createTempFile(parent,backup.getFileName().toString(),".tmp");write(bakTmp,old);move(bakTmp,backup);bakTmp=null;verifyObserved(old);move(tmp,file);}finally{Files.deleteIfExists(tmp);if(bakTmp!=null)Files.deleteIfExists(bakTmp);}}
    private Path parent()throws IOException{Path p=file.getParent();if(p==null)throw new IOException("Player Role config has no parent");return p;}
    private static void write(Path p,byte[] b)throws IOException{try(FileChannel c=FileChannel.open(p,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer x=ByteBuffer.wrap(b);while(x.hasRemaining())c.write(x);c.force(true);}}
    private static void move(Path a,Path b)throws IOException{try{Files.move(a,b,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){throw new IOException("atomic replacement is not supported for Player Role config",e);}}
    private void verifyObserved(byte[] expected)throws IOException{if(!MessageDigest.isEqual(expected,current()))throw new IOException("Player Role config changed outside Chronicle during save; reload it before saving "+file);}
    public static Map<String,PlayerRole> decode(String yaml){
        if(yaml==null||yaml.getBytes(StandardCharsets.UTF_8).length>MAX_FILE_BYTES)throw new IllegalArgumentException("Player Role YAML exceeds 2 MiB");
        try{inspectEvents(yaml);Object root=new Load(SETTINGS).loadFromString(yaml);return roles(map(root,"root"));}catch(RuntimeException e){throw new IllegalArgumentException("invalid Player Role YAML",e);}
    }
    public static String encode(Map<String,PlayerRole> roles){Map<String,Object> root=new LinkedHashMap<>();root.put("schemaVersion",SCHEMA_VERSION);List<Map<String,Object>> values=new ArrayList<>();for(PlayerRole role:valid(roles,"Player Roles").values())values.add(yaml(role));root.put("roles",values);return new Dump(DUMP_SETTINGS).dumpToString(root);}
    private static Map<String,PlayerRole> roles(Map<String,Object> root){exact(root,Set.of("schemaVersion","roles"),"root");if(integer(root.get("schemaVersion"),"schemaVersion")!=SCHEMA_VERSION)throw new IllegalArgumentException("unsupported Player Role schemaVersion");Object value=root.get("roles");if(!(value instanceof List<?> list)||list.size()>PlayerRole.MAX_ROLES)throw new IllegalArgumentException("roles must be a list of at most "+PlayerRole.MAX_ROLES);Map<String,PlayerRole> result=new LinkedHashMap<>();for(int i=0;i<list.size();i++){PlayerRole r=role(map(list.get(i),"roles["+i+"]"));if(result.putIfAbsent(r.id(),r)!=null)throw new IllegalArgumentException("duplicate Player Role id: "+r.id());}return valid(result,"Player Roles");}
    private static PlayerRole role(Map<String,Object> v){exact(v,Set.of("id","name","description","selectable","capacity","starting","respawn","items","books"),"role");return new PlayerRole(string(v.get("id"),"id"),string(v.get("name"),"name"),string(v.get("description"),"description"),bool(v.get("selectable"),"selectable"),integer(v.get("capacity"),"capacity"),binding(v.get("starting"),"starting"),binding(v.get("respawn"),"respawn"),items(v.get("items")),strings(v.get("books"),"books"));}
    private static SpawnBinding binding(Object v,String f){if(v==null)return null;Map<String,Object> m=map(v,f);exact(m,Set.of("purpose","locationId","locationRevision"),f);return new SpawnBinding(string(m.get("purpose"),f+".purpose"),string(m.get("locationId"),f+".locationId"),integer(m.get("locationRevision"),f+".locationRevision"));}
    private static List<StartingItem> items(Object v){if(!(v instanceof List<?> l)||l.size()>PlayerRole.MAX_ITEMS)throw new IllegalArgumentException("items must be a list");List<StartingItem> r=new ArrayList<>();for(Object x:l)r.add(new StartingItem(string(x,"item")));return r;}
    private static List<String> strings(Object v,String field){if(!(v instanceof List<?> l)||l.size()>PlayerRole.MAX_BOOKS)throw new IllegalArgumentException(field+" must be a list");List<String> r=new ArrayList<>();for(Object x:l)r.add(string(x,field));return r;}
    private static Map<String,Object> yaml(PlayerRole r){Map<String,Object> o=new LinkedHashMap<>();o.put("id",r.id());o.put("name",r.name());o.put("description",r.description());o.put("selectable",r.selectable());o.put("capacity",r.capacity());o.put("starting",yaml(r.starting()));o.put("respawn",yaml(r.respawn()));o.put("items",r.items().stream().map(StartingItem::stackJson).toList());o.put("books",r.books());return o;}
    private static Map<String,Object> yaml(SpawnBinding b){if(b==null)return null;Map<String,Object> o=new LinkedHashMap<>();o.put("purpose",b.purpose());o.put("locationId",b.locationId());o.put("locationRevision",b.locationRevision());return o;}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value,String field){if(!(value instanceof Map<?,?> raw))throw new IllegalArgumentException(field+" must be a mapping");Map<String,Object> r=new LinkedHashMap<>();raw.forEach((k,v)->{if(!(k instanceof String s)||r.putIfAbsent(s,v)!=null)throw new IllegalArgumentException(field+" keys must be unique strings");});return r;}
    /** Inspect parser events before construction: aliases, tags, merges and deep trees never reach Load. */
    private static void inspectEvents(String yaml){
        int documents=0,depth=0,nodes=0;
        for(Event event:new Parse(SETTINGS).parseString(yaml)){
            if(event instanceof DocumentStartEvent start){if(++documents>1||!start.getTags().isEmpty())throw new IllegalArgumentException("Player Role YAML permits exactly one untagged document");continue;}
            if(event instanceof AliasEvent)throw new IllegalArgumentException("Player Role YAML forbids aliases");
            if(event instanceof NodeEvent node&&node.getAnchor().isPresent())throw new IllegalArgumentException("Player Role YAML forbids anchors");
            if(event instanceof ScalarEvent scalar&&scalar.getTag().isPresent())throw new IllegalArgumentException("Player Role YAML forbids tags");
            if(event instanceof CollectionStartEvent collection){if(collection.getTag().isPresent())throw new IllegalArgumentException("Player Role YAML forbids tags");if(++depth>MAX_YAML_DEPTH)throw new IllegalArgumentException("Player Role YAML exceeds nesting limit");}
            if(event instanceof CollectionEndEvent)depth--;
            if(event instanceof NodeEvent&&++nodes>MAX_YAML_NODES)throw new IllegalArgumentException("Player Role YAML exceeds node limit");
        }
        if(documents!=1||depth!=0)throw new IllegalArgumentException("Player Role YAML must contain one complete document");
    }
    private static String string(Object v,String field){if(!(v instanceof String s))throw new IllegalArgumentException(field+" must be a string");return s;}
    private static boolean bool(Object v,String field){if(!(v instanceof Boolean b))throw new IllegalArgumentException(field+" must be boolean");return b;}
    private static int integer(Object v,String f){if(!(v instanceof Number n)||n instanceof Float||n instanceof Double||n.longValue()!=n.doubleValue())throw new IllegalArgumentException(f+" must be an integer");try{return Math.toIntExact(n.longValue());}catch(ArithmeticException e){throw new IllegalArgumentException(f+" outside range",e);}}
    private static void exact(Map<String,Object> v,Set<String> fields,String f){if(!v.keySet().equals(fields))throw new IllegalArgumentException(f+" fields do not match schema");}
    private static Map<String,PlayerRole> valid(Map<String,PlayerRole> roles,String f){if(roles==null||roles.size()>PlayerRole.MAX_ROLES)throw new IllegalArgumentException(f+" may contain at most "+PlayerRole.MAX_ROLES+" roles");TreeMap<String,PlayerRole> r=new TreeMap<>();roles.forEach((id,role)->{if(id==null||role==null||!id.equals(role.id())||r.putIfAbsent(id,role)!=null)throw new IllegalArgumentException(f+" must map ids to matching distinct roles");});return Collections.unmodifiableMap(r);}
}
