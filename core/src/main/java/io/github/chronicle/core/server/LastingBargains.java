package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.offering.OfferingDefinition;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;

/** Generic one-use consent interface; campaign code owns intrinsic station power and trade defaults. */
public final class LastingBargains {
    private static final Map<String, List<Choice>> GROUPS = new TreeMap<>();
    private static final Map<MinecraftServer, Map<UUID, Offer>> OFFERS = new IdentityHashMap<>();
    private static final long MAX_AGE = 60_000_000_000L;
    private static boolean frozen;
    private LastingBargains() { }
    public record Choice(OfferingDefinition definition, String label, String description) {
        public Choice {
            if (definition == null || label == null || label.isBlank() || label.length() > 64
                    || description == null || description.isBlank() || description.length() > 512) throw new IllegalArgumentException("Invalid bargain choice");
        }
    }
    @FunctionalInterface public interface Gate {
        /** Null means usable now. Recheck physical station/ACTIVE/participation before each transaction. */
        String unavailable(ServerPlayerEntity player);
        default void completed(ServerPlayerEntity player) { }
    }
    public static void registerGroup(String id, List<Choice> choices) {
        OfferingDefinition.validId(id);
        if (frozen || GROUPS.size() >= 32 || GROUPS.containsKey(id) || choices == null || choices.isEmpty() || choices.size() > 16) throw new IllegalStateException("Invalid or frozen bargain group");
        Set<String> ids = new HashSet<>();
        for (Choice choice : choices) if (!ids.add(choice.definition.id())) throw new IllegalArgumentException("Duplicate offering choice");
        GROUPS.put(id, List.copyOf(choices));
    }
    public static void register() {
        OfferingPayloads.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> frozen = true);
        ServerLifecycleEvents.SERVER_STOPPED.register(OFFERS::remove);
        io.github.chronicle.core.server.ServerDisconnects.register((handler, server) -> {
            var offers = OFFERS.get(server);
            if (offers != null) {
                Offer offer = offers.get(handler.player.getUuid());
                if (offer != null && OfferIdentity.sameConnection(offer.connection, handler)) {
                    offers.remove(handler.player.getUuid(), offer);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var offers = OFFERS.get(server);
            if (offers != null && server.getTicks() % 20 == 0) offers.values().removeIf(offer -> System.nanoTime() - offer.created >= MAX_AGE);
        });
        ServerPlayNetworking.registerGlobalReceiver(OfferingPayloads.Action.ID,
                (payload, context) -> context.server().execute(() -> act(context.player(), payload)));
    }
    public static void open(ServerPlayerEntity player, String source, String group, String title, Gate gate) {
        open(player, source, group, title, gate, 0, true,
                "Choose a bargain. Read both the sacrifice and bonus before accepting.", null);
    }
    private static void open(ServerPlayerEntity player, String source, String group, String title, Gate gate,
                             int selected, boolean opening, String status, UUID parent) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (!server.isOnThread()) throw new IllegalStateException("Bargains require the server thread");
        OfferingDefinition.validId(source);
        if (!ServerPlayNetworking.canSend(player, OfferingPayloads.View.ID)) {
            player.sendMessage(Text.literal("Install the matching Chronicle client to use this Altar."), false); return;
        }
        if (title == null || title.isBlank() || title.length() > 80 || gate == null) throw new IllegalArgumentException("Invalid bargain station");
        List<Choice> choices = GROUPS.get(group);
        if (choices == null || selected < 0 || selected >= choices.size()) throw new IllegalArgumentException("Unknown bargain group/choice");
        Map<UUID, Offer> offers = OFFERS.computeIfAbsent(server, ignored -> new HashMap<>());
        if (offers.size() >= 256 && !offers.containsKey(player.getUuid())) { player.sendMessage(Text.literal("Too many open bargains; try again later."), false); return; }
        List<OfferingPayloads.Choice> views = new ArrayList<>(); List<OfferingStats.Preview> previews = new ArrayList<>();
        String gateProblem = gate.unavailable(player);
        for (Choice choice : choices) {
            OfferingStats.Preview preview = null; int count = 0; String problem = gateProblem;
            String result = "Current: " + totals(new OfferingStats.Totals(player.getMaxHealth(), io.github.chronicle.core.stats.FoodCapacity.capacity(player),
                    player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED)));
            try {
                count = OfferingStats.ledger(server).count(player.getUuid(), source, choice.definition.id());
                preview = OfferingStats.preview(player, source, choice.definition);
                result = String.format(Locale.ROOT,"Max hearts: %.1f -> %.1f\nFood capacity: %d -> %d\nWalking speed: %.0f%% -> %.0f%% (vanilla = 100%%)",
                        preview.before().health()/2,preview.after().health()/2,preview.before().food(),preview.after().food(),
                        preview.before().speed()*1000,preview.after().speed()*1000);
            } catch (RuntimeException error) { if (problem == null) problem = error.getMessage(); }
            previews.add(preview);
            views.add(new OfferingPayloads.Choice(choice.label, count, choice.definition.maxPurchases(), choice.description,
                    clip(result, 256), problem == null ? "" : clip(problem, 256)));
        }
        UUID token = UUID.randomUUID();
        Offer offer = new Offer(token, System.nanoTime(), player.networkHandler, parent, source, group, title, gate,
                selected, List.copyOf(views), Collections.unmodifiableList(previews));
        offers.put(player.getUuid(), offer);
        send(player, offer, status, opening, false);
    }
    private static String totals(OfferingStats.Totals stats) {
        return String.format(Locale.ROOT, "%.1f hearts, %d food, %.0f%% walking speed", stats.health() / 2, stats.food(), stats.speed()*1000);
    }
    private static void send(ServerPlayerEntity player, Offer offer, String status, boolean opening, boolean completed) {
        ServerPlayNetworking.send(player, new OfferingPayloads.View(offer.token, offer.title, clip(status, 384), offer.views, offer.selected, opening, completed));
    }
    public static void act(ServerPlayerEntity player, OfferingPayloads.Action action) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (!server.isOnThread()) throw new IllegalStateException("Bargains require the server thread");
        var offers = OFFERS.get(server); if (offers == null) return;
        Offer offer = offers.get(player.getUuid());
        if (action.action() == 3) {
            if (offer != null && OfferIdentity.accepts(offer.connection, offer.token, offer.parent,
                    player.networkHandler, action.token(), true)) offers.remove(player.getUuid(), offer);
            return;
        }
        if (offer == null || !OfferIdentity.accepts(offer.connection, offer.token, offer.parent,
                player.networkHandler, action.token(), false)) return;
        offers.remove(player.getUuid(), offer); // Every action consumes this token, including unsuccessful attempts.
        if (System.nanoTime() - offer.created >= MAX_AGE) { send(player, offer, "Offer expired. Open the Altar again; no bargain was accepted.", false, true); return; }
        if (action.action() == 0 || action.action() == 2) {
            int selected = action.action() == 0 ? action.selected() : offer.selected;
            if (selected < GROUPS.get(offer.group).size()) open(player, offer.source, offer.group, offer.title, offer.gate,
                    selected, false, "Review the complete trade. Acceptance is permanent until this campaign source ends.", offer.token);
            return;
        }
        if (action.selected() != offer.selected) { send(player, offer, "Selection changed. Open the Altar again.", false, true); return; }
        String problem = offer.gate.unavailable(player);
        OfferingStats.Preview expected = offer.previews.get(offer.selected);
        if (problem == null && expected == null) problem = offer.views.get(offer.selected).problem();
        if (problem != null) { open(player, offer.source, offer.group, offer.title, offer.gate, offer.selected, false,
                "No bargain accepted. " + problem, offer.token); return; }
        try {
            OfferingStats.purchase(player, offer.source, GROUPS.get(offer.group).get(offer.selected).definition, offer.token, expected);
        } catch (Exception error) {
            ChronicleCore.LOGGER.warn("Bargain confirmation did not complete for {}: {}", player.getUuid(), error.toString());
            // A durable commit may precede a projection/IO error. Never promise a refund here.
            send(player, offer, "Confirmation could not finish: " + error.getMessage() + ". Reopen to check the authoritative purchase count.", false, true); return;
        }
        try { offer.gate.completed(player); } catch (Exception error) { ChronicleCore.LOGGER.error("Bargain saved; cosmetic Altar feedback failed", error); }
        open(player, offer.source, offer.group, offer.title, offer.gate, offer.selected, false,
                "Bargain accepted. Both bonus and sacrifice survive death and reconnecting; there is no player reversal.", offer.token);
    }
    private static String clip(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit - 3) + "..."; }
    private record Offer(UUID token, long created, net.minecraft.server.network.ServerPlayNetworkHandler connection,
                         UUID parent, String source, String group, String title, Gate gate, int selected,
                         List<OfferingPayloads.Choice> views, List<OfferingStats.Preview> previews) { }
}
