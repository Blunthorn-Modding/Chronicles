package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import net.minecraft.server.command.ServerCommandSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Bounded campaign-provided controls rendered under Core's Game setup page.
 * Core owns registration, UI transport and guard enforcement; tools own their state and effects.
 */
public final class GameSetupTools {
    public static final int MAX_TOOLS = 16;
    public static final int MAX_VERBS = 12;
    private static final Map<String, Tool> TOOLS = new LinkedHashMap<>();
    private static final java.util.Set<String> PRESENTED_WITHIN = new java.util.HashSet<>();
    private static boolean frozen;
    private static boolean finishing;
    private static final java.util.List<Runnable> FINALIZERS=new java.util.ArrayList<>();

    private GameSetupTools() { }

    public static synchronized void register(Tool tool) {
        Objects.requireNonNull(tool, "tool is required");
        if (frozen) throw new IllegalStateException("Game setup tools are frozen after server startup.");
        if (TOOLS.containsKey(tool.id())) throw new IllegalArgumentException("Duplicate game setup tool: " + tool.id());
        if (TOOLS.size() >= MAX_TOOLS) throw new IllegalStateException("Game setup tool limit of " + MAX_TOOLS + " reached.");
        TOOLS.put(tool.id(), tool);
    }

    /** Compose tools only after every mod initializer has registered its own tools. */
    public static synchronized void afterRegistrations(Runnable action){
        if(frozen||finishing||FINALIZERS.size()>=MAX_TOOLS)throw new IllegalStateException("Game setup registration is complete or full.");
        FINALIZERS.add(Objects.requireNonNull(action));
    }
    public static synchronized void freeze() {
        if(frozen)return;if(finishing)throw new IllegalStateException("Recursive game setup registration.");
        finishing=true;try{for(var action:List.copyOf(FINALIZERS))action.run();FINALIZERS.clear();frozen=true;}finally{finishing=false;}
    }
    public static synchronized List<Tool> tools() { return List.copyOf(TOOLS.values()); }
    /** Presentation only: original IDs, commands, guards and recovery providers remain registered. */
    public static synchronized List<Tool> menuTools(){return TOOLS.values().stream().filter(tool->!PRESENTED_WITHIN.contains(tool.id())).toList();}
    public static synchronized void presentWithin(String parent,List<String> children){
        if(frozen)throw new IllegalStateException("Game setup presentation is frozen after server startup.");
        tool(parent);
        for(String child:children){tool(child);if(child.equals(parent)||PRESENTED_WITHIN.contains(parent))throw new IllegalArgumentException("Invalid consolidated game setup presentation");}
        PRESENTED_WITHIN.addAll(children);
    }
    public static synchronized Optional<Tool> get(String id) { return Optional.ofNullable(TOOLS.get(id)); }

    public static Status status(ServerCommandSource source, String toolId) {
        requireServerThread(source);
        return Objects.requireNonNull(tool(toolId).status().apply(source), "tool status provider returned null");
    }

    /** Executes only a registered finite verb and rechecks its Core-owned guard. */
    public static String execute(ServerCommandSource source, String toolId, String verbId) throws Exception {
        requireServerThread(source);
        Tool tool = tool(toolId);
        Verb verb = tool.verb(verbId);
        String denied = guardReason(source, verb.guard());
        if (denied != null) throw new IllegalStateException(denied);
        Status status = Objects.requireNonNull(tool.status().apply(source), "tool status provider returned null");
        String unavailable = status.disabledReasons().get(verbId);
        if (unavailable != null && !unavailable.isBlank()) throw new IllegalStateException(unavailable);
        return Objects.requireNonNullElse(tool.executor().execute(source, verbId), "Game setup tool action completed.");
    }

    /** Null means the requested guard currently permits execution. */
    public static String guardReason(ServerCommandSource source, Guard guard) {
        requireServerThread(source);
        if (!source.hasPermissionLevel(2)) return "Game setup tools require operator permission level 2.";
        return switch (guard) {
            case READ_ONLY, OPERATOR -> null;
            case EDIT -> {
                if (source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer()) || !AuthoringRuntime.editable(source.getServer())) {
                    yield "This tool requires operator editing mode while Chronicle play is non-active.";
                }
                yield null;
            }
            case LIVE -> {
                if (ChronicleRuntime.service(source.getServer()).state().phase() != SessionPhase.ACTIVE) yield "This tool requires active Chronicle play.";
                if (RuntimeHolds.reason(source.getServer()) != null) yield "Resolve the active Chronicle operation hold first.";
                yield null;
            }
        };
    }

    private static Tool tool(String id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("Unknown game setup tool: " + id));
    }

    private static void requireServerThread(ServerCommandSource source) {
        Objects.requireNonNull(source, "source is required");
        if (!source.getServer().isOnThread()) throw new IllegalStateException("Game setup tools require the server thread.");
    }

    public enum Guard { READ_ONLY, EDIT, OPERATOR, LIVE }

    public record Verb(String id, String label, Guard guard, boolean confirm) {
        public Verb {
            GameSetupTools.id(id, 27, "verb ID");
            GameSetupTools.text(label, 96, "verb label");
            Objects.requireNonNull(guard, "verb guard is required");
        }
    }

    public record Status(long controlRevision, List<Fact> facts, Map<String, String> disabledReasons) {
        public Status {
            if (controlRevision < 0) throw new IllegalArgumentException("tool control revision cannot be negative");
            facts = List.copyOf(Objects.requireNonNull(facts, "tool facts are required"));
            if (facts.size() > 32) throw new IllegalArgumentException("tool facts exceed 32 entries");
            disabledReasons = Map.copyOf(Objects.requireNonNull(disabledReasons, "tool disabled reasons are required"));
            for (var reason : disabledReasons.entrySet()) {
                GameSetupTools.id(reason.getKey(), 27, "disabled verb ID");
                GameSetupTools.text(reason.getValue(), 512, "disabled reason");
            }
        }
    }

    public record Tool(String id, String label, String description, List<Verb> verbs,
                       Function<ServerCommandSource, Status> status, Executor executor) {
        public Tool {
            GameSetupTools.id(id, 128, "tool ID");
            GameSetupTools.text(label, 160, "tool label");
            GameSetupTools.text(description, 2048, "tool description");
            verbs = List.copyOf(Objects.requireNonNull(verbs, "tool verbs are required"));
            if (verbs.size() > MAX_VERBS) throw new IllegalArgumentException("tool verbs exceed " + MAX_VERBS);
            Map<String, Verb> distinct = new LinkedHashMap<>();
            for (Verb verb : verbs) {
                Objects.requireNonNull(verb, "tool verb is required");
                if (distinct.putIfAbsent(verb.id(), verb) != null) throw new IllegalArgumentException("duplicate tool verb: " + verb.id());
            }
            Objects.requireNonNull(status, "tool status provider is required");
            Objects.requireNonNull(executor, "tool executor is required");
        }

        private Verb verb(String id) {
            for (Verb verb : verbs) if (verb.id().equals(id)) return verb;
            throw new IllegalArgumentException("Unknown game setup tool action: " + id);
        }
    }

    @FunctionalInterface
    public interface Executor { String execute(ServerCommandSource source, String verbId) throws Exception; }

    private static void id(String value, int max, String field) {
        if (value == null || value.length() > max || !value.matches("[a-z0-9][a-z0-9_.:-]*")) {
            throw new IllegalArgumentException(field + " must be a bounded lowercase identifier");
        }
    }

    private static void text(String value, int max, String field) {
        if (value == null || value.length() > max || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " exceeds bounds");
        }
    }
}
