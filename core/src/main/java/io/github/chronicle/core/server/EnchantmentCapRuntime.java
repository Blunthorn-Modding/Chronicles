package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.enchanting.EnchantmentCaps;
import io.github.chronicle.core.enchanting.EnchantmentCapConfig;
import io.github.chronicle.core.enchanting.EnchantmentStacks;
import io.github.chronicle.core.enchanting.InventoryMigrationQueue;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.Identifier;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Server-owned policy. The binding never propagates to a client/render/IO thread. */
public final class EnchantmentCapRuntime {
    private static final String TOOL = "chronicle_core:enchantment_caps";
    private static final ThreadLocal<Runtime> CURRENT = new ThreadLocal<>();
    private static final Map<MinecraftServer, Runtime> SERVERS = new IdentityHashMap<>();
    private static final List<Integer> LEVELS = List.of(1,2,3,4,5,10,255);
    private static EnchantmentCaps defaults = EnchantmentCaps.DEFAULT;
    private static String defaultOwner;
    private static boolean started;
    private EnchantmentCapRuntime() { }

    /** A campaign recommends first-file defaults; existing operator configuration always wins. */
    public static synchronized void recommendDefaults(String owner, EnchantmentCaps policy) {
        if (started || defaultOwner != null) throw new IllegalStateException("Enchantment defaults already registered or server started");
        if (owner == null || owner.isBlank() || policy == null) throw new IllegalArgumentException("Default owner/policy required");
        defaultOwner = owner; defaults = policy;
    }

