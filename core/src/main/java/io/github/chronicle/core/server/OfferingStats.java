package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.offering.*;
import io.github.chronicle.core.stats.FoodCapacity;
import io.github.chronicle.core.stats.OfferingAttributes;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.*;

/** Durable authority first, idempotent native projection second. Trusted server callers supply gameplay gates. */
public final class OfferingStats {
    private static final String OWNER = "chronicle_core:offerings";
    private static final String PREFIX = "offerings/";
    private static final List<RegistryEntry<EntityAttribute>> ATTRIBUTES = List.of(EntityAttributes.MAX_HEALTH,
            OfferingAttributes.FOOD_CAPACITY, EntityAttributes.MOVEMENT_SPEED, OfferingAttributes.EXHAUSTION_MULTIPLIER);
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private OfferingStats() { }

    public record Totals(double health,int food,double speed,double exhaustion) {
        public Totals(double health,int food,double speed){this(health,food,speed,1);}
    }
    public record PurchaseResult(UUID transaction,boolean newlyCommitted) { }
    public record Preview(long revision, int count, Totals before, Totals after) { }

    public static void register() {
        OfferingAttributes.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(OfferingStats::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            State state = SERVERS.remove(server);
            if (state != null && state.service != null) try { state.service.close(); }
            catch (IOException error) { ChronicleCore.LOGGER.error("Could not release offering ledger lock", error); }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reconcileSafely(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            // Keep the last known price AND bonus even if authority failed during death.
            State state = SERVERS.get(player.getEntityWorld().getServer());
            if (state == null || state.failure != null || state.service == null) copyOwnedModifiers(oldPlayer, player);
            else try {
                state.store.verifyUnchanged();
                // Vanilla can create fresh attributes on death. Check the old authority
                // before that reset could hide an unaccounted-for lasting sacrifice.
                assertAuthority(oldPlayer,state.service.ledger());reconcile(player,state.service.ledger());
            } catch(Exception error) {
                fail(player.getEntityWorld().getServer(),state,error);copyOwnedModifiers(oldPlayer,player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            State state = SERVERS.get(server);
            if (state == null || state.failure != null || server.getTicks() % 20 != 0) return;
            try {
                if (server.getPlayerManager().getPlayerList().size() > 256) throw new IllegalStateException("more than256 online players");
                if (server.getTicks() % 200 == 0) state.store.verifyUnchanged();
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) reconcile(player, state.service.ledger());
            } catch (Exception error) { fail(server, state, error); }
        });
        GameSetupTools.register(new GameSetupTools.Tool(OWNER, "Lasting bargains",
                "Permanent character trades. Their bonus and sacrifice survive death and reconnecting; campaign endings remove both.",
                List.of(new GameSetupTools.Verb("reload", "Reload repaired ledger", GameSetupTools.Guard.EDIT, true),
                        new GameSetupTools.Verb("refresh", "Refresh", GameSetupTools.Guard.READ_ONLY, false)),
                source -> {
                    State state = required(source.getServer());
                    OfferingLedger ledger = state.service == null ? null : state.service.ledger();
                    return new GameSetupTools.Status(ledger == null ? 0 : ledger.revision(), List.of(
                            new io.github.chronicle.core.menu.CoreMenuModel.Fact("Status", state.failure == null ? "Ready" : state.failure),
                            new io.github.chronicle.core.menu.CoreMenuModel.Fact("Purchases", ledger == null ? "Unavailable" : Integer.toString(ledger.receipts().size())),
                            new io.github.chronicle.core.menu.CoreMenuModel.Fact("Authority", "world/chronicle/offerings.json; no normal refund or automatic backup restore.")), Map.of());
                }, (source, verb) -> {
                    if (verb.equals("reload")) { reload(source.getServer()); return "Offering ledger reloaded; character stats reconciled."; }
                    return "Lasting bargain status refreshed.";
                }));
    }

    private static void start(MinecraftServer server) {
        State state = new State(); SERVERS.put(server, state);
        try { load(server, state); }
        catch (Exception error) { fail(server, state, error); }
    }

    private static void load(MinecraftServer server, State state) throws IOException {
        OfferingStore store = new OfferingStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/offerings.json"));
        try { state.service = new OfferingService(store); state.store = store; state.failure = null; }
        catch (IOException | RuntimeException error) { store.close(); throw error; }
        ChronicleCore.LOGGER.info("Lasting offering ledger ready: {} receipts, {} ended sources",
                state.service.ledger().receipts().size(), state.service.ledger().closedSources().size());
    }

    private static void reload(MinecraftServer server) throws IOException {
        State state = required(server);
        try {
            if (state.service != null) state.service.close();
            state.service = null; state.store = null; load(server, state);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) reconcile(player, state.service.ledger());
            RuntimeHolds.release(server, OWNER);
        } catch (IOException | RuntimeException error) { fail(server, state, error); throw error; }
    }

