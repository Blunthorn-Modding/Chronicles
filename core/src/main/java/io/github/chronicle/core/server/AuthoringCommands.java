package io.github.chronicle.core.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.AuthoringCatalog;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Canonical Region, Location and Editing commands backed by the shared authoring services. */
public final class AuthoringCommands {
    private AuthoringCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var regions = literal("regions").requires(AuthoringCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.REGIONS));
        regions.then(literal("create").requires(AuthoringCommands::isGm)
                        .then(argument("name", StringArgumentType.string())
                                .executes(context -> regionBegin(context.getSource(), StringArgumentType.getString(context, "name"), null))
                                .then(literal("states")
                                        .then(literal("start").executes(context -> regionBegin(context.getSource(), StringArgumentType.getString(context, "name"), AreaState.START)))
                                        .then(literal("end").executes(context -> regionBegin(context.getSource(), StringArgumentType.getString(context, "name"), AreaState.END))))))
                .then(literal("edit").requires(AuthoringCommands::isGm)
                        .executes(context -> edit(context.getSource(), runtime -> {
                            var draft = runtime.resumeRegionDraft(player(context.getSource()));
                            return "Resumed Region draft " + draft.id() + "; its outline is visible.";
                        })).then(argument("id", StringArgumentType.string())
                                .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.REGION, true, builder))
                                .executes(context -> edit(context.getSource(), runtime -> {
                                    var draft = runtime.resumeRegionDraft(player(context.getSource()), id(context));
                                    return "Editing Region " + draft.id() + "; both saved corners are loaded and its outline is visible.";
                                }))))
                .then(regionPosition("pos1", 1))
                .then(regionPosition("pos2", 2))
                .then(literal("save").requires(AuthoringCommands::isGm)
                        .executes(context -> regionSave(context.getSource(), false))
                        .then(literal("confirm").executes(context -> regionSave(context.getSource(), true))))
                .then(literal("cancel").requires(AuthoringCommands::isGm)
                        .executes(context -> regionCancel(context.getSource())))
                .then(literal("preview").requires(AuthoringCommands::isGm)
                        .then(argument("id", StringArgumentType.string())
                                .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.REGION, true, builder))
                                .executes(context -> preview(context.getSource(), id(context), AuthoringCatalog.Kind.REGION))))
                .then(literal("list").requires(AuthoringCommands::isGm)
                        .executes(context -> list(context.getSource(), AuthoringCatalog.Kind.REGION)))
                .then(literal("remove").requires(AuthoringCommands::isGm)
                        .then(argument("id", StringArgumentType.string())
                                .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.REGION, true, builder))
                                .executes(context -> remove(context.getSource(), id(context), AuthoringCatalog.Kind.REGION))))
                .then(literal("validate").requires(AuthoringCommands::isGm).executes(context -> validateSites(context.getSource(),AuthoringCatalog.Kind.REGION)))
                .then(regionStates());

        var locations = literal("locations").requires(AuthoringCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.LOCATIONS));
        var points = literal("points").requires(AuthoringCommands::isGm);
        var blocks = literal("blocks").requires(AuthoringCommands::isGm);
        for (boolean replace : new boolean[]{false, true}) {
            String pointVerb = replace ? "replace" : "save";
            points.then(literal(pointVerb).then(argument("id", StringArgumentType.string())
                    .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.LOCATION, replace, builder))
                    .then(literal("here").executes(context -> edit(context.getSource(), runtime -> {
                        var source = context.getSource();
                        var player = player(source);
                        var site = new AuthoringSite.Location(id(context), dimension(source), 1,
                                player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                        validateWritable(source, site);
                        return persist(source, runtime, site, replace);
                    })))));
            blocks.then(literal(replace ? "replace" : "bind").then(argument("id", StringArgumentType.string())
                    .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.BLOCK, replace, builder))
                    .then(literal("target").executes(context -> edit(context.getSource(), runtime -> {
                        var source = context.getSource();
                        BlockPos target = target(source);
                        String expected = Registries.BLOCK.getId(source.getWorld().getBlockState(target).getBlock()).toString();
                        var site = new AuthoringSite.Anchor(id(context), dimension(source), 1, position(target), expected);
                        validateWritable(source, site);
                        return persist(source, runtime, site, replace);
                    })))));
        }
        locations.then(points).then(blocks)
                .then(literal("preview").requires(AuthoringCommands::isGm)
                        .then(argument("id", StringArgumentType.string()).suggests(AuthoringCommands::suggestLocations)
                                .executes(context -> preview(context.getSource(), id(context), null))))
                .then(literal("remove").requires(AuthoringCommands::isGm)
                        .then(argument("id", StringArgumentType.string()).suggests(AuthoringCommands::suggestLocations)
                                .executes(context -> remove(context.getSource(), id(context), null))))
                .then(literal("list").requires(AuthoringCommands::isGm)
                        .executes(context -> list(context.getSource(), null))
                        .then(literal("points").executes(context -> list(context.getSource(), AuthoringCatalog.Kind.LOCATION)))
                        .then(literal("blocks").executes(context -> list(context.getSource(), AuthoringCatalog.Kind.BLOCK))))
                .then(literal("validate").requires(AuthoringCommands::isGm)
                        .executes(context -> validateSites(context.getSource(), null))
                        .then(literal("points").executes(context -> validateSites(context.getSource(), AuthoringCatalog.Kind.LOCATION)))
                        .then(literal("blocks").executes(context -> validateSites(context.getSource(), AuthoringCatalog.Kind.BLOCK))));

        var editing = literal("editing").requires(AuthoringCommands::isGm)
                .then(literal("on").requires(AuthoringCommands::isGm).executes(context -> run(context.getSource(), runtime -> {
                    requireEditable(context.getSource());
                    runtime.enable(player(context.getSource()).getUuid());
                    return "Editing enabled. Select a Region, Point or Block site from its canonical family.";
                })))
                .then(literal("off").requires(AuthoringCommands::isGm).executes(context -> run(context.getSource(), runtime -> {
                    AreaChanges.cancelIfPresent(context.getSource());
                    runtime.disable(player(context.getSource()).getUuid());
                    AuthoringRuntime.clearPreview(player(context.getSource()));
                    return "Editing disabled; selection cleared.";
                })));

        editing.then(literal("preview").then(literal("hide").executes(context->run(context.getSource(),runtime->{AuthoringRuntime.clearPreview(player(context.getSource()));return "Authoring preview hidden.";}))));
        dispatcher.register(literal("chronicle").then(regions).then(locations).then(editing));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> regionPosition(String name, int corner) {
        return literal(name).requires(AuthoringCommands::isGm)
                .executes(context -> regionCorner(context.getSource(), corner, player(context.getSource()).getBlockPos()))
                .then(argument("position", BlockPosArgumentType.blockPos()).executes(context ->
                        regionCorner(context.getSource(), corner, BlockPosArgumentType.getBlockPos(context, "position"))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> regionStates() {
        return literal("states").requires(AuthoringCommands::isGm)
                .then(literal("recover").then(argument("region",StringArgumentType.string()).suggests((c,b)->suggest(c.getSource(),AuthoringCatalog.Kind.REGION,true,b)).executes(c->CoreMenuServer.executeCommand(c.getSource(),Action.AREA_RECOVER,Page.REGIONS,StringArgumentType.getString(c,"region"),""))))
                .then(stateAction("capture", Action.AREA_CAPTURE_REVIEW, Action.AREA_CAPTURE, false))
                .then(stateAction("apply", Action.AREA_APPLY_REVIEW, Action.AREA_APPLY, false))
                .then(stateAction("remove", Action.AREA_DELETE_REVIEW, Action.AREA_DELETE, true));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> stateAction(String name, Action review, Action confirm, boolean unchanged) {
        return literal(name)
                .then(stateDecision("review", review, unchanged))
                .then(stateDecision("confirm", confirm, unchanged));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> stateDecision(String name, Action action, boolean unchanged) {
        var region = argument("region", StringArgumentType.string())
                .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.REGION, true, builder));
        region.then(literal("start").executes(context -> state(context.getSource(), action, context, "start")))
                .then(literal("end").executes(context -> state(context.getSource(), action, context, "end")));
        if (unchanged) region.then(literal("unchanged").executes(context -> state(context.getSource(), action, context, "unchanged")));
        return literal(name).then(region);
    }

    private static int state(ServerCommandSource source, Action action,
                             com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, String state) {
        return CoreMenuServer.executeCommand(source, action, Page.REGIONS, StringArgumentType.getString(context, "region"), state);
    }

    private static int regionBegin(ServerCommandSource source, String name, AreaState initial) {
        return run(source, runtime -> checkedBegin(source, name, initial));
    }

    private static String checkedBegin(ServerCommandSource source, String name, AreaState initial) throws IOException {
        try { return "Region selection " + RegionAuthoringWorkflow.begin(source, name, initial) + " opened. Set pos1 and pos2, then save."; }
        catch (IOException exception) { throw exception; }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Region selection could not start.", exception); }
    }

    private static int regionSave(ServerCommandSource source, boolean confirm) {
        return run(source, runtime -> checkedSave(source, confirm));
    }

    private static String checkedSave(ServerCommandSource source, boolean confirm) throws IOException {
        try { return RegionAuthoringWorkflow.save(source, confirm); }
        catch (IOException exception) { throw exception; }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Region save failed.", exception); }
    }

    private static int regionCancel(ServerCommandSource source) {
        return run(source, runtime -> RegionAuthoringWorkflow.cancel(source));
    }

    private static int regionCorner(ServerCommandSource source, int which, BlockPos position) {
        return edit(source, runtime -> {
            var player = player(source);
            if (AreaChanges.hasDraft(source.getServer(), player.getUuid())) {
                var draft = AreaChanges.setDraftCorner(player, which, position(position));
                return "Set saved-state Region " + draft.name() + " position " + which + " at " + coordinates(position(position)) + ".";
            }
            if (runtime.regionDraft(player.getUuid()).isEmpty()) throw new IllegalStateException("No Region selection is open; use regions create or edit first.");
            var draft = runtime.setRegionDraftCorner(player, which, position(position));
            return "Set Region draft " + draft.id() + " position " + which + " at " + coordinates(position(position)) + ".";
        });
    }

    private static int preview(ServerCommandSource source, String id, AuthoringCatalog.Kind kind) {
        return run(source, runtime -> {
            var site = typedSite(runtime, id, kind);
            runtime.preview(player(source), site);
            return describe(site) + "; validation=" + validate(source, site) + ".";
        });
    }

    private static int remove(ServerCommandSource source, String id, AuthoringCatalog.Kind kind) {
        return edit(source, runtime -> {
            requireNoAreaDraft(source);
            var site = typedSite(runtime, id, kind);
            if (site instanceof AuthoringSite.Region && AreaChanges.forRegion(source.getServer(), id).isPresent()) {
                throw new IllegalStateException("Remove Saved states before deleting this Region.");
            }
            runtime.service().remove(id);
            AuthoringRuntime.clearPreview(player(source));
            log(source, "remove", id, runtime.service().state().revision());
            return "Removed " + describe(site) + "; authoring revision=" + runtime.service().state().revision() + ".";
        });
    }

    private static int list(ServerCommandSource source, AuthoringCatalog.Kind kind) {
        return run(source, runtime -> {
            var sites = runtime.service().state().sites().values().stream().filter(site -> matches(site, kind)).map(AuthoringSite::id).toList();
            String label = kind == AuthoringCatalog.Kind.REGION ? "Regions" : kind == AuthoringCatalog.Kind.LOCATION ? "Points" : kind == AuthoringCatalog.Kind.BLOCK ? "Block sites" : "Locations";
            return label + "=" + sites.size() + (sites.isEmpty() ? "." : ": " + String.join(", ", sites));
        });
    }

    private static int validateSites(ServerCommandSource source, AuthoringCatalog.Kind kind) {
        return run(source, runtime -> {
            int valid = 0, invalid = 0, unresolved = 0;
            for (var site : runtime.service().state().sites().values()) {
                if (!matches(site, kind)) continue;
                String result = validate(source, site);
                if (result.equals("valid")) valid++;
                else if (result.startsWith("unresolved")) unresolved++;
                else invalid++;
                if (!result.equals("valid") && invalid + unresolved <= 20) {
                    String line = site.id() + ": " + result;
                    source.sendFeedback(() -> Text.literal(line), false);
                }
            }
            return "Location validation: valid=" + valid + "; invalid=" + invalid + "; unresolved=" + unresolved + ".";
        });
    }

    private static boolean matches(AuthoringSite site, AuthoringCatalog.Kind kind) {
        if (kind != null) return kind.accepts(site);
        return site instanceof AuthoringSite.Location || site instanceof AuthoringSite.Anchor;
    }

    private static AuthoringSite typedSite(AuthoringRuntime runtime, String id, AuthoringCatalog.Kind kind) {
        var site = runtime.service().state().sites().get(id);
        if (site == null || !matches(site, kind)) {
            String label = kind == AuthoringCatalog.Kind.REGION ? "Region" : kind == AuthoringCatalog.Kind.LOCATION ? "Point" : kind == AuthoringCatalog.Kind.BLOCK ? "Block site" : "Location";
            throw new IllegalArgumentException("Unknown " + label + ": " + id);
        }
        return site;
    }

    private static String persist(ServerCommandSource source, AuthoringRuntime runtime, AuthoringSite site, boolean replace) throws IOException {
        ChronicleCore.authoring().validate(site);
        var saved = runtime.service().put(site, replace);
        log(source, replace ? "replace" : "save", saved.id(), saved.revision());
        runtime.preview(player(source), saved);
        return "Saved " + describe(saved) + ".";
    }

    private static void log(ServerCommandSource source, String action, String id, long revision) {
        ChronicleCore.LOGGER.info("Authoring {}: {} by {} ({}), revision={}", action, id, source.getName(),
                source.getPlayer() == null ? "console" : source.getPlayer().getUuid(), revision);
    }

    private static String describe(AuthoringSite site) {
        String type = site instanceof AuthoringSite.Region ? "Region" : site instanceof AuthoringSite.Location ? "Point" : "Block site";
        String details;
        if (site instanceof AuthoringSite.Region region) {
            details = "from=" + coordinates(region.min()) + "; to=" + coordinates(region.max()) + "; volume=" + region.volume();
        } else if (site instanceof AuthoringSite.Location point) {
            details = String.format(Locale.ROOT, "at=%.3f %.3f %.3f; yaw=%.2f; pitch=%.2f", point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        } else {
            var anchor = (AuthoringSite.Anchor) site;
            details = "at=" + coordinates(anchor.position()) + "; expected=" + anchor.expectedBlock();
        }
        return type + " " + site.id() + "; revision=" + site.revision() + "; dimension=" + site.dimension() + "; " + details;
    }

    private static String coordinates(BlockPosition position) { return position.x() + " " + position.y() + " " + position.z(); }
    private static BlockPosition position(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }
    private static BlockPos block(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
    private static String dimension(ServerCommandSource source) { return source.getWorld().getRegistryKey().getValue().toString(); }
    private static String id(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context) { return StringArgumentType.getString(context, "id"); }

    private static void validateWritable(ServerCommandSource source, AuthoringSite site) {
        String result = validate(source, site);
        if (!result.equals("valid")) throw new IllegalArgumentException("Cannot save site: " + result);
    }

    private static String validate(ServerCommandSource source, AuthoringSite site) {
        try { ChronicleCore.authoring().validate(site); }
        catch (IllegalArgumentException exception) { return "invalid: " + exception.getMessage(); }
        var world = source.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(site.dimension())));
        if (world == null) return "invalid: dimension unavailable";
        try {
            if (site instanceof AuthoringSite.Region region) {
                checkBounds(world, block(region.min())); checkBounds(world, block(region.max()));
            } else if (site instanceof AuthoringSite.Location point) {
                checkBounds(world, BlockPos.ofFloored(point.x(), point.y(), point.z()));
            } else if (site instanceof AuthoringSite.Anchor anchor) {
                BlockPos pos = block(anchor.position());
                checkBounds(world, pos);
                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return "unresolved: anchor chunk unloaded";
                String actual = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
                if (!actual.equals(anchor.expectedBlock())) return "invalid: expected " + anchor.expectedBlock() + ", found " + actual;
            }
            return "valid";
        } catch (IllegalArgumentException exception) { return "invalid: " + exception.getMessage(); }
    }

    private static void checkBounds(ServerWorld world, BlockPos position) {
        if (!world.isInBuildLimit(position)) throw new IllegalArgumentException("Position outside dimension build limits.");
        if (!world.getWorldBorder().contains(position)) throw new IllegalArgumentException("Position outside world border.");
    }

    private static BlockPos target(ServerCommandSource source) {
        var player = player(source);
        var start = player.getEyePos();
        var end = start.add(player.getRotationVec(1).multiply(6));
        for (int sample = 0; sample <= 24; sample++) {
            var pos = BlockPos.ofFloored(start.lerp(end, sample / 24.0));
            if (!source.getWorld().getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                throw new IllegalArgumentException("Target ray crosses an unloaded chunk.");
            }
        }
        HitResult hit = source.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) throw new IllegalArgumentException("Look at a block within six blocks.");
        return block.getBlockPos();
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        if (source.getPlayer() == null) throw new IllegalStateException("This authoring command requires a player.");
        return source.getPlayer();
    }

    private static boolean isGm(ServerCommandSource source) { return source.hasPermissionLevel(2); }

    private static void requireEditable(ServerCommandSource source) {
        if (!AuthoringRuntime.editable(source.getServer())) throw new IllegalStateException("Editing requires a non-active, available Chronicle session.");
    }

    private static void requireNoAreaDraft(ServerCommandSource source) {
        if (AreaChanges.hasDraft(source.getServer(), player(source).getUuid())) throw new IllegalStateException("A saved-state Region draft is active. Use /chronicle regions save or cancel it first.");
    }

    private static CompletableFuture<Suggestions> suggest(ServerCommandSource source, AuthoringCatalog.Kind kind, boolean existing, SuggestionsBuilder builder) {
        try {
            var sites = AuthoringRuntime.get(source.getServer()).service().state().sites();
            var choices = existing ? sites.values().stream().filter(site -> kind == null || kind.accepts(site)).map(AuthoringSite::id).toList()
                    : ChronicleCore.authoring().roles().stream().filter(role -> role.kind() == kind && !sites.containsKey(role.id())).map(AuthoringCatalog.Role::id).toList();
            return CommandSource.suggestMatching(choices, builder);
        } catch (IllegalStateException exception) { return builder.buildFuture(); }
    }

    private static CompletableFuture<Suggestions> suggestLocations(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                                                    SuggestionsBuilder builder) {
        try {
            var choices = AuthoringRuntime.get(context.getSource().getServer()).service().state().sites().values().stream()
                    .filter(site -> site instanceof AuthoringSite.Location || site instanceof AuthoringSite.Anchor).map(AuthoringSite::id).toList();
            return CommandSource.suggestMatching(choices, builder);
        } catch (IllegalStateException exception) { return builder.buildFuture(); }
    }

    private static void requireEditor(ServerCommandSource source, AuthoringRuntime runtime) {
        if (RuntimeHolds.reason(source.getServer()) != null) throw new IllegalStateException("Finish pending Chronicle operations before authoring changes.");
        requireEditable(source);
        var player = player(source);
        runtime.selection(player.getUuid());
        if (source.getWorld() != player.getEntityWorld()) throw new IllegalStateException("Authoring edits must use the player's actual dimension.");
    }

    private static int edit(ServerCommandSource source, Operation operation) {
        return run(source, runtime -> { requireEditor(source, runtime); return operation.run(runtime); });
    }

    private static int run(ServerCommandSource source, Operation operation) {
        if (!isGm(source)) { source.sendError(Text.literal("Authoring requires operator permission level 2.")); return 0; }
        try {
            String message = operation.run(AuthoringRuntime.get(source.getServer()));
            source.sendFeedback(() -> Text.literal(message), false);
            return 1;
        } catch (IOException exception) {
            ChronicleCore.LOGGER.error("Authoring was not saved; in-memory definitions unchanged", exception);
            source.sendError(Text.literal("Authoring could not be saved; no change committed. Check the server log."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendError(Text.literal(exception.getMessage()));
        }
        return 0;
    }

    @FunctionalInterface private interface Operation { String run(AuthoringRuntime runtime) throws IOException; }
}
