package io.github.chronicle.core.server;

import io.github.chronicle.core.network.ActionConfirmationPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** One private, short-lived confirmation per player. Campaign callbacks own durable idempotency. */
public final class ActionConfirmations {
    private static final long MAX_AGE_NANOS = 30_000_000_000L;
    private static final int MAX_OFFERS = 256;
    private static final Map<MinecraftServer, Map<UUID, Offer>> OFFERS = new IdentityHashMap<>();
    private ActionConfirmations() { }
    @FunctionalInterface public interface Gate { String unavailable(ServerPlayerEntity player); }
    @FunctionalInterface public interface Action { void execute(ServerPlayerEntity player, UUID token) throws Exception; }

    public static void register() {
        ActionConfirmationPayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(ActionConfirmationPayloads.Action.ID,
                (payload, context) -> context.server().execute(() -> act(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            Map<UUID, Offer> offers = OFFERS.get(server);
            if (offers != null) offers.values().removeIf(offer -> expired(offer));
        });
        io.github.chronicle.core.server.ServerDisconnects.register((handler,server)->{
            Map<UUID,Offer> offers=OFFERS.get(server);if(offers!=null){Offer offer=offers.get(handler.player.getUuid());if(offer!=null&&OfferIdentity.sameConnection(offer.connection(),handler))offers.remove(handler.player.getUuid());}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(OFFERS::remove);
    }

    public static void open(ServerPlayerEntity player, String title, String details, String acceptLabel, Gate gate, Action action) {
        if (player == null || gate == null || action == null) throw new IllegalArgumentException("Confirmation player, gate, and action are required");
        validate(title, 80); validate(details, 2048); validate(acceptLabel, 48);
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || !server.isOnThread()) throw new IllegalStateException("Confirmations require the server thread");
        if(!ServerPlayNetworking.canSend(player,ActionConfirmationPayloads.View.ID)){
            player.sendMessage(net.minecraft.text.Text.literal("Install a matching Chronicle client to review this action."),false);return;
        }
        Map<UUID, Offer> offers = OFFERS.computeIfAbsent(server, ignored -> new java.util.HashMap<>());
        if (offers.size() >= MAX_OFFERS && !offers.containsKey(player.getUuid())) {
            player.sendMessage(net.minecraft.text.Text.literal("Too many pending confirmations; try again shortly."), false); return;
        }
        Offer offer = new Offer(UUID.randomUUID(), System.nanoTime(), player.getUuid(), player.networkHandler, title, details, acceptLabel, gate, action, null);
        offers.put(player.getUuid(), offer);
        send(player, offer, "Read the consequences, then explicitly confirm.", true, false);
    }

    private static void act(ServerPlayerEntity player, ActionConfirmationPayloads.Action request) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || !server.isOnThread()) return;
        Map<UUID, Offer> offers = OFFERS.get(server); if (offers == null) return;
        Offer offer = offers.get(player.getUuid());
        if (request.action() == 2 && offer != null && OfferIdentity.accepts(offer.connection(),offer.token(),offer.parent(),player.networkHandler,request.token(),true) && request.token().equals(offer.parent())) { offers.remove(player.getUuid()); return; }
        if (offer == null || !OfferIdentity.accepts(offer.connection(),offer.token(),offer.parent(),player.networkHandler,request.token(),false) || !player.getUuid().equals(offer.player())) return;
        offers.remove(player.getUuid()); // Consume before all rechecks/callbacks; retries cannot execute twice.
        if (expired(offer)) { send(player, offer, "Confirmation expired. Reopen the action to review it again.", false, true); return; }
        if (request.action() == 2) return;
        if (request.action() == 1) { successor(player, offers, offer); return; }
        String unavailable;
        try { unavailable = unavailable(offer.gate().unavailable(player)); }
        catch (RuntimeException error) { unavailable = "The action could not be checked."; }
        if (unavailable != null) { send(player, offer, clip(unavailable), false, true); return; }
        try {
            offer.action().execute(player, offer.token());
            send(player, offer, "Action accepted. Check the campaign status for the result.", false, true);
        } catch (Exception error) {
            // A trusted callback may have durably begun its transition before it failed.
            send(player, offer, "Could not finish; check operation status.", false, true);
        }
    }

    private static void successor(ServerPlayerEntity player, Map<UUID, Offer> offers, Offer old) {
        if (offers.size() >= MAX_OFFERS && !offers.containsKey(player.getUuid())) return;
        Offer next = new Offer(UUID.randomUUID(), System.nanoTime(), old.player(), old.connection(), old.title(), old.details(), old.acceptLabel(), old.gate(), old.action(), old.token());
        offers.put(player.getUuid(), next);
        send(player, next, "Review the current action before confirming.", false, false);
    }
    private static void send(ServerPlayerEntity player, Offer offer, String status, boolean opening, boolean completed) {
        String unavailable;
        try { unavailable = unavailable(offer.gate().unavailable(player)); } catch (RuntimeException error) { unavailable = "The action could not be checked."; }
        if(!ServerPlayNetworking.canSend(player,ActionConfirmationPayloads.View.ID))return;
        ServerPlayNetworking.send(player, new ActionConfirmationPayloads.View(offer.token(), offer.title(), offer.details(), offer.acceptLabel(),
                clip(unavailable == null ? status : unavailable), unavailable == null, opening, completed));
    }
    private static boolean expired(Offer offer) { return System.nanoTime() - offer.created() >= MAX_AGE_NANOS; }
    private static String unavailable(String value) { return value == null ? null : value.isBlank() ? "This action is unavailable." : value; }
    private static void validate(String text, int limit) { if (text == null || text.isBlank() || text.length() > limit) throw new IllegalArgumentException("Invalid confirmation text"); }
    private static String clip(String value) { return value.length() <= 384 ? value : value.substring(0, 381) + "..."; }
    private record Offer(UUID token, long created, UUID player, net.minecraft.server.network.ServerPlayNetworkHandler connection, String title, String details, String acceptLabel, Gate gate, Action action, UUID parent) { }
}