    public static OfferingLedger ledger(MinecraftServer server) { return ready(server).service.ledger(); }
    static boolean sourceDefinitelyAbsent(MinecraftServer server,UUID transaction) {
        State state=ready(server);try{state.store.verifyUnchanged();return state.service.ledger().receipts().stream().noneMatch(receipt->receipt.transaction().equals(transaction));}
        catch(IOException failure){throw new IllegalStateException("Offering source refusal cannot be proved from its observed authority",failure);}
    }

    public static Preview preview(ServerPlayerEntity player, String source, OfferingDefinition definition) {
        State state = ready(player.getEntityWorld().getServer());
        OfferingLedger ledger = state.service.ledger();
        assertAuthority(player, ledger);
        OfferingLedger next = ledger.purchase(ledger.revision(), UUID.randomUUID(), player.getUuid(), source, definition);
        Totals before = projected(player, ledger), after = projected(player, next);
        validate(after);
        return new Preview(ledger.revision(), ledger.count(player.getUuid(), source, definition.id()), before, after);
    }

    /** Call only after the current campaign gate and one-use consent offer have been rechecked. */
    public static PurchaseResult purchase(ServerPlayerEntity player, String source, OfferingDefinition definition,
                                UUID transaction, Preview expected) throws IOException {
        MinecraftServer server = player.getEntityWorld().getServer(); State state = ready(server);
        for (OfferingReceipt receipt : state.service.ledger().receipts()) if (receipt.transaction().equals(transaction)) {
            if (!receipt.player().equals(player.getUuid()) || !receipt.source().equals(source)
                    || !receipt.offering().equals(definition.id()) || !receipt.terms().equals(definition.terms())) {
                throw new IllegalStateException("Transaction id belongs to a different bargain");
            }
            try {
                // A durable grant with a lost reply is success, even after its third purchase.
                state.service.purchase(expected.revision(), transaction, player.getUuid(), source, definition);
                reconcile(player, state.service.ledger()); return new PurchaseResult(transaction,false);
            } catch (IOException | RuntimeException error) { fail(server, state, error); throw error; }
        }
        Preview current = preview(player, source, definition);
        if (!current.equals(expected)) throw new IllegalStateException("Your bargain or stats changed; review the trade again.");
        try {
            OfferingLedger committed = state.service.purchase(expected.revision(), transaction, player.getUuid(), source, definition);
            reconcile(player, committed);
            return new PurchaseResult(transaction,true);
        } catch (IOException error) { fail(server, state, error); throw error; }
        catch (RuntimeException error) {
            // Once a receipt exists, a projection error must hold instead of advertising a rollback.
            if (state.service.ledger().receipts().stream().anyMatch(receipt -> receipt.transaction().equals(transaction))) fail(server, state, error);
            throw error;
        }
    }