    public static void register() {
        var edit = GameSetupTools.Guard.EDIT;
        GameSetupTools.register(new GameSetupTools.Tool(TOOL, "Enchantment caps",
                "Server-wide maximum levels, including vanilla enchanting and loot. Reductions permanently change stored items; back up first.",
                List.of(new GameSetupTools.Verb("enable","Enable permanent caps",edit,true),
                        new GameSetupTools.Verb("disable","Disable future reductions",edit,true),
                        new GameSetupTools.Verb("lower","Lower default maximum",edit,true),
                        new GameSetupTools.Verb("raise","Raise default maximum",edit,true),
                        new GameSetupTools.Verb("reload","Reload cap JSON",edit,true),
                        new GameSetupTools.Verb("refresh","Refresh",GameSetupTools.Guard.READ_ONLY,false)),
                EnchantmentCapRuntime::status,EnchantmentCapRuntime::action));
        ServerLifecycleEvents.SERVER_STARTING.register(EnchantmentCapRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SERVERS.remove(server);
            if (CURRENT.get() != null && CURRENT.get().server == server) CURRENT.remove();
        });
        ServerTickEvents.END_SERVER_TICK.register(EnchantmentCapRuntime::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            queue(server,handler.player.getInventory(),handler.player); queue(server,handler.player.getEnderChestInventory(),handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> runtime(server).migration.removeOwner(handler.player));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,player,alive) -> {
            var server=player.getEntityWorld().getServer();runtime(server).migration.removeOwner(oldPlayer);
            queue(server,player.getInventory(),player);queue(server,player.getEnderChestInventory(),player);
        });
        ServerChunkEvents.CHUNK_LOAD.register(EnchantmentCapRuntime::loadedChunk);
        ServerChunkEvents.CHUNK_UNLOAD.register(EnchantmentCapRuntime::unloadedChunk);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world) -> runtime(world.getServer()).migration.removeOwner(entity));
        ServerEntityEvents.ENTITY_LOAD.register((entity,world) -> {
            if (entity instanceof Inventory inventory) queue(world.getServer(),inventory,entity);
            if (entity instanceof ItemEntity item && normalize(world.getServer(),item.getStack())) item.setStack(item.getStack());
            if (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity frame && normalize(world.getServer(),frame.getHeldItemStack())) frame.setHeldItemStack(frame.getHeldItemStack());
            if (entity instanceof LivingEntity living) for (var slot : EquipmentSlot.values()) normalize(world.getServer(),living.getEquippedStack(slot));
            if (entity instanceof PersistentProjectileEntity projectile) {
                normalize(world.getServer(),projectile.getItemStack());
                ItemStack weapon = projectile.getWeaponStack();
                if (weapon != null) normalize(world.getServer(),weapon);
            }
        });
    }

    public static void loadedChunk(ServerWorld world,WorldChunk chunk) {
            if (!runtime(world.getServer()).policy.enabled()) return;
            // Saving this loaded chunk also normalizes its stack snapshots on the server thread.
            // Dense valid worlds must not fail startup merely because the migration queue is full.
            chunk.markNeedsSaving(); int admitted=0;
            for (var block : chunk.getBlockEntities().values()) {
                if (++admitted>256) break;
                if (block instanceof Inventory inventory) queue(world.getServer(),inventory,chunk);
            }
    }
    public static void unloadedChunk(ServerWorld world,WorldChunk chunk) { runtime(world.getServer()).migration.removeOwner(chunk); }
    public static int pendingSlots(MinecraftServer server) { return runtime(server).migration.remainingSlots(); }
    public static int pendingInventories(MinecraftServer server) { return runtime(server).migration.size(); }

    private static void start(MinecraftServer server) {
        started = true;
        var config = new EnchantmentCapConfig(FabricLoader.getInstance().getConfigDir().resolve("chronicle/enchantment-caps.json"));
        try {
            var policy = config.loadOrCreate(defaults); validate(server,policy);
            Runtime runtime = new Runtime(server,config,policy);
            if (!server.isOnThread()) throw new IllegalStateException("Cap initialization requires server thread");
            SERVERS.put(server,runtime); CURRENT.set(runtime);
            ChronicleCore.LOGGER.info("Chronicle enchantment caps ready: enabled={}, default maximum={}; existing stored levels permanently reduce on load/access",policy.enabled(),policy.defaultMaxLevel());
        } catch (Exception exception) {
            // Do not start play with an accidental permissive fallback for an unreadable policy.
            throw new IllegalStateException("Enchantment caps unavailable; existing configuration was not replaced. Fix config/chronicle/enchantment-caps.json before restarting.",exception);
        }
    }

    private static void validate(MinecraftServer server,EnchantmentCaps policy) {
        var registry = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        for (String id : policy.overrides().keySet()) if (!registry.containsId(Identifier.of(id))) throw new IllegalArgumentException("Unknown enchantment cap ID: "+id);
    }
    private static Runtime bound() {
        Runtime runtime = CURRENT.get();
        return runtime != null && runtime.server.isOnThread() ? runtime : null;
    }
    public static void access(ItemStack stack) {
        Runtime runtime = bound();
        if (runtime != null) EnchantmentStacks.normalize(stack,runtime.policy);
    }
    public static boolean active() { Runtime runtime=bound(); return runtime!=null&&runtime.policy.enabled(); }
    public static int maximum(Enchantment enchantment,int nativeMaximum) {
        Runtime runtime = bound();
        if (runtime == null || !runtime.policy.enabled()) return nativeMaximum;
        Identifier id = runtime.server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getId(enchantment);
        return id == null ? nativeMaximum : runtime.policy.cap(id.toString(),nativeMaximum);
    }
    public static boolean normalize(MinecraftServer server,ItemStack stack) {
        if (!server.isOnThread()) throw new IllegalStateException("Cap mutation requires server thread");
        Runtime runtime = SERVERS.get(server);
        return runtime != null && EnchantmentStacks.normalize(stack,runtime.policy);
    }
    public static EnchantmentCaps policy(MinecraftServer server) { return runtime(server).policy; }
    private static Runtime runtime(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Cap operations require server thread");
        Runtime result = SERVERS.get(server);
        if (result == null) throw new IllegalStateException("Enchantment caps not initialized");
        return result;
    }
    private static void queue(MinecraftServer server,Inventory inventory,Object owner) {
        Runtime runtime = runtime(server);
        if (!runtime.policy.enabled()) return;
        if (inventory.size()==0) return;
        // CHUNK_LOAD runs before the chunk's FULL future completes. BlockEntity.markDirty()
        // requests that same chunk synchronously and deadlocks on queue overflow. The
        // callback already marks its owning WorldChunk directly for the save fallback.
        runtime.migration.offer(inventory,inventory.size(),owner);
    }
    private static void tick(MinecraftServer server) {
        Runtime runtime = runtime(server);
        int remaining = 64;
        while (remaining > 0 && runtime.migration.next()!=null) {
            var pending=runtime.migration.next();Inventory inventory=pending.inventory();
            if (inventory instanceof net.minecraft.block.entity.BlockEntity block&&block.isRemoved()
                    ||pending.owner() instanceof net.minecraft.entity.Entity entity&&entity.isRemoved()
                    ||pending.slot()>=inventory.size()) { runtime.migration.removeInventory(inventory);continue; }
            ItemStack stack=inventory.getStack(pending.slot());normalize(server,stack);remaining--;
            if (pending.remaining()==1) {
                // getStack implementations may access/normalize components before normalize() sees them.
                // Mark once per completed on-load pass even when the last explicit call was cached.
                inventory.markDirty();
            }
            runtime.migration.advance(pending);
        }
    }

    private static GameSetupTools.Status status(ServerCommandSource source) {
        Runtime runtime = runtime(source.getServer()); var policy = runtime.policy;
        var disabled = new java.util.HashMap<String,String>();
        if (policy.enabled()) disabled.put("enable","Caps are already enabled."); else disabled.put("disable","Caps are already disabled.");
        if (policy.defaultMaxLevel() == 1) disabled.put("lower","Minimum maximum level: I.");
        if (policy.defaultMaxLevel() == 255) disabled.put("raise","Maximum configured level: 255; native generation maxima still apply.");
        return new GameSetupTools.Status(runtime.revision,List.of(
                new Fact("Policy",policy.enabled()?"Enabled; permanent stored-level reductions.":"Disabled; previous reductions remain permanent."),
                new Fact("Default maximum",Integer.toString(policy.defaultMaxLevel())),
                new Fact("Overrides",policy.overrides().size()+" enchantments; edit config/chronicle/enchantment-caps.json, then Reload cap JSON."),
                new Fact("Existing items","Gear/books and nested items reduce on authoritative load/access. Offline players/unloaded chunks wait until loading; no forced loads."),
                new Fact("Irreversible","Stored reductions are permanent. Back up the stopped world before reducing caps. Raising/disabling never restores removed levels."),
                new Fact("Native mechanics","Tables/anvils/loot/commands use server maxima. Ingredient recipes and physical Forge power are separate."),
                new Fact("Loaded inventory queue",runtime.migration.size()+" inventories / "+runtime.migration.remainingSlots()+" pending slots; maximum8192 slots,64 each tick. Unloads discard pending references.")),disabled);
    }
    private static String action(ServerCommandSource source,String verb) throws Exception {
        Runtime runtime = runtime(source.getServer()); var old = runtime.policy; var next = old;
        switch (verb) {
            case "enable" -> next = new EnchantmentCaps(true,old.defaultMaxLevel(),old.overrides());
            case "disable" -> next = new EnchantmentCaps(false,old.defaultMaxLevel(),old.overrides());
            case "lower" -> next = new EnchantmentCaps(old.enabled(),LEVELS.stream().filter(n -> n < old.defaultMaxLevel()).reduce((a,b)->b).orElse(1),old.overrides());
            case "raise" -> next = new EnchantmentCaps(old.enabled(),LEVELS.stream().filter(n -> n > old.defaultMaxLevel()).findFirst().orElse(255),old.overrides());
            case "reload" -> {
                try {
                    var candidate = runtime.config.loadOrCreate(defaults); validate(source.getServer(),candidate);
                    next = candidate;
                } catch (Exception error) {
                    RuntimeHolds.hold(source.getServer(),TOOL,"Enchantment cap reload failed; fix the JSON and reload. The previous policy remains active.");
                    throw error;
                }
            }
            case "refresh" -> { return "Enchantment caps refreshed; removed levels cannot be restored by changing a cap."; }
            default -> throw new IllegalArgumentException("Unknown enchantment cap action");
        }
        validate(source.getServer(),next);
        if (!verb.equals("reload")) next = runtime.config.save(next,old);
        runtime.policy = next; runtime.revision++;
        RuntimeHolds.release(source.getServer(),TOOL);
        if (!next.enabled()) runtime.migration.clear();
        // Queue already-loaded state for dirty/resync handling; never obtain or force-load chunks.
        for (var player : source.getServer().getPlayerManager().getPlayerList()) { queue(source.getServer(),player.getInventory(),player); queue(source.getServer(),player.getEnderChestInventory(),player); }
        ChronicleCore.LOGGER.info("Enchantment caps {} by {}; enabled={}, default={}",verb,source.getName(),next.enabled(),next.defaultMaxLevel());
        return "Enchantment caps saved: "+(next.enabled()?"enabled":"disabled")+", default maximum "+next.defaultMaxLevel()+". Stored reductions are permanent; unloaded/offline items update when loaded.";
    }
    private static final class Runtime {
        final MinecraftServer server; final EnchantmentCapConfig config;
        EnchantmentCaps policy; long revision;
        final InventoryMigrationQueue<Inventory,Object> migration=new InventoryMigrationQueue<>();
        Runtime(MinecraftServer server,EnchantmentCapConfig config,EnchantmentCaps policy) { this.server=server;this.config=config;this.policy=policy; }
    }
}
