package io.github.chronicle.core.encounters;

import io.github.chronicle.core.history.ImmutableHistoryArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EncounterFoundationTest {
    @Test void pinnedPlansRoundTripAndHashExactTargetRevision() throws Exception {
        EncounterOperation legacy=operation(UUID.randomUUID());Optional<EncounterTarget> target=Optional.of(new EncounterTarget(EncounterTarget.Kind.REGION,"yard",3));
        EncounterOperation pinned=new EncounterOperation(legacy.operationId(),legacy.actorId(),legacy.groupId(),legacy.groupFingerprint(),EncounterOperation.planHash(legacy.operationId(),legacy.actorId(),legacy.groupFingerprint(),legacy.entries(),target),legacy.entries(),legacy.terminal(),target);
        EncounterOperationStore store=store();store.load();store.create(pinned);assertEquals(pinned,store.load().operations().get(pinned.operationId()));
        Optional<EncounterTarget> moved=Optional.of(new EncounterTarget(EncounterTarget.Kind.REGION,"yard",4));
        assertThrows(IllegalArgumentException.class,()->new EncounterOperation(pinned.operationId(),pinned.actorId(),pinned.groupId(),pinned.groupFingerprint(),pinned.planHash(),pinned.entries(),pinned.terminal(),moved));
        store.cancelOpen(pinned.operationId());store.archiveComplete(pinned.operationId());assertEquals(target,store.archived(pinned.operationId()).orElseThrow().target());
    }

    @Test void legacyJournalAndRawArchiveKeepOriginalHashAndExactPublicationBytes() throws Exception {
        EncounterOperation planned=operation(UUID.randomUUID());UUID entity=planned.entries().getFirst().entityId();EncounterOperation legacy=planned.claim(entity).markSpawned(entity).complete();
        byte[] modern=EncounterOperationStore.encode(new EncounterOperationJournal(7,Map.of(legacy.operationId(),legacy)));
        // A schema-1 record ends at its terminal string, before the v2 optional-target byte.
        byte[] oldJournal=Arrays.copyOf(modern,modern.length-1);java.nio.ByteBuffer.wrap(oldJournal).putInt(4,1);
        Files.write(directory.resolve("operations.bin"),oldJournal);EncounterOperationStore store=store();assertEquals(legacy,store.load().operations().get(legacy.operationId()));
        byte[] raw=Arrays.copyOfRange(oldJournal,20,oldJournal.length);ImmutableHistoryArchive archive=new ImmutableHistoryArchive(directory.resolve("history"),"encounter-operation",64*1024);
        archive.publish(legacy.operationId().toString(),raw);assertEquals(legacy,store.archived(legacy.operationId()).orElseThrow());
        store.archiveComplete(legacy.operationId());assertArrayEquals(raw,archive.read(legacy.operationId().toString()).orElseThrow());
        assertEquals(legacy.planHash(),store.ensure(legacy).planHash());
    }
    @Test void blockedUnattemptedPlanCanBeCancelledWithoutInventingUncertainty() throws Exception {
        EncounterOperationStore store=store();store.load();EncounterOperation planned=operationWithTwoEntries(UUID.randomUUID());store.create(planned);
        assertThrows(IllegalStateException.class,()->store.uncertain(planned.operationId()));
        EncounterOperation cancelled=store.cancelOpen(planned.operationId());
        assertEquals(EncounterOperation.Terminal.CANCELLED,cancelled.terminal());
        assertEquals(planned.entries(),cancelled.entries());assertEquals(planned.planHash(),cancelled.planHash());
        assertEquals(cancelled,store.load().operations().get(planned.operationId()));
        assertThrows(IllegalStateException.class,()->store.claim(planned.operationId(),planned.entries().getFirst().entityId()));
        store.archiveComplete(planned.operationId());assertEquals(cancelled,store.archived(planned.operationId()).orElseThrow());
        assertEquals(cancelled,store.ensure(planned));
    }

    @Test void cancellationPreservesConfirmedStagesButCannotBypassUncertainReview() throws Exception {
        EncounterOperationStore store=store();store.load();EncounterOperation planned=operationWithTwoEntries(UUID.randomUUID());store.create(planned);
        UUID entity=planned.entries().getFirst().entityId();store.claim(planned.operationId(),entity);store.markSpawned(planned.operationId(),entity);
        EncounterOperation before=store.state().operations().get(planned.operationId());
        assertEquals(before.entries(),store.cancelOpen(planned.operationId()).entries());
        EncounterOperation uncertain=operation(UUID.randomUUID());store.create(uncertain);store.claim(uncertain.operationId(),uncertain.entries().getFirst().entityId());store.uncertain(uncertain.operationId());
        assertThrows(IllegalStateException.class,()->store.cancelOpen(uncertain.operationId()));
    }
    @TempDir Path directory;
    private static final EncounterTypeAllowlist ALLOWED=EncounterTypeAllowlist.only(List.of("minecraft:zombie","minecraft:skeleton"));
    private static EncounterGroup group(String id){return new EncounterGroup(id,"Patrol","A bounded group",List.of(new EncounterMember("minecraft:zombie",2),new EncounterMember("minecraft:skeleton",1)));}
    private static EncounterOperation operation(UUID id){UUID actor=UUID.randomUUID();List<EncounterSpawnEntry> entries=List.of(new EncounterSpawnEntry(UUID.randomUUID(),"minecraft:zombie","minecraft:overworld",1,2,3,EncounterSpawnEntry.Stage.PLANNED));String fingerprint=EncounterOperation.fingerprint(group("example:patrol"));return new EncounterOperation(id,actor,"example:patrol",fingerprint,EncounterOperation.planHash(id,actor,fingerprint,entries),entries,EncounterOperation.Terminal.OPEN);}
    private static EncounterOperation operationWithTwoEntries(UUID id){UUID actor=UUID.randomUUID();List<EncounterSpawnEntry> entries=List.of(new EncounterSpawnEntry(UUID.randomUUID(),"minecraft:zombie","minecraft:overworld",1,2,3,EncounterSpawnEntry.Stage.PLANNED),new EncounterSpawnEntry(UUID.randomUUID(),"minecraft:skeleton","minecraft:overworld",4,5,6,EncounterSpawnEntry.Stage.PLANNED));String fingerprint=EncounterOperation.fingerprint(group("example:patrol"));return new EncounterOperation(id,actor,"example:patrol",fingerprint,EncounterOperation.planHash(id,actor,fingerprint,entries),entries,EncounterOperation.Terminal.OPEN);}
    private EncounterOperationStore store(){return new EncounterOperationStore(directory.resolve("operations.bin"),new ImmutableHistoryArchive(directory.resolve("history"),"encounter-operation",64*1024));}

    @Test void validatesPortableBoundedGroupsAndLogicalBindings(){assertThrows(IllegalArgumentException.class,()->new EncounterGroup("bad","x","",List.of(new EncounterMember("minecraft:zombie",1))));assertThrows(IllegalArgumentException.class,()->new EncounterGroup("example:x","x".repeat(81),"",List.of(new EncounterMember("minecraft:zombie",1))));assertThrows(IllegalArgumentException.class,()->new EncounterGroup("example:x","x","",List.of(new EncounterMember("minecraft:zombie",32),new EncounterMember("minecraft:skeleton",32),new EncounterMember("minecraft:creeper",1))));assertThrows(IllegalArgumentException.class,()->new EncounterGroup("example:x","x","",List.of(new EncounterMember("minecraft:zombie",1),new EncounterMember("minecraft:zombie",1))));assertEquals(new EncounterBinding("example:x",EncounterBinding.TargetKind.POINT,"point",1).siteId(),"point");assertThrows(IllegalArgumentException.class,()->group("example:x").requireAllowed(EncounterTypeAllowlist.only(List.of("minecraft:zombie"))));}

    @Test void portableAllowlistAcceptsCuratedFriendlyMobsWithoutWeakeningTypeChecks(){new EncounterGroup("example:village","Village","",List.of(new EncounterMember("minecraft:villager",1),new EncounterMember("minecraft:cow",2))).requireAllowed(EncounterTypeAllowlist.only(List.of("minecraft:villager","minecraft:cow")));assertThrows(IllegalArgumentException.class,()->new EncounterGroup("example:boss","Boss","",List.of(new EncounterMember("minecraft:warden",1))).requireAllowed(EncounterTypeAllowlist.only(List.of("minecraft:villager","minecraft:cow"))));}

    @Test void strictConfigMergesDefaultsAndRejectsUnknownDuplicateInvalidUtf8AndExternalChanges() throws Exception {
        Path file = directory.resolve("groups.json");
        EncounterGroupStore config = new EncounterGroupStore(file, ALLOWED);
        EncounterGroup defaultGroup = group("example:default");
        EncounterGroupRegistry loaded = config.loadOrCreate(new EncounterGroupRegistry(0, Map.of(defaultGroup.id(), defaultGroup)));
        assertEquals(defaultGroup, loaded.groups().get(defaultGroup.id()));
        String original = Files.readString(file);

        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"groups\":[],\"future\":true}");
        assertThrows(IOException.class, () -> config.loadOrCreate(EncounterGroupRegistry.empty()));
        assertTrue(Files.readString(file).contains("future"));
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"groups\":[],\"groups\":[]}");
        assertThrows(IOException.class, () -> config.loadOrCreate(EncounterGroupRegistry.empty()));
        Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> config.loadOrCreate(EncounterGroupRegistry.empty()));

        Files.writeString(file, original);
        final EncounterGroupRegistry reloaded = config.loadOrCreate(EncounterGroupRegistry.empty());
        Files.writeString(file, original + "\n");
        assertThrows(IOException.class, () -> config.replace(reloaded, group("example:new"), null));
        assertEquals(original + "\n", Files.readString(file));
    }

    @Test void strictBindingStoreRoundTripsPinsAndPreservesCorruptEvidence()throws Exception{
        Path file=directory.resolve("bindings.json");EncounterBindingStore store=new EncounterBindingStore(file);
        EncounterBindingRegistry empty=store.loadOrCreate();EncounterBinding binding=new EncounterBinding("example:patrol",EncounterBinding.TargetKind.REGION,"yard",9);
        EncounterBindingRegistry saved=store.save(empty,empty.replace(binding,null));assertEquals(binding,saved.bindings().get(binding.groupId()));
        assertEquals(saved,EncounterBindingStore.decode(EncounterBindingStore.encode(saved)));
        String corrupt="{\"schemaVersion\":1,\"revision\":1,\"bindings\":[],\"future\":true}";Files.writeString(file,corrupt);
        assertThrows(IOException.class,()->new EncounterBindingStore(file).loadOrCreate());assertEquals(corrupt,Files.readString(file));
    }

    @Test void combinedAuthoringPublicationNeverExposesOneNewHalf()throws Exception{
        Path root=directory.resolve("combined");Path file=root.resolve("encounter-authoring.json"),marker=root.resolve("encounter-authoring.initialized"),legacyGroups=root.resolve("encounter-groups.json"),legacyBindings=root.resolve("encounter-bindings.json");
        EncounterAuthoringStore store=new EncounterAuthoringStore(file,marker,legacyGroups,legacyBindings,ALLOWED);EncounterGroup initialGroup=group("example:patrol");EncounterAuthoringCatalog initial=store.loadOrCreate(new EncounterGroupRegistry(0,Map.of(initialGroup.id(),initialGroup)));
        EncounterBinding initialBinding=new EncounterBinding(initialGroup.id(),EncounterBinding.TargetKind.POINT,"yard",1);EncounterAuthoringCatalog published=new EncounterAuthoringCatalog(initial.groups().replace(initialGroup,initialGroup),initial.bindings().replace(initialBinding,null));published=store.save(initial,published);
        EncounterGroup edited=new EncounterGroup(initialGroup.id(),"Edited patrol","New composition text",initialGroup.members());EncounterBinding moved=new EncounterBinding(initialGroup.id(),EncounterBinding.TargetKind.REGION,"field",2);EncounterAuthoringCatalog attempted=new EncounterAuthoringCatalog(published.groups().replace(edited,initialGroup),published.bindings().replace(moved,initialBinding));
        EncounterAuthoringStore failing=new EncounterAuthoringStore(file,marker,legacyGroups,legacyBindings,ALLOWED,()->{throw new IOException("injected before atomic move");});EncounterAuthoringCatalog observed=failing.loadOrCreate(EncounterGroupRegistry.empty());assertEquals(published,observed);assertThrows(IOException.class,()->failing.save(observed,attempted));
        EncounterAuthoringCatalog after=new EncounterAuthoringStore(file,marker,legacyGroups,legacyBindings,ALLOWED).loadOrCreate(EncounterGroupRegistry.empty());assertEquals(initialGroup,after.groups().groups().get(initialGroup.id()));assertEquals(initialBinding,after.bindings().bindings().get(initialGroup.id()));
    }

    @Test void combinedAuthoringInitializationFailsClosedForMissingOrAsymmetricEvidence()throws Exception{
        Path asymmetric=directory.resolve("asymmetric"),groups=asymmetric.resolve("encounter-groups.json"),bindings=asymmetric.resolve("encounter-bindings.json");Files.createDirectories(asymmetric);Files.writeString(groups,EncounterGroupStore.encode(new EncounterGroupRegistry(0,Map.of(group("example:patrol").id(),group("example:patrol")))));
        assertThrows(IOException.class,()->new EncounterAuthoringStore(asymmetric.resolve("encounter-authoring.json"),asymmetric.resolve("encounter-authoring.initialized"),groups,bindings,ALLOWED).loadOrCreate(EncounterGroupRegistry.empty()));assertFalse(Files.exists(asymmetric.resolve("encounter-authoring.json")));
        Path dangling=directory.resolve("dangling"),danglingGroups=dangling.resolve("encounter-groups.json"),danglingBindings=dangling.resolve("encounter-bindings.json");Files.createDirectories(dangling);Files.writeString(danglingGroups,EncounterGroupStore.encode(EncounterGroupRegistry.empty()));Files.writeString(danglingBindings,EncounterBindingStore.encode(new EncounterBindingRegistry(0,Map.of("example:missing",new EncounterBinding("example:missing",EncounterBinding.TargetKind.POINT,"yard",1)))));
        assertThrows(IOException.class,()->new EncounterAuthoringStore(dangling.resolve("encounter-authoring.json"),dangling.resolve("encounter-authoring.initialized"),danglingGroups,danglingBindings,ALLOWED).loadOrCreate(EncounterGroupRegistry.empty()));
        Path initialized=directory.resolve("initialized"),catalog=initialized.resolve("encounter-authoring.json"),initializedMarker=initialized.resolve("encounter-authoring.initialized");EncounterAuthoringStore first=new EncounterAuthoringStore(catalog,initializedMarker,initialized.resolve("encounter-groups.json"),initialized.resolve("encounter-bindings.json"),ALLOWED);first.loadOrCreate(EncounterGroupRegistry.empty());Files.delete(catalog);
        assertThrows(IOException.class,()->new EncounterAuthoringStore(catalog,initializedMarker,initialized.resolve("encounter-groups.json"),initialized.resolve("encounter-bindings.json"),ALLOWED).loadOrCreate(EncounterGroupRegistry.empty()));
    }

    @Test void validLegacyPairImportsOnceWithoutDeletingItsEvidence()throws Exception{
        Path root=directory.resolve("legacy-import"),groups=root.resolve("encounter-groups.json"),bindings=root.resolve("encounter-bindings.json");Files.createDirectories(root);EncounterGroup legacy=group("example:legacy");EncounterBinding binding=new EncounterBinding(legacy.id(),EncounterBinding.TargetKind.POINT,"yard",3);Files.writeString(groups,EncounterGroupStore.encode(new EncounterGroupRegistry(7,Map.of(legacy.id(),legacy))));Files.writeString(bindings,EncounterBindingStore.encode(new EncounterBindingRegistry(5,Map.of(binding.groupId(),binding))));
        Path catalog=root.resolve("encounter-authoring.json"),marker=root.resolve("encounter-authoring.initialized");EncounterAuthoringCatalog imported=new EncounterAuthoringStore(catalog,marker,groups,bindings,ALLOWED).loadOrCreate(EncounterGroupRegistry.empty());assertEquals(7,imported.groups().revision());assertEquals(5,imported.bindings().revision());assertEquals(binding,imported.bindings().bindings().get(legacy.id()));assertTrue(Files.isRegularFile(catalog)&&Files.isRegularFile(marker)&&Files.isRegularFile(groups)&&Files.isRegularFile(bindings));
        assertEquals(imported,new EncounterAuthoringStore(catalog,marker,groups,bindings,ALLOWED).loadOrCreate(EncounterGroupRegistry.empty()));
    }

    @Test void initializedCatalogIsAuthoritativeAndDoesNotResurrectDeletedPackagedDefault()throws Exception{
        Path root=directory.resolve("default-delete"),catalog=root.resolve("encounter-authoring.json"),marker=root.resolve("encounter-authoring.initialized"),legacyGroups=root.resolve("encounter-groups.json"),legacyBindings=root.resolve("encounter-bindings.json");EncounterGroup packaged=group("example:packaged");EncounterGroupRegistry defaults=new EncounterGroupRegistry(0,Map.of(packaged.id(),packaged));EncounterAuthoringStore store=new EncounterAuthoringStore(catalog,marker,legacyGroups,legacyBindings,ALLOWED);EncounterAuthoringCatalog initialized=store.loadOrCreate(defaults);EncounterAuthoringCatalog deleted=new EncounterAuthoringCatalog(initialized.groups().remove(packaged.id(),packaged),initialized.bindings().remove(packaged.id(),null));store.save(initialized,deleted);
        EncounterAuthoringCatalog restarted=new EncounterAuthoringStore(catalog,marker,legacyGroups,legacyBindings,ALLOWED).loadOrCreate(defaults);assertTrue(restarted.groups().groups().isEmpty());assertTrue(restarted.bindings().bindings().isEmpty());
    }

    @Test void missingJournalStartsEmptyAndCorruptJournalNeverOverwrites() throws Exception {EncounterOperationStore store=store();assertEquals(EncounterOperationJournal.empty(),store.load());Path file=directory.resolve("operations.bin");Files.write(file,new byte[]{1,2,3});assertThrows(IOException.class,()->store.load());assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(file));}

    @Test void planIdentityCannotBeTamperedOrRegenerated() {EncounterOperation operation=operation(UUID.randomUUID());assertThrows(IllegalArgumentException.class,()->new EncounterOperation(operation.operationId(),operation.actorId(),operation.groupId(),operation.groupFingerprint(),"0".repeat(64),operation.entries(),EncounterOperation.Terminal.OPEN));}

    @Test void claimIsDurableBeforeSpawnAndOldUuidCannotReplanOrReplay() throws Exception {EncounterOperationStore store=store();store.load();EncounterOperation initial=operation(UUID.randomUUID());store.create(initial);UUID entity=initial.entries().get(0).entityId();assertEquals(EncounterSpawnEntry.Stage.CLAIMED,store.claim(initial.operationId(),entity).entries().get(0).stage());EncounterOperation afterRestart=store.load().operations().get(initial.operationId());assertEquals(EncounterSpawnEntry.Stage.CLAIMED,afterRestart.entries().get(0).stage());assertThrows(IllegalStateException.class,()->store.claim(initial.operationId(),entity));assertThrows(IllegalStateException.class,()->store.create(initial));assertThrows(IllegalStateException.class,afterRestart::complete);}

    @Test void uncertainClaimIsHeldAndReviewedWithoutReplayOrStageRewrite() throws Exception {EncounterOperationStore store=store();store.load();EncounterOperation initial=operation(UUID.randomUUID());store.create(initial);UUID entity=initial.entries().get(0).entityId();store.claim(initial.operationId(),entity);EncounterOperation uncertain=store.uncertain(initial.operationId());assertEquals(EncounterOperation.Terminal.UNCERTAIN,uncertain.terminal());assertEquals(EncounterSpawnEntry.Stage.CLAIMED,uncertain.entries().get(0).stage());assertThrows(IllegalStateException.class,()->store.markSpawned(initial.operationId(),entity));assertThrows(IllegalStateException.class,()->store.uncertain(initial.operationId()));EncounterOperation reviewed=store.reviewUncertain(initial.operationId());assertEquals(EncounterOperation.Terminal.REVIEWED,reviewed.terminal());assertEquals(uncertain.entries(),reviewed.entries());assertThrows(IllegalStateException.class,()->store.claim(initial.operationId(),entity));store.archiveComplete(initial.operationId());assertEquals(EncounterOperation.Terminal.REVIEWED,store.archived(initial.operationId()).orElseThrow().terminal());assertEquals(EncounterOperation.Terminal.REVIEWED,store.ensure(initial).terminal());}

    @Test void reviewTransitionRequiresExactUncertainIdentity(){EncounterOperation open=operation(UUID.randomUUID());assertThrows(IllegalStateException.class,open::reviewed);EncounterOperation claimed=open.claim(open.entries().getFirst().entityId());EncounterOperation reviewed=claimed.uncertain().reviewed();assertEquals(open.planHash(),reviewed.planHash());assertEquals(claimed.entries(),reviewed.entries());assertThrows(IllegalStateException.class,reviewed::reviewed);}

    @Test void uncertaintyPreservesUnattemptedPlannedEntriesExactly() throws Exception {EncounterOperationStore store=store();store.load();EncounterOperation initial=operationWithTwoEntries(UUID.randomUUID());store.create(initial);store.claim(initial.operationId(),initial.entries().get(0).entityId());EncounterOperation uncertain=store.uncertain(initial.operationId());assertEquals(EncounterSpawnEntry.Stage.CLAIMED,uncertain.entries().get(0).stage());assertEquals(EncounterSpawnEntry.Stage.PLANNED,uncertain.entries().get(1).stage());EncounterOperation afterRestart=store.load().operations().get(initial.operationId());assertEquals(EncounterOperation.Terminal.UNCERTAIN,afterRestart.terminal());assertThrows(IllegalStateException.class,()->store.claim(initial.operationId(),initial.entries().get(1).entityId()));}

    @Test void storeRejectsDirectPlannedToSpawnedAndOldUuidContentTampering() throws Exception {EncounterOperationStore store=store();store.load();EncounterOperation initial=operation(UUID.randomUUID());store.create(initial);EncounterSpawnEntry planned=initial.entries().get(0);EncounterSpawnEntry jumped=new EncounterSpawnEntry(planned.entityId(),planned.entityType(),planned.dimension(),planned.x(),planned.y(),planned.z(),EncounterSpawnEntry.Stage.SPAWNED);EncounterOperation jumpedOperation=new EncounterOperation(initial.operationId(),initial.actorId(),initial.groupId(),initial.groupFingerprint(),EncounterOperation.planHash(initial.operationId(),initial.actorId(),initial.groupFingerprint(),List.of(jumped)),List.of(jumped),EncounterOperation.Terminal.OPEN);assertThrows(IllegalArgumentException.class,()->store.save(new EncounterOperationJournal(1,Map.of(initial.operationId(),jumpedOperation))));UUID alteredActor=UUID.randomUUID();EncounterOperation altered=new EncounterOperation(initial.operationId(),alteredActor,initial.groupId(),initial.groupFingerprint(),EncounterOperation.planHash(initial.operationId(),alteredActor,initial.groupFingerprint(),initial.entries()),initial.entries(),EncounterOperation.Terminal.OPEN);assertThrows(IllegalArgumentException.class,()->store.save(new EncounterOperationJournal(1,Map.of(initial.operationId(),altered))));}

    @Test void completeOperationArchivesExactlyBeforePruningAndCapacityFailsClosed() throws Exception {EncounterOperationStore store=store();store.load();EncounterOperation first=operation(UUID.randomUUID());store.create(first);UUID entity=first.entries().get(0).entityId();store.claim(first.operationId(),entity);store.markSpawned(first.operationId(),entity);store.complete(first.operationId());store.archiveComplete(first.operationId());assertTrue(store.load().operations().isEmpty());assertEquals(EncounterOperation.Terminal.COMPLETE,store.archived(first.operationId()).orElseThrow().terminal());assertThrows(IllegalStateException.class,()->store.create(first));for(int i=0;i<EncounterOperationJournal.MAX_LIVE_OPERATIONS;i++)store.create(operation(UUID.randomUUID()));assertThrows(IllegalStateException.class,()->store.create(operation(UUID.randomUUID())));}

    @Test void archivePublicationIsRetryableBeforePruneAndCorruptionRefusesIdentityReuse()throws Exception{
        EncounterOperationStore store=store();store.load();EncounterOperation first=operation(UUID.randomUUID());store.create(first);UUID entity=first.entries().getFirst().entityId();store.claim(first.operationId(),entity);store.markSpawned(first.operationId(),entity);store.complete(first.operationId());
        // Publish the exact payload through the normal operation, then restore the still-live journal to model interruption before pruning.
        byte[] live=Files.readAllBytes(directory.resolve("operations.bin"));store.archiveComplete(first.operationId());Files.write(directory.resolve("operations.bin"),live);
        EncounterOperationStore retry=store();assertEquals(EncounterOperation.Terminal.COMPLETE,retry.load().operations().get(first.operationId()).terminal());retry.archiveComplete(first.operationId());assertTrue(retry.state().operations().isEmpty());
        Path archive;try(var files=Files.list(directory.resolve("history"))){archive=files.filter(Files::isRegularFile).findFirst().orElseThrow();}Files.write(archive,new byte[]{1,2,3});
        assertThrows(IOException.class,()->retry.archived(first.operationId()));assertThrows(IOException.class,()->retry.ensure(first));
    }

    @Test void repeatedCompletedRunsActuallyReclaimCapacityBeyondLiveLimit()throws Exception{EncounterOperationStore store=store();store.load();UUID first=null,last=null;for(int i=0;i<EncounterOperationJournal.MAX_LIVE_OPERATIONS+32;i++){EncounterOperation operation=operation(UUID.randomUUID());if(first==null)first=operation.operationId();last=operation.operationId();store.create(operation);UUID entity=operation.entries().getFirst().entityId();store.claim(operation.operationId(),entity);store.markSpawned(operation.operationId(),entity);store.complete(operation.operationId());store.archiveComplete(operation.operationId());}assertEquals(0,store.state().operations().size());assertEquals(EncounterOperation.Terminal.COMPLETE,store.archived(first).orElseThrow().terminal());assertEquals(EncounterOperation.Terminal.COMPLETE,store.archived(last).orElseThrow().terminal());}
}
