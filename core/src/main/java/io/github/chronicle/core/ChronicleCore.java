package io.github.chronicle.core;

import io.github.chronicle.core.campaign.CampaignEvents;
import io.github.chronicle.core.authoring.AuthoringCatalog;
import io.github.chronicle.core.loot.LootCatalog;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.managed.ManagedBlockCatalog;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.core.server.TypedChestLoot;
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
import io.github.chronicle.core.server.ManagedBlocks;
import io.github.chronicle.core.server.MenuPlayerDestinations;
import io.github.chronicle.core.server.GameSetupTools;
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
    private static final io.github.chronicle.core.books.BookCatalog BOOKS = new io.github.chronicle.core.books.BookCatalog();
    private static final io.github.chronicle.core.roles.PlayerRoleCatalog PLAYER_ROLES = new io.github.chronicle.core.roles.PlayerRoleCatalog();
    private static final LootCatalog CHEST_TYPES = new LootCatalog();
    private static final ObservationCatalog OBSERVATIONS = new ObservationCatalog();
    private static final ManagedBlockCatalog MANAGED_BLOCKS = new ManagedBlockCatalog();

    /** Register campaign definitions during mod initialization, before the server starts. */
    public static CampaignEvents events() { return EVENTS; }
    public static AuthoringCatalog authoring() { return AUTHORING; }
    public static io.github.chronicle.core.books.BookCatalog books() { return BOOKS; }
    public static io.github.chronicle.core.roles.PlayerRoleCatalog playerRoles() { return PLAYER_ROLES; }
    public static LootCatalog chestTypes() { return CHEST_TYPES; }
    public static ObservationCatalog observations() { return OBSERVATIONS; }
    public static ManagedBlockCatalog managedBlocks() { return MANAGED_BLOCKS; }

    @Override
    public void onInitialize() {
        io.github.chronicle.core.server.ScenarioAuthoringProfiles.register();
        io.github.chronicle.core.server.PackagedScenarios.register();
        AUTHORING.register(new AuthoringCatalog.Role("chronicle_core:arrival", "Starting position", "An optional oriented starting point; saving it moves no players.", AuthoringCatalog.Kind.LOCATION));
        AUTHORING.register(new AuthoringCatalog.Role("chronicle_core:respawn", "Respawn point", "A default oriented respawn location; saving it changes no vanilla respawn rules.", AuthoringCatalog.Kind.LOCATION));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ChronicleCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Profiles.registerCommands(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AuthoringCommands.register(dispatcher));
        ProfilePayloads.register();
        io.github.chronicle.core.network.SessionStatePayload.register();
        CoreMenuPayloads.register();
        CoreMenuServer.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CoreMenuCommands.register(dispatcher));
        MenuPlayerDestinations.register();
        io.github.chronicle.core.server.EnchantmentCapRuntime.register();
        io.github.chronicle.core.enchanting.EnchantmentCapsTrial.register();
        io.github.chronicle.core.server.testing.SessionPauseTrial.register();
        io.github.chronicle.core.server.testing.RegressionTrial.register();
        io.github.chronicle.core.server.testing.BooksLoreTrial.register();
        io.github.chronicle.core.server.testing.PlayerRolesTrial.register();
        io.github.chronicle.core.server.testing.AreaChangesTrial.register();
        io.github.chronicle.core.server.testing.EventActionsTrial.register();
        io.github.chronicle.core.server.testing.EventPoolTrial.register();
        io.github.chronicle.core.server.testing.EncounterActionsTrial.register();
        io.github.chronicle.core.server.testing.EventRecoveryTrial.register();
        io.github.chronicle.core.server.testing.EventProgressionTrial.register();
        io.github.chronicle.core.server.testing.EventSourcesTrial.register();
        io.github.chronicle.core.server.testing.DeparturesTrial.register();
        io.github.chronicle.core.server.testing.SubtypesTrial.register();
        AuthoringPreviewPayload.register();
        Profiles.registerNetworking();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> EVENTS.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> AUTHORING.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CHEST_TYPES.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> BOOKS.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> PLAYER_ROLES.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> io.github.chronicle.core.scenario.ScenarioEventTemplates.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> OBSERVATIONS.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> MANAGED_BLOCKS.freeze());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> GameSetupTools.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(ChronicleRuntime::start);
        ServerLifecycleEvents.SERVER_STARTED.register(Profiles::start);
        ServerLifecycleEvents.SERVER_STARTED.register(AuthoringRuntime::start);
        ServerLifecycleEvents.SERVER_STARTED.register(MapSiteAssignments::start);
        ManagedBlocks.register();
        ChestAuthoring.register();
        io.github.chronicle.core.server.BookRuntime.register();
        io.github.chronicle.core.server.BookDeliveryRuntime.register();
        TypedChestLoot.register();
        SpawnAuthoring.register();
        PlayRuntime.register();
        io.github.chronicle.core.server.AreaChanges.register();
        io.github.chronicle.core.server.WorldAreaOperations.register();
        io.github.chronicle.core.server.EventWorldBatches.register();
        io.github.chronicle.core.server.EventProgressRuntime.register();
        io.github.chronicle.core.server.EventOccurrenceRuntime.register();
        io.github.chronicle.core.server.Encounters.register();
        io.github.chronicle.core.server.EventRuntime.register();
        io.github.chronicle.core.server.EventPools.register();
        io.github.chronicle.core.network.EventPoolEditorPayloads.register();
        io.github.chronicle.core.server.EventPoolCatalogRuntime.register();
        io.github.chronicle.core.server.EventSources.register();
        io.github.chronicle.core.server.RoleEventSources.register();
        io.github.chronicle.core.server.CompletionEventSources.register();
        io.github.chronicle.core.server.EntitySpawnEventSources.register();
        io.github.chronicle.core.server.ScenarioContentRuntime.register();
        io.github.chronicle.core.server.ScenarioDefaultEvents.register();
        io.github.chronicle.core.network.BundledSoundHandshake.register();
        io.github.chronicle.core.server.FoundationVerificationTrial.register();
        io.github.chronicle.core.server.ScenarioDefaultsTrial.register();
        io.github.chronicle.core.server.EventHistoryTools.register();
        io.github.chronicle.core.server.Departures.register();
        io.github.chronicle.core.server.SiteSubtypes.registerBuiltins();
        io.github.chronicle.core.server.RegionAuthoringWorkflow.register();
        io.github.chronicle.core.server.AuthorSelectionTools.register();
        AutomaticPlayerSpawns.register();
        io.github.chronicle.core.server.PlayerRoles.register();
        io.github.chronicle.core.server.RoleFirstEntry.register();
        io.github.chronicle.core.server.PersonalCopies.register();
        io.github.chronicle.core.server.OfferingStats.register();
        io.github.chronicle.core.server.LastingBargains.register();
        io.github.chronicle.core.server.TemporaryOfferings.register();
        io.github.chronicle.core.server.EventHistoryScheduler.register();
        io.github.chronicle.core.server.ActionConfirmations.register();
        io.github.chronicle.core.server.ItemEnchantingRuntime.register();
        ServerLifecycleEvents.SERVER_STARTED.register(SessionPresentation::changed);
        ServerTickEvents.START_SERVER_TICK.register(SessionPresentation::tick);
        ServerTickEvents.END_SERVER_TICK.register(AuthoringRuntime::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SessionPresentation.joined(handler.player));
        io.github.chronicle.core.server.ServerDisconnects.register((handler, server) -> Profiles.disconnected(handler.player));
        io.github.chronicle.core.server.ServerDisconnects.register((handler, server) -> AuthoringRuntime.disconnected(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(ChronicleRuntime::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(Profiles::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(AuthoringRuntime::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(MapSiteAssignments::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(RuntimeHolds::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(SessionPresentation::stop);
        LOGGER.info("Chronicle Core initialized");
    }

}
