package io.github.chronicle.core;

import io.github.chronicle.core.campaign.CampaignEvents;
import io.github.chronicle.core.server.ChronicleCommands;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.Profiles;
import io.github.chronicle.core.server.SessionPresentation;
import io.github.chronicle.core.network.ProfilePayloads;
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

    /** Register campaign definitions during mod initialization, before the server starts. */
    public static CampaignEvents events() { return EVENTS; }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ChronicleCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Profiles.registerCommands(dispatcher));
        ProfilePayloads.register();
        Profiles.registerNetworking();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> EVENTS.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(ChronicleRuntime::start);
        ServerLifecycleEvents.SERVER_STARTED.register(Profiles::start);
        ServerLifecycleEvents.SERVER_STARTED.register(SessionPresentation::changed);
        ServerTickEvents.START_SERVER_TICK.register(SessionPresentation::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SessionPresentation.joined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Profiles.disconnected(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(ChronicleRuntime::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(Profiles::stop);
        LOGGER.info("Chronicle Core initialized");
    }

}
