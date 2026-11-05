package io.github.chronicle.core.server;

import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.scenario.ScenarioSelection;
import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.WorldSavePath;

/** Resolves before campaign initializers register defaults; no content-mod IDs are hard-coded in Core. */
public final class PackagedScenarios {
    private static Optional<String> selected = Optional.empty();
    private static boolean initialized;
    private PackagedScenarios() { }
    public static synchronized void register() {
        if (initialized) throw new IllegalStateException("Scenario selection already initialized.");
        Set<String> providers = new TreeSet<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            var value = mod.getMetadata().getCustomValue("chronicle_scenario");
            if (value != null && !providers.add(value.getAsString())) throw new IllegalStateException("Duplicate scenario metadata.");
        }
        try {
            var file = new AtomicDataFile(ScenarioAuthoringProfiles.chronicleConfig().resolve("scenario-selection.json"), 4096);
            byte[] bytes = file.load();
            var custom = ScenarioAuthoringProfiles.active();
            if (custom.isPresent()) {
                if (bytes != null) throw new IOException("Use either scenario-selection.json or a custom scenario-profile.json, not both.");
                selected = Optional.of(custom.get().content().scenarioId());
            } else if (bytes == null && providers.size() > 1 && FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
                // A client pack may join servers using different scenarios. Do not demand server config on a remote client.
                selected = Optional.empty();
            } else selected = ScenarioSelection.resolve(providers, bytes == null ? Optional.empty() : Optional.of(ScenarioSelection.decode(bytes)));
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Scenario selection refused; world and configuration were preserved. " + invalid.getMessage(), invalid);
        }
        initialized = true;
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (selected.isEmpty()) {
                if (providers.size() > 1) throw new IllegalStateException("Choose a scenario in config/chronicle/scenario-selection.json before starting an integrated server with several scenario mods.");
                return;
            }
            try {
                var marker = new AtomicDataFile(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/scenario-selection.json"), 4096);
                byte[] existing = marker.load();
                if (existing != null && !ScenarioSelection.decode(existing).equals(selected.get()))
                    throw new IOException("This map belongs to another scenario. Restore its selected scenario; do not switch an active world.");
                if (existing == null) marker.save(ScenarioSelection.encode(selected.get()));
            } catch (IOException | RuntimeException invalid) { throw new IllegalStateException("World scenario identity check failed; existing data was preserved.", invalid); }
        });
    }
    public static Optional<String> selected() { return selected; }
    public static boolean defaultsFor(String id) { return selected.filter(id::equals).isPresent() && ScenarioAuthoringProfiles.active().isEmpty(); }
    public static boolean mechanicsFor(String id, String mechanics) {
        return ScenarioAuthoringProfiles.active().map(p -> p.mechanics().contains(mechanics)).orElseGet(() -> selected.filter(id::equals).isPresent());
    }
}
