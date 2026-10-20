package io.github.chronicle.core.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.chronicle.core.authoring.AuthoringCatalog;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
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

/** Shared metadata authoring; no terrain, inventories, gameplay flags or spawns are mutated. */
public final class AuthoringCommands {
    private AuthoringCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var author = literal("author").requires(source -> source.hasPermissionLevel(2))
                .executes(context -> AuthoringUi.open(context.getSource()))
                .then(literal("menu").executes(context -> menu(context.getSource(), 1))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> menu(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
                .then(literal("preview").then(literal("hide").executes(context -> run(context.getSource(), runtime -> {
                    AuthoringRuntime.clearPreview(player(context.getSource()));
                    return "Authoring preview hidden.";
                }))))
                .then(literal("mode")
                        .then(literal("on").executes(context -> run(context.getSource(), runtime -> {
                            requireEditable(context.getSource());
                            runtime.enable(player(context.getSource()).getUuid());
                            return "Author mode enabled. Select two corners or save a location/block anchor.";
                        })))
                        .then(literal("off").executes(context -> run(context.getSource(), runtime -> {
                            runtime.disable(player(context.getSource()).getUuid());
                            AuthoringRuntime.clearPreview(player(context.getSource()));
                            return "Author mode disabled; selection cleared.";
                        }))));
        var select = literal("select")
                .then(literal("clear").executes(context -> edit(context.getSource(), runtime -> {
                    runtime.selection(player(context.getSource()).getUuid(), new AuthoringRuntime.Selection(null, null, null));
                    AuthoringRuntime.clearPreview(player(context.getSource()));
                    return "Author selection cleared.";
                })));
        for (int corner = 1; corner <= 2; corner++) {
            final int which = corner;
            select.then(literal("pos" + corner)
                    .executes(context -> corner(context.getSource(), which, player(context.getSource()).getBlockPos()))
                    .then(argument("position", BlockPosArgumentType.blockPos())
                            .executes(context -> corner(context.getSource(), which, BlockPosArgumentType.getBlockPos(context, "position")))))
                    .then(literal("target" + corner).executes(context -> run(context.getSource(), runtime -> {
                        var source = context.getSource();
                        requireEditor(source, runtime);
                        return setCorner(source, runtime, which, target(source));
                    })));
        }
        author.then(select);
        for (boolean replace : new boolean[]{false, true}) {
            String verb = replace ? "replace" : "save";
            // Merges into the same region/location/block nodes through Brigadier.
            author.then(literal("region").then(literal(verb).then(argument("id", StringArgumentType.string())
                    .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.REGION, replace, builder))
                    .executes(context -> edit(context.getSource(), runtime -> {
                        var source = context.getSource();
                        var selected = runtime.selection(player(source).getUuid());
                        if (selected.first() == null || selected.second() == null) throw new IllegalStateException("Select both corners first.");
                        var site = new AuthoringSite.Region(id(context), selected.dimension(), 1, selected.first(), selected.second());
                        validateWritable(source, site);
                        return persist(source, runtime, site, replace);
                    })))));
            author.then(literal("location").then(literal(verb).then(argument("id", StringArgumentType.string())
                    .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.LOCATION, replace, builder))
                    .then(literal("here").executes(context -> edit(context.getSource(), runtime -> {
                        var source = context.getSource();
                        var player = player(source);
                        var site = new AuthoringSite.Location(id(context), dimension(source), 1,
                                player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                        validateWritable(source, site);
                        return persist(source, runtime, site, replace);
                    }))))));
            author.then(literal("block").then(literal(replace ? "replace" : "bind").then(argument("id", StringArgumentType.string())
                    .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.BLOCK, replace, builder))
                    .then(literal("target").executes(context -> edit(context.getSource(), runtime -> {
                        var source = context.getSource();
                        BlockPos target = target(source);
                        String expected = Registries.BLOCK.getId(source.getWorld().getBlockState(target).getBlock()).toString();
                        var site = new AuthoringSite.Anchor(id(context), dimension(source), 1, position(target), expected);
                        validateWritable(source, site);
                        return persist(source, runtime, site, replace);
                    }))))));
        }
        // CF-10 region draft grammar.  The legacy save/replace <id> commands above remain
        // available; these commands make a named draft explicit so the menu can resume it.
        var regions = literal("region")
                .executes(context -> run(context.getSource(), runtime -> {
                    var sites = runtime.service().state().sites().values().stream()
                            .filter(site -> site instanceof AuthoringSite.Region).map(AuthoringSite::id).toList();
                    return "Regions=" + sites.size() + (sites.isEmpty() ? ". Select pos1 and pos2, then use region create <id>."
                            : ": " + String.join(", ", sites));
                }))
                .then(literal("list").executes(context -> run(context.getSource(), runtime -> {
                    var sites = runtime.service().state().sites().values().stream()
                            .filter(site -> site instanceof AuthoringSite.Region).map(AuthoringSite::id).toList();
                    return "Regions=" + sites.size() + (sites.isEmpty() ? "." : ": " + String.join(", ", sites));
                })))
                .then(literal("pos1").executes(context -> regionCorner(context.getSource(), 1, player(context.getSource()).getBlockPos()))
                        .then(argument("position", BlockPosArgumentType.blockPos()).executes(context -> regionCorner(context.getSource(), 1,
                                BlockPosArgumentType.getBlockPos(context, "position")))))
                .then(literal("pos2").executes(context -> regionCorner(context.getSource(), 2, player(context.getSource()).getBlockPos()))
                        .then(argument("position", BlockPosArgumentType.blockPos()).executes(context -> regionCorner(context.getSource(), 2,
                                BlockPosArgumentType.getBlockPos(context, "position")))))
                .then(literal("create").then(argument("id", StringArgumentType.string())
                        .executes(context -> edit(context.getSource(), runtime -> {
                            var draft = runtime.beginRegionDraft(player(context.getSource()), id(context));
                            return "Region draft " + draft.id() + " opened. Set pos1 and pos2, then use region save.";
                        }))))
                .then(literal("edit").executes(context -> edit(context.getSource(), runtime -> {
                    var draft = runtime.resumeRegionDraft(player(context.getSource()));
                    return "Resumed region draft " + draft.id() + "; its outline is visible.";
                })).then(argument("id", StringArgumentType.string())
                        .suggests((context, builder) -> suggest(context.getSource(), AuthoringCatalog.Kind.REGION, true, builder))
                        .executes(context -> edit(context.getSource(), runtime -> {
                            var draft = runtime.resumeRegionDraft(player(context.getSource()), id(context));
                            return "Editing region " + draft.id() + "; both saved corners are loaded and its outline is visible.";
                        }))))
                .then(literal("save").executes(context -> edit(context.getSource(), runtime -> {
                    var saved = runtime.saveRegionDraft(player(context.getSource()));
                    log(context.getSource(), "save-region-draft", saved.id(), saved.revision());
                    return "Saved " + describe(saved) + ".";
                })))
                .then(literal("selection").then(literal("show").executes(context -> run(context.getSource(), runtime -> {
                    var selected = runtime.selection(player(context.getSource()).getUuid());
                    return selected.dimension() == null ? "No region positions selected." : "Selected positions in " + selected.dimension()
                            + ": pos1=" + selected.first() + "; pos2=" + selected.second() + ".";
                }))).then(literal("clear").executes(context -> edit(context.getSource(), runtime -> {
                    runtime.selection(player(context.getSource()).getUuid(), new AuthoringRuntime.Selection(null, null, null));
                    AuthoringRuntime.clearPreview(player(context.getSource()));
                    return "Region positions cleared.";
                }))));
        author.then(regions);
        author.then(literal("list").executes(context -> run(context.getSource(), runtime -> {
            var state = runtime.service().state();
            return "Authoring revision=" + state.revision() + "; sites=" + state.sites().size() + ": "
                    + String.join(", ", state.sites().keySet());
        })));
        author.then(literal("show").then(argument("id", StringArgumentType.string())
                .suggests((context, builder) -> {
                    try { return suggestValues(AuthoringRuntime.get(context.getSource().getServer()).service().state().sites().keySet(), builder); }
                    catch (IllegalStateException exception) { return builder.buildFuture(); }
                })
                .executes(context -> run(context.getSource(), runtime -> {
                    var site = site(runtime, id(context));
                    var player = context.getSource().getPlayer();
                    if (player != null) runtime.preview(player, site);
                    return describe(site) + "; validation=" + validate(context.getSource(), site) + ".";
                }))));
        author.then(literal("remove").then(argument("id", StringArgumentType.string())
                .suggests((context, builder) -> suggest(context.getSource(), null, true, builder))
                .executes(context -> edit(context.getSource(), runtime -> {
                    runtime.service().remove(id(context));
                    AuthoringRuntime.clearPreview(player(context.getSource()));
                    log(context.getSource(), "remove", id(context), runtime.service().state().revision());
                    return "Removed authoring site " + id(context) + "; revision=" + runtime.service().state().revision() + ".";
                }))));
        author.then(literal("validate").executes(context -> run(context.getSource(), runtime -> {
            int valid = 0, invalid = 0, unresolved = 0;
            for (var site : runtime.service().state().sites().values()) {
                String result = validate(context.getSource(), site);
                if (result.equals("valid")) valid++;
                else if (result.startsWith("unresolved")) unresolved++;
                else invalid++;
                if (!result.equals("valid") && invalid + unresolved <= 20) {
                    String line = site.id() + ": " + result;
                    context.getSource().sendFeedback(() -> Text.literal(line), false);
                }
            }
            return "Authoring validation: valid=" + valid + "; invalid=" + invalid + "; unresolved=" + unresolved + ".";
        })));
        dispatcher.register(literal("chronicle").then(author));
    }

    private static int corner(ServerCommandSource source, int which, BlockPos position) {
        return edit(source, runtime -> setCorner(source, runtime, which, position));
    }

    private static int regionCorner(ServerCommandSource source, int which, BlockPos position) {
        return edit(source, runtime -> {
            var player = player(source);
            if (runtime.regionDraft(player.getUuid()).isPresent()) {
                var draft = runtime.setRegionDraftCorner(player, which, position(position));
                return "Set region draft " + draft.id() + " position " + which + " at " + coordinates(position(position)) + ".";
            }
            return setCorner(source, runtime, which, position);
        });
    }

    private static String setCorner(ServerCommandSource source, AuthoringRuntime runtime, int which, BlockPos block) {
        var player = player(source);
        var old = runtime.selection(player.getUuid());
        String dimension = dimension(source);
        if (old.dimension() != null && !old.dimension().equals(dimension)) {
            throw new IllegalStateException("Selection is in another dimension; clear it first.");
        }
        checkBounds(source.getWorld(), block);
        var selected = new AuthoringRuntime.Selection(dimension, which == 1 ? position(block) : old.first(), which == 2 ? position(block) : old.second());
        runtime.selection(player.getUuid(), selected);
        if (selected.first() != null && selected.second() != null) {
            runtime.preview(player, new AuthoringSite.Region("selection", dimension, 1, selected.first(), selected.second()));
        }
        return "Selected corner " + which + " at " + block.getX() + " " + block.getY() + " " + block.getZ() + " in " + dimension + ".";
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
        String type = site instanceof AuthoringSite.Region ? "region" : site instanceof AuthoringSite.Location ? "location" : "block";
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

    private static String coordinates(BlockPosition p) { return p.x() + " " + p.y() + " " + p.z(); }
    private static BlockPosition position(BlockPos p) { return new BlockPosition(p.getX(), p.getY(), p.getZ()); }
    private static BlockPos block(BlockPosition p) { return new BlockPos(p.x(), p.y(), p.z()); }
    private static String dimension(ServerCommandSource source) { return source.getWorld().getRegistryKey().getValue().toString(); }
    private static String id(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context) { return StringArgumentType.getString(context, "id"); }

    private static AuthoringSite site(AuthoringRuntime runtime, String id) {
        var site = runtime.service().state().sites().get(id);
        if (site == null) throw new IllegalArgumentException("Unknown authoring site: " + id);
        return site;
    }

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
        // Do not raycast through a chunk that is not already available.
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
    private static void requireEditable(ServerCommandSource source) {
        if (!AuthoringRuntime.editable(source.getServer())) throw new IllegalStateException("Authoring edits require a non-active, available Chronicle session.");
    }

    private static CompletableFuture<Suggestions> suggest(ServerCommandSource source, AuthoringCatalog.Kind kind, boolean existing, SuggestionsBuilder builder) {
        try {
            var sites = AuthoringRuntime.get(source.getServer()).service().state().sites();
            var choices = existing ? sites.values().stream().filter(site -> kind == null || kind.accepts(site)).map(AuthoringSite::id).toList()
                    : ChronicleCore.authoring().roles().stream().filter(role -> role.kind() == kind && !sites.containsKey(role.id())).map(AuthoringCatalog.Role::id).toList();
            return suggestValues(choices, builder);
        } catch (IllegalStateException exception) { return builder.buildFuture(); }
    }

    private static int menu(ServerCommandSource source, int page) {
        return run(source, runtime -> {
            source.sendFeedback(() -> Text.literal("Authoring: select corners, then choose a registered place. Click an action and press Enter.").formatted(Formatting.GOLD), false);
            source.sendFeedback(() -> Text.empty().append(choice("[Author mode] ", "/chronicle author mode on", "Enter author mode while the session is not active."))
                    .append(choice("[Corner 1] ", "/chronicle author select target1", "Look at the first corner block within six blocks."))
                    .append(choice("[Corner 2] ", "/chronicle author select target2", "Look at the second corner block within six blocks."))
                    .append(choice("[Loot chests] ", "/chronicle author chest", "Look at a container, then choose its loot type."))
                    .append(choice("[Clear]", "/chronicle author select clear", "Clear the selection and its preview.")), false);
            var roles = ChronicleCore.authoring().roles();
            int pages = Math.max(1, (roles.size() + 15) / 16);
            if (page > pages) throw new IllegalArgumentException("Authoring menu has " + pages + " pages.");
            for (var role : roles.subList((page - 1) * 16, Math.min(page * 16, roles.size()))) {
                boolean bound = runtime.service().state().sites().containsKey(role.id());
                String roleArgument = StringArgumentType.escapeIfRequired(role.id());
                String command = switch (role.kind()) {
                    case REGION -> "/chronicle author region " + (bound ? "replace " : "save ") + roleArgument;
                    case LOCATION -> "/chronicle author location " + (bound ? "replace " : "save ") + roleArgument + " here";
                    case BLOCK -> "/chronicle author block " + (bound ? "replace " : "bind ") + roleArgument + " target";
                };
                source.sendFeedback(() -> Text.literal(role.label() + " (" + role.id() + ") ")
                        .append(choice(bound ? "[Replace] " : "[Set] ", command, role.description()))
                        .append(bound ? choice("[Show]", "/chronicle author show " + roleArgument, "Inspect the saved site.") : Text.literal("unbound").formatted(Formatting.GRAY)), false);
            }
            if (page < pages) source.sendFeedback(() -> choice("[Next page]", "/chronicle author menu " + (page + 1), "More registered places."), false);
            return "Authoring menu page " + page + "/" + pages + "; registered roles=" + roles.size() + ".";
        });
    }
    private static CompletableFuture<Suggestions> suggestValues(Iterable<String> values, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (String value : values) {
            String escaped = StringArgumentType.escapeIfRequired(value);
            if (value.startsWith(prefix) || escaped.startsWith(prefix)) builder.suggest(escaped);
        }
        return builder.buildFuture();
    }
    private static Text choice(String label, String command, String help) {
        return Text.literal(label).styled(style -> style.withColor(Formatting.AQUA)
                .withClickEvent(new ClickEvent.SuggestCommand(command)).withHoverEvent(new HoverEvent.ShowText(Text.literal(help))));
    }
    private static void requireEditor(ServerCommandSource source, AuthoringRuntime runtime) {
        requireEditable(source);
        var player = player(source);
        runtime.selection(player.getUuid());
        if (source.getWorld() != player.getEntityWorld()) throw new IllegalStateException("Authoring edits must use the player's actual dimension.");
    }
    private static int edit(ServerCommandSource source, Operation operation) {
        return run(source, runtime -> { requireEditor(source, runtime); return operation.run(runtime); });
    }
    private static int run(ServerCommandSource source, Operation operation) {
        if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Authoring requires operator permission level 2.")); return 0; }
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
