package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.enchanting.*;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.network.ItemEnchantingPayloads.State;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import java.util.*;

/** Native temporary inputs and one-use component-preserving output transactions. */
public final class ItemEnchantingRuntime {
    private static final String TOOL="chronicle_core:item_enchanting";
    private static final IngredientEnchantmentCatalog CATALOG=new IngredientEnchantmentCatalog();
    private static final Map<MinecraftServer,Runtime> SERVERS=new IdentityHashMap<>();
    private static final long OFFER_NANOS=30_000_000_000L;
    public static final int MAX_OFFERS=256;
    private ItemEnchantingRuntime() { }
    public static IngredientEnchantmentCatalog catalog(){return CATALOG;}
    @FunctionalInterface public interface Gate {
        State state(ServerPlayerEntity player);
        default boolean stillPresent(ServerPlayerEntity player){return player.isAlive()&&!player.isSpectator();}
        /** Cosmetic feedback only, after the inventory transaction. */
        default void completed(ServerPlayerEntity player){ }
    }
    public static void register(){
        ItemEnchantingPayloads.register();ItemEnchantingScreenHandler.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server->CATALOG.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(ItemEnchantingRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var r=SERVERS.get(server);if(r!=null)for(var b:List.copyOf(r.open.values()))settle(b);});
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
        ServerDisconnects.register((connection,server)->{var r=SERVERS.get(server);if(r!=null){var b=r.open.get(connection.player.getUuid());if(b!=null&&b.connection==connection)settle(b);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var r=SERVERS.get(server);if(r==null||r.open.isEmpty())return;
            var sample=List.copyOf(r.open.values());int start=r.cursor%sample.size();
            for(int n=0;n<Math.min(4,sample.size());n++){
                var b=sample.get((start+n)%sample.size());var actor=b.handler.owner();
                if(actor.currentScreenHandler!=b.handler||actor.networkHandler!=b.connection||!b.gate.stillPresent(actor))settle(b);
                else refresh(actor,b.handler,false);
            }
            r.cursor=(start+Math.min(4,sample.size()))%sample.size();
        });
        ServerPlayNetworking.registerGlobalReceiver(ItemEnchantingPayloads.Take.ID,(payload,context)->context.server().execute(()->take(context.player(),payload)));
        GameSetupTools.register(new GameSetupTools.Tool(TOOL,"Ingredient enchanting",
                "Recipes for item-based workshops. Campaigns supply stations and power; server JSON controls ingredients, levels and counts.",
                List.of(new GameSetupTools.Verb("reload","Reload recipes JSON",GameSetupTools.Guard.EDIT,true),new GameSetupTools.Verb("refresh","Refresh",GameSetupTools.Guard.READ_ONLY,false)),
                source->{var r=runtime(source.getServer());return new GameSetupTools.Status(r.revision,List.of(
                        new Fact("Configuration","config/chronicle/ingredient-enchantments.json; complete operator override of campaign defaults."),
                        new Fact("Recipes",r.recipes.size()+" configured, "+r.recipes.stream().filter(IngredientEnchantmentRecipe::enabled).count()+" enabled."),
                        new Fact("Status",r.failure==null?"Ready; native compatibility and universal caps apply.":r.failure),
                        new Fact("Workflow","Place target and material in the workstation. Take the result to forge; no XP charge. Close returns staged inputs using vanilla inventory/drop behavior.")),Map.of());},
                (source,verb)->{var r=runtime(source.getServer());if(verb.equals("reload")){reload(source.getServer(),r);return "Ingredient enchanting recipes reloaded; workshop previews refreshed.";}return "Ingredient enchanting refreshed.";}));
    }
    private static void settle(Binding b){
        var actor=b.handler.owner();if(b.handler.settled())return;
        if(actor.currentScreenHandler==b.handler)actor.closeHandledScreen();else b.handler.onClosed(actor);
    }
    private static void start(MinecraftServer server){
        var r=new Runtime(new IngredientEnchantmentConfig(FabricLoader.getInstance().getConfigDir().resolve("chronicle/ingredient-enchantments.json")));SERVERS.put(server,r);
        try{reload(server,r);}catch(Exception error){ChronicleCore.LOGGER.error("Ingredient enchanting unavailable; existing config preserved",error);}
    }
    private static void reload(MinecraftServer server,Runtime r)throws Exception{
        try{var next=r.config.loadOrCreate(List.copyOf(CATALOG.recipes().values()));validate(server,next);r.recipes=List.copyOf(next);r.failure=null;r.revision++;
            ChronicleCore.LOGGER.info("Ingredient enchanting ready: {} configured recipes",next.size());
        }catch(Exception error){r.failure="Recipes unavailable; repair ingredient-enchantments.json and reload.";r.revision++;throw error;}
        finally{for(var b:List.copyOf(r.open.values()))refresh(b.handler.owner(),b.handler,true);}
    }
    private static void validate(MinecraftServer server,List<IngredientEnchantmentRecipe> recipes){
        Set<String> groups=new HashSet<>();CATALOG.recipes().values().forEach(r->groups.add(r.group()));
        var enchantments=server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        for(var r:recipes){
            if(!groups.contains(r.group()))throw new IllegalArgumentException("Unregistered workshop group: "+r.group());
            if(!Registries.ITEM.containsId(Identifier.of(r.ingredient()))||Identifier.of(r.ingredient()).equals(Identifier.ofVanilla("air")))throw new IllegalArgumentException("Unknown/non-item ingredient: "+r.ingredient());
            if(!enchantments.containsId(Identifier.of(r.enchantment())))throw new IllegalArgumentException("Unknown recipe enchantment: "+r.enchantment());
        }
    }
    private static Runtime runtime(MinecraftServer server){
        if(!server.isOnThread())throw new IllegalStateException("Workshop requires server thread");var r=SERVERS.get(server);if(r==null)throw new IllegalStateException("Ingredient enchanting not initialized");return r;
    }
    public static void open(ServerPlayerEntity actor,String group,String title,Gate gate){
        var server=actor.getEntityWorld().getServer();var r=runtime(server);
        if(!ServerPlayNetworking.canSend(actor,ItemEnchantingPayloads.View.ID)){actor.sendMessage(Text.literal("Install the matching Chronicle client to use this workshop."),false);return;}
        if(title==null||title.isBlank()||title.length()>80||gate==null||CATALOG.recipes().values().stream().noneMatch(v->v.group().equals(group)))throw new IllegalArgumentException("Invalid workshop");
        if(r.open.size()>=MAX_OFFERS&&!r.open.containsKey(actor.getUuid())){actor.sendMessage(Text.literal("Workshop capacity reached; try again shortly."),false);return;}
        Binding[] opened=new Binding[1];
        actor.openHandledScreen(new ExtendedScreenHandlerFactory<ItemEnchantingPayloads.View>(){
            @Override public Text getDisplayName(){return Text.literal(title);}
            @Override public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity player){
                var policy=EnchantmentCapRuntime.policy(server);State state=gateState(gate,actor);if(state==State.READY)state=r.failure==null?State.EMPTY_TARGET:State.UNAVAILABLE;
                var initial=new ItemEnchantingPayloads.View(syncId,UUID.randomUUID(),UUID.randomUUID(),0,title,state,hints(server,r,group,policy),true);
                var h=new ItemEnchantingScreenHandler(syncId,inventory,initial);var b=new Binding(h,actor.networkHandler,group,title,gate);
                b.catalogRevision=r.revision;b.catalogPolicy=policy;opened[0]=b;r.open.put(actor.getUuid(),b);
                b.offer=new Offer(initial.token(),System.nanoTime(),ItemStack.EMPTY,ItemStack.EMPTY,r.revision,policy,state,ItemStack.EMPTY,0);
                return h;
            }
            @Override public ItemEnchantingPayloads.View getScreenOpeningData(ServerPlayerEntity player){if(player!=actor||opened[0]==null)throw new IllegalStateException("Workshop opening changed");return opened[0].handler.view();}
        });
    }
    private static Binding binding(ServerPlayerEntity actor,ItemEnchantingScreenHandler h){
        var r=runtime(actor.getEntityWorld().getServer());var b=r.open.get(actor.getUuid());return b!=null&&b.handler==h&&h.owner()==actor&&b.connection==actor.networkHandler&&!h.settled()?b:null;
    }
    public static boolean canUse(ServerPlayerEntity actor,ItemEnchantingScreenHandler h){var b=binding(actor,h);return b!=null&&actor.currentScreenHandler==h&&b.gate.stillPresent(actor);}
    public static void closed(ServerPlayerEntity actor,ItemEnchantingScreenHandler h){
        var r=runtime(actor.getEntityWorld().getServer());var b=r.open.get(actor.getUuid());
        if(b!=null&&b.handler==h&&h.owner()==actor&&b.connection==actor.networkHandler)r.open.remove(actor.getUuid(),b);
    }
    public static boolean ingredient(ServerPlayerEntity actor,ItemEnchantingScreenHandler h,ItemStack stack){
        var b=binding(actor,h);if(b==null)return false;String id=Registries.ITEM.getId(stack.getItem()).toString();return runtime(actor.getEntityWorld().getServer()).recipes.stream().anyMatch(v->v.enabled()&&v.group().equals(b.group)&&v.ingredient().equals(id));
    }
    public static void changed(ServerPlayerEntity actor,ItemEnchantingScreenHandler h){if(binding(actor,h)!=null)refresh(actor,h,false);}
    private static State gateState(Gate gate,ServerPlayerEntity actor){
        State state=gate.state(actor);if(state!=State.READY&&state!=State.QUIET&&state!=State.UNPOWERED&&state!=State.UNAVAILABLE)throw new IllegalStateException("Invalid workshop gate state");return state;
    }
    private static void refresh(ServerPlayerEntity actor,ItemEnchantingScreenHandler h,boolean force){
        var b=binding(actor,h);if(b==null)return;if(actor.currentScreenHandler!=h||!b.gate.stillPresent(actor)){settle(b);return;}
        var server=actor.getEntityWorld().getServer();var r=runtime(server);var policy=EnchantmentCapRuntime.policy(server);
        Preview preview=preview(server,r,b.group,h.target(),h.material(),policy);State state=gateState(b.gate,actor);if(state==State.READY)state=r.failure==null?preview.state:State.UNAVAILABLE;
        boolean catalogChanged=b.catalogRevision!=r.revision||!policy.equals(b.catalogPolicy);Offer old=b.offer;
        if(!force&&!catalogChanged&&old!=null&&old.revision==r.revision&&old.policy.equals(policy)&&old.state==state
                &&ItemStack.areEqual(old.target,h.target())&&ItemStack.areEqual(old.material,h.material())
                &&ItemStack.areEqual(old.output,preview.output)&&System.nanoTime()-old.created<OFFER_NANOS)return;
        var rows=catalogChanged?hints(server,r,b.group,policy):h.view().recipes();UUID token=UUID.randomUUID();
        b.offer=new Offer(token,System.nanoTime(),h.target().copy(),h.material().copy(),r.revision,policy,state,preview.output.copy(),preview.count);
        b.catalogRevision=r.revision;b.catalogPolicy=policy;
        var view=new ItemEnchantingPayloads.View(h.syncId,h.view().session(),token,Math.addExact(h.view().revision(),1),b.title,state,rows,false);
        h.view(view);h.result(state==State.READY?preview.output.copy():ItemStack.EMPTY);h.sendContentUpdates();
        ServerPlayNetworking.send(actor,catalogChanged?view:view.withRecipes(null));
    }
    private static Preview preview(MinecraftServer server,Runtime r,String group,ItemStack target,ItemStack material,EnchantmentCaps policy){
        if(target.isEmpty())return new Preview(State.EMPTY_TARGET,ItemStack.EMPTY,0);
        if(target.getCount()!=1)return new Preview(State.INCOMPATIBLE,ItemStack.EMPTY,0);
        if(material.isEmpty())return new Preview(State.EMPTY_MATERIAL,ItemStack.EMPTY,0);
        String item=Registries.ITEM.getId(material.getItem()).toString();var recipe=r.recipes.stream().filter(v->v.enabled()&&v.group().equals(group)&&v.ingredient().equals(item)).findFirst().orElse(null);
        if(recipe==null)return new Preview(State.EMPTY_MATERIAL,ItemStack.EMPTY,0);
        if(material.getCount()<recipe.count())return new Preview(State.SHORT_MATERIAL,ItemStack.EMPTY,0);
        var enchant=enchantment(server,recipe.enchantment());boolean book=target.isOf(Items.BOOK)||target.isOf(Items.ENCHANTED_BOOK);
        if(!book&&!enchant.value().isAcceptableItem(target))return new Preview(State.INCOMPATIBLE,ItemStack.EMPTY,0);
        int level=Math.min(recipe.level(),policy.cap(recipe.enchantment(),enchant.value().getMaxLevel()));
        var type=book?DataComponentTypes.STORED_ENCHANTMENTS:DataComponentTypes.ENCHANTMENTS;var old=target.getOrDefault(type,ItemEnchantmentsComponent.DEFAULT);
        if(old.getLevel(enchant)>=level)return new Preview(State.UNCHANGED,ItemStack.EMPTY,0);
        for(var component:List.of(target.getOrDefault(DataComponentTypes.ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT),target.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT)))
            for(var existing:component.getEnchantments())if(!existing.equals(enchant)&&!Enchantment.canBeCombined(existing,enchant))return new Preview(State.INCOMPATIBLE,ItemStack.EMPTY,0);
        ItemStack output=target.isOf(Items.BOOK)?target.copyComponentsToNewStack(Items.ENCHANTED_BOOK,1):target.copy();
        var builder=new ItemEnchantmentsComponent.Builder(old);builder.set(enchant,level);output.set(type,builder.build());EnchantmentStacks.normalize(output,policy);
        return new Preview(State.READY,output,recipe.count());
    }
    public static void take(ServerPlayerEntity actor,ItemEnchantingPayloads.Take request){
        var server=actor.getEntityWorld().getServer();var r=runtime(server);var b=r.open.get(actor.getUuid());
        if(b==null||!canUse(actor,b.handler)||request.syncId()!=b.handler.syncId||!request.session().equals(b.handler.view().session()))return;
        Offer offer=b.offer;if(offer==null||!offer.token.equals(request.token()))return;b.offer=null;
        var h=b.handler;var policy=EnchantmentCapRuntime.policy(server);
        if(System.nanoTime()-offer.created>=OFFER_NANOS||offer.state!=State.READY||r.failure!=null||offer.revision!=r.revision||!offer.policy.equals(policy)
                ||!ItemStack.areEqual(offer.target,h.target())||!ItemStack.areEqual(offer.material,h.material())||gateState(b.gate,actor)!=State.READY){refresh(actor,h,true);return;}
        Preview preview=preview(server,r,b.group,h.target(),h.material(),policy);
        if(preview.state!=State.READY||preview.count!=offer.count||!ItemStack.areEqual(preview.output,offer.output)){refresh(actor,h,true);return;}
        ItemStack output=preview.output.copy();Runnable delivery=delivery(actor,h,request,output);if(delivery==null){refresh(actor,h,true);return;}
        h.mutate(()->{h.result(ItemStack.EMPTY);h.consume(preview.count);delivery.run();});
        actor.getInventory().markDirty();h.syncState();actor.playerScreenHandler.syncState();refresh(actor,h,true);
        try{b.gate.completed(actor);}catch(Exception error){ChronicleCore.LOGGER.error("Workshop completed; cosmetic feedback failed",error);}
    }
    private static Runnable delivery(ServerPlayerEntity actor,ItemEnchantingScreenHandler h,ItemEnchantingPayloads.Take request,ItemStack output){
        if(request.destination()==0){
            ItemStack cursor=h.getCursorStack();if(cursor.isEmpty())return ()->h.setCursorStack(output);
            if(!ItemStack.areItemsAndComponentsEqual(cursor,output)||cursor.getCount()>=cursor.getMaxCount())return null;
            ItemStack next=cursor.copy();next.increment(1);return ()->h.setCursorStack(next);
        }
        if(request.destination()==1){
            for(int pass=0;pass<2;pass++)for(int index=38;index>=3;index--){
                var slot=h.getSlot(index);ItemStack old=slot.getStack();if(!slot.canInsert(output))continue;
                if(pass==0&&!old.isEmpty()&&ItemStack.areItemsAndComponentsEqual(old,output)&&old.getCount()<slot.getMaxItemCount(output)){
                    ItemStack next=old.copy();next.increment(1);return ()->slot.setStack(next);
                }
                if(pass==1&&old.isEmpty())return ()->slot.setStack(output);
            }
            return null;
        }
        if(request.destination()==2){
            if(request.button()==40)return actor.getOffHandStack().isEmpty()?()->actor.setStackInHand(Hand.OFF_HAND,output):null;
            int slot=request.button();return actor.getInventory().getStack(slot).isEmpty()?()->actor.getInventory().setStack(slot,output):null;
        }
        return ()->actor.dropItem(output,false);
    }
    private static net.minecraft.registry.entry.RegistryEntry<Enchantment> enchantment(MinecraftServer server,String id){return server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(id)));}
    private static List<ItemEnchantingPayloads.RecipeHint> hints(MinecraftServer server,Runtime r,String group,EnchantmentCaps policy){
        List<ItemEnchantingPayloads.RecipeHint> rows=new ArrayList<>();for(var recipe:r.recipes)if(recipe.enabled()&&recipe.group().equals(group)){
            var enchant=enchantment(server,recipe.enchantment());String label=enchant.value().description().getString();if(label.length()>160)label=label.substring(0,160);
            rows.add(new ItemEnchantingPayloads.RecipeHint(recipe.ingredient(),recipe.enchantment(),label,Math.min(recipe.level(),policy.cap(recipe.enchantment(),enchant.value().getMaxLevel())),recipe.count()));
        }return List.copyOf(rows);
    }
    private record Preview(State state,ItemStack output,int count){ }
    private record Offer(UUID token,long created,ItemStack target,ItemStack material,long revision,EnchantmentCaps policy,State state,ItemStack output,int count){ }
    private static final class Binding {
        final ItemEnchantingScreenHandler handler;final net.minecraft.server.network.ServerPlayNetworkHandler connection;final String group,title;final Gate gate;
        Offer offer;long catalogRevision;EnchantmentCaps catalogPolicy;
        Binding(ItemEnchantingScreenHandler h,net.minecraft.server.network.ServerPlayNetworkHandler connection,String group,String title,Gate gate){handler=h;this.connection=connection;this.group=group;this.title=title;this.gate=gate;}
    }
    private static final class Runtime {
        final IngredientEnchantmentConfig config;List<IngredientEnchantmentRecipe> recipes=List.of();String failure;long revision;int cursor;
        final Map<UUID,Binding> open=new LinkedHashMap<>();Runtime(IngredientEnchantmentConfig config){this.config=config;}
    }
}