    /** Campaign terminal transition: durable tombstone prevents purchases and removes both sides. */
    public static void closeSource(MinecraftServer server, String source) throws IOException {
        State state = ready(server);
        try {
            OfferingLedger committed = state.service.closeSource(source);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) reconcile(player, committed);
        } catch (IOException | RuntimeException error) { fail(server, state, error); throw error; }
    }

    private static State required(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Offering stats require the server thread");
        State state = SERVERS.get(server);
        if (state == null) throw new IllegalStateException("Offering authority has not started");
        return state;
    }

    private static State ready(MinecraftServer server) {
        State state = required(server);
        if (state.failure != null || state.service == null) throw new IllegalStateException("Offering ledger unavailable; an organizer must repair it.");
        return state;
    }

    private static void fail(MinecraftServer server, State state, Exception error) {
        if (state.failure == null) ChronicleCore.LOGGER.error("Offering authority held; existing ledger/modifiers retained. Repair the ledger before reload.", error);
        state.failure = "Bargain data unavailable; repair the offering ledger before reloading.";
        RuntimeHolds.hold(server, OWNER, state.failure);
    }

    private static Identifier modifierId(String source) {
        return Identifier.of("chronicle_core", PREFIX + source.replace(':', '/'));
    }

    private static String sourceOf(Identifier id) {
        if (!id.getNamespace().equals("chronicle_core") || !id.getPath().startsWith(PREFIX)) return null;
        String value = id.getPath().substring(PREFIX.length()); int split = value.indexOf('/');
        if (split <= 0 || split == value.length() - 1) throw new IllegalStateException("Unrecognized persistent offering modifier");
        String source = value.substring(0, split) + ":" + value.substring(split + 1);
        OfferingDefinition.validId(source); return source;
    }

    private static void assertAuthority(ServerPlayerEntity player, OfferingLedger ledger) {
        Set<String> authorized = ledger.totals(player.getUuid()).keySet();
        for (var attribute : ATTRIBUTES) for (var modifier : instance(player, attribute).getModifiers()) {
            String source = sourceOf(modifier.id());
            if (source != null && !authorized.contains(source) && !ledger.closedSources().contains(source)) {
                throw new IllegalStateException("Persistent offering modifiers have no corresponding ledger authority");
            }
        }
    }

    private static EntityAttributeInstance instance(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute) {
        EntityAttributeInstance result = player.getAttributeInstance(attribute);
        if (result == null) throw new IllegalStateException("Missing offering player attribute");
        return result;
    }

    private static Map<Identifier, EntityAttributeModifier> desired(Map<String, OfferingTerms> totals, int index) {
        Map<Identifier, EntityAttributeModifier> result = new TreeMap<>();
        for (var entry : totals.entrySet()) {
            OfferingTerms terms = entry.getValue();
            double value = switch(index){case 0->terms.health();case 1->terms.food();case 2->terms.speedPercent()/100.0;default->terms.exhaustionPercent()/100.0;};
            if (value != 0) result.put(modifierId(entry.getKey()), new EntityAttributeModifier(modifierId(entry.getKey()), value,
                    index == 2 ? EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE : EntityAttributeModifier.Operation.ADD_VALUE));
        }
        return result;
    }

    private static void project(EntityAttributeInstance attribute, Map<Identifier, EntityAttributeModifier> desired) {
        for (var modifier : List.copyOf(attribute.getModifiers())) if (sourceOf(modifier.id()) != null && !desired.containsKey(modifier.id())) {
            attribute.removeModifier(modifier.id());
        }
        for (var modifier : desired.values()) {
            if (!modifier.equals(attribute.getModifier(modifier.id())) || !attribute.getPersistentModifiers().contains(modifier)) {
                attribute.overwritePersistentModifier(modifier);
            }
        }
    }

    private static Totals projected(ServerPlayerEntity player, OfferingLedger ledger) {
        Map<String, OfferingTerms> totals = ledger.totals(player.getUuid()); double[] values = new double[ATTRIBUTES.size()];
        for (int index = 0; index < ATTRIBUTES.size(); index++) {
            EntityAttributeInstance copy = new EntityAttributeInstance(ATTRIBUTES.get(index), ignored -> { });
            copy.setFrom(instance(player, ATTRIBUTES.get(index))); project(copy, desired(totals, index)); values[index] = copy.getValue();
        }
        return new Totals(values[0], (int)Math.floor(values[1]), values[2],values[3]);
    }

    private static void validate(Totals totals) {
        if (!Double.isFinite(totals.health()) || !Double.isFinite(totals.speed()) || totals.health() < 2
                || totals.food() < 2 || totals.food() > 40 || totals.speed() <= 0
                || !Double.isFinite(totals.exhaustion()) || totals.exhaustion()<1 || totals.exhaustion()>256) throw new IllegalStateException("The resulting stats would be unsafe; no bargain was accepted.");
    }

    private static void reconcile(ServerPlayerEntity player, OfferingLedger ledger) {
        assertAuthority(player, ledger);
        Map<String, OfferingTerms> bySource = ledger.totals(player.getUuid());
        for (int index = 0; index < ATTRIBUTES.size(); index++) project(instance(player, ATTRIBUTES.get(index)), desired(bySource, index));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        FoodCapacity.clamp(player.getHungerManager(), FoodCapacity.capacity(player));
    }

    private static void reconcileSafely(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer(); State state = SERVERS.get(server);
        if (state == null || state.failure != null || state.service == null) return;
        try { state.store.verifyUnchanged(); reconcile(player, state.service.ledger()); }
        catch (Exception error) { fail(server, state, error); }
    }

    private static void copyOwnedModifiers(ServerPlayerEntity oldPlayer, ServerPlayerEntity player) {
        for (var attribute : ATTRIBUTES) for (var modifier : instance(oldPlayer, attribute).getPersistentModifiers()) {
            if (modifier.id().getNamespace().equals("chronicle_core") && modifier.id().getPath().startsWith(PREFIX))
                instance(player, attribute).overwritePersistentModifier(modifier);
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        FoodCapacity.clamp(player.getHungerManager(), FoodCapacity.capacity(player));
    }

    private static final class State { OfferingStore store; OfferingService service; String failure; }
}
