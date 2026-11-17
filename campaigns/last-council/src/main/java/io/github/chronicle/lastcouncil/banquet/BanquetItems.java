package io.github.chronicle.lastcouncil.banquet;

import io.github.chronicle.lastcouncil.server.BanquetRuntime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.SupplyKind;

/** Every supply has the same opaque token shape. Contamination is never an item component. */
public final class BanquetItems {
    private static final String TOKEN = "chronicle_banquet_token";
    private static final Map<SupplyKind, Item> ITEMS = new EnumMap<>(SupplyKind.class);
    private BanquetItems() { }
    public static void register() {
        for (SupplyKind kind : new SupplyKind[]{SupplyKind.DRINK, SupplyKind.POISON, SupplyKind.ANTIDOTE}) {
            Identifier id = Identifier.of("last_council", "banquet_" + kind.name().toLowerCase(java.util.Locale.ROOT));
            var key = RegistryKey.of(RegistryKeys.ITEM, id);
            ITEMS.put(kind, Registry.register(Registries.ITEM, key, new SupplyItem(new Item.Settings().registryKey(key).maxCount(1), kind)));
        }
    }
    public static ItemStack stack(SupplyKind kind, UUID token) {
        ItemStack stack = new ItemStack(java.util.Objects.requireNonNull(ITEMS.get(kind)));
        NbtCompound data = new NbtCompound(); data.putString(TOKEN, token.toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        return stack;
    }
    public static UUID token(ItemStack stack, SupplyKind kind) {
        boolean correctItem = kind == SupplyKind.FOOD ? edible(stack) : stack.isOf(ITEMS.get(kind));
        if (stack.isEmpty() || !correctItem || stack.getCount() != 1) throw new IllegalArgumentException("Choose an issued supply or a single food serving.");
        var data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) throw new IllegalArgumentException("This supply has not been issued.");
        String value = data.copyNbt().getString(TOKEN).orElse("");
        UUID token = UUID.fromString(value);
        if (!token.toString().equals(value)) throw new IllegalArgumentException("Invalid supply token.");
        return token;
    }
    public static boolean edible(ItemStack stack) {
        return !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD) && stack.contains(DataComponentTypes.CONSUMABLE);
    }
    public static boolean trackedFood(ItemStack stack) {
        var data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return edible(stack) && data != null && data.copyNbt().contains(TOKEN);
    }
    public static ItemStack trackFood(ItemStack stack, UUID token) {
        return withToken(stack.copyWithCount(1), token);
    }
    public static ItemStack withToken(ItemStack stack, UUID token) {
        ItemStack serving = stack.copy();
        var existing = serving.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound data = existing == null ? new NbtCompound() : existing.copyNbt();
        data.putString(TOKEN, token.toString());
        serving.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        return serving;
    }
    public static ItemStack untrackFood(ItemStack stack) {
        ItemStack clean = stack.copy();
        var existing = clean.get(DataComponentTypes.CUSTOM_DATA);
        if (existing != null) {
            NbtCompound data = existing.copyNbt();
            data.remove(TOKEN);
            if (data.isEmpty()) clean.remove(DataComponentTypes.CUSTOM_DATA);
            else clean.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        }
        return clean;
    }
    private static final class SupplyItem extends Item {
        private final SupplyKind kind;
        SupplyItem(Settings settings, SupplyKind kind) { super(settings); this.kind = kind; }
        @Override public ActionResult use(World world, PlayerEntity user, Hand hand) {
            if (kind == SupplyKind.POISON) {
                if (hand != Hand.MAIN_HAND) return ActionResult.FAIL;
                if (user instanceof ServerPlayerEntity player) return BanquetRuntime.contaminate(player) ? ActionResult.SUCCESS_SERVER : ActionResult.FAIL;
                return ActionResult.SUCCESS;
            }
            if (user instanceof ServerPlayerEntity player && !BanquetRuntime.canUse(player)) return ActionResult.FAIL;
            user.setCurrentHand(hand);
            return ActionResult.CONSUME;
        }
        @Override public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 32; }
        @Override public UseAction getUseAction(ItemStack stack) { return UseAction.DRINK; }
        @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
            if (user instanceof ServerPlayerEntity player) {
                Hand hand = player.getActiveHand();
                BanquetRuntime.consume(player, kind, hand);
                return player.getStackInHand(hand);
            }
            return stack;
        }
    }
}
