package io.github.chronicle.core.server;

import com.google.gson.Gson;
import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.managed.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Immutable side effects pinned by the main Area operation's SHA-256 evidence. */
final class AreaEffectFiles {
    private static final Gson JSON=new Gson();
    private static final int MAX_BYTES=24*1024*1024;
    private AreaEffectFiles(){}
    record Book(String bookId,String token){Book{Objects.requireNonNull(bookId);UUID.fromString(token);}}
    record Effects(String operationId,String managed,long contentRevision,Set<String> affectedBindings,
                   Set<String> retiredBindings,Set<Book> books,Set<String> retiredAnchors){
        Effects {
            UUID.fromString(operationId);Objects.requireNonNull(managed);if(contentRevision<0)throw new IllegalArgumentException("Invalid content revision");
            affectedBindings=Set.copyOf(affectedBindings);retiredBindings=Set.copyOf(retiredBindings);books=Set.copyOf(books);retiredAnchors=Set.copyOf(retiredAnchors);
            if(affectedBindings.size()>512||books.size()>256||retiredAnchors.size()>512||!affectedBindings.containsAll(retiredBindings))throw new IllegalArgumentException("Area effects exceed limits");
        }
        boolean hasContent(){return !affectedBindings.isEmpty()||!books.isEmpty();}
        ManagedAreaReplacementPlan managedPlan(){return managed.isEmpty()?null:ManagedAreaReplacementCodec.decode(Base64.getDecoder().decode(managed));}
    }
    static String save(MinecraftServer server,Effects effects)throws IOException{
        byte[] bytes=JSON.toJson(effects).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Area effects exceed size limit");
        put(path(server,UUID.fromString(effects.operationId()),"effects.json"),bytes);return "effects-sha256:"+sha(bytes);
    }
    static Effects load(MinecraftServer server,AreaOperation operation)throws IOException{
        Path file=path(server,operation.operationId(),"effects.json");if(!Files.isRegularFile(file)||Files.size(file)>MAX_BYTES)throw new IOException("Area effects are missing or oversized");byte[] bytes=Files.readAllBytes(file);
        if(!operation.detail().equals("effects-sha256:"+sha(bytes)))throw new IOException("Area effects do not match the committed hash");
        try{if(!BookConfig.strictObject(new String(bytes,StandardCharsets.UTF_8)).keySet().equals(Set.of("operationId","managed","contentRevision","affectedBindings","retiredBindings","books","retiredAnchors")))throw new IllegalArgumentException("Unexpected Area effect fields");Effects e=JSON.fromJson(new String(bytes,StandardCharsets.UTF_8),Effects.class);if(!e.operationId().equals(operation.operationId().toString()))throw new IllegalArgumentException("Area sidecar identity mismatch");ManagedAreaReplacementPlan plan=e.managedPlan();if(plan!=null&&!plan.operationId().equals(operation.operationId()))throw new IllegalArgumentException("Managed sidecar identity mismatch");return e;}catch(RuntimeException failure){throw new IOException("Invalid Area effects preserved",failure);}
    }
    static void savePrepared(MinecraftServer server,AreaOperation operation)throws IOException{
        AreaOperationStore store=new AreaOperationStore(path(server,operation.operationId(),"prepared.bin"));AreaOperationJournal old=store.load();
        if(!old.operations().isEmpty()){if(!operation.equals(old.operations().get(operation.operationId())))throw new IOException("Prepared Area evidence changed");return;}
        store.save(new AreaOperationJournal(1,Map.of(operation.operationId(),operation)));
    }
    static AreaOperation prepared(MinecraftServer server,UUID operationId)throws IOException{
        AreaOperation operation=new AreaOperationStore(path(server,operationId,"prepared.bin")).load().operations().get(operationId);
        if(operation==null||operation.stage()!=AreaOperation.Stage.PREPARED)throw new IOException("Prepared Area evidence is unavailable");load(server,operation);return operation;
    }
    private static Path path(MinecraftServer server,UUID id,String suffix){return server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/area-changes/effects").resolve(id+"."+suffix);}
    static void put(Path target,byte[] bytes)throws IOException{
        Files.createDirectories(target.getParent());if(Files.exists(target)){if(!Arrays.equals(bytes,Files.readAllBytes(target)))throw new IOException("Immutable Area effects changed");return;}
        Path temp=Files.createTempFile(target.getParent(),".area-",".tmp");try{try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())channel.write(b);channel.force(true);}try{Files.createLink(target,temp);}catch(FileAlreadyExistsException race){if(!Arrays.equals(bytes,Files.readAllBytes(target)))throw new IOException("Immutable Area effects changed",race);}}finally{Files.deleteIfExists(temp);}
    }
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
