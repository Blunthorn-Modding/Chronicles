package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.enchanting.*;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.network.ItemEnchantingPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;

/** Reusable ingredient enchanting; campaign stations supply an authoritative live gate. */
public final class ItemEnchantingRuntime {
    private static final String TOOL="chronicle_core:item_enchanting";
    private static final IngredientEnchantmentCatalog CATALOG=new IngredientEnchantmentCatalog();
    private static final Map<MinecraftServer,Runtime> SERVERS=new IdentityHashMap<>();
    private static final long OFFER_NANOS=30_000_000_000L;
    public static final int MAX_OFFERS=256;
    private ItemEnchantingRuntime() { }
    public static IngredientEnchantmentCatalog catalog(){return CATALOG;}
    /** Null means ready. Implementations must use loaded-only reads and recheck the physical station. */
    @FunctionalInterface public interface Gate {
        String unavailable(ServerPlayerEntity player);
        /** Cosmetic feedback only, after the inventory transaction has committed. */
        default void completed(ServerPlayerEntity player) { }
    }

    public static void register() {
        ItemEnchantingPayloads.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server->CATALOG.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(ItemEnchantingRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
        io.github.chronicle.core.server.ServerDisconnects.register((h,s)->{
            var r=SERVERS.get(s);if(r!=null){var offer=r.offers.get(h.player.getUuid());
                if(offer!=null&&OfferIdentity.sameConnection(offer.connection,h))r.offers.remove(h.player.getUuid(),offer);}
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var r=SERVERS.get(server);if(r!=null&&server.getTicks()%20==0){long now=System.nanoTime();r.offers.values().removeIf(o->now-o.created>=OFFER_NANOS);}
        });
        ServerPlayNetworking.registerGlobalReceiver(ItemEnchantingPayloads.Action.ID,(payload,context)->context.server().execute(()->act(context.player(),payload)));
        GameSetupTools.register(new GameSetupTools.Tool(TOOL,"Ingredient enchanting",
                "Recipes for item-based workshops. Campaigns supply physical stations and power; server JSON controls ingredients, levels and counts.",
                List.of(new GameSetupTools.Verb("reload","Reload recipes JSON",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("refresh","Refresh",GameSetupTools.Guard.READ_ONLY,false)),
                source->{var r=runtime(source.getServer());return new GameSetupTools.Status(r.revision,List.of(
                        new Fact("Configuration","config/chronicle/ingredient-enchantments.json; complete operator override of campaign defaults."),
                        new Fact("Recipes",r.recipes.size()+" configured, "+r.recipes.stream().filter(IngredientEnchantmentRecipe::enabled).count()+" enabled."),
                        new Fact("Status",r.failure==null?"Ready; native compatibility and universal caps apply.":r.failure),
                        new Fact("Workflow","Target in main hand, ingredient in offhand; open a placed workshop and confirm the exact trade. No XP charge.")),Map.of());},
                (source,verb)->{var r=runtime(source.getServer());if(verb.equals("reload")){reload(source.getServer(),r);return "Ingredient enchanting recipes reloaded; open workshop offers were invalidated.";}return "Ingredient enchanting refreshed.";}));
    }
    private static void start(MinecraftServer server) {
        var r=new Runtime(new IngredientEnchantmentConfig(FabricLoader.getInstance().getConfigDir().resolve("chronicle/ingredient-enchantments.json")));
        SERVERS.put(server,r);
        try{reload(server,r);}catch(Exception error){ChronicleCore.LOGGER.error("Ingredient enchanting unavailable; existing config preserved",error);}
    }
    private static void reload(MinecraftServer server,Runtime r)throws Exception {
        r.offers.clear();
        try{
            var next=r.config.loadOrCreate(List.copyOf(CATALOG.recipes().values()));validate(server,next);
            r.recipes=List.copyOf(next);r.failure=null;r.revision++;
            ChronicleCore.LOGGER.info("Ingredient enchanting ready: {} configured recipes",next.size());
        }catch(Exception error){r.failure="Recipes unavailable; repair ingredient-enchantments.json and reload.";r.revision++;throw error;}
    }
    private static void validate(MinecraftServer server,List<IngredientEnchantmentRecipe> recipes) {
        Set<String> groups=new HashSet<>();CATALOG.recipes().values().forEach(r->groups.add(r.group()));
        var enchantments=server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        for(var r:recipes){
            if(!groups.contains(r.group()))throw new IllegalArgumentException("Unregistered workshop group: "+r.group());
            if(!Registries.ITEM.containsId(Identifier.of(r.ingredient()))||Identifier.of(r.ingredient()).equals(Identifier.ofVanilla("air")))throw new IllegalArgumentException("Unknown/non-item ingredient: "+r.ingredient());
            if(!enchantments.containsId(Identifier.of(r.enchantment())))throw new IllegalArgumentException("Unknown recipe enchantment: "+r.enchantment());
        }
    }
    private static Runtime runtime(MinecraftServer server) {
        if(!server.isOnThread())throw new IllegalStateException("Workshop requires server thread");
        var r=SERVERS.get(server);if(r==null)throw new IllegalStateException("Ingredient enchanting not initialized");return r;
    }
    public static void open(ServerPlayerEntity player,String group,String title,Gate gate) {open(player,group,title,gate,"",true,null);}
    private static void open(ServerPlayerEntity player,String group,String title,Gate gate,String notice,boolean opening,UUID parent) {
        var server=player.getEntityWorld().getServer();var r=runtime(server);
        if(!ServerPlayNetworking.canSend(player,ItemEnchantingPayloads.View.ID)){player.sendMessage(Text.literal("Install the matching Chronicle client to use this workshop."),false);return;}
        if(title==null||title.isBlank()||title.length()>80||gate==null)throw new IllegalArgumentException("Invalid workshop");
        if(!r.offers.containsKey(player.getUuid())&&r.offers.size()>=MAX_OFFERS){player.sendMessage(Text.literal("Workshop capacity reached; try again shortly."),false);return;}
        var target=player.getMainHandStack();var ingredient=player.getOffHandStack();
        var preview=preview(server,r,group,target,ingredient);String blocked=gate.unavailable(player);
        if(blocked==null&&r.failure!=null)blocked=r.failure;
        if(blocked==null)blocked=preview.problem;
        var offer=new Offer(UUID.randomUUID(),System.nanoTime(),player.networkHandler,parent,group,title,gate,
                player.getInventory().getSelectedSlot(),target.copy(),ingredient.copy(),r.revision,EnchantmentCapRuntime.policy(server),opening);
        r.offers.put(player.getUuid(),offer);
        String status=notice.isBlank()?(blocked==null?"Ready. Confirm to consume the offering and enchant your held item.":blocked):notice+" "+(blocked==null?"Ready.":blocked);
        send(player,offer,status,preview,blocked==null,false,r);
    }
    private static Preview preview(MinecraftServer server,Runtime r,String group,ItemStack target,ItemStack ingredient) {
        if(target.isEmpty())return new Preview(null,ItemStack.EMPTY,"Hold one tool, armour piece or book in your main hand.","");
        if(target.getCount()!=1)return new Preview(null,ItemStack.EMPTY,"Hold a single target item; stacked targets cannot be enchanted.","");
        String item=Registries.ITEM.getId(ingredient.getItem()).toString();
        var recipe=r.recipes.stream().filter(v->v.enabled()&&v.group().equals(group)&&v.ingredient().equals(item)).findFirst().orElse(null);
        if(recipe==null)return new Preview(null,ItemStack.EMPTY,"Put a listed ingredient in your offhand, then Refresh.","");
        if(ingredient.getCount()<recipe.count())return new Preview(recipe,ItemStack.EMPTY,"Not enough ingredients: requires "+recipe.count()+".","");
        var enchant=server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(recipe.enchantment())));
        boolean book=target.isOf(Items.BOOK)||target.isOf(Items.ENCHANTED_BOOK);
        if(!book&&!enchant.value().isAcceptableItem(target))return new Preview(recipe,ItemStack.EMPTY,"That enchantment does not support this target.","");
        int level=Math.min(recipe.level(),enchant.value().getMaxLevel());
        var type=book?DataComponentTypes.STORED_ENCHANTMENTS:DataComponentTypes.ENCHANTMENTS;
        var old=target.getOrDefault(type,ItemEnchantmentsComponent.DEFAULT);
        String result=enchantmentLabel(enchant,level);
        if(old.getLevel(enchant)>=level)return new Preview(recipe,ItemStack.EMPTY,"This item already has that enchantment at the granted level or higher.",result);
        for(var component:List.of(target.getOrDefault(DataComponentTypes.ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT),target.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT)))
            for(var existing:component.getEnchantments())if(!existing.equals(enchant)&&!Enchantment.canBeCombined(existing,enchant))return new Preview(recipe,ItemStack.EMPTY,"An existing enchantment is incompatible; nothing will be removed.",result);
        ItemStack output=target.isOf(Items.BOOK)?target.copyComponentsToNewStack(Items.ENCHANTED_BOOK,1):target.copy();
        var builder=new ItemEnchantmentsComponent.Builder(old);builder.set(enchant,level);output.set(type,builder.build());
        return new Preview(recipe,output,null,result);
    }
    private static void send(ServerPlayerEntity player,Offer offer,String status,Preview preview,boolean available,boolean completed,Runtime r) {
        var hints=new StringBuilder();
        for(var recipe:r.recipes)if(recipe.enabled()&&recipe.group().equals(offer.group)){
            var enchant=player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(recipe.enchantment())));
            String line=recipe.count()+" "+Registries.ITEM.get(Identifier.of(recipe.ingredient())).getName().getString()+" → "+enchantmentLabel(enchant,Math.min(recipe.level(),enchant.value().getMaxLevel()))+"\n";
            if(hints.length()+line.length()>1900){hints.append("More recipes are configured on this server.\n");break;}hints.append(line);
        }
        String ingredient=offer.ingredient.isEmpty()?"Empty offhand":offer.ingredient.getName().getString()+" × "+(preview.recipe==null?"?":preview.recipe.count());
        ServerPlayNetworking.send(player,new ItemEnchantingPayloads.View(offer.token,offer.title,shorten(status,384),
                shorten(offer.target.isEmpty()?"Empty main hand":offer.target.getName().getString(),160),shorten(ingredient,160),
                shorten(preview.result.isBlank()?"No valid result":preview.result,160),hints.toString(),available,offer.opening&&!completed,completed));
    }
    public static void act(ServerPlayerEntity player,ItemEnchantingPayloads.Action action) {
        var server=player.getEntityWorld().getServer();var r=runtime(server);var offer=r.offers.get(player.getUuid());
        // Close also cancels a just-refreshed successor whose token has not reached
        // the client yet. It can only revoke the current connection's immediate successor.
        if(action.action()==2){if(offer!=null&&OfferIdentity.accepts(offer.connection,offer.token,offer.parent,
                player.networkHandler,action.token(),true))r.offers.remove(player.getUuid(),offer);return;}
        if(offer==null||!OfferIdentity.accepts(offer.connection,offer.token,offer.parent,
                player.networkHandler,action.token(),false))return;
        r.offers.remove(player.getUuid(),offer); // one use, including failed/stale/refresh attempts
        if(System.nanoTime()-offer.created>=OFFER_NANOS){player.sendMessage(Text.literal("Workshop offer expired. Open the station again; nothing was consumed."),false);return;}
        if(action.action()==1){open(player,offer.group,offer.title,offer.gate,"",false,offer.token);return;}
        String problem=offer.gate.unavailable(player);
        var inventory=player.getInventory();var target=player.getMainHandStack();var ingredient=player.getOffHandStack();
        if(problem==null&&(r.failure!=null||r.revision!=offer.revision||!EnchantmentCapRuntime.policy(server).equals(offer.policy)))problem="The workshop configuration changed.";
        if(problem==null&&(inventory.getSelectedSlot()!=offer.slot||!ItemStack.areEqual(target,offer.target)||!ItemStack.areEqual(ingredient,offer.ingredient)))problem="Your held items changed.";
        var preview=preview(server,r,offer.group,target,ingredient);
        if(problem==null)problem=preview.problem;
        if(problem!=null){open(player,offer.group,offer.title,offer.gate,"Nothing consumed. "+problem,false,offer.token);return;}
        // All validation and copies precede the two ordinary native inventory writes.
        ItemStack remainder=ingredient.copy();remainder.decrement(preview.recipe.count());
        inventory.setStack(offer.slot,preview.output);player.setStackInHand(net.minecraft.util.Hand.OFF_HAND,remainder);
        inventory.markDirty();player.currentScreenHandler.syncState();player.playerScreenHandler.syncState();
        try{offer.gate.completed(player);}catch(Exception error){ChronicleCore.LOGGER.error("Workshop completed; cosmetic feedback failed",error);}
        send(player,offer,"Enchanted. Consumed "+preview.recipe.count()+" ingredient(s); no XP spent.",preview,false,true,r);
    }
    private static String shorten(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
    private static String enchantmentLabel(net.minecraft.registry.entry.RegistryEntry<Enchantment> enchant,int level){
        // Native getName omits I when its maximum is I. A trade must show its exact level.
        String key="enchantment.level."+level,value=Text.translatable(key).getString();
        return enchant.value().description().getString()+" "+(value.equals(key)?Integer.toString(level):value);
    }
    private record Preview(IngredientEnchantmentRecipe recipe,ItemStack output,String problem,String result) { }
    private record Offer(UUID token,long created,net.minecraft.server.network.ServerPlayNetworkHandler connection,UUID parent,
                         String group,String title,Gate gate,int slot,ItemStack target,ItemStack ingredient,long revision,EnchantmentCaps policy,boolean opening) { }
    private static final class Runtime {
        final IngredientEnchantmentConfig config;List<IngredientEnchantmentRecipe> recipes=List.of();String failure;long revision;
        final Map<UUID,Offer> offers=new HashMap<>();Runtime(IngredientEnchantmentConfig config){this.config=config;}
    }
}
