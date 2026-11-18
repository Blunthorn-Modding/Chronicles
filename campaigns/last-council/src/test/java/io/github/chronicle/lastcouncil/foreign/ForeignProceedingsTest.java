package io.github.chronicle.lastcouncil.foreign;

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

class ForeignProceedingsTest {
    static final UUID HOST=new UUID(0,1);
    static final AuthoringSite.Region REGION=new AuthoringSite.Region("council","minecraft:overworld",1,new BlockPosition(-5,64,-5),new BlockPosition(5,80,5));
    static final Map<String,BallotElector> ELECTORS=electors();
    static Map<String,BallotElector> electors(){Map<String,BallotElector> result=new LinkedHashMap<>();for(int i=0;i<8;i++)result.put("house:h"+i,new BallotElector(new UUID(0,10+i),"House "+i));return result;}
    static final ProceedingsService.ProgressionGate GATE=new ProceedingsService.ProgressionGate(){public void beforeRegency(int meeting){} public void beforeMeetingClose(int meeting){}};
    static class FaultStore implements ForeignAffairsService.Storage {
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
        final Path root; final ForeignRealmService realms; final String realm; final FaultStore foreignStore; final ForeignAffairsService affairs; final FaultRepo repo; final BallotService ballots; final ForeignMotionEffects effects; final ProceedingsService service;
        Fixture(Path root)throws Exception {this.root=root;realms=new ForeignRealmService(root.resolve("realms.json"));realm=realms.create(0,"North","").id();foreignStore=new FaultStore(root.resolve("foreign.json"));affairs=new ForeignAffairsService(foreignStore);repo=new FaultRepo(root.resolve("proceedings.json"));ballots=new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC());effects=new ForeignMotionEffects(realms,affairs);service=service(repo,ballots,effects);service.region(REGION.id());service.openMeeting();}
        ProceedingsService restart()throws Exception {var r=new ForeignRealmService(root.resolve("realms.json"));var a=new ForeignAffairsService(root.resolve("foreign.json"));return service(new ProceedingsStore(root.resolve("proceedings.json")),new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC()),new ForeignMotionEffects(r,a));}
        Motion motion(ForeignKind kind,long revision){return new Motion(UUID.randomUUID(),"Foreign motion","Body",HOST,"",BallotVisibility.SECRET,Threshold.TWO_THIRDS,true,Set.of(),MotionStatus.SUBMITTED,List.of(),List.of(),new ForeignSubject(kind,realm,revision));}
        Ballot open(Motion motion)throws Exception {service.propose(motion);return service.open(motion.id(),HOST,REGION,ELECTORS);}
        void votes(Ballot ballot,int yes)throws Exception {for(int i=0;i<yes;i++)service.cast(ballot.definition().id(),new UUID(0,10+i),"yes");}
    }
    static ProceedingsService service(ProceedingsRepository repo,BallotService ballots,ForeignMotionEffects effects)throws IOException{return new ProceedingsService(repo,ballots,Clock.systemUTC(),n->0,GATE,effects);}
    @Test void capturedTwoThirdsPassedFailedVoidAndOldReceipt(@TempDir Path root)throws Exception {
        var f=new Fixture(root); var war=f.motion(ForeignKind.DECLARE_WAR,0); var b=f.open(war); assertEquals(6,b.definition().threshold()); f.votes(b,5); f.service.close(b.definition().id(),HOST); assertEquals(0,f.affairs.view(f.realm).revision());
        var voidMotion=f.motion(ForeignKind.DECLARE_WAR,0); var v=f.open(voidMotion); f.votes(v,6); f.service.voidBallot(v.definition().id(),HOST,"Wrong electorate"); assertEquals(0,f.affairs.view(f.realm).revision());
        war=f.motion(ForeignKind.DECLARE_WAR,0); b=f.open(war); f.votes(b,6); Ballot closed=f.service.close(b.definition().id(),HOST); assertTrue(f.affairs.view(f.realm).atWar()); Motion saved=f.service.state().motions().get(war.id());
        var failedPeace=f.motion(ForeignKind.MAKE_PEACE,1); var failed=f.open(failedPeace); f.votes(failed,5); f.service.close(failed.definition().id(),HOST); assertTrue(f.affairs.view(f.realm).atWar()); assertEquals(1,f.affairs.view(f.realm).revision());
        var peace=f.motion(ForeignKind.MAKE_PEACE,1); var p=f.open(peace); f.votes(p,6); f.service.close(p.definition().id(),HOST); f.effects.apply(saved,closed); assertEquals(2,f.affairs.view(f.realm).revision()); assertFalse(f.affairs.view(f.realm).atWar()); assertNotNull(f.restart());
    }
    @Test void foreignSaveFailureBeforeAndAfterPublicationRecovers(@TempDir Path root)throws Exception {
        for(boolean after:List.of(false,true)){Path dir=Files.createDirectory(root.resolve(after?"after":"before"));var f=new Fixture(dir);var ballot=f.open(f.motion(ForeignKind.DECLARE_WAR,0));f.votes(ballot,6);f.foreignStore.before=!after;f.foreignStore.after=after;assertThrows(IOException.class,()->f.service.close(ballot.definition().id(),HOST));assertNotNull(f.service.state().pending());var restarted=f.restart();restarted.recover();assertNull(restarted.state().pending());assertEquals(1,new ForeignAffairsService(dir.resolve("foreign.json")).effects().size());}
    }
    @Test void finalizationFailureRecoversWithoutDuplicate(@TempDir Path root)throws Exception {var f=new Fixture(root);var ballot=f.open(f.motion(ForeignKind.DECLARE_WAR,0));f.votes(ballot,6);f.repo.failAt=f.repo.saves+2;assertThrows(IOException.class,()->f.service.close(ballot.definition().id(),HOST));assertEquals(1,f.affairs.effects().size());var restarted=f.restart();restarted.recover();assertNull(restarted.state().pending());assertEquals(1,new ForeignAffairsService(root.resolve("foreign.json")).effects().size());}
    @Test void changedCloseActorCannotApplyForeignOutcome(@TempDir Path root)throws Exception {var f=new Fixture(root);var ballot=f.open(f.motion(ForeignKind.DECLARE_WAR,0));f.votes(ballot,6);f.foreignStore.before=true;assertThrows(IOException.class,()->f.service.close(ballot.definition().id(),HOST));assertEquals(BallotStatus.CLOSED,f.ballots.state().ballots().get(ballot.definition().id()).status());assertTrue(f.affairs.effects().isEmpty());Path path=root.resolve("proceedings.json");var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.getAsJsonObject("state").getAsJsonObject("pending").addProperty("actor",new UUID(0,999).toString());Files.writeString(path,json+"\n");assertThrows(IllegalStateException.class,f::restart);assertTrue(new ForeignAffairsService(root.resolve("foreign.json")).effects().isEmpty());}
    @Test void missingAndOrphanReceiptsRefuseRestart(@TempDir Path root)throws Exception {
        Path missing=Files.createDirectory(root.resolve("missing"));var f=new Fixture(missing);var ballot=f.open(f.motion(ForeignKind.DECLARE_WAR,0));f.votes(ballot,6);f.service.close(ballot.definition().id(),HOST);Files.writeString(missing.resolve("foreign.json"),"{\"schema\":1,\"effects\":[]}\n");assertThrows(IOException.class,f::restart);
        Path orphan=Files.createDirectory(root.resolve("orphan"));var g=new Fixture(orphan);g.affairs.apply(new ForeignAffairsService.Effect(UUID.randomUUID(),UUID.randomUUID(),g.realm,0,ForeignAffairsService.Kind.DECLARE_WAR,"Orphan"));assertThrows(IOException.class,g::restart);
    }
    @Test void abaInterventionDoesNotOverwriteAndMajorityIsRefused(@TempDir Path root)throws Exception {
        var f=new Fixture(root);assertThrows(IllegalArgumentException.class,()->new Motion(UUID.randomUUID(),"Foreign motion","Body",HOST,"",BallotVisibility.SECRET,Threshold.MAJORITY,true,Set.of(),MotionStatus.SUBMITTED,List.of(),List.of(),new ForeignSubject(ForeignKind.DECLARE_WAR,f.realm,0)));
        var ballot=f.open(f.motion(ForeignKind.DECLARE_WAR,0));f.votes(ballot,6);f.affairs.apply(new ForeignAffairsService.Effect(UUID.randomUUID(),UUID.randomUUID(),f.realm,0,ForeignAffairsService.Kind.DECLARE_WAR,"Other war"));f.affairs.apply(new ForeignAffairsService.Effect(UUID.randomUUID(),UUID.randomUUID(),f.realm,1,ForeignAffairsService.Kind.MAKE_PEACE,"Other peace"));assertThrows(IllegalStateException.class,()->f.service.close(ballot.definition().id(),HOST));assertNotNull(f.service.state().pending());assertEquals(2,f.affairs.view(f.realm).revision());assertFalse(f.affairs.view(f.realm).atWar());
    }
}
