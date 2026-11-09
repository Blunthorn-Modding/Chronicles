package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Startup-only profile initialization and one-time migration of reusable House definitions. */
public final class CouncilContentProfiles {
    private static final byte[] MARKER="1\n".getBytes(StandardCharsets.US_ASCII);
    private CouncilContentProfiles() { }
    public static Path file(Path directory) { return directory.resolve("last-council/houses.json"); }
    public static CouncilContent empty() { return new CouncilContent(Map.of(),List.of(),List.of(),0,0,0); }

    public static void initialize(Path directory, String initial, boolean legacy) throws IOException {
        AtomicDataFile marker=new AtomicDataFile(directory.resolve("last-council/houses-profile-v1.marker"),32);
        byte[] mark=marker.load();
        if(mark!=null) { checkMarker(mark); requireTarget(directory); return; }
        AtomicDataFile target=new AtomicDataFile(file(directory),CouncilContentJson.MAX_BYTES);
        byte[] existing=target.load();
        byte[] desired=null;
        if(!legacy) desired=initial==null ? CouncilContentJson.encode(empty()) : initial.getBytes(StandardCharsets.UTF_8);
        if(desired!=null) CouncilContentJson.decode(desired);
        if(existing!=null) {
            CouncilContent existingContent=CouncilContentJson.decode(existing);
            if(!legacy && !existingContent.equals(CouncilContentJson.decode(desired))) throw new IOException("Existing profile House content differs from the requested content; preserved.");
        } else {
            if(legacy) {
                AtomicDataFile source=new AtomicDataFile(file(directory.getParent()),CouncilContentJson.MAX_BYTES);
                desired=source.load();
                if(desired!=null) { CouncilContentJson.decode(desired); source.verifyUnchanged(); }
                else desired=CouncilContentJson.encode(empty());
            }
            target.save(desired);
        }
        target.verifyUnchanged();
        marker.save(MARKER);
    }
    /** Old profiles have the generic marker but lack this provider's marker. */
    public static void verify(Path directory) throws IOException { initialize(directory,null,true); }
    private static void requireTarget(Path directory) throws IOException {
        byte[] data=new AtomicDataFile(file(directory),CouncilContentJson.MAX_BYTES).load();
        if(data==null) throw new IOException("Initialized profile House content is missing; restore it.");
        CouncilContentJson.decode(data);
    }
    private static void checkMarker(byte[] bytes) throws IOException {
        if(!Arrays.equals(bytes,MARKER)) throw new IOException("Invalid House profile marker; retained files were preserved.");
    }
}
