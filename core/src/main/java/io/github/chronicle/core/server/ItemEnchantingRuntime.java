package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.enchanting.*;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.network.ItemEnchantingPayloads.Phase;
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
import net.minecraft.util.Identifier;
import java.util.*;

/** Server-authoritative sequential offerings with private pre-commit escrow. */
public final class ItemEnchantingRuntime {
    private static final String TOOL="chronicle_core:item_enchanting";
    private static final IngredientEnchantmentCatalog CATALOG=new IngredientEnchantmentCatalog();
    private static final Map<MinecraftServer,Runtime> SERVERS=new IdentityHashMap<>();
    private static final long ACTION_NANOS=30_000_000_000L;
    private static final int WORK_TICKS=30;
    public static final int MAX_OFFERS=256;
    private ItemEnchantingRuntime() { }

    public static IngredientEnchantmentCatalog catalog(){return CATALOG;}
    @FunctionalInterface public interface Gate {
        State state(ServerPlayerEntity player);
        default boolean stillPresent(ServerPlayerEntity player){return player.isAlive()&&!player.isSpectator();}
        /** Cosmetic feedback only, after a committed output exists. */
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
            var sample=List.copyOf(r.open.values());int count=Math.min(4,sample.size()),start=r.cursor%sample.size();
            for(int n=0;n<count;n++){
                var b=sample.get((start+n)%sample.size());var actor=b.handler.owner();
                if(actor.currentScreenHandler!=b.handler||actor.networkHandler!=b.connection||!b.gate.stillPresent(actor))settle(b);
                else refresh(actor,b.handler,false);
            }
            r.cursor=(start+count)%sample.size();
        });
        ServerPlayNetworking.registerGlobalReceiver(ItemEnchantingPayloads.Offer.ID,(payload,context)->context.server().execute(()->offer(context.player(),payload)));
        GameSetupTools.register(new GameSetupTools.Tool(TOOL,"Ingredient enchanting",
                "Recipes for item-based workshops. Campaigns supply stations and power; server JSON controls ingredients, levels and counts.",
                List.of(new GameSetupTools.Verb("reload","Reload recipes JSON",GameSetupTools.Guard.EDIT,true),new GameSetupTools.Verb("refresh","Refresh",GameSetupTools.Guard.READ_ONLY,false)),
                source->{var r=runtime(source.getServer());return new GameSetupTools.Status(r.revision,List.of(
                        new Fact("Configuration","config/chronicle/ingredient-enchantments.json; complete operator override of campaign defaults."),
                        new Fact("Recipes",r.recipes.size()+" configured, "+r.recipes.stream().filter(IngredientEnchantmentRecipe::enabled).count()+" enabled."),
                        new Fact("Status",r.failure==null?"Ready; native compatibility and universal caps apply.":r.failure),
                        new Fact("Workflow","Offer two items sequentially. The server resolves their roles after both are lodged; closing safely returns staged items.")),Map.of());},
                (source,verb)->{var r=runtime(source.getServer());if(verb.equals("reload")){reload(source.getServer(),r);return "Ingredient enchanting recipes reloaded; open workshops refreshed.";}return "Ingredient enchanting refreshed.";}));
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
        }catch(Exception error){r.failure="Recipes unavailable: "+(error.getMessage()==null?"repair ingredient-enchantments.json and reload":error.getMessage());r.revision++;throw error;}
        finally{for(var b:List.copyOf(r.open.values()))refresh(b.handler.owner(),b.handler,true);}
    }
    private static void validate(MinecraftServer server,List<IngredientEnchantmentRecipe> recipes){
        validateSingleItemRecipes(recipes);
        Set<String> groups=new HashSet<>();CATALOG.recipes().values().forEach(r->groups.add(r.group()));
        var enchantments=server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        for(var recipe:recipes){
            if(!groups.contains(recipe.group()))throw new IllegalArgumentException("Unregistered workshop group: "+recipe.group());
            if(!Registries.ITEM.containsId(Identifier.of(recipe.ingredient()))||Identifier.of(recipe.ingredient()).equals(Identifier.ofVanilla("air")))throw new IllegalArgumentException("Unknown/non-item ingredient: "+recipe.ingredient());
            if(!enchantments.containsId(Identifier.of(recipe.enchantment())))throw new IllegalArgumentException("Unknown recipe enchantment: "+recipe.enchantment());
        }
    }
    static void validateSingleItemRecipes(List<IngredientEnchantmentRecipe> recipes){
        for(var recipe:recipes)if(recipe.enabled()&&recipe.count()!=1)
            throw new IllegalArgumentException("Enabled workshop recipe "+recipe.id()+" must use the single-item ingredient count of 1");
    }
    private static Runtime runtime(MinecraftServer server){
        if(!server.isOnThread())throw new IllegalStateException("Workshop requires server thread");var r=SERVERS.get(server);if(r==null)throw new IllegalStateException("Ingredient enchanting not initialized");return r;
    }

    public static void open(ServerPlayerEntity actor,String group,String title,ItemEnchantingPayloads.Artwork artwork,Gate gate){
        var server=actor.getEntityWorld().getServer();var r=runtime(server);
        if(!ServerPlayNetworking.canSend(actor,ItemEnchantingPayloads.View.ID)){actor.sendMessage(Text.literal("Install the matching Chronicle client to use this workshop."),false);return;}
        if(title==null||title.isBlank()||title.length()>80||artwork==null||gate==null||CATALOG.recipes().values().stream().noneMatch(v->v.group().equals(group)))throw new IllegalArgumentException("Invalid workshop");
        if(r.open.size()>=MAX_OFFERS&&!r.open.containsKey(actor.getUuid())){actor.sendMessage(Text.literal("Workshop capacity reached; try again shortly."),false);return;}
        Binding[] opened=new Binding[1];
        actor.openHandledScreen(new ExtendedScreenHandlerFactory<ItemEnchantingPayloads.View>(){
            @Override public Text getDisplayName(){return Text.literal(title);}
            @Override public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity player){
                boolean available=gateState(gate,actor)==State.READY&&r.failure==null;
                var initial=new ItemEnchantingPayloads.View(syncId,UUID.randomUUID(),UUID.randomUUID(),0,title,Phase.EMPTY,available,false,artwork,true);
                var h=new ItemEnchantingScreenHandler(syncId,inventory,initial);var b=new Binding(h,actor.networkHandler,group,title,artwork,gate);
                b.actionToken=initial.actionToken();b.tokenCreated=System.nanoTime();b.observedEntry=ItemStack.EMPTY;opened[0]=b;r.open.put(actor.getUuid(),b);return h;
            }
            @Override public ItemEnchantingPayloads.View getScreenOpeningData(ServerPlayerEntity player){if(player!=actor||opened[0]==null)throw new IllegalStateException("Workshop opening changed");return opened[0].handler.view();}
        });
    }

    private static Binding binding(ServerPlayerEntity actor,ItemEnchantingScreenHandler handler){
        var r=runtime(actor.getEntityWorld().getServer());var b=r.open.get(actor.getUuid());
        return b!=null&&b.handler==handler&&handler.owner()==actor&&b.connection==actor.networkHandler&&!handler.settled()?b:null;
    }
    public static boolean canUse(ServerPlayerEntity actor,ItemEnchantingScreenHandler handler){
        var b=binding(actor,handler);return b!=null&&actor.currentScreenHandler==handler&&b.gate.stillPresent(actor);
    }
    public static void closed(ServerPlayerEntity actor,ItemEnchantingScreenHandler handler){
        var r=runtime(actor.getEntityWorld().getServer());var b=r.open.get(actor.getUuid());
        if(b!=null&&b.handler==handler&&handler.owner()==actor&&b.connection==actor.networkHandler&&r.open.remove(actor.getUuid(),b))recoverPrivate(actor,b);
    }
    public static void changed(ServerPlayerEntity actor,ItemEnchantingScreenHandler handler){if(binding(actor,handler)!=null)refresh(actor,handler,false);}

    private static State gateState(Gate gate,ServerPlayerEntity actor){
        State state=gate.state(actor);if(state!=State.READY&&state!=State.QUIET&&state!=State.UNPOWERED&&state!=State.UNAVAILABLE)throw new IllegalStateException("Invalid workshop gate state");return state;
    }
    private static void refresh(ServerPlayerEntity actor,ItemEnchantingScreenHandler handler,boolean force){
        var b=binding(actor,handler);if(b==null)return;
        if(actor.currentScreenHandler!=handler||!b.gate.stillPresent(actor)){settle(b);return;}
        var server=actor.getEntityWorld().getServer();var r=runtime(server);var policy=EnchantmentCapRuntime.policy(server);
        boolean available=gateState(b.gate,actor)==State.READY&&r.failure==null;
        if(b.phase!=Phase.RESULT&&hasPrivate(b)&&(r.revision!=b.transactionRevision||!policy.equals(b.transactionPolicy)||!available)){
            recoverAll(actor,b);b.phase=Phase.EMPTY;force=true;
        }
        if(b.phase==Phase.WORKING){
            if(!handler.entry().isEmpty()||!handler.output().isEmpty()||!privateIntact(b)){
                recoverAll(actor,b);b.phase=Phase.EMPTY;force=true;
            }else if((long)server.getTicks()>=b.deadlineTick){complete(actor,b);return;}
        }
        if(b.phase==Phase.RESULT&&handler.output().isEmpty()){b.phase=Phase.EMPTY;force=true;}
        sync(actor,b,available,force);
    }
    private static void sync(ServerPlayerEntity actor,Binding b,boolean available,boolean force){
        var handler=b.handler;boolean offerable=available&&(b.phase==Phase.EMPTY||b.phase==Phase.OFFERED)&&singleEntry(handler)&&handler.output().isEmpty();
        boolean expired=System.nanoTime()-b.tokenCreated>=ACTION_NANOS;
        boolean entryChanged=!ItemStack.areEqual(b.observedEntry,handler.entry());
        var old=handler.view();
        if(!force&&!expired&&!entryChanged&&old.phase()==b.phase&&old.available()==available&&old.offerable()==offerable)return;
        b.actionToken=UUID.randomUUID();b.tokenCreated=System.nanoTime();b.observedEntry=handler.entry().copy();
        var next=new ItemEnchantingPayloads.View(handler.syncId,old.sessionId(),b.actionToken,Math.addExact(old.revision(),1),b.title,b.phase,available,offerable,b.artwork,false);
        handler.view(next);handler.sendContentUpdates();ServerPlayNetworking.send(actor,next);
    }

    public static void offer(ServerPlayerEntity actor,ItemEnchantingPayloads.Offer request){
        var server=actor.getEntityWorld().getServer();var r=runtime(server);var b=r.open.get(actor.getUuid());
        if(b==null||!canUse(actor,b.handler))return;
        refresh(actor,b.handler,false);
        var view=b.handler.view();
        if(request.syncId()!=b.handler.syncId||!request.sessionId().equals(view.sessionId())||!request.actionToken().equals(view.actionToken())
                ||System.nanoTime()-b.tokenCreated>=ACTION_NANOS||!view.available()||!view.offerable())return;
        var policy=EnchantmentCapRuntime.policy(server);
        if(b.phase==Phase.EMPTY){
            ItemStack offered=removeSingleEntry(b.handler);if(offered.isEmpty())return;
            b.first=offered;b.firstSnapshot=offered.copy();b.transactionRevision=r.revision;b.transactionPolicy=policy;b.phase=Phase.OFFERED;
            sync(actor,b,true,true);return;
        }
        if(b.phase!=Phase.OFFERED||r.revision!=b.transactionRevision||!policy.equals(b.transactionPolicy)){recoverAll(actor,b);b.phase=Phase.EMPTY;sync(actor,b,view.available(),true);return;}
        ItemStack offered=removeSingleEntry(b.handler);if(offered.isEmpty())return;
        b.second=offered;b.secondSnapshot=offered.copy();b.deadlineTick=(long)server.getTicks()+WORK_TICKS;b.phase=Phase.WORKING;sync(actor,b,true,true);
    }
    private static boolean singleEntry(ItemEnchantingScreenHandler handler){return !handler.entry().isEmpty()&&handler.entry().getCount()==1;}
    private static ItemStack removeSingleEntry(ItemEnchantingScreenHandler handler){
        if(!singleEntry(handler))return ItemStack.EMPTY;
        ItemStack[] offered={ItemStack.EMPTY};handler.mutate(()->offered[0]=handler.takeEntry());return offered[0];
    }

    private static void complete(ServerPlayerEntity actor,Binding b){
        var server=actor.getEntityWorld().getServer();var r=runtime(server);var policy=EnchantmentCapRuntime.policy(server);
        if(b.phase!=Phase.WORKING||r.failure!=null||r.revision!=b.transactionRevision||!policy.equals(b.transactionPolicy)
                ||gateState(b.gate,actor)!=State.READY||!b.gate.stillPresent(actor)||!b.handler.entry().isEmpty()||!b.handler.output().isEmpty()||!privateIntact(b)){
            recoverAll(actor,b);b.phase=Phase.EMPTY;sync(actor,b,false,true);return;
        }
        Proposal direct=proposal(server,r,b.group,b.first,b.second,policy,1,2);
        Proposal reverse=proposal(server,r,b.group,b.second,b.first,policy,2,1);
        Selection selection=select(direct,reverse);
        if(selection==Selection.NONE){clearPrivate(b);b.phase=Phase.EMPTY;sync(actor,b,true,true);return;}
        if(selection==Selection.AMBIGUOUS){recoverPrivate(actor,b);b.phase=Phase.EMPTY;sync(actor,b,true,true);return;}
        Proposal accepted=selection==Selection.FIRST?direct:selection==Selection.SECOND?reverse:null;
        if(accepted==null)throw new IllegalStateException("Workshop selection lost its proposal");

        ItemStack firstRemainder=remainder(b.first,accepted.consumeFirst),secondRemainder=remainder(b.second,accepted.consumeSecond);
        ItemStack result=accepted.output.copy();clearPrivate(b);
        b.handler.mutate(()->b.handler.output(result));
        returnStack(actor,firstRemainder);returnStack(actor,secondRemainder);b.phase=Phase.RESULT;sync(actor,b,true,true);
        actor.getInventory().markDirty();b.handler.syncState();actor.playerScreenHandler.syncState();
        try{b.gate.completed(actor);}catch(Exception error){ChronicleCore.LOGGER.error("Workshop completed; cosmetic feedback failed",error);}
    }

    private static Proposal proposal(MinecraftServer server,Runtime runtime,String group,ItemStack target,ItemStack material,EnchantmentCaps policy,int targetPosition,int materialPosition){
        if(target.isEmpty()||target.getCount()!=1||material.isEmpty()||material.getCount()!=1)return null;
        String item=Registries.ITEM.getId(material.getItem()).toString();
        var recipe=runtime.recipes.stream().filter(v->v.enabled()&&v.group().equals(group)&&v.ingredient().equals(item)).findFirst().orElse(null);
        if(recipe==null||recipe.count()!=1)return null;
        var enchant=enchantment(server,recipe.enchantment());boolean book=target.isOf(Items.BOOK)||target.isOf(Items.ENCHANTED_BOOK);
        if(!book&&!enchant.value().isAcceptableItem(target))return null;
        int level=Math.min(recipe.level(),policy.cap(recipe.enchantment(),enchant.value().getMaxLevel()));
        var type=book?DataComponentTypes.STORED_ENCHANTMENTS:DataComponentTypes.ENCHANTMENTS;var old=target.getOrDefault(type,ItemEnchantmentsComponent.DEFAULT);
        for(var component:List.of(target.getOrDefault(DataComponentTypes.ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT),target.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT)))
            for(var existing:component.getEnchantments())if(!existing.equals(enchant)&&!Enchantment.canBeCombined(existing,enchant))return null;
        if(old.getLevel(enchant)>=level)return new Proposal(target.copy(),1,1);
        ItemStack output=target.isOf(Items.BOOK)?target.copyComponentsToNewStack(Items.ENCHANTED_BOOK,1):target.copy();
        var builder=new ItemEnchantmentsComponent.Builder(old);builder.set(enchant,level);output.set(type,builder.build());EnchantmentStacks.normalize(output,policy);
        int consumeFirst=targetPosition==1?1:recipe.count(),consumeSecond=materialPosition==2?recipe.count():1;
        return new Proposal(output,consumeFirst,consumeSecond);
    }

    enum Selection { NONE, FIRST, SECOND, AMBIGUOUS }
    static record Proposal(ItemStack output,int consumeFirst,int consumeSecond) {
        Proposal {
            if(output==null||output.isEmpty()||consumeFirst<0||consumeSecond<0)throw new IllegalArgumentException("Invalid workshop proposal");
            output=output.copy();
        }
    }
    static Selection select(Proposal first,Proposal second){
        return select(first!=null,first==null?0:first.consumeFirst,first==null?0:first.consumeSecond,
                second!=null,second==null?0:second.consumeFirst,second==null?0:second.consumeSecond,
                first!=null&&second!=null&&ItemStack.areEqual(first.output,second.output));
    }
    /** Pure ambiguity rule; the caller supplies exact item-and-component equality. */
    static Selection select(boolean first,int firstConsumeA,int firstConsumeB,boolean second,int secondConsumeA,int secondConsumeB,boolean outputsExactlyEqual){
        if(!first)return second?Selection.SECOND:Selection.NONE;
        if(!second)return Selection.FIRST;
        return firstConsumeA==secondConsumeA&&firstConsumeB==secondConsumeB&&outputsExactlyEqual?Selection.FIRST:Selection.AMBIGUOUS;
    }

    private static ItemStack remainder(ItemStack original,int consumed){ItemStack result=original.copy();result.decrement(consumed);return result;}
    private static boolean hasPrivate(Binding b){return !b.first.isEmpty()||!b.second.isEmpty();}
    private static boolean privateIntact(Binding b){return !b.first.isEmpty()&&!b.second.isEmpty()&&ItemStack.areEqual(b.first,b.firstSnapshot)&&ItemStack.areEqual(b.second,b.secondSnapshot);}
    private static void recoverAll(ServerPlayerEntity actor,Binding b){
        ItemStack[] entry={ItemStack.EMPTY};b.handler.mutate(()->entry[0]=b.handler.takeEntry());recoverPrivate(actor,b);returnStack(actor,entry[0]);
    }
    private static void recoverPrivate(ServerPlayerEntity actor,Binding b){
        ItemStack first=b.first.copy(),second=b.second.copy();clearPrivate(b);returnStack(actor,first);returnStack(actor,second);
    }
    private static void clearPrivate(Binding b){b.first=ItemStack.EMPTY;b.second=ItemStack.EMPTY;b.firstSnapshot=ItemStack.EMPTY;b.secondSnapshot=ItemStack.EMPTY;b.deadlineTick=-1;}
    private static void returnStack(ServerPlayerEntity actor,ItemStack stack){
        if(stack.isEmpty())return;ItemStack returned=stack.copy();
        boolean removed=actor.isRemoved()&&actor.getRemovalReason()!=net.minecraft.entity.Entity.RemovalReason.CHANGED_DIMENSION;
        if(!actor.isAlive()||actor.isDisconnected()||removed){actor.dropItem(returned,false);return;}
        actor.getInventory().insertStack(returned);if(!returned.isEmpty())actor.dropItem(returned,false);
    }
    private static net.minecraft.registry.entry.RegistryEntry<Enchantment> enchantment(MinecraftServer server,String id){
        return server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of(id)));
    }

    private static final class Binding {
        final ItemEnchantingScreenHandler handler;final net.minecraft.server.network.ServerPlayNetworkHandler connection;
        final String group,title;final ItemEnchantingPayloads.Artwork artwork;final Gate gate;
        Phase phase=Phase.EMPTY;UUID actionToken;long tokenCreated,transactionRevision,deadlineTick=-1;
        EnchantmentCaps transactionPolicy=EnchantmentCaps.DEFAULT;ItemStack observedEntry=ItemStack.EMPTY;
        ItemStack first=ItemStack.EMPTY,second=ItemStack.EMPTY,firstSnapshot=ItemStack.EMPTY,secondSnapshot=ItemStack.EMPTY;
        Binding(ItemEnchantingScreenHandler handler,net.minecraft.server.network.ServerPlayNetworkHandler connection,String group,String title,ItemEnchantingPayloads.Artwork artwork,Gate gate){
            this.handler=handler;this.connection=connection;this.group=group;this.title=title;this.artwork=artwork;this.gate=gate;
        }
    }
    private static final class Runtime {
        final IngredientEnchantmentConfig config;List<IngredientEnchantmentRecipe> recipes=List.of();String failure;long revision;int cursor;
        final Map<UUID,Binding> open=new LinkedHashMap<>();Runtime(IngredientEnchantmentConfig config){this.config=config;}
    }
}
