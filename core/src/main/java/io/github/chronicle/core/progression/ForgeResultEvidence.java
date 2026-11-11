package io.github.chronicle.core.progression;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Immutable exact accepted workshop proposal for inspection when result publication is interrupted. */
public record ForgeResultEvidence(UUID operation,UUID actor,String target,String first,String second,String result) {
    public ForgeResultEvidence {
        Objects.requireNonNull(operation);Objects.requireNonNull(actor);SourceLedger.text(target,160,false);
        for(String stack:List.of(first,second,result))if(stack.isBlank()||stack.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Forge result stack evidence exceeds limits");
    }
    public String save(Path directory)throws IOException {
        directory=directory.toAbsolutePath().normalize();verifyParents(directory);byte[] encoded=encode();Files.createDirectories(directory);verifyParents(directory);Path file=directory.resolve(operation+".bin");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>200000||!Arrays.equals(encoded,Files.readAllBytes(file)))throw new IOException("Immutable Forge result evidence changed");
        } else {
            Path temporary=Files.createTempFile(directory,".forge-result-",".tmp");
            try{try(FileChannel out=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer buffer=ByteBuffer.wrap(encoded);while(buffer.hasRemaining())out.write(buffer);out.force(true);}Files.createLink(file,temporary);}
            finally{Files.deleteIfExists(temporary);}
        }
        return hash(encoded);
    }
    private byte[] encode()throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(0x43465247);out.writeInt(1);out.writeUTF(operation.toString());out.writeUTF(actor.toString());out.writeUTF(target);
            for(String stack:List.of(first,second,result)){byte[] encoded=stack.getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);}
        }
        return bytes.toByteArray();
    }
    public static ForgeResultEvidence load(Path directory,UUID operation,String expectedHash)throws IOException {
        if(expectedHash==null||!expectedHash.matches("[0-9a-f]{64}"))throw new IOException("Invalid Forge result hash");
        directory=directory.toAbsolutePath().normalize();verifyParents(directory);
        Path file=directory.resolve(operation+".bin");if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>200000)throw new IOException("Forge result evidence is missing or oversized");
        byte[] bytes=Files.readAllBytes(file);if(!hash(bytes).equals(expectedHash))throw new IOException("Forge result evidence hash changed");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readInt()!=0x43465247||in.readInt()!=1)throw new IllegalArgumentException("Unsupported Forge result schema");
            UUID id=UUID.fromString(in.readUTF()),actor=UUID.fromString(in.readUTF());String target=in.readUTF();
            String[] stacks=new String[3];for(int i=0;i<3;i++){int length=in.readInt();if(length<1||length>65536)throw new IllegalArgumentException("Invalid Forge stack length");byte[] stack=in.readNBytes(length);if(stack.length!=length)throw new EOFException();stacks[i]=new String(stack,StandardCharsets.UTF_8);if(!Arrays.equals(stack,stacks[i].getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Invalid Forge stack encoding");}
            var evidence=new ForgeResultEvidence(id,actor,target,stacks[0],stacks[1],stacks[2]);if(!id.equals(operation)||in.read()!=-1||!Arrays.equals(bytes,evidence.encode()))throw new IllegalArgumentException("Forge result evidence identity or encoding changed");
            return evidence;
        }catch(RuntimeException failure){throw new IOException("Forge result evidence is invalid and was preserved",failure);}
    }
    private static String hash(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void verifyParents(Path directory)throws IOException {
        for(Path current=directory;current!=null;current=current.getParent()) {
            if(Files.isSymbolicLink(current))throw new IOException("Forge result evidence refuses symbolic-link directories");
            if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS))throw new IOException("Forge result evidence parent is not a directory");
        }
    }
}
