package io.github.chronicle.quietbelow.ecology;

import com.google.gson.GsonBuilder;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;

/** Offline maintenance only. Never opens terrain, starts a server, or changes Core data. */
public final class EcologyMaintenance {
    public record Result(boolean applied,String reason,long beforeBytes,long afterBytes,long beforeRecords,long afterRecords,
                         String sourceHash,String checkpointHash,String archive) { }
    @FunctionalInterface interface Promotion {void replace(Path candidate,Path journal)throws IOException;}
    private EcologyMaintenance() { }
    public static void main(String[] args)throws Exception {
        if(args.length<1||args.length>2||args.length==2&&!args[1].equals("--apply"))throw new IllegalArgumentException("Usage: EcologyMaintenance WORLD [--apply]");
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(compact(Path.of(args[0]),args.length==2)));
    }
    public static Result compact(Path world,boolean apply)throws IOException {
        return compact(world,apply,(candidate,journal)->Files.move(candidate,journal,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING));
    }
    static Result compact(Path world,boolean apply,Promotion promotion)throws IOException {
        world=world.toAbsolutePath().normalize();
        if(!world.toRealPath().equals(world))throw new IOException("Maintenance world must be a real directory, not a link");
        Path session=required(world,"session.lock");
        try(FileChannel guard=FileChannel.open(session,StandardOpenOption.WRITE);FileLock lock=lock(guard)){
            return locked(world,apply,promotion);
        }catch(OverlappingFileLockException e){throw new IOException("World is in use; save and stop the server first",e);}
    }
    private static FileLock lock(FileChannel channel)throws IOException {
        FileLock lock=channel.tryLock();if(lock==null)throw new IOException("World is in use; save and stop the server first");return lock;
    }
    private static Result locked(Path world,boolean apply,Promotion promotion)throws IOException {
        Path campaign=required(world,"chronicle/campaign-state.json"),authoring=required(world,"chronicle/authoring.json"),
                assignments=required(world,"chronicle/map-assignments.json"),file=required(world,"quiet-below/ecology.jsonl");
        if(new CampaignStore(campaign).load().phase()==SessionPhase.ACTIVE)throw new IOException("Pause the campaign and save-stop before maintenance");
        Map<Path,String> protectedHashes=new LinkedHashMap<>();for(Path p:List.of(campaign,authoring,assignments))protectedHashes.put(p,hash(p));
        Path candidate=null,archive=null;long beforeBytes,afterBytes,beforeRecords,afterRecords;String sourceHash,checkpointHash;
        EcologyEngine sourceEngine;
        try(EcologyJournal source=new EcologyJournal(file)){
            sourceEngine=new EcologyEngine(source);attach(sourceEngine,authoring,assignments);
            if(!sourceEngine.view().indexed())throw new IOException("Finish source indexing before journal maintenance");
            sourceHash=source.fingerprint();beforeBytes=source.bytes();beforeRecords=source.revision();
            Path staging=apply?file.getParent():Files.createTempDirectory("chronicle-ecology-check-");
            candidate=staging.resolve("ecology-"+UUID.randomUUID()+".prepared");
            try(EcologyJournal replacement=new EcologyJournal(candidate)){
                sourceEngine.writeCheckpoint(replacement,sourceHash);afterBytes=replacement.bytes();afterRecords=replacement.revision();checkpointHash=replacement.fingerprint();
            }
            // Reopen from disk, rather than trusting the in-memory writer.
            try(EcologyJournal replacement=new EcologyJournal(candidate)){
                EcologyEngine replay=new EcologyEngine(replacement);attach(replay,authoring,assignments);
                if(!sourceEngine.sameCheckpointState(replay))throw new IOException("Checkpoint replay differs from source; original preserved; inspect "+candidate);
            }
            if(!apply||beforeBytes-afterBytes<4096){
                Files.delete(candidate);if(!apply)Files.delete(candidate.getParent());
                return new Result(false,beforeBytes-afterBytes<4096?"Less than 4 KiB saving; original retained":"Dry run verified; original retained",beforeBytes,afterBytes,beforeRecords,afterRecords,sourceHash,checkpointHash,null);
            }
            Path archives=file.getParent().resolve("journal-archives");Files.createDirectories(archives);
            if(!archives.toRealPath().equals(archives))throw new IOException("Journal archive directory may not be a link");
            archive=archives.resolve("ecology-"+sourceHash+"-"+UUID.randomUUID()+".jsonl");source.archiveTo(archive);
            try(EcologyJournal evidence=new EcologyJournal(archive)){
                if(!evidence.fingerprint().equals(sourceHash))throw new IOException("Archive verification failed; original retained");
            }
            for(var e:protectedHashes.entrySet())if(!hash(e.getKey()).equals(e.getValue()))throw new IOException("Core metadata changed during maintenance; original retained");
            if(!source.fingerprint().equals(sourceHash))throw new IOException("Source journal changed during maintenance; original retained");
        }catch(RuntimeException e){throw new IOException("Ecology maintenance refused invalid data; original retained; prepared="+candidate,e);}
        // Minecraft cannot acquire session.lock during the close/atomic-replace gap, including old builds.
        if(!hash(file).equals(sourceHash))throw new IOException("Source changed before promotion; inspect prepared checkpoint and archive");
        promotion.replace(candidate,file); // No non-atomic fallback and no rollback over uncertain evidence.
        try(EcologyJournal installed=new EcologyJournal(file)){
            EcologyEngine replay=new EcologyEngine(installed);attach(replay,authoring,assignments);
            if(!installed.fingerprint().equals(checkpointHash)||!sourceEngine.sameCheckpointState(replay))throw new IOException("Promoted checkpoint verification failed; inspect archive "+archive);
        }
        return new Result(true,"Verified checkpoint installed; original archived",beforeBytes,afterBytes,beforeRecords,afterRecords,sourceHash,checkpointHash,archive.toString());
    }
    private static void attach(EcologyEngine engine,Path authoring,Path assignments)throws IOException {
        var sites=new AuthoringStore(authoring).load().sites();var bindings=new SiteAssignmentStore(assignments).load().bindings();
        var city=bindings.get("quiet_below:source_city");var heart=bindings.get("quiet_below:relic_heart");
        if(city==null||heart==null||!(sites.get(city.siteId()) instanceof AuthoringSite.Region region)||!(sites.get(heart.siteId()) instanceof AuthoringSite.Anchor anchor)
                ||region.revision()!=city.siteRevision()||anchor.revision()!=heart.siteRevision()||!region.dimension().equals(anchor.dimension()))throw new IOException("Source city/Heart assignments are missing or stale");
        engine.attach(new EcologyEngine.Setup(region.id(),region.revision(),anchor.id(),anchor.revision(),region.dimension()),
                new EcologyEngine.Area(position(region.min()),position(region.max())),position(anchor.position()));
    }
    private static Pos position(BlockPosition p){return new Pos(p.x(),p.y(),p.z());}
    private static Path required(Path world,String relative)throws IOException {
        Path p=world.resolve(relative);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!p.toRealPath().equals(p))throw new IOException("Missing or linked maintenance input: "+p);return p;
    }
    private static String hash(Path file)throws IOException {
        try(var input=Files.newInputStream(file)){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[64*1024];int read;
            while((read=input.read(buffer))!=-1)digest.update(buffer,0,read);return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
}
