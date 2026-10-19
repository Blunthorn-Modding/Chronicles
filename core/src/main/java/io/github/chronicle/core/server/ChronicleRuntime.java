package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.CampaignService;
import io.github.chronicle.core.campaign.CampaignStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.IdentityHashMap;
import java.util.Map;

/** Per-world services, accessed only on the Minecraft server thread. */
public final class ChronicleRuntime {
    private static final Map<MinecraftServer, CampaignService> SERVICES = new IdentityHashMap<>();

    private ChronicleRuntime() { }

    public static void start(MinecraftServer server) {
        Path file = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle").resolve("campaign-state.json");
        try {
            SERVICES.put(server, new CampaignService(new CampaignStore(file), ChronicleCore.events(), Clock.systemUTC()));
            ChronicleCore.LOGGER.info("Chronicle campaign state ready: {}", file);
        } catch (IOException | IllegalArgumentException exception) {
            SERVICES.remove(server);
            ChronicleCore.LOGGER.error("Chronicle campaign data could not be loaded. The existing file was not replaced. "
                    + "Stop the server and restore {} from a verified backup before using campaign commands.", file, exception);
        }
    }

    public static CampaignService service(MinecraftServer server) {
        if (!server.isOnThread()) {
            throw new IllegalStateException("Chronicle operations must run on the server thread.");
        }
        CampaignService service = SERVICES.get(server);
        if (service == null) {
            throw new IllegalStateException("Chronicle state is unavailable. Check the server log and restore its campaign-state.json while the server is stopped.");
        }
        return service;
    }

    static CampaignService available(MinecraftServer server) {
        return SERVICES.get(server);
    }

    public static void stop(MinecraftServer server) {
        // Successful mutations are already persisted; never save an empty fallback on shutdown.
        SERVICES.remove(server);
    }
}
