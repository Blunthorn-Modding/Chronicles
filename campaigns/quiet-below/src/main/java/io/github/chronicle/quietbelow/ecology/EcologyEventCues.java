package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.server.*;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import java.io.IOException;
import java.util.*;

/** Scenario-specific readiness/growth facts. Core Events retain all delivery and recurrence authority. */
public final class EcologyEventCues {
    private static final String HOLD = "quiet_below:event_cues";
    private static final Map<MinecraftServer, EcologyCueLedger> WORLDS = new IdentityHashMap<>();
    private EcologyEventCues() { }
    public static void register() {
        EventSources.registerProvider("quiet_below:station_ready", "Station first powered", "The first time a physical Altar or Forge is ready on living sculk.",
                server -> List.of(new EventSources.Choice("*", "Either station", "First Altar or Forge"),
                        new EventSources.Choice("quiet_below:ancient_altar", "Ancient Altar", "First powered Altar"),
                        new EventSources.Choice("quiet_below:ancient_forge", "Ancient Forge", "First powered Forge")), List.of(), false);
        EventSources.registerProvider("quiet_below:growth_step", "Network growth step", "Each new 5,000 living connected blocks above the initial count; old regrowth does not repeat it.",
                server -> List.of(new EventSources.Choice("network", "Campaign sculk network", "Living connected sculk")),
                List.of(new EventSources.Choice("5000", "5,000 more blocks", "High-water growth step")), false);
        EventSources.registerAuthority("qbcue:", (server, receipt) -> {
            EcologyCueLedger ledger = WORLDS.get(server);
            if (ledger == null) return false;
            String key = receipt.authority().substring(6);
            if (!(key.startsWith("station:") && receipt.type().equals("quiet_below:station_ready") && key.substring(8).equals(receipt.target()) && receipt.activity().isEmpty()
                    || key.startsWith("growth:") && receipt.type().equals("quiet_below:growth_step") && receipt.target().equals("network") && receipt.activity().equals("5000")))
                throw new IllegalStateException("Ecology cue source identity changed");
            try { return ledger.proves(key); } catch (IOException failure) { throw new IllegalStateException("Ecology cue history is unavailable", failure); }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { var ledger = new EcologyCueLedger(server.getSavePath(WorldSavePath.ROOT).resolve("quiet-below/ecology-cues")); ledger.load(); WORLDS.put(server, ledger); }
            catch (IOException failure) { fail(server, failure); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }
    static void tick(MinecraftServer server, EcologyEngine engine) {
        EcologyCueLedger ledger = WORLDS.get(server);
        if (ledger == null || server.getTicks() % 20 != 0 || !QuietBelowEcology.playing(server) || !engine.stationPowerAssessed()) return;
        try {
            if (!ledger.initialized()) ledger.initialize(engine.countedSculk());
            if (engine.countedSculk() >= ledger.nextThreshold()) {
                var source = new EventSource("quiet_below:growth_step", "network", "5000");
                String receipt = prepare(server, ledger.nextGrowthKey(), source);
                ledger.publishGrowth(); EventSources.complete(server, receipt, true);
            }
            var snapshot = ManagedBlocks.snapshot(server);
            if (!snapshot.ready()) return;
            for (var instance : snapshot.instances()) {
                String type = instance.typeId();
                if (!List.of("quiet_below:ancient_altar", "quiet_below:ancient_forge").contains(type) || ledger.stationSeen(type)) continue;
                var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(instance.dimension())));
                var p = instance.position(); var position = new BlockPos(p.x(), p.y(), p.z());
                if (world == null || !world.isChunkLoaded(position) || !net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(position).getBlock()).toString().equals(type)
                        || QuietBelowEcology.stationPower(world, position) != null) continue;
                String receipt = prepare(server, "station:" + type, new EventSource("quiet_below:station_ready", type, ""));
                ledger.publishStation(type); EventSources.complete(server, receipt, true);
            }
        } catch (EventSources.Unavailable failure) { /* Source authority already holds the world for exact recovery. */ }
        catch (IOException | RuntimeException failure) { WORLDS.remove(server); fail(server, failure); }
    }
    private static String prepare(MinecraftServer server, String key, EventSource source) {
        String authority = "qbcue:" + key;
        return EventSources.prepare(server, EventSources.id(authority, source.type(), source.target(), source.activity()), source, null, true, authority);
    }
    private static void fail(MinecraftServer server, Exception failure) {
        RuntimeHolds.hold(server, HOLD, "Ecology Event cue evidence needs repair; preserve the saved cue history and restart after restoring it.");
        QuietBelow.LOGGER.error("Ecology Event cues stopped; evidence was preserved", failure);
    }
}
