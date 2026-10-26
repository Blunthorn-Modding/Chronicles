package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.core.server.PlayerRoles;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

/** Explicitly opt-in, disposable-fixture checks for the CF19 role runtime. */
public final class PlayerRolesTrial {
    private static final String ROLE = "quiet_below:island_resident";
    private PlayerRolesTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_ROLES_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("chronicle_roles_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb : List.of("prepare", "full", "uncertain", "verify_uncertain", "release", "verify", "repeat", "clear", "death", "verify_death", "manual_respawn", "verify_manual", "off_respawn", "verify_off", "restart"))
                root.then(literal(verb).executes(context -> run(context.getSource(), verb)));
            dispatcher.register(root);
        });
    }

    private static int run(ServerCommandSource source, String verb) {
        try {
            fixture(source);ServerPlayerEntity player = source.getPlayerOrThrow();
            switch (verb) {
                case "prepare" -> prepare(source, player);
                case "full" -> {require(!PlayerRoles.assignments(source.getServer()).started().contains(player.getUuid()),"full inventory did not defer first entry");require(count(player,Items.BREAD)==0,"kit issued into full inventory");}
                case "uncertain" -> {clear(player);io.github.chronicle.core.server.AutomaticPlayerSpawns.service(source.getServer()).claim(player.getUuid(),"test:prior");}
                case "verify_uncertain" -> {require(!PlayerRoles.assignments(source.getServer()).started().contains(player.getUuid()),"unresolved prior movement claim was bypassed");require(count(player,Items.BREAD)==0&&!io.github.chronicle.core.server.BookGifts.status(source.getServer(),"test:role_intro").found(),"uncertain movement granted supplies or discovered the book");}
                case "release" -> {var campaign=ChronicleRuntime.service(source.getServer());campaign.pause(new Actor("roles-trial",null));io.github.chronicle.core.server.AuthoringRuntime.get(source.getServer()).enable(player.getUuid());io.github.chronicle.core.server.AutomaticPlayerSpawns.reset(source,player.getUuid());io.github.chronicle.core.server.AuthoringRuntime.get(source.getServer()).disable(player.getUuid());campaign.resume(new Actor("roles-trial",null));clear(player);}
                case "verify" -> verify(source, player);
                case "repeat" -> repeat(source, player);
                case "clear" -> { PlayerRoles.assign(source, player.getUuid(), ""); require(!PlayerRoles.assignments(source.getServer()).assigned().containsKey(player.getUuid()), "assignment did not clear"); }
                case "death" -> source.getServer().getCommandManager().getDispatcher().execute("kill @s",source);
                case "verify_death" -> {emptyAfterDeath(source,player);require(Math.abs(player.getX()-9.5)<0.1,"role respawn Point was not applied");}
                case "manual_respawn" -> {editRespawn(source,player,false);source.getServer().getCommandManager().getDispatcher().execute("kill @s",source);}
                case "verify_manual" -> {emptyAfterDeath(source,player);require(Math.abs(player.getX()-2.5)<0.1,"manual respawn override did not take priority over role Point");}
                case "off_respawn" -> {editRespawn(source,player,true);source.getServer().getCommandManager().getDispatcher().execute("spawnpoint @s 0 64 2",source);source.getServer().getCommandManager().getDispatcher().execute("kill @s",source);}
                case "verify_off" -> {emptyAfterDeath(source,player);require(player.getX()<1.5,"explicit Off did not retain vanilla respawn");}
                case "restart" -> {emptyAfterDeath(source,player);require(player.getX()<1.5&&PlayerRoles.assignments(source.getServer()).respawnDisabled().contains(player.getUuid()),"explicit Off or vanilla location was lost on restart");require(PlayerRoles.assignments(source.getServer()).started().contains(player.getUuid()), "first-entry receipt did not survive restart");}
                default -> throw new IllegalArgumentException("unknown role trial action");
            }
            source.sendFeedback(() -> Text.literal("Roles trial PASS " + verb), false); return 1;
        } catch (Exception error) { source.sendError(Text.literal("Roles trial FAIL " + verb + ": " + error.getMessage())); return 0; }
    }

    private static void prepare(ServerCommandSource source, ServerPlayerEntity player) throws Exception {
        require(PlayerRoles.ready(source.getServer()), "roles are unavailable");
        require(PlayerRoles.definitions(source.getServer()).containsKey(ROLE), "Quiet Below default YAML was not materialized");
        var world=source.getWorld();
        for(int x=-4;x<=12;x++)for(int z=-4;z<=8;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,63,z),net.minecraft.block.Blocks.STONE.getDefaultState(),3);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);player.teleport(world,2.5,64,2.5,java.util.Set.of(),0,0,false);player.setVelocity(0,0,0);player.onLanding();
        var point=(io.github.chronicle.core.authoring.AuthoringSite.Location)io.github.chronicle.core.server.AuthoringRuntime.get(source.getServer()).service().put(new io.github.chronicle.core.authoring.AuthoringSite.Location("test:role_point","minecraft:overworld",1,5.5,64,2.5,0,0),false);
        var respawn=(io.github.chronicle.core.authoring.AuthoringSite.Location)io.github.chronicle.core.server.AuthoringRuntime.get(source.getServer()).service().put(new io.github.chronicle.core.authoring.AuthoringSite.Location("test:role_respawn","minecraft:overworld",1,9.5,64,2.5,0,0),false);
        var prior=PlayerRoles.definitions(source.getServer()).get(ROLE);
        PlayerRoles.save(source,new io.github.chronicle.core.roles.PlayerRole(prior.id(),prior.name(),prior.description(),true,prior.capacity(),new io.github.chronicle.core.spawn.SpawnBinding("chronicle_core:role_start",point.id(),point.revision()),new io.github.chronicle.core.spawn.SpawnBinding("chronicle_core:role_respawn",respawn.id(),respawn.revision()),prior.items(),List.of("test:role_intro")),prior);
        fill(player); PlayerRoles.assign(source, player.getUuid(), ROLE);
        PlayRuntime.setSelfParticipant(player, true);
        ChronicleRuntime.service(source.getServer()).start(new Actor("roles-trial", null), null);
        require(PlayerRoles.roleFor(source.getServer(), player.getUuid()) != null, "assignment did not resolve");
    }
    private static void fill(ServerPlayerEntity player) { for (int slot = 0; slot < 36; slot++) player.getInventory().setStack(slot, new ItemStack(Items.COBBLESTONE, 64)); player.currentScreenHandler.sendContentUpdates(); }
    private static void clear(ServerPlayerEntity player) { for (int slot = 0; slot < 36; slot++) player.getInventory().setStack(slot, ItemStack.EMPTY); player.currentScreenHandler.sendContentUpdates(); }
    private static void verify(ServerCommandSource source, ServerPlayerEntity player) {
        require(PlayerRoles.assignments(source.getServer()).started().contains(player.getUuid()), "first entry is still pending (release inventory room and wait)");
        require(count(player, Items.BREAD) == 4 && count(player, Items.TORCH) == 8, "starter kit was not granted exactly once");
        require(count(player,Items.WRITTEN_BOOK)==1,"personal lore book was not granted exactly once");
        var found=io.github.chronicle.core.server.BookGifts.status(source.getServer(),"test:role_intro");require(found.found()&&found.finderUuid().equals(player.getUuidAsString()),"first role book receipt did not record discovery");
        require(Math.abs(player.getX()-5.5)<0.1&&Math.abs(player.getZ()-2.5)<0.1,"role Point movement did not occur");
    }
    private static void repeat(ServerCommandSource source, ServerPlayerEntity player) {
        int bread = count(player, Items.BREAD), torches = count(player, Items.TORCH);
        require(PlayerRoles.assignments(source.getServer()).started().contains(player.getUuid()), "first-entry receipt missing");
        require(bread == 4 && torches == 8, "completed entry replayed supplies");
    }
    private static int count(ServerPlayerEntity player, net.minecraft.item.Item item) { int result = 0; for (int slot = 0; slot < 36; slot++) if (player.getInventory().getStack(slot).isOf(item)) result += player.getInventory().getStack(slot).getCount(); return result; }
    private static void emptyAfterDeath(ServerCommandSource source,ServerPlayerEntity player){
        require(PlayerRoles.assignments(source.getServer()).started().contains(player.getUuid()),"start receipt lost after death/restart");
        require(count(player,Items.BREAD)==0&&count(player,Items.TORCH)==0&&count(player,Items.WRITTEN_BOOK)==0,"death/restart replayed starting supplies");
        require(io.github.chronicle.core.server.BookGifts.status(source.getServer(),"test:role_intro").found(),"death/restart lost original book discovery");
    }
    private static void editRespawn(ServerCommandSource source,ServerPlayerEntity player,boolean off) throws Exception{
        var server=source.getServer();var campaign=ChronicleRuntime.service(server);campaign.pause(new Actor("roles-trial",null));
        var author=io.github.chronicle.core.server.AuthoringRuntime.get(server);author.enable(player.getUuid());
        if(off)io.github.chronicle.core.server.AutomaticPlayerSpawns.disable(source,player.getUuid(),false);
        else{
            var point=(io.github.chronicle.core.authoring.AuthoringSite.Location)author.service().put(new io.github.chronicle.core.authoring.AuthoringSite.Location("test:manual_point","minecraft:overworld",1,2.5,64,2.5,0,0),false);
            io.github.chronicle.core.server.SpawnAuthoring.service(server).bind(author.service().state(),"test:manual_respawn",point.id(),false);
            io.github.chronicle.core.server.AutomaticPlayerSpawns.enable(source,player.getUuid(),"test:manual_respawn",false);
        }
        author.disable(player.getUuid());campaign.resume(new Actor("roles-trial",null));
    }
    private static void fixture(ServerCommandSource source) {
        var server=source.getServer();var root=server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&java.nio.file.Files.isRegularFile(root.resolve(".player-roles-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp())&&source.getWorld().getRegistryKey()==net.minecraft.world.World.OVERWORLD,"unsafe Player Roles fixture");
    }
    private static void require(boolean condition, String text) { if (!condition) throw new IllegalStateException(text); }
}
