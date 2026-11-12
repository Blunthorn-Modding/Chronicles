package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** One-time addition for older initialized profiles; later missing settings refuse reseeding. */
final class BanquetProfiles {
    private static final byte[] MARKER="{\"schema\":1}\n".getBytes(StandardCharsets.UTF_8);
    private BanquetProfiles() { }
    static void initialize(Path directory,String initial,boolean legacy)throws IOException{
        if(Files.exists(directory.resolve("last-council-banquet.initialized"),LinkOption.NOFOLLOW_LINKS)){verify(directory);return;}
        new BanquetSettings(directory.resolve("last-council-banquet.json"),!legacy&&initial!=null?BanquetSettings.decode(initial.getBytes(StandardCharsets.UTF_8)):BanquetConfig.defaults());
        mark(directory);
    }
    static void verify(Path directory)throws IOException{
        Path settings=directory.resolve("last-council-banquet.json"),marker=directory.resolve("last-council-banquet.initialized");
        if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS)){
            // Old profiles predate this extension. Never replace a present invalid settings file.
            new BanquetSettings(settings,BanquetConfig.defaults());mark(directory);
        }
        mark(directory);
        if(!Files.isRegularFile(settings,LinkOption.NOFOLLOW_LINKS))throw new IOException("Initialized banquet settings are missing; restore them.");
        if(Files.size(settings)>16384)throw new IOException("Initialized banquet settings exceed their size limit.");
        BanquetSettings.decode(Files.readAllBytes(settings));
    }
    private static void mark(Path directory)throws IOException{
        AtomicDataFile marker=new AtomicDataFile(directory.resolve("last-council-banquet.initialized"),128);
        byte[] bytes=marker.load();if(bytes==null)marker.save(MARKER);else if(!Arrays.equals(bytes,MARKER))throw new IOException("Invalid banquet profile migration marker; preserved.");
    }
}
