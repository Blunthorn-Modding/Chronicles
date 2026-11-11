package io.github.chronicle.core.progression;

import io.github.chronicle.core.history.ImmutableHistoryArchive;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Strict, observed-file, atomic source ledger. Invalid or externally changed evidence is never overwritten. */
public final class SourceLedgerStore {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAGIC = 0x43535243, SCHEMA = 1;
    private final Path file;
    private final ImmutableHistoryArchive history;
    private byte[] observed;
    private boolean loaded;
    public SourceLedgerStore(Path file) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        this.history = new ImmutableHistoryArchive(this.file.resolveSibling("event-source-history"), "event-source", MAX_BYTES);
    }
    public synchronized SourceLedger load() throws IOException {
        loaded=false;
        if (!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) { observed=null; loaded=true; return SourceLedger.empty(); }
        byte[] bytes=read();
        try { SourceLedger result=decode(bytes); observed=bytes; loaded=true; return result; }
        catch (RuntimeException failure) { throw new IOException("Event source evidence is invalid and was preserved",failure); }
    }
    public synchronized void save(SourceLedger ledger) throws IOException {
        if (!loaded) throw new IOException("load valid Event source evidence before saving");
        byte[] bytes=encode(ledger); if(bytes.length>MAX_BYTES)throw new IOException("Event source evidence exceeds size limit");
        verifyUnchanged();
        SourceLedger previous=observed==null?SourceLedger.empty():decode(observed);
        for(var old:previous.receipts().entrySet())if(!ledger.receipts().containsKey(old.getKey())) {
            if(old.getValue().stage()==SourceLedger.Stage.PREPARED)throw new IOException("prepared Event source evidence cannot be retired");
            history.publish(old.getKey(),encodeReceipt(old.getValue()));
        }
        for(var next:ledger.receipts().entrySet())if(!previous.receipts().containsKey(next.getKey())&&history.read(next.getKey()).isPresent())
            throw new IOException("archived Event source ID cannot return to the live ledger: "+next.getKey());
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),".event-sources-",".tmp");
        try {
            try(FileChannel out=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
            }
            verifyUnchanged();
            if(observed==null)Files.createLink(file,temporary);
            else Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            observed=bytes;
        } finally { Files.deleteIfExists(temporary); }
    }
    public synchronized void verifyUnchanged() throws IOException {
        if(!loaded)throw new IOException("Event source evidence is not loaded");
        boolean exists=Files.exists(file,LinkOption.NOFOLLOW_LINKS);
        if(exists!=(observed!=null)||exists&&!MessageDigest.isEqual(observed,read()))throw new IOException("Event source evidence changed externally");
    }
    public synchronized Optional<SourceLedger.Receipt> archived(String id) throws IOException {
        Optional<byte[]> payload=history.read(id);
        if(payload.isEmpty())return Optional.empty();
        SourceLedger.Receipt receipt=decodeReceipt(payload.get());
        if(!receipt.id().equals(id))throw new IOException("archived Event source identity mismatch");
        return Optional.of(receipt);
    }
    private byte[] read() throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_BYTES)throw new IOException("Event source evidence type/size is invalid");
        byte[] bytes=Files.readAllBytes(file);if(bytes.length>MAX_BYTES)throw new IOException("Event source evidence exceeds size limit");return bytes;
    }
    static byte[] encode(SourceLedger ledger) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);out.writeInt(SCHEMA);out.writeLong(ledger.revision());out.writeInt(ledger.receipts().size());
            for(var r:ledger.receipts().values()) {
                text(out,r.id());text(out,r.type());text(out,r.target());text(out,r.activity());text(out,r.actor()==null?"":r.actor().toString());
                out.writeBoolean(r.activeOrigin());out.writeInt(r.flags().size());for(var flag:r.flags().entrySet()){text(out,flag.getKey());out.writeBoolean(flag.getValue());}
                text(out,r.authority());text(out,r.stage().name());
            }
        }return bytes.toByteArray();
    }
    static SourceLedger decode(byte[] bytes) {
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readInt()!=MAGIC||in.readInt()!=SCHEMA)throw new IllegalArgumentException("unsupported Event source evidence schema");
            long revision=in.readLong();int count=in.readInt();if(count<0||count>SourceLedger.MAX_RECEIPTS)throw new IllegalArgumentException("invalid source receipt count");
            Map<String,SourceLedger.Receipt> receipts=new LinkedHashMap<>();
            for(int i=0;i<count;i++) {
                String id=text(in,256),type=text(in,128),target=text(in,160),activity=text(in,128),actor=text(in,36);
                UUID uuid=actor.isEmpty()?null:UUID.fromString(actor);if(uuid!=null&&!uuid.toString().equals(actor))throw new IllegalArgumentException("noncanonical source actor");
                int active=in.readUnsignedByte();if(active>1)throw new IllegalArgumentException("invalid source origin boolean");
                int flagCount=in.readInt();if(flagCount<0||flagCount>256)throw new IllegalArgumentException("invalid source flag count");Map<String,Boolean> flags=new LinkedHashMap<>();
                for(int f=0;f<flagCount;f++){String key=text(in,128);int flag=in.readUnsignedByte();if(flag>1||flags.put(key,flag==1)!=null)throw new IllegalArgumentException("invalid or duplicate source flag");}
                var receipt=new SourceLedger.Receipt(id,type,target,activity,uuid,active==1,flags,text(in,256),SourceLedger.Stage.valueOf(text(in,16)));
                if(receipts.put(id,receipt)!=null)throw new IllegalArgumentException("duplicate source receipt");
            }
            if(in.read()!=-1)throw new IllegalArgumentException("source evidence has trailing data");
            return new SourceLedger(revision,receipts);
        }catch(IOException failure){throw new IllegalArgumentException("source evidence is truncated",failure);}
    }
    private static byte[] encodeReceipt(SourceLedger.Receipt receipt)throws IOException {
        return encode(new SourceLedger(0,Map.of(receipt.id(),receipt)));
    }
    private static SourceLedger.Receipt decodeReceipt(byte[] bytes)throws IOException {
        try {
            SourceLedger ledger=decode(bytes);
            if(ledger.revision()!=0||ledger.receipts().size()!=1)throw new IllegalArgumentException("archived source payload is not singular");
            return ledger.receipts().values().iterator().next();
        } catch(RuntimeException failure){throw new IOException("archived Event source evidence is invalid",failure);}
    }
    private static void text(DataOutputStream out,String text)throws IOException {byte[] bytes=text.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static String text(DataInputStream in,int max)throws IOException {
        int length=in.readInt();if(length<0||length>max*4)throw new IllegalArgumentException("invalid source field length");
        byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException();String text=new String(bytes,StandardCharsets.UTF_8);
        if(text.length()>max||!Arrays.equals(bytes,text.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("invalid source UTF-8");return text;
    }
}
