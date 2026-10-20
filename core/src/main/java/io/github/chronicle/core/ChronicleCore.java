package io.github.chronicle.core;

import io.github.chronicle.core.campaign.CampaignEvents;
import io.github.chronicle.core.authoring.AuthoringCatalog;
import io.github.chronicle.core.loot.LootCatalog;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.core.server.TypedChestLoot;
import io.github.chronicle.core.server.AuthoringUi;
import io.github.chronicle.core.network.AuthoringUiPayloads;
import io.github.chronicle.core.server.RuntimeHolds;
import io.github.chronicle.core.server.ChestAuthoring;
import io.github.chronicle.core.server.SpawnAuthoring;
import io.github.chronicle.core.server.AutomaticPlayerSpawns;
import io.github.chronicle.core.server.ChronicleCommands;
import io.github.chronicle.core.server.AuthoringCommands;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.Profiles;
import io.github.chronicle.core.server.SessionPresentation;
import io.github.chronicle.core.network.ProfilePayloads;
import io.github.chronicle.core.network.AuthoringPreviewPayload;
import io.github.chronicle.core.network.CoreMenuPayloads;
import io.github.chronicle.core.server.CoreMenuServer;
import io.github.chronicle.core.server.CoreMenuCommands;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.core.server.MenuPlayerDestinations;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap for Chronicle's reusable, server-safe services.
 * Owns server-authoritative sessions, campaign flags, events, and audit history.
 */
public final class ChronicleCore implements ModInitializer {
    public static final String MOD_ID = "chronicle_core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final CampaignEvents EVENTS = new CampaignEvents();
    private static final AuthoringCatalog AUTHORING = new AuthoringCatalog();
    private static final LootCatalog CHEST_TYPES = new LootCatalog();
    private static final ObservationCatalog OBSERVATIONS = new ObservationCatalog();

    /** Register campaign definitions during mod initialization, before the server starts. */
    public static CampaignEvents events() { return EVENTS; }
    public static AuthoringCatalog authoring() { return AUTHORING; }
    public static LootCatalog chestTypes() { return CHEST_TYPES; }
    public static ObservationCatalog observations() { return OBSERVATIONS; }

    @Override
    public void onInitialize() {
        AUTHORING.register(new AuthoringCatalog.Role("chronicle_core:arrival", "Starting position", "An optional oriented starting point; saving it moves no players.", AuthoringCatalog.Kind.LOCATION));
        AUTHORING.register(new AuthoringCatalog.Role("chronicle_core:respawn", "Respawn point", "A default oriented respawn location; saving it changes no vanilla respawn rules.", AuthoringCatalog.Kind.LOCATION));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ChronicleCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Profiles.registerCommands(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AuthoringCommands.register(dispatcher));
        ProfilePayloads.register();
        AuthoringUiPayloads.register();
        AuthoringUi.register();
        CoreMenuPayloads.register();
        CoreMenuServer.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CoreMenuCommands.register(dispatcher));
        MenuPlayerDestinations.register();
        AuthoringPreviewPayload.register();
        Profiles.registerNetworking();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> EVENTS.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> AUTHORING.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CHEST_TYPES.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> OBSERVATIONS.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(ChronicleRuntime::start);
        ServerLifecycleEvents.SERVER_STARTED.register(Profiles::start);
        ServerLifecycleEvents.SERVER_STARTED.register(AuthoringRuntime::start);
        ServerLifecycleEvents.SERVER_STARTED.register(MapSiteAssignments::start);
        ChestAuthoring.register();
        TypedChestLoot.register();
        SpawnAuthoring.register();
        AutomaticPlayerSpawns.register();
        PlayRuntime.register();
        ServerLifecycleEvents.SERVER_STARTED.register(SessionPresentation::changed);
        ServerTickEvents.START_SERVER_TICK.register(SessionPresentation::tick);
        ServerTickEvents.END_SERVER_TICK.register(AuthoringRuntime::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SessionPresentation.joined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Profiles.disconnected(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AuthoringRuntime.disconnected(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(ChronicleRuntime::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(Profiles::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(AuthoringRuntime::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(MapSiteAssignments::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(RuntimeHolds::stop);
        LOGGER.info("Chronicle Core initialized");
    }

}
