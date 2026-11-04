package io.github.chronicle.core.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.play.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in native smoke commands, restricted to a disposable loopback fixture. */
public final class ScenarioDefaultsTrial {
    private static final Actor ACTOR = new Actor("scenario-defaults-trial", null);
    private static final String FLAG = "quiet_below:heart_destroyed";
    private ScenarioDefaultsTrial() { }
    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_SCENARIO_DEFAULTS_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("chronicle_scenario_defaults").requires(source -> source.hasPermissionLevel(2));
            for (String verb : List.of("fresh", "prepare", "captured", "held", "release", "promoted", "run", "completed", "copied")) root.then(literal(verb).executes(c -> run(c.getSource(), verb)));
            dispatcher.register(root);
        });
    }
    private static int run(ServerCommandSource source, String verb) {
        try {
            fixture(source); var server = source.getServer(); var play = PlayRuntime.service(server); var campaign = ChronicleRuntime.service(server);
            var player = source.getPlayerOrThrow(); Path evidence = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("chronicle/scenario-defaults-trial.json");
            switch (verb) {
                case "fresh" -> {
                    require(RuntimeHolds.reason(server) == null, "startup hold: " + RuntimeHolds.reason(server));
                    require(play.state().scenarioDefaults().size() == 9 && play.state().events().size() == 6 && play.state().scenarioDrafts().size() == 3, "fresh Event defaults/drafts count");
                    require(campaign.state().flags().size() == 4 && campaign.state().flags().values().stream().noneMatch(Boolean::booleanValue), "fresh flag defaults");
                    var entries = CoreEventMenu.entries(server);
                    var sink = entries.stream().filter(e -> e.reference().equals("quiet_below:sinkhole_opening")).findFirst().orElseThrow();
                    require(sink.label().startsWith("! ") && sink.description().contains("Needs setup"), "missing sinkhole warning");
                    var heart = CoreFlagMenu.entries(server).stream().filter(e -> e.reference().equals(FLAG)).findFirst().orElseThrow();
                    require(!heart.label().startsWith("! ") && heart.description().contains("Off"), "ordinary Off flag is incorrectly warned");
                    require(CoreEventMenu.detail(player, sink.id(), false).controls().stream().anyMatch(c -> c.label().equals("Preview privately") && c.enabled()), "draft private preview unavailable");
                    require(BookRuntime.books(server).size() == 9 && TypedChestLoot.types(server).size() == 4, "default books or loot missing");
                }
                case "prepare" -> {
                    require(RuntimeHolds.reason(server) == null, "unexpected hold");
                    require(campaign.state().flags().values().stream().noneMatch(Boolean::booleanValue)&&play.state().executions().isEmpty(),"private preview changed Event progress");
                    var author = AuthoringRuntime.get(server); author.enable(player.getUuid());
                    var pos = player.getBlockPos().add(2, 0, 0); String dimension = source.getWorld().getRegistryKey().getValue().toString();
                    var here = new io.github.chronicle.core.authoring.BlockPosition(pos.getX(), pos.getY(), pos.getZ());
                    author.service().put(new io.github.chronicle.core.authoring.AuthoringSite.Region("quiet_below:sinkhole", dimension, 1, here, here), false);
                    var area = AreaChanges.enableForRegion(source, "quiet_below:sinkhole");
                    source.getWorld().setBlockState(pos, net.minecraft.block.Blocks.GOLD_BLOCK.getDefaultState());
                    AreaChanges.captureCurrent(source, area.id(), io.github.chronicle.core.areas.AreaState.END, area.revision());
                }
                case "captured" -> {
                    if (AreaChanges.busy(server)) return 0;
                    var area = AreaChanges.forRegion(server, "quiet_below:sinkhole").orElseThrow();
                    require(area.end().isPresent(), "End snapshot missing");
                    var region = (io.github.chronicle.core.authoring.AuthoringSite.Region) AuthoringRuntime.get(server).service().state().sites().get("quiet_below:sinkhole");
                    var p = region.min(); source.getWorld().setBlockState(new net.minecraft.util.math.BlockPos(p.x(), p.y(), p.z()), net.minecraft.block.Blocks.STONE.getDefaultState());
                    RuntimeHolds.hold(server, "test:scenario_defaults", "Disposable setup hold");
                    var city = new io.github.chronicle.core.authoring.BlockPosition(p.x() + 1000, p.y(), p.z());
                    AuthoringRuntime.get(server).service().put(new io.github.chronicle.core.authoring.AuthoringSite.Region("quiet_below:ancient_city", region.dimension(), 1, city, city), false);
                }
                case "held" -> {
                    require(RuntimeHolds.reason(server) != null && play.state().scenarioDrafts().contains("quiet_below:city_discovery"), "held setup promoted a draft");
                }
                case "release" -> RuntimeHolds.release(server, "test:scenario_defaults");
                case "promoted" -> {
                    if (!play.state().scenarioDrafts().isEmpty()) return 0;
                    require(play.state().events().size() == 9, "not all defaults promoted");
                    var sink = play.state().events().get("quiet_below:sinkhole_opening");
                    require(sink != null && !sink.definition().changes().isEmpty(), "sinkhole has no pinned End action");
                    require(play.state().events().get("quiet_below:city_discovery").triggerRegion().isPresent(), "city trigger has no Region pin");
                }
                case "run" -> {
                    String id = "quiet_below:sinkhole_opening"; var old = play.state().events().get(id); var d = old.definition();
                    var revised = new EventDefinition(d.id(), play.nextEventRevision(), "Scenario copy proof", d.description(), "Scenario copy proof text", d.sound(), d.audience(), d.audienceRegion(), d.changes(), d.flagChanges(), d.encounterActions(), d.formatVersion());
                    var updated = new EventConfiguration(revised, PlayState.Mode.MANUAL, Optional.empty(), play.elapsedTicks(), 0, old.requiredFlags(), old.enabled(), old.source(), old.formatVersion(), old.occurrencePolicy());
                    play.putEvent(updated, old, false); EventProgressRuntime.arm(server, updated);
                    AuthoringRuntime.get(server).disable(player.getUuid()); player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                    PlayRuntime.setSelfParticipant(player, true); campaign.start(ACTOR, null);
                    EventRuntime.run(player, id, EventRuntime.review(server, id));
                }
                case "completed" -> {
                    String id = "quiet_below:sinkhole_opening"; var execution = play.state().executions().get(id);
                    if (execution == null || execution.phase() != EventExecution.Phase.COMPLETE) return 0;
                    require(campaign.state().flag("quiet_below:sinkholes_opened"), "sinkhole did not set flag");
                    var region = (io.github.chronicle.core.authoring.AuthoringSite.Region) AuthoringRuntime.get(server).service().state().sites().get("quiet_below:sinkhole");
                    var p = region.min(); require(source.getWorld().getBlockState(new net.minecraft.util.math.BlockPos(p.x(), p.y(), p.z())).isOf(net.minecraft.block.Blocks.GOLD_BLOCK), "End snapshot not applied");
                    campaign.pause(ACTOR); var updated = play.state().events().get(id);
                    String deletedId = play.state().events().keySet().stream().filter(k -> !k.equals(id) && !k.equals("quiet_below:city_discovery")).sorted().findFirst().orElseThrow();
                    play.deleteEvent(deletedId, play.state().events().get(deletedId));
                    var exported=io.github.chronicle.core.scenario.ScenarioContentJson.decode(Files.readString(Path.of(ScenarioContentRuntime.export(server,ScenarioContentRuntime.open(server,"quiet_below:scenario")))));
                    require(exported.flags().size()==4&&exported.flags().values().stream().noneMatch(Boolean::booleanValue),"export lost flag definitions or leaked campaign progression");
                    require(!exported.events().containsKey(deletedId)&&exported.events().size()==8,"export resurrected a deleted default");
                    require(source.getWorld().getForcedChunks().isEmpty(), "forced chunks present");
                    JsonObject record = new JsonObject(); record.addProperty("edited", id); record.addProperty("deleted", deletedId); record.addProperty("fingerprint", updated.fingerprint()); record.addProperty("armed", updated.armedAtTicks());
                    record.addProperty("operation", execution.operationId().toString()); record.addProperty("cityFingerprint", play.state().events().get("quiet_below:city_discovery").fingerprint());
                    Files.createDirectories(evidence.getParent()); Files.writeString(evidence, record.toString());
                }
                case "copied" -> {
                    var record = JsonParser.parseString(Files.readString(evidence)).getAsJsonObject(); String edited = record.get("edited").getAsString(), deleted = record.get("deleted").getAsString();
                    var event = play.state().events().get(edited); require(event != null && event.fingerprint().equals(record.get("fingerprint").getAsString()) && event.armedAtTicks() == record.get("armed").getAsLong(), "edited Event/pin/time changed on copied world");
                    require(!play.state().events().containsKey(deleted) && !play.state().scenarioDrafts().contains(deleted) && play.state().scenarioDefaults().contains(deleted), "deleted Event resurrected");
                    var execution = play.state().executions().get(edited);
                    require(execution != null && execution.phase() == EventExecution.Phase.COMPLETE && execution.operationId().toString().equals(record.get("operation").getAsString()), "completion receipt changed or was lost");
                    require(play.state().events().get("quiet_below:city_discovery").fingerprint().equals(record.get("cityFingerprint").getAsString()), "city pins changed");
                    require(campaign.state().flag("quiet_below:sinkholes_opened"), "saved Event flag reset");
                    var savedRegion = (io.github.chronicle.core.authoring.AuthoringSite.Region) AuthoringRuntime.get(server).service().state().sites().get("quiet_below:sinkhole");
                    var savedPos = savedRegion.min(); require(source.getWorld().getBlockState(new net.minecraft.util.math.BlockPos(savedPos.x(), savedPos.y(), savedPos.z())).isOf(net.minecraft.block.Blocks.GOLD_BLOCK), "copied End block changed");
                    require(source.getWorld().getForcedChunks().isEmpty(), "forced chunks present");
                    require(BookRuntime.books(server).size() == 9 && TypedChestLoot.types(server).size() == 4, "fresh server did not initialize books and loot");
                }
                default -> throw new IllegalArgumentException("unknown trial");
            }
            source.sendFeedback(() -> Text.literal("Scenario defaults trial PASS " + verb), false); return 1;
        } catch (Exception error) { source.sendError(Text.literal("Scenario defaults trial FAIL " + verb + ": " + error)); return 0; }
    }
    private static void fixture(ServerCommandSource source) {
        var server = source.getServer(); Path root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root != null && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs") && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z") && Files.isRegularFile(root.resolve(".scenario-defaults-fixture")) && server.isDedicated() && !server.isOnlineMode() && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp()), "unsafe fixture");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
