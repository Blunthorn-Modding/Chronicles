package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.CampaignService;
import io.github.chronicle.core.campaign.CampaignState;
import io.github.chronicle.core.campaign.SessionPhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/** Session-owned vanilla simulation freeze; player/network ticks continue. */
public final class SessionPresentation {
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
        if (refresh || server.getTicks() % 20 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                show(player, state);
            }
        }
    }

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
                    ? Text.translatableWithFallback("chronicle.session.active", "Chronicle | Session %s: %s",
                            state.sessionNumber(), phaseText).formatted(Formatting.GREEN)
                    : Text.translatableWithFallback("chronicle.session.frozen", "Chronicle | Session %s: %s | World frozen",
                            state.sessionNumber(), phaseText).formatted(Formatting.GOLD);
        }
        player.sendMessage(message, true);
    }
}
