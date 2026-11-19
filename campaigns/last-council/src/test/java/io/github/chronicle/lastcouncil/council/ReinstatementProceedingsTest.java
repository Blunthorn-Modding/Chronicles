package io.github.chronicle.lastcouncil.council;
import io.github.chronicle.lastcouncil.rebellion.RebellionService;

import com.google.gson.JsonParser;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.ballots.*;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.lastcouncil.council.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;
import static org.junit.jupiter.api.Assertions.*;

class ReinstatementProceedingsTest {
    static final UUID HOST=new UUID(0,1);
    static final AuthoringSite.Region REGION=new AuthoringSite.Region("council","minecraft:overworld",1,new BlockPosition(-5,64,-5),new BlockPosition(5,80,5));
    static final Map<String,BallotElector> ELECTORS=electors();
    static Map<String,BallotElector> electors(){Map<String,BallotElector> result=new LinkedHashMap<>();for(int i=0;i<8;i++)result.put("house:h"+i,new BallotElector(new UUID(0,10+i),"House "+i));return result;}
    static final ProceedingsService.ProgressionGate GATE=new ProceedingsService.ProgressionGate(){public void beforeRegency(int meeting){} public void beforeMeetingClose(int meeting){}};
    static class FaultStore implements RebellionService.Storage {
        final AtomicDataFile file; boolean before,after;
        FaultStore(Path path){file=new AtomicDataFile(path,2*1024*1024);}
        public byte[] load()throws IOException{return file.load();}
        public void verify()throws IOException{file.verifyUnchanged();}
        public void save(byte[] bytes)throws IOException{if(before)throw new IOException("before publication");file.save(bytes);if(after)throw new IOException("after publication");}
    }
    static class FaultRepo implements ProceedingsRepository {
        final ProceedingsStore store; int saves,failAt=-1;
        FaultRepo(Path path){store=new ProceedingsStore(path);}
        public ProceedingsState load()throws IOException{return store.load();}
        public void verifyUnchanged()throws IOException{store.verifyUnchanged();}
        public void save(ProceedingsState state)throws IOException{if(++saves==failAt)throw new IOException("finalize refused");store.save(state);}
    }
    static class Fixture {
        final Path root;final FaultStore rebellionStore;final RebellionService rebellion;final FaultRepo repo;final BallotService ballots;final ReinstatementMotionEffects effects;final ProceedingsService service;
        Fixture(Path root)throws Exception{this.root=root;rebellionStore=new FaultStore(root.resolve("rebellions.json"));rebellion=new RebellionService(rebellionStore);rebellion.declare(UUID.randomUUID(),0,new RebellionService.House("house:rebel","Rebel House"),"We reject the Council.");repo=new FaultRepo(root.resolve("proceedings.json"));ballots=new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC());effects=new ReinstatementMotionEffects(rebellion);service=service(repo,ballots,effects);service.region(REGION.id());service.openMeeting();}
        ProceedingsService restart()throws Exception{return service(new ProceedingsStore(root.resolve("proceedings.json")),new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC()),new ReinstatementMotionEffects(new RebellionService(root.resolve("rebellions.json"))));}
        Motion motion()throws Exception{return new Motion(UUID.randomUUID(),"Restore House","Terms",HOST,"",BallotVisibility.SECRET,Threshold.TWO_THIRDS,true,Set.of(),MotionStatus.SUBMITTED,List.of(),List.of(),null,new ReinstatementSubject(rebellion.targets(List.of("house:rebel"))));}
        Ballot open(Motion m)throws Exception{service.propose(m);return service.open(m.id(),HOST,REGION,ELECTORS);}
        void votes(Ballot b,int yes)throws Exception{for(int i=0;i<yes;i++)service.cast(b.definition().id(),new UUID(0,10+i),"yes");}
    }
    static ProceedingsService service(ProceedingsRepository repo,BallotService ballots,ReinstatementMotionEffects effects)throws IOException{return new ProceedingsService(repo,ballots,Clock.systemUTC(),n->0,GATE,effects);}
    @Test void failedVoidPassedAndIdempotentRestart(@TempDir Path root)throws Exception{
        var f=new Fixture(root);var b=f.open(f.motion());assertEquals(6,b.definition().threshold());f.votes(b,5);f.service.close(b.definition().id(),HOST);assertNotNull(f.rebellion.membership("house:rebel"));
        b=f.open(f.motion());f.votes(b,6);f.service.voidBallot(b.definition().id(),HOST,"Wrong motion");assertNotNull(f.rebellion.membership("house:rebel"));
        var m=f.motion();b=f.open(m);f.votes(b,6);var closed=f.service.close(b.definition().id(),HOST);assertNull(f.rebellion.membership("house:rebel"));f.effects.apply(m,closed);assertEquals(1,f.rebellion.effects().size());assertNotNull(f.restart());
    }
    @Test void authorityWriteFailureRecoversBeforeAndAfterPublication(@TempDir Path root)throws Exception{
        for(boolean after:List.of(false,true)){var dir=Files.createDirectory(root.resolve(after?"after":"before"));var f=new Fixture(dir);var b=f.open(f.motion());f.votes(b,6);f.rebellionStore.before=!after;f.rebellionStore.after=after;assertThrows(IOException.class,()->f.service.close(b.definition().id(),HOST));assertNotNull(f.service.state().pending());var next=f.restart();next.recover();assertNull(next.state().pending());assertNull(new RebellionService(dir.resolve("rebellions.json")).membership("house:rebel"));}
    }
    @Test void finalizationFailureKeepsSingleEffect(@TempDir Path root)throws Exception{var f=new Fixture(root);var b=f.open(f.motion());f.votes(b,6);f.repo.failAt=f.repo.saves+2;assertThrows(IOException.class,()->f.service.close(b.definition().id(),HOST));assertEquals(1,f.rebellion.effects().size());var next=f.restart();next.recover();assertNull(next.state().pending());assertEquals(1,new RebellionService(root.resolve("rebellions.json")).effects().size());}
    @Test void wrongCloseActorRefusesEffect(@TempDir Path root)throws Exception{var f=new Fixture(root);var b=f.open(f.motion());f.votes(b,6);f.rebellionStore.before=true;assertThrows(IOException.class,()->f.service.close(b.definition().id(),HOST));var path=root.resolve("proceedings.json");var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.getAsJsonObject("state").getAsJsonObject("pending").addProperty("actor",UUID.randomUUID().toString());Files.writeString(path,json+"\n");assertThrows(IllegalStateException.class,f::restart);}
    @Test void staleMembershipCannotRestoreNewRebellion(@TempDir Path root)throws Exception{var f=new Fixture(root);var stale=f.motion();var b=f.open(f.motion());f.votes(b,6);f.service.close(b.definition().id(),HOST);f.rebellion.declare(UUID.randomUUID(),f.rebellion.snapshot().revision(),new RebellionService.House("house:rebel","Rebel House"),"Again.");assertThrows(IllegalStateException.class,()->f.service.propose(stale));}
    @Test void schemaFourMigratesGeneralWithoutPoliticalSubject(@TempDir Path root)throws Exception{var f=new Fixture(root);var m=new Motion(UUID.randomUUID(),"General","Body",HOST,"",BallotVisibility.PUBLIC,Threshold.MAJORITY,true,Set.of(),MotionStatus.SUBMITTED,List.of(),List.of());f.service.propose(m);var path=root.resolve("proceedings.json");var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.addProperty("schemaVersion",4);json.getAsJsonObject("state").getAsJsonObject("motions").asMap().values().forEach(v->v.getAsJsonObject().remove("reinstatement"));Files.writeString(path,json+"\n");assertNull(new ProceedingsStore(path).load().motions().get(m.id()).reinstatement());}
    @Test void schemaFourRejectsSmuggledReinstatementField(@TempDir Path root)throws Exception{
        var f=new Fixture(root);var m=f.motion();f.service.propose(m);var path=root.resolve("proceedings.json");var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.addProperty("schemaVersion",4);
        String retained=json+"\n";Files.writeString(path,retained);assertThrows(IOException.class,()->new ProceedingsStore(path).load());assertEquals(retained,Files.readString(path));
        json.getAsJsonObject("state").getAsJsonObject("motions").asMap().values().forEach(v->v.getAsJsonObject().add("reinstatement",com.google.gson.JsonNull.INSTANCE));Files.writeString(path,json+"\n");assertThrows(IOException.class,()->new ProceedingsStore(path).load());
    }

}
