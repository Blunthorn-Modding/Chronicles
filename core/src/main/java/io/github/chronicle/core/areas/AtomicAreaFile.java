package io.github.chronicle.core.areas;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Main-thread store with observed-byte checks and atomic publication. Initial creation cannot
 * clobber a competing file. Existing-file replacement is not an OS compare-and-swap: a
 * non-cooperating external writer can race the final check. Edit world journals only stopped.
 */
final class AtomicAreaFile {
    private final Path file; private final long maxBytes; private Boolean observedPresent; private byte[] observedHash;
    AtomicAreaFile(Path file,long maxBytes){if(file==null||file.getFileName()==null)throw new IllegalArgumentException("area data file is required");this.file=file.toAbsolutePath().normalize();this.maxBytes=maxBytes;}
    synchronized byte[] load()throws IOException{if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)){observedPresent=false;observedHash=null;return null;}byte[] b=read();observedPresent=true;observedHash=hash(b);return b;}
    synchronized void verifyUnchanged()throws IOException{
        if(observedPresent==null)throw new IOException("load valid area authority before verifying it");
        boolean present=Files.exists(file,LinkOption.NOFOLLOW_LINKS);if(present!=observedPresent)throw new IOException("area authority presence changed outside this store");
        if(present&&(observedHash==null||!MessageDigest.isEqual(observedHash,hash(read()))))throw new IOException("area authority changed outside this store");
    }
    synchronized void save(byte[] next)throws IOException{if(next==null||next.length>maxBytes)throw new IOException("area data exceeds byte limit");Path parent=file.getParent();if(parent==null)throw new IOException("area data path has no parent");Files.createDirectories(parent);boolean present=Files.exists(file,LinkOption.NOFOLLOW_LINKS);if(observedPresent!=null&&present!=observedPresent)throw new IOException("area data presence changed outside this store; refusing overwrite");if(present&&observedHash!=null&&!MessageDigest.isEqual(observedHash,hash(read())))throw new IOException("area data changed outside this store; refusing overwrite");Path tmp=Files.createTempFile(parent,file.getFileName().toString(),".tmp");try{try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer x=ByteBuffer.wrap(next);while(x.hasRemaining())c.write(x);c.force(true);}try{boolean now=Files.exists(file,LinkOption.NOFOLLOW_LINKS);if(now!=present||now&&observedHash!=null&&!MessageDigest.isEqual(observedHash,hash(read())))throw new IOException("area data changed before publication; refusing overwrite");if(present)Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else Files.createLink(file,tmp);}catch(AtomicMoveNotSupportedException e){throw new IOException("atomic replacement is not supported for area data: "+file,e);}observedPresent=true;observedHash=hash(next);}finally{Files.deleteIfExists(tmp);}}
    private byte[] read()throws IOException{if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("area data is not a regular file: "+file);long n=Files.size(file);if(n<1||n>maxBytes)throw new IOException("area data size is outside limits: "+file);return Files.readAllBytes(file);}
    private static byte[] hash(byte[] b)throws IOException{try{return MessageDigest.getInstance("SHA-256").digest(b);}catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}}
}
