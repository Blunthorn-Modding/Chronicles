package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.SiteSubtypes;
import io.github.chronicle.core.server.VoiceRooms;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in fixture only; optional API types are confined to the nested class. */
public final class VoiceRoomsTrial {
    private static volatile Object api;
    private static UUID manual;
    private VoiceRoomsTrial() { }
    public static void voiceStarted(Object value) { if (enabled()) api = value; }
    private static boolean enabled() { return "true".equals(System.getenv("CHRONICLE_VOICE_ROOMS_TRIAL")); }
    public static void register() {
        if (!enabled()) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("chronicle_voice_rooms_trial").requires(s -> s.hasPermissionLevel(2));
            for (String step : new String[]{"prepare", "same", "different", "outside", "gates", "race", "restart", "remove", "first_room_race", "without_voice"})
                root.then(literal(step).executes(c -> run(c.getSource(), step)));
            dispatcher.register(root);
        });
    }
    private static int run(ServerCommandSource source, String step) {
        try {
            fixture(source);
            var server = source.getServer();
            var a = server.getPlayerManager().getPlayer("ChronicleFabricA");
            var b = server.getPlayerManager().getPlayer("ChronicleFabricB");
            require(a != null && b != null, "two fixture players required");
            if (step.equals("without_voice")) {
                require(api == null && !VoiceRooms.enableRefusal(server).isBlank()
                        && io.github.chronicle.core.server.RuntimeHolds.reason(server) == null,
                        "voice absence not reported cleanly");
                require(AuthoringRuntime.get(server).service().state().sites().containsKey("room_a"), "authoring unavailable");
                source.sendFeedback(() -> Text.literal("Voice rooms trial PASS without_voice"), false); return 1;
            }
            require(api != null, "voice API required");
            switch (step) {
                case "prepare" -> {
                    var author = AuthoringRuntime.get(server);
                    author.enable(a.getUuid());
                    for (int x = 0; x < 14; x++) for (int z = 0; z < 6; z++)
                        source.getWorld().setBlockState(new net.minecraft.util.math.BlockPos(x, 63, z), net.minecraft.block.Blocks.STONE.getDefaultState(), 3);
                    author.service().put(region("room_a", 1, 3), false);
                    author.service().put(region("room_b", 7, 9), false);
                    move(a, 12.5); move(b, 12.5);
                    for (String id : new String[]{"room_a", "room_b"}) {
                        var site = author.service().state().sites().get(id);
                        SiteSubtypes.set(a.getCommandSource(), site, Set.of(VoiceRooms.SUBTYPE));
                    }
                    var view = VoiceRooms.inspect(server);
                    require(!view.blocked() && view.groups().size() == 2 && view.membership().isEmpty(), "empty rooms not created");
                    Live.verifyGroups(api, view.groups().values());
                    Live.verifyOnlyRooms(api, view.groups().values());
                    manual = Live.createManual(api);
                }
                case "race" -> {
                    move(a, 1.5); move(b, 2.5);
                    Live.race(api, a, b, () -> move(b, 12.5), false);
                }
                case "first_room_race" -> {
                    move(a, 1.5); move(b, 12.5);
                    var author = AuthoringRuntime.get(server); author.enable(a.getUuid());
                    require(VoiceRooms.inspect(server).groups().isEmpty(), "first-room race requires an empty room catalog");
                    Live.race(api, a, b, () -> SiteSubtypes.set(a.getCommandSource(), author.service().state().sites().get("room_a"), Set.of(VoiceRooms.SUBTYPE)), true);
                    SiteSubtypes.set(a.getCommandSource(), author.service().state().sites().get("room_a"), Set.of());
                }
                case "same" -> {
                    move(a, 1.5); move(b, 2.5);
                    verify(source, a, b, "room_a", "room_a");
                }
                case "different" -> {
                    move(b, 7.5);
                    verify(source, a, b, "room_a", "room_b");
                }
                case "outside" -> {
                    move(a, 12.5); move(b, 12.5);
                    verify(source, a, b, null, null);
                }
                case "gates" -> {
                    move(a, 1.5); move(b, 2.5);
                    Live.gates(api, a, b, VoiceRooms.inspect(server), manual, false);
                    move(b, 7.5);
                    Live.gates(api, a, b, VoiceRooms.inspect(server), manual, true);
                    move(b, 12.5);
                    Live.outsideGates(api, a, b, VoiceRooms.inspect(server), manual);
                }
                case "restart" -> {
                    var view = VoiceRooms.inspect(server);
                    require(!view.blocked() && view.groups().size() == 2 && VoiceRooms.assigned(server, "room_a") && VoiceRooms.assigned(server, "room_b"), "restart did not reconstruct both rooms");
                    Live.verifyGroups(api, view.groups().values());
                    Live.verifyOnlyRooms(api, view.groups().values());
                    move(a, 1.5); move(b, 7.5); verify(source, a, b, "room_a", "room_b");
                    manual = Live.createManual(api);
                }
                case "remove" -> {
                    move(a, 12.5); move(b, 12.5);
                    var author = AuthoringRuntime.get(server); author.enable(a.getUuid());
                    var before = VoiceRooms.inspect(server);
                    for (String id : new String[]{"room_a", "room_b"})
                        SiteSubtypes.set(a.getCommandSource(), author.service().state().sites().get(id), Set.of());
                    var after = VoiceRooms.inspect(server);
                    require(!after.blocked() && after.groups().isEmpty(), "removed rooms remain");
                    Live.verifyRemoved(api, before.groups().values(), manual);
                }
                default -> throw new IllegalArgumentException(step);
            }
            source.sendFeedback(() -> Text.literal("Voice rooms trial PASS " + step), false); return 1;
        } catch (Exception failure) {
            source.sendError(Text.literal("Voice rooms trial FAIL " + step + ": " + failure)); return 0;
        }
    }
    private static void verify(ServerCommandSource source, ServerPlayerEntity a, ServerPlayerEntity b, String ra, String rb) {
        var view = VoiceRooms.inspect(source.getServer());
        require(!view.blocked() && java.util.Objects.equals(ra, view.membership().get(a.getUuid())) && java.util.Objects.equals(rb, view.membership().get(b.getUuid())), "synchronous routing mismatch");
        Live.verifyMember(api, a.getUuid(), ra == null ? null : view.groups().get(ra));
        Live.verifyMember(api, b.getUuid(), rb == null ? null : view.groups().get(rb));
    }
    private static AuthoringSite.Region region(String id, int min, int max) {
        return new AuthoringSite.Region(id, "minecraft:overworld", 1, new BlockPosition(min, 64, 1), new BlockPosition(max, 66, 3));
    }
    private static void move(ServerPlayerEntity player, double x) {
        player.teleport(player.getEntityWorld(), x, 64, 2.5, Set.of(), 0, 0, false);
        player.setVelocity(0, 0, 0); player.onLanding();
    }
    private static void fixture(ServerCommandSource source) {
        var server = source.getServer();
        var root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root != null && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                && Files.isRegularFile(root.resolve(".voice-rooms-fixture")) && server.isDedicated() && !server.isOnlineMode()
                && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp())
                && source.getWorld().getRegistryKey() == net.minecraft.world.World.OVERWORLD, "unsafe fixture");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    /** This class is resolved only when the explicitly gated fixture calls it. */
    private static final class Live {
        private static de.maxhenkel.voicechat.api.VoicechatServerApi api(Object value) { return (de.maxhenkel.voicechat.api.VoicechatServerApi) value; }
        static UUID createManual(Object value) {
            var group = api(value).groupBuilder().setName("Fixture unrelated group").setPersistent(true).build();
            require(group != null, "manual group creation failed"); return group.getId();
        }
        static void verifyOnlyRooms(Object value, java.util.Collection<UUID> ids) {
            var groups = api(value).getGroups();
            require(groups.size() == 2 && groups.stream().allMatch(g -> ids.contains(g.getId())), "unexpected live groups or orphans");
        }
        private static java.util.Map<Class<?>, java.util.function.Consumer<de.maxhenkel.voicechat.api.events.Event>> handlers() {
            var callbacks = new java.util.HashMap<Class<?>, java.util.function.Consumer<de.maxhenkel.voicechat.api.events.Event>>();
            var plugins = net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypoints("voicechat", de.maxhenkel.voicechat.api.VoicechatPlugin.class);
            var plugin = plugins.stream().filter(p -> p.getPluginId().equals("chronicle_core:voice_rooms")).findFirst().orElseThrow();
            plugin.registerEvents(new de.maxhenkel.voicechat.api.events.EventRegistration() {
                @SuppressWarnings("unchecked")
                public <T extends de.maxhenkel.voicechat.api.events.Event> void registerEvent(Class<T> type, java.util.function.Consumer<T> callback, int priority) {
                    callbacks.put(type, (java.util.function.Consumer<de.maxhenkel.voicechat.api.events.Event>) (java.util.function.Consumer<?>) callback);
                }
            });
            return callbacks;
        }
        private static boolean event(java.util.Map<Class<?>, java.util.function.Consumer<de.maxhenkel.voicechat.api.events.Event>> callbacks,
                                     Class<? extends de.maxhenkel.voicechat.api.events.Event> type, Object api,
                                     Object sender, Object receiver, Object group, String source) {
            boolean[] cancelled = {false};
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
                return switch (method.getName()) {
                    case "getVoicechat" -> api;
                    case "getSenderConnection", "getConnection" -> sender;
                    case "getReceiverConnection" -> receiver;
                    case "getGroup" -> group;
                    case "getSource" -> source;
                    case "cancel" -> { cancelled[0] = true; yield true; }
                    case "isCancelled" -> cancelled[0];
                    case "isCancellable" -> true;
                    case "toString" -> "Voice rooms fixture event";
                    default -> throw new IllegalStateException("Unexpected event method: " + method.getName());
                };
            });
            var callback = callbacks.get(type); require(callback != null, "missing production callback " + type.getName());
            callback.accept((de.maxhenkel.voicechat.api.events.Event) proxy);
            return cancelled[0];
        }
        private interface Transition { void run() throws Exception; }
        static void race(Object value, ServerPlayerEntity a, ServerPlayerEntity b, Transition transition, boolean inactive) throws Exception {
            var callbacks = handlers(); var voice = api(value);
            var ca = voice.getConnectionOf(a.getUuid()); var cb = voice.getConnectionOf(b.getUuid());
            var entered = new java.util.concurrent.CountDownLatch(1);
            var resume = new java.util.concurrent.CountDownLatch(1);
            var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Class<?> type = de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class;
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> switch (method.getName()) {
                case "getVoicechat" -> voice;
                case "getSource" -> {
                    if (inactive) { entered.countDown(); require(resume.await(2, java.util.concurrent.TimeUnit.SECONDS), "inactive race timed out"); }
                    yield de.maxhenkel.voicechat.api.events.SoundPacketEvent.SOURCE_GROUP;
                }
                case "getSenderConnection" -> {
                    if (!inactive) { entered.countDown(); require(resume.await(2, java.util.concurrent.TimeUnit.SECONDS), "race callback timed out"); }
                    yield ca;
                }
                case "getReceiverConnection" -> cb;
                case "cancel" -> { cancelled.set(true); yield true; }
                case "isCancelled" -> cancelled.get();
                case "isCancellable" -> true;
                default -> throw new IllegalStateException("Unexpected race event method " + method.getName());
            });
            Thread thread = new Thread(() -> {
                try { callbacks.get(type).accept((de.maxhenkel.voicechat.api.events.Event) proxy); }
                catch (Throwable e) { failure.set(e); }
            }, "chronicle-voice-room-race-fixture");
            thread.setDaemon(true); thread.start();
            try {
                require(entered.await(2, java.util.concurrent.TimeUnit.SECONDS), "callback never captured the old snapshot");
                transition.run();
            } finally { resume.countDown(); }
            thread.join(2500);
            require(!thread.isAlive() && failure.get() == null && cancelled.get(), "stale voice callback was not cancelled: " + failure.get());
        }
        static void gates(Object value, ServerPlayerEntity a, ServerPlayerEntity b, io.github.chronicle.core.voice.VoiceRoomGroups.Routing view, UUID manual, boolean different) {
            var callbacks = handlers(); var voice = api(value);
            var ca = voice.getConnectionOf(a.getUuid()); var cb = voice.getConnectionOf(b.getUuid());
            require(ca != null && cb != null, "voice connections unavailable");
            for (String source : new String[]{de.maxhenkel.voicechat.api.events.SoundPacketEvent.SOURCE_GROUP,
                    de.maxhenkel.voicechat.api.events.SoundPacketEvent.SOURCE_PROXIMITY,
                    de.maxhenkel.voicechat.api.events.SoundPacketEvent.SOURCE_SPECTATOR}) {
                require(event(callbacks, de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class, voice, ca, cb, null, source) == different, "sound gate mismatch " + source);
                require(event(callbacks, de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class, voice, cb, ca, null, source) == different, "reverse sound gate mismatch " + source);
            }
            var own = voice.getGroup(view.groups().get("room_a")); var foreign = voice.getGroup(manual);
            require(!event(callbacks, de.maxhenkel.voicechat.api.events.JoinGroupEvent.class, voice, ca, null, own, null), "own room join blocked");
            require(event(callbacks, de.maxhenkel.voicechat.api.events.JoinGroupEvent.class, voice, ca, null, foreign, null), "foreign join allowed inside");
            require(event(callbacks, de.maxhenkel.voicechat.api.events.LeaveGroupEvent.class, voice, ca, null, null, null), "inside leave allowed");
            require(event(callbacks, de.maxhenkel.voicechat.api.events.CreateGroupEvent.class, voice, ca, null, null, null), "inside create allowed");
        }
        static void outsideGates(Object value, ServerPlayerEntity a, ServerPlayerEntity b, io.github.chronicle.core.voice.VoiceRoomGroups.Routing view, UUID manual) {
            var callbacks = handlers(); var voice = api(value);
            var ca = voice.getConnectionOf(a.getUuid()); var cb = voice.getConnectionOf(b.getUuid());
            require(ca != null && cb != null, "voice connections unavailable");
            for (String source : new String[]{de.maxhenkel.voicechat.api.events.SoundPacketEvent.SOURCE_GROUP,
                    de.maxhenkel.voicechat.api.events.SoundPacketEvent.SOURCE_PROXIMITY,
                    de.maxhenkel.voicechat.api.events.SoundPacketEvent.SOURCE_SPECTATOR}) {
                require(event(callbacks, de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class, voice, ca, cb, null, source), "inside-outside sound allowed");
                require(event(callbacks, de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class, voice, cb, ca, null, source), "outside-inside sound allowed");
            }
            require(event(callbacks, de.maxhenkel.voicechat.api.events.JoinGroupEvent.class, voice, cb, null, voice.getGroup(view.groups().get("room_a")), null), "outside private join allowed");
            require(!event(callbacks, de.maxhenkel.voicechat.api.events.JoinGroupEvent.class, voice, cb, null, voice.getGroup(manual), null), "outside manual join blocked");
        }
        static void verifyGroups(Object value, java.util.Collection<UUID> groups) {
            for (UUID id : groups) {
                var group = api(value).getGroup(id);
                require(group != null && group.isHidden() && group.isPersistent() && group.getType() == de.maxhenkel.voicechat.api.Group.Type.ISOLATED, "room group privacy mismatch");
            }
        }
        static void verifyMember(Object value, UUID player, UUID expected) {
            var connection = api(value).getConnectionOf(player);
            require(connection != null, "voice connection unavailable for fixture player");
            var group = connection.getGroup();
            require(java.util.Objects.equals(expected, group == null ? null : group.getId()), "voice API membership mismatch");
        }
        static void verifyRemoved(Object value, java.util.Collection<UUID> ids, UUID unrelated) {
            var groups = api(value).getGroups();
            for (UUID id : ids) require(groups.stream().noneMatch(g -> id.equals(g.getId())), "owned group survived removal");
            require(unrelated != null && groups.stream().anyMatch(g -> unrelated.equals(g.getId())), "unrelated group removed");
        }
    }
}
