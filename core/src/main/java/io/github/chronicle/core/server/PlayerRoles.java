package io.github.chronicle.core.server;

import com.google.gson.Gson;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import io.github.chronicle.core.roles.*;
import io.github.chronicle.core.spawn.SpawnBinding;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.util.*;

/** Server-thread role configuration and private assignment authority. Grants have a separate coordinator. */
public final class PlayerRoles {
    private static final Map<MinecraftServer, State> WORLDS = new IdentityHashMap<>();
    private static final String HOLD = "chronicle_core:player_roles";
    private static final Gson JSON = new Gson();
    private PlayerRoles() { }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var config = new PlayerRoleConfig(FabricLoader.getInstance().getConfigDir().resolve("chronicle/player-roles.yaml"));
                var roles = config.loadOrCreate(ChronicleCore.playerRoles().roles());
                var assignments = new PlayerRoleAssignmentService(new PlayerRoleAssignmentStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/player-roles.json")));
                validateSeats(roles, assignments.state());
                var legacy = new HashSet<UUID>();
                var play = PlayRuntime.available(server); if (play != null) legacy.addAll(play.participants());
                var automatic = AutomaticPlayerSpawns.available(server);
                if (automatic != null) {legacy.addAll(automatic.arrivals().keySet());legacy.addAll(automatic.policies().keySet());}
                assignments.bootstrapExisting(legacy);
                WORLDS.put(server, new State(config, roles, assignments));
            } catch (IOException | RuntimeException failure) {
                ChronicleCore.LOGGER.error("Player roles unavailable; existing files retained", failure);
                RuntimeHolds.hold(server, HOLD, "Player roles unavailable. Correct player-roles.yaml or the world role ledger and reload roles.");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    public static boolean ready(MinecraftServer server) { return WORLDS.containsKey(server); }
    public static Map<String, PlayerRole> definitions(MinecraftServer server) { return state(server).roles; }
    public static PlayerRoleAssignments assignments(MinecraftServer server) { return state(server).assignments.state(); }
    public static Object revision(MinecraftServer server) { return ready(server) ? List.of(definitions(server),assignments(server)) : "unavailable"; }
    public static PlayerRole roleFor(MinecraftServer server, UUID player) { return ready(server) ? definitions(server).get(assignments(server).assigned().get(player)) : null; }
    public static void markStarted(MinecraftServer server, UUID player) throws IOException { state(server).assignments.markStarted(player); }
    public static void setPolicyDisabled(MinecraftServer server,UUID player,boolean arrival,boolean disabled) throws IOException { state(server).assignments.setPolicyDisabled(player,arrival,disabled); }
    public static long assigned(MinecraftServer server, String id) { return assignments(server).assigned().values().stream().filter(id::equals).count(); }

    public static void reload(ServerCommandSource source) throws IOException {
        var server=source.getServer();
        if(!source.hasPermissionLevel(2)||!AuthoringRuntime.editable(server)||source.getPlayer()!=null&&!AuthoringRuntime.isAuthor(source.getPlayer())||RuntimeHolds.heldByOther(server,HOLD))
            throw new IllegalStateException("Reload roles in non-active operator Editing, without another operation hold.");
        var config=new PlayerRoleConfig(FabricLoader.getInstance().getConfigDir().resolve("chronicle/player-roles.yaml"));
        var roles=config.loadOrCreate(ChronicleCore.playerRoles().roles());
        var prior=WORLDS.get(server);
        var service=prior==null?new PlayerRoleAssignmentService(new PlayerRoleAssignmentStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/player-roles.json"))):prior.assignments;
        validateSeats(roles,service.state());
        var legacy=new HashSet<UUID>();var play=PlayRuntime.available(server);if(play!=null)legacy.addAll(play.participants());
        var automatic=AutomaticPlayerSpawns.available(server);if(automatic!=null){legacy.addAll(automatic.arrivals().keySet());legacy.addAll(automatic.policies().keySet());}
        service.bootstrapExisting(legacy);WORLDS.put(server,new State(config,roles,service));RuntimeHolds.release(server,HOLD);
    }
    public static PlayerRole create(ServerCommandSource source, PlayerRole role) throws IOException {
        requireEditor(source); validateContent(source.getServer(),role);
        var st = state(source.getServer()); st.roles = st.config.create(role); return role;
    }
    public static void save(ServerCommandSource source, PlayerRole role, PlayerRole expected) throws IOException {
        requireEditor(source); validateContent(source.getServer(),role);
        if (expected == null || !expected.equals(definitions(source.getServer()).get(role.id()))) throw new IllegalStateException("Role changed; reopen it before saving.");
        if (assigned(source.getServer(),role.id()) > role.capacity()) throw new IllegalStateException("Cannot lower the limit below assigned accounts, including offline accounts.");
        var st = state(source.getServer()); st.roles = st.config.replace(role, expected);
    }
    public static void delete(ServerCommandSource source, String id, PlayerRole expected) throws IOException {
        requireEditor(source);
        if (assigned(source.getServer(),id) != 0) throw new IllegalStateException("An assigned role cannot be deleted; clear or reassign its accounts first.");
        var st = state(source.getServer()); st.roles = st.config.delete(id,expected);
    }
    public static void choose(ServerPlayerEntity player, String id) throws IOException {
        var server = player.getEntityWorld().getServer(); requireUnheld(server);
        Departures.requireParticipation(server,player.getUuid());
        if (assignments(server).started().contains(player.getUuid())) throw new IllegalStateException("You have already entered play; ask the GM to reassign your role.");
        var role = definitions(server).get(id);
        if (role == null || !role.selectable()) throw new IllegalArgumentException("Choose an available player role.");
        state(server).assignments.assign(player.getUuid(),id,definitions(server));
    }
    public static void assign(ServerCommandSource source, UUID player, String id) throws IOException {
        if (!source.hasPermissionLevel(2) || source.getPlayer() == null) throw new IllegalStateException("An operator must assign roles.");
        requireUnheld(source.getServer());
        if (!known(source.getServer(),player)) throw new IllegalArgumentException("Choose a known player in this campaign.");
        if (assignmentRequiresParticipation(id)) Departures.requireParticipation(source.getServer(),player);
        if (id.isBlank()||id.equals("-")) state(source.getServer()).assignments.unassign(player);
        else state(source.getServer()).assignments.assign(player,id,definitions(source.getServer()));
    }
    public static RoleAssignmentForm form(ServerPlayerEntity viewer, UUID target, String pending) {
        var server=viewer.getEntityWorld().getServer();var st=assignments(server);
        boolean gm=viewer.getCommandSource().hasPermissionLevel(2),own=target.equals(viewer.getUuid());
        if(!gm&&!own)throw new IllegalStateException("Player-role assignments are private.");
        boolean started=st.started().contains(target),unheld=RuntimeHolds.reason(server)==null;
        boolean participation=Departures.ready(server)&&!Departures.concluded(server)&&!Departures.departed(server,target);
        String id=st.assigned().getOrDefault(target,"");var current=definitions(server).get(id);
        var choices=new ArrayList<RoleChoice>();
        if(gm)choices.add(new RoleChoice("-","No role","Clear this assignment; starting history remains.",0,512,unheld));
        for(var r:definitions(server).values())if(gm||r.selectable()){
            int count=(int)assigned(server,r.id());
            choices.add(new RoleChoice(r.id(),r.name(),r.description(),count,r.capacity(),unheld&&participation&&(count<r.capacity()||r.id().equals(id))));
        }
        var candidate=definitions(server).get(pending);
        return new RoleAssignmentForm(target.toString(),id,pending.equals("-")?"No role":candidate!=null?candidate.name():current==null?"Unassigned":current.name(),own&&!started&&unheld,gm&&unheld,started,choices,pending);
    }
    public static RoleForm editorForm(MinecraftServer server,PlayerRole role,boolean creating) {
        var points=AuthoringRuntime.get(server).service().state().sites().values().stream().filter(AuthoringSite.Location.class::isInstance)
                .map(p->new Entry(p.id(),p.id(),"Saved point","POINT",p.id(),p.revision())).toList();
        var books=BookRuntime.books(server).values().stream().map(b->new Entry(b.id(),b.title(),b.description(),"BOOK",b.id(),0)).toList();
        return new RoleForm(role,points,books,creating);
    }
    public static PlayerRole decode(String value) { return io.github.chronicle.core.menu.PlayerRoleDraftJson.decode(value); }
    public static void validateContent(MinecraftServer server,PlayerRole role) {
        if(role.starting()!=null&&!role.starting().purpose().equals("chronicle_core:role_start")||role.respawn()!=null&&!role.respawn().purpose().equals("chronicle_core:role_respawn"))throw new IllegalArgumentException("Role Point purposes do not match their starting/respawn use.");
        validatePoint(server,role.starting());validatePoint(server,role.respawn());
        for(var id:role.books())if(!BookRuntime.books(server).containsKey(id))throw new IllegalArgumentException("Book is unavailable: "+id);
        if(role.items().size()+role.books().size()>36)throw new IllegalArgumentException("Starting supplies and books must fit in 36 inventory slots.");
        int bytes=0;
        for(var item:role.items()){
            bytes+=item.stackJson().length();if(bytes>65536)throw new IllegalArgumentException("Starting item data exceeds the menu limit.");
            var stack=ChestAuthoring.decode(server,new InventorySnapshot(1,List.of(item.stackJson()))).getFirst();
            if(stack.isEmpty()||stack.getCount()>stack.getMaxCount())throw new IllegalArgumentException("Choose a non-empty item stack within its stack limit.");
            if(stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)!=null&&stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA).copyNbt().contains("chronicle_book"))throw new IllegalArgumentException("Managed Chronicle items must use a named entitlement, not copied item data.");
        }
    }
    private static void validatePoint(MinecraftServer server,SpawnBinding binding) {
        if(binding==null)return;var site=AuthoringRuntime.get(server).service().state().sites().get(binding.locationId());
        if(!(site instanceof AuthoringSite.Location)||site.revision()!=binding.locationRevision())throw new IllegalArgumentException("Point is missing or changed. Choose it again.");
    }
    private static boolean known(MinecraftServer server,UUID id) {
        var play=PlayRuntime.available(server);var automatic=AutomaticPlayerSpawns.available(server);return automatic!=null&&(automatic.policies().containsKey(id)||automatic.arrivals().containsKey(id))||server.getPlayerManager().getPlayer(id)!=null||assignments(server).assigned().containsKey(id)||assignments(server).started().contains(id)||play!=null&&play.participants().contains(id);
    }
    private static void validateSeats(Map<String,PlayerRole> roles,PlayerRoleAssignments assignments) {
        var counts=new HashMap<String,Integer>();assignments.assigned().values().forEach(id->counts.merge(id,1,Integer::sum));
        for(var entry:counts.entrySet()){
            var role=roles.get(entry.getKey());if(role==null)throw new IllegalArgumentException("Assigned role is missing: "+entry.getKey());
            if(entry.getValue()>role.capacity())throw new IllegalArgumentException("Role limit is below its assignments: "+entry.getKey());
        }
    }
    private static void requireUnheld(MinecraftServer server){if(RuntimeHolds.reason(server)!=null)throw new IllegalStateException("Resolve the active Chronicle operation before changing roles.");}
    static boolean assignmentRequiresParticipation(String id){return id!=null&&!id.isBlank()&&!id.equals("-");}
    private static void requireEditor(ServerCommandSource source){if(!source.hasPermissionLevel(2)||source.getPlayer()==null||!AuthoringRuntime.isAuthor(source.getPlayer())||!AuthoringRuntime.editable(source.getServer()))throw new IllegalStateException("Role definitions require non-active operator Editing.");requireUnheld(source.getServer());}
    private static State state(MinecraftServer server){if(!server.isOnThread())throw new IllegalStateException("Player roles require the server thread.");var result=WORLDS.get(server);if(result==null)throw new IllegalStateException("Player roles unavailable; inspect the server log and role files.");return result;}
    private static final class State {
        PlayerRoleConfig config;Map<String,PlayerRole> roles;final PlayerRoleAssignmentService assignments;
        State(PlayerRoleConfig config,Map<String,PlayerRole> roles,PlayerRoleAssignmentService assignments){this.config=config;this.roles=roles;this.assignments=assignments;}
    }
}
