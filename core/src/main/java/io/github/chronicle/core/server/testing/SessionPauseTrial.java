package io.github.chronicle.core.server.testing;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.SessionGameplay;
import io.github.chronicle.core.server.SessionBuilding;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Opt-in, loopback-only fixture commands for the paused-player native smoke.
 * They prepare or inspect state; the smoke client performs every attempted
 * placement, break and food use through normal play packets.
 */
public final class SessionPauseTrial {
    private static final BlockPos PLACE_BASE = new BlockPos(1, 64, 0);
    private static final BlockPos BREAK_TARGET = new BlockPos(2, 65, 0);
    private static final BlockPos BUILD_BASE = new BlockPos(4, 64, 0);
    private static final BlockPos BUILD_BREAK_TARGET = new BlockPos(5, 65, 0);
    private static final BlockPos REVOKED_BASE = new BlockPos(-2, 64, 0);
    private static final Map<String, Snapshot> SNAPSHOTS = new HashMap<>();

    private SessionPauseTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_PAUSE_NATIVE_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("chronicle_pause_trial").requires(source -> source.hasPermissionLevel(4))
                        .then(literal("setup").then(argument("player", StringArgumentType.word()).executes(context -> setup(context.getSource(), player(context)))))
                        .then(literal("snapshot").then(argument("player", StringArgumentType.word()).executes(context -> snapshot(context.getSource(), player(context)))))
                        .then(literal("paused_check").then(argument("player", StringArgumentType.word()).executes(context -> pausedCheck(context.getSource(), player(context)))))
                        .then(literal("paused_damage").then(argument("player", StringArgumentType.word()).executes(context -> pausedDamage(context.getSource(), player(context)))))
                        .then(literal("author_only").then(argument("player", StringArgumentType.word()).executes(context -> authorOnly(context.getSource(), player(context)))))
                        .then(literal("author_only_check").then(argument("player", StringArgumentType.word()).executes(context -> authorOnlyCheck(context.getSource(), player(context)))))
                        .then(literal("non_operator_enable").then(argument("player", StringArgumentType.word()).executes(context -> nonOperatorEnable(context.getSource(), player(context)))))
                        .then(literal("building_enable").then(argument("player", StringArgumentType.word()).executes(context -> buildingEnable(context.getSource(), player(context)))))
                        .then(literal("building_check").then(argument("player", StringArgumentType.word()).executes(context -> buildingCheck(context.getSource(), player(context)))))
                        .then(literal("building_disable").then(argument("player", StringArgumentType.word()).executes(context -> buildingDisable(context.getSource(), player(context)))))
                        .then(literal("building_disabled_check").then(argument("player", StringArgumentType.word()).executes(context -> buildingDisabledCheck(context.getSource(), player(context)))))
                        .then(literal("resume").then(argument("player", StringArgumentType.word()).executes(context -> resume(context.getSource(), player(context)))))
                        .then(literal("active_check").then(argument("player", StringArgumentType.word()).executes(context -> activeCheck(context.getSource(), player(context)))))
                        .then(literal("transition").then(argument("player",StringArgumentType.word()).then(argument("action",StringArgumentType.word()).executes(context -> transition(context.getSource(),player(context),StringArgumentType.getString(context,"action"))))))
        ));
    }

    private static ServerPlayerEntity player(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        ServerCommandSource source = context.getSource();
        requireFixture(source);
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(name);
        if (player == null) throw new IllegalArgumentException("Native pause player is not online: " + name);
        require(player.getEntityWorld().getRegistryKey().equals(World.OVERWORLD), "native pause player must be in the Overworld");
        return player;
    }

    private static int setup(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            var server = source.getServer();
            var world = player.getEntityWorld();
            for (int x=-6;x<=7;x++) for(int z=-3;z<=5;z++) floor(world,new BlockPos(x,64,z));
            if (ChronicleRuntime.service(server).state().phase().name().equals("INACTIVE")) {
                ChronicleRuntime.service(server).start(new Actor("native-pause", null), 1);
            }
            if (!ChronicleRuntime.service(server).state().phase().name().equals("PAUSED")) {
                ChronicleRuntime.service(server).pause(new Actor("native-pause", null));
            }
            server.getCommandManager().getDispatcher().execute("gamemode survival " + player.getName().getString(), source.withSilent());
            server.getCommandManager().getDispatcher().execute("tp " + player.getName().getString() + " 0.5 65 0.5 0 0", source.withSilent());
            player.getInventory().clear();
            player.getInventory().setStack(0, new ItemStack(Items.DIRT, 8));
            player.getInventory().setStack(1, new ItemStack(Items.BREAD, 3));
            player.getInventory().setStack(2, new ItemStack(Items.DIAMOND_PICKAXE));
            player.setHealth(10.0F);
            player.getHungerManager().setFoodLevel(10);
            player.getHungerManager().setSaturationLevel(0.0F);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 600, 0));
            floor(world, PLACE_BASE); floor(world, BREAK_TARGET.down()); floor(world, BUILD_BASE); floor(world, BUILD_BREAK_TARGET.down()); floor(world, REVOKED_BASE);
            world.setBlockState(PLACE_BASE.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(BREAK_TARGET, Blocks.STONE.getDefaultState());
            world.setBlockState(BUILD_BASE.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(BUILD_BREAK_TARGET, Blocks.STONE.getDefaultState());
            world.setBlockState(REVOKED_BASE.up(), Blocks.AIR.getDefaultState());
            SessionBuilding.stop(server);
            SNAPSHOTS.put(player.getUuidAsString(), snapshot(player));
            pass(source, "setup");
            return 1;
        } catch (Exception error) { return fail(source, "setup", error); }
    }

    private static int pausedCheck(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            Snapshot before = snapshotFor(player);
            require(SessionGameplay.frozen(player), "paused player is not frozen");
            require(!SessionGameplay.canBuild(player), "ordinary paused player may build");
            require(player.getEntityWorld().getBlockState(PLACE_BASE.up()).isAir(), "paused placement changed world");
            require(player.getEntityWorld().getBlockState(BREAK_TARGET).isOf(Blocks.STONE), "paused mining changed world");
            require(count(player, Items.DIRT) == before.dirt(), "paused placement consumed dirt");
            require(count(player, Items.BREAD) == before.bread(), "paused food use consumed bread");
            require(player.getHealth() == before.health(), "paused health changed");
            require(player.getHungerManager().getFoodLevel() == before.food(), "paused hunger changed: " + before.food() + " -> " + player.getHungerManager().getFoodLevel());
            require(player.getHungerManager().getSaturationLevel() == before.saturation(), "paused saturation changed");
            require(exhaustion(player) == before.exhaustion(), "paused exhaustion changed");
            require(effectDuration(player) == before.effectDuration(), "paused status duration ticked");
            pass(source, "paused_check"); return 1;
        } catch (Exception error) { return fail(source, "paused_check", error); }
    }

    private static int snapshot(ServerCommandSource source, ServerPlayerEntity player) {
        SNAPSHOTS.put(player.getUuidAsString(), snapshot(player));
        pass(source, "snapshot");
        return 1;
    }

    private static int pausedDamage(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            float health = player.getHealth();
            boolean accepted = player.damage((ServerWorld) player.getEntityWorld(), player.getDamageSources().generic(), 2.0F);
            require(!accepted && player.getHealth() == health, "paused direct damage was accepted");
            player.addExhaustion(4.0F);
            player.getHungerManager().addExhaustion(4.0F);
            require(exhaustion(player) == snapshotFor(player).exhaustion(), "paused direct exhaustion accumulated");
            require(player.getHungerManager().getFoodLevel() == snapshotFor(player).food(), "paused exhaustion changed hunger");
            pass(source, "paused_damage"); return 1;
        } catch (Exception error) { return fail(source, "paused_damage", error); }
    }

    private static int authorOnlyCheck(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            require(player.getEntityWorld().getBlockState(BUILD_BASE.up()).isAir(), "author-only paused placement changed world");
            require(!SessionBuilding.enabled(source.getServer()), "author-only interaction enabled global Building Mode");
            pass(source,"author_only_check");return 1;
        } catch(Exception error){return fail(source,"author_only_check",error);}
    }

    private static int authorOnly(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            var server = source.getServer();
            server.getCommandManager().getDispatcher().execute("op " + player.getName().getString(), source.withSilent());
            AuthoringRuntime.get(server).enable(player.getUuid());
            require(!SessionGameplay.canBuild(player), "author flag granted paused map-work without global Building Mode");
            player.getEntityWorld().setBlockState(BUILD_BASE.up(), Blocks.AIR.getDefaultState());
            pass(source, "author_only"); return 1;
        } catch (Exception error) { return fail(source, "author_only", error); }
    }

    private static int nonOperatorEnable(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            var server = source.getServer();
            server.getCommandManager().getDispatcher().execute("deop " + player.getName().getString(), source.withSilent());
            AuthoringRuntime.get(server).disable(player.getUuid());
            require(!SessionBuilding.enabled(server), "global Building Mode started before non-operator command");
            boolean rejected;
            try {
                rejected = server.getCommandManager().getDispatcher().execute("chronicles session building on", player.getCommandSource()) == 0;
            } catch (CommandSyntaxException expected) {
                rejected = true;
            }
            require(rejected, "non-operator Building Mode command unexpectedly succeeded");
            require(!SessionBuilding.enabled(server), "non-operator Building Mode command mutated global state");
            require(!SessionGameplay.canBuild(player), "non-operator can build without global Building Mode");
            pass(source, "non_operator_enable"); return 1;
        } catch (Exception error) { return fail(source, "non_operator_enable", error); }
    }

    private static int buildingEnable(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            var server = source.getServer();
            server.getCommandManager().getDispatcher().execute("chronicles session building on", source.withSilent());
            require(SessionBuilding.enabled(server), "operator command did not enable global Building Mode");
            require(!player.hasPermissionLevel(2), "native player is still an operator during global Building Mode proof");
            require(!AuthoringRuntime.isAuthor(player), "native player still has author flag during global Building Mode proof");
            require(SessionGameplay.canBuild(player), "ordinary survival player could not build during global Building Mode");
            player.getEntityWorld().setBlockState(BUILD_BASE.up(), Blocks.AIR.getDefaultState());
            player.getEntityWorld().setBlockState(BUILD_BREAK_TARGET, Blocks.STONE.getDefaultState());
            pass(source, "building_enable"); return 1;
        } catch (Exception error) { return fail(source, "building_enable", error); }
    }

    private static int buildingCheck(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            require(player.getEntityWorld().getBlockState(BUILD_BASE.up()).isOf(Blocks.DIRT), "global Building Mode placement did not apply");
            require(player.getEntityWorld().getBlockState(BUILD_BREAK_TARGET).isAir(), "global Building Mode mining did not apply");
            require(count(player, Items.BREAD) == snapshotFor(player).bread(), "global Building Mode food use consumed bread");
            require(player.getHealth()==snapshotFor(player).health(), "global Building Mode changed frozen health");
            require(player.getHungerManager().getFoodLevel()==snapshotFor(player).food(), "global Building Mode changed frozen hunger");
            require(player.getHungerManager().getSaturationLevel()==snapshotFor(player).saturation(), "global Building Mode changed frozen saturation");
            require(exhaustion(player)==snapshotFor(player).exhaustion(), "global Building Mode changed frozen exhaustion");
            require(effectDuration(player)==snapshotFor(player).effectDuration(), "global Building Mode ticked frozen status duration");
            pass(source,"building_check");return 1;
        } catch(Exception error){return fail(source,"building_check",error);}
    }

    private static int buildingDisable(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            source.getServer().getCommandManager().getDispatcher().execute("chronicles session building off", source.withSilent());
            require(!SessionBuilding.enabled(source.getServer()), "operator command did not disable global Building Mode");
            require(!SessionGameplay.canBuild(player), "Building Mode off retained paused map-work permission");
            player.getEntityWorld().setBlockState(REVOKED_BASE.up(), Blocks.AIR.getDefaultState());
            pass(source, "building_disable"); return 1;
        } catch (Exception error) { return fail(source, "building_disable", error); }
    }

    private static int buildingDisabledCheck(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            require(player.getEntityWorld().getBlockState(REVOKED_BASE.up()).isAir(), "Building Mode off still allowed placement");
            pass(source, "building_disabled_check"); return 1;
        } catch (Exception error) { return fail(source, "building_disabled_check", error); }
    }

    private static int resume(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            source.getServer().getCommandManager().getDispatcher().execute("chronicles session building on", source.withSilent());
            require(SessionBuilding.enabled(source.getServer()), "pre-resume global Building Mode did not enable");
            ChronicleRuntime.service(source.getServer()).resume(new Actor("native-pause", null));
            player.getEntityWorld().setBlockState(PLACE_BASE.up(), Blocks.AIR.getDefaultState());
            player.getEntityWorld().setBlockState(BREAK_TARGET, Blocks.STONE.getDefaultState());
            require(!SessionBuilding.enabled(source.getServer()), "resume did not clear global Building Mode");
            require(!SessionGameplay.frozen(player) && SessionGameplay.canBuild(player), "active player did not recover normal gameplay");
            pass(source, "resume"); return 1;
        } catch (Exception error) { return fail(source, "resume", error); }
    }

    private static int activeCheck(ServerCommandSource source, ServerPlayerEntity player) {
        try {
            require(player.getEntityWorld().getBlockState(PLACE_BASE.up()).isOf(Blocks.DIRT), "active placement was not applied");
            require(player.getEntityWorld().getBlockState(BREAK_TARGET).isAir(), "active mining was not applied");
            require(count(player, Items.BREAD) < snapshotFor(player).bread(), "active food use did not consume bread");
            pass(source, "active_check"); return 1;
        } catch (Exception error) { return fail(source, "active_check", error); }
    }

    private static int transition(ServerCommandSource source,ServerPlayerEntity player,String action) {
        try {
            var server=source.getServer();var service=ChronicleRuntime.service(server);var actor=new Actor("native-pause",null);
            switch(action){
                case "prepare_food" -> {player.clearActiveItem();player.getHungerManager().setFoodLevel(5);player.getHungerManager().setSaturationLevel(0);player.getInventory().setStack(1,new ItemStack(Items.BREAD,3));SessionGameplay.correctInventory(player);}
                case "pause_food" -> {require(player.isUsingItem(),"client never began active food use");service.pause(actor);SNAPSHOTS.put(player.getUuidAsString(),snapshot(player));}
                case "check_food" -> {require(!player.isUsingItem(),"paused in-flight use was not cleared");require(count(player,Items.BREAD)==snapshotFor(player).bread(),"in-flight paused food was consumed");require(player.getHungerManager().getFoodLevel()==snapshotFor(player).food(),"in-flight paused hunger changed");}
                case "hold" -> {require(service.state().phase().name().equals("PAUSED"),"hold trial must begin paused");server.getCommandManager().getDispatcher().execute("chronicles session building on",source.withSilent());require(SessionBuilding.enabled(server),"pre-hold global Building Mode did not enable");io.github.chronicle.core.server.RuntimeHolds.hold(server,"native-pause","Pause regression hold");require(SessionGameplay.frozen(player)&&!SessionGameplay.canBuild(player)&&!SessionBuilding.enabled(server),"paused runtime hold did not suspend building");io.github.chronicle.core.server.RuntimeHolds.release(server,"native-pause");require(service.state().phase().name().equals("PAUSED")&&SessionGameplay.frozen(player)&&SessionGameplay.canBuild(player)&&SessionBuilding.enabled(server),"hold release did not restore the same-phase Building Mode choice");server.getCommandManager().getDispatcher().execute("chronicles session building on",source.withSilent());require(SessionBuilding.enabled(server),"pre-resume global Building Mode did not re-enable");service.resume(actor);require(!SessionBuilding.enabled(server),"resume transition did not clear global Building Mode");io.github.chronicle.core.server.RuntimeHolds.hold(server,"native-pause","Pause regression active hold");require(SessionGameplay.frozen(player)&&!SessionGameplay.canBuild(player)&&!SessionBuilding.enabled(server),"ACTIVE runtime hold did not freeze gameplay");require(!player.interactionManager.tryBreakBlock(BREAK_TARGET.down()),"held direct mining was permitted");require(!player.damage(player.getEntityWorld(),player.getDamageSources().generic(),2),"held damage permitted");io.github.chronicle.core.server.RuntimeHolds.release(server,"native-pause");require(!SessionGameplay.frozen(player),"release did not resume gameplay");}
                case "end" -> {service.pause(actor);server.getCommandManager().getDispatcher().execute("chronicles session building on",source.withSilent());require(SessionBuilding.enabled(server),"pre-end global Building Mode did not enable");service.end(actor);require(SessionGameplay.frozen(player)&&!SessionGameplay.canBuild(player)&&!SessionBuilding.enabled(server),"ENDED phase did not freeze or clear Building Mode");require(!player.interactionManager.tryBreakBlock(BREAK_TARGET.down()),"ended direct mining permitted");}
                default -> throw new IllegalArgumentException("Unknown pause trial transition");
            }
            pass(source,action);return 1;
        }catch(Exception error){return fail(source,action,error);}
    }

    private static void floor(ServerWorld world, BlockPos pos) { world.setBlockState(pos, Blocks.STONE.getDefaultState()); }
    private static int count(ServerPlayerEntity player, net.minecraft.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(item)) total += stack.getCount();
        }
        return total;
    }
    private static int effectDuration(ServerPlayerEntity player) { var effect = player.getStatusEffect(StatusEffects.SLOWNESS); return effect == null ? -1 : effect.getDuration(); }
    private static float exhaustion(ServerPlayerEntity player) {
        var view=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY);
        player.getHungerManager().writeData(view);
        return view.getNbt().getFloat("foodExhaustionLevel",Float.NaN);
    }
    private static Snapshot snapshot(ServerPlayerEntity player) { return new Snapshot(count(player, Items.DIRT), count(player, Items.BREAD), player.getHealth(), player.getHungerManager().getFoodLevel(), effectDuration(player),player.getHungerManager().getSaturationLevel(),exhaustion(player)); }
    private static Snapshot snapshotFor(ServerPlayerEntity player) { Snapshot snapshot = SNAPSHOTS.get(player.getUuidAsString()); if (snapshot == null) throw new IllegalStateException("native pause fixture was not set up"); return snapshot; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void requireFixture(ServerCommandSource source) {
        var root = source.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root != null && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                        && root.getFileName().toString().matches("\\d{8}T\\d{12}Z") && source.getServer().isDedicated()
                        && !source.getServer().isOnlineMode() && source.getServer().getServerPort() == 25575
                        && source.getServer().getServerIp().equals("127.0.0.1"), "unsafe native fixture");
    }
    private static void pass(ServerCommandSource source, String verb) { source.sendFeedback(() -> Text.literal("Pause trial PASS " + verb), false); }
    private static int fail(ServerCommandSource source, String verb, Exception error) { ChronicleCore.LOGGER.error("Pause native trial failed: " + verb, error); source.sendError(Text.literal("Pause trial FAIL " + verb + ": " + error.getMessage())); return 0; }
    private record Snapshot(int dirt, int bread, float health, int food, int effectDuration, float saturation,float exhaustion) { }
}
