package io.github.chronicle.core.roles;

import io.github.chronicle.core.spawn.SpawnBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlayerRoleConfigTest {
 @TempDir Path dir;
 private static PlayerRole role(String id,String name,int cap){return new PlayerRole(id,name,"A plain role.",true,cap,new SpawnBinding("test:start","test:point",1),null,List.of(),List.of());}
 @Test void onlyFirstCreationUsesDefaultsAndSuccessfulSaveBacksUpBytes() throws Exception {Path file=dir.resolve("config/chronicle/player-roles.yaml");var one=role("test:one","One",2);var config=new PlayerRoleConfig(file);assertEquals(Map.of(one.id(),one),config.loadOrCreate(Map.of(one.id(),one)));String before=Files.readString(file);assertEquals("Changed",config.replace(role("test:one","Changed",2),one).get("test:one").name());assertEquals(before,Files.readString(file.resolveSibling("player-roles.yaml.bak")));assertEquals(Map.of("test:one",role("test:one","Changed",2)),new PlayerRoleConfig(file).loadOrCreate(Map.of("test:one",one,"test:two",role("test:two","Two",1))));}
 @Test void yamlEncodingRoundTripsAndDoesNotRejectOrdinaryProse() {var source=new PlayerRole("test:one","One","The edge remembers... * & quietly.",true,1,null,null,List.of(new StartingItem("{\"count\":1,\"id\":\"minecraft:apple\"}")),List.of());String yaml=PlayerRoleConfig.encode(Map.of(source.id(),source));assertFalse(yaml.startsWith("{"));assertEquals(Map.of(source.id(),source),PlayerRoleConfig.decode(yaml));}
 @Test void rejectsYamlAttacksUnknownFieldsAndExternalWritesWithoutReplacement() throws Exception {Path file=dir.resolve("roles.yaml");String alias="schemaVersion: 1\nroles: &bad []\n";Files.writeString(file,alias);assertThrows(IOException.class,()->new PlayerRoleConfig(file).loadOrCreate(Map.of()));assertEquals(alias,Files.readString(file));String unknown="schemaVersion: 1\nroles: []\nfuture: true\n";Files.writeString(file,unknown);assertThrows(IOException.class,()->new PlayerRoleConfig(file).loadOrCreate(Map.of()));assertEquals(unknown,Files.readString(file));Files.delete(file);var original=role("test:one","One",1);var config=new PlayerRoleConfig(file);config.loadOrCreate(Map.of(original.id(),original));Files.writeString(file,Files.readString(file)+"\n");assertThrows(IOException.class,()->config.replace(role("test:one","Changed",1),original));assertThrows(IOException.class,()->config.create(role("test:two","Two",1)));}
 @Test void requiresExactSchemaAndStrictTypes(){assertThrows(IllegalArgumentException.class,()->PlayerRoleConfig.decode("schemaVersion: 1\nroles: []\n---\n"));assertThrows(IllegalArgumentException.class,()->PlayerRoleConfig.decode("schemaVersion: 1\nroles:\n - id: test:one\n   name: One\n"));assertThrows(IllegalArgumentException.class,()->PlayerRoleConfig.decode("!!java.lang.Runtime {}"));assertThrows(IllegalArgumentException.class,()->new PlayerRole("test:one","One","",true,0,null,null,List.of(),List.of()));}
 @Test void boundsYamlNestingBeforeSchemaConversion(){String nested="x";for(int depth=0;depth<18;depth++)nested="["+nested+"]";String yaml="schemaVersion: 1\nroles:\n - "+nested;assertThrows(IllegalArgumentException.class,()->PlayerRoleConfig.decode(yaml));}
 @Test void missingInitializedConfigDoesNotResurrectDeletedDefaults() throws Exception {Path file=dir.resolve("roles.yaml");var one=role("test:one","One",1);var config=new PlayerRoleConfig(file);config.loadOrCreate(Map.of(one.id(),one));config.delete(one.id(),one);Files.delete(file);assertThrows(IOException.class,()->new PlayerRoleConfig(file).loadOrCreate(Map.of(one.id(),one)));assertFalse(Files.exists(file));}
 @Test void assignmentCountsAreLogicallyBounded(){var map=new java.util.LinkedHashMap<java.util.UUID,String>();for(int n=0;n<513;n++)map.put(new java.util.UUID(0,n),"test:one");assertThrows(IllegalArgumentException.class,()->new PlayerRoleAssignments(0,map,java.util.Set.of(),true));}
 @Test void failedInitialCreationDoesNotPoisonFutureInitialization() throws Exception {Path file=dir.resolve("roles.yaml");var config=new PlayerRoleConfig(file,()->{throw new IOException("injected initial save failure");});assertThrows(IOException.class,()->config.loadOrCreate(Map.of()));assertFalse(Files.exists(file));assertFalse(Files.exists(file.resolveSibling("roles.yaml.initialized")));assertEquals(Map.of(),new PlayerRoleConfig(file).loadOrCreate(Map.of()));}
}
