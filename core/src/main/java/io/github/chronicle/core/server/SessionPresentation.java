package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.CampaignService;
import io.github.chronicle.core.campaign.CampaignState;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.campaign.SessionNoticePolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.Map;
import java.util.IdentityHashMap;

/** Session-owned vanilla simulation freeze; player/network ticks continue. */
public final class SessionPresentation {
    private static final Map<MinecraftServer,SessionNoticePolicy.State> LAST_NOTICE=new IdentityHashMap<>();
    private SessionPresentation() { }

    public static void tick(MinecraftServer server) {
        apply(server, false);
    }

    public static void changed(MinecraftServer server) {
        apply(server, true);
    }

    private static void apply(MinecraftServer server, boolean refresh) {
        CampaignService service = ChronicleRuntime.available(server);
        CampaignState state = service == null ? null : service.state();
        boolean frozen = SessionGameplay.frozen(server);
        ServerTickManager ticks = server.getTickManager();
        if (frozen) {
            ticks.stopSprinting();
            ticks.stopStepping();
        }
        if (ticks.isFrozen() != frozen) {
            ticks.setFrozen(frozen);
        }
        // The tick manager may already have calculated this tick before Fabric's callback.
        if (frozen && ticks.shouldTick()) {
            ticks.step();
        }
        var current=new SessionNoticePolicy.State(state==null?null:state.phase(),state==null?0:state.sessionNumber(),
                java.util.Objects.requireNonNullElse(RuntimeHolds.reason(server),""), SessionBuilding.enabled(server));
        var previous=LAST_NOTICE.put(server,current);
        if (current.broadcastAfter(previous,refresh,server.getTicks()%20==0)) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                show(player, state);
            }
        }
    }

    public static void stop(MinecraftServer server) { LAST_NOTICE.remove(server); SessionBuilding.stop(server); }

    public static void joined(ServerPlayerEntity player) {
        CampaignService service = ChronicleRuntime.available(player.getEntityWorld().getServer());
        show(player, service == null ? null : service.state());
    }

    private static void show(ServerPlayerEntity player, CampaignState state) {
        Text message;
        String hold = RuntimeHolds.reason(player.getEntityWorld().getServer());
        if (state == null) {
            message = Text.translatableWithFallback("chronicle.session.unavailable",
                    "Chronicle: unavailable | World frozen").formatted(Formatting.RED);
        } else {
            String phase = state.phase().name().toLowerCase(Locale.ROOT);
            String fallback = phase.substring(0, 1).toUpperCase(Locale.ROOT) + phase.substring(1);
            Text phaseText = Text.translatableWithFallback("chronicle.session.phase." + phase, fallback);
            message = hold != null
                    ? Text.translatableWithFallback("chronicle.session.held", "Chronicle | Session %s: %s | World frozen: %s",
                            state.sessionNumber(), phaseText, hold).formatted(Formatting.GOLD)
                    : state.phase() == SessionPhase.ACTIVE
                    ? Text.translatableWithFallback("chronicle.session.play_active", "Chronicle | Play is active").formatted(Formatting.GREEN)
                    : Text.translatableWithFallback("chronicle.session.frozen", "Chronicle | Session %s: %s | World frozen",
                            state.sessionNumber(), phaseText).formatted(Formatting.GOLD);
        }
        if (SessionBuilding.enabled(player.getEntityWorld().getServer()))
            message = Text.translatableWithFallback("chronicle.session.building", "%s | Building Mode on", message).formatted(Formatting.GOLD);
        player.sendMessage(message, true);
    }
}
