package io.github.chronicle.core.scenario;

import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.loot.LootConfig;
import java.io.IOException;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** All initial config catalogs publish together. Existing profile catalogs are never re-seeded. */
public final class ScenarioProfileContentStore {
    private final Path chronicle;
    public ScenarioProfileContentStore(Path chronicle){this.chronicle=chronicle.toAbsolutePath().normalize();}
    public Path initialize(ScenarioProfile profile)throws IOException{
        ScenarioProfileStore.directories(chronicle);Files.createDirectories(chronicle);
        Path published=chronicle.resolve("profile-content");
        if(Files.exists(published,LinkOption.NOFOLLOW_LINKS)){verifyPublished(published,profile);return published;}
        for(String legacy:List.of("books.json","chest-loot.json","player-roles.yaml"))if(Files.exists(chronicle.resolve(legacy),LinkOption.NOFOLLOW_LINKS))throw new IOException("Custom authoring requires a separate configuration directory; existing "+legacy+" was preserved.");
        Path temporary=Files.createTempDirectory(chronicle,".profile-content-");
        new BookConfig(temporary.resolve("books.json")).loadOrCreate(profile.content().books());
        new LootConfig(temporary.resolve("chest-loot.json")).loadOrCreate(profile.content().lootTables());
        new io.github.chronicle.core.eventpool.EventPoolCatalog(new io.github.chronicle.core.eventpool.EventPoolCatalogStore(temporary.resolve("event-pools.json")),profile.content().eventPools().values());
        Files.writeString(temporary.resolve("portable-sections-new.marker"),"1\n",StandardOpenOption.CREATE_NEW);
        identity(temporary.resolve("identity.json"),profile);
        try{Files.move(temporary,published,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException failure){throw new IOException("Atomic profile catalog publication is unsupported; the prepared directory was preserved: "+temporary,failure);}
        verifyPublished(published,profile);return published;
    }
    private static void verifyPublished(Path directory,ScenarioProfile profile)throws IOException{
        ScenarioProfileStore.directories(directory);
        Path identity=directory.resolve("identity.json");
        if(!Files.isRegularFile(identity,LinkOption.NOFOLLOW_LINKS)||Files.size(identity)>8192||!Files.readString(identity,StandardCharsets.UTF_8).equals(identityText(profile)))throw new IOException("Profile catalog identity is missing or changed; existing data was preserved.");
        for(String name:List.of("books.json","chest-loot.json"))if(!Files.isRegularFile(directory.resolve(name),LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(directory.resolve(name)))throw new IOException("Initialized profile catalog is missing: "+name+". Restore it; Chronicle will not regenerate it.");
        new BookConfig(directory.resolve("books.json")).loadExisting();new LootConfig(directory.resolve("chest-loot.json")).loadExisting();
        if (Files.exists(directory.resolve("portable-sections-v1.marker"),LinkOption.NOFOLLOW_LINKS) || Files.exists(directory.resolve("portable-sections-new.marker"),LinkOption.NOFOLLOW_LINKS)) {
            Path pools=directory.resolve("event-pools.json");
            if (!Files.isRegularFile(pools,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Initialized pool catalog is missing; restore it.");
            if (Files.size(pools)>io.github.chronicle.core.eventpool.EventPoolCatalogStore.MAX_BYTES) throw new IOException("Pool catalog exceeds its size limit.");
            io.github.chronicle.core.eventpool.EventPoolCatalogStore.decode(Files.readAllBytes(pools));
        }
    }
    public static String identityText(ScenarioProfile profile){return profile.identity()+"\n"+profile.content().scenarioId()+"\n"+String.join("\n",profile.mechanics())+"\n";}
    private static void identity(Path file,ScenarioProfile profile)throws IOException{
        byte[] bytes=identityText(profile).getBytes(StandardCharsets.UTF_8);
        try(FileChannel channel=FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
    }
    /** Called before any Core world authority starts; stable identity does not bind map geometry to content bytes. */
    public static void checkWorld(Path world,ScenarioProfile profile)throws IOException{
        Path directory=world.resolve("chronicle"),marker=directory.resolve("scenario-profile.identity");ScenarioProfileStore.directories(directory);
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)){
            if(profile==null||!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)||Files.size(marker)>8192||!Files.readString(marker,StandardCharsets.UTF_8).equals(identityText(profile)))throw new IOException("This world belongs to another custom scenario, or its scenario profile is missing. Existing world data was preserved.");
            return;
        }
        if(profile==null)return;
        if(Files.exists(directory)){try(var files=Files.list(directory)){if(files.findAny().isPresent())throw new IOException("Use a fresh authoring world or a map without existing Chronicle campaign data. The current world was preserved.");}}
    }
    public static void bindWorld(Path world,ScenarioProfile profile)throws IOException{
        checkWorld(world,profile);if(profile==null)return;
        Path directory=world.resolve("chronicle"),marker=directory.resolve("scenario-profile.identity");
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS))return;
        Files.createDirectories(directory);identity(marker,profile);
    }
}
