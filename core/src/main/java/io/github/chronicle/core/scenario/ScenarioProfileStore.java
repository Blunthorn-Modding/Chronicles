package io.github.chronicle.core.scenario;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Controlled create-only profile files; callers cannot supply filesystem destinations. */
public final class ScenarioProfileStore {
    private final Path root;
    public ScenarioProfileStore(Path chronicleConfig){root=Objects.requireNonNull(chronicleConfig).toAbsolutePath().normalize();}
    public Path create(ScenarioProfile profile)throws IOException{
        Path directory=directory("scenario-setups");
        try(var files=Files.list(directory)){if(files.limit(17).count()>=16)throw new IOException("There are already 16 authoring setups in this server configuration.");}
        return publish(directory.resolve(digest(profile.content().scenarioId().getBytes(StandardCharsets.UTF_8))+".json"),profile,false);
    }
    public Path export(ScenarioProfile profile)throws IOException{
        byte[] bytes=ScenarioProfileJson.encode(profile).getBytes(StandardCharsets.UTF_8);
        return publish(directory("scenario-exports").resolve("profile-"+digest(bytes)+".json"),profile,true);
    }
    private Path publish(Path destination,ScenarioProfile profile,boolean reuse)throws IOException{
        byte[] bytes=ScenarioProfileJson.encode(profile).getBytes(StandardCharsets.UTF_8);
        try(FileChannel channel=FileChannel.open(destination,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }catch(FileAlreadyExistsException exists){
            if(!reuse)throw new IOException("That scenario ID already has an authoring setup. Choose another ID.",exists);
            if(!MessageDigest.isEqual(bytes,readBytes(destination)))throw new IOException("Existing scenario export differs; it was preserved.",exists);
        }
        if(!read(destination).equals(profile))throw new IOException("Scenario profile verification failed; existing evidence was preserved.");
        return destination;
    }
    private Path directory(String name)throws IOException{directories(root);Files.createDirectories(root);Path result=root.resolve(name);directories(result);Files.createDirectories(result);return result;}
    public static ScenarioProfile read(Path path)throws IOException{
        byte[] bytes=readBytes(path);try{return ScenarioProfileJson.decode(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());}
        catch(RuntimeException|CharacterCodingException error){throw new IOException("Invalid scenario profile was preserved: "+path,error);}
    }
    private static byte[] readBytes(Path path)throws IOException{
        path=path.toAbsolutePath().normalize();directories(path.getParent());
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>ScenarioProfileJson.MAX_BYTES)throw new IOException("Scenario profile must be a bounded regular file.");
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            long size=channel.size();if(size>ScenarioProfileJson.MAX_BYTES)throw new IOException("Scenario profile grew beyond its size limit.");
            ByteBuffer bytes=ByteBuffer.allocate((int)size);while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new IOException("Scenario profile changed during read.");
            if(channel.size()!=size)throw new IOException("Scenario profile changed during read.");return bytes.array();
        }
    }
    public static void directories(Path path)throws IOException{
        if(path==null)throw new IOException("A profile directory is required.");Path absolute=path.toAbsolutePath().normalize(),cursor=absolute.getRoot();
        for(Path component:absolute){cursor=cursor.resolve(component);if(Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(cursor)||!Files.isDirectory(cursor,LinkOption.NOFOLLOW_LINKS)))throw new IOException("Profile directory must not contain links or files: "+cursor);}
    }
    public static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
